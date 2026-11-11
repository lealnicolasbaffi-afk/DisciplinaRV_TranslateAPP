#!/usr/bin/env bash
set -euo pipefail

# Apenas em runner macOS descartável. Não imprima certificados nem segredos.
for name in IOS_TEAM_ID IOS_BUNDLE_ID IOS_CERTIFICATE_P12_BASE64 IOS_CERTIFICATE_PASSWORD IOS_PROVISIONING_PROFILE_BASE64 IOS_KEYCHAIN_PASSWORD RUNNER_TEMP; do
  if [[ -z "${!name:-}" ]]; then
    printf 'Configuração ausente: %s\n' "$name" >&2
    exit 1
  fi
done

private_dir="$(mktemp -d "$RUNNER_TEMP/ios-signing.XXXXXX")"
keychain="$private_dir/ci.keychain-db"
profile_file="$private_dir/profile.mobileprovision"
decoded_profile="$private_dir/profile.plist"
profile_installed=''
cleanup() {
  security delete-keychain "$keychain" >/dev/null 2>&1 || true
  if [[ -n "$profile_installed" ]]; then rm -f "$profile_installed"; fi
  rm -rf "$private_dir"
}
trap cleanup EXIT

printf '%s' "$IOS_CERTIFICATE_P12_BASE64" | base64 -D > "$private_dir/cert.p12"
printf '%s' "$IOS_PROVISIONING_PROFILE_BASE64" | base64 -D > "$profile_file"
security cms -D -i "$profile_file" > "$decoded_profile"

# Recusa perfis de desenvolvimento, App Store, enterprise ou de outro app.
profile_uuid="$(python3 - "$decoded_profile" <<'PY'
import datetime
import os
import plistlib
import sys

with open(sys.argv[1], 'rb') as f:
    p = plistlib.load(f)
team = os.environ['IOS_TEAM_ID']
bundle = os.environ['IOS_BUNDLE_ID']
entitlements = p.get('Entitlements', {})
expected = team + '.' + bundle
expiry = p.get('ExpirationDate')
if (team not in p.get('TeamIdentifier', [])
        or entitlements.get('application-identifier') != expected
        or entitlements.get('get-task-allow', False)
        or p.get('ProvisionsAllDevices', False)
        or not p.get('ProvisionedDevices')
        or not expiry or expiry <= datetime.datetime.utcnow()):
    sys.exit('Perfil inválido: use Ad Hoc vigente com Team ID e Bundle ID exatos e dispositivo registrado.')
print(p['UUID'])
PY
)"

security create-keychain -p "$IOS_KEYCHAIN_PASSWORD" "$keychain"
security set-keychain-settings -lut 21600 "$keychain"
security unlock-keychain -p "$IOS_KEYCHAIN_PASSWORD" "$keychain"
security import "$private_dir/cert.p12" -k "$keychain" -P "$IOS_CERTIFICATE_PASSWORD" -T /usr/bin/codesign
security set-key-partition-list -S apple-tool:,apple:,codesign: -s -k "$IOS_KEYCHAIN_PASSWORD" "$keychain" >/dev/null
security list-keychains -d user -s "$keychain"
mkdir -p "$HOME/Library/MobileDevice/Provisioning Profiles"
profile_installed="$HOME/Library/MobileDevice/Provisioning Profiles/$profile_uuid.mobileprovision"
cp "$profile_file" "$profile_installed"

python3 - "$private_dir/ExportOptions.plist" "$profile_uuid" <<'PY'
import os
import plistlib
import sys

options = {
    'method': 'ad-hoc',
    'signingStyle': 'manual',
    'teamID': os.environ['IOS_TEAM_ID'],
    'provisioningProfiles': {os.environ['IOS_BUNDLE_ID']: sys.argv[2]},
}
with open(sys.argv[1], 'wb') as f:
    plistlib.dump(options, f)
PY

xcodebuild -project TradutorOffline.xcodeproj -scheme TradutorOffline \
  -configuration Release -destination 'generic/platform=iOS' \
  -archivePath "$RUNNER_TEMP/TradutorOffline.xcarchive" \
  PRODUCT_BUNDLE_IDENTIFIER="$IOS_BUNDLE_ID" DEVELOPMENT_TEAM="$IOS_TEAM_ID" \
  CODE_SIGN_STYLE=Manual CODE_SIGN_IDENTITY='Apple Distribution' \
  PROVISIONING_PROFILE_SPECIFIER="$profile_uuid" archive
xcodebuild -exportArchive -archivePath "$RUNNER_TEMP/TradutorOffline.xcarchive" \
  -exportOptionsPlist "$private_dir/ExportOptions.plist" \
  -exportPath "$RUNNER_TEMP/tradutor-export"
