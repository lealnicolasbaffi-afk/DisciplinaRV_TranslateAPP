# Testes e evidências — Android 0.2.0

## Câmera: verificação anterior à integração — 09/11/2026

**Verificação após integrar com a versão 0.2.0:**
`Configuration/check.sh --write-locks assembleDebugAndroidTest` concluiu com
BUILD SUCCESSFUL: APK principal e APK de instrumentação compilados, lint sem
erros e 29 testes JVM contabilizados, sendo 27 aprovados e 2 ignorados pela
ausência dos ZIPs oficiais em `work/models`; zero falhas/erros. Os testes
instrumentados foram compilados, mas não executados neste ciclo. Os registros
do SM-A546E abaixo são evidências anteriores preservadas, não uma nova execução.

Na implementação da câmera anterior à integração com a versão 0.2.0,
`Configuration/check.sh --write-locks` passou: assembleDebug, 23 testes JVM de
regressão sem falhas e lintDebug. A resolução de dependências também passou.
APK instalado no Samsung SM-S926B / Android 16; Activity principal abriu e botão
confirmado via UI. O aparelho bloqueou antes do teste interativo de câmera.
Não há novo teste automatizado de câmera/OCR. Preview, OCR, alinhamento e modo
avião dessa função ainda exigem o [roteiro físico](CAMERA.md).

## Evidências da versão 0.2.0 recebida do repositório

Execuções em 09/11/2026 no código desta pasta. Aparelho físico: Samsung SM-A546E, Android 16/API 36, arm64-v8a.

## Build Android real

Com JDK Temurin 17.0.18 do Unity, SDK Platform 35 e Build Tools 35.0.0 instalados em work/sdk:

```powershell
.\Configuration\check.ps1 -Offline -DeviceTests
```

Resultado: **BUILD SUCCESSFUL**, incluindo assembleDebug, testDebugUnitTest, lintDebug e assembleDebugAndroidTest. APK debug 0.2.0, versionCode 2, pacote br.edu.tradutoroffline. APK instalado no aparelho por ADB. O Wrapper teve SHA-256 conferido. O build substitui o antigo bloqueio por SDK ausente; o harness com stubs da entrega anterior não é a evidência usada aqui.

Lint terminou com **0 erros e 20 advertências**: versões mais novas disponíveis, textos da UI em português diretamente no código, duas verificações de API redundantes e ícone monocromático opcional ausente. Não foi usado baseline para esconder erros.

## Testes JVM: 29 passaram

| Grupo | Testes | Falhas | Ignorados |
| --- | ---: | ---: | ---: |
| WAV/PCM e limites | 10 | 0 | 0 |
| ZIP, caminhos, layout e limites | 8 | 0 | 0 |
| SHA-256, truncamento, tamanho e cancelamento | 4 | 0 | 0 |
| ZIPs oficiais PT e EN reais | 2 | 0 | 0 |
| Contrato/JSON e medições | 5 | 0 | 0 |

Os testes de ZIPs oficiais usam fixtures em work/models. Sem esses arquivos, apenas esses dois casos são ignorados ao repetir em outro ambiente. Nesta execução ambos rodaram. Nenhuma fixture foi inserida no histórico real de medições do usuário.

## Instrumentação no aparelho: 3 passaram

Executados novamente após a mudança final da tela, em 11,188 segundos:

- Abertura da Activity e preservação do texto após recriação.
- Persistência e recuperação de backup AtomicFile, com exportação UTF-8.
- Importação dos dois modelos completos, abertura nativa Vosk e rejeição de silêncio nos dois sentidos.

Os testes usam diretórios isolados no cache; não criam resultados fictícios no histórico do usuário. VoskNativeTest exige os dois ZIPs no APK de testes. Os modelos não entram no APK principal.

## Preparação real do app: 1 teste passou

A preparação no Samsung levou 68,150 segundos neste ensaio: os dois ZIPs foram baixados pelo próprio código Android, verificados por SHA-256, importados e abertos nativamente; o pacote português do ML Kit foi instalado e ambas as vozes locais foram verificadas.

