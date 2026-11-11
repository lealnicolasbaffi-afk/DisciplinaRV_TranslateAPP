# Compilar iOS a partir do Linux com um Mac remoto

O workflow [ios-macos.yml](../../../.github/workflows/ios-macos.yml) pode ser iniciado manualmente no GitHub Actions e também roda em pull requests que alterem a pasta iOS ou o próprio workflow. Os comandos são enviados do Linux, mas a compilação ocorre em uma máquina virtual **macOS**, com Xcode e o SDK da Apple. Ele gera o projeto com XcodeGen, compila para iOS, empacota um IPA sem assinatura e executa os testes do pacote Foundation. Esse IPA precisa ser assinado antes da instalação e não substitui os testes no iPhone. O workflow foi preparado, mas ainda não foi executado no GitHub.

É necessário hospedar esta pasta em um repositório GitHub com Actions e runner macOS habilitados. Em repositórios privados pode haver limites de minutos e cobrança; confira a cota antes de rodar. Não publique seu código em um repositório público sem considerar se deseja expô-lo.

1. Envie o projeto ao repositório, incluindo `.github/workflows/ios-macos.yml` e toda a pasta iOS, na branch escolhida.
2. Na página **Actions**, selecione **iOS no macOS remoto**, **Run workflow**, escolha a branch e mantenha `export_adhoc` desligado. O workflow também pode ser iniciado com `gh workflow run ios-macos.yml --ref NOME_DA_BRANCH`.
3. Abra a execução e examine os logs. Corrija erros de build antes de configurar assinatura. Um resultado verde significa apenas que as etapas realizadas passaram no runner; não verifica a câmera, os recursos Apple nem a experiência offline no aparelho.

