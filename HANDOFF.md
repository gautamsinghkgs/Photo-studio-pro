# HANDOFF — your first session on this project

This file is the **procedure**. The **knowledge** — what the project is, its current
state, the rules, the architecture and the remaining work — lives in **`README.md`**.
Read that first; this page only tells you what to *do*, in what order, and points at the
section that explains it.

Nothing here duplicates `README.md`. If the two ever disagree, **`README.md` wins** —
tell the owner and fix this file.

---

## Minute 0–2 — before you touch anything

Read, in this order:

| Read | Why | Time |
|---|---|---|
| `README.md` **§0** | The current ground truth: **it compiles and the 117 tests pass under a real Android toolchain** (2026-10-30) — and precisely which two stages still cannot be verified locally, and why | 2 min |
| `README.md` **§4.3** | The verification pyramid. Know exactly what "green" means here before you claim anything | 2 min |
| `README.md` **§2** | The 7 constraints (C1–C7) and the **five pieces of earlier advice that were wrong** — do not reintroduce them | 3 min |
| `README.md` **§3.1** | The "never do these" table. Most are enforced by a linter or a test, but not all | 2 min |

Then `AGENTS.md` (9 non-negotiables) and, when you need the reasoning behind a rule,
`docs/DECISIONS.md`. Its **§6 bug ledger** is the single most valuable file in the repo:
29 defects, each one a class of mistake that will recur.

**Do not start editing code yet.**

## Minute 2–5 — find out where it actually stands

1. **Is the patch set on GitHub?** The 22 files that made the build green were produced in
   a sandbox and handed to the owner as a zip to upload by drag-drop. Verify before you
   trust anything: `curl` the raw file and `diff -u` it against the workspace copy.
   **Paste drift has already happened twice.** Start with the four that matter most:
   `gradle/libs.versions.toml`, `app/build.gradle.kts`, `.github/workflows/ci.yml`,
   `app/src/main/java/com/photostudio/pro/monetization/ads/AdPolicy.kt`.
2. **What did the latest CI run do?** Four jobs: `resources`, `compile`, `test`, `release`.
   The run ledger is `README.md` §7.1. GitHub's API exposes run/job/step *conclusions*
   publicly but **not log text** (401) — so if a job is red and you need the error, either
   ask the owner to paste the log or reproduce it locally (next item). Do not burn a round
   trying to fetch logs anonymously.
3. **Reproduce the build yourself.** README §7.2 lists the free toolchain URLs and the exact
   command line that works inside a 2 GB sandbox:
   ```bash
   python3 tools/check_resources.py   # problems INSIDE a file      (~10 s, no SDK)
   python3 tools/check_wiring.py      # problems BETWEEN files      (~1 s,  no SDK)
   # then the real gate — see §7.2 for the env vars and flags:
   gradle --no-daemon -g <private-gradle-home> --max-workers=1 \
     -Dorg.gradle.jvmargs="-Xmx1000m" :app:compileDebugKotlin :app:testDebugUnitTest
   ```
   Both linters were **negative-control verified**, so a green run means something.
   **Never run `assembleDebug` or `bundleRelease` in a small sandbox** — D8 gets
   OOM-killed and Gradle reports *"build daemon disappeared unexpectedly"*, which reads
   like a project failure and is not one.

## Minute 5 onward — the loop

The `compile`-error loop is **finished**. Six rounds, bugs #15–#29, all recorded. The loop
now is:

```
take the FIRST error from the red job (most likely `release`)
  → fix only that
  → run both linters, then :app:compileDebugKotlin :app:testDebugUnitTest locally
  → hand the changed files to the owner (zip, full relative paths)
  → let CI re-run
  → repeat
```

**Fix the FIRST error only.** The following twenty are usually fallout from it.

