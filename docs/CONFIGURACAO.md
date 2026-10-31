# Configuração do ambiente e das APIs

## Requisitos

- JDK 17 ou superior (o projeto compila com JDK 21).
- Android SDK com a plataforma **37** (Android Studio instala sozinho na primeira sincronização).
- Gradle: use o wrapper do projeto (`./gradlew`), versão 9.8.2.

## Segredos

Nada sensível vai para o Git. O build lê, nesta ordem:

1. `secrets.properties` na raiz do projeto (ignorado pelo Git);
2. variáveis de ambiente com o mesmo nome (usado no GitHub Actions).

```bash
cp secrets.properties.example secrets.properties
# edite e preencha MAPS_API_KEY
```

Sem `MAPS_API_KEY` o app **funciona em modo manual**: os endereços são digitados como texto e o motorista informa
distância e tempo. Mapa, busca de endereços e rotas ficam desativados com aviso na tela.

## Google Maps Platform

No [Console do Google Cloud](https://console.cloud.google.com/):

1. Crie (ou escolha) um projeto e ative o faturamento.
2. Ative as APIs:
   - **Maps SDK for Android** (mapa na tela de nova viagem);
   - **Routes API** (rotas reais com paradas na ordem, alternativas e pedágios);
   - **Places API (New)** (autocompletar e detalhes de endereço).
3. Crie uma chave de API e **restrinja**:
   - *Restrições de aplicativo*: Apps Android → adicione `com.routeprice.app` e `com.routeprice.app.debug` com o
     SHA-1 do certificado de cada um;
   - *Restrições de API*: somente as três APIs acima.
4. Coloque a chave em `secrets.properties` (`MAPS_API_KEY=...`) ou no secret `MAPS_API_KEY` do repositório.

Para obter o SHA-1:

```bash
# debug padrão da sua máquina
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android | grep SHA1
# release
keytool -list -v -keystore /caminho/release.jks -alias SEU_ALIAS | grep SHA1
```

### Por que a chave fica no app (e o que isso implica)

Qualquer chave embarcada num APK pode ser extraída. Por isso:

- O app envia os cabeçalhos `X-Android-Package` e `X-Android-Cert` em toda chamada REST (Routes e Places),
  o que permite usar a restrição por app Android também nessas APIs (`GoogleApiHeadersInterceptor`).
- A chave deve ter restrição de app **e** de API. Com isso, uma chave extraída não serve para outros apps nem outras APIs.
- Configure alertas e cotas de uso no Console.
- Segredos que precisem ser realmente privados (ex.: um fornecedor de pedágio pago) devem ficar num backend próprio.
  A arquitetura já isola os repositórios atrás de interfaces (`RouteRepository`, `PlacesRepository`) para trocar a
  implementação por um backend sem mexer nas telas.

Geocodificação reversa (endereço de um ponto tocado no mapa ou da localização atual) usa o `Geocoder` do próprio
Android, sem custo de API.

## Pedágios

O app usa apenas o valor que a Routes API devolve (`travelAdvisory.tollInfo.estimatedPrice` em BRL), marcado como
**Confirmado** para o passageiro. Quando a API indica pedágio sem valor, o app mostra "valor não informado" e pede que
o motorista digite o total (marcado como **Informado**). Nenhum valor é inventado ou estimado por tabela.

## Assinatura

| Variável | Uso |
|---|---|
| `DEBUG_KEYSTORE_PATH`, `DEBUG_KEYSTORE_PASSWORD`, `DEBUG_KEY_ALIAS`, `DEBUG_KEY_PASSWORD` | Opcional. Keystore de debug compartilhada: mesmo SHA-1 em todas as máquinas e no CI, para a restrição da chave funcionar com o APK do Actions. |
| `RELEASE_KEYSTORE_PATH`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD` | Release assinada. Sem elas, o release é gerado sem assinatura (não instalável). |

No GitHub, a keystore vai como texto base64 nos secrets `RELEASE_KEYSTORE_BASE64` / `DEBUG_KEYSTORE_BASE64`
(veja [GERAR_APK.md](GERAR_APK.md)). Nunca faça commit de `.jks`/`.keystore` — o `.gitignore` bloqueia.

## Permissões do app

| Permissão | Quando |
|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | Rotas, endereços e aviso de "sem internet". |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | Só quando o motorista toca em **Usar minha localização**. Se negar, o resto do app segue funcionando. |

Tráfego somente HTTPS (`network_security_config.xml`), sem backup em nuvem dos dados pessoais
(`data_extraction_rules.xml`) — o motorista exporta os dados quando quiser em **Configurações → Seus dados**.
