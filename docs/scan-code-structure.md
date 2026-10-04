# Estrutura do fluxo Scan

## Limites de responsabilidade

- `ScanBackExtractor` e `ScanFrontExtractor` são as fachadas públicas do OCR. Elas preservam as entradas existentes e delegam para colaboradores internos por responsabilidade.
- O verso foi separado em regras/normalização, seção de ingredientes, vocabulário INCI, dados da empresa, metadados da embalagem e blocos de uso/segurança. O maior arquivo caiu de aproximadamente 7.775 para 415 linhas.
- A frente foi separada em extração de marca, produto e texto compartilhado. O maior arquivo caiu de aproximadamente 3.757 para 339 linhas.
- `ScanCosmeticClassifier` mantém a decisão pública e separa categorias, evidências e normalização. O arquivo de fachada caiu de aproximadamente 3.375 para 976 linhas.
- `ScanProductMatchRepository` mantém apenas o ciclo assíncrono/catálogo e delega matching, pontuação e texto. O arquivo caiu de aproximadamente 2.336 para 226 linhas.
- `ScanIngredientNormalizer` delega à política compartilhada `domain.scan.ScanIngredientPolicy`: correções pequenas e únicas contra o vocabulário local, depois da segmentação, sem completar fórmulas por frases conhecidas. Fragmentos incertos e nomes brutos permanecem disponíveis; nomes fora do vocabulário seguem como candidatos para revisão administrativa, inclusive ingredientes legítimos ainda não cadastrados nesse vocabulário.
- A confiança do texto não invalida sozinha a ordenação espacial. Caixas, rotação, cobertura e correspondência com o texto continuam sendo verificadas por `ScanBackSpatialLayout`.
- Linhas completas de códigos de embalagem a aproximadamente ±90° são mantidas em um grupo periférico separado, sem misturá-las às linhas horizontais, somente quando não sobrepõem suas caixas. O OCR completo e todas as linhas/tokens são preservados. A orientação horizontal tolera até 20° de variação de perspectiva; texto rotacionado que possa ser ingrediente, orientação inconsistente acima desse limite ou cobertura incompleta mantém o fallback conservador. `BACK_ORIENTATION_GROUPS` registra a separação; o limite da composição continua dependendo das regras da seção, não apenas da rotação.
- Os colaboradores internos dos extractors são package-private e não constituem novas entradas do fluxo. A política e o vocabulário de ingredientes são compartilhados com a validação do envio.

## Código removido

Foram removidas classes sem referências no código de produção ou nos testes do projeto: `ScanFrontAnalyzer`, `ScanTextAnalyzer`, `ScanProductMatcher`, `ScanProductCandidate`, `ScanProductResolver`, `ScanBackOcrRepository` e `ScanStatus`. O fluxo ativo usa os extractors, o classificador e o `ScanProductMatchRepository`; a busca real do catálogo não passa pelos utilitários antigos.

## Proteções

- `tools/test-scan-back.ps1` compila os colaboradores do verso e executa 35 regressões do parser, incluindo os textos reais fornecidos, preservação de fragmentos incertos e geometria sintética com baixa confiança textual, perspectiva e códigos verticais periféricos. Os logs reais não incluem coordenadas; esses testes não substituem a câmera no aparelho.
- `tools/test-scan-analysis.ps1` executa 16 rótulos sintéticos e compara um digest dos campos de frente/classificação capturados antes da separação.
- `tools/test-scan-submission.ps1` cobre 67 testes de envio/armazenamento, incluindo o matcher do catálogo, consultas exatas ao PostgreSQL e a preservação de candidatos incertos para revisão no mapper e na entrada do cliente POST. Resíduos administrativos e composição vazia continuam protegidos. Esses testes usam serviços simulados, sem envio real.
- O matcher de catálogo também é compilável isoladamente com os modelos e as quatro classes de catálogo; seus valores de pontuação continuam encapsulados em `ScanCatalogScoring.ProductEvidence`, com campos package-private apenas para o consumidor direto.

A separação não muda o contrato das fachadas públicas nem habilita o `POST /api/scan-sessions`: as imagens continuam no Cloudinary e o cadastro no Mongo permanece como próxima etapa.
