# Agent instructions

**Read `README.md` first** — it is the single source of truth for project state, rules,
architecture and next steps. Then `HANDOFF.md` (first-session procedure), then
`docs/DECISIONS.md` (the rationale behind every rule, plus the 29-bug ledger in §6).

Non-negotiables, in priority order:

1. **No cloud AI APIs.** On-device ML Kit only. (`docs/DECISIONS.md` C1)
2. **No backend the owner maintains.** (`docs/DECISIONS.md` C2)
3. **Never write "100% offline".** Use "works offline after one-time setup". (`docs/DECISIONS.md` §2.2)
4. **`AdPolicy` alone decides ad display; `ExportFlow` alone handles exports.** Do not add a second path.
5. **Banners and app-open ads stay unconditionally disabled.** A test enforces this.
6. **All versions live in `gradle/libs.versions.toml`.** The one unavoidable exception is the
   KGP coordinate inside the root `buildscript { }` block — version-catalog accessors do not
   exist on the build classpath, so it has to be literal. `check_wiring.py` reports it as a
   *note*: if you bump `kotlin` or `ksp` in the catalog, bump that line by hand.
7. **The toolchain is frozen because it is verified: AGP 9.1.1 · Gradle 9.3.1 · JDK 17 ·
   Kotlin 2.3.21 · KSP 2.3.12 · compileSdk 37 · targetSdk 36 · Build-Tools 36.0.0.**
   `:app:compileDebugKotlin` is green and **117 unit tests pass** (2026-10-30). Do not
   downgrade a library to satisfy an AAR-metadata check (bug #16), do not re-apply
   `org.jetbrains.kotlin.android` (AGP 9 has *built-in* Kotlin, bug #17), and do not set
   `android.builtInKotlin=false` — that hatch is removed in AGP 10.
8. **`assembleDebug` / `bundleRelease` cannot run in a small sandbox.** D8 is OOM-killed at
   `dexBuilderDebug` and Gradle reports only *"build daemon disappeared unexpectedly"* — that
   is a RAM limit, **not** a project error. Verify with
   `:app:compileDebugKotlin :app:testDebugUnitTest` (`--max-workers=1 -Xmx1000m`, README §7.2)
   and let the 7 GB CI runner do dexing, R8 and packaging.
9. **Run both linters before every commit** — `python3 tools/check_resources.py` and
   `python3 tools/check_wiring.py`. No Android SDK, under a second, both negative-control
   verified. The wiring audit found four bugs a compiler cannot see.
10. **Treat "parameter is never used" as an error.** A parameter a function ignores makes the
    signature lie — bugs #5, #12 and #24. Call multi-parameter functions with **named** arguments.
11. **A stub harness proves logic; only Gradle proves compilation.** Bug #28: the kotlinc test
    harness reported "117/117 green" for weeks while `AdPolicyTest.kt` did not compile at all.
    Never end a session on a harness result.
12. **Verify an API against the real artifact, not memory.** `javap` the AAR you downloaded
    (bugs #18, #19, #25). When *every* type from one library is unresolved at once, the
    coordinate is wrong, not the code.
13. **Reply to the owner in Hinglish.** He is solo and non-programmer.
14. **When something is unverified, say so.** Two earlier confident claims turned out to be
    wrong; both are documented in `README.md` §2.
