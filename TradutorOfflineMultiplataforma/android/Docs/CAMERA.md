# Tradução pela câmera

Na tela inicial, toque em **Traduzir com a câmera** e autorize a câmera.
O padrão é inglês → português; toque no botão de direção para inverter.
Aponte para texto impresso, mantenha o aparelho estável e use boa iluminação.
As caixas verdes indicam regiões reconhecidas. A tradução aparece sobre a região
assim que o processamento local termina e acompanha as posições dos frames novos.

O OCR latino ML Kit 16.0.1 vem no APK, sem download separado. A tradução reutiliza
o pacote português do ML Kit já usado na conversa. Se necessário, toque em
**Preparar tradução (com Wi-Fi)**. Esse botão não depende de modelos Vosk ou vozes.
Depois de preparar, teste em modo avião com Wi-Fi desligado. A câmera não salva
fotos, texto reconhecido ou medições de conversa. Nenhum frame é enviado a um
serviço remoto pelo aplicativo.

CameraX 1.4.2 usa a câmera traseira e `MlKitAnalyzer` com coordenadas da
`PreviewView`, incluindo recorte e rotação. Frames antigos são descartados pela
estratégia KEEP_ONLY_LATEST. Até 12 blocos por frame, de até 1.500 caracteres cada,
são considerados. Traduções são serializadas e mantidas em cache limitado a 128
textos por direção/sessão. Uma tradução que termina depois de mover a câmera usa
somente as posições atuais; trocar direção ou sair invalida resultados pendentes.
Sair da tela libera a câmera; voltar reinicia a análise. Rotacionar recria a tela
e preserva a direção, sem preservar o cache.

Trata-se de sobreposição de caixas com texto, sem reconstrução do fundo ou
substituição tipográfica em perspectiva. Texto pequeno, desfocado, manuscrito,
muito inclinado ou com pouco contraste pode não ser reconhecido. Há atraso entre
detectar e traduzir; a atualização não promete uma taxa fixa de quadros.

## Verificação física

1. Abrir a câmera, aceitar permissão e confirmar preview sem travamento.
2. Apontar para “The station is two blocks away.” em inglês → português;
   confirmar caixa na região e tradução em português.
3. Mover para outro texto: caixas antigas devem sumir; tradução anterior não
   deve ser desenhada sobre um texto diferente.
4. Inverter a direção e testar “Bom dia. Onde fica a estação?”.
5. Repetir com aparelho na horizontal e com texto perto das bordas do preview.
6. Voltar à conversa, reabrir câmera, bloquear/desbloquear e alternar apps;
   confirmar liberação e retomada da câmera.
7. Negar permissão e verificar mensagem de recuperação, sem crash.
8. Com pacote preparado, ativar modo avião/Wi-Fi desligado e repetir ambos os sentidos.

Fontes: [OCR local ML Kit](https://developers.google.com/ml-kit/vision/text-recognition/v2/android)
e [CameraX ML Kit Analyzer](https://developer.android.com/media/camera/camerax/mlkitanalyzer).
