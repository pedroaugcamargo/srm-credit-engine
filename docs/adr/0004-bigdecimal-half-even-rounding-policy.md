# ADR 0004: `BigDecimal`, HALF_EVEN e uma única `RoundingPolicy`

- **Status:** aceita
- **Decidida em:** entrega 001 (seção 3 do [`SPEC.md`](../../SPEC.md) e [`001-fundacao-motor-precificacao.md`](../sdd/001-fundacao-motor-precificacao.md))

## Contexto

O motor calcula `VP = Face / (1 + base + spread) ^ prazo`, com prazo fracionário (ACT/30, premissa A3) e conversão cambial. Os golden cases exigem o resultado ao centavo, com half-even, arredondando só no resultado final. O enunciado trata ponto flutuante binário para dinheiro como eliminatório. A regra de arredondamento é uma decisão de negócio que pode mudar, e a defesa ao vivo cita isso como exemplo de mudança.

## Decisão

- **Tipo:** `BigDecimal` para dinheiro, taxas e câmbio, sempre criado a partir de `String` (`new BigDecimal("0.015")`). Nunca `double`, `float` ou `Math.pow`.
- **Precisão dos intermediários:** `MathContext.DECIMAL128` (34 dígitos significativos). Potência inteira com `BigDecimal.pow(int, mc)`; potência fracionária com a biblioteca `big-math` ([`PricingEngine`](../../backend/src/main/java/com/srmasset/creditengine/domain/pricing/PricingEngine.java)).
- **Momento do arredondamento:** uma vez, no VP em BRL. Em pagamento em USD, o VP já arredondado é convertido e arredondado de novo, como no golden case C3. O deságio é `face - VP`, o que garante `face = VP + deságio` ao centavo (premissa A10).
- **Ponto único:** todo arredondamento passa por [`RoundingPolicy`](../../backend/src/main/java/com/srmasset/creditengine/domain/pricing/RoundingPolicy.java), configurável por properties (`credit-engine.rounding.scale`, padrão `2`; `credit-engine.rounding.mode`, padrão `HALF_EVEN`). Mudar a regra não toca o motor. Regra 3 do [`CLAUDE.md`](../../CLAUDE.md): nada de `setScale` espalhado.
- **Fronteiras:** dinheiro trafega como string na API e fica em `NUMERIC` no banco. O frontend nunca converte para `number` ([`money.ts`](../../frontend/src/lib/money.ts)).

## Alternativas consideradas

- **`double` com arredondamento no fim.** Bate os golden cases por acaso, mas erra nos meios-centavos: `2.345` vira `2.35` com `toFixed`, quando o half-even dá `2.34` (tabela no item 5 do [`REVIEW.md`](../../REVIEW.md)). É eliminatório pelo enunciado.
- **Centavos em `long`.** Funciona para somar e guardar valores, mas a fórmula tem divisão e potência fracionária. Os intermediários precisariam de decimal de qualquer jeito, e as taxas têm 8 casas.
- **Arredondar a cada etapa.** O erro acumula e o resultado diverge dos golden cases, que arredondam só no final.
- **`MathContext` menor (`DECIMAL64`).** Seria suficiente para os valores atuais, mas o ganho de desempenho não compensa reduzir a margem para valores grandes e prazos fracionários.

## Consequências

- **Positivas.**
  - Os golden cases batem ao centavo e bloqueiam o merge ([`PricingEngineTest`](../../backend/src/test/java/com/srmasset/creditengine/domain/pricing/PricingEngineTest.java)).
  - A política tem testes próprios ([`RoundingPolicyTest`](../../backend/src/test/java/com/srmasset/creditengine/domain/pricing/RoundingPolicyTest.java), [`RoundingPolicyBindingTest`](../../backend/src/test/java/com/srmasset/creditengine/domain/pricing/RoundingPolicyBindingTest.java)).
  - Trocar a regra é mudar uma property.
- **Negativas.**
  - **Verbosidade:** o código é mais longo que aritmética comum, e todo `divide` precisa de `MathContext`, senão lança exceção em dízima.
  - **Dependência:** `big-math` entra só por causa do expoente fracionário.
  - **Regra só por revisão:** a proibição de `double` é garantida pela revisão e pelo `CLAUDE.md`, não por lint no build. Isso está proposto na seção de prevenção do [`REVIEW.md`](../../REVIEW.md).
