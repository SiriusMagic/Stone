#!/usr/bin/env python3
"""Regenerates android-native/MAP.md: every Kotlin file with its size and a one-line summary
(the first KDoc sentence of the file), grouped by folder. Run after adding or moving files:
    python3 android-native/tools/update_map.py
"""
import os, re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOURCES = [("app", "app/src/main/java/com/zirakusa/stone/nativeapp"), ("core", "core/src/main/kotlin/com/zirakusa/stone/core")]

def summary(text: str) -> str:
    match = re.search(r"/\*\*\s*(.*?)\*/", text, re.S)
    if not match:
        return ""
    doc = " ".join(l.strip().lstrip("*").strip() for l in match.group(1).splitlines()).strip()
    first = re.split(r"(?<=[.:;])\s", doc)[0]
    return first[:150]

lines = ["# Mapa de código de Stone (Android)", "",
         "Generado por `tools/update_map.py`. Lee este mapa, abre solo el archivo que necesitas y,",
         "si es grande, busca con grep la función antes de leerlo entero.", ""]
for label, base in SOURCES:
    base_path = os.path.join(ROOT, base)
    for folder, _, files in sorted(os.walk(base_path)):
        kotlin = sorted(f for f in files if f.endswith(".kt"))
        if not kotlin:
            continue
        rel = os.path.relpath(folder, base_path)
        lines.append(f"## {label}/{'' if rel == '.' else rel}")
        for name in kotlin:
            text = open(os.path.join(folder, name), encoding="utf-8").read()
            size = text.count("\n") + 1
            lines.append(f"- `{name}` ({size}) {summary(text)}".rstrip())
        lines.append("")
open(os.path.join(ROOT, "MAP.md"), "w", encoding="utf-8").write("\n".join(lines))
print("MAP.md:", sum(1 for l in lines if l.startswith("- ")), "files")
