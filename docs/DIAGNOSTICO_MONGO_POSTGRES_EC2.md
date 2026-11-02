# Diagnóstico do fluxo Scan: MongoDB, PostgreSQL e EC2

## Objetivo

Restabelecer e validar o fluxo de envio do Venus Mobile:

1. O aplicativo envia as imagens para o Cloudinary.
2. O aplicativo envia a sessão e os dados OCR para a API Venus-CRUD.
3. A API grava a sessão inicialmente no MongoDB, com status de revisão pendente.
4. O PostgreSQL permanece como catálogo oficial/consulta e só deve receber dados no fluxo previsto pela API (por exemplo, após aprovação administrativa), salvo se o contrato atual da API disser explicitamente o contrário.

O objetivo desta investigação é separar claramente falhas de rede/TLS, configuração da API, validação do payload e persistência. Não alterar dados de produção até que a causa seja comprovada.

## Ambiente conhecido

- Aplicativo: `Venus-mobile`.
- API: projeto `Venus-CRUD`, executado na EC2 dentro de Docker.
- Região AWS: `us-east-1`.
- Instância usada anteriormente: `t3.micro`, Amazon Linux 2023, serviço `venus-crud.service`.
- Acesso público usado pela aplicação: `https://34-192-194-32.sslip.io/`.
- Proxy: Nginx na porta 80/443, encaminhando para a API na porta local `8080`.
- Banco MongoDB: cluster Atlas `scan-session-qa` (não divulgar URI, usuário ou senha).
- PostgreSQL: banco Aiven configurado por variáveis `DB_HOST`, `DB_PORT`, `DB_NAME` e `DB_USERNAME` no container.
- A conta é AWS Academy/Lab e tem orçamento limitado. Não criar instâncias, IPs, NAT gateways, balanceadores ou clusters adicionais sem autorização explícita.

## Sintomas observados no mobile

### Falhas HTTP no envio do scan

Foram observados:

- `HTTP 503` em `POST_SCAN_SESSION`, indicando indisponibilidade da API ou de uma dependência.
- `HTTP 500` em `POST_SCAN_SESSION`, indicando erro interno na API.
- `HTTP 422` em `POST_SCAN_SESSION`, indicando que a API recebeu a requisição, mas rejeitou o payload por validação/regra de domínio.

Exemplo:

```text
VENUS_SCAN_SUBMISSION REQUEST scanId=...
VENUS_SCAN_SUBMISSION FAILED ... stage=POST_SCAN_SESSION http=422
```

O `422` não deve ser tratado automaticamente como falha de rede. É necessário capturar a resposta JSON da API (sem expor tokens) para descobrir qual campo foi rejeitado.

### Pipeline local

Em testes anteriores, as etapas abaixo concluíram:

```text
FINISH_REQUEST
DRAFT_SAVED
SIGNATURES_READY
PHOTOS_UPLOADED_BEGIN_CATALOG
CATALOG_REQUEST
CATALOG_SAVED
```

Isso indica que OCR, rascunho local, assinaturas e Cloudinary podem concluir antes da falha no envio da sessão. A investigação deve evitar reenviar indefinidamente o mesmo `scanId` sem verificar idempotência.

## Problema MongoDB

### Erro registrado

```text
com.mongodb.MongoTimeoutException:
Timed out while waiting for a server that matches WritableServerSelector

MongoSocketWriteException: Exception sending message
javax.net.ssl.SSLException: (internal_error) Received fatal alert: internal_error
```

Os três nós do replica set apareceram como `UNKNOWN/CONNECTING`. O timeout de escrita é consequência; a pista mais específica é a falha durante TLS.

### Evidências já obtidas

Em uma execução anterior, diretamente na EC2:

- DNS resolveu os hosts do Atlas.
- TCP para a porta `27017` conectou.
- `openssl s_client` negociou TLS 1.2 e informou `Verification: OK`.

Esses testes demonstram que a rota básica EC2 → Atlas já funcionou, mas não provam que a JVM/driver, credenciais ou configuração atual do container estão corretos.

### Possíveis causas a verificar

