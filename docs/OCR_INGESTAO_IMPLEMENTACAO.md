# OCR e ingestão: estado implementado

## Limite do escopo desta aplicação

Este repositório é o cliente Android. A classificação científica, a promoção para o
catálogo PostgreSQL e as tabelas/views de prontidão pertencem ao `Venus-CRUD` e não
são executadas pelo OCR do aplicativo.

O mobile envia a evidência para a API e mantém o scan em revisão. Ele não cria
ingredientes, não cria `product_scores` e não calcula riscos científicos.

## Fluxo atual

```text
foto original
  -> OCR ML Kit (texto completo, linhas e tokens espaciais)
  -> parser estrutural do verso
  -> candidatos na ordem do rótulo
  -> normalização apenas para busca
  -> snapshot local do catálogo INCI/common name/aliases
  -> resolução exata, alias, common name ou fuzzy conservador
  -> issues abertas para ambiguidade, ausência e falha de catálogo
  -> upload Cloudinary
  -> POST /api/scan-sessions
  -> PENDING_REVIEW no MongoDB
```

O texto OCR bruto continua preservado no rascunho até o ACK da API. Após o ACK,
as imagens e os textos são removidos do rascunho local; o recibo de submissão e os
hashes SHA-256 das fotos permanecem para retomada/idempotência.

## Alterações feitas no cliente

- Cada rascunho também mantém um snapshot serializável dos tokens espaciais do
  ML Kit (texto, caixa, confiança e índices de bloco/linha). Essa evidência é
  app-private e é apagada no ACK junto com o OCR textual.

- O catálogo de ingredientes agora pode ser baixado uma vez e reutilizado por até
  24 horas, evitando uma chamada por ingrediente.
- O cache é atômico, app-private e contém somente IDs e nomes públicos; não copia
  avaliações científicas nem tokens de autenticação.
- Se a atualização falhar, um snapshot antigo pode ser usado para resolução local,
  mas o resultado fica marcado como não fresco e qualquer incerteza continua em
  `needs_review`.
- A evidência do relatório é vinculada à posição, ao texto bruto e à chave
  normalizada do candidato. Correções OCR determinísticas podem ser a segunda
  chave da mesma evidência; um relatório de outra tentativa não pode promover
  um ID para um token diferente.
- O relatório local expõe `issues` com posição, tipo, motivo e estado aberto.
- O rascunho grava hash SHA-256 das cópias imutáveis das imagens e rejeita a leitura
  se a evidência local tiver sido alterada antes do upload.
- O parser mantém o texto bruto e não transforma correção OCR em confirmação
  semântica. O nome canônico só é colocado na requisição quando o catálogo devolve
  uma resolução única.
- A qualidade agregada usa o pior `blurScore` das duas faces (o índice é maior
  quando há mais desfoque), a média de brilho e `background_ok=true` somente
  quando as duas faces passam. `background_ok=false` continua sendo aviso,
  não bloqueio automático.
- Os logs do envio registram somente etapa, código HTTP, tipo e contagens; texto
  de OCR, nomes de ingredientes, mensagens de validação do servidor e tokens não
  são concatenados ao logcat.

## Contrato com o backend

O contrato atual de `POST /api/scan-sessions` aceita texto bruto, linhas, campos
extraídos, imagens Cloudinary e candidatos posicionais. Por compatibilidade, as
issues e a confiança local ficam no rascunho do mobile e o backend refaz o match
autoritativo antes de persistir.

O mobile não envia um campo de estado do match de ingrediente. Portanto, a
classificação local de qualquer candidato não resolvido é `NEEDS_REVIEW` e ele
segue como evidência bruta; se o documento do Mongo exibir `match.status: "new"`,
essa conversão ocorre no Venus-CRUD e só pode ser renomeada no backend sem alterar
o contrato deste aplicativo.

No `Venus-CRUD`, a etapa administrativa deve continuar sendo a responsável por:

- revisar `NEEDS_REVIEW`/ambiguous;
- decidir `LINK`, `CREATE` ou `DISCARD`;
- criar/atualizar `product_labels`, `product_versions` e `product_ingredients`;
- gerar o receipt de composição;
- promover somente uma composição integralmente validada;
- executar classificação e scores em etapa separada.

Se for necessário persistir bounding boxes, confidence por token, issue codes ou
formula hash no banco, o contrato da API e o backend precisam ser estendidos. O
mobile não envia campos que o backend atual não declara, pois isso quebraria a
compatibilidade e poderia produzir `422`.

## Estados de resolução

```text
RESOLVED  -> INCI, ALIAS, COMMON_NAME ou fuzzy único
AMBIGUOUS -> candidatos próximos; revisão obrigatória
UNRESOLVED -> não encontrado, token curto/truncado, catálogo indisponível ou erro
```

`VALID` só é usado quando todos os tokens processáveis têm uma resolução única.
Qualquer token não resolvido ou ambíguo produz `NEEDS_REVIEW`; ele não é descartado
e não recebe nota científica.

## Validação necessária antes de produção

1. `testDebugUnitTest` e `assembleDebug` foram executados com sucesso no
   Android Studio, usando o JDK/Android SDK configurados no projeto. O daemon
   isolado deste ambiente pode falhar ao abrir loopback, mas isso não invalida
   a execução concluída pelo Android Studio/CI.
2. Testar um rótulo de uma coluna, duas colunas, imagem inclinada, texto com
   acento/hífen/barra/parênteses, `PARFUM`, `FRAGRANCE`, `AROMA`, alias, ambiguidade
   e token não encontrado.
3. Repetir a mesma tentativa e confirmar que não há duplicidade de scan, upload,
   receipt ou vínculo.
4. Fechar e reabrir o aplicativo antes do ACK e confirmar a retomada do rascunho.
5. Confirmar no Mongo que o documento fica `PENDING_REVIEW` e que nenhum score é
   criado.
6. Confirmar que o PostgreSQL só é alterado depois da aprovação administrativa.

