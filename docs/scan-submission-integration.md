# Envio de novos produtos — preparação, assinaturas e fotos

## Implementado no mobile

- `scanId` UUID estável, conta proprietária e horários do rascunho.
- Snapshot privado em `noBackupFilesDir/scan-submissions`, separado por conta, com cópias das duas fotos. Gravação atômica e reabertura sem os arquivos temporários originais.
- Estado `WAITING_UPLOAD`: salvar no aparelho não significa enviar ao servidor.
- Avaliação local das duas fotos: luminosidade, desfoque por variância do Laplaciano e uniformidade do fundo; métricas são agregadas no rascunho.
- Contratos de `GET /api/scan-sessions/upload-signatures` e `POST /api/scan-sessions`.
- Cliente com Firebase ID token, renovação única após HTTP 401, recusa de troca de conta e tratamento de 403 sem criar/alterar contas automaticamente.
- Em 403, diálogo no visual Venus oferece **Conectar minha conta**: somente após o toque, envia `POST /api/users` com o próprio UID autenticado, nome/e-mail Firebase e status inicial ACTIVE. Não envia senha. Não consulta rotas administrativas nem atualiza contas existentes.
- Um 409 de cadastro não é tratado como sucesso: o fluxo confirma o acesso com novo GET de assinaturas. Se continuar 403, orienta verificar o cadastro, sem repetir POST nessa tentativa. Fotos e `scanId` são preservados.
- Após salvar o rascunho, o fluxo chama `GET /api/scan-sessions/upload-signatures?scanId=...` na API configurada em `ClienteApi.URL_BASE`. O token é obtido fora da thread principal e só enviado à API Venus.
- Depois de os dois recibos do Cloudinary e a consulta de ingredientes serem persistidos, o fluxo monta o contrato final e chama `POST /api/scan-sessions` com o mesmo `scanId`. A resposta precisa conter `id`, `scanId` correspondente e `status`; ela é salva atomicamente como `SUBMITTED`, permitindo retry local sem repetir o POST quando a resposta já foi confirmada.
- Validação da resposta: configuração obrigatória e assinaturas dos dois `publicId` correspondentes ao UUID e lado esperados. Preset é opcional. A validade temporal será reavaliada no momento do upload; este passo não certifica que o Cloudinary aceitará a assinatura.
- Tela de espera e erros seguros para autenticação, 403, conexão, timeout, HTTP e resposta incompatível. O retry consulta o snapshot salvo e mantém o mesmo `scanId`, sem repetir OCR ou copiar fotos.
- Assinaturas ficam somente em memória; tokens, assinaturas e corpos de resposta não são registrados. Troca de conta durante o GET descarta a resposta antes de entregá-la à tela.
- Conversão explícita do OCR para o contrato do backend, preservando nomes brutos e rejeitando limites excedidos em vez de cortar texto silenciosamente.
- Correção conservadora após segmentação, compartilhada pelo parser e mapper: erros pequenos e únicos como `QLYCINE` → `GLYCINE` e `0CTYLDODECANOL` → `OCTYLDODECANOL` podem ser corrigidos. Não se completam fórmulas a partir de frases OCR corrompidas. A composição e o OCR original permanecem para auditoria. Como a API aceita apenas `rawName`, o mapper recalcula a correção a partir do candidato bruto, sem confiar em normalizações antigas.
- Candidatos incertos seguem preservados para a revisão administrativa e não bloqueiam a preparação nem a entrada do cliente POST. Resíduos administrativos, composição vazia e dados estruturalmente inválidos continuam bloqueados antes de autenticação/rede. A lista local não é um catálogo INCI completo: ingredientes legítimos desconhecidos também seguem como candidatos. Não há meta fixa de quantidade de ingredientes; a decisão final continua com o administrador.
- Logs de produção não imprimem OCR, nomes de ingredientes, marca, produto, fabricante, lote, barcode ou evidências; ficam apenas contagens, estados, códigos HTTP e métricas de qualidade. O backup Android foi desativado para não copiar dados locais do fluxo.
- Após `MONGO_SUBMITTED`, o recibo idempotente é mantido e o snapshot local é redigido: OCR, candidatos, classificação, composição/catalogação e referências das fotos são removidos do JSON; os arquivos `front.photo` e `back.photo` são apagados. Isso mantém retry seguro sem conservar o rótulo no aparelho.
- A frente escolhe o candidato mais forte de marca/produto por uma pontuação determinística que penaliza descritores genéricos, números e ruído, sem substituir o OCR integral enviado para auditoria.
- A segmentação recompõe limites de alguns erros recorrentes do OCR (`PPG-14 BUTL ETE`, `OOPENTASILOXANE`, `EJANTHUS`, `ROPYLENE CARBONATE`, entre outros) somente em frases conhecidas; texto não reconhecido permanece candidato para revisão.
- A consulta de ingredientes não baixa mais páginas completas do catálogo: para cada candidato, usa `GET /api/ingredients/inci-name/{name}` e, quando necessário, `GET /api/ingredient-aliases/alias-name/{name}` seguido do ingrediente canônico. Respostas 404 viram `NOT_FOUND`; outros erros são tratados como indisponibilidade, sem concluir que o ingrediente não existe.
- A consulta reaproveita resultados iguais normalizados por até 10 minutos (cache limitado a 256 nomes e isolado por instância Retrofit), reduzindo chamadas repetidas em scans consecutivos sem ignorar a verificação da conta.
- Upload multipart das fotos privadas de frente e verso para o endpoint HTTPS fixo do Cloudinary, usando exatamente os parâmetros assinados pela API. Cliente separado, sem token Firebase, sem redirects e sem segredo Cloudinary no mobile.
- Validação do recibo: `public_id` do scan/lado esperado, recurso `image`, tipo de entrega, formato, dimensões, bytes e versão. Recibo de cada lado gravado atomicamente no rascunho; somente ambos confirmados levam ao estado local `PHOTOS_UPLOADED`, que então segue para a consulta e o cadastro.
- Nova tentativa conserva o mesmo `scanId` e as mesmas fotos; pula os lados com recibo salvo. Assinatura expirada localmente é renovada uma vez por lado; falhas HTTP do Cloudinary não disparam cadastro de conta.
- Troca de conta bloqueia o início/próximo upload. Se ocorrer durante um upload já iniciado, seu recibo é salvo na conta original, mas o outro lado não é enviado. Sair da tela não cancela uma requisição já iniciada; não há execução garantida após encerrar o processo.
- `background_ok=false` é uma advertência, não uma reprovação: o usuário é avisado e o scan continua. Somente desfoque extremo ou luminosidade fora dos limites bloqueiam a preparação local.

