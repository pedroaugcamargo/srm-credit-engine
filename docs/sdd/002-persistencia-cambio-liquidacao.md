# Entrega 002: Persistência, Currency Engine e liquidação idempotente

- **Status:** pronta para execução
- **Branch:** `feat/002-liquidacao`
- **Base:** `SPEC.md`, premissas A2, A4, A6, A7, A11, A12 e seção 3
- **Depende de:** entrega 001 (motor de precificação)

## Objetivo

Liquidar um recebível de forma atômica, idempotente, segura sob concorrência e auditável. É a entrega que mais pesa na avaliação: cada requisito aqui responde a um defeito do código do Anexo A.

## Fora de escopo

Integração externa de câmbio com resiliência, logs estruturados, métricas e extrato (entrega 003). Frontend (entrega 004). Estorno de liquidação (premissa A12).

## Requisitos

- **R1.** PostgreSQL no docker-compose, schema versionado com Flyway, `spring.jpa.hibernate.ddl-auto=validate`. Testes de integração com Testcontainers (Postgres real, nunca H2).
- **R2.** Taxa base e câmbio lidos do banco por data de vigência (A2, A6). As portas `BaseRateProvider` e `FxRateProvider` ganham implementação com banco, substituindo a fixa da 001.
- **R3.** Currency Engine: cadastrar nova taxa sempre cria uma nova linha. Taxa nunca é alterada.
- **R4.** Cadastro e consulta de recebíveis.
- **R5.** Liquidação numa única transação: ou grava tudo (liquidação e status do recebível), ou não grava nada.
- **R6.** Idempotência por `Idempotency-Key` conforme A11.
- **R7.** Três barreiras contra liquidação dupla: checagem de status, optimistic locking (`@Version`) e constraint UNIQUE no banco. Se uma falhar, a próxima segura.
- **R8.** A liquidação é o registro de auditoria: guarda o snapshot completo do cálculo (valores, taxa base, spread, prazo, ID e valor da taxa de câmbio, timestamps). O banco bloqueia UPDATE e DELETE nessa tabela.

## Modelo de dados

| Tabela | Colunas principais | Restrições |
|---|---|---|
| `cedentes` | `id uuid`, `nome`, `documento` | `documento` UNIQUE. Seed com 3 cedentes de demonstração |
| `receivables` | `id uuid`, `cedente_id`, `type`, `face_value NUMERIC(19,2)`, `due_date date`, `status` (`OPEN`, `SETTLED`), `version bigint`, `created_at timestamptz` | CHECK `face_value > 0` |
| `base_rates` | `id`, `rate NUMERIC(12,8)`, `valid_from timestamptz`, `created_at` | Seed: 0.01 |
| `fx_rates` | `id`, `base_currency`, `quote_currency`, `rate NUMERIC(18,8)`, `valid_from timestamptz`, `source`, `created_at` | CHECK `rate > 0`. Índice `(base_currency, quote_currency, valid_from DESC)` |
| `settlements` | `id uuid`, `receivable_id`, `cedente_id`, `idempotency_key uuid`, `request_hash char(64)`, `payment_currency`, `face_value`, `term_months NUMERIC(12,6)`, `base_rate`, `spread`, `present_value_brl`, `discount_brl`, `fx_rate_id`, `fx_rate`, `net_amount`, `settled_at timestamptz` | UNIQUE `receivable_id`, UNIQUE `idempotency_key`. Trigger que rejeita UPDATE e DELETE. Índices `(cedente_id, settled_at)` e `(payment_currency, settled_at)` para o extrato da 003 |

`cedente_id` é copiado para `settlements` de propósito: a liquidação precisa ser autossuficiente para auditoria e o extrato não precisa de join.

## Contratos

### Currency Engine

- `POST /api/v1/fx-rates` com `{ "base": "USD", "quote": "BRL", "rate": "5.4321", "validFrom": "2026-10-29T10:00:00-03:00" }` responde `201`.
- `GET /api/v1/fx-rates/current?base=USD&quote=BRL` responde `200` com a taxa vigente e o campo `stale: true|false`.

### Recebíveis

- `POST /api/v1/receivables` com `{ "cedenteId", "type", "faceValue", "dueDate" }` responde `201` com `Location`.
- `GET /api/v1/receivables/{id}` responde `200` ou `404`.

### Liquidação

`POST /api/v1/settlements`, header obrigatório `Idempotency-Key: <uuid>`, corpo `{ "receivableId": "...", "paymentCurrency": "USD" }`.

