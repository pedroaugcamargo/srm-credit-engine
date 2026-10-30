# Arquitetura (C4)

Diagramas nos níveis 1 (contexto) e 2 (containers) do modelo C4, em Mermaid. Linha contínua é o que existe e roda hoje; linha tracejada é integração externa que ainda não existe. As decisões por trás do desenho estão nos [ADRs](adr/).

## Nível 1: contexto

Quem usa o sistema e com quais sistemas externos ele conversa.

```mermaid
flowchart TB
    op[<b>Operador da mesa</b><br/>[Pessoa]<br/>Simula, liquida recebíveis<br/>e consulta o histórico]

    srm[<b>SRM Credit Engine</b><br/>[Sistema]<br/>Precifica recebíveis com deságio por risco,<br/>liquida em BRL ou USD de forma idempotente<br/>e mantém o registro auditável]

    fx[<b>Provedor de câmbio</b><br/>[Sistema externo]<br/>Cotação USD/BRL]
    pay[<b>Sistema de pagamentos</b><br/>[Sistema externo]<br/>Executa o pagamento ao cedente]

    op -- "Usa pelo navegador<br/>[HTTPS]" --> srm
    srm -- "Busca cotações na rotina de atualização,<br/>nunca durante a liquidação" --> fx
    srm -. "Instrui pagamentos<br/>(evolução, ver EDA.md)" .-> pay

    classDef person fill:#08427b,stroke:#052e56,color:#fff
    classDef system fill:#1168bd,stroke:#0b4884,color:#fff
    classDef external fill:#999,stroke:#6b6b6b,color:#fff
    class op person
    class srm system
    class fx,pay external
```

| Elemento | O que é hoje |
|---|---|
| Operador | Único usuário. Não há autenticação nesta versão (corte registrado em `DECISIONS.md`). |
| Provedor de câmbio | **Mockado dentro da API** ([`ConfigurableExternalFxClient`](../backend/src/main/java/com/srmasset/creditengine/infra/fx/ConfigurableExternalFxClient.java)). Simula latência e falha por configuração, atrás de timeout, retry e circuit breaker ([`ResilientExternalFxClient`](../backend/src/main/java/com/srmasset/creditengine/infra/fx/ResilientExternalFxClient.java)). O cliente real entraria no lugar do mock sem mudar quem o usa. |
| Sistema de pagamentos | **Não integrado.** A liquidação termina no registro ([ADR 0003](adr/0003-liquidacao-sincrona-outbox-como-evolucao.md)). A integração por eventos está desenhada em [`EDA.md`](EDA.md). |

## Nível 2: containers

O que roda, em qual tecnologia, e como as peças se falam. São dois arquivos do compose: [`docker-compose.yml`](../docker-compose.yml) com a aplicação e [`docker-compose.observability.yml`](../docker-compose.observability.yml) com a observabilidade, que entra na rede `credit_engine` criada pelo primeiro.

```mermaid
flowchart TB
    op[<b>Operador</b><br/>[Pessoa]]

    subgraph app[SRM Credit Engine — docker-compose.yml]
        spa[<b>Painel do operador</b><br/>[Container: React + TypeScript, Nginx]<br/>Simulação em tempo real, liquidação<br/>e histórico. Nginx serve o build e faz<br/>proxy de /api na mesma origem]
        api[<b>API</b><br/>[Container: Java 21, Spring Boot]<br/>Motor de precificação, câmbio,<br/>liquidação idempotente e extrato]
        db[(<b>Banco</b><br/>[Container: PostgreSQL 18]<br/>Recebíveis, taxas versionadas,<br/>liquidações imutáveis)]
    end

    subgraph obs["Observabilidade — docker-compose.observability.yml]
        prom[<b>Prometheus</b><br/>[Container]<br/>Métricas]
        alloy[<b>Alloy</b><br/>[Container]<br/>Coleta de logs dos containers]
        loki[<b>Loki</b><br/>[Container]<br/>Logs]
        graf[<b>Grafana</b><br/>[Container]<br/>Dashboard credit-engine-operacao]
    end

    fx["<b>Provedor de câmbio</b><br/>[Sistema externo, mockado na API]"]
    pay["<b>Sistema de pagamentos</b><br/>[Sistema externo, não integrado]"]

    op -- "HTTPS<br/>:4173" --> spa
    spa -- "JSON /api/v1<br/>proxy do Nginx" --> api
    api -- "JDBC<br/>Flyway, JPA, SQL nativo" --> db
    api -- "Rotina de atualização<br/>Resilience4j" --> fx
    api -. "evolução: outbox e eventos" .-> pay

    prom -- "scrape /actuator/prometheus<br/>a cada 30s" --> api
    alloy -- "lê logs JSON<br/>pelo socket do Docker" --> api
    alloy -- "push" --> loki
    graf -- "consulta" --> prom
    graf -- "consulta" --> loki
    op -- "HTTP :3000" --> graf

    classDef person fill:#08427b,stroke:#052e56,color:#fff
    classDef container fill:#438dd5,stroke:#2e6295,color:#fff
    classDef store fill:#438dd5,stroke:#2e6295,color:#fff
    classDef obsc fill:#85bbf0,stroke:#5d82a8,color:#000
    classDef external fill:#999,stroke:#6b6b6b,color:#fff
    class op person
    class spa,api container
    class db store
    class prom,alloy,loki,graf obsc
    class fx,pay external
```

