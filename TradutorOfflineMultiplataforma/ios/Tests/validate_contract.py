#!/usr/bin/env python3
"""Verificação estática iOS e do contrato JSON; não compila nem executa o app.

Sem argumentos, verifica fontes/configuração/CSV/script. Use --measurements para
validar um JSON exportado pelo app ou por testes Swift. --self-test usa somente
objetos sintéticos em memória, sem produzir medições ou resultados de aparelho.
"""

from __future__ import annotations

import argparse
import copy
import csv
from datetime import datetime
import io
import json
import math
from pathlib import Path
import re
import shlex
import shutil
import subprocess
import sys
from typing import Any
import uuid


ROOT = Path(__file__).resolve().parents[1]
SHARED = ROOT.parent / "shared"
MODULES = (
    "Sources/App", "Sources/Domain", "Sources/Features/Conversation",
    "Sources/Services/Speech", "Sources/Services/Translation",
    "Sources/Services/Synthesis", "Sources/Services/Audio", "Sources/Persistence",
)
DIRECTORIES = (*MODULES, "Resources", "Tests", "Docs", "Configuration")
REQUIRED_FILES = (
    "README.md", "project.yml", "Package.swift", "gerar-reserva-mac.sh",
    "Docs/STATUS.md", "Docs/CONFIGURACAO.md", "Docs/TESTES.md",
    "Docs/DEMONSTRACAO.md", "Resources/README.md",
)
SUPPORTED_KEYWORDS = {
    "$schema", "title", "type", "items", "required", "properties", "enum",
    "format", "minimum", "additionalProperties",
}
DATE_TIME = re.compile(r"\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d+)?(?:Z|[+-]\d\d:\d\d)\Z")


def schema_errors(value: Any, schema: dict[str, Any], path: str = "$") -> list[str]:
    """Aplica todos os recursos usados pelo schema comum v1, sem dependências.

    Rejeita keywords desconhecidas para não ignorar extensões futuras. Não é
    um validador genérico de todos os recursos do JSON Schema draft 2020-12.
    """
    unknown = set(schema) - SUPPORTED_KEYWORDS
    if unknown:
        raise ValueError(f"Schema usa keywords não suportadas: {sorted(unknown)}")
    errors: list[str] = []
    predicates = {
        "array": lambda v: isinstance(v, list),
        "object": lambda v: isinstance(v, dict),
        "string": lambda v: isinstance(v, str),
        "boolean": lambda v: isinstance(v, bool),
        "number": lambda v: (isinstance(v, int) and not isinstance(v, bool)) or
                            (isinstance(v, float) and math.isfinite(v)),
        "null": lambda v: v is None,
    }
    expected = schema.get("type")
    if expected is not None:
        types = expected if isinstance(expected, list) else [expected]
        if any(kind not in predicates for kind in types):
            raise ValueError(f"Tipo de schema não suportado: {types}")
        if not any(predicates[kind](value) for kind in types):
            return [f"{path}: esperado {types}, recebido {type(value).__name__}"]
    if "enum" in schema and value not in schema["enum"]:
        errors.append(f"{path}: valor fora de enum: {value!r}")
    if isinstance(value, dict):
        properties = schema.get("properties", {})
        for key in schema.get("required", []):
            if key not in value:
                errors.append(f"{path}.{key}: propriedade obrigatória ausente")
        if schema.get("additionalProperties") is False:
            for key in set(value) - set(properties):
                errors.append(f"{path}.{key}: propriedade adicional proibida")
        for key, item in value.items():
            if key in properties:
                errors.extend(schema_errors(item, properties[key], f"{path}.{key}"))
    if isinstance(value, list) and "items" in schema:
        for index, item in enumerate(value):
            errors.extend(schema_errors(item, schema["items"], f"{path}[{index}]"))
    if isinstance(value, (int, float)) and not isinstance(value, bool) and "minimum" in schema:
        if value < schema["minimum"]:
            errors.append(f"{path}: valor menor que {schema['minimum']}")
    if isinstance(value, str) and "format" in schema:
        try:
            if schema["format"] == "uuid":
                if str(uuid.UUID(value)) != value.lower():
                    raise ValueError("UUID não canônico")
            elif schema["format"] == "date-time":
                if not DATE_TIME.fullmatch(value):
                    raise ValueError("Data deve conter hora e timezone")
                if datetime.fromisoformat(value.replace("Z", "+00:00")).utcoffset() is None:
                    raise ValueError("Timezone ausente")
            else:
                raise ValueError(f"Formato de schema não suportado: {schema['format']}")
        except ValueError as exc:
            errors.append(f"{path}: {schema['format']} inválido ({exc})")
    return errors


