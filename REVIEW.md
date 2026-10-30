# Code review: endpoint de liquidação (Anexo A)

> Revisão do PR com `settlement.controller.ts`, escrita como revisor antes do merge. Resultado: **mudanças necessárias, não mergear**.

O endpoint move dinheiro e tem defeitos em todas as camadas: o preço sai errado, a gravação pode ficar pela metade, um retry paga duas vezes, a entrada vai direto para o SQL e não sobra registro para auditar. Os problemas estão em ordem de severidade, medida pelo dano financeiro e pela chance de acontecer. Cada item diz onde este repositório já resolve o problema, com o arquivo e o teste que provam.

| # | Problema | Severidade |
|---|---|---|
| 1 | Taxas em percentual usadas como fração | Crítica |
| 2 | Sem transação e com exceção engolida | Crítica |
| 3 | Sem idempotência, sem checagem de status e sem controle de concorrência | Crítica |
| 4 | SQL injection em todas as queries | Crítica |
| 5 | Ponto flutuante para dinheiro e `toFixed` | Alta |
| 6 | Câmbio "latest" sem vigência e sem registro | Alta |
| 7 | Recebível inexistente vira 500; nenhuma validação de entrada | Média |
| 8 | Strategy ausente: tipo desconhecido vira cheque | Média |

---

## 1. Taxas em percentual usadas como fração (crítica)

```typescript
const BASE_RATE = 1.0; // taxa base mensal
const spread = receivable.type === "DUPLICATA" ? 1.5 : 2.5;
... Math.pow(1 + BASE_RATE + spread, receivable.term)
```

**Problema.** A fórmula espera fração (1% = `0.01`), mas recebe percentual. Para uma duplicata, o fator mensal vira `1 + 1.0 + 1.5 = 3.5`: um desconto de 250% ao mês.

**Impacto em produção.** Todo preço sai errado. No golden case C1 (duplicata de R$ 100.000,00 em 3 meses), o código calcula `100000 / 3.5³` = **R$ 2.332,36**, quando o correto é **R$ 92.859,94**. O cedente recebe cerca de 2,5% do valor justo. Esse é o defeito que mais custa, e passou porque não havia nenhum teste com valor esperado.

**Correção.** Taxas como fração decimal, com a unidade documentada. A taxa base vem de dados versionados, não de uma constante. Um teste com os golden cases bloqueia o merge.

**No repositório.**
- Premissa A1 do [`SPEC.md`](SPEC.md): fração decimal ao mês (base `0.01`, duplicata `0.015`, cheque `0.025`).
- Spreads em [`DuplicataMercantilStrategy`](backend/src/main/java/com/srmasset/creditengine/domain/pricing/strategy/DuplicataMercantilStrategy.java) e [`ChequeStrategy`](backend/src/main/java/com/srmasset/creditengine/domain/pricing/strategy/ChequeStrategy.java).
- Taxa base na tabela `base_rates`, versionada por vigência ([`V1__initial_schema.sql`](backend/src/main/resources/db/migration/V1__initial_schema.sql), lida por [`DbBaseRateProvider`](backend/src/main/java/com/srmasset/creditengine/persistence/DbBaseRateProvider.java)).
- Golden cases C1, C2 e C3 em [`PricingEngineTest`](backend/src/test/java/com/srmasset/creditengine/domain/pricing/PricingEngineTest.java) (`pricesToTheCentAndKeepsFaceEqualToPresentValuePlusDiscount`), rodando no CI ([`verify.yml`](.github/workflows/verify.yml)).

## 2. Sem transação e com exceção engolida (crítica)

```typescript
try {
  await db.query(`INSERT INTO settlements ...`);
  await db.query(`UPDATE receivables SET status = 'SETTLED' ...`);
} catch (e) {
  // se falhar aqui, o insert já rodou, então segue o jogo
}
res.status(200).json({ ok: true, amount: finalAmount.toFixed(2) });
```

**Problema.** O INSERT e o UPDATE são duas operações independentes, e qualquer erro é descartado. O comentário admite o estado inconsistente e segue em frente.

