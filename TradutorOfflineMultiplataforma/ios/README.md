# Tradutor Offline — iOS

Protótipo acadêmico Swift/SwiftUI para **iPhone 14 com iOS 18 ou posterior**, português brasileiro ↔ inglês americano, conversa por turnos de até 20 segundos. Depois de Parar, o fluxo transcreve, traduz e reproduz automaticamente. Permite ouvir o original, corrigir, repetir, importar áudio local e avaliar/exportar medições. Speech exige reconhecimento no aparelho; Translation e síntese usam recursos do iOS, preparados antes da demonstração.

**Estado em 13/11/2026:** implementados em código o redesenho com ícones nativos, configurações em janela própria e câmera com OCR, sobreposição em perspectiva, acompanhamento temporal e imagem congelada. **Não houve compilação Swift/iOS, execução de testes ou instalação neste ciclo**, por solicitação do responsável; outra pessoa fará a validação no Mac/iPhone. As verificações históricas de Linux não certificam estas mudanças. O funcionamento offline e o alinhamento precisam ser confirmados no aparelho. Roteiro: [Câmera: arquitetura, uso e validação](Docs/CAMERA.md).

Esta pasta é a fonte do projeto iOS. O contrato é `../shared/ESPECIFICACAO.md`, o schema é `../shared/medicoes.schema.json` e as 12 frases são idênticas às de `../shared/Tests/frases.csv`. A frente Android permanece separada; o alinhamento aqui é por módulos, fluxo, rótulos e contrato de dados.

## Gerar e compilar no Mac

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

1. Toque no ícone de **configurações** para abrir “Idiomas e vozes”. Prepare os idiomas com internet; o download precisa terminar antes do ensaio offline.
2. Inverta a direção no controle Português ↔ Inglês. O **microfone** grava por até 20 segundos; o **stop** encerra e inicia transcrição, tradução e voz. A **pasta** importa áudio local e o **alto-falante junto à entrada** reproduz o original.
3. Revise o texto no cartão de entrada e use **Traduzir**. Editar mantém a origem e o instante da rodada. O **+** inicia uma nova rodada de texto digitado.
4. Leia o cartão de tradução e use seu **alto-falante** para reproduzir. O replay preserva a primeira latência.
5. Em **Avaliação**, registre qualidade, condições e a declaração manual de modo avião; salve e exporte o JSON. Avisos de persistência precisam ser resolvidos antes de confiar na exportação.
6. O ícone de **câmera** abre o OCR com tradução sobre a imagem. O **download** prepara somente os idiomas de tradução; **pausa/play** congela/retoma. Toque para focar e use pinça para zoom. Consulte [CAMERA.md](Docs/CAMERA.md) para os limites.

Os ícones usam SF Symbols com rótulos para acessibilidade e centralização em áreas de toque explícitas. A câmera usa tema escuro, permanece em retrato e descarta a captura ao sair. As configurações e a avaliação usam janelas separadas; os serviços de fala e o contrato de medições existentes foram preservados. A aparência e o VoiceOver ainda não foram verificados no iPhone.

## Estrutura

- `Sources/App`: inicialização e composição dos serviços.
- `Sources/Domain`: direção, estados, rodada/medições e contratos dos serviços.
- `Sources/Features/Conversation`: tela e coordenação da conversa.
- `Sources/Features/Camera`: captura AVFoundation, OCR Vision, rastreador, projeção e coordenação da tradução visual.
- `Sources/UI`: estilo comum e botões com SF Symbols.
- `Sources/Services/{Speech,Translation,Synthesis,Audio}`: adaptadores Apple, captura e reprodução.
- `Sources/Persistence`: JSON comum, leitura legada e proteção dos arquivos anteriores.
- `Configuration`: ajustes de build; `project.yml`: geração reproduzível do projeto.
- `Tests`: frases comuns, testes Foundation preparados e validador Python executável em Linux.
- `Resources`: orientação para recursos e reserva; modelos do sistema não acompanham o código.
- `Docs`: [status](Docs/STATUS.md), [configuração](Docs/CONFIGURACAO.md), [testes](Docs/TESTES.md) e [demonstração](Docs/DEMONSTRACAO.md).

## Verificações disponíveis em Linux (não executadas nesta atualização)

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
