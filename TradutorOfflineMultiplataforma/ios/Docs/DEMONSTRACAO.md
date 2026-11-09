# Demonstração — iOS

Roteiro preparado para execução física, **ainda não validado**. Até 09/11/2026: nenhum build instalado e **0/36 tentativas físicas**. Reserve cerca de três minutos para mostrar o fluxo depois de concluir a preparação e os ensaios de `TESTES.md`.

## Antes da apresentação

1. No Mac, gere/compile o projeto, rode os testes Foundation e instale no iPhone 14 com iOS 18+; anote a versão exata.
2. Com internet, prepare PT → EN e EN → PT e espere o par instalado. Faça uma gravação e ouça a síntese em ambos os sentidos.
3. Prepare arquivos locais de PT01 e EN01 em **No Meu iPhone**; teste sua importação. Identifique se são gravações humanas ou síntese. Opcionalmente, `bash gerar-reserva-mac.sh` no Mac gera quatro falas sintéticas; não foi executado no Linux.
4. Ative modo avião, desligue Wi-Fi nos Ajustes, reabra o app e confirme os três estágios sem rede. Tenha bateria e espaço disponíveis. O checkbox do app apenas registra sua declaração.
5. Traga somente medições reais da série executada. Se a série ainda não ocorreu, apresente o plano e informe **0 tentativas**, sem preencher gráficos ou alegar aprovação.

## Roteiro na tela

1. Mostre nos Ajustes o modelo/versão do iPhone e o modo avião com Wi-Fi desligado. Abra **Tradutor Offline** e marque **Confirmo modo avião e Wi-Fi desligado**.
2. Em PT → EN, bloco **1. Falar**, toque **Gravar fala**, diga **PT01: “Bom dia. Onde fica a estação?”** e toque **Parar e traduzir** imediatamente. Mostre **2. Transcrição • pode editar**, **3. Tradução** e a voz automática. Se houver falha, mostre-a e registre-a.
3. Selecione **Inglês → Português**. Diga **EN01: “The station is two blocks away.”** e repita o fluxo. Não conte uma repetição improvisada como toda a série de 36.
4. Use **Ouvir gravação** para conferir o original. Avalie a transcrição antes de corrigir; se necessário, edite e use **Traduzir texto revisado**. Mostre que o original fica preservado em `rawInput` e que a revisão é identificada.
5. Em **Medições**, registre ID/repetição/condições, use **Salvar avaliação** e **Exportar medições JSON**. Explique que os tempos começam no toque Parar e o início da voz usa callback, sem medir a latência acústica.
6. Informe quantas tentativas realmente foram executadas, quantas falharam e quais limites permanecem. Arquivo importado, texto digitado e correção são condições distintas e devem aparecer assim na apresentação.

## Plano B

Se a captura falhar, use **Importar áudio de reserva** com um arquivo já local e anuncie **demonstração por arquivo pré-gravado**, identificando humano/sintético. A referência temporal passa a ser o fim da importação. Não apresente essa latência como resultado do microfone.

Se o reconhecimento local falhar, inicie **Nova rodada (texto digitado)** e demonstre tradução e voz, explicando que o fluxo completo não foi concluído. Esses dados têm origem texto digitado e tempos de fala `null`.

Se Translation ou voz não funcionar sem conexão, registre a falha e mostre o código/contrato e o plano de teste. Não religue a rede silenciosamente para manter a aparência de execução offline. O objetivo é uma prova de conceito verificável, com limites visíveis.