Modelos extraídos: PT 53.552.717 bytes; EN 70.898.967 bytes. Vozes selecionadas pelo motor Google: pt-BR-language e en-US-language. Esses tamanhos são arquivos extraídos, não RAM. A preparação permanece instalada para uso normal do aplicativo.

## Tradução e voz offline: 1 teste passou

O script ativou modo avião e desligou Wi-Fi; o teste verificou AIRPLANE_MODE_ON=1 e ausência de rede ativa antes e depois do ensaio. Reabriu os modelos locais, conferiu o pacote já instalado e traduziu/reproduziu ambos os sentidos sem solicitar download.

| Direção | Entrada | Saída real | Chamada de tradução |
| --- | --- | --- | ---: |
| PT → EN | Bom dia. Onde fica a estação? | Good morning. Where is the station? | 318,94 ms |
| EN → PT | The station is two blocks away. | A estação fica a dois quarteirões de distância. | 216,42 ms |

O motor confirmou início e conclusão de síntese em ambos os sentidos. O teste completo levou 11,837 segundos. São duas chamadas de serviço neste aparelho, não médias de desempenho, latência acústica ou medições de microfone. Não foram adicionadas à coleção do usuário.

Ao terminar, o script restaurou modo avião desligado e Wi-Fi ligado; esses valores foram consultados novamente pelo ADB. O script conserva o estado anterior em cada nova execução.

## Fala humana e qualidade

Duas rodadas reais de microfone foram concluídas no Samsung e o usuário confirmou transcrição e áudio em ambos os sentidos. No segundo ensaio, confirmou ter ativado modo avião e desligado Wi-Fi. A campanha comum de 12 frases × 3 repetições, 36 tentativas, não foi concluída. Testes instrumentados e texto sintetizado não contam como repetições humanas. Memória, bateria, temperatura, ruído e qualidade estatística ainda não foram medidos.

O reconhecimento ocorre após Parar ou 20 segundos. Replays/correções atualizam a rodada e preservam rawInput/origem; não são novas tentativas. O callback TTS aproxima início de síntese, sem medir som no ambiente.


### Duas rodadas interativas reais

| Direção | Transcrição real | Tradução real | Parar → transcrição | Parar → início TTS |
| --- | --- | --- | ---: | ---: |
| PT → EN | bom dia onde fica a estação | Good morning, where is the station. | 2.504 ms | 3.009 ms |
| EN → PT | the station is two blocks away | A estação fica a dois quarteirões de distância | 2.114 ms | 2.482 ms |

Ambas têm origem microfone e failure=null. As chamadas de tradução levaram 218,24 ms e 145,79 ms. São duas observações, sem média ou controle de ruído. O JSON privado salvo foi lido por ADB e validado pelo validador do contrato v1: dois registros válidos. Evidência: Evidencias/medicoes-microfone.json e validation-device-json.log.

A caixa manual de modo avião não foi marcada nessas rodadas: airplaneModeConfirmed permanece false no JSON, sem alteração retroativa. A condição offline do segundo ensaio foi relatada pelo usuário. Separadamente, o teste instrumentado acima verificou modo avião e ausência de rede via API.
## Repetir no aparelho

```powershell
.\Configuration\device.ps1 -Serial SERIAL
.\Configuration\device.ps1 -Serial SERIAL -Prepare
.\Configuration\device.ps1 -Serial SERIAL -OfflineTest
```

A preparação e o ensaio offline exigem opt-in explícito nos testes. O script de offline testa tradução/voz e restaura rede em finally. Ele não executa gravações automáticas de pessoas.

## Evidências preservadas

- Evidencias/build-delivery.log e XMLs JUnit: build real e 29 testes.
- Evidencias/lint-results-debug.html e XML: relatório completo de lint.
- Evidencias/instrumentation-final.log: três testes nativos na versão final.
- Evidencias/device-prepare.log: download/instalação efetivos no aparelho.
- Evidencias/device-offline.log: rede ausente, traduções reais e callbacks TTS.

O plano de demonstração e os cenários de erro estão em DEMONSTRACAO.md. O schema comum permanece em ../../shared/medicoes.schema.json.
