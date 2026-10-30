# Entrega 001: Fundação e motor de precificação

- **Status:** pronta para execução
- **Branch:** `feat/001-motor-precificacao`
- **Base:** `SPEC.md`, premissas A1, A3, A5, A8, A10 e seção 3

## Objetivo

Projeto rodando com um motor de precificação correto ao centavo, exposto por um endpoint de simulação. Sem persistência de negócio nesta entrega.

## Fora de escopo

Liquidação, idempotência, auditoria, extrato, Currency Engine real e frontend (entregas 002 em diante).

## Requisitos

- **R1.** O motor calcula `VP = Face / (1 + base + spread) ^ prazoMeses` seguindo a seção 3 do SPEC.
- **R2.** Cada tipo de recebível tem sua própria Strategy. Tipo novo = classe nova, sem alterar o motor.
- **R3.** O registro de strategies falha no startup se um tipo do enum ficar sem strategy ou tiver duas.
- **R4.** Política de arredondamento isolada e configurável por properties (default: escala 2, `HALF_EVEN`).
- **R5.** Conversão de prazo ACT/30 a partir de (data da operação, vencimento), separada do motor.
- **R6.** Endpoint `POST /api/v1/pricing/simulations`, sem persistência.
- **R7.** Câmbio vem de uma porta `FxRateProvider` com implementação fixa por configuração (substituída na 002).

## Contrato

Exemplo assumindo operação em 2026-10-26 (90 dias = 3 meses ACT/30) e câmbio fixo 5.4321.

Request:

```json
{
  "faceValue": "100000.00",
  "receivableType": "DUPLICATA_MERCANTIL",
  "dueDate": "2027-01-24",
  "paymentCurrency": "USD"
}
```

Response `200`:

```json
{
  "faceValue": "100000.00",
  "termMonths": "3",
  "baseRate": "0.01",
  "spread": "0.015",
  "presentValueBrl": "92859.94",
  "discountBrl": "7140.06",
  "paymentCurrency": "USD",
  "fxRate": "5.4321",
  "netAmount": "17094.67"
}
```

Erros (ProblemDetail com campo `code`):

- `400 VALIDATION_ERROR`: valor de face menor ou igual a zero, campo ausente, tipo ou moeda desconhecidos.
- `422 INVALID_TERM`: vencimento hoje ou no passado.

## Design

```
com.srmasset.creditengine
├── web            controllers, DTOs, exception handler
├── domain
│   └── pricing    PricingEngine, PricingStrategy, strategies, registry,
│                  RoundingPolicy, TermCalculator, FxRateProvider (porta)
└── infra          FixedFxRateProvider
```

```java
public interface PricingStrategy {
    ReceivableType type();
    BigDecimal monthlySpread(PricingContext context);
}
```

`PricingContext` carrega valor de face e prazo, para que um tipo futuro possa ter spread por faixa de prazo sem mudar a assinatura. `PricingEngine.price(PricingInput)` devolve `PricingResult`. Ambos são records imutáveis.

## Tarefas (uma tarefa = um commit)

| # | Commit | O que entrega |
|---|---|---|
| T1 | `chore(backend): scaffold spring boot 3 with java 21` | Maven wrapper, Java 21, Spring Boot 3.x, dependências web, validation, actuator, springdoc-openapi, big-math e testes. Dockerfile e compose com o backend. `./mvnw verify` verde |
| T2 | `feat(pricing): add domain types and rounding policy` | `ReceivableType`, `PricingInput`, `PricingResult`, `RoundingPolicy`. Testes de half-even: 2.345 vira 2.34 e 2.355 vira 2.36 |
| T3 | `feat(pricing): add receivable strategies and registry` | Strategies de duplicata (0.015) e cheque (0.025), registry com fail-fast. Testes de tipo sem strategy e strategy duplicada |
| T4 | `feat(pricing): add pricing engine validated by golden cases` | Motor + teste parametrizado C1, C2, C3 ao centavo + invariante VP + deságio = face |
| T5 | `feat(pricing): add act/30 term calculator` | 90 dias = 3, 45 dias = 1.5, vencimento hoje ou passado gera erro. Teste do caminho fracionário com big-math |
| T6 | `feat(api): expose pricing simulation endpoint` | DTOs com string, Bean Validation, `@RestControllerAdvice` com ProblemDetail, OpenAPI com exemplos. Teste MockMvc: caso C1, erro 400 e erro 422 |
| T7 | `ci: run maven verify on pull requests` | GitHub Actions rodando `./mvnw verify` em todo PR |

## Definition of done

- [x] Golden cases verdes localmente e no CI.
- [x] Nenhum `double` ou `float` no pacote `domain`.
- [x] Swagger exibe o endpoint com exemplo de request e response.
- [x] PR descreve o porquê das decisões e registra o que a IA errou nesta entrega (alimenta o `AI_USAGE.md`).

## Instruções para o agente de IA

- Execute uma tarefa por vez, na ordem da tabela. Ao terminar cada uma, pare, mostre o diff e o resultado dos testes e aguarde aprovação antes do commit.
- Não crie arquivos, dependências ou camadas fora do que a tarefa pede.
- Escreva primeiro o teste com o valor esperado, depois a implementação.
- Se uma premissa do SPEC parecer errada ou ambígua, pergunte antes de decidir.
