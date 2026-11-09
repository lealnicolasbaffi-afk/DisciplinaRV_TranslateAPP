# Testes — iOS

`frases.csv` é cópia byte a byte de `../../shared/Tests/frases.csv`: 12 frases, seis por idioma. O plano físico prevê três repetições de cada uma; **nenhuma das 36 tentativas foi executada**.

Na raiz iOS:

```sh
python3 Tests/validate_contract.py --self-test
bash -n gerar-reserva-mac.sh
cmp Tests/frases.csv ../shared/Tests/frases.csv
```

O validador Python usa só a biblioteca padrão. Os autotestes são objetos sintéticos em memória; não geram medições. Para conferir um JSON efetivamente exportado pelo código Swift ou pelo aparelho:

```sh
python3 Tests/validate_contract.py --measurements /caminho/para/medicoes.json
```

`Unit/*Tests.swift` testa o núcleo Foundation de `Package.swift`; execute `swift test` em ambiente com toolchain Swift. `Conversation/ConversationModelTests.swift` usa serviços falsos no target `TradutorOfflineTests` do Xcode; execute por Product → Test. Ambos estão preparados e **não executados**, pois Swift/Xcode não estão disponíveis neste Linux. Conformidade estática e autoteste Python não comprovam a saída do codificador Swift, e serviços falsos não certificam modelos ou voz offline.

Veja `../Docs/TESTES.md` para condições, erros, persistência/legado, interpretação das latências e registro dos resultados reais.
