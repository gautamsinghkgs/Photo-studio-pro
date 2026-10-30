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
| `README.md` **§0** | The one fact that reframes everything: **this code has never been compiled.** Compile errors are the expected state, not a defect | 1 min |
| `README.md` **§2** | The 7 constraints (C1–C7) and the **five pieces of earlier advice that were wrong** — do not reintroduce them | 3 min |
| `README.md` **§3.1** | The "never do these" table. Most are enforced by a linter or a test, but not all | 2 min |

Then `AGENTS.md` (9 non-negotiables) and, when you need the reasoning behind a rule,
`docs/DECISIONS.md`.

**Do not start editing code yet.**

## Minute 2–5 — find out where it actually stands

1. **What is the CI status of the latest run on `main`?** Which of the 4 jobs passed,
   which failed. (`README.md` §7.1 explains what each job proves.)
2. If you cannot read Actions logs, **ask the owner to paste them.** He has to do this
   either way — Actions logs are not publicly fetchable, even for a public repo.
3. Run the two checks that need no Android SDK (both take under a second):
   ```bash
   python3 tools/check_resources.py   # problems INSIDE a file
   python3 tools/check_wiring.py      # problems BETWEEN files
   ```
   Exit 0 = clean. **Both were negative-control verified** — real violations were injected
   into the sources and confirmed to be caught — so a green run means something.
   `check_wiring.py` is what found bugs #9–#11 (`README.md` §8), none of which a compiler
   could see. Run both before every commit.

## Minute 5 onward — the loop

```
take the FIRST error from the `compile` job
  → fix only that
  → run python3 tools/check_resources.py
  → commit
  → let CI re-run
  → repeat
```

**Fix the FIRST error only.** The following twenty are usually fallout from it, and
chasing them burns iterations. Expect **3–6 iterations** to reach a green `compile`.

`README.md` §7.1 lists the five most likely errors in probability order. §7.4 covers the
one you should read *before* touching billing — the RevenueCat API-style split, which is
a live inconsistency across two files, not a one-file fix.

**Treat `kotlinc`'s "parameter is never used" warning as an error.** It is how bug #12 was
found: `AdPolicy` accepted a `dailyCap` argument and ignored it, so a caller passing `0`
would have expected ads off and silently got ads.

## What NOT to do in this session

- ❌ Do not refactor, add features, or "clean up" before `compile` is green. A refactor on
  top of code that has never compiled multiplies the unknowns. The one pending refactor
  (3 tools onto `ExportFlow`) is `README.md` §6 P1 and carries a documented double-ad trap.
- ❌ Do not re-litigate the constraints in §2. They came from the owner and are settled.
  In particular: **no cloud AI APIs**, **no backend he maintains**, **never "100% offline"**.
- ❌ Do not claim something works unless it has been executed. See the verification
  pyramid in `README.md` §4.3 — there is a large gap between "117 tests pass" and
  "the app runs", and the owner has twice been misled by confident overclaiming.
- ❌ Do not delete the 22 unreferenced strings or `feature/later/` — both are deliberate.
- ❌ Do not remove an entry from `PARKED` in `tools/check_wiring.py` without resolving the
  underlying item. Parking is how the audit distinguishes "dead on purpose" from
  "dead by accident"; emptying it makes the lint useless. Two items are parked today
  (`paywall_after_screens`, `ssvStrictMode`), both with reasons, both in `README.md` §6.

## Reporting back to the owner

Reply in **Hinglish**. He is solo, non-programmer, and cannot read Kotlin.

Say *what to click*, not *what to understand*. When something is unverified, say so
plainly — that is `AGENTS.md` rule 9 and he specifically asked for it.

Full notes on working with him: `README.md` §11.
Everything only **he** can do (accounts, IDs, keystore, testers, store listing):
`YOUR-TURN.md`, 20 steps in Hinglish.
