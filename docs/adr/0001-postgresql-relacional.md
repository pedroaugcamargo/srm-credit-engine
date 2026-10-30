# ADR 0001: PostgreSQL relacional em vez de NoSQL

- **Status:** aceita
- **Decidida em:** entrega 002 ([`002-persistencia-cambio-liquidacao.md`](../sdd/002-persistencia-cambio-liquidacao.md))

## Contexto

O núcleo do sistema é a liquidação: marcar um recebível como liquidado e gravar o registro do pagamento, com valores exatos, sem duplicar e sem permitir alteração depois. O enunciado exige ACID ("nenhuma liquidação fica pela metade"), auditoria imutável e um extrato com filtros por período, cedente e moeda.

O modelo é naturalmente relacional: cedentes têm recebíveis, recebíveis têm no máximo uma liquidação, e a liquidação referencia a taxa de câmbio usada. O volume esperado neste case é pequeno. O desenho para volume alto está em `docs/SCALE.md`.

## Decisão

PostgreSQL como único banco, com o schema versionado por Flyway e `ddl-auto=validate` (o Hibernate valida o schema, nunca o altera).

As regras que não podem falhar ficam também no banco, como última barreira caso a aplicação tenha um bug ([`V1__initial_schema.sql`](../../backend/src/main/resources/db/migration/V1__initial_schema.sql)):

- **Unicidade:** `settlements_receivable_unique` (um recebível liquida uma vez) e `settlements_idempotency_key_unique`.
- **Domínio:** checks de tipo, status, moeda, valor de face positivo e taxa positiva.
- **Imutabilidade:** o trigger `settlements_immutable` rejeita UPDATE e DELETE em `settlements` (premissa A12).
- **Dinheiro exato:** `NUMERIC(19,2)` para valores, `NUMERIC(12,8)` para taxas e `NUMERIC(18,8)` para câmbio.
- **Chaves:** `uuidv7()`, ordenáveis no tempo, o que ajuda os índices por data.

O extrato usa SQL nativo parametrizado sobre índices compostos ([`SettlementStatementRepository`](../../backend/src/main/java/com/srmasset/creditengine/persistence/SettlementStatementRepository.java)).

## Alternativas consideradas

- **MongoDB.** Tem transações multi-documento, índices únicos, `Decimal128` e validação de schema. Daria para chegar perto. Ficou de fora porque as garantias que importam aqui (transação entre recebível e liquidação, unicidade, imutabilidade por trigger, checks de domínio) são nativas e maduras no PostgreSQL. No Mongo, parte delas viraria disciplina da aplicação. O extrato analítico também é mais natural em SQL.
- **DynamoDB ou Cassandra.** Escrita condicional resolveria a idempotência e o volume escala horizontalmente. O custo é perder consultas ad hoc e joins: o extrato precisaria de um modelo de leitura separado desde o primeiro dia. É complexidade de escala que este case não tem.
- **Event store como fonte da verdade.** Auditoria seria natural, mas a reconstrução de estado e a idempotência ficariam muito mais complexas para o tamanho do problema.

## Consequências

- **Positivas.**
  - Uma transação cobre o status do recebível e a liquidação.
  - Um bug na aplicação esbarra numa constraint e vira um erro visível (`409`), em vez de pagamento duplicado. Essa garantia é provada contra PostgreSQL real em [`ConcurrentSettlementTest`](../../backend/src/test/java/com/srmasset/creditengine/web/ConcurrentSettlementTest.java) e [`SettlementImmutabilityTest`](../../backend/src/test/java/com/srmasset/creditengine/SettlementImmutabilityTest.java).
- **Negativas.**
  - **Escala de escrita:** com um só primário, a escrita escala só verticalmente. Volume alto exige particionamento e outbox, descritos em `docs/SCALE.md`.
  - **Testes mais lentos:** os testes de integração precisam de Docker (Testcontainers). Foi escolhido não usar H2, porque H2 não reproduz o trigger, os erros de constraint nem o `uuidv7()`.
  - **Mudança de schema:** toda mudança passa por migração versionada. É o preço da auditoria.