**Impacto em produção.**
- **Falha no UPDATE:** a liquidação fica gravada, mas o recebível continua `OPEN`. O próximo pedido liquida de novo: este defeito alimenta o item 3.
- **Falha no INSERT:** o operador recebe `200` com o valor e acredita que pagou, mas nada foi gravado.
- **Sem rastro:** como o erro não é logado nem propagado, ninguém fica sabendo. O incidente só aparece quando a mesa reclama.

**Correção.** INSERT e UPDATE na mesma transação: ou os dois acontecem, ou nenhum. Erro nunca vira `2xx`. Toda falha é mapeada para um status e um código de negócio, no formato ProblemDetail (RFC 9457).

**No repositório.**
- [`SettlementService.settle`](backend/src/main/java/com/srmasset/creditengine/application/SettlementService.java) roda a criação inteira (status do recebível e insert da liquidação) dentro de um `TransactionTemplate`.
- [`ApiExceptionHandler`](backend/src/main/java/com/srmasset/creditengine/web/ApiExceptionHandler.java) mapeia cada falha para ProblemDetail com `code`. Nenhum `catch` engole erro.
- Teste [`SettlementControllerTest.keepsTheReceivableOpenWhenTheSettlementInsertFails`](backend/src/test/java/com/srmasset/creditengine/web/SettlementControllerTest.java): o insert falha, o recebível continua `OPEN` e a resposta é `409`, não `200`.

## 3. Sem idempotência, sem checagem de status e sem controle de concorrência (crítica)

**Problema.** Nada impede liquidar o mesmo recebível duas vezes:
- o código não verifica se o recebível já está `SETTLED`;
- não há chave de idempotência;
- duas requisições simultâneas leem o mesmo estado e as duas gravam.

**Impacto em produção.** Retry automático do gateway, timeout com nova tentativa do cliente e duplo clique viram pagamentos duplicados. É a causa provável do incidente do Anexo B: três cedentes recebendo a mesma liquidação duas vezes. Dinheiro pago a mais é difícil de recuperar e expõe o fundo perante os cedentes.

**Correção.** Três barreiras independentes, porque cada uma cobre a falha da outra:
1. **Chave de idempotência obrigatória.** Mesma chave e mesmo payload devolvem a liquidação original; mesma chave com payload diferente é recusada.
2. **Checagem de status com lock otimista no recebível.** Detecta a corrida entre duas requisições.
3. **Constraints únicas no banco.** Última linha de defesa, que vale mesmo se o código da aplicação tiver bug.

**No repositório.**
- **Chave obrigatória:** `Idempotency-Key` em [`SettlementController`](backend/src/main/java/com/srmasset/creditengine/web/SettlementController.java) (`requireKey`).
- **Hash do payload:** `request_hash` em [`SettlementService`](backend/src/main/java/com/srmasset/creditengine/application/SettlementService.java). A repetição volta `200` com `Idempotent-Replayed`; payload diferente com a mesma chave é recusado com `422 IDEMPOTENCY_KEY_REUSED`.
- **Lock otimista:** `@Version` em [`ReceivableEntity`](backend/src/main/java/com/srmasset/creditengine/persistence/ReceivableEntity.java). Recebível já liquidado: `409 RECEIVABLE_ALREADY_SETTLED`.
- **Constraints:** `settlements_receivable_unique` e `settlements_idempotency_key_unique` em [`V1__initial_schema.sql`](backend/src/main/resources/db/migration/V1__initial_schema.sql).
- **Testes de concorrência contra PostgreSQL real:** [`ConcurrentSettlementTest`](backend/src/test/java/com/srmasset/creditengine/web/ConcurrentSettlementTest.java) (`twoThreadsWithDifferentKeysLeaveOneSettlement`, `twoThreadsWithTheSameKeyLeaveOneSettlement`).
- **Testes do contrato:** [`SettlementControllerTest`](backend/src/test/java/com/srmasset/creditengine/web/SettlementControllerTest.java) (`replaysTheOriginalSettlementWhenTheKeyAndBodyMatch`, `rejectsTheSameKeyWhenTheBodyChanges`, `rejectsASecondSettlementOfTheSameReceivable`, `rejectsAMissingIdempotencyKey`).
- **No front:** a chave nasce no clique e é reutilizada em todo retry da mesma tentativa ([`useSettle.ts`](frontend/src/features/pricing/useSettle.ts)).

