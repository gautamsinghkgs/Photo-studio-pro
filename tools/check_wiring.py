#!/usr/bin/env python3
"""
check_wiring.py — cross-file wiring audit for Photo Studio Pro.

The resource linter (check_resources.py) catches things INSIDE a file. This catches
things BETWEEN files: a flag that is read but never defined, a route that is declared
but never routed, a state setter nobody calls, an architectural rule that got bypassed.

Why this exists: two of the eight bugs already found in this project were wiring bugs
that compiled perfectly, because each piece was individually correct and only the
CONNECTION was missing —

  #6  Prefs.setOnboardingDone() had ZERO callers, so AdPolicy always returned
      `never_during_onboarding` and NO AD COULD EVER SHOW on any device. It would
      have masqueraded as "AdMob isn't filling".
  #7  BatchScreen passed `treeUri = null`, silently exporting into cacheDir.

Neither is visible by reading one file. Both are visible by counting references.

Stdlib only. No Android SDK. Runs in well under a second.
Exit 0 = clean (warnings allowed). Exit 1 = at least one ERROR.

Usage:  python3 tools/check_wiring.py
"""

import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MAIN = os.path.join(ROOT, "app", "src", "main", "java", "com", "photostudio", "pro")
FEATURE = os.path.join(MAIN, "feature")

errors = []
warnings = []
notes = []

# ---------------------------------------------------------------------------
# PARKED — deliberately unwired, with a reason.
#
# A lint that cannot distinguish "dead by accident" from "parked on purpose"
# gets ignored, and then it catches nothing. So parking is explicit, carries a
# reason, and is reported every run. If an entry here GAINS a caller the audit
# says so, because the entry has become obsolete and should be deleted.
#
# Anything NOT in this list that has zero callers is treated as bug #6 — a
# silent mechanism that looks alive.
# ---------------------------------------------------------------------------
PARKED = {
    "RemoteFlags.ssvStrictMode": (
        "AdMob Server-Side Verification is deliberately deferred to Phase 2 "
        "(docs/DECISIONS.md section 2.3): it is optional, the SDK has no retry "
        "logic, and callbacks never fire for test ads so it is untestable during "
        "closed testing. The flag is pre-declared for when Phase 2 lands. Build it "
        "only if weekly rewarded impressions / Firebase reward_granted exceeds 3.0."
    ),
}
obsolete = []


def rel(p):
    return os.path.relpath(p, ROOT)


def kt_files(base):
    out = []
    for dirpath, _, files in os.walk(base):
        for f in files:
            if f.endswith(".kt"):
                out.append(os.path.join(dirpath, f))
    return sorted(out)


def masked(text):
    """Blank out comments while preserving line numbers and column offsets.

    Lesson learned the hard way on check_resources.py: a line-prefix test like
    `startswith("//")` fails on the CONTINUATION lines of a block comment, which
    produced 9 false positives. Comments must be masked with a real state machine,
    or documentation that merely MENTIONS an API gets flagged as calling it.
    """
    out = []
    i, n = 0, len(text)
    in_block = in_line = in_str = in_char = False
    while i < n:
        c = text[i]
        nxt = text[i + 1] if i + 1 < n else ""
        if in_line:
            if c == "\n":
                in_line = False
                out.append(c)
            else:
                out.append(" ")
            i += 1
            continue
        if in_block:
            if c == "*" and nxt == "/":
                in_block = False
                out.append("  ")
                i += 2
                continue
            out.append("\n" if c == "\n" else " ")
            i += 1
            continue
        if in_str:
            if c == "\\":
                out.append("  ")
                i += 2
                continue
            if c == '"':
                in_str = False
            out.append(c)
            i += 1
            continue
        if in_char:
            if c == "\\":
                out.append("  ")
                i += 2
                continue
            if c == "'":
                in_char = False
            out.append(c)
            i += 1
            continue
        if c == "/" and nxt == "/":
            in_line = True
            out.append("  ")
            i += 2
            continue
        if c == "/" and nxt == "*":
            in_block = True
            out.append("  ")
            i += 2
            continue
        if c == '"':
            in_str = True
            out.append(c)
            i += 1
            continue
        if c == "'":
            in_char = True
            out.append(c)
            i += 1
            continue
        out.append(c)
        i += 1
    return "".join(out)


# ---------------------------------------------------------------- load everything
SRC = {}
for p in kt_files(os.path.join(ROOT, "app", "src")):
    with open(p, encoding="utf-8") as fh:
        SRC[p] = masked(fh.read())

ALL = "\n".join(SRC.values())


