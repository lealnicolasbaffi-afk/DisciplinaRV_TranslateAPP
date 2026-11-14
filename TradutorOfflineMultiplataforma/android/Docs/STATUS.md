# Android — versão 0.2.0

## Correção de configuração e centralização — 13/11/2026

Configuração agora abre em diálogo rolável, em vez de expandir um painel fora
da área visível no fim da conversa. Ícones são desenhados no centro geométrico
do botão, sem depender do alinhamento de compound drawables do TextView.
Controle principal circular de 64 dp na câmera; controles auxiliares de 52 dp.
Botões desabilitados reduzem a opacidade do símbolo. Teste instrumentado dedicado
verifica se as ações de preparo e fechamento estão visíveis após tocar em configurar.
Build/lint aprovados e correção conferida visualmente no Samsung SM-S926B:
configurações em diálogo e pausa centralizada no círculo. Três testes de interface/
armazenamento passaram, incluindo o novo teste de configuração; os dois testes de
OCR/projeção também passaram neste ciclo.

## Interface compacta com ícones — 13/11/2026

Ações frequentes usam ícones vetoriais nativos de traço consistente: câmera,
configuração, microfone/parar, importar áudio, ouvir, nova conversa, voltar,
baixar idioma e congelar/retomar. Todos têm descrição acessível e tooltip.
Conversa organizada em cartões com barras de ações, configuração fora do fluxo
principal e avaliações recolhíveis. Avisos de erro permanecem visíveis.
Câmera mantém retrato, preview amplo e nomes dos idiomas por extenso.
Build, testes JVM e lint aprovados; interface instalada e conferida visualmente
no Samsung SM-S926B. TalkBack e fontes ampliadas ainda exigem ensaio específico.

## Ajustes de interface — 13/11/2026

Interface nativa com paleta verde, superfícies claras, tipografia e espaçamento
compartilhados. Conversa com entrada e tradução em cartões e destaque para falar
e traduzir. Gravar/parar alternam conforme o estado. Medições permanecem recolhíveis.
Câmera fixada em retrato, com cabeçalho compacto, tema escuro e controles inferiores;
o preparo mostra “Idioma pronto” quando disponível, preservando a área do preview.

Build principal/instrumentação e lint aprovados com JDK Temurin 21 e SDK 35.
Testes JVM: 41 contabilizados, 39 aprovados, 2 ignorados por fixtures Vosk ausentes.
Instalação por ADB no Samsung SM-S926B/Android 16 preservando os dados.
Quatro testes instrumentados passaram: OCR sintético, projeção, abertura/recriação
da conversa e recuperação de armazenamento isolado. Conferência visual confirmou
interface da conversa, preview real em retrato e congelamento com opção de retomada.
Uma captura inicial desfocada informou ausência de texto legível. Na conferência
final, uma embalagem impressa foi reconhecida e recebeu traduções em português
sobre as regiões na imagem congelada. Há erros de reconhecimento/tradução e fonte
pequena nos blocos densos; isso não comprova estabilidade durante movimento.
Fontes ampliadas, TalkBack, giro físico para ambos os lados e avaliação prolongada
da sobreposição continuam pendentes.

## Estabilização de câmera e projeção na superfície — 09/11/2026

Implementado acompanhamento temporal de regiões por geometria e texto, consenso
antes de traduzir, suavização adaptativa dos quatro cantos e descarte de regiões
perdidas. Sobreposição agora usa projeção em perspectiva dentro do quadrilátero
do texto, com fonte ajustada e cache de layout. Incluídos foco por toque, zoom,
solicitação de OCR próximo de 1280×960 e captura congelada com OCR da imagem exata.
A área do preview permanece com tamanho estável quando as mensagens mudam.

Build principal/instrumentação e lint passaram. Dos 41 testes JVM, 39 passaram
(incluindo 12 novos de acompanhamento) e 2 foram ignorados por falta dos ZIPs.
Dois testes instrumentados passaram no Samsung SM-S926B / Android 16: OCR ML Kit
real sobre imagem sintética e desenho/clipping de tradução em quadrilátero
inclinado. App atualizado por ADB e tela principal reaberta com sucesso.

O acompanhamento é 2D entre resultados de OCR; não usa fluxo óptico nem âncora
3D. Estabilidade perceptiva com movimento, foco, oclusão, imagem congelada e modo
avião ainda exige o roteiro físico de [CAMERA.md](CAMERA.md). Os testes sintéticos
não medem equivalência visual ao Google Lens nem certificam esses cenários.

