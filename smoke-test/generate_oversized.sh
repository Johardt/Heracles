#!/usr/bin/env bash
set -euo pipefail

output="${1:-smoke-test/fixtures/invalid/smoke_oversized.json}"
mkdir -p "$(dirname "$output")"

python3 - "$output" <<'PY'
import json
import sys

output = sys.argv[1]
document = {
    "display": {
        "icon": {"type": "heracles:item", "item": "minecraft:paper"},
        "title": "Oversized fixture",
    },
    "tasks": {"check": {"type": "heracles:check"}},
    "rewards": {},
    "custom": {"padding": "x" * (1024 * 1024)},
}
with open(output, "w", encoding="utf-8") as handle:
    json.dump(document, handle)
    handle.write("\n")
print(f"wrote {output}")
PY
