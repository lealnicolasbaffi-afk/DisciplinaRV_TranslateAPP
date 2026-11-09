# Tradutor Offline — Android 0.2.0

Aplicativo nativo Kotlin, português brasileiro ↔ inglês americano, por turnos de até 20 segundos. Após Parar, o app transcreve com Vosk, traduz com ML Kit e reproduz com uma voz local do Android. Também aceita texto digitado e WAV de reserva.

## Instalar e preparar

1. Instale o APK de teste `TradutorOfflineAndroid-debug.apk` ou use o script abaixo com o aparelho autorizado no ADB.
2. Com Wi-Fi, abra **Configurar idiomas e vozes** → **Baixar e preparar idiomas (Wi-Fi)**. O app baixa os dois modelos Vosk oficiais, confere SHA-256 e verifica os modelos nativos e a tradução local. Os ZIPs podem ser importados manualmente como alternativa.
3. Se o app indicar voz ausente, use **Instalar ou configurar vozes offline** e instale os dados pt-BR e en-US. Depois toque em **Verificar recursos locais (sem baixar)**.
4. Quando os recursos estiverem prontos, a configuração se recolhe. Selecione a direção, toque em **Gravar**, conceda o microfone, fale e toque em **Parar**. O limite automático é 20 segundos.
5. Confira o funcionamento em modo avião, com Wi-Fi desligado. Reabrir o app só verifica os recursos já instalados, sem iniciar downloads.

**Ouvir original**, correção da transcrição, **Traduzir e falar** e replay estão disponíveis. **Nova rodada** começa outra tentativa. O WAV de reserva deve ser PCM16, mono, 16 kHz, até 20 segundos.

## Build no Windows

Requisitos: JDK completo 17, SDK Platform 35 e Build Tools 35.0.0. Neste computador o SDK fica em `android/work/sdk`, e o script localiza o JDK do Unity já instalado. Caches e ferramentas ficam fora do código versionado.

```powershell
cd android
.\Configuration\check.ps1 -DeviceTests
# Com dependências já baixadas:
.\Configuration\check.ps1 -Offline -DeviceTests
# Outro computador:
.\Configuration\check.ps1 -Jdk 'C:\caminho\jdk-17' -AndroidSdk 'C:\caminho\sdk' -DeviceTests
```

O script verifica o Wrapper e executa assembleDebug, testDebugUnitTest e lintDebug. `-DeviceTests` também compila o APK de instrumentação. O APK fica em `build/outputs/apk/debug/TradutorOfflineAndroid-debug.apk`. A versão debug é assinada com chave de desenvolvimento; publicação em loja exige assinatura e revisão de lançamento próprias.

No Linux/macOS, configure JAVA_HOME e ANDROID_HOME e execute `./Configuration/check.sh`. Para compilar instrumentação acrescente `assembleDebugAndroidTest`.

## Executar no aparelho por USB

```powershell
# Liste os aparelhos:
.\work\sdk\platform-tools\adb.exe devices -l
# Instale e execute os três testes nativos isolados:
.\Configuration\device.ps1 -Serial SERIAL_DO_APARELHO
# Prepare modelos e verifique vozes no armazenamento real do app:
.\Configuration\device.ps1 -Serial SERIAL_DO_APARELHO -Prepare
# Teste tradução e síntese offline; o script restaura a conexão no finally:
.\Configuration\device.ps1 -Serial SERIAL_DO_APARELHO -OfflineTest
```

Os testes nativos de Vosk usam os ZIPs em `work/models`, incluídos somente no APK de testes. Sem esses arquivos, o teste de modelos reais é marcado como ignorado. Os dois testes JVM com ZIPs oficiais têm a mesma condição. Os demais testes usam fixtures pequenas. Os ensaios de serviço não criam medições fictícias na coleção do usuário.

## Dados e ciclo de vida

As medições ficam em `files/medicoes.json`, no armazenamento privado, com gravação atômica e contrato v1 de `../shared/medicoes.schema.json`. Abra **Mostrar medições e avaliação** para avaliar, anotar e exportar JSON pelo seletor do Android. Exporte antes de desinstalar ou limpar os dados.

A rodada corrente é mantida ao recriar a Activity. Sair da tela ou girá-la durante uma operação cancela áudio/trabalho ativo; o texto e as medições da rodada permanecem. Encerrar o processo não restaura o áudio/estado corrente; medições já salvas continuam no armazenamento. Backups e transferências automáticas estão desativados.

## Evidências e limites

Consulte [STATUS](Docs/STATUS.md), [TESTES](Docs/TESTES.md), [configuração técnica](Docs/CONFIGURACAO.md) e [demonstração](Docs/DEMONSTRACAO.md). Testes de serviço offline, reconhecimento por microfone e avaliação de qualidade são resultados distintos.

A frente iOS e os arquivos shared foram preservados. O código atualizado está nesta pasta de trabalho; a cópia original em Downloads não foi alterada.
