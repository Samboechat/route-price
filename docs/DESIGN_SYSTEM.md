# Design System

Código: `core/designsystem`. Direção: aplicativo financeiro de mobilidade — grafite profundo, preto como base,
verde-limão como única cor de destaque, números grandes e tabulares, bordas discretas, quase sem sombra.

## Cor

| Token (escuro) | Hex | Uso |
|---|---|---|
| background / surface | `#0A0B0D` | Fundo |
| surfaceContainerLow / Container / High / Highest | `#111316` / `#171A1E` / `#1E2227` / `#272C33` | Cards, campos, blocos de métrica |
| outlineVariant / outline | `#272C33` / `#353B44` | Bordas de 1 dp |
| primary | `#C6F45A` (limão) | Ação principal, preço em destaque, rota no mapa |
| secondary | `#2ED3A0` (esmeralda) | Valores positivos |
| error | `#FF6B6B` | Erros, valores abaixo do custo |

Tema claro: fundo `#F6F7F4`, cards brancos e o verde escurecido para `#3F6A00` (contraste AA sobre branco).

Cores semânticas (`RpTheme.extended`): **Confirmado** azul (`#6CC4FF`), **Informado** limão, **Estimado** âmbar
(`#F5BE4F`), positivo, negativo, alerta, rota e rota alternativa.

## Tipografia

- **Manrope** (variável) — textos e interface.
- **Space Grotesk** (variável) — títulos de destaque e preços; `NumericStyle` usa algarismos tabulares (`tnum`).

Escala Material 3 ajustada: `displayMedium` 40 sp para o preço sugerido, `titleMedium` 16 sp para títulos de card,
`labelSmall` 11 sp com espaçamento largo para rótulos de seção em caixa alta.

## Espaço e forma

Grade de 4 dp (`Spacing`): 4 · 8 · 12 · 16 · 24 · 32. Margem lateral de tela 20 dp; botões com 56 dp de altura;
alvos de toque de 48 dp no mínimo. Cantos: 8 (chips), 12, 16 (campos, botões, métricas), 24 (cards).

## Componentes

| Componente | Função |
|---|---|
| `RpPrimaryButton`, `RpSecondaryButton`, `RpTextButton`, `RpIconButton` | Ações; o primário aceita estado de carregamento |
| `RpCard` | Superfície padrão; `highlighted` para o bloco principal da tela |
| `RpTextField`, `RpNumberField` | Campos com prefixo/sufixo, ajuda e erro; o numérico filtra caracteres e usa teclado decimal |
| `MetricTile`, `PriceDisplay` | Indicadores e preço em destaque |
| `SourceBadge`, `StatusChip`, `DemoBadge` | Origem do dado, situação do orçamento, marcação de exemplo |
| `RouteSketch`, `RouteTimeline`, `StopMarker` | Desenho da rota a partir da polyline e linha do tempo A → paradas → B |
| `LoadingState`/`SkeletonBlock`, `EmptyState`, `ErrorState`, `Banner` | Estados de carregamento, vazio, erro e feedback (info/alerta/erro) com `liveRegion` para leitores de tela |
| `Wordmark`, `BrandMark` | Marca |

## Acessibilidade

- Contraste: textos principais sobre fundo escuro acima de 12:1; verde-limão só sobre grafite/preto ou como fundo de texto preto.
- Todos os ícones de ação têm `contentDescription`; ícones decorativos usam `null`.
- Títulos de seção marcados como `heading()`; banners anunciados com `liveRegion`.
- Linhas clicáveis declaram `Role` e altura mínima de 48 dp; a ordem das paradas é alterável por botões (não só por arrastar).
- Tamanhos em `sp`, respeitando a fonte do sistema.

## Movimento

Transições de navegação curtas (260 ms, deslize de 1/6 da tela + fade), expansão animada de seções
(`animateContentSize`), skeleton com pulso suave. Nada de animação decorativa contínua.
