# Testes

## Como rodar

```bash
./gradlew :core:domain:test testDebugUnitTest   # JVM, sem emulador
./gradlew connectedDebugAndroidTest             # precisa de emulador ou aparelho
```

## Cenários cobertos

| Cenário | Onde |
|---|---|
| Viagem simples (os dois modelos, mínimo sustentável) | `PricingEngineTest` |
| Viagem com paradas, múltiplas paradas, espera, ajuste, pedágio e despesas | `PricingEngineTest` |
| Pedágio entra uma vez e sem margem; combustível fora do modelo comercial | `PricingEngineTest` |
| Alteração do preço do combustível, do consumo e da margem (0 e limite de 90%) | `PricingEngineTest` |
| Taxa de espera com tolerância; deslocamento vazio só nos custos | `PricingEngineTest` |
| Comercial abaixo do custo recebe complemento; arredondamentos; soma das linhas = total (varredura) | `PricingEngineTest` |
| Ajuste manual: diferença R$ e %, abaixo do mínimo, volta ao sugerido | `QuoteUseCasesTest`, `QuoteFeatureTest` |
| Pedágio da rota × digitado × valor desconhecido (nunca inventado) | `QuoteUseCasesTest`, `RouteMapperTest`, `QuoteFeatureTest` |
| Duplicação, recálculo preservando id, resumo mensal, CSV | `QuoteUseCasesTest` |
| Leitura da resposta real da Routes API (MockWebServer), cabeçalhos e corpo do pedido | `RouteMapperTest` |
| Paradas na ordem; reordenar recalcula na nova ordem; rota alternativa usada no cálculo | `TripPlannerViewModelTest` |
| Erro de geocodificação, permissão de localização negada, falta de internet (falha de rota) | `TripPlannerViewModelTest` |
| Sem chave de mapas: texto livre + distância manual; valores inválidos bloqueiam | `TripPlannerViewModelTest` |
| Persistência: JSON do orçamento, payload corrompido, veículo, DataStore real | `LocalStorageTest` |
| Room: salvar, buscar sem acento, atualizar, excluir (instrumentado) | `QuoteDaoTest` |
| Compartilhamento: texto, marcação de enviado; visão do passageiro fecha com o total | `QuoteFeatureTest` |
| Formulário de preços (vírgula, negativos, limites, combustível vazio) | `PricingSettingsFormTest` |
| Início: checklist e orçamento de exemplo identificado | `HomeViewModelTest` |
| Interface: visão do passageiro (Compose) e jornada completa nova viagem → orçamento | `PassengerQuoteContentTest`, `QuoteJourneyTest` |

## Situação na entrega

- 70 testes unitários passando (domain 36, data 10, trip 11, quote 8, settings 3, home 2).
- Detekt e Android Lint sem erros.
- `assembleDebug` e `assembleRelease` (R8) gerados localmente.
- 4 testes instrumentados passando no emulador API 34 do GitHub Actions (Room, tela do passageiro, jornada completa e tour de capturas).
- O tour salva capturas das telas principais no artifact `capturas-de-tela` para revisão visual.
- A geração de PDF/imagem usa APIs gráficas do Android e é exercitada manualmente e pelo fluxo de compartilhamento;
  ainda não há teste automatizado que abra o PDF gerado.