## 4. SQL injection em todas as queries (crítica)

```typescript
`SELECT * FROM receivables WHERE id = ${receivableId}`
`VALUES (${receivableId}, ${finalAmount.toFixed(2)}, '${currency}')`
```

**Problema.** `receivableId` e `currency` vêm do corpo da requisição e são interpolados direto no SQL. Isso acontece nas três queries.

**Impacto em produção.** Qualquer cliente da API pode ler, alterar ou apagar dados: liquidar sem recebível válido, marcar recebíveis como liquidados, apagar o histórico. Num sistema de pagamentos, é comprometimento total. Está em quarto lugar só porque exige um ator mal-intencionado, enquanto os três primeiros acontecem sozinhos no uso normal.

**Correção.** Queries sempre parametrizadas. Tipos fortes na fronteira, para que um `receivableId` que não é UUID seja recusado antes de chegar ao banco.

**No repositório.**
- Repositórios JPA e, no extrato, `NamedParameterJdbcTemplate` com parâmetros nomeados ([`SettlementStatementRepository`](backend/src/main/java/com/srmasset/creditengine/persistence/SettlementStatementRepository.java)).
- `receivableId` tipado como `UUID` em [`SettlementRequest`](backend/src/main/java/com/srmasset/creditengine/web/SettlementRequest.java): um valor inválido vira `400 VALIDATION_ERROR` antes de qualquer query.
- Regra 4 do [`CLAUDE.md`](CLAUDE.md): "SQL sempre parametrizado".

## 5. Ponto flutuante para dinheiro e `toFixed` (alta)

```typescript
receivable.face_value / Math.pow(...)
finalAmount = presentValue / rate;
finalAmount.toFixed(2)
```

**Problema.** `number` é ponto flutuante binário. `toFixed` arredonda o valor binário, que muitas vezes não é o decimal que parece, e não aplica half-even (arredondamento bancário). Medido no Node:

| Valor | `toFixed(2)` | Half-even correto |
|---|---|---|
| `2.345` | `2.35` | `2.34` |
| `2.355` | `2.35` | `2.36` |
| `0.125` | `0.13` | `0.12` |
| `1.005` | `1.00` | `1.00` (por acaso, pelo erro binário) |

**Impacto em produção.** Diferenças de centavo em parte das liquidações, sem padrão previsível, que quebram a conciliação com a mesa e com o banco. O defeito é traiçoeiro: com as taxas corrigidas, o C1 e o C3 até batem em `number` (`92859.94` e `17094.67`). O erro só aparece nos meios-centavos, justamente onde ninguém testou.

**Correção.**
- Decimal exato (`BigDecimal` criado a partir de string), com precisão definida nos intermediários.
- Um único ponto de arredondamento, HALF_EVEN, 2 casas, aplicado uma vez no resultado.
- Dinheiro trafegando como string na API.

**No repositório.**
- Premissas da seção 3 do [`SPEC.md`](SPEC.md).
- [`RoundingPolicy`](backend/src/main/java/com/srmasset/creditengine/domain/pricing/RoundingPolicy.java) como único ponto de arredondamento, testado em [`RoundingPolicyTest`](backend/src/test/java/com/srmasset/creditengine/domain/pricing/RoundingPolicyTest.java) com os mesmos valores da tabela acima (`rounds2_345To2_34`, `rounds2_355To2_36`).
- [`PricingEngine`](backend/src/main/java/com/srmasset/creditengine/domain/pricing/PricingEngine.java) com `MathContext.DECIMAL128` e `big-math` para expoente fracionário.
- `NUMERIC(19,2)` no banco.
- No front, [`formatMoney`](frontend/src/lib/money.ts) formata a string sem passar por `number`.

## 6. Câmbio "latest" sem vigência e sem registro (alta)

