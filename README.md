# SRM Credit Engine

Plataforma de cessão de crédito multimoedas (BRL/USD): precificação de recebíveis com deságio por risco e liquidação idempotente e auditável. Desafio técnico SRM Asset, nível Staff/Tech Lead ([enunciado](desafio-tecnico-srm-credit-engine-v2.md)).

O escopo de implementação foi reduzido de propósito: um núcleo pequeno e correto (motor, liquidação, auditoria e observabilidade), com o restante do esforço em decisões e documentos. O que ficou de fora e por quê está em [`DECISIONS.md`](DECISIONS.md).

## Como rodar

É preciso ter o Docker. Na raiz do repositório:

```bash
docker compose up --build
```

O comando sobe o postgres, o backend e o frontend.

| O quê | Endereço |
|---|---|
| Painel do operador | http://localhost:4173 |
| API | http://localhost:8080 |
| Documentação da API | http://localhost:8080/swagger-ui.html |
| OpenAPI | http://localhost:8080/v3/api-docs |

### Liquidar em USD na demonstração

A taxa de câmbio do seed é de 01/01/2026, e a atualização automática vem desligada. Por isso, uma liquidação em USD responde `422 FX_RATE_STALE`, porque a regra recusa taxa com mais de 24h ([ADR 0006](docs/adr/0006-cambio-lido-do-banco.md)). Para testar em USD, registre uma taxa vigente a partir de agora. O comando abaixo funciona no bash, no Linux, no macOS ou no Git Bash:

```bash
curl -X POST http://localhost:8080/api/v1/fx-rates -H "Content-Type: application/json" -d "{\"base\":\"USD\",\"quote\":\"BRL\",\"rate\":\"5.4321\",\"validFrom\":\"$(date -Iseconds)\"}"
```

A taxa vale por 24h. Liquidações em BRL não dependem de câmbio.

### Observabilidade

A observabilidade entra na rede `credit_engine`, que o compose da aplicação cria. Suba a aplicação antes:

```bash
docker compose -f docker-compose.observability.yml up
```

| O quê | Endereço |
|---|---|
| Grafana | http://localhost:3000 |
| Dashboard | http://localhost:3000/d/credit-engine-operacao |

O Grafana abre sem login, como administrador anônimo. Isso é um corte de escopo ([`DECISIONS.md`](DECISIONS.md), item 2).

### Frontend em desenvolvimento

No compose, o Nginx serve o build e faz proxy de `/api` para o backend na mesma origem, sem CORS.

Para desenvolver com recarga automática, suba só o postgres e o backend pelo compose e rode o Vite, que faz o mesmo proxy. O Vite usa a mesma porta do painel no compose, então o serviço `frontend` não pode estar rodando:

```bash
docker compose up -d postgres backend
```

```bash
cd frontend
npm install
npm run dev
```

O painel abre em http://localhost:4173.

## Testes

Os testes do backend rodam contra PostgreSQL real com Testcontainers, então precisam de Docker. Na pasta `backend`:

```bash
./mvnw verify
```

Na pasta `frontend`:

```bash
npm test
```

- **Golden cases.** C1, C2 e C3 do enunciado, ao centavo, em [`PricingEngineTest`](backend/src/test/java/com/srmasset/creditengine/domain/pricing/PricingEngineTest.java). O snapshot completo de uma liquidação em USD está em [`SettlementControllerTest`](backend/src/test/java/com/srmasset/creditengine/web/SettlementControllerTest.java).
- **Concorrência.** Duas liquidações simultâneas do mesmo recebível gravam exatamente uma, em [`ConcurrentSettlementTest`](backend/src/test/java/com/srmasset/creditengine/web/ConcurrentSettlementTest.java).
- **CI.** O workflow [`verify.yml`](.github/workflows/verify.yml) roda em todo pull request: checagem de formatação (Spotless) e `./mvnw verify` com Testcontainers. Os testes do frontend ainda não estão no pipeline.

## Stack e por quê

