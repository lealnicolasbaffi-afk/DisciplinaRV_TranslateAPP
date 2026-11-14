# DisciplinaRV_TranslateAPP

Tradutor de voz português brasileiro ↔ inglês americano, com foco inicial no Android e uma frente iOS independente.

O projeto fica em [TradutorOfflineMultiplataforma](TradutorOfflineMultiplataforma). A versão Android 0.2.0 inclui reconhecimento Vosk, tradução ML Kit e vozes locais, com preparação dos recursos para uso sem internet. O fluxo é por turnos de até 20 segundos.

- [Instalação, build e uso do Android](TradutorOfflineMultiplataforma/android/README.md)
- [Tradução ao vivo pela câmera: uso e limites](TradutorOfflineMultiplataforma/android/Docs/CAMERA.md)
- [Status da implementação](TradutorOfflineMultiplataforma/android/Docs/STATUS.md)
- [Testes e evidências](TradutorOfflineMultiplataforma/android/Docs/TESTES.md)
- [Especificação compartilhada](TradutorOfflineMultiplataforma/shared/ESPECIFICACAO.md)

A versão Android foi compilada e testada no Samsung SM-A546E com Android 16, incluindo tradução e voz em modo avião e duas rodadas interativas de microfone. A avaliação prolongada de qualidade, bateria e a campanha de 36 repetições continuam pendentes.

A atualização de Hudson adiciona OCR local e tradução sobre a imagem da câmera no Android. No Samsung SM-S926B / Android 16, foram conferidos preview em retrato, congelamento/retomada e tradução de uma embalagem sobre a imagem congelada. A qualidade e a estabilidade do alinhamento durante movimento contínuo ainda não foram avaliadas sistematicamente.


## Estado atual e maturidade

Atualizado em 13/11/2026. O sistema é um **protótipo acadêmico funcional**, com integração real de componentes e testes em aparelho. Ainda não é um produto com qualidade validada em diferentes dispositivos, condições ambientais e perfis de usuário.

- **Voz:** reconhecimento Vosk, tradução ML Kit e síntese local em PT-BR ↔ EN-US, em turnos de até 20 segundos. Há evidências anteriores de execução offline; não se trata de tradução simultânea de fala.
- **Câmera:** OCR latino ML Kit incluído no aplicativo, tradução local após preparar o pacote de idioma, associação temporal de regiões, confirmação textual, suavização dos cantos e expiração de regiões perdidas.
- **Sobreposição:** projeção do texto traduzido em um quadrilátero, com ajuste de fonte, quebras de linha e cache de layout. Um painel claro cobre o texto original; não há reconstrução da textura, reprodução da tipografia ou adaptação completa à aparência da superfície.
- **Congelamento:** OCR da captura exata exibida, permitindo leitura sem movimento. Uma embalagem real recebeu traduções em português, com erros de reconhecimento/tradução e fontes pequenas em blocos densos.
- **Interface:** câmera em retrato, controles por ícones com descrição acessível e tooltip, cartões de entrada/tradução, configurações em diálogo rolável e botão de congelar/retomar centralizado.
- **Limites:** acompanhamento 2D entre resultados de OCR, sem fluxo óptico entre reconhecimentos ou âncora 3D persistente. Movimento, oclusão, desfoque e pouca luz podem causar atraso, oscilação e perda de regiões.

Build do app e da instrumentação e lint passaram no ciclo de correções. Foram contabilizados 41 testes JVM: 39 aprovados e 2 ignorados pela ausência das fixtures Vosk. Também passaram três testes instrumentados de interface/armazenamento, incluindo abertura visível das configurações, e dois de OCR/projeção com imagem sintética. Esses testes verificam funcionamento e regressões; **não demonstram precisão estatística, estabilidade perceptiva ou equivalência a aplicativos comerciais**. Consulte os registros em [STATUS.md](TradutorOfflineMultiplataforma/android/Docs/STATUS.md) e [TESTES.md](TradutorOfflineMultiplataforma/android/Docs/TESTES.md).

