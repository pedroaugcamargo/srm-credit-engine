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

## Fluxo de Git

GitHub Flow: a `main` está sempre estável e cada entrega nasce numa branch curta, entrando por Pull Request com descrição do porquê. Hooks versionados em `.githooks` bloqueiam commit e push direto na `main` e validam Conventional Commits. Para ativar após clonar:

```bash
git config core.hooksPath .githooks
```
