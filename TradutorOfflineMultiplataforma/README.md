# Tradutor Offline — Android e iOS

- `ios/`: SwiftUI para iPhone 14, a partir do protótipo existente.
- `android/`: Kotlin nativo, versão 0.2.0; build Android e testes JVM aprovados. Aparelho de teste Samsung SM-A546E, Android 16/API 36.
- `shared/`: especificação única, contrato de medições e mesmas frases de teste.

O Android usa Vosk para reconhecimento, ML Kit para tradução e vozes locais do Android. Consulte [instalação e comandos](android/README.md), [status](android/Docs/STATUS.md) e [evidências](android/Docs/TESTES.md). O fluxo é por turnos de até 20 segundos.

Consulte [a especificação comum](shared/ESPECIFICACAO.md). A versão anterior de iPhone permanece em `../TradutorOffline` e `../TradutorOffline-iPhone14.zip`.

O trabalho desta entrega está na pasta Android. A frente iOS e o contrato compartilhado foram preservados. O projeto iOS precisa de Mac/Xcode e de validação própria.
