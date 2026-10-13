# Fluxo de Scan

## Arquitetura
Cada etapa possui sua própria Activity e layout XML, seguindo o padrão do restante do aplicativo.

- `ScanTutorial1Activity` / `activity_scan_tutorial_1.xml`
- `ScanCameraBackActivity` / `activity_scan_camera_back.xml`
- `ScanSuccessBackActivity` / `activity_scan_success_back.xml`
- `ScanTutorial2Activity` / `activity_scan_tutorial_2.xml`
- `ScanCameraFrontActivity` / `activity_scan_camera_front.xml`
- `ScanSuccessFrontActivity` / `activity_scan_success_front.xml`
- `ScanTutorial3Activity` / `activity_scan_tutorial_3.xml`

As duas câmeras usam CameraX real. As telas de sucesso exibem exclusivamente a foto real capturada em tela cheia, sem moldura adicional e sem barras pretas.

## Fluxo
Primeira utilização de uma conta:

`Tutorial 1 -> CameraX verso -> Foto real -> decisão`

- match: abre `DetalheProdutoActivity` e encerra o fluxo;
- sem match: `Tutorial 2 -> CameraX frente -> Foto real -> Tutorial 3 -> Toast "Produto enviado para análise!"`.

Depois que o onboarding dessa conta é concluído, nenhum dos três tutoriais volta a aparecer. Novos scans entram direto na câmera traseira; caso não haja match, vão direto para a câmera frontal; ao concluir a foto frontal, o fluxo finaliza e mostra a notificação.

## Persistência
O estado é salvo em `SharedPreferences`, mas a chave é composta pelo UID do usuário autenticado no Firebase. Assim, a conclusão do onboarding de uma conta não bloqueia os tutoriais de outra conta no mesmo dispositivo.

As flags antigas globais foram deixadas de fora da decisão para evitar que um usuário marque o onboarding como concluído para todos os demais.

## Imagens dos tutoriais
As três artes de referência são mantidas em PNG lossless no diretório `drawable-nodpi`, com as dimensões originais 412x917. Isso evita a perda adicional de qualidade provocada por uma conversão WebP com perdas.