def files_containing(pattern):
    rx = re.compile(pattern)
    return [p for p, t in SRC.items() if rx.search(t)]


def occurrences(pattern, exclude=()):
    rx = re.compile(pattern)
    hits = []
    for p, t in SRC.items():
        if any(x in p for x in exclude):
            continue
        for m in rx.finditer(t):
            line = t.count("\n", 0, m.start()) + 1
            hits.append((p, line))
    return hits


# =============================================== 1. Remote Config flag parity
# HomeScreen filters the 8 tools through ServiceLocator.flags.isToolEnabled(id).
# config.getBoolean() returns FALSE for a key that was never set, so a tool id
# missing from the RemoteFlags defaults map makes that tool VANISH from the home
# screen with no error anywhere. Silent, total, and invisible in a build.
home = os.path.join(FEATURE, "home", "HomeScreen.kt")
flags = os.path.join(MAIN, "monetization", "config", "RemoteFlags.kt")
if home in SRC and flags in SRC:
    listed = re.findall(r'Tool\(\s*"([a-z0-9_]+)"', SRC[home])
    prefixed = set(re.findall(r'TOOL_ENABLED_PREFIX\s*\+\s*"([a-z0-9_]+)"', SRC[flags]))
    for t in listed:
        if t not in prefixed:
            errors.append(
                "tool id '%s' is listed in HomeScreen but has no tool_enabled_%s entry "
                "in the RemoteFlags defaults map -> getBoolean() returns false -> "
                "the tool silently disappears from the home screen" % (t, t)
            )
    for t in sorted(prefixed - set(listed)):
        notes.append("RemoteFlags defines tool_enabled_%s but HomeScreen lists no such tool" % t)
    if listed and not errors:
        notes.append("HomeScreen/RemoteFlags tool parity OK (%d tools)" % len(listed))
else:
    warnings.append("could not locate HomeScreen.kt or RemoteFlags.kt — check 1 skipped")

# =============================================== 2. Navigation route integrity
nav = os.path.join(MAIN, "Nav.kt")
if nav in SRC:
    declared = set(re.findall(r"const val ([A-Z][A-Z0-9_]*)\s*=", SRC[nav]))
    composables = set(re.findall(r"composable\(\s*Routes\.([A-Z][A-Z0-9_]*)", SRC[nav]))
    used = set()
    for p, t in SRC.items():
        if p == nav:
            continue
        used |= set(re.findall(r"Routes\.([A-Z][A-Z0-9_]*)", t))

    for r in sorted(used - declared):
        errors.append("Routes.%s is referenced but never declared in Nav.kt" % r)
    for r in sorted(declared - composables):
        warnings.append(
            "Routes.%s is declared but has no composable() in Nav.kt — unreachable "
            "destination (deliberate for parked milestones; otherwise a dead button)" % r
        )
    for r in sorted(declared - used - composables):
        notes.append("Routes.%s is neither navigated to nor routed" % r)
    if not (used - declared):
        notes.append("navigation routes OK (%d declared, %d referenced elsewhere)"
                     % (len(declared), len(used)))
else:
    warnings.append("could not locate Nav.kt — check 2 skipped")

# =============================================== 3. Prefs setters with zero callers
# The bug #6 class. Every state-mutating function in Prefs.kt must have at least one
# caller OUTSIDE Prefs.kt, or the state it writes is never written.
prefs_path = os.path.join(MAIN, "core", "data", "local", "Prefs.kt")
if prefs_path in SRC:
    mutators = re.findall(r"\bsuspend fun (\w+)|\bfun (\w+)\s*\(", SRC[prefs_path])
    names = sorted({a or b for a, b in mutators})
    MUTATING = re.compile(r"^(set|note|mark|record|increment|clear|reset|save|log|bump)")
    for name in names:
        if not MUTATING.match(name):
            continue
        callers = occurrences(r"\.\s*%s\s*\(" % re.escape(name), exclude=("Prefs.kt",))
        decls = len(re.findall(r"\bfun %s\s*\(" % re.escape(name), SRC[prefs_path]))
        if not callers:
            key = "Prefs.%s" % name
            if key in PARKED:
                notes.append("PARKED %s() has zero callers — %s" % (name, PARKED[key]))
            else:
                errors.append(
                    "Prefs.%s() has ZERO callers outside Prefs.kt — whatever it writes "
                    "is never written. This is bug #6: setOnboardingDone() with no "
                    "callers silently disabled every ad in the app. If it is parked on "
                    "purpose, add it to PARKED in this file with a reason."
                    % name
                )
        elif key_parked := ("Prefs.%s" % name) in PARKED:
            obsolete.append(
                "PARKED entry 'Prefs.%s' now HAS %d caller(s) — it is no longer dead. "
                "Delete the entry from PARKED in tools/check_wiring.py." % (name, len(callers))
            )
    notes.append("Prefs mutator audit done (%d mutating functions checked)"
                 % len([n for n in names if MUTATING.match(n)]))
