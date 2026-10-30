# ADR 0006: a liquidação lê o câmbio do banco, nunca do provedor

- **Status:** aceita
- **Decidida em:** entregas 002 e 003 (premissas A6 e A7 do [`SPEC.md`](../../SPEC.md) e "decisão central" de [`003-operacao-extrato.md`](../sdd/003-operacao-extrato.md))

## Contexto

Uma liquidação em USD converte o VP em BRL pela taxa de câmbio. O enunciado pergunta o que acontece se o provedor de taxa cair no meio de uma liquidação. Também exige que a liquidação registre a taxa efetivamente usada. O Anexo A busca a taxa "latest" no provedor dentro da liquidação e não guarda qual foi.

## Decisão

- **O provedor só alimenta o banco.** O provedor externo nunca é chamado durante a liquidação. Ele alimenta a tabela `fx_rates` por uma rotina de atualização:
  - `POST /api/v1/fx-rates/refresh` ou o agendamento, desligado por padrão;
  - com timeout, retry e circuit breaker do Resilience4j ([`ResilientExternalFxClient`](../../backend/src/main/java/com/srmasset/creditengine/infra/fx/ResilientExternalFxClient.java)).
  
  Uma falha do provedor nunca apaga nem altera uma taxa existente.
- **Taxa vigente no instante.** A liquidação usa a taxa com maior `valid_from` menor ou igual ao instante da liquidação (A6), selecionada em [`FxValidity`](../../backend/src/main/java/com/srmasset/creditengine/domain/fx/FxValidity.java).
- **Taxa velha é recusada.** Se a taxa vigente tem mais de 24h (configurável em `credit-engine.fx.max-age`), a liquidação em USD é recusada com `422 FX_RATE_STALE`. Não há fallback silencioso (A7).
- **Taxa registrada.** A liquidação grava `fx_rate_id` e `fx_rate`. A taxa nunca é alterada: atualizar significa inserir uma nova linha.

## Alternativas consideradas

- **Chamar o provedor dentro da liquidação (o que o Anexo A faz).**
  - A latência e a disponibilidade da liquidação passam a depender de um terceiro.
  - A taxa usada não fica registrada.
  - Duas liquidações no mesmo minuto podem usar taxas diferentes sem explicação.
- **Cache em memória com fallback para a última taxa conhecida.** Resolve a disponibilidade, mas aceita uma taxa velha em silêncio. Pagar com a taxa errada é perda financeira direta, e ninguém fica sabendo.
- **Aceitar qualquer taxa cadastrada, sem limite de idade.** É simples, mas sem uma rotina de atualização confiável vira o mesmo problema do fallback.

## Consequências

- **Positivas.**
  - A queda do provedor não afeta a liquidação. O pior caso é a taxa envelhecer e a liquidação em USD ser recusada de forma explícita, enquanto a liquidação em BRL segue normalmente ([`ProviderDownSettlementTest`](../../backend/src/test/java/com/srmasset/creditengine/web/ProviderDownSettlementTest.java)).
  - Toda liquidação em USD é reproduzível pela taxa gravada.
  - O comportamento está coberto em [`FxValidityTest`](../../backend/src/test/java/com/srmasset/creditengine/domain/fx/FxValidityTest.java) e [`StaleSettlementTest`](../../backend/src/test/java/com/srmasset/creditengine/web/StaleSettlementTest.java).
- **Negativas.**
  - **Frescor depende da rotina:** a liquidação em USD só funciona se alguém, ou o agendamento, mantiver a taxa atualizada.
  - **Demo trava em USD:** neste repositório o agendamento vem desligado e o seed tem taxa de 01/01/2026, então a demonstração em USD recebe `FX_RATE_STALE` até alguém registrar uma taxa nova. Além disso, o provedor mockado devolve uma vigência fixa (`credit-engine.fx.provider.valid-from`), e o refresh destrava só até essa vigência completar 24h.
  - **Painel sem atualização de câmbio:** o painel ainda não oferece ao operador uma ação para atualizar a taxa quando recebe `FX_RATE_STALE`.
  - **Limite de idade fixo:** 24h é um padrão configurável, não uma regra de negócio validada. A idade máxima aceitável é a pergunta 3 da seção 4 do [`SPEC.md`](../../SPEC.md).
