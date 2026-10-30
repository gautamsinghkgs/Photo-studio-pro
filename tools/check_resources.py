#!/usr/bin/env python3
"""
Resource sanity checks that need no Android SDK.

WHY THIS EXISTS
---------------
`aapt2` fails the build on a duplicate resource name, and that error surfaces deep
inside a 10-minute Gradle run. A duplicate `<string name="cancel">` was shipped in
this repo once and would have broken the very first build; it was found by a script
like this one, not by a test.

These checks run in under two seconds with only the Python standard library, so CI
can fail fast and name the exact file and line before Gradle ever starts.

Exit code 0 = clean, 1 = problems found.
"""
import collections
import glob
import os
import re
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
SRC = os.path.join(ROOT, "app", "src", "main", "java")
problems = []


def fail(msg):
    problems.append(msg)


def read(path):
    with open(path, encoding="utf-8") as fh:
        return fh.read()


# ---------------------------------------------------------------- 1. XML validity
res_xml = sorted(glob.glob(os.path.join(RES, "**", "*.xml"), recursive=True))
for path in res_xml:
    try:
        ET.parse(path)
    except ET.ParseError as exc:
        fail(f"{os.path.relpath(path, ROOT)}: XML parse error - {exc}")

# ------------------------------------------------- 2. duplicate resource names
KINDS = {
    "string": r'<string name="([^"]+)"',
    "color": r'<color name="([^"]+)"',
    "dimen": r'<dimen name="([^"]+)"',
    "bool": r'<bool name="([^"]+)"',
    "integer": r'<integer name="([^"]+)"',
    "style": r'<style name="([^"]+)"',
    "plurals": r'<plurals name="([^"]+)"',
    "string-array": r'<string-array name="([^"]+)"',
}
for path in sorted(glob.glob(os.path.join(RES, "values*", "*.xml"))):
    text = read(path)
    lines = text.split("\n")
    for kind, pattern in KINDS.items():
        names = re.findall(pattern, text)
        for name, count in collections.Counter(names).items():
            if count > 1:
                hits = [str(i) for i, line in enumerate(lines, 1)
                        if f'name="{name}"' in line]
                fail(f"{os.path.relpath(path, ROOT)}: duplicate <{kind} name="
                     f'"{name}"> on line(s) {", ".join(hits)} - aapt2 fails the build')

# duplicate file-based resources within the SAME qualifier bucket
for folder in ("drawable", "layout", "xml", "mipmap", "raw", "anim"):
    seen = {}
    for path in sorted(glob.glob(os.path.join(RES, folder + "*", "*"))):
        stem = os.path.splitext(os.path.basename(path))[0]
        key = (os.path.dirname(path), stem)
        if key in seen:
            fail(f"duplicate {folder} resource '{stem}' in {os.path.dirname(path)}")
        seen[key] = path

# --------------------------------------------- 3. locale parity (EN vs HI)
def string_names(path):
    return set(re.findall(r'<string name="([^"]+)"', read(path))) \
        if os.path.exists(path) else set()

en_path = os.path.join(RES, "values", "strings.xml")
hi_path = os.path.join(RES, "values-hi", "strings.xml")
en, hi = string_names(en_path), string_names(hi_path)
if en and hi:
    if en - hi:
        fail(f"values-hi/strings.xml is missing {len(en - hi)} string(s): {sorted(en - hi)}")
    if hi - en:
        fail(f"values-hi/strings.xml has {len(hi - en)} string(s) absent from EN: {sorted(hi - en)}")

# ------------------------------- 4. format-argument types must match across locales
# %1$d in EN but %1$s in HI crashes at runtime with IllegalFormatConversionError,
# and nothing in the build catches it.
def arg_types(path):
    if not os.path.exists(path):
        return {}
    return {name: re.findall(r"%\d+\$([a-zA-Z])", body)
            for name, body in re.findall(r'<string name="([^"]+)"[^>]*>(.*?)</string>',
                                         read(path), re.S)}

en_args, hi_args = arg_types(en_path), arg_types(hi_path)
for name in sorted(set(en_args) & set(hi_args)):
    if en_args[name] != hi_args[name]:
        fail(f"string '{name}': format args differ - EN {en_args[name]} vs HI "
             f"{hi_args[name]} (runtime IllegalFormatConversionError)")