else:
    warnings.append("could not locate Prefs.kt — check 3 skipped")

# =============================================== 4. the double-ad trap
# ExportFlow.run() ALREADY calls adCoordinator.afterExportCompleted() as its final
# step. A feature that exports through ExportFlow must NOT also call it from its
# Screen, or two ad decisions fire per export.
exportflow_users = set()
for p in files_containing(r"\bExportFlow\b"):
    if os.sep + "feature" + os.sep in p and "ExportFlow.kt" not in p:
        exportflow_users.add(os.path.basename(os.path.dirname(p)))

for tool in sorted(exportflow_users):
    d = os.path.join(FEATURE, tool)
    hits = [
        (rel(p), ln)
        for p, ln in occurrences(r"afterExportCompleted\s*\(")
        if p.startswith(d)
    ]
    if hits:
        errors.append(
            "feature/%s exports through ExportFlow (which already calls "
            "afterExportCompleted as its final step) but ALSO calls it directly at %s "
            "-> two ad decisions per export. Delete the Screen-level call."
            % (tool, ", ".join("%s:%d" % h for h in hits))
        )

# The inverse: tools that hand-roll their export MUST call it themselves, or no ad
# is ever considered after their export.
for tool in sorted(
    os.path.basename(d) for d in os.listdir(FEATURE) if os.path.isdir(os.path.join(FEATURE, d))
):
    if tool in exportflow_users or tool in ("home", "settings", "paywall", "onboarding", "later"):
        continue
    d = os.path.join(FEATURE, tool)
    exports = occurrences(r"ExportOps\.(encode|toShareUri|saveToTree)")
    in_tool = [(rel(p), ln) for p, ln in exports if p.startswith(d)]
    ad_call = [h for h in occurrences(r"afterExportCompleted\s*\(") if h[0].startswith(d)]
    if in_tool and not ad_call:
        errors.append(
            "feature/%s hand-rolls an export (%s) but never calls "
            "afterExportCompleted and does not use ExportFlow -> no ad decision is "
            "ever made after its export. Either move it onto ExportFlow or add the call."
            % (tool, ", ".join("%s:%d" % h for h in in_tool[:2]))
        )
    elif in_tool and ad_call:
        notes.append(
            "feature/%s hand-rolls its export and calls afterExportCompleted from the "
            "Screen — works, but see README §6 P1: moving it onto ExportFlow REQUIRES "
            "deleting that Screen-level call in the same commit" % tool
        )

# =============================================== 4b. Remote Config knobs nobody reads
# The owner has to type every one of these into the Firebase console by hand.
# A knob that no code reads is wasted work AND a false expectation: he changes a
# number, nothing happens, and he concludes Remote Config is broken.
if flags in SRC:
    props = sorted(set(re.findall(r"val (\w+)\s*:\s*\w+\s+get\(\)", SRC[flags])))
    for prop in props:
        readers = [
            p for p, t in SRC.items()
            if p != flags and re.search(r"\bflags\.%s\b" % re.escape(prop), t)
        ]
        if not readers:
            key = "RemoteFlags.%s" % prop
            if key in PARKED:
                notes.append("PARKED RemoteFlags.%s is never read — %s" % (prop, PARKED[key]))
            else:
                errors.append(
                    "RemoteFlags.%s is never read by any code. The owner would "
                    "configure this key in Firebase for NO effect. Either read it or "
                    "remove it (and remove the key from RemoteFlagKeys)." % prop
                )
        elif ("RemoteFlags.%s" % prop) in PARKED:
            obsolete.append(
                "PARKED entry 'RemoteFlags.%s' now HAS %d reader(s). Delete the entry "
                "from PARKED in tools/check_wiring.py." % (prop, len(readers))
            )
    if props:
        notes.append("Remote Config knobs audited (%d properties)" % len(props))

# =============================================== 5. architectural single paths
for p, ln in occurrences(r"AdManager\b[\s\S]{0,40}?showInterstitial|showInterstitial\s*\("):
    if "AdCoordinator.kt" in p or "AdManager.kt" in p:
        continue
    errors.append(
        "%s:%d calls showInterstitial directly. AdCoordinator is the ONLY permitted "
        "caller — bypassing it means AdPolicy never gets consulted, which is how a "
        "paying user ends up seeing a forced ad (the #2 revenue killer)." % (rel(p), ln)
    )