## Não implementado / não habilitado nesta etapa

- Tela de retomada das submissões pendentes. Os arquivos já são duráveis e há API local de leitura/listagem.
- Tela de revisão de ingredientes e resolução de nomes fora do vocabulário local. O log `INGREDIENT_REVIEW_REQUIRED` sinaliza essa necessidade, mas não representa uma revisão humana concluída.
- Recuperação do ID numérico de contas já existentes sem cache: a sincronização geral trazida do remoto ainda usa `/api/users/search`, restrito a ADMIN no backend local. O fluxo de scan não depende dessa consulta, mas esta mudança não corrige toda a sincronização de perfil.
- Calibração da avaliação de qualidade: as métricas atuais são heurísticas; `COMPLETED` significa cálculo concluído, não aprovação da foto. Falhas de decodificação impedem a preparação local.
- Revisão administrativa, aprovação e sincronização final do produto com PostgreSQL (fora do escopo). Nesta etapa, o mobile apenas consulta, em modo leitura, o catálogo de ingredientes PostgreSQL para classificar cada candidato.

## Próxima etapa

Validar no aparelho uma submissão completa: Cloudinary, consulta do catálogo e `POST /api/scan-sessions`. Confirmar no MongoDB um registro com o mesmo `scanId` e status inicial de análise (normalmente `PENDING_REVIEW`). O POST chegou ao Render, mas retornou HTTP 503 ao acessar o MongoDB; se o provedor do Mongo exige allowlist de IP, o Render não oferece o egress fixo necessário. A migração para AWS deve acontecer antes do novo teste real, preservando o mesmo contrato da API e sem alterações no backend. Revisão administrativa e sincronização final com PostgreSQL continuam fora desta etapa.

### Hospedagem planejada da API

O caminho de menor custo para este projeto é uma instância EC2 pequena executando a imagem do `Dockerfile` existente, com um Elastic IP associado. Esse IP será adicionado à allowlist do MongoDB e, se necessário, do PostgreSQL. O acesso público deve usar um subdomínio HTTPS apontado para o Elastic IP; as variáveis já esperadas em `application.yml` (`MONGO_URI`, `DB_*`, Cloudinary, Firebase e limites de mídia) devem ser entregues como secrets da infraestrutura. O MongoDB e o PostgreSQL existentes não devem ser substituídos por containers vazios do `docker-compose`; o compose é apenas ambiente local. Para reduzir custos, não criar NAT Gateway, Load Balancer, RDS, DocumentDB ou ECS nesta primeira implantação.

