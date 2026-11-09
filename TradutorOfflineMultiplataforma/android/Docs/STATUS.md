# Android — implementação inicial revisável

Atualizado em 09/11/2026. Fonte de verdade: esta pasta Android. Contrato: `../../shared/ESPECIFICACAO.md` e `../../shared/medicoes.schema.json`, preservados. A frente iOS não foi alterada. Não há proposta de mudança do contrato comum.

## Implementado em código

- Estrutura comum App/Domain/Conversation/Services/Persistence/Resources/Tests/Docs/Configuration; contratos dos adaptadores no Domain.
- Tela nativa Kotlin com direção, preparação, estados, Gravar/Parar/20 s, transcrição editável, ouvir original, tradução automática, síntese, avaliação e exportação.
- Vosk local PT/EN, importação dos ZIPs oficiais separada do APK, validação e ativação atômica; um modelo em RAM por vez. Exibição de bytes dos arquivos extraídos após preparação/importação.
- Captura WAV para replay e áudio de reserva PCM16 mono/16 kHz, até 20 s. Seletores de arquivo sem permissão ampla de armazenamento.
- ML Kit Translation local após preparação explícita; tradução não baixa modelos. TTS exige vozes instaladas pt-BR/en-US que declarem não exigir rede; ausência resulta em erro claro.
- Operações incompatíveis bloqueadas, cancelamento, foco de áudio, timeouts e proteção contra callbacks antigos. Revisão corrigiu tradução antiga visível após falha, recuperação de backup atômico e desbloqueio indevido da UI durante erro de persistência.
- JSON v1 com todas as propriedades, `null` explícitos, UUID/data com timezone, origem, correção preservando rawInput, avaliações e falhas persistidas. Relógio monotônico; replay não reescreve a primeira latência. Exportação por destino escolhido.
- Build configurado com versões fixadas, Wrapper completo/verificado e lock dos classpaths debug/release do app e testes. README, configuração, testes e demonstração documentados.

## Verificado neste ambiente

Wrapper e Gradle `help` passaram. Dependências foram resolvidas e lockadas. Os Sources Kotlin foram compilados juntos em harness JVM com stubs API 35; **23 testes passaram, zero falhas** (WAV, ZIP e contrato/medições). As 12 frases são idênticas ao shared. Validador Python testado com coleção vazia e caso inválido; XML, shell e whitespace conferidos. Evidências e comandos em `TESTES.md`.

## Pendente ou limitado

**Nenhum APK gerado.** Build Android, task Android de testes e lint pararam por SDK ausente. O runtime Java inicial também era incompleto para o Gradle; foi preparado um JDK 17 local para chegar ao bloqueio real do SDK. A compilação JVM não valida empacotamento, recursos, manifesto mesclado, bibliotecas nativas ou runtime Android.

**Nenhum teste físico: 0/36 tentativas executadas. Aparelho Android ainda não informado.** Modelos completos, vozes, ML Kit, latências, qualidade, RAM, bateria e térmica não foram exercitados no Android. A declaração de voz local depende do motor TTS; só o ensaio em modo avião com Wi-Fi desligado verifica o alvo. Não há ASR/tradução/síntese remotos como fallback solicitado pelo app.

A tela é simples: apresenta a rodada corrente e total salvo, sem navegador de histórico. Recriar a Activity/processo interrompe a rodada e reinicia a tela; medições já salvas permanecem, mas áudio/estado corrente não são restaurados. A confirmação de modo avião é manual e permanece como escolha da sessão; novas rodadas limpam notas e qualidades. Correção/retradução atualiza a mesma linha, não cria histórico de revisões. ZIPs não têm hash de autenticidade embutido; usar os links oficiais. APIs usadas não expõem tamanho exato dos pacotes ML Kit/vozes.

## Próximos passos concretos

1. Prover SDK Platform 35/Build Tools 35.0.0 em ambiente com JDK 17 e executar `Configuration/check.sh`; corrigir eventuais achados nativos/lint antes de declarar build aprovado.
2. Confirmar aparelho Android, instalar o APK gerado, importar modelos e preparar pacote/vozes.
3. Executar o roteiro PT01 → EN01 e os cenários de erro em modo avião/Wi-Fi desligado.
4. Executar as 36 tentativas planejadas, exportar e validar o JSON; medir memória em rodada separada. Registrar resultados reais sem misturar origens/correções.

## Limpeza da entrega

Em 09/11/2026 foram removidos `work/`, `.gradle/` e marcadores `.gitkeep` redundantes, por solicitação do usuário. Código, testes, documentação, recursos, configurações, lockfile e Gradle Wrapper foram preservados. O JDK local, caches, harness e logs temporários não acompanham mais a pasta. Os resultados anteriores estão resumidos em `TESTES.md`; repetir os checks exige ferramentas preparadas novamente.
