# Tradutor Offline — duas frentes

- `ios/`: SwiftUI para iPhone 14, a partir do protótipo existente.
- `android/`: Kotlin nativo, aparelho de teste ainda a definir.
- `shared/`: especificação única, contrato de medições e mesmas frases de teste.

As duas frentes têm a mesma estrutura modular e o mesmo fluxo de uso. A implementação dos modelos é específica do sistema operacional. As tarefas dedicadas devem manter seu andamento em `Docs/STATUS.md`.

Consulte [a especificação comum](shared/ESPECIFICACAO.md). A versão anterior de iPhone permanece em `../TradutorOffline` e `../TradutorOffline-iPhone14.zip`.

Nenhuma versão foi certificada offline em aparelho físico. O projeto iOS precisa de Mac/Xcode; o Android precisa de SDK/Gradle e de um aparelho para validação final.
