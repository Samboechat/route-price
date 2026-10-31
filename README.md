# Route Price

> Nome provisório. Marca centralizada em `core/designsystem` (`Brand.kt` e `res/values/brand.xml`).

Aplicativo Android para motoristas que fazem **viagens particulares** negociadas direto com o passageiro.
O motorista informa embarque, paradas e destino; o app traça a rota real, calcula o preço com base em distância,
tempo, custos do veículo, pedágios e margem, e gera uma **proposta profissional** para enviar por WhatsApp, PDF ou imagem.

<p>
<b>Kotlin</b> · Jetpack Compose · Material 3 · MVVM + Clean Architecture · Hilt · Room · DataStore · Retrofit/OkHttp ·
kotlinx.serialization · Navigation Compose (rotas tipadas) · Google Maps SDK · Routes API · Places API (New) · Android 9+ (API 28)
</p>

## O que o app faz

| Área | Funcionalidades |
|---|---|
| **Nova viagem** | Busca de endereço com autocompletar, localização atual (permissão pedida só no momento do uso), escolha no mapa, paradas intermediárias (adicionar, remover, reordenar), rota real respeitando a ordem das paradas, rotas alternativas, distância, tempo e pedágio estimado. Sem chave ou sem rota: modo manual de distância e tempo. |
| **Preço** | Dois modelos — **tarifa comercial** (km, minuto, paradas, espera, mínima, ajuste) e **custos operacionais + margem** (combustível, manutenção, desgaste, hora do motorista) — com **mínimo sustentável**, **sugerido** e **final negociado** (diferença registrada). [Fórmulas](docs/MOTOR_DE_PRECOS.md). |
| **Passageiro** | Proposta com motorista, trajeto, desenho da rota, distância, tempo, pedágios, composição do valor, total, validade e a seção **"Entenda o valor da sua viagem"**. Cada valor é marcado como *Confirmado* (fonte externa), *Informado* (motorista) ou *Estimado* (app). Nada de preço de concorrente, tarifa oficial ou pedágio inventado. |
| **Compartilhar** | WhatsApp (imagem + texto), PDF, imagem PNG e texto pelo menu de compartilhamento do Android. |
| **Gestão** | Histórico com busca (sem acentos) e filtros, duplicar, editar, recalcular, excluir com desfazer; situação do orçamento; resumo do mês; perfil do motorista; perfis de veículo; parâmetros de preço com prévia ao vivo; tema escuro/claro; exportação JSON e CSV. |
| **Offline** | Histórico, configurações, veículos e orçamentos salvos funcionam sem internet. Rotas e endereços avisam quando precisam de conexão. |

## Começando

```bash
git clone https://github.com/Samboechat/route-price.git
cd route-price
cp secrets.properties.example secrets.properties   # opcional: MAPS_API_KEY
./gradlew assembleDebug
```

APK em `app/build/outputs/apk/debug/app-debug.apk`. Sem a chave da Google Maps Platform o app roda em modo manual.
Configuração das APIs, restrições da chave e assinatura: [docs/CONFIGURACAO.md](docs/CONFIGURACAO.md).
APK pelo GitHub Actions, sem instalar nada: [docs/GERAR_APK.md](docs/GERAR_APK.md).

## Estrutura

```
app/                     Activity, navegação, Hilt, configuração (BuildConfig, manifesto, ícone)
core/domain/             Kotlin puro: modelos, motor de preços, casos de uso, interfaces
core/data/               Room, DataStore, Routes/Places via Retrofit, localização, exportação
core/designsystem/       Tema, tipografia, componentes, estados, textos e marca
feature/home|trip|quote|history|settings   Telas e ViewModels
config/detekt/           Regras do Detekt
docs/                    Arquitetura, motor de preços, configuração, APK, design system, testes
.github/workflows/       CI, build de APK e testes instrumentados
```

Detalhes em [docs/ARQUITETURA.md](docs/ARQUITETURA.md) e [docs/DESIGN_SYSTEM.md](docs/DESIGN_SYSTEM.md).

## Qualidade

| Verificação | Comando | CI |
|---|---|---|
| Testes unitários (motor de preços, dados, ViewModels) | `./gradlew :core:domain:test testDebugUnitTest` | `CI` |
| Detekt | `./gradlew detekt` | `CI` |
| Android Lint | `./gradlew lintDebug` | `CI` |
| Testes instrumentados (Room, Compose, jornada completa) | `./gradlew connectedDebugAndroidTest` | `Testes instrumentados` (emulador) |
| APK debug / release | `./gradlew assembleDebug assembleRelease` | `Android Build` (artifacts) |

Situação atual e cobertura por cenário: [docs/TESTES.md](docs/TESTES.md).

## Roadmap

- [x] Auditoria, arquitetura modular e Design System
- [x] Início, navegação, nova viagem com mapa, busca e paradas ordenadas
- [x] Motor de preços (dois modelos, mínimo sustentável, negociação) com testes
- [x] Orçamento do passageiro, PDF, imagem, WhatsApp e Sharesheet
- [x] Histórico, configurações, veículos, exportação
- [x] CI, Detekt, Lint, build de APK e testes instrumentados no GitHub Actions
- [ ] Testar em aparelhos reais com chave da Maps Platform restrita e ajustar a tela de nova viagem
- [ ] Ícones/identidade definitivos e nome final da marca
- [ ] Importar backup JSON
- [ ] Link público do orçamento (exige backend) e aceite pelo passageiro
- [ ] Backend opcional para esconder a chave das APIs e integrar fornecedor de pedágio confiável
- [ ] Publicação na Play Store (política de privacidade, assinatura de release, AAB)

## Licença

Ainda não definida. Até lá, todos os direitos reservados ao autor. As fontes Manrope e Space Grotesk são
distribuídas sob a SIL Open Font License ([docs/licenses](docs/licenses)).
