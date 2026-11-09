# Especificação comum — Tradutor Offline v1

Este documento orienta as duas frentes solicitadas pelo usuário: iOS e Android. Cada frente altera apenas sua pasta; mudanças neste contrato devem ser coordenadas no chat de origem. O projeto antigo e o ZIP de iPhone 14 ficam preservados como versão anterior.

## Escopo comum

Português brasileiro ↔ inglês americano, conversa por turnos com botões Gravar e Parar, limite de 20 segundos. Uma única tela principal com seleção de direção, preparação dos idiomas, gravação/parada, transcrição editável, ouvir original, tradução, reprodução e medições/avaliação/exportação JSON. Mesmos rótulos em português e ordem de blocos. No modo padrão, traduzir e falar automaticamente após a transcrição final. Permitir corrigir e repetir. Áudio de reserva importável de arquivos locais.

Fluxo: pronto → gravando → transcrevendo → traduzindo → reproduzindo → pronto. Preparação e erro são estados explícitos. Desabilitar ações incompatíveis; permitir nova tentativa. O erro precisa dizer o que corrigir: permissão, recurso local indisponível, pacote ausente, formato inválido ou timeout. Tratar interrupções e impedir callbacks antigos de alterar a rodada nova.

Não usar serviço remoto como fallback de reconhecimento, tradução ou voz. Preparação dos modelos ocorre com internet e separadamente da instalação sempre que possível. Se uma capacidade offline não puder ser garantida pela API, explicitar o limite e exigir teste em modo avião. Verificar as APIs atuais na documentação primária antes de implementar.

## Estrutura idêntica de módulos

Em cada plataforma, manter `Sources/App`, `Sources/Domain`, `Sources/Features/Conversation`, `Sources/Services/Speech`, `Sources/Services/Translation`, `Sources/Services/Synthesis`, `Sources/Services/Audio`, `Sources/Persistence`, `Resources`, `Tests`, `Docs` e `Configuration`. Arquivos nativos de build ficam onde a ferramenta exigir e apontam para Sources/Resources. Linguagens e detalhes internos podem variar.

- App: inicialização e composição.
- Domain: direção, estados da conversa, rodada de teste e contratos dos serviços.
- Features/Conversation: tela e coordenação do fluxo.
- Services: captura/reprodução e adaptadores locais de fala, tradução e voz.
- Persistence: salvar/carregar/exportar o formato comum de medições.
- Resources: recursos pequenos; modelos grandes não devem ser copiados ao repositório sem necessidade.
- Tests: casos e testes relevantes para o fluxo, medições e erros. Mesmas 12 frases de `shared/Tests/frases.csv`.
- Docs: `STATUS.md`, `CONFIGURACAO.md`, `TESTES.md`, `DEMONSTRACAO.md`.
- Configuration: configuração de build quando aplicável.

## Plataformas

**iOS:** Swift/SwiftUI, iPhone 14, iOS 18 ou posterior; confirmar versão instalada. Aproveitar o protótipo existente, refatorando para estes módulos. Speech com execução local obrigatória, Translation e AVSpeechSynthesizer. Conferir disponibilidade, preparar idiomas, documentar limites de acesso a tamanho dos modelos. Requer Mac/Xcode para build e teste físico; o ambiente atual é Linux.

**Android:** Kotlin nativo e UI simples seguindo a mesma tela. Aparelho ainda não informado. Avaliar Vosk com modelos pequenos locais PT/EN, ML Kit Translation e TextToSpeech com voz que não requeira conexão. Fixar versões compatíveis após verificar fontes oficiais. Se usar ASR do sistema, exigir a variante realmente on-device e erro explícito quando indisponível. Suportar ouvir o áudio original e importar reserva. Determinar minSdk/targetSdk conforme dependências e registrar em CONFIGURACAO.md. Não afirmar build ou teste físico sem execução.

A estrutura e o comportamento devem ser equivalentes; os frameworks e modelos podem ser diferentes. Compartilhar cenários e contratos, não código dependente de sistema operacional.

## Contrato de medições

Exportar array JSON conforme `medicoes.schema.json`, versão 1. Todas as propriedades do schema devem aparecer, com `null` para tempos não medidos e ausência de erro. UUID como string, `date` ISO 8601 com timezone, `platform` ios/android, `device` modelo físico conhecido quando disponível e `system` versão do sistema. Não confundir UIDevice.model genérico com identificação comprovada de iPhone 14.

Usar relógio monotônico para durações. `transcriptionMS`: de Parar até transcrição final. `textMS`: de Parar até tradução retornada, como aproximação da disponibilidade do texto. `audioStartMS`: de Parar ao callback de início de síntese, como aproximação, não latência acústica. `translationMS`: somente a chamada de tradução.

Texto digitado: tempos a partir da fala são null. Arquivo importado: referência no fim da importação e origin identificada. Correção manual: manter origem/instante da rodada e marcar edited; tempos incluem revisão humana. Não misturar origens e correções na análise de desempenho. `rawInput` preserva a transcrição antes de editar; `input` é o texto efetivamente traduzido. Avaliar também esse original. Reiniciar tempos e metadados quando começar nova rodada. Reproduzir de novo não deve reescrever a primeira latência.

`airplaneModeConfirmed` é declaração manual, não detecção nem prova. Não converter falhas em tempo zero. Registrar falhas relevantes e avaliações persistidas. Não anunciar estatísticas preenchidas com dados fictícios.

## Verificação e demonstração iguais

12 frases (6 por idioma), 3 repetições: 36 tentativas planejadas por aparelho. Estado inicial: 0 tentativas físicas executadas em ambas as plataformas. Registrar ruído, distância, falante, versão do sistema, bateria, aquecimento, execução fria/quente, rede e falhas. Medir espaço quando o SO expuser; caso contrário, marcar indisponível e distinguir estimativa de valor exato. Memória com Instruments/Android Profiler em rodada separada.

Demonstração em modo avião com Wi-Fi desligado: PT01, depois EN01, mostrando transcrição, tradução, voz e medição; depois ouvir/corrigir e exportar. Plano B com arquivos de áudio reais ou sintetizados preparados antes. Identificar a origem. Avaliar separadamente ASR, tradução e voz antes do fluxo inteiro.

## Entregas esperadas por frente

README com comandos de build e configuração, módulos implementados, testes executados com evidência, STATUS com o que funciona/o que não foi validado/próximo passo, roteiro de demonstração e pacote revisável. Não aguardar a outra frente para avançar e não editar arquivos dela. Evitar alterações ao contrato comum sem coordenação.