| Camada | Tecnologia | Por quê |
|---|---|---|
| Backend | Java 21, Spring Boot 4 | Tipagem forte e `BigDecimal` maduro para dinheiro. O ecossistema resolve o que um sistema financeiro precisa sem código próprio: transação (Spring), migração versionada (Flyway), ProblemDetail (RFC 9457) nativo, métricas (Micrometer) e OpenAPI (springdoc). |
| Precisão | `BigDecimal` e `big-math` | `BigDecimal` com `DECIMAL128` e uma única `RoundingPolicy`. `big-math` só para o expoente fracionário do prazo ACT/30 ([ADR 0004](docs/adr/0004-bigdecimal-half-even-rounding-policy.md)). |
| Banco | PostgreSQL 18 | ACID para a liquidação. Constraints únicas, checks e o trigger de imutabilidade como última barreira, e `NUMERIC` para valores exatos ([ADR 0001](docs/adr/0001-postgresql-relacional.md)). |
| Resiliência | Resilience4j | Timeout, retry e circuit breaker na integração (mockada) de câmbio, fora do caminho da liquidação ([ADR 0006](docs/adr/0006-cambio-lido-do-banco.md)). |
| Testes | JUnit, Testcontainers, Vitest, Testing Library | Banco real nos testes de integração, nunca H2, porque H2 não reproduz o trigger nem os erros de constraint. Hooks do front testados com relógio falso para o debounce e o retry. |
| Frontend | React 19, TypeScript, Vite, TanStack Query | Tipos nos contratos da API. O TanStack Query cuida do estado de servidor: a chave da query são os próprios inputs, para uma resposta antiga nunca sobrescrever a simulação atual. Não há estado global, porque não há estado compartilhado que o justifique. O front nunca faz conta financeira. |
| Servidor do front | Nginx (`nginx-unprivileged`, sem root) | Serve o build e faz proxy de `/api` na mesma origem, sem CORS. |
| Observabilidade | Prometheus, Loki, Alloy, Grafana | Stack aberta que roda local, com métricas de negócio, logs estruturados com `correlationId` e um dashboard de operação versionado. |

## Desenho em uma página

- **Monolito modular** em camadas: `web`, `application`, `domain`, `persistence` e `infra`. Uma transação local cobre a liquidação inteira ([ADR 0002](docs/adr/0002-monolito-modular.md), [C4](docs/architecture.md)).
- **Motor de precificação com Strategy** por tipo de recebível. O registro de strategies falha na inicialização se algum tipo ficar sem regra.
- **Liquidação idempotente em três barreiras:** `Idempotency-Key` com hash do payload, status com lock otimista e constraints únicas no banco ([ADR 0005](docs/adr/0005-idempotencia-em-tres-barreiras.md)).
- **Câmbio lido do banco por vigência,** nunca do provedor durante a liquidação. Taxa com mais de 24h é recusada de forma explícita ([ADR 0006](docs/adr/0006-cambio-lido-do-banco.md)).
- **Liquidação imutável:** o snapshot completo do cálculo é o registro de auditoria, e o banco bloqueia UPDATE e DELETE.
- **Liquidação síncrona hoje,** com outbox e eventos como evolução ([ADR 0003](docs/adr/0003-liquidacao-sincrona-outbox-como-evolucao.md), [EDA](docs/EDA.md)).

## Documentos

| Documento | Conteúdo |
|---|---|
| [`SPEC.md`](SPEC.md) | Premissas para as ambiguidades do enunciado, precisão numérica, perguntas ao negócio e critérios de aceite |
| [`docs/sdd/`](docs/sdd) | Especificação de cada entrega (spec driven development), uma tarefa por commit |
| [`REVIEW.md`](REVIEW.md) | Code review do endpoint do Anexo A, em ordem de severidade |
| [`POSTMORTEM.md`](POSTMORTEM.md) | Post-mortem do incidente de liquidações duplicadas (Anexo B) |
| [`docs/adr/`](docs/adr) | Seis ADRs: PostgreSQL, monolito modular, liquidação síncrona e outbox, `BigDecimal`, idempotência e câmbio |
| [`docs/architecture.md`](docs/architecture.md) | Diagramas C4, níveis 1 e 2 |
| [`docs/SCALE.md`](docs/SCALE.md) | Desenho para 1 milhão de transações por minuto |
| [`docs/EDA.md`](docs/EDA.md) | Arquitetura orientada a eventos para a liquidação |
| [`DECISIONS.md`](DECISIONS.md) | O que foi cortado, por quê e qual risco foi aceito |
| [`AI_USAGE.md`](AI_USAGE.md) | Como a IA foi usada, erros dela e como foram detectados, e o que não foi delegado |
| [`CLAUDE.md`](CLAUDE.md) | Regras para agentes de IA neste repositório |

## Onde está cada requisito do enunciado