def measurement_errors(value: Any, schema: dict[str, Any]) -> list[str]:
    errors = schema_errors(value, schema)
    if not isinstance(value, list):
        return errors
    for index, trial in enumerate(value):
        if not isinstance(trial, dict):
            continue
        if trial.get("platform") != "ios":
            errors.append(f"$[{index}].platform: este validador espera exportação iOS")
        if trial.get("origin") == "texto digitado":
            for field in ("transcriptionMS", "textMS", "audioStartMS"):
                if trial.get(field) is not None:
                    errors.append(f"$[{index}].{field}: texto digitado exige null")
    return errors


def reject_non_json_number(value: str) -> None:
    raise ValueError(f"Número não permitido em JSON: {value}")


def reject_duplicate_keys(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"Chave JSON duplicada: {key}")
        result[key] = value
    return result


def read_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"),
                      parse_constant=reject_non_json_number,
                      object_pairs_hook=reject_duplicate_keys)


def check_static() -> list[str]:
    errors: list[str] = []
    for directory in DIRECTORIES:
        if not (ROOT / directory).is_dir():
            errors.append(f"Diretório obrigatório ausente: {directory}")
    for module in MODULES:
        if not any((ROOT / module).rglob("*.swift")):
            errors.append(f"Módulo sem fonte Swift: {module}")
    for filename in REQUIRED_FILES:
        if not (ROOT / filename).is_file():
            errors.append(f"Arquivo obrigatório ausente: {filename}")
    if not any((ROOT / "Configuration").glob("*.xcconfig")):
        errors.append("Configuration não possui .xcconfig")
    if not any((ROOT / "Tests").rglob("*Tests.swift")):
        errors.append("Nenhum arquivo *Tests.swift preparado")

    try:
        local_csv = (ROOT / "Tests/frases.csv").read_bytes()
        if local_csv != (SHARED / "Tests/frases.csv").read_bytes():
            errors.append("Tests/frases.csv difere byte a byte de shared/Tests/frases.csv")
        reader = csv.DictReader(io.StringIO(local_csv.decode("utf-8"), newline=""))
        if reader.fieldnames != ["id", "idioma", "frase", "traducao_referencia"]:
            errors.append("Cabeçalho CSV diferente do contrato")
        rows = list(reader)
        expected_ids = [f"{prefix}{i:02}" for prefix in ("PT", "EN") for i in range(1, 7)]
        if [row.get("id") for row in rows] != expected_ids:
            errors.append("CSV deve conter as 12 frases PT01–PT06 e EN01–EN06 na ordem comum")
        for row in rows:
            if None in row or any(not row.get(key) for key in ("id", "idioma", "frase", "traducao_referencia")):
                errors.append(f"Linha CSV incompleta/inválida: {row.get('id')}")
            expected_language = "pt-BR" if row.get("id", "").startswith("PT") else "en-US"
            if row.get("idioma") != expected_language:
                errors.append(f"Idioma incorreto em {row.get('id')}")
        by_id = {row["id"]: row for row in rows}
        for line in (ROOT / "gerar-reserva-mac.sh").read_text(encoding="utf-8").splitlines():
            parts = shlex.split(line)
            if parts and parts[0] == "say":
                phrase_id = Path(parts[parts.index("-o") + 1]).stem
                if phrase_id not in by_id or parts[-1] != by_id[phrase_id]["frase"]:
                    errors.append(f"Áudio de reserva diverge da frase comum: {phrase_id}")
    except (OSError, UnicodeError, csv.Error, ValueError, IndexError, KeyError) as exc:
        errors.append(f"Falha ao verificar CSV/reserva: {exc}")

    project_path = ROOT / "project.yml"
    if project_path.is_file():
        project = project_path.read_text(encoding="utf-8")
        if not re.search(r"iOS:\s*['\"]?18\.0['\"]?", project):
            errors.append("project.yml deve declarar deploymentTarget iOS 18.0")
        for token in ("Sources", "Resources", "configFiles", "NSMicrophoneUsageDescription", "NSSpeechRecognitionUsageDescription"):
            if token not in project:
                errors.append(f"project.yml não referencia {token}")
    script = ROOT / "gerar-reserva-mac.sh"
    if script.is_file():
        checked = subprocess.run(["bash", "-n", str(script)], capture_output=True, text=True, check=False)
        if checked.returncode:
            errors.append(f"bash -n falhou: {checked.stderr.strip()}")
    return errors


