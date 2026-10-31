# Changelog

Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/). Versionamento semântico.

## [Não lançado] — 0.1.0

### Adicionado
- Projeto Android multi-módulo (Kotlin, Compose, Material 3, Hilt, Room, DataStore, Retrofit, Navigation tipada).
- Motor de preços com Modelo A (tarifa comercial) e Modelo B (custos operacionais + margem), preço mínimo sustentável,
  preço sugerido, preço negociado com diferença registrada e arredondamento comercial.
- Nova viagem com mapa, autocompletar de endereços, localização atual, escolha no mapa, paradas ordenáveis,
  rotas alternativas, pedágio estimado e modo manual.
- Orçamento do motorista, visão do passageiro com "Entenda o valor da sua viagem" e origem de cada valor.
- Compartilhamento por WhatsApp, PDF, imagem e texto.
- Histórico com busca e filtros, duplicação, edição, recálculo e exclusão com desfazer.
- Perfil do motorista, perfis de veículo, parâmetros de preço com prévia, tema escuro/claro, exportação JSON/CSV.
- Design System (paleta grafite/limão, Manrope e Space Grotesk, componentes e estados).
- CI (compilação, Detekt, Lint, testes, varredura de segredos, revisão de dependências), build de APK e testes
  instrumentados no GitHub Actions.
