# Configuração Android

## Versões fixadas

AGP 8.9.2; Gradle 8.11.1; Kotlin 2.2.0; bytecode Java/Kotlin 17; coroutines Android 1.10.2; Vosk Android 0.3.75; JNA AAR 5.18.1; ML Kit Translation 17.0.3. Testes: JUnit 4.13.2 e org.json 20240303. As versões diretas estão fixadas no build; o Wrapper verifica SHA-256. `gradle.lockfile` fixa os oito classpaths de compilação/runtime do app e dos testes unitários, nas variantes debug/release. O grafo foi resolvido sem dependências pendentes. Isso não comprova reprodutibilidade binária de um APK, que ainda não foi gerado.

`minSdk=26` (Android 8) permite `java.time` e as APIs de foco usadas diretamente. O ML Kit exige no mínimo API 23; essa não é a única restrição do app. `compileSdk=35`, `targetSdk=35`, Build Tools 35.0.0. AGP 8.9 suporta até API 35 e Gradle 8.11.1/JDK 17. Kotlin 2.2.0 cobre essa combinação na matriz oficial. O AAR Vosk 0.3.75 publicado informa `minCompileSdk=1`; a configuração API 36 do demo não impõe esse nível ao app consumidor. Target 35 é uma escolha para a prova de conceito, sem preparação para publicação na Play Store.

O aparelho Android ainda não foi informado. Confirmar fabricante/modelo, API, arquitetura, RAM/espaço livre, motor TTS e vozes antes da validação física. O app registra `Build.MANUFACTURER`, `Build.MODEL` e versão/API do sistema; esses valores não comprovam que é um aparelho físico quando usado em emulador.

## Ferramentas

Instale o JDK completo 17 e o Android SDK por uma distribuição oficial/Android Studio. Provisione Platform 35 e Build Tools 35.0.0 e examine/aceite as licenças nesse ambiente. Configure `JAVA_HOME` e `ANDROID_HOME` ou `local.properties` com `sdk.dir` para execução direta do Gradle; `Configuration/check.sh` requer `ANDROID_HOME`.

```bash
./Configuration/verificar-wrapper.sh
JAVA_HOME=/caminho/jdk-17 ANDROID_HOME=/caminho/sdk ./Configuration/check.sh
```

O script de checks não configura dispositivos nem instala modelos. `work/`, `.gradle/`, `build/` e `local.properties` ficam ignorados. O JDK local baixado durante o desenvolvimento é intermediário de verificação, sem redistribuição no pacote do projeto.

## Recursos locais separados do APK

Baixe os ZIPs diretamente da [lista oficial de modelos Vosk](https://alphacephei.com/vosk/models):

- [vosk-model-small-pt-0.3.zip](https://alphacephei.com/vosk/models/vosk-model-small-pt-0.3.zip), 31M na lista do fornecedor, licença Apache 2.0.
- [vosk-model-small-en-us-0.15.zip](https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip), 40M na lista do fornecedor, licença Apache 2.0.

Esses são tamanhos de download anunciados, não medição no aparelho. A interface exibe bytes dos arquivos extraídos no diretório do modelo ativo; isso não inclui overhead do sistema de arquivos. O importador aceita os nomes/estruturas desses modelos, valida caminhos, limites, CRC, arquivos essenciais e abertura nativa antes de ativar o modelo. O modelo anterior é preservado se a importação falhar. Durante a importação, coexistem temporariamente ZIP, extração e modelo anterior: deixe espaço livre. A validação estrutural/CRC não autentica a origem do ZIP; baixe de fonte oficial. Não há hash de modelo embutido neste ciclo.

O Vosk carrega apenas o modelo da direção atual e o fecha ao terminar. A documentação estima cerca de 300 MB de RAM para modelos pequenos; isso é uma indicação do fornecedor, **não uma medição deste app**. Tempo de carregamento entra em `transcriptionMS` nas rodadas de áudio. Não há cache persistente do modelo em RAM para mascarar esse custo.

**Preparar idiomas** solicita somente o download português do ML Kit com Wi-Fi; inglês é incorporado. O SDK administra seus arquivos. O app verifica disponibilidade antes de traduzir e não chama download em `translate`. Um download já aceito pelo SDK pode continuar após cancelamento da espera. Tamanho exato do pacote ML Kit e das vozes não é exposto pelas APIs usadas e aparece como indisponível.

O TTS exige correspondência pt-BR/en-US, `isNetworkConnectionRequired=false`, ausência de `KEY_FEATURE_NOT_INSTALLED` e confirmação da voz selecionada. Não escolhe automaticamente uma voz de rede. Essas declarações são fornecidas pelo motor TTS externo; elas não são prova de ausência de rede. Instale os dados de voz nas configurações do Android e demonstre o funcionamento em modo avião com Wi-Fi desligado. O app não inicia instalação de vozes automaticamente.

## Permissões, arquivos e interrupções

`RECORD_AUDIO` é solicitado em tempo de execução; negar permite usar arquivo de reserva. `INTERNET`/`ACCESS_NETWORK_STATE` servem à preparação do ML Kit. A consulta de serviços TTS está no manifesto. Não há permissão ampla de armazenamento: seletores SAF importam/exportam arquivos escolhidos. `allowBackup=false`, cleartext bloqueado, sem serviço de microfone em segundo plano. Dados e modelos ficam no armazenamento privado; o original fica no cache da rodada. Textos e avaliações são dados locais em claro, sem criptografia adicional neste protótipo.

Sair da Activity solicita cancelamento, solta áudio e registra falha quando há rodada. Chamadas nativas Vosk não são preemptíveis: liberam recursos quando retornam ou no próximo bloco PCM; o token da rodada rejeita callbacks antigos mesmo nesse intervalo. Reprodução usa foco de áudio; perda de foco também é erro. A orientação e recriação da Activity reinicializam a tela e exigem nova preparação das capacidades; o histórico JSON permanece, mas a tela só apresenta a rodada atual e o total salvo. Não há recuperação da rodada/áudio corrente após encerramento do processo. Testar chamadas, bloqueio de tela e rotação no aparelho real.

## Fontes primárias consultadas em 09/11/2026

- [AGP 8.9, compatibilidade](https://developer.android.com/build/releases/agp-8-9-0-release-notes), [matriz Kotlin/Gradle/AGP](https://kotlinlang.org/docs/gradle-configure-project.html).
- [Vosk Android demo e dependências](https://github.com/alphacep/vosk-android-demo/blob/master/app/build.gradle), [Vosk API](https://github.com/alphacep/vosk-api), [modelos](https://alphacephei.com/vosk/models).
- [ML Kit Android Translation](https://developers.google.com/ml-kit/language/translation/android), [modelo inglês incorporado](https://developers.google.com/android/reference/com/google/mlkit/nl/translate/TranslateRemoteModel), [limites de qualidade](https://developers.google.com/ml-kit/language/translation).
- [AudioRecord](https://developer.android.com/reference/android/media/AudioRecord), [TextToSpeech](https://developer.android.com/reference/android/speech/tts/TextToSpeech), [Voice](https://developer.android.com/reference/android/speech/tts/Voice), [UtteranceProgressListener](https://developer.android.com/reference/android/speech/tts/UtteranceProgressListener).