```typescript
const rate = await fxService.getLatestRate("USD");
finalAmount = presentValue / rate;
INSERT INTO settlements (receivable_id, amount, currency) ...
```

**Problema.**
- "A mais recente" não tem corte temporal: depende de quando a chamada roda e do que o provedor devolve naquele instante.
- Uma taxa velha é usada sem aviso.
- A liquidação grava só `amount` e `currency`: nem a taxa, nem o valor presente em BRL, nem o momento.
- A chamada ao provedor fica no caminho da liquidação, então a queda dele derruba a liquidação.

**Impacto em produção.** Uma liquidação em USD não pode ser auditada nem reproduzida: não há como provar qual taxa foi usada. Taxa desatualizada é perda financeira direta. Numa disputa com o cedente, não há registro que sustente o valor pago.

**Correção.**
- Taxas gravadas com data de vigência. Vale a de maior vigência menor ou igual ao instante da liquidação.
- Taxa velha recusa a liquidação explicitamente, sem fallback silencioso.
- A liquidação grava o ID e o valor da taxa usada, mais todos os componentes do preço.
- A liquidação lê a taxa do banco e nunca chama o provedor.

**No repositório.**
- Premissas A6, A7 e A8 do [`SPEC.md`](SPEC.md).
- Tabela `fx_rates` com `valid_from` ([`V1__initial_schema.sql`](backend/src/main/resources/db/migration/V1__initial_schema.sql)).
- Seleção por vigência e idade máxima em [`FxValidity`](backend/src/main/java/com/srmasset/creditengine/domain/fx/FxValidity.java), testada em [`FxValidityTest`](backend/src/test/java/com/srmasset/creditengine/domain/fx/FxValidityTest.java).
- Taxa com mais de 24h: `422 FX_RATE_STALE` ([`StaleSettlementTest`](backend/src/test/java/com/srmasset/creditengine/web/StaleSettlementTest.java)).
- A liquidação grava `fx_rate_id`, `fx_rate`, taxa base, spread, prazo, VP, deságio e `settled_at` ([`SettlementControllerTest.settlesDuplicataInUsdWithTheGoldenSnapshot`](backend/src/test/java/com/srmasset/creditengine/web/SettlementControllerTest.java)).
- Liquidação imutável por trigger no banco ([`SettlementImmutabilityTest`](backend/src/test/java/com/srmasset/creditengine/SettlementImmutabilityTest.java)).
- Queda do provedor durante a liquidação coberta em [`ProviderDownSettlementTest`](backend/src/test/java/com/srmasset/creditengine/web/ProviderDownSettlementTest.java).

## 7. Recebível inexistente vira 500; nenhuma validação de entrada (média)

**Problema.**
- Se o `SELECT` não encontra o recebível, `receivable` é `undefined` e `receivable.type` lança `TypeError`: a resposta é um 500 genérico.
- Nada valida o corpo da requisição.
- `currency` é texto livre: qualquer valor diferente de `"USD"` é tratado como BRL e gravado como veio (`'EUR'`, `'usd'`, string vazia).
- `receivable.term` é lido do banco, mas o prazo depende da data da operação: um prazo armazenado envelhece.

**Impacto em produção.** O operador recebe erro sem explicação, e o suporte não distingue bug de recebível inexistente. Moedas inválidas entram na base e quebram o extrato e a conciliação. Um título vencido seria "liquidado" com prazo antigo.

**Correção.** Validação na fronteira (campos obrigatórios, moeda como enum). Erros de negócio com status semântico e mensagem para o operador (`404` para recebível inexistente). Prazo calculado a partir do vencimento e da data da operação. Constraints de domínio também no banco.

**No repositório.**
- `@NotNull` e `PaymentCurrency` como enum em [`SettlementRequest`](backend/src/main/java/com/srmasset/creditengine/web/SettlementRequest.java).
- Check constraint `settlements_payment_currency_check` no banco.
- `404 RECEIVABLE_NOT_FOUND` ([`SettlementControllerTest.rejectsAnUnknownReceivable`](backend/src/test/java/com/srmasset/creditengine/web/SettlementControllerTest.java)).
- Prazo calculado em [`TermCalculator`](backend/src/main/java/com/srmasset/creditengine/domain/pricing/TermCalculator.java). Vencimento hoje ou no passado é recusado com `422 INVALID_TERM` (premissa A5, teste `rejectsADueDateThatIsNotAfterTheOperationDate`).

