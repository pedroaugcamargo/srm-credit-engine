# Post-mortem: liquidações duplicadas (Anexo B)

> **Cenário hipotético.** Este é um exercício sobre o incidente descrito no Anexo B do desafio. Os horários, a sequência de eventos e os sinais observados são uma reconstrução plausível, coerente com o código do Anexo A, e **não medições reais**. Valores e quantidades que dependeriam de dados de produção aparecem como "a apurar", com a consulta que os apuraria.

- **Formato:** sem culpados (blameless). O código foi aprovado pelo processo que existia, e é o processo que muda.
- **Severidade:** SEV-1. Houve pagamento a mais para cedentes.
- **Plantão:** eu (incident commander) e um dev pleno.

## Resumo

Numa sexta-feira à tarde, o banco ficou lento e as requisições de liquidação passaram a demorar mais que o timeout do gateway. O gateway e os operadores repetiram as requisições. Cada repetição gravou uma nova liquidação, porque o endpoint do Anexo A não tem idempotência, não verifica se o recebível já foi liquidado e não tem nenhuma restrição de unicidade no banco. Três cedentes receberam a mesma liquidação duas vezes. Quem detectou o problema foi a mesa de operações, às 18h40, e não o monitoramento.

## Linha do tempo (hipotética)

| Horário | Evento |
|---|---|
| Sexta, 2 semanas antes | O código do Anexo A entra em produção. |
| 16h00 | O banco fica lento (hipótese: consultas pesadas de fechamento do dia disputando recursos). A latência das queries sobe. |
| 16h05 | Liquidações passam a demorar mais que o timeout do gateway. O gateway devolve 504 ao cliente, mas a requisição continua rodando no servidor e o INSERT acaba gravando. |
| 16h05–17h30 | O gateway repete automaticamente os POST que deram timeout. Operadores que viram erro na tela clicam de novo em "Liquidar". |
| 16h20 | Primeira duplicata: duas linhas em `settlements` para o mesmo `receivable_id`. Nenhum alerta dispara: o endpoint responde `200` até quando a gravação falha, e nada é logado. |
| 17h30 | A lentidão do banco cede e os timeouts param. Novas duplicatas deixam de surgir. |
| 18h40 | A mesa reporta que três cedentes receberam a mesma liquidação duas vezes. Abertura do incidente. |
| 18h45 | Contenção: bloqueio de `POST /settlements` no gateway e retry automático do gateway desligado para POST. |
| 18h50 | Pedido ao time de pagamentos para segurar o que ainda não saiu. |
| 19h00–20h00 | Confirmação da causa raiz e levantamento de todas as duplicatas (seção seguinte). |
| 20h00 | Conciliação com a mesa: lista fechada de cedentes, recebíveis e valores pagos a mais. |

O tempo entre a primeira duplicata e a detecção foi de 2h20, e quem detectou foi uma pessoa. Essa lacuna é parte do problema, tanto quanto o bug.

## Causa raiz

**Causa direta.** O endpoint do Anexo A não é idempotente. Uma requisição repetida grava uma segunda liquidação, porque nada impede:
- não há chave de idempotência;
- o código não verifica se o recebível já está `SETTLED`;
- não há transação nem lock: duas requisições leem o mesmo estado e as duas gravam;
- não há `UNIQUE (receivable_id)` em `settlements`.

**Por que a repetição aconteceu.** O timeout do gateway e o tempo da requisição no servidor são independentes. O cliente recebe 504, mas o INSERT termina e é gravado. O gateway trata o POST como seguro para repetir, e o operador, sem confirmação na tela, clica de novo. Os dois comportamentos são razoáveis isoladamente; juntos com um endpoint não idempotente, pagam em dobro.

**Fatores contribuintes.**
- **Exceção engolida** (item 2 do [`REVIEW.md`](REVIEW.md)): se o UPDATE falha por causa da lentidão, o recebível continua `OPEN` sem ninguém saber. Neste código isso não muda o resultado, porque o status nunca é consultado, mas esconde o sintoma.
- **Nenhum log nem métrica de liquidação no código:** o incidente ficou invisível até a mesa reclamar.
- **Aprovação às pressas:** um PR que move dinheiro foi mergeado numa sexta-feira sem nenhum teste de repetição ou concorrência.

## Evidências: como confirmar

O Anexo A não loga nada, então a evidência vem do banco, do gateway e do sistema de pagamentos.

1. **Banco: recebíveis com mais de uma liquidação.**
   ```sql
   select receivable_id, count(*) as liquidacoes, sum(amount) as total_pago
     from settlements
    group by receivable_id
   having count(*) > 1;
   ```
   O resultado esperado são os três recebíveis reportados pela mesa, ou mais. É essa consulta que fecha a lista, não o relato.

2. **Gateway: mesmo payload repetido em segundos.** Nos access logs do gateway, procurar `POST /settlements` com o mesmo `receivableId` no corpo, com intervalo de segundos ou milissegundos, e uma resposta 504 antes da repetição. O padrão "504 seguido de uma nova tentativa" confirma o retry.

3. **Métricas do gateway e do banco.** Contagem de retries e de 504 em `/settlements` e latência das queries entre 16h e 17h30. A correlação temporal entre lentidão, timeouts e duplicatas sustenta a causa.

4. **Descartar a outra hipótese.** Se houvesse duplicatas fora da janela de lentidão, a causa principal seria o duplo clique, e não o retry. O INSERT do Anexo A não grava data, então, a menos que a tabela tenha uma coluna com valor padrão, a hora de cada linha sai do log do gateway.

O que precisa ser apurado com essas consultas: quantos recebíveis foram duplicados, o valor total pago a mais e se algum pagamento duplicado ainda não saiu.