**What is most likely to break next, in order:**
1. `release` job → *"Missing class detected while running R8"* → add that package as
   `-dontwarn` in `app/proguard-rules.pro`. Nothing references `okhttp3` any more (Coil was
   removed, bug #29).
2. `release` job → AAB size. The ~33 MB figure is arithmetic, never a measurement. If it
   comes back near 100 MB, `material-icons-extended` did not shrink — read the comment block
   at the bottom of `app/proguard-rules.pro` before touching anything.
3. `compile` job → a packaging/dexing error nobody has ever seen, because the sandbox cannot
   reach that stage.

**§6 P1 is done** (2026-10-30): BG Remover, ID Photo and Batch all export through
`ExportFlow` now, their Screen-level ad calls are deleted, and the refactor surfaced two
real defects on the way (**#30** Batch never checked the daily export cap, **#31** a failed
file was recorded as a success). Only `feature/pdf` still hand-rolls its export, legitimately
— it writes a `.pdf`, not an image. The **double-ad trap** in §6 P1 stays in the docs on
purpose: it is live for the next tool anyone adds.

So the queue is now: the `release` job (R8 / zipalign / AAB size), then **§6 P2** — the
29 hardcoded English `Text("…")` literals as one EN↔HI pass, the 6 deprecation warnings, the
parked `paywall_after_screens` lever, and one open product question the owner has to answer
(should Batch watermark free-tier output?).

**Treat "parameter is never used" as an error, not a warning.** That is how bugs #5, #12 and
#24 were found: a signature that accepts something and ignores it is a lie, and in this app
the lies were about ad caps and image filters.

## What NOT to do in this session

- ❌ **Do not re-open the build-toolchain decisions.** AGP 9.1.1 / Gradle 9.3.1 / JDK 17 /
  Kotlin 2.3.21 / compileSdk 37 / targetSdk 36 / Build-Tools 36.0.0 / the RevenueCat
  *umbrella* artifact / non-`-ktx` Firebase — all verified by six compile rounds. Do not add
  `org.jetbrains.kotlin.android` back, do not set `android.builtInKotlin=false`, do not
  downgrade a library to satisfy a metadata check (bug #16).
- ❌ Do not fold the **6 deprecation warnings** or the **29 hardcoded English `Text("…")`
  literals** into a build-fix commit. Both are deliberate and scheduled as one dedicated pass
  (§6 P2) — mixing them multiplies the hand-off file list for zero build benefit.
- ❌ Do not re-litigate the constraints in §2. They came from the owner and are settled.
  In particular: **no cloud AI APIs**, **no backend he maintains**, **never "100% offline"**.
- ❌ Do not claim something works unless it has been executed. See §4.3 — there is still a
  large gap between "it compiles" and "the app runs", and the owner has twice been misled by
  confident overclaiming. Bug **#28** is the cautionary tale: a stub harness reported
  "117/117 green" for weeks while the test file did not even compile.
- ❌ Do not delete the 22 unreferenced strings or `feature/later/` — both are deliberate.
- ❌ Do not remove an entry from `PARKED` in `tools/check_wiring.py` without resolving the
  underlying item. Parking is how the audit distinguishes "dead on purpose" from "dead by
  accident"; emptying it makes the lint useless. Two items are parked today
  (`paywall_after_screens`, `ssvStrictMode`), both with reasons, both in `README.md` §6.
- ❌ Do not push build output. `app/build/` and `.gradle/` are local artifacts — and stale
  `.gradle/` junk is already sitting on GitHub and should be deleted there.

## Reporting back to the owner

Reply in **Hinglish**. He is solo, non-programmer, and cannot read Kotlin.

Say *what to click*, not *what to understand*. When something is unverified, say so
plainly — that is `AGENTS.md` rule 9 and he specifically asked for it.

Full notes on working with him: `README.md` §11.
Everything only **he** can do (accounts, IDs, keystore, testers, store listing):
`YOUR-TURN.md`, 20 steps in Hinglish. **Sequencing he set explicitly:** finish the app
first, Play Store integration afterwards — do not front-load store work.