Permanecem pendentes ensaios sistemáticos de câmera em movimento, giro físico para ambos os lados, fontes ampliadas, TalkBack, consumo de recursos e avaliação com participantes. As mudanças de interface não representam uma melhoria de usabilidade já demonstrada experimentalmente.

## Contexto e estado da arte

A proposta geral tem antecedentes comerciais e acadêmicos. Google Tradutor, Microsoft Translator e Traduzir da Apple oferecem combinações de texto, voz, imagem e recursos offline, com diferenças de plataforma, idioma e modo de operação. Não se assume equivalência entre seus recursos, nem superioridade deste protótipo.

- [Google Tradutor: tradução de imagens e preparação para câmera offline](https://support.google.com/translate/answer/6142483?co=GENIE.Platform%3DAndroid&hl=en).
- [Microsoft Translator: funções e limites dos pacotes offline no Android](https://www.microsoft.com/en-us/translator/help/android/).
- [Apple: tradução pela visualização da câmera](https://support.apple.com/guide/iphone/translate-with-the-camera-view-iphea8b95631/ios).

Na literatura, o [TranslatAR (2010)](https://vfragoso.com/pdfs/131.pdf) já apresentava detecção, acompanhamento e tradução de palavras com sobreposição em câmera. O trabalho [Incorporating Domain-Specific Neural Machine Translation into Augmented Reality Systems (2020)](https://aisel.aisnet.org/pacis2020/14/) investigou tradução contextual integrada à realidade aumentada. O [SimulTron (2024)](https://arxiv.org/abs/2406.02133) estudou tradução simultânea de fala executada em dispositivo móvel; é um antecedente sobre processamento local, com escopo diferente do fluxo por turnos deste aplicativo.

Essas referências são um ponto de partida, **não uma revisão sistemática ou exaustiva do estado da arte**. Integrar OCR, tradução, voz e câmera offline, isoladamente, não estabelece novidade científica. A denominação técnica atual é **sobreposição contextual 2D baseada em OCR**, sem afirmar registro espacial 3D persistente.

## Valor acadêmico já disponível

| Elemento | Valor atual | Limite da afirmação |
| --- | --- | --- |
| Integração Android de reconhecimento, tradução, síntese e câmera | Artefato de engenharia funcional e documentável | Integração não constitui, sozinha, contribuição científica original |
| Processamento local após preparação | Base para estudar uso sem conectividade e restrições do aparelho | Não comprova menor consumo ou melhor desempenho que alternativas |
| Consenso, associação e suavização temporal | Estratégia implementada que pode ser comparada experimentalmente | Ganhos de estabilidade ainda não medidos |
| Código, testes e registros | Base para reprodução e comparação | Requer versões, ambiente, dados e protocolos bem documentados |
| Interface de voz, texto e câmera | Permite estudar interação e leitura contextual | Benefício para usuários ainda precisa de experimento |

Para uma disciplina, o protótipo demonstra projeto, implementação e integração. Para sustentar um artigo, a contribuição mais defensável é produzir **evidência reproduzível sobre um problema delimitado**, usando o aplicativo como instrumento de pesquisa.

## Recortes com potencial de publicação

As propostas abaixo são planos de pesquisa, não resultados obtidos nem garantia de aceitação.

| Recorte | Pergunta principal | Contribuição possível | Trabalho necessário |
| --- | --- | --- | --- |
| Estabilidade da tradução na câmera — prioridade | Quanto a estabilização reduz a oscilação e quanto atrasa o acompanhamento? | Caracterização dos compromissos entre alinhamento, estabilidade e latência | Ablações, vídeos anotados, métricas e análise dos resultados |
| Leitura ao vivo versus imagem congelada | Qual modo facilita compreender textos e concluir tarefas? | Evidências sobre interação e legibilidade | Estudo com participantes e tarefas controladas |
| Plataforma experimental offline | Como oferecer um ambiente reutilizável para pesquisar tradução visual em Android? | Software de pesquisa e protocolo reproduzível | Instrumentação, documentação, distribuição e demonstração de utilidade científica |

Um recorte de aplicação possível é leitura de materiais em inglês sem internet em celulares intermediários. Ele precisa de requisitos do público e de aparelhos representativos; não se deve extrapolar a partir do Samsung SM-S926B. Melhorias de interface apoiam o estudo, mas não substituem contribuição e avaliação.

## Proposta prioritária: estabilidade e latência

**Título provisório:** Estabilidade e latência na sobreposição de tradução visual offline em smartphones Android: um estudo experimental.

**Pergunta de pesquisa:** quanto a confirmação temporal e a suavização dos cantos reduzem a oscilação da tradução, e qual atraso introduzem durante o movimento da câmera?

### Condições de comparação

1. **OCR direto:** sobreposição na região do resultado mais recente, sem estabilização temporal.
2. **Confirmação temporal:** consistência do texto antes da exibição, sem a estratégia completa de acompanhamento e suavização.
3. **Estratégia completa:** confirmação, associação espacial/textual, suavização e expiração de regiões.

É necessário implementar essas condições de forma explícita, registrar parâmetros e manter OCR, tradução, resolução e conteúdo comparáveis. Comparações adicionais removendo um componente por vez poderão separar seus efeitos, caso a análise principal deixe dúvidas. Não atribuir todo ganho à suavização se outras regras também mudarem.

### Material e execução

- Construir um conjunto de vídeos com regiões de texto e tempos anotados: câmera parada, tremor, deslocamento, inclinação, oclusão, troca de texto e saída do enquadramento.
- Variar iluminação, tamanho/densidade textual e perspectiva, com condições e limites documentados.
- Reproduzir os mesmos vídeos nas condições de comparação, controlando a entrada e os tempos dos frames. Diferenciar tempo do frame, tempo de processamento e tempo de apresentação.
- Complementar o replay com ensaios físicos ao vivo; replay não comprova sozinho a experiência completa da câmera.
- Registrar aparelho, Android, versões, parâmetros, estado de rede, aquecimento e condições de execução. Separar preparação inicial do uso com recursos já disponíveis.
- Usar materiais próprios ou com permissão de redistribuição para os dados públicos e as anotações.

### Métricas propostas

| Dimensão | Medição proposta |
| --- | --- |
| Alinhamento | Erro entre cantos da sobreposição e região anotada, normalizado pelo tamanho da região; eventual sobreposição entre polígonos |
| Oscilação | Variação dos cantos em cenas estáveis, sem confundir movimento real com erro do sistema |
| Acompanhamento | Atraso para responder a deslocamentos ou mudança de região |
| Conteúdo obsoleto | Tempo de permanência da tradução após troca, oclusão ou desaparecimento do texto |
| Disponibilidade | Fração de tempo em que o texto visível tem tradução correta exibida; evita premiar um método apenas por esconder regiões difíceis |
| Reconhecimento | Erros de OCR em relação à transcrição de referência, separados do erro geométrico |
| Legibilidade | Tamanho de fonte, cortes, quebras e densidade da tradução; avaliação por tarefa quando aplicável |
| Custo | Tempo de processamento e consumo de memória; bateria e temperatura em protocolo específico |

Definir previamente hipóteses, métricas principais, repetições e critérios de exclusão. Apresentar distribuições, tamanhos de efeito e intervalos de confiança, com análise pareada por cenas quando apropriado. Frames consecutivos não são observações independentes; a unidade experimental e a análise devem respeitar essa dependência. O número de cenas, aparelhos e repetições precisa de justificativa, não apenas um total arbitrário de frames.

**Resultado científico esperado:** conhecimento sobre quando cada estratégia ajuda ou prejudica o equilíbrio entre estabilidade, resposta, disponibilidade e legibilidade. Resultados negativos ou limites também são úteis quando obtidos com um protocolo rigoroso. Não há resultados quantitativos desse estudo neste momento.

## Alternativa: estudo de leitura e interação

**Pergunta:** uma tradução visual mais estável ajuda o usuário a compreender o conteúdo com mais rapidez e menos dificuldade?

Comparar ao vivo sem estabilização, ao vivo estabilizado e imagem congelada, usando tarefas com perguntas de compreensão. Medir acerto, tempo de tarefa, dificuldade percebida e preferência. Controlar tamanho e dificuldade do texto, familiaridade com inglês, ordem das condições e efeitos de aprendizagem; evitar que a pessoa simplesmente releia o mesmo conteúdo em todas as condições.

Justificar o tamanho da amostra pelo desenho e análise planejados. Antes de recrutar, alinhar com o orientador os procedimentos institucionais de ética, consentimento e tratamento dos dados aplicáveis ao estudo. Não afirmar melhoria de usabilidade apenas por preferência dos desenvolvedores ou por mudanças visuais.

## Alternativa: artigo de software de pesquisa

O enquadramento seria uma plataforma reutilizável de experimentação, e não apenas um aplicativo de tradução. Para isso, preparar:

- Modos de comparação configuráveis e instrumentação de métricas, sem misturar dados sintéticos com medições de usuários.
- Releases identificadas, ambiente reproduzível, instruções de instalação e demonstrações executáveis.
- Documentação da arquitetura, decisões, limites, testes e caminhos para extensão.
- Licença apropriada e compatibilidade com as condições dos componentes, modelos e dados utilizados.
- Dados/anotações e scripts de análise redistribuíveis, com demonstração de uso em pesquisa.

## Possíveis veículos e posicionamento

A escolha depende do resultado final, da contribuição, das exigências editoriais e das condições de publicação vigentes. Compatibilidade temática não implica aceitação.

- **International Journal of Virtual Reality:** possibilidade a investigar para resultados originais sobre apresentação visual e interação em XR. O artigo precisaria demonstrar contribuição experimental compatível com o escopo, descrevendo com precisão o acompanhamento 2D. [Escopo oficial](https://ijvr.eu/aimScope).
- **SoftwareX:** possibilidade para software de pesquisa e suas aplicações, caso a plataforma seja reutilizável e sua utilidade científica esteja demonstrada. [Descrição oficial da editora](https://www.prod.webpresence.elsevier.com/researcher/author/tools-and-resources/research-elements-journals).
- **Journal of Open Source Software (JOSS):** alternativa futura condicionada a software aberto de pesquisa, relevância demonstrada e maturidade de desenvolvimento público. As diretrizes consultadas exigem histórico público superior a seis meses e evidência de uso em pesquisa; não é uma via automática para publicar um aplicativo recém-desenvolvido. [Requisitos oficiais](https://joss.readthedocs.io/en/latest/submitting.html).

Diretrizes de autoria e uso de IA devem ser conferidas no veículo escolhido. As contribuições humanas, decisões de pesquisa e revisão dos resultados precisam estar claras, com declaração da assistência por IA conforme exigido pela revista.

## Próximos passos para um artigo

1. Fazer revisão bibliográfica dirigida e verificar a lacuna de pesquisa antes de alegar originalidade.
2. Escolher uma pergunta principal; priorizar estabilidade/latência em vez de acumular novas funções.
3. Implementar condições de comparação e instrumentação, verificando seu funcionamento.
4. Preparar dados anotados e protocolo; executar piloto para identificar erros de medição.
5. Definir e executar o experimento, analisar resultados e ameaças à validade.
6. Disponibilizar artefatos reproduzíveis e escrever o artigo em torno dos resultados efetivos.
7. Selecionar a revista pelo conteúdo produzido e adaptar a submissão às suas diretrizes.

**Posicionamento atual:** o artefato já existe; a contribuição publicável ainda precisa ser sustentada por evidência. O objetivo não é alegar um tradutor inédito ou superior aos comerciais, mas explicar e medir um problema específico com rigor.
