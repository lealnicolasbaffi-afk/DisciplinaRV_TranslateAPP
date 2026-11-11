# Tradutor Offline — iOS

Protótipo acadêmico Swift/SwiftUI para **iPhone 14 com iOS 18 ou posterior**, português brasileiro ↔ inglês americano, conversa por turnos de até 20 segundos. Depois de Parar, o fluxo transcreve, traduz e reproduz automaticamente. Permite ouvir o original, corrigir, repetir, importar áudio local e avaliar/exportar medições. Speech exige reconhecimento no aparelho; Translation e síntese usam recursos do iOS, preparados antes da demonstração.

**Estado em 10/11/2026:** câmera com OCR e tradução implementada em caráter experimental, e fluxo de compilação remota preparado no Linux. **Ainda não há compilação iOS, instalação ou testes físicos confirmados: 0/36 tentativas planejadas.** O funcionamento offline, a câmera e o alinhamento das caixas precisam ser validados no iPhone. Não há resultados de latência, qualidade, armazenamento ou memória.

Esta pasta é a fonte do projeto iOS. O contrato é `../shared/ESPECIFICACAO.md`, o schema é `../shared/medicoes.schema.json` e as 12 frases são idênticas às de `../shared/Tests/frases.csv`. A frente Android permanece separada; o alinhamento aqui é por módulos, fluxo, rótulos e contrato de dados.

## Gerar e compilar no Mac

Sem Mac próprio, consulte o [guia de compilação remota a partir do Linux](Docs/BUILD_REMOTO.md), que descreve GitHub Actions e os requisitos de assinatura para um IPA Ad Hoc.

Instale Xcode 16 ou posterior com SDK iOS 18+, selecione-o em `xcode-select` e aceite os componentes solicitados. Na pasta `ios` deste projeto:

```sh
brew install xcodegen
xcodegen generate
xcodebuild -project TradutorOffline.xcodeproj -scheme TradutorOffline \
  -configuration Debug -destination 'generic/platform=iOS' \
  CODE_SIGNING_ALLOWED=NO build
swift test
open TradutorOffline.xcodeproj
```

O build genérico verifica compilação sem assinatura; a instalação exige selecionar uma equipe Apple em Signing & Capabilities e ajustar o Bundle Identifier. Conecte o iPhone 14, confirme a versão instalada do iOS, habilite Modo de Desenvolvedor e execute pelo Xcode. `swift test` cobre o núcleo Foundation, separado dos frameworks Apple; o scheme Xcode tem testes de ConversationModel com serviços falsos, executáveis por Product → Test. Esses comandos/testes estão **preparados e ainda não executados**. O simulador não certifica Translation ou execução offline.

Veja [CONFIGURACAO](Docs/CONFIGURACAO.md) para permissões, pacotes, vozes e configuração de build.

## Usar a tela

1. Escolha a direção e toque em **Preparar idiomas (com internet)**. Preparação concluída só é anunciada quando o par está instalado; solicitar o download não prova sua conclusão.
2. Em **1. Falar**, use **Gravar fala** → **Parar e traduzir**. O limite encerra a captura em 20 segundos. **Ouvir gravação** reproduz o original; **Importar áudio de reserva** usa um arquivo local.
3. Em **2. Transcrição • pode editar**, revise o texto ou inicie **Nova rodada (texto digitado)** para um texto independente. Corrigir a rodada existente preserva a origem e a referência de tempo e marca `edited`.
4. Em **3. Tradução**, veja o resultado e use **Reproduzir tradução** para ouvi-lo novamente. A repetição não deve substituir a primeira latência registrada.
5. Em **Medições**, avalie a transcrição original (`rawInput`) e a tradução, registre a frase/repetição/condições e toque em **Salvar avaliação**. Use **Exportar medições JSON** para compartilhar o arquivo persistido. Erros de gravação/exportação precisam ser resolvidos antes de usar o arquivo como registro atualizado.

