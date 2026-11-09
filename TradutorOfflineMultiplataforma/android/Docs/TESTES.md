# Testes e evidências

## Executado neste ciclo — 09/11/2026

O ambiente inicial oferecia runtime OpenJDK 21, sem `javac` disponível ao Gradle, sem Gradle global, SDK, `sdkmanager` ou `adb` localizados. Foi preparado um JDK completo Temurin 17.0.21+9 em `work/`, com SHA-256 conferido. Não foram instalados SDK, emulador ou modelos de fala completos, nem aceitas licenças Android automaticamente.

- Wrapper Gradle 8.11.1 executou `--version`; JAR e distribuição tiveram hashes conferidos. `Configuration/verificar-wrapper.sh` passou.
- `gradlew help --offline` com JDK 17 passou; configuração Kotlin DSL e plugins carregaram.
- Os oito classpaths de compilação/runtime do app e dos testes debug/release foram resolvidos e fixados em `gradle.lockfile`, sem dependências irresolvidas.
- **Compilação Kotlin integrada em JVM e 23 testes passaram**, usando um harness temporário em `work/harness`, JDK 17, Kotlin 2.2.0 e stubs `android-all:15-robolectric-12650502` para API 35. O harness compila os Sources reais e os Tests reais; não inicia Activity, áudio, Vosk nativo ou ML Kit. Ele não é o build Android.
- Os testes incluem 10 casos de WAV, 8 de ZIP/modelo e 5 de contrato/medições. As fixtures são sintéticas e identificadas; nenhuma entra no histórico do app.
- Validador Python do schema aceitou `[]` e rejeitou coleção sintética incompleta/com tipo inválido. As 12 frases Android são byte a byte iguais às de shared: seis PT e seis EN. XMLs, scripts shell, UTF-8 e whitespace foram verificados.
- **`assembleDebug testDebugUnitTest lintDebug` não executaram até o fim**: após resolver o JDK, o Gradle parou por `SDK location not found`. Nenhum APK foi gerado. Os testes acima foram executados pelo harness JVM; o task Android de testes e o lint continuam pendentes.

Comando do harness local usado, enquanto seus intermediários existirem:

```bash
JAVA_HOME="$PWD/work/jdk/jdk-17.0.21+9" \
GRADLE_USER_HOME="$PWD/work/gradle-home" \
./gradlew -p work/harness --offline --no-daemon test --console=plain
```

O sandbox restringe o socket local do daemon Gradle; a verificação foi executada com autorização automática para esse processo. Isso não indica falha do código. Logs locais: `work/build-check.log` (JDK inicial), `work/build-check-jdk17.log` (SDK ausente), `work/gradle-help.log`, `work/dependency-lock-all.log` e `work/harness/final-check.log`; resultados JUnit XML em `work/harness/build/test-results/test`. Esses arquivos eram intermediários ignorados, e não medições do aplicativo. Na limpeza solicitada em 09/11/2026, `work/` e `.gradle/` foram removidos: os logs, o harness temporário e o JDK local não acompanham mais a entrega. O resumo dos resultados executados permanece neste documento; para repetir os checks, prepare JDK/SDK e use `Configuration/check.sh`.

Evidência resumida do bloqueio nativo:

```text
Could not determine the dependencies of task ':compileDebugJavaWithJavac'.
SDK location not found. Define a valid SDK location with an ANDROID_HOME
environment variable or by setting the sdk.dir path in local.properties.
BUILD FAILED
```

## Cobertura e limites

WAV: PCM16 mono/16 kHz, cabeçalho, chunks auxiliares e padding, truncamento, codificação/taxa/canais inválidos, duração, arquivo vazio. ZIP: dois layouts oficiais diferentes (PT legado plano; EN em am/conf/graph), caminho inseguro/outro modelo, entrada excessiva, quantidade excessiva, arquivo essencial ausente e cancelamento. São parsers puros; não provam abertura nativa de um modelo real no Android.

Medições: todas as 20 propriedades obrigatórias, `null` explícito, acentos/aspas/quebras de linha, roundtrip, falha, correção preservando UUID/data/origem/rawInput e tempo da transcrição, e relógio monotônico sem referência para texto digitado. Recuperação `AtomicFile`, SAF e ciclo de vida foram revisados estaticamente; precisam de teste Android. Não há cobertura automatizada da tela e dos serviços reais neste ciclo.

## Repetir os checks nativos

Com JDK completo 17 e SDK configurado/licenciado:

```bash
JAVA_HOME=/caminho/jdk-17 ANDROID_HOME=/caminho/sdk ./Configuration/check.sh
```

Isso executa `assembleDebug testDebugUnitTest lintDebug`. Só após sucesso instalar o APK e passar aos testes físicos. Para conferir uma exportação real, em Python com `jsonschema` instalado:

```bash
python3 Tests/validar_medicoes.py /caminho/medicoes.json
```

O validador usa o schema comum inalterado, incluindo formatos UUID e data/hora. Schema válido não comprova origem física, qualidade ou condição de rede.

## Plano físico comum — 0/36 tentativas executadas

Confirmar primeiro aparelho, versão/API, arquitetura, motor/vozes TTS e espaço livre. Não preencher resultados antes das execuções. Usar as 12 frases em `Tests/frases.csv`, com três repetições por aparelho: 36 primeiras tentativas de microfone planejadas. Anotar ID e repetição em `notes`. Correções/replays atualizam a rodada e não contam como nova repetição; usar **Nova rodada** para cada tentativa planejada.

Registrar ruído, distância, falante, versão, bateria, aquecimento, execução fria/quente e rede. Separar avaliação de transcrição original, tradução e voz antes de avaliar o fluxo completo. Com modo avião e Wi-Fi desligado, confirmar que nenhum estágio pede rede. Medir memória em rodada separada com Android Profiler; registrar separadamente o tamanho dos modelos e o espaço total do app mostrado pelo SO. Bytes dos arquivos Vosk não são RAM nem espaço alocado em disco.

Exercitar também: permissão negada/permanentemente negada; ambos os sentidos; parada manual e limite automático de 20 s; silêncio; chamadas e concorrência de microfone/áudio; perda de foco, bloqueio/rotação/saída da tela; arquivo corrompido, estéreo ou longo; ZIP ausente/corrompido/idioma errado; pacote ML Kit ou voz removidos depois da preparação; falta de espaço; timeouts e repetição após erro; edição com origem preservada; replay sem reescrever latência; reinício do app e exportação/validação do JSON; gravação atômica interrompida. Comparar separadamente microfone, arquivo, texto e correções.

Tempos usam `SystemClock.elapsedRealtime`/`elapsedRealtimeNanos`. Manual: referência no clique **Parar**; automático: fim lógico da captura antes da finalização WAV; arquivo: fim da importação; digitado: sem referência. `transcriptionMS` termina na transcrição, `textMS` ao retornar a tradução, `translationMS` mede somente a chamada `Translator.translate` até retornar (a verificação do pacote e a criação do cliente ficam fora desse intervalo), e `audioStartMS` termina em `onStart` do TTS. Este callback aproxima disponibilidade, sem medir saída acústica. Revisão humana entra nos tempos de uma correção. Medidas indisponíveis permanecem `null`; nenhuma falha vira zero.
