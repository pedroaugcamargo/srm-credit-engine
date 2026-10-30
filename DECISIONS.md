# Decisões de escopo

Reduzi o escopo de implementação de propósito, como o nível Staff permite. Investi num núcleo pequeno e correto (motor de precificação, liquidação idempotente, auditoria e observabilidade) e nos documentos de decisão. Abaixo está o que deixei de fora, por que e qual risco aceitei.

> **Tudo o que está listado aqui é obrigatório antes de ir para produção.** São cortes para o contexto de um teste técnico, não decisões de produto.

## Decisões que tomei

| # | O que ficou de fora | Por quê | Risco aceito | O que fazer em produção |
|---|---|---|---|---|
| 1 | **Autenticação.** Todas as APIs estão abertas, e o painel não tem login. | Facilitar o acesso de quem avalia o teste e manter o foco no backend e na observabilidade. | Qualquer pessoa com acesso à rede pode simular, liquidar e ver o extrato. | Autenticação no painel e na API (OIDC, por exemplo), autorização por perfil (quem pode liquidar, quem só consulta) e o operador gravado em cada liquidação, para auditoria. |
| 2 | **Senha no Grafana.** O Grafana abre sem login, com acesso anônimo de administrador. | Abrir o dashboard direto, sem passo extra na avaliação. | Qualquer um com acesso à porta 3000 vê os logs e as métricas e pode alterar os dashboards. | Login obrigatório, perfis de acesso e nenhum acesso anônimo. |
| 3 | **Visual do frontend.** Não há CSS, biblioteca de componentes nem layout elaborado. | O objetivo era o mais simples possível na tela, com integração forte com o backend: simulação em tempo real, liquidação segura contra duplo clique e paginação no servidor. | A tela é crua, e a usabilidade para uso diário da mesa não foi trabalhada. | Layout, design system, acessibilidade e testes de usabilidade com a mesa. |
| 4 | **Alertas.** Não há envio de alerta para incident.io, Slack ou similar, nem regras de alerta no Prometheus. | Não era o objetivo do teste. As métricas e os logs existem e estão no Grafana. | Um problema só é visto se alguém olhar o dashboard. No incidente do Anexo B, quem detectou foi a mesa ([`POSTMORTEM.md`](POSTMORTEM.md)). | Regras de alerta para taxa de câmbio vencida, falhas do provedor, aumento de `conflict` e `replayed` nas liquidações e latência fora da meta, com roteamento para o plantão. |
| 5 | **Teste de carga.** Não medi quanto o sistema aguenta. | Não fazia parte do escopo que escolhi. A única medição é a do extrato com 100 mil liquidações ([README](README.md#desempenho-do-extrato)). | Não sei o limite real de simulações e liquidações por segundo. As metas de p95 do [`SPEC.md`](SPEC.md) para simulação e liquidação não foram verificadas. | Um script k6 com cenários de simulação, liquidação (incluindo retries e concorrência) e extrato, para achar o limite de um primário. É o primeiro passo do [`docs/SCALE.md`](docs/SCALE.md). |

## Cortes previstos no SPEC

| # | O que ficou de fora | Por quê | Risco aceito | O que fazer em produção |
|---|---|---|---|---|
| 6 | **Estorno de liquidação.** | A liquidação é imutável (premissa A12): sem UPDATE e sem DELETE. Corrigir uma liquidação exige um lançamento novo de estorno, com regras de negócio que ainda são uma pergunta aberta (pergunta 6 da seção 4 do [`SPEC.md`](SPEC.md)). | Uma liquidação errada não tem como ser corrigida pelo sistema; precisa de ação manual fora dele. | Estorno como lançamento novo, com aprovação da mesa. O desenho está em [`docs/EDA.md`](docs/EDA.md). |
| 7 | **Idempotência no cadastro de recebível.** | Cadastrar recebível não movimenta dinheiro. A idempotência que importa está na liquidação ([ADR 0005](docs/adr/0005-idempotencia-em-tres-barreiras.md)). | Um timeout no `POST /receivables` seguido de retry pode criar um recebível duplicado em aberto. Ele não é pago duas vezes, mas fica órfão. | `Idempotency-Key` também no cadastro, ou unicidade por um identificador do título vindo da origem. |
| 8 | **Dias úteis e feriados.** | O prazo usa dias corridos convertidos por ACT/30 (premissa A3). A convenção certa é uma pergunta ao negócio (pergunta 1 da seção 4 do [`SPEC.md`](SPEC.md)). | Se a mesa precificar por dias úteis (252), o preço do sistema diverge do esperado. | Definir a convenção com o negócio e, se for dias úteis, um calendário de feriados versionado. |
| 9 | **Título em USD.** | Títulos são sempre em BRL; só o pagamento pode ser em USD (premissa A9). É o caso descrito no enunciado. | Um título emitido em dólar não pode ser cadastrado. | Moeda no título, regras de conversão nos dois sentidos e a pergunta 4 da seção 4 do [`SPEC.md`](SPEC.md) respondida. |

A observabilidade não é corte: Prometheus, Loki, Alloy e Grafana com o dashboard de operação foram entregues na entrega 003 ([`docs/architecture.md`](docs/architecture.md)). O que ficou de fora foram só a senha do Grafana (item 2) e os alertas (item 4).

## Limitações conhecidas

Não são cortes que planejei. São pontos que apareceram durante as entregas e estão registrados nos documentos de cada uma.

- **Liquidação em USD na demo recebe `FX_RATE_STALE`.** A taxa do seed é de 01/01/2026, o agendamento de atualização vem desligado, e o provedor mockado devolve uma vigência fixa. O painel também não oferece ao operador uma ação para atualizar a taxa. Detalhes na [ADR 0006](docs/adr/0006-cambio-lido-do-banco.md).
- **`GET /api/v1/cedentes` sem paginação.** Basta para os cedentes de demonstração.
- **A proibição de `double` e `float` depende de revisão.** Não há lint no build que a garanta ([ADR 0004](docs/adr/0004-bigdecimal-half-even-rounding-policy.md)).
- **As fronteiras entre módulos são convenção de pacote,** sem verificação automática ([ADR 0002](docs/adr/0002-monolito-modular.md)).