## Atualização de Hudson: câmera e OCR local — 09/11/2026

Adicionada tradução ao vivo pela câmera traseira com CameraX 1.4.2 e OCR latino
ML Kit 16.0.1 incluído no APK. A tela permite trocar PT/EN, preparar tradução
independentemente de Vosk/TTS e desenhar traduções nas regiões reconhecidas.
Inclui cache limitado, descarte de resultados antigos e liberação da câmera ao sair.

Antes da integração com a versão 0.2.0: build nativo, 23 testes JVM e lint passaram;
o APK foi instalado no Samsung SM-S926B / Android 16, a tela inicial abriu e o
botão da câmera foi confirmado. O aparelho bloqueou antes do ensaio de câmera:
preview, OCR físico, alinhamento e tradução sobre imagem permanecem pendentes.
O resultado no SM-S926B é separado das evidências no SM-A546E descritas abaixo.
Consulte [CAMERA.md](CAMERA.md) para o roteiro físico.

Após integrar com a versão 0.2.0, o build do app e da instrumentação e lint
passaram. Dos 29 testes JVM, 27 passaram e 2 foram ignorados por falta dos ZIPs
oficiais de teste neste ambiente; não houve falhas. A instrumentação não foi
executada neste ciclo. Detalhes em [TESTES.md](TESTES.md).

## Estado da versão 0.2.0 recebida do repositório

Atualizado em 09/11/2026. Código ativo nesta pasta de trabalho. A cópia original em Downloads, a frente iOS e o contrato shared foram preservados.

## Entregue

- APK debug real compilado com SDK 35 e Build Tools 35.0.0; instalado no Samsung SM-A546E, Android 16/API 36, arm64-v8a.
- Fluxo PT-BR ↔ EN-US por turnos de até 20 segundos: gravação PCM/WAV, Vosk local, tradução ML Kit e voz local; edição, replay, áudio de reserva, avaliações e exportação JSON.
- Download explícito dos modelos Vosk por Wi-Fi com SHA-256, limites e importação atômica validada nativamente; importação de ZIPs continua disponível.
- Verificação local ao abrir sem iniciar downloads; atalho para configurações de voz; cancelamento de operações e preservação da rodada ao recriar a Activity.
- Configuração e medições recolhíveis para priorizar a conversa. Ícone e regras explícitas de backup/transferência.
- Scripts PowerShell para build, instalação, preparação e ensaio offline com restauração da rede.

## Verificado

- Build Android, 29 testes JVM e lint passaram. Os 29 testes foram executados com zero falhas e zero ignorados, incluindo os dois ZIPs oficiais. Lint: zero erros; advertências de versões fixadas, internacionalização da UI, ícone monocromático e verificações redundantes de API.
- Três testes instrumentados passaram no aparelho: abertura/recriação da tela, recuperação AtomicFile e importação/abertura nativa dos dois modelos Vosk, com silêncio rejeitado nos dois sentidos.
- Preparação real do app passou no aparelho: modelos Vosk baixados pelo código Android, conferidos e instalados; tradução portuguesa instalada; vozes pt-BR-language e en-US-language selecionadas pelo motor Google.
- Arquivos Vosk extraídos: PT 53.552.717 bytes; EN 70.898.967 bytes. Esses números são arquivos em disco, não RAM.

Tradução e síntese nos dois sentidos passaram em teste instrumentado com modo avião e nenhuma rede ativa. O usuário também confirmou duas rodadas reais de microfone, incluindo EN → PT sem internet; os dois registros JSON passaram no schema comum. Resultados e medições: consulte TESTES.md e as evidências. Testes instrumentados de serviço não contam como as 36 tentativas de fala humana planejadas.

## Limites restantes

A qualidade de reconhecimento/tradução e a latência em uso contínuo exigem avaliação com falantes, ruído e frases reais. O plano de 36 tentativas, memória, bateria e térmica não está concluído. O fluxo é por turnos, com processamento após Parar/20 segundos. A rodada é mantida na recriação da Activity, mas não é restaurada após encerramento do processo; medições já salvas permanecem.

O APK entregue é de desenvolvimento. Publicação em loja, assinatura de produção e conclusão da frente iOS são etapas próprias.