# ------------------------------------------- 5. every R.* reference resolves
colors_path = os.path.join(RES, "values", "colors.xml")
have = {
    "string": en,
    "color": set(re.findall(r'<color name="([^"]+)"', read(colors_path)))
             if os.path.exists(colors_path) else set(),
}
for kind, folder in (("drawable", "drawable*"), ("xml", "xml"),
                     ("mipmap", "mipmap*"), ("raw", "raw"), ("layout", "layout*")):
    have[kind] = {os.path.splitext(os.path.basename(p))[0]
                  for p in glob.glob(os.path.join(RES, folder, "*"))}

used = collections.defaultdict(set)
kt_files = sorted(glob.glob(os.path.join(SRC, "**", "*.kt"), recursive=True))
for path in kt_files:
    for kind, name in re.findall(r"R\.(string|color|drawable|xml|mipmap|raw|layout)\.(\w+)",
                                 read(path)):
        used[kind].add((name, os.path.relpath(path, ROOT)))
for path in res_xml:
    for kind, name in re.findall(r"@(string|color|drawable|xml|mipmap|raw|layout)/(\w+)",
                                 read(path)):
        used[kind].add((name, os.path.relpath(path, ROOT)))

for kind, pairs in sorted(used.items()):
    known = have.get(kind) or set()
    if not known:
        continue
    for name, where in sorted(pairs):
        if name not in known:
            fail(f"{where}: R.{kind}.{name} does not exist in res/")

# ------------------------------------------- 6. banned strings (Play policy)
# docs/DECISIONS.md 2.2 and 2.5. These get apps rejected and swept retroactively,
# so they earn a hard failure rather than a review note.
BANNED = [
    r"100%\s*offline",
    r"\bAI\s+Enhance\b", r"\bAI\s+Restore\b", r"\bAI\s+Generator\b",
    r"\bAI\s+Avatar\b", r"\bAI\s+Colorize\b", r"\bAI\s+Retouch\b",
    r"\bAI\s+Beauty\b", r"\bAI\s+Photo\s+Studio\b",
]
scan = sorted(glob.glob(os.path.join(RES, "values*", "strings.xml")) + kt_files +
              glob.glob(os.path.join(ROOT, "app", "src", "main", "assets", "*.json")))
def masked_lines(path):
    """
    Return the file's lines with comment bodies blanked out, preserving line
    numbers so a reported problem still points at the right line.

    A line-prefix check is NOT enough: both strings.xml files carry multi-line
    XML comment blocks whose continuation lines do not start with `<!--`, and a
    KDoc block whose continuation lines start with `*` only sometimes. Tracking
    the comment state across lines is what makes this accurate. The naming-rule
    header at the top of strings.xml deliberately lists every banned phrase, so
    a naive scan reports nine false positives and the real check gets ignored.
    """
    out, in_xml, in_block = [], False, False
    for line in read(path).split("\n"):
        kept, i = [], 0
        while i < len(line):
            if not in_xml and not in_block and line.startswith("<!--", i):
                in_xml = True; i += 4; continue
            if in_xml:
                if line.startswith("-->", i):
                    in_xml = False; i += 3
                else:
                    i += 1
                continue
            if not in_xml and not in_block and line.startswith("/*", i):
                in_block = True; i += 2; continue
            if in_block:
                if line.startswith("*/", i):
                    in_block = False; i += 2
                else:
                    i += 1
                continue
            # A `//` comment runs to end of line. Skip it, but not inside a
            # string literal such as "https://".
            if line.startswith("//", i) and not (i and line[i - 1] == ":"):
                break
            kept.append(line[i]); i += 1
        out.append("".join(kept))
    return out


for path in scan:
    for i, line in enumerate(masked_lines(path), 1):
        # Lines that state the rule ("NEVER use...", a ❌ marker) are documentation
        # even outside a comment block.
        if "NEVER" in line or "\u274c" in line:
            continue
        for pat in BANNED:
            if re.search(pat, line, re.I):
                fail(f"{os.path.relpath(path, ROOT)}:{i}: banned string {pat!r} "
                     f"- see docs/DECISIONS.md 2.2/2.5")

# --------------------------------------------------------------- report
print(f"Scanned {len(kt_files)} Kotlin files and {len(res_xml)} resource XML files.")
if problems:
    print(f"\n\u274c {len(problems)} problem(s):\n")
    for p in problems:
        print("  * " + p)
    sys.exit(1)
print("\n\u2705 Resources clean: no duplicates, EN/HI in sync, all references resolve, "
      "no banned strings.")
sys.exit(0)
