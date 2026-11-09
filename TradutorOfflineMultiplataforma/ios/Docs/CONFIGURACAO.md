# Configuração — iOS

Alvo: iPhone 14, iOS mínimo 18.0; confirme modelo comercial e versão exata nos Ajustes do aparelho. O campo `device` registra o modelo genérico e identificador de hardware do sistema, sem inferir que todo `iPhone` é um iPhone 14. `system` registra a versão do iOS.

## Ambiente e build

Requer Mac, Xcode 16+ com SDK iOS 18 ou posterior e XcodeGen. `project.yml` aponta para `Sources`, `Resources` e `Configuration/{Base,Debug,Release}.xcconfig`, e gera o Info.plist em `Configuration`. O `Package.swift` permite testar Domain/Persistence usando Foundation sem incorporar a tela ou os adaptadores Apple. O scheme Xcode inclui o target de testes `TradutorOfflineTests` com serviços falsos, separado dos testes do package.

Na pasta `ios`:

```sh
xcode-select -p
xcodebuild -version
brew install xcodegen
xcodegen generate
xcodebuild -project TradutorOffline.xcodeproj -scheme TradutorOffline \
  -configuration Debug -destination 'generic/platform=iOS' \
  CODE_SIGNING_ALLOWED=NO build
swift test
open TradutorOffline.xcodeproj
```

Esses comandos Mac/Swift ainda não foram executados neste ciclo. Após o build, selecione a equipe em Signing & Capabilities, defina um Bundle Identifier disponível, conecte o iPhone, confie no Mac e habilite Modo de Desenvolvedor conforme solicitado. Selecione o aparelho como destino e execute. Build sem assinatura não instala o aplicativo. Execute os testes de estado/callbacks do target Xcode por Product → Test; usar serviços falsos não comprova os modelos Apple, mesmo que os testes sejam executados no simulador.

O projeto declara `NSMicrophoneUsageDescription` e `NSSpeechRecognitionUsageDescription`. No primeiro uso, autorize ambos. Se negar, a tela deve explicar a correção nos Ajustes; testar esse caminho faz parte da validação. Importar áudio usa o seletor e copia o conteúdo para `Documents/ConversationAudio`, também usado pela captura. Os arquivos têm nomes únicos e permanecem ali; ainda não há limpeza automática. Compartilhamento de arquivos/exportação está habilitado no projeto.

## Preparar antes de desligar a rede

1. Com internet, selecione PT → EN e use **Preparar idiomas (com internet)**. Aceite o download do sistema. Repita no outro sentido e aguarde confirmação do par instalado. A Apple documenta que [`prepareTranslation()`](https://developer.apple.com/documentation/translation/translationsession/preparetranslation()) pode retornar se o download já estiver em curso. O adaptador consulta [`LanguageAvailability.Status`](https://developer.apple.com/documentation/translation/languageavailability/status) depois da preparação e antes de traduzir; apenas `installed` autoriza a tradução.
2. Faça uma gravação curta em cada idioma. O adaptador exige [`supportsOnDeviceRecognition`](https://developer.apple.com/documentation/speech/sfspeechrecognizer/supportsondevicerecognition) e define `requiresOnDeviceRecognition = true`. O reconhecimento deve falhar explicitamente quando o recurso local estiver indisponível; não existe fallback remoto. O app não distribui nem controla um modelo ASR independente do iOS.
3. Teste a voz nos dois idiomas. Se necessário, instale vozes nos ajustes de Acessibilidade/Conteúdo Falado do iOS; a localização pode mudar conforme a versão. A Apple descreve a [síntese como processamento no aparelho](https://developer.apple.com/documentation/avfoundation/speech-synthesis) e [`speechVoices()`](https://developer.apple.com/documentation/avfaudio/avspeechsynthesisvoice/speechvoices()) lista as vozes disponíveis. Isso não substitui confirmar a reprodução e o pacote instalado no aparelho sem rede.
4. Coloque os áudios de reserva em **No Meu iPhone**, no app Arquivos, com download local concluído. Execute uma importação antes do ensaio. Arquivo mostrado somente em nuvem não é uma reserva offline; outros provedores podem baixar conteúdo ainda no seletor, antes de entregá-lo ao app.
5. Ative modo avião e desligue Wi-Fi nos Ajustes. Confirme visualmente, reabra o app sem conexão e teste ASR, tradução e voz em ambos os sentidos. Marque a declaração manual na tela apenas nessa condição. O app não detecta nem comprova modo avião.

Se a preparação informar pacote pendente, continue com internet, aguarde e tente preparar novamente. O retorno da solicitação não deve ser apresentado como download concluído. Pacotes podem ser removidos pelo sistema; a checagem precisa continuar em cada operação.

## Recursos e reserva

Os modelos de Speech, Translation e vozes pertencem ao sistema e não acompanham o código/IPA. Não há pesos para copiar em `Resources`. O projeto usa APIs disponíveis no iOS 18 e não depende de Apple Intelligence. A [amostra oficial Translation](https://developer.apple.com/documentation/translation/translating-text-within-your-app) exige aparelho físico para traduzir em iOS; o simulador não valida esse estágio.

Para quatro arquivos de reserva sintetizados no Mac, instale as vozes Luciana e Samantha e execute:

```sh
bash gerar-reserva-mac.sh
```

O script usa `say` para PT01, EN01, PT02 e EN02 e grava em `reserva/`. Nenhum áudio foi gerado neste Linux. Transfira os arquivos ao iPhone antes de ficar offline e identifique-os como **sintéticos**. Prepare também gravações humanas das mesmas frases, identificadas separadamente. O importador aceita CAF, WAV, M4A, MP3, AIF/AIFF e AAC locais, com 1–2 canais, conteúdo não vazio, no máximo 20 segundos e 20 MB. A decodificação real ainda precisa ser validada no aparelho. Veja `Resources/README.md`.

## Persistência e recuperação

`medicoes.json` fica em Documents, é salvo de forma atômica e exporta array conforme `../../shared/medicoes.schema.json`. Todas as propriedades aparecem, inclusive opcionais como `null`. As datas novas usam ISO 8601 com timezone e a plataforma é `ios`. `rawInput` preserva o texto reconhecido antes da revisão. Uma nova rodada reinicia metadados; editar a rodada corrente preserva sua origem e referência temporal.

O leitor aceita o formato legado do protótipo, inclusive datas numéricas relativas à época de referência da Foundation. A migração preserva `medicoes.legacy.backup.json` antes de gravar a versão comum; um backup legado diferente recebe nome único, e salvamentos comuns mantêm a versão anterior válida em `medicoes.backup.json`. Onde o legado não guardou `rawInput`, usa o texto disponível e anota a limitação em `notes`; a transcrição prévia à edição é irrecuperável e não deve ser tratada como original comprovado. Modelos genéricos e valores desconhecidos do arquivo antigo não se tornam evidência de modelo físico.

Erros ao carregar, salvar ou exportar devem ser apresentados. Não descarte o arquivo anterior para esconder corrupção. Antes de compartilhar, resolva os alertas e confira que a exportação corresponde às avaliações atuais. Há dados textuais das falas nesses arquivos; use o conjunto público de teste.

## Espaço e memória

Registre app, espaço livre e recursos nos Ajustes antes/depois. Recursos Apple podem ser compartilhados; o sandbox não expõe necessariamente o tamanho exato de cada pacote. Diferença de espaço livre é aproximação sujeita a ruído. Registre **não disponível** se o sistema não fornecer valor confiável. Use Instruments no Mac em rodada separada para memória; não há coleta automática no app. Nenhum valor foi medido nesta entrega.
