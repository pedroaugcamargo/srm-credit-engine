# Entrega 005: Documentos de decisão (nível Staff)

- **Status:** pronta para execução
- **Branch:** `docs/005-decisoes`
- **Base:** enunciado, seções 5, 6 (Staff), 7, 10 e Anexos A e B
- **Depende de:** entregas 001 a 004 (os documentos citam o código real)

## Objetivo

Os documentos são a maior parte da nota no nível Staff. Cada um precisa refletir o que foi realmente construído e decidido, e ser defensável ao vivo.

## Regra para uso de IA nesta entrega

A IA pode organizar, revisar a escrita e sugerir estrutura. Fatos, números, decisões e o relato de erros são do autor. A IA não inventa métricas, incidentes nem erros que não aconteceram. O `AI_USAGE.md` é escrito a partir das anotações reais feitas durante as entregas.

## Artefatos

### 1. `REVIEW.md` (Anexo A)

Problemas em ordem de severidade. Para cada um: impacto em produção e correção proposta, apontando onde o repositório já resolve.

1. Taxas em percentual (`1.0`, `1.5`) usadas como fração: desconto de 250% ao mês, preço completamente errado.
2. Sem transação e com exceção engolida: liquidação gravada com recebível ainda aberto, e resposta `200` mesmo com falha.
3. Sem idempotência, sem checagem de status e sem controle de concorrência: retry ou duplo clique paga duas vezes (causa do Anexo B).
4. SQL injection em todas as queries.
5. `number` (ponto flutuante) para dinheiro e `toFixed`, que não aplica half-even.
6. Câmbio "latest" sem vigência e sem registro: liquidação não auditável nem reproduzível.
7. Recebível inexistente vira erro 500 genérico; nenhuma validação de entrada; `currency` livre.
8. Strategy ausente (ternário por tipo): todo tipo desconhecido vira cheque.

Fechar com prevenção sistêmica: golden cases bloqueando merge, constraints no banco, checklist de PR financeiro, lint contra `float` e `double`.

### 2. `POSTMORTEM.md` (Anexo B)

- Linha do tempo hipotética coerente com o Anexo A (ex.: lentidão no banco às 16h, timeouts no gateway, retries automáticos e cliques repetidos, duplicatas a partir de 16h20, reporte às 18h40).
- Causa raiz e evidências: query de duplicidade (`receivable_id` com mais de uma liquidação), logs com o mesmo payload em milissegundos, métrica de retries do gateway.
- Contenção (feature flag desligando liquidação, bloqueio dos pagamentos pendentes, conciliação com a mesa) separada da correção definitiva.
- Prevenção sistêmica sem burocracia.
- Formato sem culpados (blameless).

### 3. ADRs em `docs/adr/` (formato: contexto, decisão, alternativas, consequências)

- `0001`: PostgreSQL relacional (ACID, constraints como última barreira) versus NoSQL.
- `0002`: monolito modular versus serviços.
- `0003`: liquidação síncrona hoje, eventos via outbox como evolução.
- `0004`: `BigDecimal`, HALF_EVEN e `RoundingPolicy` única.
- `0005`: idempotência em três barreiras.
- `0006`: liquidação lê câmbio do banco, nunca do provedor.

### 4. `docs/SCALE.md` (1 milhão de transações por minuto, cerca de 17 mil por segundo)

Cobrir: motor stateless escalado horizontalmente; câmbio e taxa base em cache local com versão; banco particionado por cedente e o problema de cedente "quente"; outbox com Kafka; idempotência passando a ser por partição (chave roteada para a mesma partição, tabela de deduplicação com TTL, consumidor idempotente, entrega at-least-once com efeito exactly-once); extrato como read model com consistência eventual; o que continua forte (a liquidação de um recebível) e o que pode ser eventual (extrato, métricas).

### 5. `docs/EDA.md`

Fluxo de eventos da liquidação (`SettlementRequested`, `SettlementCompleted`, `PaymentInstructed`, `PaymentConfirmed`, `SettlementReversed`), outbox, saga com compensação por estorno e o que muda para o operador.

### 6. `docs/architecture.md`

C4 nível 1 (operador, SRM Credit Engine, provedor de câmbio, sistema de pagamentos) e nível 2 (SPA, API Spring, PostgreSQL, Prometheus, Grafana, Loki) em Mermaid.

### 7. `DECISIONS.md`

O que foi cortado e por quê: autenticação, alertas, estorno, cadastro de recebível sem idempotência própria (não movimenta dinheiro), dias úteis e feriados, título em USD. Cada corte com o risco aceito. Grafana entra na entrega 003 e não é corte.

### 8. `AI_USAGE.md`

- Os specs da pasta `docs/sdd/` como evidência do plano dado à IA.
- No mínimo um erro real da IA, como foi detectado (teste, golden case, build, revisão) e a correção.
- O que não foi delegado: premissas do SPEC, política de arredondamento, desenho da idempotência, priorização dos cortes.

### 9. README final

Como rodar (um comando), stack e motivo, links para todos os documentos, estratégia de branching (GitHub Flow, com justificativa para o contexto) e medições de desempenho.

## Tarefas (um artefato = um commit)

| # | Commit |
|---|---|
| T1 | `docs: add reverse code review of settlement endpoint` |
| T2 | `docs: add duplicated settlements post-mortem` |
| T3 | `docs(adr): add architecture decision records` |
| T4 | `docs: add high-scale and event-driven designs` |
| T5 | `docs: add c4 architecture diagrams` |
| T6 | `docs: add decisions and ai usage reports` |
| T7 | `docs: finalize readme` |

## Definition of done

- [x] Todo documento cita arquivos e testes reais do repositório.
- [x] Nenhum número ou incidente inventado como se fosse medição real (cenários hipotéticos marcados como tal).
- [x] O autor consegue explicar cada parágrafo sem ler.
