# Tradução pela câmera

Na tela inicial, toque em **Traduzir com a câmera** e autorize a câmera.
O padrão é inglês → português; toque no botão de direção para inverter.
A tela da câmera permanece na vertical (retrato), mesmo ao girar o celular.
Aponte para texto impresso e use boa iluminação. Toque na imagem para focar e
use dois dedos para ampliar. O app mantém a tela ligada enquanto esta tela está aberta.

A leitura é confirmada em mais de um reconhecimento antes de traduzir. As regiões
mantêm identidade entre leituras; pequenos tremores são suavizados e movimentos
maiores são acompanhados mais rapidamente. Mudanças relevantes no texto escondem
a tradução anterior até confirmar o conteúdo novo. Uma falha breve de OCR não
apaga imediatamente a região; regiões sem confirmação visual expiram em até 450 ms.
A associação para retomar uma região pode durar até 1,2 s, mas não a mantém desenhada.

A tradução é ajustada dentro dos quatro cantos do texto detectado, respeitando
inclinação e perspectiva. Um painel claro cobre a área original; fonte e quebras
são ajustadas à região, com cache de layout para reduzir oscilações. Regiões ainda
não traduzidas recebem contorno discreto. Textos muito densos podem ficar pequenos.

Para ler com a imagem parada, toque em **Congelar imagem para ler**. O OCR é
executado sobre a captura exata mostrada na tela e a tradução continua nessa
imagem, mesmo se o celular se mover. **Retomar câmera ao vivo** reinicia o
acompanhamento. Ao sair da tela, a captura é descartada; não é salva na galeria.

## Preparação e processamento local

O OCR latino ML Kit 16.0.1 vem no APK, sem download separado. A tradução reutiliza
o pacote português do ML Kit já usado na conversa. Se necessário, toque em
**Preparar tradução (com Wi-Fi)**. Esse botão não depende de modelos Vosk ou vozes.
Depois de preparar, teste em modo avião com Wi-Fi desligado. A câmera não salva
fotos, texto reconhecido ou medições de conversa. Nenhum frame é enviado a um
serviço remoto pelo aplicativo.

CameraX 1.4.2 usa a câmera traseira e `MlKitAnalyzer` com coordenadas da
`PreviewView`, incluindo recorte e rotação. Solicita resolução próxima de 1280×960;
a resolução efetiva depende do aparelho. Frames antigos são descartados com
KEEP_ONLY_LATEST. Até 12 blocos por frame, de até 1.500 caracteres cada, são
considerados. Regiões inválidas ou de baixa confiança são rejeitadas. Traduções
são serializadas e mantidas em cache limitado a 128 textos normalizados por
sessão/direção; layouts são limitados a 64 entradas e o rastreador a 48 regiões.
Trocar direção ou sair invalida resultados pendentes. A orientação da tela da
câmera é fixada em retrato.

## Limites

O acompanhamento é temporal em coordenadas da imagem, baseado nos cantos do OCR,
associação espacial e consistência textual. **Não é uma âncora 3D persistente nem
rastreamento por fluxo óptico entre frames de OCR.** Movimentos rápidos, oclusão,
desfoque e baixa luz podem causar atraso ou perda da região. Também não há
reconstrução da textura original por baixo do texto. A referência visual desejada
é tradução sobre a superfície; não foi demonstrada equivalência ao Google Lens.

## Verificação física

1. Abrir câmera, aceitar permissão e confirmar preview sem travamento.
2. Apontar para “The station is two blocks away.” em inglês → português;
   confirmar tradução em português na região do texto.
3. Manter imóvel por 10 s, depois fazer pequenos tremores: verificar oscilação
   dos cantos, fonte e quebras de linha.
4. Inclinar a folha e mover lateralmente devagar: verificar perspectiva e atraso.
5. Trocar “EXIT” por “ENTRY” no mesmo lugar: a tradução antiga deve desaparecer
   antes da confirmação nova. Repetir com duas placas de texto igual lado a lado.
6. Cobrir o texto, sair do enquadramento e fazer um movimento rápido; verificar
   que traduções sem região visível não permanecem indefinidamente na tela.
7. Congelar, mover o aparelho, trocar direção e preparar tradução se necessário:
   a captura deve continuar parada com tradução alinhada. Retomar e conferir o ao vivo.
8. Girar o celular para ambos os lados e confirmar que a tela permanece em retrato;
   testar texto perto das bordas e zoom/foco por toque.
9. Sair/reabrir, bloquear/desbloquear e negar permissão: não deve haver crash.
10. Com pacote preparado, repetir ambos os sentidos em modo avião/Wi-Fi desligado.

Os testes JVM usam sequências sintéticas de geometria/texto. Os testes
`CameraSurfaceTest` usam OCR ML Kit real sobre bitmap sintético e desenho Android
em um quadrilátero inclinado; não substituem o roteiro de câmera acima.

Fontes: [OCR local ML Kit](https://developers.google.com/ml-kit/vision/text-recognition/v2/android),
[CameraX ML Kit Analyzer](https://developer.android.com/media/camera/camerax/mlkitanalyzer)
e [projeção de quatro pontos no Android](https://developer.android.com/reference/android/graphics/Matrix#setPolyToPoly(float%5B%5D,int,float%5B%5D,int,int)).
