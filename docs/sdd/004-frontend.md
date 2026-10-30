# Entrega 004: Frontend do operador

- **Status:** pronta para execução
- **Branch:** `feat/004-frontend`
- **Base:** `SPEC.md`, seção 3 (API com valores em string) e critérios de usabilidade
- **Depende de:** entregas 002 e 003

## Objetivo

Painel do operador com simulação em tempo real e liquidação segura contra duplo clique, mais o histórico com paginação server-side. Mínimo e bem separado: a nota está no comportamento, não no visual.

## Fora de escopo

Autenticação, tema visual elaborado, estado global, testes end-to-end.

## Requisitos

- **R1.** React + TypeScript + Vite. TanStack Query para estado de servidor. Sem Redux nem contexto global: não há estado compartilhado que justifique.
- **R2.** O frontend nunca faz conta financeira e nunca converte dinheiro para `number`. Valores chegam e saem como string. Formatação com `Intl.NumberFormat` recebendo a string (sem `parseFloat`).
- **R3.** Simulação em tempo real: debounce de 300 ms e chave de query com os inputs, para que respostas antigas nunca sobrescrevam a simulação atual.
- **R4.** Liquidação segura: a `Idempotency-Key` é gerada com `crypto.randomUUID()` quando o operador clica em liquidar e reutilizada em qualquer retry da mesma tentativa. Botão desabilitado enquanto a requisição está pendente. Resposta `200` com `Idempotent-Replayed` mostra a mesma confirmação, sem erro.
- **R5.** Fluxo do painel: escolher cedente, informar valor, vencimento, tipo e moeda; ver a simulação; confirmar; o front registra o recebível e liquida.
- **R6.** Histórico consumindo o extrato da 003: filtros de período, cedente e moeda; paginação feita no servidor.
- **R7.** Erros de negócio exibidos com a mensagem do campo `detail` do ProblemDetail, em linguagem do operador.
- **R8.** Container com Nginx servindo o build e fazendo proxy de `/api` para o backend. Sem CORS: tudo na mesma origem. Em desenvolvimento, proxy do Vite.

## Estrutura

```
frontend/src
├── api            cliente HTTP, tipos dos contratos, tratamento de ProblemDetail
├── features
│   ├── pricing     hooks (useSimulation, useSettle) e componentes do painel
│   └── statement   hooks (useStatement) e componentes do histórico
├── components     peças visuais sem lógica (Money, ErrorMessage, Table)
└── lib            formatação de moeda e utilitários
```

Regra de separação: componentes só renderizam; regra, chamadas e estado ficam nos hooks.

## Tarefas (uma tarefa = um commit)

| # | Commit | O que entrega |
|---|---|---|
| T1 | `chore(frontend): scaffold react typescript with vite` | Projeto, ESLint, Vitest, proxy de dev |
| T2 | `feat(frontend): add api client and money formatting` | Cliente, tipos, leitura de ProblemDetail, `formatMoney(string, currency)`. Teste: formatação sem perda de precisão para valores grandes |
| T3 | `feat(frontend): add real-time pricing simulation` | Formulário, debounce, painel de resultado com VP, deságio e valor líquido |
| T4 | `feat(frontend): add idempotent settlement action` | Fluxo registrar e liquidar, chave de idempotência por tentativa, botão travado. Teste do hook: retry reutiliza a mesma chave |
| T5 | `feat(frontend): add settlement history with server-side pagination` | Grid, filtros e paginação |
| T6 | `build(frontend): serve with nginx in docker compose` | Dockerfile, configuração do Nginx, serviço no compose. `docker compose up --build` sobe tudo |

## Definition of done

- [ ] Nenhum `parseFloat`, `Number(` ou aritmética em valores monetários no código do front.
- [ ] Duplo clique em "Liquidar" gera uma única liquidação (verificado no extrato).
- [ ] O projeto inteiro sobe com um comando a partir de um clone limpo.

## Instruções para o agente de IA

- Não adicione biblioteca de estado global, de UI pesada ou de formulário sem perguntar.
- Não calcule nada financeiro no front, nem como "prévia".
- Uma tarefa por vez, com diff antes do commit.
