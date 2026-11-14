# Atualização de câmera e interface — 13/11/2026

**Não foram executados testes, build, instalação ou medições desta atualização**, conforme solicitação. As evidências abaixo são históricas e não validam os novos arquivos de câmera/UI.

O responsável pelo Mac deve regenerar o projeto, compilar, executar os testes Swift existentes, validar os fluxos preservados e seguir o roteiro de câmera/VoiceOver em [CAMERA.md](CAMERA.md). Nenhum novo teste automatizado de câmera iOS foi executado ou declarado aprovado. Há novos componentes ainda sem cobertura automatizada específica.

---

# Testes e medições — iOS

Em 09/11/2026: **0/36 tentativas físicas executadas**, nenhuma compilação Swift/iOS e nenhum teste Foundation executado. As verificações Linux abaixo são estáticas e não demonstram reconhecimento, tradução, voz ou correção de concorrência em execução.

## Evidência disponível em Linux

Executar a partir da pasta `ios`:

```sh
python3 Tests/validate_contract.py --self-test
bash -n gerar-reserva-mac.sh
cmp Tests/frases.csv ../shared/Tests/frases.csv
```

O validador verifica diretórios/módulos com fontes Swift, presença de projeto/configuração/testes/documentos, referências estáticas do `project.yml`, CSV idêntico byte a byte ao shared, 12 frases em ordem (6 PT-BR e 6 EN-US), cabeçalho/campos/idiomas, frases do gerador de reserva e sintaxe Bash. Seus autotestes validam objetos **sintéticos em memória**, incluindo recusas de datas sem timezone, UUID inválido, campo ausente/extra, tipo incorreto, tempo negativo e latência de fala em texto digitado. Não escreve dados fictícios em `medicoes.json`.

Python 3 e Bash estão disponíveis. `swift`, `xcodebuild`, `xcodegen` e `say` não estão disponíveis; não foi possível compilar, executar testes Swift, gerar projeto Xcode nem produzir áudios. As verificações não substituem o compilador ou as APIs Apple.

Para um JSON efetivamente exportado pelo app ou por um teste Swift:

```sh
python3 Tests/validate_contract.py --measurements /caminho/para/medicoes.json
```

`--measurements` é repetível. O validador aplica os recursos usados pelo schema v1 (tipos, obrigatoriedade, propriedades extras, enum, mínimo e formatos), exige `platform: "ios"` e tempos de fala `null` em texto digitado. Recusa chaves duplicadas e valores não JSON como `NaN`. Não comprova origem do arquivo nem execução física. Nenhum JSON real do app ou do exportador Swift foi produzido/validado neste ambiente.

## Testes automatizados preparados para Mac/Swift

```sh
swift test
xcodegen generate
xcodebuild -project TradutorOffline.xcodeproj -scheme TradutorOffline \
  -configuration Debug -destination 'generic/platform=iOS' \
  CODE_SIGNING_ALLOWED=NO build
```

O package prepara 15 casos de Domain/Persistence com Foundation em `Tests/Unit/{TrialTests,JSONTrialStoreTests}.swift`: contrato/datas/`null`, round-trip, origens e revisão, relógio, valores inválidos, backup, migração e corrupção. O build genérico verifica a integração das fontes Apple sem instalar o app. **Ambos pendentes.**

O scheme Xcode também tem `Tests/Conversation/ConversationModelTests.swift`, com 11 casos de serviços falsos: transcrição dispara pedido automático imutável, ASR e recorder tardios são ignorados, permissão suspensa impede duplo início, nova rodada digitada zera contexto, cancelamento de reprodução preserva a tentativa, falha de save desabilita exportação e recuperação permite salvar avaliação, histórico inválido não é sobrescrito, cancelar preparação não cria tentativa, replay mantém a primeira latência de voz, revisão preserva `rawInput` e inclui tempo humano, e callback de voz antigo é ignorado em nova rodada. Execute por Product → Test no Xcode após escolher um destino. Os **26 testes Swift** (15 Foundation + 11 Conversation) estão **preparados e não executados**; não usam modelos Apple reais. Dados gerados por testes são sintéticos e não contam como tentativas das 36 frases.

Ao executar os testes no Mac, preserve comando, versão do Swift/Xcode e resultado. Se produzir fixture de exportação real pelo codificador Swift, execute o validador sobre ela; identifique a fixture como sintética. A conformidade de um JSON montado em Python não prova que o codificador Swift implementa o contrato.

## Validação funcional no iPhone

Preparação: confirme iPhone 14 e versão exata do iOS; instale o app, conceda permissões e prepare os dois sentidos online. Em modo avião com Wi-Fi desligado, reabra o app e faça primeiro os estágios isolados:

1. **Tradução:** inicie **Nova rodada (texto digitado)**, digite uma frase, traduza e confirme que `transcriptionMS`, `textMS` e `audioStartMS` ficam `null`.
2. **Voz:** confirme idioma e áudio da tradução sem rede. Repita e verifique que a primeira `audioStartMS` não é substituída.
3. **Speech:** grave uma frase curta, toque Parar imediatamente e compare a transcrição com a referência antes de editar. A configuração local é obrigatória, sem fallback remoto.
4. **Original e reserva:** ouça a gravação, importe um arquivo local válido e compare sua transcrição. O JSON deve distinguir `microfone`, `arquivo pré-gravado` e `texto digitado`.
5. **Revisão:** corrija uma transcrição, traduza novamente e confirme `rawInput` inalterado, `input` corrigido e `edited: true`. Os tempos da rodada revisada incluem revisão humana.
6. **Persistência:** salve avaliações, encerre/reabra o app e exporte. Valide o arquivo contra o schema. Instale o protótipo anterior ou use arquivo legado controlado para verificar backup e migração sem perder dados.

Casos de erro/estado a executar e registrar separadamente:

- Permissão de microfone ou Speech negada; repetir após corrigir nos Ajustes.
- Reconhecimento local indisponível em um idioma; pacote Translation ausente/pendente; voz ausente. Confirmar mensagem acionável e nenhum fallback remoto.
- Silêncio, áudio inválido/vazio, importação cancelada e arquivo acessível só pela nuvem.
- Captura no limite de 20 s e transcrição que exceda o timeout.
- Timeouts configurados: ASR e tradução 30 s, preparação 180 s, início da voz 15 s e reprodução da voz até 120 s. Esses limites são proteções de estado, não metas de desempenho atingidas.
- Cancelar durante preparação, permissão, gravação, transcrição, tradução e reprodução. Começar outra rodada e confirmar que callbacks anteriores não alteram texto, estado nem medições.
- Trocar direção após cancelar, repetir reprodução e iniciar texto digitado após uma gravação. Conferir reinicialização dos metadados somente na nova rodada.
- Interrupção de sessão de áudio e app em segundo plano; nova tentativa após retornar.
- Falha controlada ao ler/salvar/exportar o JSON, com alerta visível e sem compartilhar como atual uma gravação antiga. Preservar arquivo corrompido para análise.

Não declare esses cenários aprovados apenas por inspeção do código. Cada um exige execução e anotação de resultado.

## Série comum de 36 tentativas

Use `Tests/frases.csv`, idêntico ao conjunto comum: PT01–PT06 e EN01–EN06, três repetições por frase, **36 tentativas planejadas no aparelho**. Testes de erro, texto digitado e reserva são séries separadas. Não substitua frases ou traduções de referência sem coordenar o contrato. A referência de tradução é um exemplo, sem exigir correspondência literal.

Mantenha ambiente, falante por idioma, distância (por exemplo 30 cm), microfone interno, ruído e volume constantes; anote bateria, estado térmico, rede, versão do sistema, execução fria/quente e uso de depurador. Registre ID e repetição nas observações. Faça a série principal sem Bluetooth e, de preferência, sem depurador; memória com Instruments é uma rodada separada.

Avalie separadamente a transcrição **original antes da edição** e a tradução: `não avaliada`, `correta`, `compreensível com erros` ou `incorreta`. Um avaliador que compreenda ambos os idiomas deve julgar o significado. Salve a avaliação e registre falhas; não transforme falha em latência zero nem descarte tentativas sem anotação.

## Interpretação dos tempos

- `transcriptionMS`: de Parar à transcrição final.
- `textMS`: de Parar ao retorno da tradução, aproximação da disponibilidade do texto, sem medir o frame renderizado.
- `audioStartMS`: de Parar ao callback `didStart` da síntese, aproximação sem medir o início acústico.
- `translationMS`: duração somente da chamada de tradução.

As durações usam relógio monotônico; a data da tentativa é ISO 8601 com timezone. O fim real da fala não é detectado: o operador toca Parar. Importações usam o fim da importação como referência; revisão mantém o instante e inclui tempo humano; texto digitado não tem tempos de fala. Nova rodada reinicia o contexto. Reprodução repetida não deve reescrever a primeira latência.

Analise por direção, origem e `edited`, separando execuções frias/quentes. Calcule mediana e p95 apenas para tempos válidos e informe `n` e falhas; p95 por posto mais próximo usa `ceil(0,95*n)`. Com poucos exemplos, não generalize. Não há meta de desempenho nem qualidade já atingida; nenhum resultado foi medido neste ciclo.

Registre espaço do app/pacotes quando exposto pelo iOS. Diferença de espaço livre é aproximação, não tamanho exato de modelo; use **não disponível** quando necessário. Para memória, use Instruments em ensaio próprio. A declaração `airplaneModeConfirmed` não detecta nem comprova rede desligada; mantenha evidência visual das condições.
