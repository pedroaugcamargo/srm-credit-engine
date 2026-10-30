# ADR 0002: monolito modular em vez de serviços

- **Status:** aceita
- **Decidida em:** entregas 001 e 002 (desenho de pacotes em [`001-fundacao-motor-precificacao.md`](../sdd/001-fundacao-motor-precificacao.md) e [`002-persistencia-cambio-liquidacao.md`](../sdd/002-persistencia-cambio-liquidacao.md))

## Contexto

O sistema tem quatro capacidades: precificação, câmbio, liquidação e extrato. A liquidação depende das outras duas no mesmo instante: precisa do preço e da taxa vigente, e grava tudo atomicamente. O time é uma pessoa, o prazo é de dias, e o enunciado lista "microserviços para um case" como anti-padrão.

## Decisão

Um único serviço Spring Boot, organizado em camadas e por domínio:

```
com.srmasset.creditengine
├── web          controllers, DTOs e tratamento de erro (ProblemDetail)
├── application  orquestração e transação (SettlementService, FxRateService, ...)
├── domain       pricing, fx, receivable, settlement: regras e portas
├── persistence  entidades JPA, repositórios e implementações das portas
└── infra        integração externa de câmbio (mock com Resilience4j)
```

As dependências que cruzam módulos passam por portas do domínio:
- `FxRateProvider` é implementada por [`DbFxRateProvider`](../../backend/src/main/java/com/srmasset/creditengine/persistence/DbFxRateProvider.java);
- `ExternalFxClient` é implementada por [`ResilientExternalFxClient`](../../backend/src/main/java/com/srmasset/creditengine/infra/fx/ResilientExternalFxClient.java).

O motor de precificação não sabe de onde vêm as taxas.

## Alternativas consideradas

- **Serviços separados (precificação, câmbio, liquidação).** A liquidação passaria a depender de chamadas de rede para obter o preço e a taxa. A transação que hoje o banco garante viraria uma saga com compensação, e cada falha de rede viraria um novo caminho de erro dentro do fluxo que move dinheiro. Para uma pessoa e um volume pequeno, isso é custo sem benefício.
- **Monolito sem camadas (controller acessando o banco direto).** É o que o Anexo A faz. Mistura regra financeira com HTTP e SQL e impede testar o motor isolado.

## Consequências

- **Positivas.**
  - Uma transação local cobre a liquidação inteira.
  - Um deploy só, uma observabilidade só.
  - O motor é testado sem Spring nem banco ([`PricingEngineTest`](../../backend/src/test/java/com/srmasset/creditengine/domain/pricing/PricingEngineTest.java)).
- **Negativas.**
  - **Fronteiras não verificadas:** os limites entre módulos são convenção de pacote, não verificados no build. Não há ArchUnit nem módulos Maven separados, e o domínio usa anotações do Spring (`@Component` nas strategies). Um import errado passa sem erro; quem barra é a revisão.
  - **Escala conjunta:** as partes escalam juntas, mesmo quando só a simulação recebe tráfego.
- **Caminho de extração, se o volume exigir.**
  - O motor de precificação é stateless e pode virar um serviço ou uma biblioteca.
  - A atualização de câmbio já está isolada atrás de `ExternalFxClient` e roda fora do caminho da liquidação ([ADR 0006](0006-cambio-lido-do-banco.md)).
  - A liquidação é a última a sair, porque é onde a transação local mais vale. Ver `docs/SCALE.md`.
