# Arquitetura

Clean Architecture + MVVM, em módulos Gradle. As dependências apontam sempre para dentro (para o domínio).

```
                ┌──────────────── app ────────────────┐
                │ MainActivity · NavHost · Hilt · tema │
                └──────┬──────────────────────┬───────┘
          ┌────────────┼───────────┬──────────┼────────────┐
   feature:home  feature:trip  feature:quote  feature:history  feature:settings
          │  (telas Compose + ViewModels; sem acesso direto a APIs)  │
          └────────────┬──────────────────────┬─────────────┘
               core:designsystem          core:data
           (tema, componentes, textos)  (Room, DataStore, Retrofit,
                       │                 localização, conectividade)
                       └──────────┬──────────┘
                             core:domain
              (Kotlin puro: modelos, motor de preços, casos de uso,
               interfaces de repositório, formatação BR)
```

| Módulo | Responsabilidade | Depende de |
|---|---|---|
| `core:domain` | Modelos (`TripQuote`, `Money`, `PricingSettings`…), `PricingEngine`, casos de uso, interfaces de repositório. JVM pura, testável sem Android. | — |
| `core:data` | Implementações: Room (orçamentos e veículos), DataStore (perfil, preços, preferências), Routes API e Places API (New) via Retrofit/OkHttp/kotlinx.serialization, localização (Fused), Geocoder, conectividade, exportação. | domain |
| `core:designsystem` | Tema escuro/claro, tipografia, espaçamentos, componentes, estados, `QuoteCopy` (textos), `Brand` (marca). | domain |
| `feature:*` | Uma tela (ou grupo) com seu ViewModel e sua rota de navegação tipada. Recebem callbacks de navegação; não conhecem outras features. | domain, designsystem |
| `app` | Liga tudo: Application Hilt, Activity, NavHost, chave da Maps Platform via `BuildConfig`. | todos |

## Regras

- **Composables não têm regra de negócio.** Eles recebem estado imutável e chamam funções do ViewModel.
- **ViewModels não falam com APIs.** Usam interfaces do domínio injetadas pelo Hilt.
- **Estado previsível:** cada tela expõe um `StateFlow<UiState>` e eventos únicos (navegação, compartilhamento) por `Channel`.
- **Erros tipados:** a camada de dados converte exceções em `DomainError` (sem detalhes técnicos nem dados pessoais);
  a interface mostra a mensagem de `QuoteCopy.error`.
- **Logs:** o app não registra endereços, nomes nem respostas de API; o OkHttp não tem interceptor de log.
- **Orçamento autocontido:** `TripQuote` guarda cópia da rota, do veículo, do motorista, dos parâmetros e do resultado.
  Mudar configurações depois não altera orçamentos antigos; o histórico persiste o JSON completo, com colunas indexadas
  para lista e busca (sem acentos).

## Fluxos principais

**Nova viagem** — `TripPlannerViewModel`
1. Embarque/destino/paradas via busca (Places autocomplete + detalhes), localização atual (permissão pedida só nessa
   hora), toque no mapa (geocodificação reversa) ou texto livre (modo manual).
2. Cada mudança dispara (com debounce) `RouteRepository.computeRoutes` com as paradas **na ordem da lista**.
   Sem paradas, pede rotas alternativas; o motorista escolhe pelo chip ou tocando na linha cinza do mapa.
3. Falhou (sem internet, sem rota, chave recusada)? A mensagem aparece e o motorista pode informar distância e tempo.
4. **Calcular preço** monta um `QuoteRequest`, chama `BuildQuoteUseCase` (motor de preços) e salva o orçamento.

**Orçamento** — `QuoteViewModel`: preços (mínimo, sugerido, final), avisos, comparação dos dois modelos (tocar troca e
recalcula), composição com a origem de cada valor, situação (rascunho/enviado/aceito/recusado), negociação,
compartilhamento, duplicar, editar, excluir.

**Passageiro** — `PassengerQuoteContent` e `QuoteDocumentRenderer` usam o mesmo `PassengerView`, então tela, PDF,
imagem e texto mostram exatamente os mesmos números.

## Offline

| Funciona offline | Precisa de internet |
|---|---|
| Histórico, busca no histórico, orçamentos salvos, compartilhar orçamento salvo, configurações, veículos, exportação, modo manual de distância | Busca de endereços, cálculo de rota, mapa, pedágio estimado |

A tela de nova viagem mostra um aviso quando o aparelho está sem conexão.

## Preparado para backend

Para mover rotas/endereços para um servidor próprio (ex.: esconder a chave), basta criar outra implementação de
`RouteRepository`/`PlacesRepository` em `core:data` e trocar o binding no `DataModule`. Nada nas telas muda.
