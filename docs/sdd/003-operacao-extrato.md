# Entrega 003: Operação, resiliência e extrato

- **Status:** pronta para execução
- **Branch:** `feat/003-operacao-extrato`
- **Base:** `SPEC.md`, premissas A6 e A7, critérios de desempenho
- **Depende de:** entrega 002

## Objetivo

Deixar o sistema operável: saber o que está acontecendo (logs e métricas consultáveis no Grafana), sobreviver à queda do provedor de câmbio e entregar o extrato analítico com SQL otimizado.

## Decisão central desta entrega

A liquidação **nunca chama o provedor de câmbio**. Ela lê a taxa vigente gravada no banco (A6). O provedor externo só alimenta o banco por uma rotina de atualização. Consequência: se o provedor cair no meio de uma liquidação, a liquidação não é afetada. O único efeito possível é a taxa ficar velha, e aí a regra A7 recusa liquidações em USD com `422 FX_RATE_STALE`, de forma explícita. Esta é a resposta para a pergunta do enunciado sobre o provedor cair no meio da liquidação.

## Fora de escopo

Alertas (Alertmanager, regras, incident.io). Teste de carga automatizado no CI. redis-exporter, node-exporter, cAdvisor e postgres-exporter. Traefik, domínio público e qualquer senha de outro repositório.

## Requisitos

- **R1.** Cliente do provedor externo (`ExternalFxClient`) atrás de uma interface, com implementação mockada que simula latência e falha por configuração.
- **R2.** Resiliência com Resilience4j no cliente: timeout de 2 s, retry de até 2 tentativas com backoff exponencial apenas para timeout e 5xx, circuit breaker abrindo com 50% de falha em janela de 10 chamadas.
- **R3.** Atualização do câmbio por `POST /api/v1/fx-rates/refresh` e por agendamento configurável (desligado por padrão). Falha do provedor nunca apaga nem altera taxa existente.
- **R4.** Logs estruturados em JSON (logging estruturado nativo do Spring Boot) com `correlationId` vindo do header `X-Correlation-Id` ou gerado na entrada. Eventos de liquidação logados com IDs e resultado, sem dados pessoais do cedente.
- **R5.** Métricas Micrometer expostas em `/actuator/prometheus`:
  - `settlements_total` (counter, tags `currency` e `result`: `created`, `replayed`, `conflict`, `rejected`). Liquidações por minuto saem de `rate()` sobre esse contador. O meter no código não se chama `settlements_total`: o Micrometer acrescenta o sufixo `_total` na exportação, e repetir o sufixo no nome do meter duplica (`settlements_total_total`). O mesmo vale para `fx_provider_calls_total`.
  - `pricing_engine_duration` (timer com percentis).
  - `fx_provider_calls_total` (tags `outcome`) e estado do circuit breaker (gauge do Resilience4j).
- **R6.** Extrato: `GET /api/v1/settlements?from&to&cedenteId&currency&page&size`, implementado com `NamedParameterJdbcTemplate` e SQL nativo parametrizado, usando os índices criados na 002. Período obrigatório, no máximo 366 dias. `size` máximo 100.
- **R7.** CI ganha Testcontainers (Docker do runner) e verificação de formatação com Spotless.
- **R8.** Stack local de observabilidade, no padrão de compose separado com imagens pinadas, limite de memória e retenção curta. A liquidação não fala com essa stack: ela só observa. Arquivos:
  - `docker-compose.yml` da aplicação declara a rede nomeada `credit_engine`.
  - `docker-compose.observability.yml` com `name: credit-engine-observability`, entrando em `credit_engine` como rede externa. Sobe depois da aplicação.
  - `observability/prometheus.yml`: Prometheus `prom/prometheus:v3.5.0`, scrape de `backend:8080` em `/actuator/prometheus`, intervalo 30s, retenção 7d / 2GB, limite 512m. Sem `rule_files` e sem Alertmanager.
  - `observability/loki-config.yaml`: Loki `grafana/loki:3.4.2`, filesystem, retenção 30d, config enxuta, limite 320m.
  - `observability/config.alloy`: Alloy `grafana/alloy:v1.7.5`, lê o socket do Docker, descarta logs dos containers da própria stack, extrai `level` do JSON. `correlationId` fica no corpo do log e não vira label. Limite 160m.
  - Grafana `grafana/grafana:11.5.2` (limite 256m). Signup desligado. Senha só por `GF_SECURITY_ADMIN_PASSWORD` (valor local de laboratório, não copiado de outro projeto). Datasources Prometheus e Loki versionados em `observability/grafana/provisioning/datasources/`. Dashboard `credit-engine-operacao` versionado em `observability/grafana/provisioning/dashboards/json/`, com:
    - liquidações por minuto: `sum by (currency, result) (rate(settlements_total[1m]))`
    - p95 de `pricing_engine_duration` (série de percentil do timer, em segundos)
    - chamadas ao provedor: `sum by (outcome) (rate(fx_provider_calls_total[5m]))`
    - estado do circuit breaker (gauge do Resilience4j pedido no R5)
    - logs do backend no Loki, filtráveis por `correlationId` no JSON

