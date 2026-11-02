# Golden dataset do OCR

Este arquivo é o registro da primeira etapa do plano de melhoria do OCR. Os
casos ativos do verso e da frente do Rexona Clinical Clean Men usam os textos
OCR brutos fornecidos durante a validação. A imagem da frente não é armazenada
no repositório; por isso o caso frontal declara explicitamente
`not_attached_ocr_payload_only` e não finge uma evidência visual que não foi
anexada.

## Estado atual

Casos sem OCR bruto e sem imagem correspondente permanecem em `pendingCases`
com o estado `inactive_missing_source`. Os casos ativos do Rexona contêm,
respectivamente, os nomes canônicos da composição e as evidências de marca,
produto e capacidade conferidas para o mesmo rótulo. O catálogo continua sendo
usado para resolver o produto, nunca para criar ingredientes que não tenham
evidência no texto capturado.

Enquanto o golden não é recriado, a fase de captura já aplica verificações
independentes de um produto específico: resolução mínima, foco/desfoque e
iluminação. A captura aceita mantém o JPEG original; a análise usa uma versão
reduzida apenas em memória. Fundo irregular permanece como alerta, não bloqueio.

A leitura agora também executa passagens locais limitadas (`ORIGINAL`, contraste,
recorte central e rotação de 180 graus). O resultado selecionado conserva os
tokens espaciais quando a geometria é compatível; as demais passagens deixam somente metadados de auditoria
(variante, quantidade de caracteres/linhas, pontuação e falha). Os arquivos
derivados são temporários e removidos ao terminar, sem alterar o contrato da
API nem enviar OCR duplicado.

Somente `ORIGINAL` e `CONTRAST` mantêm a geometria da imagem-fonte. Se a melhor
passagem for um recorte ou rotação, o texto selecionado é preservado, mas os
tokens espaciais são omitidos para que o parser não use coordenadas inválidas;
nesse caso ele segue pelo fallback textual seguro.

## Contrato de cada caso

- `ocrLines`: linhas retornadas pelo motor, preservadas sem correção.
- `expectedCanonicalNames`: nomes que a resolução deve recuperar quando houver
  evidência suficiente no rótulo e no catálogo.
- `allowedUnresolved`: fragmentos conhecidos que devem continuar pendentes nesta
  captura, sem descarte silencioso.
- `mustNotPromotePatterns`: texto administrativo ou ruído que não pode virar
  ingrediente/marca promovido.
- `acceptance`: critérios mínimos de auditoria do caso.

## Lacunas que ainda precisam de imagens reais

Antes de avançar para múltiplas passagens e otimização, o dataset ainda precisa
de pelo menos uma captura real para cada cenário abaixo:

1. duas colunas;
2. rótulo curvo;
3. foto inclinada/rotacionada;
4. texto muito pequeno;
5. parênteses, barras, percentuais e números;
6. iluminação escura e brilho especular;
7. Android de baixa e média capacidade;
8. composição sem o título `INGREDIENTES`.

Cada novo caso deve trazer a imagem de referência fora do repositório, o texto
OCR bruto e a composição conferida manualmente. Não se deve adicionar um alias ou
uma correção determinística apenas porque um único caso pareceu semelhante.
