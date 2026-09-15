#!/usr/bin/env python3
"""Static sanity checks for the AdShield Android project.

Runs without a JDK or the Android SDK:
  * every XML resource and the manifest must be well formed
  * every R.string / R.drawable / R.xml reference in Kotlin must exist
  * every @drawable / @string / @style / @xml reference in the manifest must exist
  * braces, brackets and parentheses must balance in every Kotlin file
  * Hebrew strings.xml must cover all English keys
"""

import glob
import os
import re
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MAIN = os.path.join(ROOT, "app", "src", "main")
RES = os.path.join(MAIN, "res")
JAVA = os.path.join(MAIN, "java")

problems = []


def fail(message):
    problems.append(message)


def parse_xml(path):
    try:
        ET.parse(path)
    except Exception as exc:  # noqa: BLE001
        fail(f"XML error in {os.path.relpath(path, ROOT)}: {exc}")


def collect_names(path):
    if not os.path.exists(path):
        return set()
    tree = ET.parse(path)
    return {node.get("name") for node in tree.getroot() if node.get("name")}


def strip_kotlin_code(text):
    """Removes comments and string literals so brackets can be counted."""
    out = []
    i = 0
    n = len(text)
    while i < n:
        c = text[i]
        if c == "/" and i + 1 < n and text[i + 1] == "/":
            i = text.find("\n", i)
            if i < 0:
                break
            continue
        if c == "/" and i + 1 < n and text[i + 1] == "*":
            end = text.find("*/", i + 2)
            i = n if end < 0 else end + 2
            continue
        if text.startswith('"""', i):
            end = text.find('"""', i + 3)
            i = n if end < 0 else end + 3
            continue
        if c == '"':
            i += 1
            while i < n:
                if text[i] == "\\":
                    i += 2
                    continue
                if text[i] == '"':
                    i += 1
                    break
                i += 1
            continue
        if c == "'":
            i += 1
            while i < n:
                if text[i] == "\\":
                    i += 2
                    continue
                if text[i] == "'":
                    i += 1
                    break
                i += 1
            continue
        out.append(c)
        i += 1
    return "".join(out)


def check_balance(path, text):
    stripped = strip_kotlin_code(text)
    pairs = {")": "(", "]": "[", "}": "{"}
    stack = []
    for index, char in enumerate(stripped):
        if char in "([{":
            stack.append(char)
        elif char in ")]}":
            if not stack or stack[-1] != pairs[char]:
                fail(f"Unbalanced {char} in {os.path.relpath(path, ROOT)}")
                return
            stack.pop()
    if stack:
        fail(f"Unclosed {''.join(stack)} in {os.path.relpath(path, ROOT)}")


def main():
    xml_files = sorted(glob.glob(os.path.join(RES, "**", "*.xml"), recursive=True))
    manifest = os.path.join(MAIN, "AndroidManifest.xml")
    xml_files.append(manifest)

    for path in xml_files:
        parse_xml(path)

    drawables = {os.path.splitext(os.path.basename(p))[0]
                 for p in glob.glob(os.path.join(RES, "drawable*", "*"))}
    strings_en = collect_names(os.path.join(RES, "values", "strings.xml"))
    strings_he = collect_names(os.path.join(RES, "values-iw", "strings.xml"))
    xml_res = {os.path.splitext(os.path.basename(p))[0]
               for p in glob.glob(os.path.join(RES, "xml", "*"))}
    styles = collect_names(os.path.join(RES, "values", "themes.xml"))

    kotlin_files = sorted(glob.glob(os.path.join(JAVA, "**", "*.kt"), recursive=True))
    if not kotlin_files:
        fail("No Kotlin sources found")

    for path in kotlin_files:
        text = open(path, encoding="utf-8").read()
        check_balance(path, text)
        rel = os.path.relpath(path, ROOT)
        for kind, name in re.findall(r"\bR\.(string|drawable|xml|style)\.([A-Za-z0-9_]+)", text):
            if kind == "string" and name not in strings_en:
                fail(f"{rel}: missing string resource '{name}'")
            if kind == "drawable" and name not in drawables:
                fail(f"{rel}: missing drawable '{name}'")
            if kind == "xml" and name not in xml_res:
                fail(f"{rel}: missing xml resource '{name}'")
            if kind == "style" and name not in styles:
                fail(f"{rel}: missing style '{name}'")

    manifest_text = open(manifest, encoding="utf-8").read()
    for kind, name in re.findall(r"@(drawable|string|style|xml|mipmap)/([A-Za-z0-9_.]+)", manifest_text):
        if kind == "drawable" and name not in drawables:
            fail(f"AndroidManifest: missing drawable '{name}'")
        if kind == "string" and name not in strings_en:
            fail(f"AndroidManifest: missing string '{name}'")
        if kind == "xml" and name not in xml_res:
            fail(f"AndroidManifest: missing xml resource '{name}'")
        if kind == "style" and name not in styles:
            fail(f"AndroidManifest: missing style '{name}'")

    missing_he = sorted(strings_en - strings_he)
    extra_he = sorted(strings_he - strings_en)

    print(f"Kotlin files checked : {len(kotlin_files)}")
    print(f"XML files checked    : {len(xml_files)}")
    print(f"English strings      : {len(strings_en)}")
    print(f"Hebrew strings       : {len(strings_he)}")
    print(f"Drawables            : {len(drawables)}")
    print(f"Bundled blocklist    : "
          f"{sum(1 for line in open(os.path.join(MAIN, 'assets', 'default_blocklist.txt'), encoding='utf-8') if line.strip() and not line.startswith('#'))} domains")
    if missing_he:
        print(f"Hebrew missing ({len(missing_he)}): {', '.join(missing_he)}")
    if extra_he:
        print(f"Hebrew only ({len(extra_he)}): {', '.join(extra_he)}")

    if problems:
        print("\nPROBLEMS:")
        for item in problems:
            print(f"  - {item}")
        sys.exit(1)
    print("\nAll static checks passed.")


if __name__ == "__main__":
    main()