## Contrato do extrato

Response `200`:

```json
{
  "items": [
    {
      "id": "...",
      "cedenteId": "...",
      "cedenteNome": "...",
      "receivableId": "...",
      "paymentCurrency": "USD",
      "faceValue": "100000.00",
      "presentValueBrl": "92859.94",
      "fxRate": "5.4321",
      "netAmount": "17094.67",
      "settledAt": "2026-10-29T14:03:11-03:00"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1
}
```

Erros: `400` para período ausente, invertido ou maior que 366 dias.

## Tarefas (uma tarefa = um commit)

| # | Commit | O que entrega |
|---|---|---|
| T1 | `feat(fx): add external fx client behind resilience4j` | Interface, mock configurável, timeout, retry, circuit breaker. Testes: timeout aciona retry; circuit abre após falhas; falha não altera taxa existente |
| T2 | `feat(fx): add fx refresh endpoint and optional schedule` | Refresh manual e agendado. Teste: provedor fora com taxa velha resulta em liquidação USD recusada com 422 |
| T3 | `feat(obs): add structured logs with correlation id` | Filtro de correlation id, MDC, configuração JSON. Teste do header propagado |
| T4 | `feat(obs): add business metrics` | Os três grupos de métricas de R5. Teste que verifica incremento do contador por resultado |
| T5 | `feat(obs): add local grafana stack` | Compose de observabilidade, Prometheus, Loki, Alloy e dashboard provisionado do R8. A rede `credit_engine` entra no compose da aplicação |
| T6 | `feat(report): add settlement statement with native sql` | Extrato com filtros e paginação. Teste com dados de vários cedentes e moedas |
| T7 | `ci: add testcontainers and spotless check` | Pipeline completo |

## Definition of done

- [ ] Derrubar o mock do provedor não quebra nenhuma liquidação em BRL e gera 422 claro em USD quando a taxa fica velha.
- [ ] `/actuator/prometheus` mostra as métricas de negócio após uma liquidação.
- [ ] Com a aplicação já na rede `credit_engine` e a stack de observabilidade no ar, a mesma liquidação aparece no dashboard `credit-engine-operacao`, e o log dela é achável no Grafana pelo `correlationId`.
- [ ] Extrato sem ORM, sem query concatenada, respondendo dentro do critério do SPEC com 100 mil linhas (medição manual registrada no README).

## Instruções para o agente de IA

- Não faça a liquidação chamar o provedor externo, mesmo como fallback. Essa é uma decisão de arquitetura (ver ADR de câmbio).
- Retry só em operações idempotentes de leitura do provedor. Nunca aplique retry na liquidação.
- A stack de observabilidade só observa. Não ligue a liquidação ao Grafana, ao Prometheus nem ao Loki.
- Não copie do outro repositório: Alertmanager, regras de alerta, incident.io, Traefik, redis-exporter, node-exporter, cAdvisor, postgres-exporter, nem senha de admin. `correlationId` não vira label do Loki.
- Uma tarefa por vez, com diff e testes antes do commit.