| Container | Tecnologia | Responsabilidade | Onde |
|---|---|---|---|
| Painel do operador | React 19, TypeScript, Vite, TanStack Query; servido por Nginx 1.30 (sem root) | Formulário com simulação em tempo real, liquidação com chave de idempotência por tentativa, histórico com paginação no servidor. Não faz conta financeira. | [`frontend/`](../frontend/), porta 4173 |
| API | Java 21, Spring Boot 4, `BigDecimal` com `big-math`, Resilience4j, Micrometer | Precificação (Strategy por tipo de recebível), câmbio por vigência, liquidação atômica e idempotente, extrato com SQL nativo, erros em ProblemDetail, OpenAPI | [`backend/`](../backend/), porta 8080 (`/swagger-ui.html`) |
| Banco | PostgreSQL 18, schema versionado por Flyway | Fonte da verdade. Constraints únicas, checks de domínio e trigger que torna a liquidação imutável | [`V1__initial_schema.sql`](../backend/src/main/resources/db/migration/V1__initial_schema.sql), porta 5432 |
| Prometheus | Prometheus 3.5 | Coleta `settlements_total`, `pricing_engine_duration`, `fx_provider_calls_total` e o estado do circuit breaker | [`prometheus.yml`](../observability/prometheus.yml) |
| Alloy | Grafana Alloy 1.7 | Lê os logs dos containers pelo socket do Docker, descarta os da própria observabilidade e extrai o `level` do JSON | [`config.alloy`](../observability/config.alloy) |
| Loki | Loki 3.4 | Guarda os logs estruturados. O `correlationId` fica no corpo do log, não vira label | [`loki-config.yaml`](../observability/loki-config.yaml) |
| Grafana | Grafana 11.5 | Dashboard de operação com liquidações por minuto, p95 do motor, chamadas ao provedor, circuit breaker e logs | [`credit-engine-operacao.json`](../observability/grafana/provisioning/dashboards/json/credit-engine-operacao.json), porta 3000 |

## Fluxos principais

- **Simulação.** Painel → API: `POST /api/v1/pricing/simulations`, chamado 300 ms depois que o operador para de digitar. A API lê a taxa base e o câmbio vigentes e responde com valores em string. Nada é gravado.
- **Liquidação.** Painel → API: `POST /api/v1/receivables` e depois `POST /api/v1/settlements` com `Idempotency-Key`. Numa transação, a API marca o recebível, grava a liquidação com o snapshot do cálculo e responde `201`. Um retry com a mesma chave recebe a liquidação original ([ADR 0005](adr/0005-idempotencia-em-tres-barreiras.md)).
- **Atualização de câmbio.** `POST /api/v1/fx-rates/refresh` ou o agendamento (desligado por padrão) chama o provedor e grava uma nova linha em `fx_rates`. A liquidação só lê do banco ([ADR 0006](adr/0006-cambio-lido-do-banco.md)).
- **Extrato.** Painel → API: `GET /api/v1/settlements` com período, cedente, moeda e página. É SQL nativo parametrizado sobre os índices `(cedente_id, settled_at)` e `(payment_currency, settled_at)`.
- **Observabilidade.** A observabilidade só observa: a API não depende dela, e derrubar o Prometheus ou o Loki não afeta a liquidação. O `X-Correlation-Id` liga a requisição aos logs no Loki ([`CorrelationIdFilter`](../backend/src/main/java/com/srmasset/creditengine/web/CorrelationIdFilter.java)).

## Para onde evolui

O desenho para volume alto (cache de taxas, particionamento por cedente, outbox com Kafka, extrato como read model) está em [`SCALE.md`](SCALE.md), e o fluxo de eventos com pagamentos em [`EDA.md`](EDA.md).
