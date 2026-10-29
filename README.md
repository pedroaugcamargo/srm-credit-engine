# SRM Credit Engine

Plataforma de cessão de crédito multimoedas (BRL/USD): precificação de recebíveis com deságio por risco e liquidação auditável. Desafio técnico SRM Asset, nível Staff/Tech Lead.

## Documentos

- [`SPEC.md`](SPEC.md): premissas, precisão numérica, perguntas ao negócio e critérios de aceite.
- [`docs/sdd/`](docs/sdd): especificação de cada entrega (spec driven development).
- [`CLAUDE.md`](CLAUDE.md): regras para agentes de IA neste repositório.

## Como rodar

Em construção (entrega 001).

## Fluxo de Git

GitHub Flow: a `main` está sempre estável e cada entrega nasce numa branch curta, entrando por Pull Request com descrição do porquê. Hooks versionados em `.githooks` bloqueiam commit e push direto na `main` e validam Conventional Commits. Para ativar após clonar:

```bash
git config core.hooksPath .githooks
```