1. Cluster Atlas pausado ou ainda inicializando.
2. IP público de saída da EC2 removido/alterado na lista de Network Access do Atlas.
3. Instância ou container usando URI antiga, credencial errada, banco errado ou parâmetros incompatíveis.
4. Versão/configuração de Java ou driver Mongo diferente dentro do container.
5. Falha TLS intermitente do cluster ou conexão sendo encerrada por proxy/firewall.
6. Restrição de validação da coleção `scan_sessions`.
7. Índice/validator criado com pressupostos incorretos.

### Observação sobre o validator/índice

Foi observado anteriormente que documentos antigos sem `scan_id` impediam a criação de um índice único esperado. Não apagar documentos nem alterar o validator sem backup e autorização. O comando de `collMod` foi tentado com um script colado de forma corrompida e terminou em erro de sintaxe; portanto, não considerar essa alteração aplicada.

### Diagnóstico seguro no container

Executar dentro da EC2/SSM, sem imprimir a senha:

```bash
sudo docker inspect venus-crud --format '{{range .Config.Env}}{{println .}}{{end}}' \
  | grep -E '^(MONGO_URI|MONGO_DATABASE|SPRING_DATA_MONGODB|DB_HOST|DB_PORT|DB_NAME|DB_USERNAME)=' \
  | sed -E 's#(mongodb\\+srv://[^:]+:)[^@]+@#\\1***@'

getent hosts ac-p4iylu5-shard-00-00.8ke7tfs.mongodb.net
timeout 10 bash -c '</dev/tcp/ac-p4iylu5-shard-00-00.8ke7tfs.mongodb.net/27017' \
  && echo MONGO_TCP=OK || echo MONGO_TCP=FAIL

openssl s_client \
  -connect ac-p4iylu5-shard-00-00.8ke7tfs.mongodb.net:27017 \
  -servername ac-p4iylu5-shard-00-00.8ke7tfs.mongodb.net \
  -tls1_2 -brief </dev/null 2>&1 | tail -n 12

sudo docker logs --since 30m venus-crud 2>&1 \
  | grep -Ei 'Mongo|SSL|WritableServer|Timeout|Authentication|Authorization|scan_sessions|validator|duplicate|ERROR' \
  | tail -n 200
```

Não executar `0.0.0.0/0` como solução permanente no Atlas. Autorizar somente o IP de saída efetivo da EC2.

## Problema PostgreSQL / insert em `transactions`

### Confusão funcional

O fluxo planejado é MongoDB primeiro. O PostgreSQL é o banco do catálogo oficial e deve receber o produto somente no estágio de aprovação administrativa, conforme o contrato real da API. Em testes, apareceram erros relacionados a insert em `transactions`, o que pode significar:

- a API está executando um fluxo de transação PostgreSQL durante `POST_SCAN_SESSION`;
- uma configuração de datasource está apontando para o banco errado;
- a rotina Mongo falha e o código tenta uma transação de compensação;
- o erro apenas aparece no stack trace como causa secundária;
- há uma constraint/schema incompatível no PostgreSQL.

Não assumir que o PostgreSQL é a causa sem correlacionar o timestamp do erro com a chamada HTTP.

### Verificações necessárias

Na EC2:

```bash
sudo docker inspect venus-crud --format '{{range .Config.Env}}{{println .}}{{end}}' \
  | grep -E '^(DB_HOST|DB_PORT|DB_NAME|DB_USERNAME|SPRING_DATASOURCE|DATABASE_URL|JDBC)='

sudo docker logs --since 30m venus-crud 2>&1 \
  | grep -Ei 'transactions|Postgre|Postgres|SQL|DataIntegrity|constraint|duplicate|foreign key|not-null|violat|ERROR' \
  | tail -n 200
```

No PostgreSQL, em uma sessão administrativa protegida, verificar apenas metadados e estrutura:

```sql
SELECT current_database(), current_user;

SELECT table_schema, table_name
FROM information_schema.tables
WHERE table_name ILIKE '%transaction%';

SELECT column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_name = 'transactions'
ORDER BY ordinal_position;
```

Depois comparar as colunas obrigatórias com o DTO/requisição do Venus-CRUD. Não conceder permissões amplas nem executar `INSERT` manual para “testar” sem uma transação controlada e autorização.

## Validação da API e da EC2

### Estado dos serviços

