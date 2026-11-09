# Status — iOS — 09/11/2026

Código refatorado na pasta iOS existente, alinhado com `../../shared/ESPECIFICACAO.md` e `../../shared/medicoes.schema.json`. **Nenhuma compilação Swift/iOS, instalação ou teste físico foi executado. 0/36 tentativas físicas planejadas.** As conclusões deste ciclo são de inspeção e verificações estáticas em Linux.

## Implementado no código, aguardando execução

- App com composição explícita; Domain, Conversation, Speech, Translation, Synthesis, Audio e Persistence em diretórios próprios. Rótulos/ordem preservados: **1. Falar**, **2. Transcrição • pode editar**, **3. Tradução** e **Medições**.
- Coordenador com estados, rodada/operação identificadas, cancelamento, proteções contra callbacks antigos, metadados reiniciados em nova rodada e ações incompatíveis desabilitadas. **Nova rodada (texto digitado)** distingue um texto independente de correção da fala atual. Permissões assíncronas não devem reabrir uma gravação cancelada.
- Gravação de até 20 s, ouvir original e importação local validada. Áudios em `Documents/ConversationAudio`, com nomes únicos; a tela acessa o áudio corrente. Importações exigem conteúdo válido de até 20 s/20 MB e 1–2 canais. Não iniciam download de iCloud; use **No Meu iPhone**, pois outros provedores podem materializar no seletor.
- Speech com `supportsOnDeviceRecognition` e `requiresOnDeviceRecognition = true`, sem fallback remoto. Translation confere o par e exige `installed`; verifica novamente após `prepareTranslation()`, que pode retornar enquanto o download continua. A sessão pertence ao ciclo de vida da view, sem ficar retida no coordenador. Voz escolhe idioma exato e usa `AVSpeechSynthesizer`; execução offline ainda depende de teste no aparelho.
- Fluxo automático após transcrição, reprodução novamente sem substituir a primeira latência, revisão preservando `rawInput`, origem e instante, avaliação com original visível e exportação suspensa se persistir falhar.
- Persistência atômica com todas as propriedades do schema, `platform: "ios"`, datas ISO 8601 com timezone e `null` explícito. Compatibilidade com o formato legado de datas Foundation, backup dos bytes anteriores e anotação quando `rawInput` não existia. Histórico ilegível não vira lista vazia nem é sobrescrito silenciosamente.
- Build reproduzível em `project.yml`, configurações `.xcconfig`, package Foundation e testes preparados; README e roteiros de configuração, teste e demonstração atualizados.

## Verificado neste ambiente

Comandos executados na pasta iOS:

```sh
python3 Tests/validate_contract.py --self-test
bash -n gerar-reserva-mac.sh
cmp Tests/frases.csv ../shared/Tests/frases.csv
```

Resultado: **38 autotestes do validador passaram**, usando apenas objetos sintéticos em memória. A estrutura obrigatória, presença das fontes/configuração/testes, referências estáticas do projeto, sintaxe Bash e as quatro frases do gerador de reserva passaram. O CSV contém as mesmas 12 frases comuns, byte a byte. Nenhum dado sintético foi gravado como medição de aparelho.

Inspeções Python adicionais executadas: leitura de `project.yml` com PyYAML e existência dos caminhos dos dois targets; comparação das 20 chaves codificadas pelo `Trial` com o schema; ausência de marcadores de conflito, espaços finais e falta de newline no conjunto inspecionado. Esse diretório não é um repositório Git; `git diff --check` não estava disponível como verificação do projeto. Essas verificações textuais passaram e não são uma compilação.

Python 3.12.3 e Bash disponíveis; `swift`, `xcodebuild`, `xcodegen` e `say` ausentes. Não foi gerado áudio de reserva. O validador aceita `--measurements` para um arquivo realmente exportado pelo app/codificador Swift, mas **nenhum desses arquivos foi produzido ou validado aqui**. A validação Python não comprova o codificador Swift.

As APIs Apple foram consultadas em fontes oficiais, referenciadas em `CONFIGURACAO.md`. Inspeção de API e texto-fonte não substitui compilação com o SDK iOS 18 nem execução dos frameworks.

## Testes preparados e pendências

- `swift test`: 15 casos Foundation de contrato, duração/origem/revisão, JSON, leitura legada, backup e corrupção em `Tests/Unit`. **Não executados.**
- Scheme Xcode `TradutorOfflineTests`: 11 testes de ConversationModel com serviços falsos, cobrindo callbacks atrasados, cancelamento/permissão, início automático, falhas de persistência, primeira latência de voz e revisão com `rawInput` preservado. **Não compilados nem executados.** Não certificam os modelos Apple. Total de testes Swift preparados: **26** (15 Foundation + 11 Conversation).
- `xcodegen generate` e `xcodebuild ... -destination 'generic/platform=iOS' CODE_SIGNING_ALLOWED=NO build`: **não executados**. Assinatura, instalação, UI e comportamento dos delegates ainda pendentes.
- iPhone 14/iOS 18+: confirmar modelo/versão, preparar pacotes/vozes online e validar ASR, tradução e voz em ambos os sentidos em modo avião com Wi-Fi desligado. **0/36 tentativas físicas**; nenhuma latência/qualidade medida.
- Rodar os cenários de erro, nova rodada/revisão, interrupção e exportação; validar um JSON efetivamente gerado pelo Swift contra o schema e preservar a evidência dos comandos. Depois executar as 12 frases × 3 repetições.

## Limites conhecidos e próximo passo

Não há garantia offline por teste físico nesta entrega. Lista de vozes ou pacote Translation instalado não certifica o fluxo inteiro; a confirmação de modo avião é manual. O início da voz é medido pelo callback, não por saída acústica. Revisões incluem tempo humano; áudio importado e texto digitado devem ser analisados separadamente. Valores não medidos são `null`, sem falhas convertidas em tempo zero.

Os pesos/tamanhos dos modelos Apple são gerenciados pelo sistema e podem não ser expostos. Não há coleta automática de memória, detecção de silêncio, histórico de áudio na tela nem limpeza automática dos arquivos com UUID. `rawInput` pré-edição é irrecuperável em medições legadas que não o guardaram. Modelo genérico `iPhone` não comprova iPhone 14; o registro novo inclui identificador de hardware e requer confirmar o nome comercial em Ajustes.

Próximo passo: levar esta mesma pasta ao Mac, gerar/compilar o projeto, executar as duas frentes de testes Swift, conferir a exportação real e instalar no iPhone. Só após validar os três estágios sem rede iniciar a série física e a demonstração de `DEMONSTRACAO.md`.

## Propostas para o contrato comum

Nenhuma alteração proposta neste ciclo. Shared e Android não foram alterados por esta frente.
