# Regras do projeto para agentes de IA

1. Antes de qualquer mudança, leia `SPEC.md` e a spec da entrega atual em `docs/sdd/`.
2. Dinheiro, taxas e câmbio: somente `BigDecimal` criado a partir de `String`. Nunca `double`, `float`, `Math.pow` ou conversões para ponto flutuante.
3. Arredondamento somente via `RoundingPolicy`. Nada de `setScale` espalhado pelo código.
4. SQL sempre parametrizado. Nunca concatenar input em query.
5. Nunca engolir exceção. Nunca responder 2xx em caso de erro. Erros no formato ProblemDetail (RFC 9457).
6. Uma tarefa da spec por vez. Ao terminar, pare, mostre o diff e o resultado dos testes e aguarde aprovação.
7. Regra financeira nova começa pelo teste com o valor esperado, depois a implementação.
8. Não adicione dependência, camada ou arquivo fora do escopo da tarefa sem perguntar.
9. Se uma premissa do SPEC parecer errada ou ambígua, pergunte. Não decida sozinho.
10. Nunca commitar na `main`. Commits no padrão Conventional Commits.