## Contenção (mesma noite)

Objetivo: parar a perda primeiro e entender depois. Cada ação é reversível.

1. **Parar novas duplicatas.** O código do Anexo A não tem feature flag, então bloqueamos `POST /settlements` no gateway com um `503` e uma mensagem para a mesa. Liquidar fica indisponível, o que é melhor do que pagar em dobro.
2. **Desligar o retry automático do gateway para POST.** Não é seguro repetir um POST sem chave de idempotência.
3. **Segurar os pagamentos pendentes** dos recebíveis duplicados com o time de pagamentos, antes que saiam.
4. **Conciliar com a mesa** a lista fechada pela consulta do banco: cedente, recebível, valor devido e valor pago.
5. **Não apagar as linhas duplicadas.** Elas são a evidência e o registro contábil. A devolução ao fundo vira um lançamento novo de estorno, combinado com cada cedente pelo negócio.
6. **Comunicar** a liderança e a mesa a cada hora até a contenção fechar, dizendo o que se sabe e o que não se sabe.

Divisão do plantão: o dev pleno roda as consultas e prepara a lista; eu coordeno a contenção, falo com a mesa e com pagamentos e decido quando reabrir.

## Correção definitiva

A liquidação só reabre com as três barreiras de idempotência em produção e testadas. É o que este repositório implementa (detalhes nos itens 2 e 3 do [`REVIEW.md`](REVIEW.md)):

- **`Idempotency-Key` obrigatória** e hash do payload: a repetição devolve a liquidação original com `Idempotent-Replayed` ([`SettlementService`](backend/src/main/java/com/srmasset/creditengine/application/SettlementService.java)).
- **Checagem de status com lock otimista** no recebível (`@Version` em [`ReceivableEntity`](backend/src/main/java/com/srmasset/creditengine/persistence/ReceivableEntity.java)) e transação única para o status e a liquidação.
- **Constraints no banco**: `settlements_receivable_unique` e `settlements_idempotency_key_unique` em [`V1__initial_schema.sql`](backend/src/main/resources/db/migration/V1__initial_schema.sql). Antes de criar a constraint em produção, as duplicatas existentes precisam ser tratadas, senão a migração falha.
- **Prova:** [`ConcurrentSettlementTest`](backend/src/test/java/com/srmasset/creditengine/web/ConcurrentSettlementTest.java) roda duas liquidações simultâneas contra PostgreSQL real e verifica que só uma é gravada.
- **No front,** a chave nasce no clique, o botão trava enquanto a requisição está pendente e o retry reusa a mesma chave ([`useSettle.ts`](frontend/src/features/pricing/useSettle.ts)).

Com isso, o mesmo cenário de 16h tem outro desfecho: o retry do gateway chega com a mesma chave e recebe a liquidação original, e o clique repetido do operador nem sai do navegador.

## Prevenção sistêmica

O critério é pegar essa classe de erro sem criar etapas que travem o time.

| Ação | Que lacuna fecha | Responsável | Estado neste repositório |
|---|---|---|---|
| Teste de concorrência e de repetição obrigatório para endpoint que move dinheiro | Bug chegou à produção | Time | Existe: [`ConcurrentSettlementTest`](backend/src/test/java/com/srmasset/creditengine/web/ConcurrentSettlementTest.java) e [`SettlementControllerTest`](backend/src/test/java/com/srmasset/creditengine/web/SettlementControllerTest.java), no CI ([`verify.yml`](.github/workflows/verify.yml)). |
| Unicidade no banco como última barreira | Bug de aplicação vira pagamento duplicado | Time | Existe: constraints em [`V1__initial_schema.sql`](backend/src/main/resources/db/migration/V1__initial_schema.sql). |
| Gateway não repete POST sem `Idempotency-Key` | Retry automático virou duplicata | Plataforma | Proposto: é configuração de infraestrutura, fora deste repositório. |
| Métrica de liquidação por resultado e alerta para `conflict` e `replayed` fora do normal | Detecção levou 2h20 e veio da mesa | Time | Métrica existe: `settlements_total` com tags `currency` e `result`, no dashboard do Grafana ([`credit-engine-operacao.json`](observability/grafana/provisioning/dashboards/json/credit-engine-operacao.json)). Alerta proposto: alertas ficaram fora do escopo da entrega 003. |
| Log estruturado de cada liquidação com `correlationId` | Sem rastro para investigar | Time | Existe: [`SettlementService`](backend/src/main/java/com/srmasset/creditengine/application/SettlementService.java) loga cada resultado e [`CorrelationIdFilter`](backend/src/main/java/com/srmasset/creditengine/web/CorrelationIdFilter.java) propaga o ID. |
| Checklist de cinco perguntas no PR que move dinheiro (transação, idempotência, arredondamento, erro nunca 2xx, taxa auditável) | Aprovação às pressas | Time | Proposto. Só para PRs financeiros, não para todos. |
| Runbook "liquidação duplicada": consulta de duplicatas, bloqueio no gateway, contato de pagamentos | Contenção dependeu de memória | Plantão | Proposto. Este documento serve de rascunho. |

## O que deu certo

- O relato da mesa foi preciso (cedentes e recebíveis), o que acelerou a confirmação.
- Bloquear a rota no gateway conteve o problema sem precisar de deploy.
- Não apagar as linhas duplicadas manteve a evidência e o registro para o estorno.

## Lições

- **Retry é inevitável; idempotência é o que torna o retry seguro.** Timeout no cliente não significa que o servidor não gravou.
- **O banco é a última barreira.** Uma constraint de unicidade teria transformado este incidente num erro 409 visível.
- **Se a mesa detecta antes do monitoramento, a observabilidade falhou.**
