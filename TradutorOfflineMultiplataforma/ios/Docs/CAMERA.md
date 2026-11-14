# Tradução visual — implementação iOS

Estado em 13/11/2026: **implementação em código, sem compilação, execução de testes ou instalação neste ciclo**. A validação será feita em Mac/iPhone por outra pessoa. Não transferir as evidências do Android para o iOS.

## Fluxo e interface

Na conversa, toque no ícone de câmera. A tela usa retrato, tema escuro e direção inicial inglês → português. O controle de idiomas inverte a direção; o botão inferior de download prepara somente a tradução, sem exigir preparação de fala. O check indica que o sistema informou idiomas instalados, não que um teste offline foi concluído.

O botão circular central usa pausa para congelar e play para retomar. A captura congelada é o mesmo CGImage publicado na visualização; o Vision executa uma nova leitura dessa imagem. Não há captura fotográfica independente, gravação em galeria ou persistência dos frames/textos da câmera. Ao sair ou entrar em segundo plano, a imagem, as regiões e o cache são descartados e a câmera é parada. O aplicativo restaura a configuração anterior de bloqueio automático da tela.

Toque para focar e use pinça para zoom, quando o dispositivo permitir. Negar câmera mostra orientação para Ajustes e um ícone que abre os ajustes do app. A permissão precisa constar no Info.plist gerado a partir do `project.yml`.

## Arquitetura

- `CameraCapture`: AVFoundation com câmera traseira, preset solicitado 1280×720, buffers fisicamente rotacionados para retrato (90 graus, após verificar suporte), descarte de frames atrasados e conversão para CGImage. A visualização e o OCR compartilham a orientação do buffer.
- Preview solicitado com intervalo mínimo de 50 ms, com no máximo uma entrega pendente para a UI. OCR com intervalo mínimo de 150 ms e somente uma análise ao vivo em voo, em fila separada. São limites de agendamento, não FPS ou latência medidos.
- `VNRecognizeTextRequest`: nível accurate, en-US/pt-BR e correção linguística. Até 12 observações por resultado, com 2–1.500 caracteres, letras presentes, quadrilátero válido e confiança mínima de 0,45. Vision retorna observações de texto que não correspondem necessariamente aos blocos do ML Kit Android.
- `CameraRegionTracker`: associação por geometria e similaridade textual, confirmação inicial em pelo menos duas leituras e 100 ms, três votos para atualizar conteúdo já aceito, suavização adaptativa e expiração visual em 450 ms. Associação interna retida até 1,2 s e até 48 regiões.
- `CameraTranslationModel`: idioma verificado por `LanguageAvailability`, `AppleTranslationService` existente, sessões pertencentes a `.translationTask`, traduções serializadas e cache limitado a 128 textos normalizados. Troca de direção, congelamento/retomada e saída invalidam operações; resultados antigos não devem alterar o estado atual. Limites de espera de 120 s para preparo e 45 s para um lote de tradução.
- `CameraSurfaceView`: imagem aspect-fill e geometria normalizada com origem superior esquerda; o mesmo recorte é aplicado aos quatro cantos do OCR. Homografia de quatro pontos aplicada com `CATransform3D` a um painel rasterizado. Fonte e quebras ajustadas à região, dimensões quantizadas e cache de até 64 layouts para reduzir oscilações.
- `CameraTranslationView`: SF Symbols em áreas de toque de pelo menos 52 pt, botão principal de 64 pt, descrições de acessibilidade e apresentação em tela cheia. O projeto já fixa retrato para o app de iPhone.

## Limites e diferenças em relação ao Android

A intenção funcional acompanha o Android, mas a implementação usa Vision, AVFoundation e Translation da Apple, não Vosk/ML Kit/CameraX. Não se garante igualdade de resultados, agrupamento textual, confiança, desempenho ou disponibilidade dos idiomas. A resolução solicitada também difere e depende do aparelho.

O acompanhamento é **2D entre leituras de OCR**, sem fluxo óptico ou âncoras 3D. Os painéis claros não reconstroem textura, tipografia ou iluminação. Traduções longas podem ter fonte pequena ou truncamento. A geometria ao vivo pode atrasar em relação à imagem entre leituras; suavização não elimina esse problema. Uma captura congelada precisa ser avaliada separadamente da estabilidade em transmissão contínua.

O código da câmera não envia frames a serviço remoto nem contém download implícito de idiomas; tradução exige estado instalado e preparo explícito. Os modelos e serviços Apple são gerenciados pelo sistema. Somente teste físico sem rede pode confirmar o comportamento offline de ponta a ponta.

## Roteiro de validação para o responsável pelo Mac/iPhone

1. Gerar novamente o projeto com XcodeGen, compilar, assinar e instalar; confirmar inclusão de todos os novos arquivos de Sources e `NSCameraUsageDescription`.
2. Abrir câmera, aceitar e negar permissão em ensaios separados; conferir o atalho para Ajustes e ausência de crash.
3. Confirmar preview vertical e sem imagem invertida/espelhada; girar o aparelho para ambos os lados. Conferir símbolos centralizados e contraste.
4. Usar “The station is two blocks away.”, preparar o par e conferir tradução em português. Repetir português → inglês.
5. Conferir alinhamento nos quatro cantos, perto das bordas, inclinação e perspectiva; testar foco por toque e pinça.
6. Manter imóvel por 10 s, provocar pequenos tremores e mover lateralmente; observar atraso, oscilações, fonte e quebras.
7. Trocar EXIT por ENTRY no mesmo lugar, duplicar textos lado a lado, cobrir e tirar texto do quadro; conferir confirmação e descarte de traduções antigas.
8. Congelar, mover o celular e inverter direção: a imagem deve permanecer fixa, e a tradução deve corresponder à captura. Retomar e conferir o ao vivo.
9. Sair/reabrir, bloquear/desbloquear, ir ao fundo, receber interrupção de câmera e negar acesso: conferir liberação, recuperação e mensagens. Em falha de sessão, sair e reabrir a tela.
10. Após preparo, ativar modo avião e desligar Wi-Fi nos Ajustes; reabrir e repetir ambos os sentidos. Não marcar como aprovado por apenas verificar `installed`.
11. Usar VoiceOver e fontes ampliadas; conferir também a conversa, janela de configurações, avaliação e exportação existentes.
12. Registrar aparelho/iOS/Xcode, comandos e resultados reais; atualizar STATUS/TESTES sem reaproveitar evidências Android.

## Referências técnicas

- [Vision: reconhecimento de texto](https://developer.apple.com/documentation/vision/vnrecognizetextrequest).
- [AVFoundation: rotação dos buffers](https://developer.apple.com/documentation/avfoundation/avcaptureconnection/videorotationangle).
- [Translation: preparo dos idiomas](https://developer.apple.com/documentation/translation/translationsession/preparetranslation()).
