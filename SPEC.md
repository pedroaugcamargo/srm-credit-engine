# SPEC: SRM Credit Engine (Fase 0)

> Documento vivo. Toda mudança de premissa entra por Pull Request explicando o porquê.

## 1. Nível e escopo

Entrega no nível Staff/Tech Lead. O escopo de implementação é reduzido de propósito (ver `DECISIONS.md`): um núcleo pequeno e correto (motor, liquidação idempotente, auditoria) e o restante do esforço em decisão (ADRs, REVIEW, post-mortem, desenho de escala).

## 2. Premissas para as ambiguidades

| # | Ambiguidade | Premissa adotada | Por quê |
|---|---|---|---|
| A1 | Unidade das taxas | Fração decimal ao mês: base `0.01`, duplicata `0.015`, cheque `0.025` | O enunciado mistura % e número puro. O Anexo A usa `1.0` e `1.5`, o que significa 100% e 150% a.m. |
| A2 | Origem e valor da taxa base | 1,00% a.m., versionada por data de vigência em tabela própria (seed de demonstração) | Taxa de fundo muda com política ou indexador e precisa ser auditável no tempo |
| A3 | Unidade do prazo | Dias corridos entre a data da operação e o vencimento, convertidos em meses por ACT/30 (expoente pode ser fracionário). O motor recebe o prazo já em meses | O operador informa a data de vencimento. Os golden cases usam meses inteiros e entram direto no motor |
| A4 | Data da operação | Data corrente no fuso `America/Sao_Paulo` no momento da simulação ou liquidação | A mesa opera no horário de Brasília |
| A5 | Vencimento hoje ou no passado | Rejeitado com 422 (prazo precisa ser maior que zero) | Título vencido não é cessão, é cobrança |
| A6 | Qual câmbio vale | A taxa com maior `vigente_em` menor ou igual ao instante da liquidação. ID e valor da taxa são gravados na liquidação | Reprodutível e auditável. "A mais recente" sem corte temporal é ambígua |
| A7 | Câmbio desatualizado | Taxa vigente com mais de 24h (configurável) recusa a liquidação em USD com 422 `FX_RATE_STALE`. Nunca há fallback silencioso | Pagar com taxa errada é perda financeira direta |
| A8 | Direção da cotação | BRL/USD = BRL por 1 USD. Valor em USD = VP em BRL / taxa | Convenção do golden case C3 |
| A9 | Moeda do título | Títulos sempre em BRL nesta versão. Pagamento em BRL ou USD | É o caso descrito no enunciado. Título em USD vira pergunta ao negócio |
| A10 | Deságio | Deságio = valor de face menos VP em BRL arredondado | Garante face = VP + deságio, ao centavo |
| A11 | Liquidação repetida | Um recebível liquida uma única vez. Toda liquidação exige `Idempotency-Key` (UUID). Mesma chave e mesmo payload: devolve a liquidação original. Mesma chave com payload diferente: 422. Recebível já liquidado com outra chave: 409 | Retry de rede e duplo clique não podem pagar duas vezes |
| A12 | Correção de liquidação | Não existe UPDATE nem DELETE. Correção futura é estorno (novo lançamento) | Auditabilidade. Estorno fica fora do escopo |

## 3. Precisão numérica

- **Aplicação:** `BigDecimal` sempre, criado a partir de `String`. `double` e `float` proibidos em valores e taxas.
- **Intermediários:** `MathContext.DECIMAL128` (34 dígitos significativos). Potência inteira com `BigDecimal.pow(int, mc)`. Potência fracionária com a biblioteca `big-math`.
- **Momento do arredondamento:** `HALF_EVEN`, 2 casas, uma única vez no VP em BRL. Em cross-currency, o VP em BRL já arredondado é convertido e o valor em USD é arredondado (HALF_EVEN, 2 casas), como no C3.
- **Isolamento:** a política de arredondamento vive num componente próprio (`RoundingPolicy`), configurável, para que mudar a regra não toque o motor.
- **Banco (PostgreSQL):** dinheiro em `NUMERIC(19,2)`, taxas e spreads em `NUMERIC(12,8)`, câmbio em `NUMERIC(18,8)`.
- **API:** dinheiro e taxas trafegam como string JSON (`"92859.94"`). O frontend nunca faz conta financeira. A simulação é sempre calculada no backend.

## 4. Perguntas ao negócio

1. Prazo em dias corridos/30 ou dias úteis/252 (padrão de mercado para CDI)? Qual calendário de feriados?
2. A taxa base é fixa por política do fundo ou indexada (ex.: % do CDI)? Quem pode alterá-la e com qual aprovação?
3. Qual a fonte oficial de câmbio (PTAX do BCB, cotação da mesa)? Qual a idade máxima aceitável da taxa?
4. Título pode ser emitido em USD? Há outras moedas no horizonte?
5. Liquidação acima de certo valor exige dupla aprovação (maker-checker)?
6. Em caso de liquidação errada, o processo é estorno com novo pagamento ou ajuste na conciliação?
7. O spread depende só do tipo do título ou também do cedente, rating ou prazo?

## 5. Critérios de aceite

**Corretude**
- C1, C2 e C3 reproduzidos ao centavo em teste automatizado que roda no CI e bloqueia o merge.
- VP + deságio = valor de face em todos os testes.

**Segurança e integridade**
- Duas liquidações simultâneas do mesmo recebível: exatamente uma é gravada (teste de concorrência contra PostgreSQL real).
- Retry com a mesma `Idempotency-Key` não cria segunda liquidação.
- Nenhum SQL concatenado com input. Toda entrada validada. Erro nunca responde 2xx. Erros em ProblemDetail (RFC 9457).
- Liquidação gravada não pode ser alterada: sem endpoint e com bloqueio no banco.

**Desempenho (ambiente local com Docker)**
- Simulação: p95 abaixo de 100 ms.
- Liquidação: p95 abaixo de 300 ms.
- Extrato com 100 mil liquidações: p95 abaixo de 500 ms na primeira página.

**Usabilidade**
- Valor líquido visível no painel em até 500 ms depois que o operador para de digitar (debounce de 300 ms + resposta).
- Erros de negócio em linguagem do operador (ex.: "Taxa de câmbio desatualizada. Atualize antes de liquidar.").
