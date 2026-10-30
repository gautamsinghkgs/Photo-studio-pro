# Agent instructions

**Read `README.md` first** — it is the single source of truth for project state, rules,
architecture and next steps. Then `HANDOFF.md` (first-session procedure), then
`docs/DECISIONS.md` (the rationale behind every rule).

Non-negotiables, in priority order:

1. **No cloud AI APIs.** On-device ML Kit only. (`docs/DECISIONS.md` C1)
2. **No backend the owner maintains.** (`docs/DECISIONS.md` C2)
3. **Never write "100% offline".** Use "works offline after one-time setup". (`docs/DECISIONS.md` §2.2)
4. **`AdPolicy` alone decides ad display; `ExportFlow` alone handles exports.** Do not add a second path.
5. **Banners and app-open ads stay unconditionally disabled.** A test enforces this.
6. **All versions live in `gradle/libs.versions.toml`.** Never hardcode one elsewhere.
7. **This code has never been compiled by an Android toolchain.** Compile errors are the expected state. Work the `compile` CI job first and fix only the FIRST error.
8. **Run both linters before every commit** — `python3 tools/check_resources.py` and `python3 tools/check_wiring.py`. They need no Android SDK and take under a second. The wiring audit found four bugs a compiler cannot see.
9. **Treat `kotlinc`'s "parameter is never used" as an error.** A parameter a function ignores makes the signature lie (bugs #5 and #12).
10. **Reply to the owner in Hinglish.** He is solo and non-programmer.
11. **When something is unverified, say so.** Two earlier confident claims turned out to be wrong; both are documented.
