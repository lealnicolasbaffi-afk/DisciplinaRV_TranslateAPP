#!/bin/bash
set -euo pipefail
command -v say >/dev/null || { echo 'Execute no macOS com as vozes Luciana e Samantha instaladas.' >&2; exit 1; }
cd -- "$(dirname -- "$0")"
mkdir -p reserva
say -v Luciana -o reserva/PT01.aiff 'Bom dia. Onde fica a estação?'
say -v Samantha -o reserva/EN01.aiff 'The station is two blocks away.'
say -v Luciana -o reserva/PT02.aiff 'Eu gostaria de um copo de água.'
say -v Samantha -o reserva/EN02.aiff 'I need a ticket for tomorrow.'
echo 'Copie reserva/ para Arquivos no iPhone e teste a importação antes da demonstração.'