BILLING = os.sep + os.path.join("monetization", "billing") + os.sep
for p, ln in occurrences(r"Purchases\.sharedInstance"):
    if BILLING in p:
        continue
    errors.append(
        "%s:%d touches Purchases.sharedInstance outside monetization/billing. "
        "EntitlementManager and PurchaseFlow own the RevenueCat surface; scattering it "
        "into screens is how the API-shape migration becomes a 20-file change instead "
        "of a 2-file one." % (rel(p), ln)
    )

# =============================================== 6. hardcoded prices in UI
# Prices must come from storeProduct.price.formatted. Play localises them; a
# hardcoded string shows a US price to an Indian user and is a Play policy problem.
PRICE = re.compile(r'"[^"]*(?:\$\d|₹\d|USD\s?\d|INR\s?\d)[^"]*"')
for p, t in SRC.items():
    if os.sep + "feature" + os.sep not in p and os.sep + "monetization" + os.sep not in p:
        continue
    for m in PRICE.finditer(t):
        ln = t.count("\n", 0, m.start()) + 1
        errors.append(
            "%s:%d hardcodes a price (%s). Prices must come from "
            "storeProduct.price.formatted so Play can localise them."
            % (rel(p), ln, m.group(0)[:40])
        )

# =============================================== 7. literal null at a call site
# The bug #7 class: a null-defaulted parameter explicitly passed null.
for p, ln in occurrences(r"treeUri\s*=\s*null"):
    warnings.append(
        "%s:%d passes treeUri = null explicitly. Export treats null as 'share sheet "
        "only'; bug #7 was exactly this, silently exporting into cacheDir. Confirm "
        "this is intentional." % (rel(p), ln)
    )

# =============================================== 8. versions outside the catalog
CATALOG = os.path.join(ROOT, "gradle", "libs.versions.toml")
COORD = re.compile(r'["\']([\w.\-]+:[\w.\-]+:\d[\w.\-]*)["\']')


def buildscript_spans(text):
    """Character spans of every `buildscript { ... }` block in a Gradle script.

    Type-safe version-catalog accessors (libs.versions.kotlin) do NOT exist on the
    build classpath — Gradle resolves buildscript {} before the catalog accessors
    are generated — so a plugin coordinate written there has to be literal. That is
    a documented exception to the "every version lives in the catalog" rule, not
    drift, and it is reported as a note instead of a warning.
    """
    spans = []
    for m in re.finditer(r"^\s*buildscript\s*\{", text, re.M):
        depth, i = 0, m.end() - 1
        while i < len(text):
            if text[i] == "{":
                depth += 1
            elif text[i] == "}":
                depth -= 1
                if depth == 0:
                    spans.append((m.start(), i))
                    break
            i += 1
    return spans


for gf in ("build.gradle.kts", os.path.join("app", "build.gradle.kts"), "settings.gradle.kts"):
    p = os.path.join(ROOT, gf)
    if not os.path.exists(p):
        continue
    with open(p, encoding="utf-8") as fh:
        txt = masked(fh.read())
    spans = buildscript_spans(txt)
    for m in COORD.finditer(txt):
        ln = txt.count("\n", 0, m.start()) + 1
        if any(a <= m.start() <= b for a, b in spans):
            notes.append(
                "%s:%d spells out %s inside buildscript { } — correct and unavoidable. "
                "Version-catalog accessors are unavailable on the build classpath, so the "
                "AGP 9 KGP/KSP override cannot use libs.versions.*. If you bump kotlin/ksp "
                "in the catalog, bump this line by hand." % (gf, ln, m.group(1))
            )
            continue
        warnings.append(
            "%s:%d hardcodes the coordinate %s. Every version belongs in "
            "gradle/libs.versions.toml so drift stays visible." % (gf, ln, m.group(1))
        )

# =============================================== report
print("=" * 74)
print("WIRING AUDIT — Photo Studio Pro")
print("=" * 74)
print("scanned %d Kotlin files under app/src" % len(SRC))
print()

for n in notes:
    print("  · %s" % n)
print()
for o in obsolete:
    warnings.append(o)
for w in warnings:
    print("WARN  %s" % w)
for e in errors:
    print("ERROR %s" % e)

print()
print("-" * 74)
print("%d error(s), %d warning(s), %d note(s)" % (len(errors), len(warnings), len(notes)))
if errors:
    print("RESULT: FAIL")
    sys.exit(1)
print("RESULT: CLEAN")
sys.exit(0)
