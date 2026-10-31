# Contribuindo

## Fluxo

1. Crie uma branch a partir de `develop` (`feat/...`, `fix/...`, `docs/...`).
2. Faça commits pequenos no padrão [Conventional Commits](https://www.conventionalcommits.org/pt-br/):
   `feat:`, `fix:`, `test:`, `docs:`, `refactor:`, `chore:`, `ci:`.
3. Antes de abrir o pull request para `develop`:
   ```bash
   ./gradlew detekt lintDebug :core:domain:test testDebugUnitTest assembleDebug
   ```
4. `main` recebe apenas versões estáveis via pull request a partir de `develop`. Sem push forçado.

## Regras do código

- Regra de negócio fica no domínio ou no ViewModel, nunca em Composables.
- Acesso a API só em `core:data`, atrás de uma interface de `core:domain`.
- Dinheiro sempre em `Money` (centavos); nada de `Double` para valores.
- Toda mudança no motor de preços vem com teste de valor conferido à mão e atualização de `docs/MOTOR_DE_PRECOS.md`.
- Textos visíveis ao passageiro ficam em `QuoteCopy`.
- Não registre em log endereços, nomes, telefones ou respostas de API.
- Dados de demonstração devem ter `isDemo = true` e aparecer marcados como "Exemplo".

## Segredos

Nunca faça commit de chaves, tokens, senhas, `secrets.properties`, `local.properties` ou keystores.
O CI falha se encontrar arquivos sensíveis ou algo com cara de chave do Google no código.
