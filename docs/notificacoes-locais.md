# Notificações locais e cartão "Seu perfil Venus"

Regra desta entrega: **só o repositório do app muda**. Nada no Venus-CRUD, na web nem no banco. Por isso não há push de servidor (Firebase): o próprio app confere a API e dispara notificações locais.

## O que a pessoa vê

| Recurso | O que acontece |
|---|---|
| Aviso de produto aprovado ou recusado | Depois de enviar um produto para análise, o app confere de tempos em tempos se a equipe decidiu. Aprovado: "Produto aprovado" / "{marca} {nome} já está no catálogo da Venus." (tocar abre o produto). Recusado: "Produto não aprovado" / "{nome}: {motivo}" (tocar abre Produtos em análise). |
| Reenvio do produto escaneado | Se o envio falha (sem internet, API dormindo) ou a pessoa sai da tela do scan no meio, o rascunho fica guardado e o app termina o envio sozinho quando a internet volta. Se der certo, chega "Produto enviado". |
| Cartão "Seu perfil Venus" | O mesmo bloco do Dashboard do site, na Home (entre a busca e as sugestões) e na aba "Match com você" do produto. Sem questionário: "Você precisa responder as perguntas de análise." + "Responder agora" (sem caixa vermelha de aviso, para o cartão não chamar atenção demais). Incompleto: barra de % + "Faltam X% para completar as perguntas de análise." + "Completar agora". Completo: some. |
| Lembrete do questionário | "Responder depois" abre direto o pedido de permissão de notificação do sistema, sem pergunta própria. Permitindo (ou já tendo permitido), chegam até 3 lembretes ("Falta pouco para o seu Match"), 1, 3 e 7 dias depois do anterior, sempre às 10h. Tocar abre o questionário. Para sozinho quando ela responde, quando sai da conta ou depois do terceiro. |

A permissão de notificação (`POST_NOTIFICATIONS`) é pedida quando faz sentido: no diálogo "Scan enviado" ("Quer ser avisado quando analisarmos o produto?") ou ao sair de um envio que não terminou (uma vez por conta), e na hora, em "Responder depois" no questionário, só com o diálogo do sistema (se negar, o lembrete não liga e a pessoa segue para o app). Negar não quebra nada: a decisão continua na tela Produtos em análise.

## Como funciona

### Aviso de aprovado ou recusado (consulta periódica)
| Peça | Papel |
|---|---|
| `repository/DecisoesAgenda` | Agenda uma conferência agora (ao abrir o app, ao enviar um produto) e, enquanto há produto esperando decisão, uma periódica de 15 em 15 min (o mínimo do Android; pode atrasar mais com o celular parado). |
| `repository/DecisoesWorker` | O trabalho do WorkManager. Confere, publica os avisos e liga ou desliga a periódica conforme ainda haja o que esperar. |
| `repository/ConsultaDeDecisoes` | Compara a lista de Produtos em análise (a mesma rota `GET /api/scan-sessions/user/{userId}`) com os ids já decididos que viu. A primeira conferência de uma conta só aprende, para quem acabou de instalar não receber um aviso por produto antigo. |
| `domain/aviso/AvisoDeAnalise` | Texto e destino do toque, em Java puro. O nome e a marca são os que o OCR leu, não os que o admin aprovou. |

Para quando não há mais produto esperando e no logout (`AutenticacaoRepository.sair`).

### Reenvio do scan pendente
| Peça | Papel |
|---|---|
| `repository/ReenvioDeScan`, `EtapasDoScan` | Termina um envio pela metade: assinaturas → fotos → ingredientes → cadastro, pulando o que já foi confirmado. |
| `repository/ReenvioDeScanWorker`, `ReenvioDeScanAgenda` | WorkManager: um trabalho só, que espera internet, repete com espera crescente (até 6 vezes) e é agendado ao falhar um envio e ao abrir o app. |

Só mexe em rascunhos parados há mais de 2 minutos. Reenviar o mesmo `scanId` é seguro (a API devolve o scan que já existe). Erros de conta (401/403) não são repetidos: a pessoa precisa agir na tela do scan ("Conectar minha conta").

### Lembrete do questionário (alarme local)
| Peça | Papel |
|---|---|
| `lembrete/Lembretes` | Agenda e dispara o alarme (`AlarmManager.setAndAllowWhileIdle`, sem permissão de alarme exato; o horário pode atrasar alguns minutos com o celular parado). Cada disparo mostra a notificação, conta o envio e arma o próximo. |
| `lembrete/LembretesReceiver` | Recebe o alarme e rearma depois de reiniciar o celular, atualizar o app ou mudar o fuso/hora. O app também rearma ao abrir. |
| `domain/lembrete/AgendaQuestionario` | A conta de quando mandar cada lembrete (1, 3 e 7 dias, às 10h), em Java puro. |
| `repository/LembreteQuestionarioRepository` | O estado do lembrete, por conta (`DadosDaConta`). |

O alarme é da conta: no logout ele sai (o estado fica guardado e volta com a conta). Se a pessoa já respondeu o questionário, o lembrete se desliga sozinho. Sem a permissão de notificação o alarme dispara em silêncio.

### Cartão do perfil
| Peça | Papel |
|---|---|
| `domain/perfil/ProgressoPerfil` | A conta do site (respondidos / campos contados, arredondado), em Java puro. |
| `PerfilRepository.progresso()` | Os 9 campos: faixa etária, gênero, tipo de pele, fototipo, sensibilidade, tipo de cabelo, couro cabeludo, gestação e condições de pele. "Prefiro não dizer" conta como respondido. |
| `view/componente/CartaoPerfilView` | O cartão. O botão abre o questionário. Na Home ele vive no `InicioFragment`, que confere o progresso ao voltar para a tela. |

O site só conta os campos de saúde com o consentimento de dados de saúde; o app não tem esse passo, então conta os 9.

### Comum
`view/Notificador` (canais "Envio de produtos" e "Análise de produtos", permissão, publicação; tocar abre as telas por cima da `PrincipalActivity`) e `view/PedidoDeAvisos` (o pedido da permissão).

## Limites
- O aviso de aprovado/recusado não é instantâneo: leva até ~15 min, e mais com o celular parado ou em economia de bateria. Para ser instantâneo seria preciso push de servidor, que exige mudar o Venus-CRUD.
- Quem fecha o app à força não recebe nada até abri-lo de novo.
- O lembrete do questionário só existe para quem permitiu notificações em "Responder depois"; quem sai do questionário sem tocar ali só tem o cartão.
- O aviso depende da EC2 ligada.

## Como conferir no aparelho
1. **Aprovado/recusado:** escanear um produto novo, aceitar "Me avisar" e, no painel web, aprovar ou recusar. Em até ~15 min chega o aviso; tocar abre o produto ou Produtos em análise. Abrir o app também dispara uma conferência na hora.
2. **Reenvio:** ligar o modo avião, escanear um produto novo até o envio falhar e tocar em "Voltar". Desligar o modo avião: chega "Produto enviado".
3. **Cartão:** numa conta sem questionário, a Home mostra o cartão com "Responder agora"; responder parte das perguntas mostra a barra; ao completar o cartão some. Na aba "Match com você" de um produto aparece o mesmo cartão.
4. **Lembrete do questionário:** numa conta sem questionário, tocar em "Responder depois" e permitir as notificações no diálogo do sistema. Para ver sem esperar um dia, mudar a data do aparelho para o dia seguinte e reabrir o app.
5. **Logout:** sair da conta não deixa aviso dela para a próxima conta no aparelho.