def self_test(schema: dict[str, Any]) -> int:
    # Objeto SINTÉTICO, sem latências ou alegação de execução física.
    trial = {
        "id": "00000000-0000-4000-8000-000000000001",
        "date": "2000-01-01T00:00:00Z", "platform": "ios",
        "device": "sintético para teste do validador", "system": "sintético",
        "direction": "pt-BR → en-US", "input": "teste", "output": "test",
        "rawInput": "teste", "edited": False, "origin": "texto digitado",
        "airplaneModeConfirmed": False, "failure": None, "notes": "SINTÉTICO",
        "transcriptionMS": None, "textMS": None, "audioStartMS": None,
        "translationMS": None, "transcriptionQuality": "não avaliada",
        "translationQuality": "não avaliada",
    }
    assert not measurement_errors([trial], schema), "Objeto sintético válido foi recusado"
    assert not measurement_errors([], schema), "Array vazio válido foi recusado"
    cases = 2
    for field in schema["items"]["required"]:
        bad = copy.deepcopy(trial)
        del bad[field]
        assert measurement_errors([bad], schema), f"Ausência não recusada: {field}"
        cases += 1
    for field, invalid in (
        ("extra", True), ("platform", "android"), ("origin", "desconhecida"),
        ("date", "2000-01-01T00:00:00"), ("date", "2000-02-31T00:00:00Z"),
        ("id", "uuid-inválido"), ("edited", 1), ("translationMS", True),
        ("translationMS", -1), ("rawInput", None), ("transcriptionQuality", "boa"),
        ("textMS", 1), ("audioStartMS", 1), ("transcriptionMS", 1),
        ("translationMS", float("inf")), ("translationMS", float("nan")),
    ):
        bad = copy.deepcopy(trial)
        bad[field] = invalid
        assert measurement_errors([bad], schema), f"Valor inválido não recusado: {field}={invalid!r}"
        cases += 1
    return cases


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--measurements", type=Path, action="append", default=[],
                        help="JSON exportado, repetível; não comprova execução física")
    parser.add_argument("--self-test", action="store_true",
                        help="exercita o validador com objetos sintéticos em memória")
    args = parser.parse_args()
    try:
        schema = read_json(SHARED / "medicoes.schema.json")
        errors = check_static()
        for path in args.measurements:
            value = read_json(path)
            found = measurement_errors(value, schema)
            errors.extend(f"{path}: {message}" for message in found)
            if not found:
                print(f"JSON conforme: {path} ({len(value)} registros; origem não certificada)")
        if args.self_test:
            print(f"Autotestes do validador: {self_test(schema)} casos sintéticos passaram")
    except (OSError, ValueError, AssertionError) as exc:
        print(f"ERRO: {exc}", file=sys.stderr)
        return 1
    if errors:
        for error in errors:
            print(f"ERRO: {error}", file=sys.stderr)
        return 1
    print("PASS: estrutura, CSV comum byte a byte, frases de reserva e bash -n")
    print("PASS: referências estáticas de projeto/configuração e testes Swift preparados")
    print("Ferramentas: " + ", ".join(f"{name}={'presente' if shutil.which(name) else 'ausente'}"
                                      for name in ("swift", "xcodebuild", "xcodegen", "say")))
    if not args.measurements:
        print("Nenhum JSON de medições fornecido; exportações Swift/aparelho ainda não validadas.")
    print("Esta verificação não compila Swift, não executa frameworks Apple e não certifica offline.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
