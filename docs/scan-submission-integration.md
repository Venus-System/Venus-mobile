# Envio de novos produtos — preparação e assinaturas

## Implementado no mobile

- `scanId` UUID estável, conta proprietária e horários do rascunho.
- Snapshot privado em `noBackupFilesDir/scan-submissions`, separado por conta, com cópias das duas fotos. Gravação atômica e reabertura sem os arquivos temporários originais.
- Estado `WAITING_UPLOAD`: salvar no aparelho não significa enviar ao servidor.
- Avaliação local das duas fotos: luminosidade, desfoque por variância do Laplaciano e uniformidade do fundo; métricas são agregadas no rascunho.
- Contratos de `GET /api/scan-sessions/upload-signatures` e `POST /api/scan-sessions`.
- Cliente com Firebase ID token, renovação única após HTTP 401, recusa de troca de conta e tratamento de 403 sem criar/alterar contas automaticamente.
- Após salvar o rascunho, o fluxo chama `GET /api/scan-sessions/upload-signatures?scanId=...` na API configurada em `ClienteApi.URL_BASE`. O token é obtido fora da thread principal e só enviado à API Venus.
- Validação da resposta: configuração obrigatória e assinaturas dos dois `publicId` correspondentes ao UUID e lado esperados. Preset é opcional. A validade temporal será reavaliada no momento do upload; este passo não certifica que o Cloudinary aceitará a assinatura.
- Tela de espera e erros seguros para autenticação, 403, conexão, timeout, HTTP e resposta incompatível. O retry consulta o snapshot salvo e mantém o mesmo `scanId`, sem repetir OCR ou copiar fotos.
- Assinaturas ficam somente em memória; tokens, assinaturas e corpos de resposta não são registrados. Troca de conta durante o GET descarta a resposta antes de entregá-la à tela.
- Conversão explícita do OCR para o contrato do backend, preservando nomes brutos e rejeitando limites excedidos em vez de cortar texto silenciosamente.

## Não implementado / não habilitado nesta etapa

- Upload real ao Cloudinary e chamada de cadastro no MongoDB. A única chamada de negócio habilitada nesta etapa é o GET das assinaturas; não é feito POST de scan ou upload de foto.
- Tela de retomada das submissões pendentes. Os arquivos já são duráveis e há API local de leitura/listagem.
- Provisionamento/sincronização do usuário Firebase no cadastro da API.
- Calibração da avaliação de qualidade: as métricas atuais são heurísticas; `COMPLETED` significa cálculo concluído, não aprovação da foto. Falhas de decodificação impedem a preparação local.
- Revisão administrativa, aprovação e sincronização com PostgreSQL (fora do escopo).

## Próxima etapa

Validar a obtenção das assinaturas no aparelho com a API publicada. Depois, implementar o coordenador de envio: obter assinaturas frescas, enviar cada foto e persistir seu recibo, enviar o JSON somente com as duas fotos confirmadas e guardar o `id` da resposta. Repetições usam o mesmo scanId e o mesmo snapshot; não reenviar uma foto já confirmada só porque o POST falhou. Nenhuma mudança no backend faz parte desta implementação.

Antes de produção, revisar no backend a propriedade do scanId ao assinar uploads/retornar registros existentes, a validação de existência das fotos e o índice único de scan_id. Essas proteções não podem ser garantidas apenas pelo mobile.

## Verificação

- `tools/test-scan-submission.ps1`: testes JVM com dados sintéticos, Retrofit simulado e armazenamento em diretório temporário. Não usa credenciais nem rede.
- `tools/compile-scan-submission.ps1`: compilação complementar de autenticação, cliente, repositório e finalizador contra SDK/cache local e último build; não verifica todas as Activities nem produz APK.
- Executar posteriormente o build Gradle e teste no dispositivo, incluindo perda de conexão, reinício, expiração de sessão e troca de conta.

### Teste desta etapa no aparelho

1. Executar a versão atualizada no Android Studio e entrar com uma conta Firebase que também esteja cadastrada/habilitada na API.
2. Fazer um scan de produto novo (frente e verso) e concluir o tutorial, se exibido.
3. Filtrar o Logcat por `VENUS_SCAN_SIGNATURES`. Esperado: `REQUEST scanId=...` e `READY scanId=... front=true back=true`. A tela informa que as assinaturas chegaram, mas nada foi enviado.
4. Em falha, observar `FAILED scanId=... reason=... http=...` e a mensagem da tela. Não copiar tokens nem assinaturas. Um 403 exige conferir o cadastro/permissão da conta existente na API; o mobile não cria contas automaticamente.
5. Para testar recuperação de rede: finalizar um novo scan sem internet, aguardar a mensagem, reconectar e tocar em **Tentar novamente**. As duas solicitações devem manter o mesmo scanId. Não há tela de retomada após sair para o início nesta etapa.

Verificação local em 30/09/2026: testes JVM com API simulada e compilação parcial dos arquivos da integração. Build completo via Gradle bloqueado no ambiente por `Unable to establish loopback connection`; não houve validação autenticada real nem geração de APK nesta execução.

Referências oficiais consultadas: [Firebase ID tokens](https://firebase.google.com/docs/auth/admin/verify-id-tokens), [Cloudinary signed uploads](https://cloudinary.com/documentation/upload_images).