Para a câmera, toque em **Traduzir com a câmera**, autorize o acesso e prepare a tradução. Aponte para texto impresso e use o botão de direção para alternar entre inglês e português. O reconhecimento usa Vision no aparelho; a tradução usa os recursos do iOS. A visualização e a posição das caixas ainda precisam de validação física. O código não grava os quadros da câmera.

Use **Cancelar operação** para interromper uma operação e tente novamente. A troca de direção e uma nova rodada invalidam o trabalho anterior; callbacks antigos não podem atualizar a rodada atual. A confirmação de modo avião é uma declaração manual. Para o ensaio offline, ative modo avião, desligue Wi-Fi nos Ajustes e reabra o app sem rede.

## Estrutura

- `Sources/App`: inicialização e composição dos serviços.
- `Sources/Domain`: direção, estados, rodada/medições e contratos dos serviços.
- `Sources/Features/Conversation`: tela e coordenação da conversa.
- `Sources/Features/Camera`: captura traseira, reconhecimento de texto e sobreposição experimental.
- `Sources/Services/{Speech,Translation,Synthesis,Audio}`: adaptadores Apple, captura e reprodução.
- `Sources/Persistence`: JSON comum, leitura legada e proteção dos arquivos anteriores.
- `Configuration`: ajustes de build; `project.yml`: geração reproduzível do projeto.
- `Tests`: frases comuns, testes Foundation preparados e validador Python executável em Linux.
- `Resources`: orientação para recursos e reserva; modelos do sistema não acompanham o código.
- `Docs`: [status](Docs/STATUS.md), [configuração](Docs/CONFIGURACAO.md), [testes](Docs/TESTES.md) e [demonstração](Docs/DEMONSTRACAO.md).

## Verificações disponíveis em Linux

```sh
python3 Tests/validate_contract.py --self-test
bash -n gerar-reserva-mac.sh
cmp Tests/frases.csv ../shared/Tests/frases.csv
```

O validador verifica estrutura/configuração, o CSV byte a byte, frases de reserva e sintaxe Bash. Seus autotestes usam objetos **sintéticos em memória**, sem resultados de aparelho. Para um arquivo efetivamente exportado pelo app ou pelos testes Swift:

```sh
python3 Tests/validate_contract.py --measurements /caminho/para/medicoes.json
```

O JSON deve conter todas as propriedades do schema, datas ISO 8601 com timezone, `platform: "ios"`, `rawInput` e `null` para valores não medidos. A leitura do formato anterior é compatível com as datas numéricas do codificador antigo; a transcrição anterior à edição, quando não foi guardada, não pode ser recuperada. Consulte [TESTES](Docs/TESTES.md) para a evidência e o plano físico.

## Dados e limites

As medições incluem texto das falas e são salvas em Documents; gravação e reserva importada ficam em `Documents/ConversationAudio`, com nomes únicos. A tela dá acesso ao áudio da rodada corrente; arquivos anteriores permanecem no diretório, sem biblioteca ou limpeza automática. Desinstalar o app remove esses arquivos; pacotes do sistema têm gerenciamento próprio. Use frases de teste sem dados sensíveis.

Os tempos usam relógio monotônico: de Parar até a transcrição (`transcriptionMS`), tradução (`textMS`) e callback de início da voz (`audioStartMS`); `translationMS` mede somente a chamada de tradução. O callback não mede o início acústico. Revisões incluem tempo humano, importações usam o fim da importação e texto digitado tem tempos de fala `null`. Separe essas condições ao analisar. Falhas não são tempo zero.

Não há detecção automática de fim de fala, conversa simultânea, telemetria de memória nem controle dos pesos dos modelos Apple. Disponibilidade de voz na API não certifica seu pacote offline: o teste em modo avião é obrigatório. O tamanho exato dos modelos compartilhados pode não ser exposto pelo iOS; nesse caso registre **não disponível**.
