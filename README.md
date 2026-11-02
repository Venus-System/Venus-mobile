# Venus-Mobile

Aplicativo Android da Venus: a pessoa monta o perfil de pele e cabelo, escaneia o rótulo de um cosmético e descobre se os ingredientes combinam com ela. Quando o produto ainda não está no catálogo, as fotos vão para análise da equipe e o app avisa quando houver decisão.

## Funcionalidades

- Cadastro e login por e-mail/senha ou conta Google (Firebase Authentication), com recuperação de senha.
- Questionário de perfil: tipo e sensibilidade da pele, fototipo, condições, cabelo, couro cabeludo, alergias, faixa etária, gestação e preferências.
- Scan do produto com CameraX e leitura do rótulo por OCR (ML Kit), primeiro o verso (ingredientes) e, se não houver correspondência, a frente.
- Envio de produtos novos para análise: fotos no Cloudinary com assinatura emitida pela API e cadastro do scan na API Venus.
- Detalhe do produto com ingredientes e a aba "Match com você", que cruza a composição com o perfil.
- Busca com histórico e filtros, produtos curtidos e listas de produtos.
- Chat com a Veninha, assistente de IA que responde sobre o produto e o perfil.
- Avisos locais quando um produto enviado é aprovado ou recusado e lembrete para terminar o questionário.

## Tecnologias

- Java 11, Android SDK 36 (mínimo Android 13, API 33), Gradle 9.1 com Android Gradle Plugin 9.
- Arquitetura MVVM com `ViewModel` e `LiveData`.
- Retrofit e OkHttp para as APIs, Coil para imagens.
- Room (SQLite) para a conversa com a Veninha e os produtos curtidos.
- WorkManager para consultar decisões de análise e reenviar scans pendentes.
- Firebase Authentication, Credential Manager (login Google), CameraX, ML Kit Text Recognition e Play Services Location.
- JUnit, Robolectric e MockWebServer nos testes.

## Estrutura

```
app/src/main/java/com/venussystem/venusmobile/
├── view/          Activities, fragments, adapters e componentes de tela
├── viewmodel/     estado de cada tela
├── repository/    acesso a dados
│   ├── api/       clientes Retrofit (API Venus, IA e Classificação) e DTOs
│   └── banco/     banco Room
├── domain/        regras de negócio (match, scan, perfil, avisos)
├── model/         objetos do app
└── lembrete/      alarmes e notificações locais
```

A documentação complementar está em [`docs/`](docs/), por exemplo o fluxo de envio de produtos em [`docs/scan-submission-integration.md`](docs/scan-submission-integration.md).

## Serviços usados

| Serviço | Para quê | Propriedade | Padrão |
| --- | --- | --- | --- |
| API Venus (Spring) | usuários, perfil, catálogo, listas, scans | `VENUS_API_URL` | `https://34-192-194-32.sslip.io/` |
| Venus IA (FastAPI) | chat com a Veninha | `VENUS_IA_URL` | `https://venus-ai.duckdns.org/` |
| Venus Classificação | nota pessoal do produto | `VENUS_CLASSIFICACAO_URL` | `https://34-192-194-32.sslip.io/classificacao/` |

As URLs não ficam no código: o Gradle lê cada propriedade e grava o valor no `BuildConfig`. Para apontar para outro ambiente, coloque a propriedade no `local.properties` da raiz:

```properties
VENUS_API_URL=http://10.0.2.2:8080/
```

ou passe na linha de comando:

```bash
./gradlew assembleDebug -PVENUS_API_URL=http://10.0.2.2:8080/
```

Sem nenhuma das duas, o build usa o padrão da tabela. Endereço `http://` só é aceito no build de debug e para `localhost`, `127.0.0.1` e `10.0.2.2` (o computador visto pelo emulador), conforme a `network_security_config` de `app/src/debug`.

O arquivo `app/google-services.json` do projeto Firebase já está no repositório.

## Como rodar

Pré-requisitos: Android Studio recente (o CI usa JDK 21; o JBR do Android Studio serve) e um aparelho ou emulador com Android 13 ou superior.

1. Clone o repositório e abra a pasta no Android Studio. Ele cria o `local.properties` com o caminho do SDK.
2. Espere a sincronização do Gradle.
3. Rode a configuração `app` no aparelho ou emulador.

Pela linha de comando:

```bash
./gradlew assembleDebug
```

O APK sai em `app/build/outputs/apk/debug/app-debug.apk`.

## Testes

```bash
./gradlew testDebugUnitTest
```

Os testes de instrumentação ficam em `app/src/androidTest` e rodam com um aparelho conectado:

```bash
./gradlew connectedDebugAndroidTest
```

## Fluxo de trabalho

- `main` recebe o que vai para produção e `develop` concentra o trabalho do dia a dia.
- Cada mudança sai de uma branch própria a partir da `develop` (`feat/`, `fix/`, `refactor/`, `docs/`, `style/`) e volta por pull request, seguindo o modelo em `.github/pull_request_template.md`.
- Commits e títulos de PR seguem os commits convencionais, por exemplo `feat: adiciona filtro por marca`.

## Integração contínua

- `CI` (`.github/workflows/ci.yaml`): em todo pull request para `develop` ou `main`, roda os testes unitários e gera o APK de debug.
- `Deploy Staging` (`deploy-qa.yaml`): a cada push na `develop`, gera o APK de debug e publica no GitHub Container Registry com a tag `staging`.
- `Deploy Production` (`deploy-prod.yaml`): a cada push na `main`, gera o APK de release e publica no GitHub Container Registry com a tag `latest`.

## Licença

[MIT](LICENSE)
