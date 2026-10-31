# Como gerar o APK

## Pelo GitHub (sem instalar nada)

Todo push nas branches `develop`/`main` (e todo pull request) roda o workflow **Android Build**:

1. Abra o repositório → **Actions** → **Android Build** → a execução mais recente.
2. Em **Artifacts**, baixe `route-price-debug-apk`.
3. Descompacte e instale `app-debug.apk` no celular (permita "instalar apps de fontes desconhecidas").

O APK debug sai **sem chave de mapas** a menos que o secret `MAPS_API_KEY` exista no repositório
(Settings → Secrets and variables → Actions). Sem a chave o app funciona em modo manual.

Para gerar manualmente: **Actions → Android Build → Run workflow**.

### Release assinada

Crie a keystore uma vez (guarde-a fora do repositório):

```bash
keytool -genkeypair -v -keystore route-price-release.jks -alias routeprice -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 route-price-release.jks > release.b64
```

Cadastre os secrets do repositório:

| Secret | Valor |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | conteúdo de `release.b64` |
| `RELEASE_KEYSTORE_PASSWORD` | senha da keystore |
| `RELEASE_KEY_ALIAS` | `routeprice` (ou o alias escolhido) |
| `RELEASE_KEY_PASSWORD` | senha da chave |

Com eles presentes, o workflow também publica o artifact `route-price-release-apk` (minificado com R8 e assinado).
Sem eles, o job informa no resumo que a release não foi assinada e entrega apenas o APK debug.

Opcional: `DEBUG_KEYSTORE_BASE64` (+ senha/alias) para o APK debug do CI ter sempre o mesmo SHA-1
e funcionar com a chave restrita do Google.

## Localmente

```bash
./gradlew testDebugUnitTest      # testes unitários de todos os módulos
./gradlew detekt lintDebug       # análise estática
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease        # precisa das variáveis RELEASE_* para sair assinado
./gradlew connectedDebugAndroidTest   # testes de interface (com emulador/aparelho conectado)
```

Ou no Android Studio: abra a pasta do projeto, aguarde a sincronização e use **Build → Build APK(s)**.

Instalar via cabo: `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
O pacote do debug é `com.routeprice.app.debug` (pode coexistir com o release `com.routeprice.app`).
