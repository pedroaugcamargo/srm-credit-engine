# SRM Credit Engine

Plataforma de cessão de crédito multimoedas (BRL/USD): precificação de recebíveis com deságio por risco e liquidação auditável. Desafio técnico SRM Asset, nível Staff/Tech Lead.

## Documentos

- [`SPEC.md`](SPEC.md): premissas, precisão numérica, perguntas ao negócio e critérios de aceite.
- [`docs/sdd/`](docs/sdd): especificação de cada entrega (spec driven development).
- [`CLAUDE.md`](CLAUDE.md): regras para agentes de IA neste repositório.

## Como rodar

É preciso ter o Docker. Na raiz do repositório:

```bash
docker compose up --build
```

Sobe o Postgres e o backend.

| O quê | Endereço |
|---|---|
| API | http://localhost:8080 |
| Documentação da API | http://localhost:8080/swagger-ui.html |
| OpenAPI | http://localhost:8080/v3/api-docs |

### Grafana

A observabilidade entra na rede `credit_engine`, que o compose da aplicação cria. Suba a aplicação antes.

```bash
docker compose -f docker-compose.observability.yml up
```

O Grafana abre sem senha.

| O quê | Endereço |
|---|---|
| Grafana | http://localhost:3000 |
| Dashboard | http://localhost:3000/d/credit-engine-operacao |

### Frontend

O painel do operador é a entrega 004 e ainda não está neste repositório. Não há endereço para abrir.

## Desempenho do extrato

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

## Fluxo de Git

GitHub Flow: a `main` está sempre estável e cada entrega nasce numa branch curta, entrando por Pull Request com descrição do porquê. Hooks versionados em `.githooks` bloqueiam commit e push direto na `main` e validam Conventional Commits. Para ativar após clonar:

```bash
git config core.hooksPath .githooks
```