| Requisito | Onde |
|---|---|
| Golden cases ao centavo | [`PricingEngineTest`](backend/src/test/java/com/srmasset/creditengine/domain/pricing/PricingEngineTest.java) |
| Strategy por tipo de recebível | [`domain/pricing/strategy`](backend/src/main/java/com/srmasset/creditengine/domain/pricing/strategy), [`PricingStrategyRegistry`](backend/src/main/java/com/srmasset/creditengine/domain/pricing/PricingStrategyRegistry.java) |
| Câmbio com vigência | [`FxRateController`](backend/src/main/java/com/srmasset/creditengine/web/FxRateController.java), [`FxValidity`](backend/src/main/java/com/srmasset/creditengine/domain/fx/FxValidity.java) |
| Liquidação ACID, idempotente e imutável | [`SettlementService`](backend/src/main/java/com/srmasset/creditengine/application/SettlementService.java), [`V1__initial_schema.sql`](backend/src/main/resources/db/migration/V1__initial_schema.sql) |
| Optimistic locking com teste de conflito | [`ConcurrentSettlementTest`](backend/src/test/java/com/srmasset/creditengine/web/ConcurrentSettlementTest.java) |
| Extrato com SQL nativo e filtros | [`SettlementStatementRepository`](backend/src/main/java/com/srmasset/creditengine/persistence/SettlementStatementRepository.java) |
| Erros globais em ProblemDetail | [`ApiExceptionHandler`](backend/src/main/java/com/srmasset/creditengine/web/ApiExceptionHandler.java) |
| Logs estruturados e métricas de negócio | [`SettlementService`](backend/src/main/java/com/srmasset/creditengine/application/SettlementService.java), [`CorrelationIdFilter`](backend/src/main/java/com/srmasset/creditengine/web/CorrelationIdFilter.java), [dashboard](observability/grafana/provisioning/dashboards/json/credit-engine-operacao.json) |
| Timeout, retry e circuit breaker no câmbio | [`ResilientExternalFxClient`](backend/src/main/java/com/srmasset/creditengine/infra/fx/ResilientExternalFxClient.java) |
| Painel com simulação em tempo real | [`features/pricing`](frontend/src/features/pricing) |
| Grid com paginação server-side | [`features/statement`](frontend/src/features/statement) |
| Code review (Anexo A) e post-mortem (Anexo B) | [`REVIEW.md`](REVIEW.md), [`POSTMORTEM.md`](POSTMORTEM.md) |
| ADRs, alta escala, EDA e C4 | [`docs/adr/`](docs/adr), [`docs/SCALE.md`](docs/SCALE.md), [`docs/EDA.md`](docs/EDA.md), [`docs/architecture.md`](docs/architecture.md) |

## Desempenho

Medição local em 30/10/2026. Postgres 18 no Docker, via Testcontainers. A primeira página de `GET /api/v1/settlements` leu 100 mil liquidações (página 0, tamanho 20, período de 1º a 3 de janeiro de 2026). Foram 20 chamadas de aquecimento e 100 medições. O p95 é a 95ª amostra depois de ordenar.

| | Tempo |
|---|---|
| mínimo | 73 ms |
| p95 | 85 ms |
| máximo | 102 ms |

O critério do SPEC é p95 abaixo de 500 ms. O teste `SettlementStatementLoadTest` não entra no CI: a entrega 003 deixa teste de carga automatizado fora do pipeline. Para repetir, na pasta `backend`:

```bash
./mvnw -Dtest=SettlementStatementLoadTest -Dsurefire.excludedGroups=none test
```

As metas do SPEC para a simulação (p95 abaixo de 100 ms) e para a liquidação (p95 abaixo de 300 ms) **não foram medidas**. O teste de carga ficou fora do escopo ([`DECISIONS.md`](DECISIONS.md), item 5), e é o primeiro passo do [desenho de escala](docs/SCALE.md).

## Fluxo de Git

**GitHub Flow.** A `main` está sempre estável. Cada entrega nasce numa branch curta (`feat/001-motor-precificacao`, `docs/005-decisoes`, …) e entra por pull request, com commits no padrão Conventional Commits.

**Por que serve a este contexto:**
- **Uma pessoa, entregas curtas e em sequência.** Cada spec de [`docs/sdd/`](docs/sdd) virou uma branch e um PR, revisável de ponta a ponta e com uma tarefa por commit.
- **Por que não Git Flow.** Não há versões paralelas para manter nem um ciclo de release que justifique `develop` e branches de release. Seriam merges a mais sem benefício.
- **Por que não trunk-based.** Trunk-based funciona bem com feature flags e um CI rápido protegendo cada commit na `main`. Aqui não há feature flags, e o PR é o ponto em que reviso o diff inteiro da entrega antes do merge, que é onde peguei boa parte dos erros da IA ([`AI_USAGE.md`](AI_USAGE.md)).
- **Se o time crescer,** o GitHub Flow continua servindo, e a evolução natural é para trunk-based com feature flags.

Hooks versionados em [`.githooks`](.githooks) bloqueiam commit e push direto na `main` e validam Conventional Commits. Para ativar depois de clonar:

```bash
git config core.hooksPath .githooks
```
