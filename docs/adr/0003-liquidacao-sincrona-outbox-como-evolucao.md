# ADR 0003: liquidação síncrona hoje, eventos via outbox como evolução

- **Status:** aceita
- **Decidida em:** entrega 002 ([`002-persistencia-cambio-liquidacao.md`](../sdd/002-persistencia-cambio-liquidacao.md))

## Contexto

O operador confirma a liquidação no painel e precisa saber na hora se ela foi gravada, com quanto e com qual taxa. Neste case, a liquidação termina no registro: não há integração com um sistema de pagamentos real. A meta de desempenho é p95 abaixo de 300 ms na liquidação (seção 5 do [`SPEC.md`](../../SPEC.md)).

Num sistema real, depois da liquidação vêm a instrução de pagamento, a confirmação do banco e a notificação ao cedente. Esses passos envolvem sistemas externos, lentos e sujeitos a falha.

## Decisão

**Hoje:** liquidação síncrona numa única transação local. O `POST /api/v1/settlements` responde `201` com o snapshot completo ou falha com um erro mapeado, nunca pela metade ([`SettlementService`](../../backend/src/main/java/com/srmasset/creditengine/application/SettlementService.java)).

**Evolução, quando houver integração com pagamentos:** transactional outbox.
1. Na mesma transação da liquidação, gravar o evento (`SettlementCompleted`) numa tabela `outbox`.
2. Um relay publica o outbox no broker (por exemplo, Kafka) e marca o que foi publicado.
3. Os consumidores (pagamentos, notificação, extrato) são idempotentes pelo ID do evento.

O fluxo completo, com saga e estorno, está em `docs/EDA.md`.

## Alternativas consideradas

- **Liquidação assíncrona desde o início (a API só enfileira).** O operador receberia "pedido aceito" e teria de acompanhar o resultado depois. Erros de negócio, como câmbio vencido ou recebível já liquidado, chegariam fora da tela onde a decisão foi tomada. A idempotência ficaria mais difícil de explicar ao operador. Não há volume que justifique.
- **Gravar no banco e publicar no broker na mesma requisição (dual write).** Se o banco grava e o broker falha, ou o contrário, os dois lados ficam inconsistentes sem ninguém saber. É o mesmo tipo de defeito do Anexo A: duas escritas sem atomicidade.
- **Chamar o sistema de pagamentos dentro da transação.** Manteria a transação aberta durante uma chamada de rede lenta, segurando locks, e uma falha de rede depois do commit do outro lado deixaria estados divergentes.

## Consequências

- **Positivas.**
  - O operador recebe a confirmação ou o erro na hora.
  - Uma única fonte da verdade, sem estado intermediário.
  - O fluxo é testável de ponta a ponta com um banco real.
- **Negativas.**
  - **Acoplamento ao banco:** a liquidação depende do banco estar disponível no momento do clique. Não há fila para absorver um pico.
  - **Evolução não implementada:** o outbox é um desenho, não código. Quando a integração com pagamentos entrar, a migração precisa incluir a tabela, o relay, a deduplicação nos consumidores e o estorno como evento de compensação (premissa A12).
