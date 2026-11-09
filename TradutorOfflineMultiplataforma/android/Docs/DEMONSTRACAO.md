# Demonstração — mesmo roteiro da frente iOS

Este roteiro descreve as tentativas de fala humana. Testes instrumentados em aparelho e ensaios de serviço estão registrados separadamente em TESTES.md; não contam como as 36 repetições planejadas.

## Antes da apresentação, com internet

1. Confirmar aparelho Android e versão/API. Instalar a versão debug após build bem-sucedido.
2. Tocar **Baixar e preparar idiomas (Wi-Fi)** para instalar os modelos Vosk e a tradução. Usar **Instalar ou configurar vozes offline** para instalar vozes pt-BR/en-US, se faltarem. A importação manual dos ZIPs oficiais continua disponível. Conferir mensagens de modelos, tradução e vozes.
3. Ensaiar uma frase digitada para isolar tradução/voz; depois conferir ASR pela transcrição e **Ouvir original**. Registrar falhas de cada componente antes de avaliar o fluxo completo.
4. Preparar arquivos reais ou sintetizados PT01 e EN01 no formato WAV PCM16 mono 16 kHz, até 20 s. Nomear origem/falante e síntese nas notas. Não apresentar áudio sintetizado como fala humana.
5. Se necessário, converter uma reserva com FFmpeg instalado previamente: `ffmpeg -i entrada.m4a -ar 16000 -ac 1 -c:a pcm_s16le reserva.wav`. Confirme duração até 20 s; o app rejeita arquivos maiores, sem cortar silenciosamente.

## Apresentar offline

1. Ativar modo avião **e desligar Wi-Fi**; mostrar a configuração. A marcação do app é uma declaração manual e não detecta/prova a condição de rede.
2. Voltar ao app, que verifica recursos locais ao abrir. Se necessário, abrir **Configurar idiomas e vozes** e tocar **Verificar recursos locais (sem baixar)**. Selecionar **pt-BR → en-US**.
3. Marcar **Confirmo modo avião e Wi-Fi desligado**. Tocar **Gravar**, dizer PT01: “Bom dia. Onde fica a estação?”, tocar **Parar**. Mostrar transcrição, tradução, fala e tempos. Traduzir não deve solicitar download ou fallback remoto.
4. Em notas, escrever `PT01; repetição 1; microfone; fria/quente; ruído; distância; falante; bateria; aquecimento; modo avião/Wi-Fi desligado`. Avaliar a transcrição original e a tradução, tocar **Salvar avaliação**.
5. Trocar para **en-US → pt-BR**. Gravar EN01: “The station is two blocks away.” Repetir conferência e avaliação.
6. Mostrar **Ouvir original**. Corrigir uma transcrição e usar **Traduzir e falar**: `rawInput` continua original, `edited=true`, a referência de tempo continua a mesma e inclui a revisão humana. Essa correção atualiza a rodada, sem representar uma nova repetição física.
7. Mostrar **Reproduzir tradução** e verificar que a primeira latência não muda.
8. Abrir **Mostrar medições e avaliação** e tocar **Exportar medições JSON**, escolher destino e conferir o arquivo. Se sair para o seletor, só o destino escolhido recebe os dados.

## Plano B

Usar **Nova rodada** e **Importar áudio de reserva**, com direção correta. A origem exportada deve ser `arquivo pré-gravado`, e a referência dos tempos começa ao terminar a importação. A duração da importação não entra na latência; os tempos de arquivo não são equivalentes aos de microfone. Uma frase apenas digitada exporta `texto digitado` e os três tempos relativos à fala ficam `null`; somente `translationMS` é medido.

Se faltar modelo/voz, não demonstrar como offline validado. Mostrar o erro explícito e registrar o bloqueio. APK instalado, vozes declaradas locais e checks de código não substituem essa execução.