```bash
sudo systemctl is-active venus-crud.service
sudo systemctl is-active nginx
sudo docker ps --filter name=venus-crud --format '{{.Names}} {{.Status}}'
sudo docker inspect venus-crud --format 'Started={{.State.StartedAt}} Image={{.Config.Image}}'
```

### Health check

O endpoint está protegido por Bearer. Portanto, `401` é esperado e confirma que a rota está respondendo:

```bash
curl -i http://127.0.0.1:8080/actuator/health
curl -ik -i https://34-192-194-32.sslip.io/actuator/health
```

Critérios:

- `401`: API/Nginx responderam; falta token.
- `5xx`: investigar serviço, banco, proxy ou configuração.
- timeout: investigar EC2, Nginx, security group e rota.
- `422`: API está viva e rejeitou o payload; coletar corpo de resposta.

### Logs do systemd/Docker

```bash
sudo systemctl status venus-crud.service --no-pager
sudo journalctl -u venus-crud.service --since '30 minutes ago' --no-pager -o cat
sudo docker logs --since 30m venus-crud 2>&1 | tail -n 300
```

Nunca compartilhar no relatório logs que contenham `MONGO_URI`, senha, token Firebase, chave Cloudinary ou cabeçalho `Authorization`.

## Validação funcional recomendada

1. Confirmar que a instância correta está `running` e com as verificações `2/2` ou `3/3` aprovadas.
2. Confirmar cluster Atlas ativo, IP da EC2 autorizado e usuário Mongo com permissões mínimas no banco de QA.
3. Confirmar serviço Docker e Nginx ativos.
4. Executar health check sem token e registrar `401` como comportamento esperado.
5. Obter um token de teste válido do usuário logado no mobile, sem registrar o token.
6. Enviar um único scan de teste com `scanId` novo.
7. Capturar o status HTTP e o corpo sanitizado da resposta.
8. Confirmar no MongoDB que existe exatamente um documento para o `scan_id`, com status esperado (`PENDING_REVIEW` ou equivalente), URLs do Cloudinary e timestamps.
9. Confirmar nos logs que não houve fallback indevido para PostgreSQL nem erro de `transactions`.
10. Se o contrato exigir aprovação antes do PostgreSQL, confirmar que nenhum registro oficial foi inserido antes dessa etapa.
11. Repetir a mesma requisição somente para verificar idempotência; não criar duplicatas.
12. Encerrar a sessão SSM e parar a EC2 ao concluir, mantendo dados e configuração preservados.

## Critérios de conclusão

A correção só pode ser considerada concluída quando todos os itens forem comprovados:

- DNS, TCP e TLS para Atlas funcionam a partir do mesmo container da API.
- A JVM consegue autenticar no Mongo e obter um servidor gravável.
- `POST_SCAN_SESSION` retorna sucesso para um payload válido.
- O documento é persistido no MongoDB uma única vez.
- O Cloudinary mantém as imagens referenciadas.
- Não há erro de insert/constraint em `transactions` nos logs da operação.
- O PostgreSQL só é utilizado no estágio previsto pelo contrato.
- Não há credenciais, OCR completo ou tokens nos logs.
- A instância é parada após os testes para evitar consumo da cota AWS Academy.

## Restrições e cuidados

- Não terminar a instância; apenas parar quando a validação terminar.
- Não criar uma segunda EC2, NAT Gateway, load balancer, IP elástico adicional ou cluster.
- Não liberar MongoDB para `0.0.0.0/0`.
- Não alterar validators, índices, schemas ou permissões sem backup e confirmação.
- Não excluir documentos antigos para fazer um índice “caber”.
- Não commitar segredos ou colocar URIs completas em código, logs ou tickets.
- Se a conta AWS Academy estiver expirada ou a instância inacessível, registrar o bloqueio e aguardar novo login; não tentar contornar a autenticação.

## Resultado esperado do diagnóstico

O relatório final da outra IA deve separar explicitamente:

1. **Confirmado:** fatos comprovados por comando/log.
2. **Não confirmado:** hipóteses que ainda precisam de teste.
3. **Correção aplicada:** somente mudanças realmente executadas.
4. **Próximo passo:** o menor teste seguro necessário.
5. **Estado de custo:** se a EC2 foi parada e se algum recurso adicional foi criado.