Em pull requests, a versão deste workflow não ativa a assinatura nem solicita os segredos. Para pull requests vindos de forks, o GitHub normalmente não disponibiliza segredos e pode exigir aprovação para executar o workflow. Pessoas com acesso de escrita ao próprio repositório podem alterar workflows e usar segredos do repositório; mantenha esse acesso restrito a colaboradores confiáveis. Antes de qualquer execução manual com assinatura, revise o código e a branch escolhidos. Detalhes: [segurança do GitHub Actions](https://docs.github.com/en/actions/reference/security/secure-use).

## Teste gratuito com conta Apple pessoal

Se o build passar, baixe o artefato `TradutorOffline-sem-assinatura` no GitHub Actions. O IPA poderá ser testado com um instalador de terceiros para Linux que ofereça assinatura usando uma conta Apple pessoal, como [SideServer para Linux](https://github.com/SideStore/SideServer-for-Linux). A compatibilidade com este app, a versão do iOS e o dispositivo ainda não foi verificada. Leia as instruções e avalie o acesso solicitado à conta Apple antes de usar a ferramenta. Nunca envie senhas ou códigos de autenticação para este chat. Com a conta pessoal gratuita, os perfis de instalação expiram em sete dias e exigem renovação; veja a [Apple](https://developer.apple.com/help/account/basics/about-your-developer-account/).

## Opcional: gerar IPA Ad Hoc

Este caminho é **experimental e ainda não testado**. Exige associação paga ao Apple Developer Program, acesso adequado à conta e um iPhone registrado. Ad Hoc e Personal Team são modalidades diferentes: o Personal Team gratuito permite testes pessoais pelo Xcode, mas não fornece este fluxo de distribuição. Uma alternativa para quem tem um Mac disponível é a instalação direta pelo Xcode; o perfil gratuito expira após sete dias. Outra alternativa com programa pago é distribuir por TestFlight usando um fluxo próprio de envio ao App Store Connect; este workflow **não** envia ao TestFlight.

1. No [portal Apple Developer](https://developer.apple.com/account/), registre um App ID **explícito** único e o UDID do iPhone; crie um certificado **Apple Distribution** e um perfil de distribuição **Ad Hoc** que selecione esse certificado, esse App ID e esse aparelho. A Apple descreve [como criar o perfil](https://developer.apple.com/help/account/provisioning-profiles/create-an-ad-hoc-provisioning-profile/) e [registrar o aparelho](https://developer.apple.com/help/account/devices/register-a-single-device/). Guarde o arquivo `.mobileprovision` e o ID da equipe. O perfil deve estar vigente e ter identificador `TEAM_ID.BUNDLE_ID`; este script não aceita curinga ou perfil de desenvolvimento. Assegure-se de usar um perfil normal, pois perfis especiais offline expiram em sete dias.
2. O certificado deve estar acompanhado da **chave privada correspondente** em um arquivo `.p12`, protegido por senha. É possível gerar uma chave RSA e CSR com OpenSSL no Linux, guardar a chave em local privado, enviar somente a CSR à Apple para emitir o certificado de distribuição e combinar o `.cer` emitido com a chave em `.p12`. Por exemplo, em um diretório privado fora do repositório, substituindo o nome do certificado baixado:

   ```sh
   umask 077
   openssl req -new -newkey rsa:2048 -nodes -keyout apple-distribution.key -out apple-distribution.csr
   # Envie somente apple-distribution.csr ao criar o certificado no portal Apple.
   openssl x509 -inform DER -in distribution.cer -out distribution.pem
   openssl pkcs12 -export -legacy -inkey apple-distribution.key -in distribution.pem -out distribution.p12
   ```

   O OpenSSL pedirá os dados da CSR e uma senha para o `.p12`. Não inclua a chave, o `.p12`, o perfil ou senhas no repositório, em logs ou neste chat. Guarde a chave em local protegido; sem ela não é possível usar o certificado. Gerar ou revogar certificados afeta a equipe Apple; entenda os certificados existentes antes de alterá-los.
3. Em **Settings → Secrets and variables → Actions** no GitHub, configure as variáveis `IOS_TEAM_ID` e `IOS_BUNDLE_ID` com os valores exatos registrados na Apple. Configure os segredos `IOS_CERTIFICATE_P12_BASE64`, `IOS_CERTIFICATE_PASSWORD`, `IOS_PROVISIONING_PROFILE_BASE64` e `IOS_KEYCHAIN_PASSWORD`. O último é uma senha nova e aleatória para o chaveiro temporário do runner, não a senha da conta Apple. Para produzir os valores Base64 no Linux sem copiá-los para o chat, com `gh` autenticado no repositório correto:

   ```sh
   base64 -w0 distribution.p12 | gh secret set IOS_CERTIFICATE_P12_BASE64
   base64 -w0 perfil.mobileprovision | gh secret set IOS_PROVISIONING_PROFILE_BASE64
   gh secret set IOS_CERTIFICATE_PASSWORD
   gh secret set IOS_KEYCHAIN_PASSWORD
   ```

   Os dois últimos comandos pedem o valor interativamente. Também é possível usar a interface web de segredos do GitHub. Veja a [documentação do GitHub para assinatura](https://docs.github.com/en/actions/how-tos/deploy/deploy-to-third-party-platforms/sign-xcode-applications). Nunca exponha a senha da Apple, códigos de autenticação ou a chave privada no chat. O workflow deve ser executado somente a partir de código confiável: quem pode alterar o workflow e executá-lo pode comprometer seus segredos.
4. Execute novamente o workflow com `export_adhoc` ligado. Se terminar com sucesso, baixe o artefato `TradutorOffline-adhoc` na página da execução antes do prazo de sete dias. O IPA fica acessível a quem tem acesso ao artefato; trate-o como arquivo privado. O processo verifica alguns dados do perfil, mas o Xcode ainda valida o certificado e demais condições.
5. Instale o IPA assinado em um dos aparelhos incluídos no perfil. Ferramentas USB no Linux, como `ideviceinstaller`, podem oferecer esse caminho, mas a compatibilidade varia com o iOS e **não foi verificada neste projeto**. O certificado e o perfil não removem essa limitação. O dispositivo pode exigir [Modo de Desenvolvedor](https://developer.apple.com/documentation/xcode/enabling-developer-mode-on-a-device) e confirmação de confiança. Para equipes novas, a Apple informa que o primeiro uso pode exigir internet para validar o certificado; leia [Provisioning profile updates](https://developer.apple.com/help/account/provisioning-profiles/provisioning-profile-updates) antes de testar offline.

Um IPA gerado sem erro não comprova que será instalado nem que o aplicativo funcionará. Registre separadamente o resultado da instalação, o preparo dos idiomas e os testes com rede desligada.
