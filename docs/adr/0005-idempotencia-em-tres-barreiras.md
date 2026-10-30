# ADR 0005: idempotência da liquidação em três barreiras

- **Status:** aceita
- **Decidida em:** entrega 002 (premissa A11 do [`SPEC.md`](../../SPEC.md) e [`002-persistencia-cambio-liquidacao.md`](../sdd/002-persistencia-cambio-liquidacao.md))

## Contexto

Retry de rede, timeout no gateway e duplo clique são parte do uso normal. O código do Anexo A paga duas vezes nesses casos, e esse é o incidente do Anexo B ([`POSTMORTEM.md`](../../POSTMORTEM.md)). Há dois problemas distintos:

1. **Repetição da mesma tentativa:** a mesma requisição chega mais de uma vez.
2. **Tentativas diferentes do mesmo recebível,** às vezes ao mesmo tempo: dois operadores, ou duas abas.

Uma única proteção não cobre os dois casos, e qualquer proteção pode ter bug. A última linha precisa valer mesmo se o código errar.

## Decisão

Três barreiras independentes, cada uma cobrindo a falha da anterior. Tudo está implementado em [`SettlementService`](../../backend/src/main/java/com/srmasset/creditengine/application/SettlementService.java).

1. **Chave de idempotência, para a repetição.**
   - O header `Idempotency-Key` (UUID) é obrigatório; sem ele, a resposta é `400 IDEMPOTENCY_KEY_REQUIRED`.
   - O servidor guarda o SHA-256 do corpo canônico (`request_hash`).
   - Mesma chave e mesmo corpo devolvem a liquidação original com `200` e `Idempotent-Replayed: true`.
   - Mesma chave com corpo diferente é recusada com `422 IDEMPOTENCY_KEY_REUSED`.
2. **Status com lock otimista, para tentativas concorrentes.**
   - Recebível já `SETTLED` recebe `409`.
   - A mudança de status usa `@Version` com `saveAndFlush`, para que o conflito apareça cedo, dentro da transação.
3. **Constraints no banco, a última barreira.**
   - `UNIQUE (receivable_id)` e `UNIQUE (idempotency_key)` em `settlements`.
   - Duas requisições com a mesma chave, ao mesmo tempo, esbarram na unicidade da chave. A perdedora relê a original numa transação nova (`REQUIRES_NEW`) e devolve o replay.
   - Uma violação em `receivable_id` vira `409`.

**No cliente,** a chave nasce no clique em "Liquidar" e é reutilizada em todo retry da mesma tentativa, automático ou manual. O botão trava enquanto a requisição está pendente ([`useSettle.ts`](../../frontend/src/features/pricing/useSettle.ts)).

## Alternativas consideradas

- **Só checar o status.** Não resolve a corrida: duas requisições leem `OPEN` ao mesmo tempo, e as duas gravam.
- **Lock pessimista (`SELECT ... FOR UPDATE`).** Resolveria a corrida, mas segura locks durante a precificação. Sozinho, sem a chave, também não distingue um retry legítimo, que deve receber a liquidação original, de uma segunda tentativa, que deve receber `409`.
- **Deduplicar pelo corpo, sem chave.** Dois pedidos idênticos e legítimos seriam confundidos com um retry.
- **Só a constraint.** Protege o dinheiro, mas o retry legítimo receberia um erro em vez da confirmação, e o operador não saberia se pagou.

## Consequências

- **Positivas.**
  - O retry recebe a mesma resposta; a tentativa concorrente recebe `409`; nunca há duas linhas.
  - Provado contra PostgreSQL real com duas threads, tanto com chaves diferentes quanto com a mesma chave ([`ConcurrentSettlementTest`](../../backend/src/test/java/com/srmasset/creditengine/web/ConcurrentSettlementTest.java)).
  - O contrato está coberto em [`SettlementControllerTest`](../../backend/src/test/java/com/srmasset/creditengine/web/SettlementControllerTest.java).
- **Negativas.**
  - **Código mais complexo:** o fluxo tem caminhos para replay, conflito e corrida, e a detecção da constraint violada depende do nome dela na mensagem de erro.
  - **Chave guardada para sempre:** a chave fica na própria tabela de liquidações, sem expiração. Isso serve a este volume; em alta escala, a deduplicação passa a ser por partição e com TTL (`docs/SCALE.md`).
  - **Registro do recebível não é idempotente.** Um timeout no `POST /receivables` seguido de retry pode criar um recebível órfão em aberto. Não movimenta dinheiro, e foi aceito como corte (`DECISIONS.md`).