## 8. Strategy ausente: tipo desconhecido vira cheque (média)

```typescript
const spread = receivable.type === "DUPLICATA" ? 1.5 : 2.5;
```

**Problema.** O ternário trata qualquer tipo diferente de `"DUPLICATA"` como cheque, inclusive um tipo novo, um erro de digitação ou o valor real do domínio (`DUPLICATA_MERCANTIL`). A regra de preço também fica acoplada ao controller.

**Impacto em produção.** Um tipo novo de recebível entra em produção com o spread do cheque sem nenhum erro. Errar o preço em silêncio é pior do que falhar: ninguém percebe até a conciliação.

**Correção.** Padrão Strategy, com uma regra por tipo. O registro de strategies falha na inicialização se algum tipo do enum não tiver regra: um tipo novo sem preço definido nem chega a subir.

**No repositório.**
- [`PricingStrategy`](backend/src/main/java/com/srmasset/creditengine/domain/pricing/PricingStrategy.java) com uma implementação por tipo.
- [`PricingStrategyRegistry`](backend/src/main/java/com/srmasset/creditengine/domain/pricing/PricingStrategyRegistry.java) recusa tipo sem strategy e strategy duplicada ([`PricingStrategyRegistryTest`](backend/src/test/java/com/srmasset/creditengine/domain/pricing/PricingStrategyRegistryTest.java): `rejectsAReceivableTypeWithoutStrategy`, `rejectsTwoStrategiesForTheSameReceivableType`).
- Check constraint `receivables_type_check` no banco.

---

## Prevenção sistêmica

Corrigir este PR não basta: ele foi aprovado às pressas, e o processo deixou passar. As medidas abaixo atacam a classe de erro sem travar o time.

| Medida | Que erro pega | Estado neste repositório |
|---|---|---|
| **Golden cases bloqueando o merge.** Teste com valores esperados ao centavo, rodando no CI. | Itens 1 e 5 | Existe: [`PricingEngineTest`](backend/src/test/java/com/srmasset/creditengine/domain/pricing/PricingEngineTest.java) no workflow [`verify.yml`](.github/workflows/verify.yml). Falta exigir o check verde na proteção da `main`. |
| **Constraints no banco como última barreira.** Unicidade, checks de domínio, trigger de imutabilidade. | Itens 3, 6, 7 e 8, mesmo com bug na aplicação | Existe: [`V1__initial_schema.sql`](backend/src/main/resources/db/migration/V1__initial_schema.sql). |
| **Teste de concorrência contra banco real.** Duas liquidações simultâneas, uma gravada. | Item 3 | Existe: [`ConcurrentSettlementTest`](backend/src/test/java/com/srmasset/creditengine/web/ConcurrentSettlementTest.java), com Testcontainers no CI. |
| **Checklist curto no template de PR, só para mudanças que movem dinheiro.** Transação? Idempotência? Arredondamento pela política única? Erro vira 2xx? Taxa auditável? | Itens 2, 3, 5 e 6 | Proposto, ainda não existe. Cinco perguntas, não um formulário. |
| **Lint contra `float` e `double` em dinheiro.** Por exemplo, uma regra ArchUnit ou forbidden-apis no build, e uma regra de ESLint no front. | Item 5 | Proposto, ainda não existe. Hoje a regra está só no [`CLAUDE.md`](CLAUDE.md) e na revisão. |
| **Regras para código gerado por IA.** Dinheiro só em decimal, SQL parametrizado, erro nunca 2xx, regra financeira começa pelo teste com valor esperado. | A origem do PR | Existe: [`CLAUDE.md`](CLAUDE.md). |
| **Nada direto na `main`.** Hooks locais e mudanças só por PR. | O "mergeado às pressas" | Existe: [`.githooks`](.githooks) (`pre-commit` e `pre-push`). |