O ambiente acadêmico usa o endereço HTTPS gratuito `https://34-192-194-32.sslip.io/`, apontado para o Elastic IP da EC2. Em produção comercial, substituir por domínio próprio. Validar assinaturas, Cloudinary e POST antes de retirar o Render como rollback; manter o fallback até o Mongo confirmar o primeiro `PENDING_REVIEW` na AWS.

Um timeout pode ocorrer depois de o Cloudinary ter aceitado a foto: sem recibo local confirmado, a tentativa seguinte pode reenviar o mesmo arquivo para o mesmo `public_id`, respeitando o `overwrite` assinado pela API. Não há garantia de execução exatamente uma vez. Recibos confirmados são preservados entre reinícios, mas a tela de retomada após sair/encerrar o aplicativo ainda não existe.

Antes de produção, revisar no backend a propriedade do scanId ao assinar uploads/retornar registros existentes, a validação de existência das fotos e o índice único de scan_id. Essas proteções não podem ser garantidas apenas pelo mobile.

## Verificação

- `tools/test-scan-submission.ps1`: testes JVM com dados sintéticos, Retrofit/Cloudinary simulados e armazenamento em diretório temporário. Não usa credenciais nem rede. Inclui multipart, recibos inválidos, falhas HTTP/rede, expiração, isolamento por conta, seleção de marca/produto, limpeza pós-envio e retomada após recriar o `ScanDraftStore` (simulando fechar/abrir o app).
- `tools/compile-scan-submission.ps1`: compilação complementar de autenticação, cliente, repositório e finalizador contra SDK/cache local e último build; não verifica todas as Activities nem produz APK.
- Executar posteriormente o build Gradle e teste no dispositivo, incluindo perda de conexão, reinício, expiração de sessão e troca de conta.

### Teste desta etapa no aparelho

1. Executar a versão atualizada no Android Studio e entrar com uma conta Firebase que também esteja cadastrada/habilitada na API.
2. Fazer um scan de produto novo (frente e verso) e concluir o tutorial, se exibido.
3. Filtrar o Logcat por `VENUS_SCAN_SIGNATURES`, `VENUS_SCAN_UPLOAD`, `VENUS_SCAN_INGREDIENTS` e `VENUS_SCAN_SUBMISSION`. Esperado: assinaturas `READY`, `START`/`UPLOADED` para `front` e `back`, `CATALOG_SAVED`, `REQUEST` e `MONGO_SUBMITTED scanId=... id=... status=PENDING_REVIEW` (ou o status equivalente devolvido pela API). Confirmar no Cloudinary os dois `public_id` em `scans/{scanId}/front` e `scans/{scanId}/back` e no MongoDB o documento com o mesmo `scanId`.
4. Em 403 nas assinaturas ou no POST, tocar em **Conectar minha conta** quando a opção aparecer. Esperado: `CONNECT_ACCOUNT scanId=...` e depois `READY`. Se continuar 403, conferir o cadastro/permissão com o responsável pela API. Não copiar tokens nem assinaturas.
5. Para testar recuperação de rede: interromper a internet durante o envio, aguardar a mensagem, reconectar e tocar em **Tentar novamente**. As solicitações devem manter o mesmo scanId. Se a frente já estava confirmada, esperar `SKIPPED ... side=front`, seguido do envio do verso. Não há tela de retomada após sair para o início nesta etapa.

Verificação local em 04/10/2026: 72 testes JVM aprovados com API/Cloudinary, armazenamento do estado `SUBMITTED`, limpeza pós-envio, seleção de marca/produto, retomada de rascunho pendente e catálogo PostgreSQL simulados; 35 regressões do parser do verso; e compilação complementar da integração Android. A compilação complementar não equivale a um build completo nem produz APK; o POST real, a consulta do catálogo e o fluxo Cloudinary ainda precisam ser validados no aparelho.

Referências oficiais consultadas: [Firebase ID tokens](https://firebase.google.com/docs/auth/admin/verify-id-tokens), [Cloudinary signed uploads](https://cloudinary.com/documentation/upload_images).
