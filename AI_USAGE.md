# Uso de IA

Trabalhei com spec driven development: primeiro a especificação, depois o código. Usei a IA para entender o teste, escrever as specs junto comigo e gerar o código. As decisões, a revisão e a aprovação de cada commit foram minhas.

## 1. Como trabalhei

### Do enunciado às specs

Entreguei o enunciado do teste ([`desafio-tecnico-srm-credit-engine-v2.md`](desafio-tecnico-srm-credit-engine-v2.md)) ao Claude e fui conversando com ele para entender melhor o que era pedido. Dessa conversa saíram as especificações, seguindo spec driven development:

- [`SPEC.md`](SPEC.md)**:** as premissas para as ambiguidades do enunciado (A1 a A12), as regras de precisão numérica, as perguntas ao negócio e os critérios de aceite.
- [`CLAUDE.md`](CLAUDE.md)**:** as regras fixas para qualquer agente de IA neste repositório:
  - dinheiro só em `BigDecimal` criado a partir de string;
  - arredondamento só pela `RoundingPolicy`;
  - SQL sempre parametrizado;
  - nunca engolir exceção nem responder 2xx em erro;
  - regra financeira começa pelo teste com o valor esperado;
  - não adicionar dependência sem perguntar;
  - perguntar quando uma premissa parecer errada;
  - uma tarefa por vez;
  - nunca commitar na `main`.
- [`docs/sdd/`](docs/sdd/)**: cinco documentos, um por entrega.** Cada um detalha o que deve ser feito, separado em tarefas, com uma tarefa por commit:

| Spec | Entrega |
|---|---|
| [`001`](docs/sdd/001-fundacao-motor-precificacao.md) | Fundação e motor de precificação |
| [`002`](docs/sdd/002-persistencia-cambio-liquidacao.md) | Persistência, câmbio e liquidação idempotente |
| [`003`](docs/sdd/003-operacao-extrato.md) | Observabilidade, resiliência e extrato |
| [`004`](docs/sdd/004-frontend.md) | Frontend do operador |
| [`005`](docs/sdd/005-documentos-staff.md) | Documentos de decisão |

Separar o trabalho em commits pequenos e definidos de antemão foi o que mais ajudou. A cada tarefa, eu commitava, revisava e testava o código gerado antes de seguir para a próxima. Um erro aparecia no commit em que nasceu, não misturado com dez outras mudanças.

### Do spec ao código

- **Cursor com Grok 4.7 high Fast:** gerou o código a partir das specs, tarefa por tarefa.

O ciclo sempre foi o mesmo, ditado pelo `CLAUDE.md`:

1. A IA executava uma tarefa da spec.
2. Mostrava o diff e o resultado dos testes.
3. Parava. Eu revisava e testava antes de aprovar o commit.

Quando a spec não cobria um caso, a IA perguntava em vez de decidir. Por exemplo, a falta de um endpoint para listar cedentes no frontend virou uma decisão minha: implementar o `GET /api/v1/cedentes`.

## 2. Erros da IA e como foram detectados

### No Cursor com Grok 4**.7 high Fast**

**1. Classe deprecada do Testcontainers.**

- **O que a IA gerou:** `import org.testcontainers.containers.PostgreSQLContainer;`, que está deprecada.
- **Como detectei:** na revisão do código.
- **Correção:** pedi a troca, e o código passou a usar `org.testcontainers.postgresql.PostgreSQLContainer` ([`PostgresContextCustomizerFactory`](backend/src/test/java/com/srmasset/creditengine/PostgresContextCustomizerFactory.java)).

**2. Chaves** `uuid` **sem valor padrão.**

- **O que a IA gerou:** as tabelas do PostgreSQL com `id uuid` sem `default`. Todo insert dependeria de a aplicação gerar o ID.
- **Como detectei:** na revisão da migração.
- **Correção:** pedi um valor padrão, e as chaves passaram a ser `default uuidv7()` ([`V1__initial_schema.sql`](backend/src/main/resources/db/migration/V1__initial_schema.sql)).

**3. Falhas encontradas na checagem de segurança.**

- **Como detectei:** rodando a skill de revisão de segurança que uso normalmente. Ela apontou três problemas:
  - `SettlementStatementRepository.java:45`: `page` não tinha teto, e `page * size` em `int` podia estourar e gerar um offset errado.
  - `FxRateController.java:36`: uma falha do provedor de câmbio não virava ProblemDetail e subia como erro 500.
  - `SettlementStatementRepository.java:43`: cada consulta do extrato fazia `COUNT(*)` e `JOIN` sem limite de tempo. Com uma janela de 366 dias, a consulta podia ficar pesada e segurar o banco.
- **Correção:** pedi as correções, que entraram no commit `bca30bd` (`fix(api): report fx outages and bound the statement query`):
  - teto de offset em [`SettlementStatementService`](backend/src/main/java/com/srmasset/creditengine/application/SettlementStatementService.java) (`MAX_OFFSET`);
  - falhas do provedor viram `503 FX_PROVIDER_UNAVAILABLE`, testado em [`FxProviderProblemDetailTest`](backend/src/test/java/com/srmasset/creditengine/web/FxProviderProblemDetailTest.java);
  - timeout de 2 s na consulta do extrato, que vira `503 STATEMENT_QUERY_TIMEOUT` ([`SettlementStatementRepositoryTest`](backend/src/test/java/com/srmasset/creditengine/persistence/SettlementStatementRepositoryTest.java)), mais o índice `settlements_settled_at_idx`.

**4. Imagem do Nginx de uma linha sem manutenção.**

- **O que a IA gerou:** o Dockerfile do frontend com `nginxinc/nginx-unprivileged:1.29-alpine`. A linha 1.29 do nginx já estava encerrada, e a imagem estava parada com pacotes desatualizados.
- **Como detectei:** o scanner de imagens apontou 14 vulnerabilidades críticas e 53 altas.
- **Correção:** troca para `1.30-alpine-slim`, a linha estável e sem módulos que não usamos, com `apk upgrade` no build ([`frontend/Dockerfile`](frontend/Dockerfile)).

**5. Tipo errado no contrato do extrato.**

- **O que a IA gerou:** `from` e `to` do extrato tipados como data simples (`2026-10-01`). O backend espera `OffsetDateTime` e recusaria a requisição.
- **Como detectei:** a própria IA, ao implementar o histórico e ler o controller do backend.
- **Correção:** o tipo passou a ser instante com offset, e o dia escolhido vira o intervalo completo em Brasília ([`date.ts`](frontend/src/lib/date.ts)). O formato foi conferido contra o backend rodando.

**6. Chave de idempotência gerada antes da trava do duplo clique.**

- **O que a IA gerou:** no hook de liquidação, o segundo clique de um duplo clique gerava uma chave nova antes de ser barrado.
- **Como detectei:** pelo teste do hook, escrito antes da implementação, que esperava uma única chave.
- **Correção:** a checagem de requisição em andamento passou a vir antes do `crypto.randomUUID()` ([`useSettle.ts`](frontend/src/features/pricing/useSettle.ts)).

### O que esses erros mostram

Nenhum erro chegou à `main`. Cada um foi pego por uma camada diferente:

- revisão humana: erros 1 e 2;
- skill de segurança: erro 3;
- scanner de imagens: erro 4;
- leitura do contrato real: erro 5;
- teste escrito antes do código: erro 6.

Os commits pequenos ajudaram: cada erro apareceu no commit em que nasceu.
