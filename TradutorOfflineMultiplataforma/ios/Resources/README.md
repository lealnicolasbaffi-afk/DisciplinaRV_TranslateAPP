# Recursos locais — iOS

Esta pasta contém apenas recursos pequenos do projeto. Os modelos de Speech/Translation e pacotes de voz são gerenciados pelo iOS e **não acompanham o código**. Prepare-os com internet e valide os dois sentidos em modo avião com Wi-Fi desligado.

O app aceita áudio importado de arquivos locais e usa `Documents/ConversationAudio` para cópias e gravações. Cada arquivo recebe nome único e permanece no diretório; a tela acessa somente o áudio da rodada corrente, sem limpeza automática. Para a reserva, prepare as mesmas frases de `../Tests/frases.csv`, sobretudo PT01 e EN01. Salve-as em **No Meu iPhone**; provedores de nuvem podem iniciar downloads ainda no seletor de arquivos. Use nomes que identifiquem frase e origem humana/sintética; não misture essa origem na análise das tentativas de microfone.

No Mac, `bash ../gerar-reserva-mac.sh` (ou `bash gerar-reserva-mac.sh` na raiz iOS) usa `say` e as vozes Luciana/Samantha para gerar PT01, EN01, PT02 e EN02 em `reserva/`. Transfira-os ao iPhone e conclua qualquer download no app Arquivos antes de ficar offline. Nenhum arquivo de áudio foi gerado no ambiente Linux desta entrega.

Teste importação e reprodução no aparelho antes da apresentação. São aceitos CAF, WAV, M4A, MP3, AIF/AIFF e AAC locais, não vazios, com 1–2 canais, até 20 segundos e 20 MB. AIFF do gerador é uma reserva candidata; a compatibilidade efetiva com decodificação e reconhecimento deve ser confirmada no iPhone. Um arquivo inválido ou inacessível deve produzir erro visível, sem fallback remoto.
