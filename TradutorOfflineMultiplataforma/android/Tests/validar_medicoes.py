#!/usr/bin/env python3
"""Validate an exported collection against the unchanged common contract."""
import argparse
import json
from pathlib import Path
import sys


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("arquivo", type=Path, help="JSON exportado pelo aplicativo")
    args = parser.parse_args()
    try:
        import jsonschema
    except ImportError:
        sys.exit("Dependência ausente: instale jsonschema em um ambiente Python local antes de validar.")
    schema_path = Path(__file__).resolve().parents[2] / "shared" / "medicoes.schema.json"
    schema = json.loads(schema_path.read_text(encoding="utf-8"))
    data = json.loads(args.arquivo.read_text(encoding="utf-8"))
    validator = jsonschema.Draft202012Validator(schema, format_checker=jsonschema.FormatChecker())
    errors = sorted(validator.iter_errors(data), key=lambda error: str(error.absolute_path))
    if errors:
        for error in errors:
            print(f"{list(error.absolute_path)}: {error.message}", file=sys.stderr)
        return 1
    print(f"Contrato v1 válido: {len(data)} registros. Isso não comprova testes físicos/offline.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
