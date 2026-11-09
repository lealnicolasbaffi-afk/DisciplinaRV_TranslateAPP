# Tradutor Offline — Android

Protótipo acadêmico nativo Kotlin, PT-BR ↔ EN-US. Esta pasta é a fonte de verdade Android; o contrato está em `../shared/ESPECIFICACAO.md` e `../shared/medicoes.schema.json`. A frente iOS é independente. Nenhuma medição física foi realizada.

## Executar os checks

Requisitos: JDK completo 17, Android SDK Platform 35 e Build Tools 35.0.0 instalados; internet na primeira resolução das dependências.

```bash
cd /caminho/TradutorOfflineMultiplataforma/android
export JAVA_HOME=/caminho/do/jdk-17
export ANDROID_HOME=/caminho/do/Android/Sdk
./Configuration/verificar-wrapper.sh
./Configuration/check.sh
```

O script usa o cache Gradle em `work/gradle-home`, sem instalar SDK ou aceitar licenças. O Gradle Wrapper 8.11.1 está incluído com checksum da distribuição. APK esperado **após build bem-sucedido**: `build/outputs/apk/debug/android-debug.apk`. A instalação pode ser feita pelo Android Studio ou `adb install -r build/outputs/apk/debug/android-debug.apk`, com depuração USB autorizada pelo usuário.

Resultado executado neste ciclo e bloqueios: consulte `Docs/STATUS.md` e `Docs/TESTES.md`. Executar o wrapper não comprova compilação ou funcionamento offline.

## Preparar o aparelho

1. Instale o app e copie para o aparelho os ZIPs oficiais `vosk-model-small-pt-0.3.zip` e `vosk-model-small-en-us-0.15.zip`. Eles não fazem parte do APK.
2. Na tela, use **Importar modelo Vosk PT (ZIP)** e **Importar modelo Vosk EN (ZIP)**. Não descompacte os arquivos antes da importação.
3. Instale previamente as vozes offline pt-BR e en-US nas configurações de texto para fala do Android. As opções e os nomes variam conforme o motor.
4. Com Wi-Fi ligado, toque em **Preparar idiomas**. Esse passo baixa o pacote português do ML Kit e verifica os modelos e vozes. O inglês é incorporado pelo ML Kit. Gravação depende do Vosk preparado; tradução de texto depende do pacote ML Kit. Uma falha posterior de voz preserva o texto e registra a falha.
5. Ative modo avião, desligue Wi-Fi e execute o roteiro em `Docs/DEMONSTRACAO.md`. Marque a confirmação manual apenas nessa condição.

Detalhes e fontes oficiais: `Docs/CONFIGURACAO.md`.

## Tela e fluxo

Direção → preparação → estado → gravação → transcrição editável/ouvir original → tradução/reprodução → medições/avaliação/exportação. Após **Parar** ou 20 segundos, o app transcreve, traduz e fala automaticamente. **Traduzir e falar** permite corrigir o texto, mantendo a origem e o instante da rodada. **Nova rodada** limpa áudio, texto e avaliações; escolha essa ação para começar um texto digitado independente.

O áudio de reserva deve ser WAV PCM16, mono, 16 kHz, até 20 segundos. Microfone e importação preservam o original no cache apenas para o replay da rodada atual. Não há envio remoto de fala, tradução ou síntese solicitado pelo app. A seleção de voz offline se baseia no que o motor Android declara; o teste em modo avião continua necessário.

As medições são salvas atomicamente em `files/medicoes.json` no armazenamento privado do app. Resultados/falhas são persistidos automaticamente; notas, confirmação e qualidades são persistidas por **Salvar avaliação**. **Exportar medições JSON** usa o seletor de arquivos. Exportação vazia é `[]`. Desinstalação/limpeza dos dados apaga medições e modelos; exporte antes. Reprodução repetida preserva a primeira latência; corrigir e retraduzir atualiza a mesma rodada, incluindo o tempo de revisão humana. Não misture esses casos com primeiras tentativas.

## Módulos

- `Sources/App`: Activity e composição da tela nativa; insets para Android 15.
- `Sources/Domain`: direção, estados, origem, qualidades, medição v1 e contratos dos serviços locais.
- `Sources/Features/Conversation`: coordenação cancelável, timeouts e proteção por geração.
- `Sources/Services/Speech`: Vosk local e importação atômica/validada dos modelos.
- `Sources/Services/Audio`: captura PCM, WAV, importação e replay com foco de áudio.
- `Sources/Services/Translation`: ML Kit, preparação explícita; tradução não baixa modelos.
- `Sources/Services/Synthesis`: TTS com vozes instaladas sem exigência de rede, callbacks e foco de áudio.
- `Sources/Persistence`: carga, atualização e exportação JSON com todas as propriedades e `null` explícitos.
- `Resources`, `Configuration`, `Tests`, `Docs`: recursos pequenos, build, testes e roteiros comuns.

Não há cópia divergente do projeto na pasta do novo chat. Modelos, caches de ferramentas e áudios não devem ser versionados. Não há proposta de alteração ao contrato shared neste ciclo.