| Situação | Resposta |
|---|---|
| Liquidação criada | `201` com o snapshot completo e `Location` |
| Mesma chave, mesmo corpo (retry) | `200` com a liquidação original e header `Idempotent-Replayed: true` |
| Mesma chave, corpo diferente | `422 IDEMPOTENCY_KEY_REUSED` |
| Header ausente ou inválido | `400 IDEMPOTENCY_KEY_REQUIRED` |
| Recebível inexistente | `404 RECEIVABLE_NOT_FOUND` |
| Recebível já liquidado, ou conflito de concorrência | `409 RECEIVABLE_ALREADY_SETTLED` |
| Vencimento hoje ou passado | `422 INVALID_TERM` |
| Câmbio vencido (A7) | `422 FX_RATE_STALE` |
| Nenhuma taxa cadastrada | `422 FX_RATE_UNAVAILABLE` |

## Fluxo da liquidação

1. Calcular `request_hash` = SHA-256 do corpo canônico (`receivableId` + `paymentCurrency`).
2. Buscar por `idempotency_key`. Se existir: mesmo hash devolve a original (`200`); hash diferente devolve `422`.
3. Abrir transação. Carregar o recebível (`404` se não existir; `409` se `SETTLED`).
4. Resolver taxa base e câmbio vigentes no instante da liquidação (A6). Câmbio velho gera `422` (A7).
5. Precificar com o motor da 001.
6. Marcar o recebível como `SETTLED` com `saveAndFlush`, para o `@Version` falhar cedo em caso de conflito.
7. Inserir a liquidação com o snapshot. Commit.
8. Se o commit falhar por violação de UNIQUE em `idempotency_key` (duas requisições com a mesma chave ao mesmo tempo): em nova transação, reler pela chave e devolver a original. Violação em `receivable_id` ou `OptimisticLockException`: `409`.

## Design

```
com.srmasset.creditengine
├── web          controllers e DTOs (fx, receivables, settlements)
├── application  SettlementService (transação e fluxo), FxRateService, ReceivableService
├── domain       pricing (001), settlement (regras e snapshot), fx (regra de vigência e stale)
└── persistence  entidades JPA, repositórios, DbFxRateProvider, DbBaseRateProvider
```

Entidades JPA usam `@Column(precision = 19, scale = 2)` em dinheiro e nunca tipos primitivos para valores.

## Tarefas (uma tarefa = um commit)

| # | Commit | O que entrega |
|---|---|---|
| T1 | `build(backend): add postgres, flyway, jpa and testcontainers` | Dependências, Postgres no compose com healthcheck, backend esperando o banco (`depends_on: condition: service_healthy`) |
| T2 | `feat(db): add initial schema with immutable settlements` | Migrations do modelo acima, trigger de imutabilidade, seeds de cedentes, taxa base e câmbio |
| T3 | `feat(fx): add currency engine with validity and staleness` | Endpoints de câmbio, `DbFxRateProvider` com regra "vigente em", regra de 24h configurável. Testes da vigência e do stale |
| T4 | `feat(receivables): add receivable registration` | Endpoints de recebíveis com validação |
| T5 | `feat(settlement): add atomic settlement with pricing snapshot` | `SettlementService` com o fluxo dos passos 3 a 7 e mapeamento de erros. Teste: falha no insert não deixa o recebível como `SETTLED` |
| T6 | `feat(settlement): make settlement idempotent` | Passos 1, 2 e 8. Testes: retry devolve a mesma liquidação; chave reutilizada com outro corpo dá 422; continua existindo uma única linha |
| T7 | `test(settlement): prove concurrent settlements are rejected` | Teste com Testcontainers e `CountDownLatch`: duas threads com chaves diferentes liquidam o mesmo recebível ao mesmo tempo; resultado exato: uma `201`, uma `409`, uma linha. Segundo cenário com a mesma chave: uma linha só |
| T8 | `test(db): prove settlements are immutable` | Teste que tenta UPDATE e DELETE via `JdbcTemplate` e espera erro do banco |

## Definition of done

- [x] Golden cases da 001 continuam verdes.
- [x] Testes de concorrência e imutabilidade verdes contra Postgres real.
- [x] Nenhuma query concatenada com input.
- [x] `docker compose up --build` sobe banco e backend sem passo manual.
- [x] PR explica as três barreiras contra liquidação dupla e por que cada uma existe.

## Instruções para o agente de IA

- Uma tarefa por vez. Pare, mostre o diff e o resultado dos testes, aguarde aprovação.
- Não use H2 nem mocks de repositório nos testes de integração.
- Não engula exceção para "seguir o jogo". Toda falha vira erro mapeado ou propaga.
- Se a ordem de flush do JPA impedir o comportamento descrito no passo 6, pare e explique antes de contornar.
