# Android — versão 0.2.0

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
