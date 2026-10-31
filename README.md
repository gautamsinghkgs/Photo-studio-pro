# Photo Studio Pro

> ## 👉 READ THIS FILE FIRST
> This is the **single source of truth** for what this project is, how far it has
> got, what must never be changed, and what to do next. Every other document is
> detail that hangs off this one — the index is in **§10**.
>
> **Owner (Hinglish):** tumhare liye step-by-step guide `YOUR-TURN.md` mein hai
> (20 steps, sirf tumhare wale kaam). Yeh file agent ke liye hai.

---

## 0. If you are an AI agent picking this up in a fresh session

**30-second brief.** This is a monetisation project, not a hobby project. An 8-tool
on-device photo toolkit for online sellers, Android/Play Store, built by a solo
non-programmer owner with AI writing ~all of the code, on a **$25 total budget with
$0/month recurring**. Every architectural choice below traces back to those numbers.

**The single most important fact (updated 2026-10-30):**

> ### ✅ It compiles, and the tests pass — under a REAL Android toolchain.
> `:app:compileDebugKotlin` → **BUILD SUCCESSFUL** (0 errors, 6 deprecation warnings).
> `:app:testDebugUnitTest` → **BUILD SUCCESSFUL**, **117 tests, 0 failures, 0 skipped**.
> Verified inside the authoring sandbox with a hand-installed toolchain:
> **AGP 9.1.1 · Gradle 9.3.1 · JDK 17 · Kotlin 2.3.21 · KSP 2.3.12 · compileSdk 37
> (`platforms;android-37.0`) · Build-Tools 36.0.0 · targetSdk 36**.
> That toolchain lives in `~/.cache/tc/`, which is **excluded from workspace snapshots**
> — a fresh session must re-download it. URLs and the exact command lines are in **§7.2**.
>
> ### ⚠️ What is still NOT proven locally: dexing and R8.
> `:app:assembleDebug` dies at `dexBuilderDebug` with *"Gradle build daemon disappeared
> unexpectedly"* — D8 gets OOM-killed because the sandbox has 2 GB of RAM in total.
> This is a **sandbox limit, not a project error**: every task before dexing passes
> (`checkDebugAarMetadata`, `mergeDebugResources`, `processDebugResources`, all three
> manifest tasks, `compileDebugJavaWithJavac`, `desugarDebugFileDependencies`,
> `mergeDebugJavaResource`). Unit tests skip D8 entirely, which is why they run here.
> **`assembleDebug`, `bundleRelease` and the 16 KB zipalign check belong to the CI
> runner (7 GB).** If the `release` job goes red, treat it as new information — it is
> the only stage nobody has ever seen run.

**What the 117 tests do and do not prove.** They prove the *pure logic* is correct —
ad-policy decisions, reward caps, ID-photo geometry, quality search, layout maths — and
since 2026-10-30 they prove it **against the real classpath**, because they now run under
Gradle instead of a stub harness. They still prove nothing about whether Compose renders
correctly, whether ML Kit's beta API behaves at runtime, whether the ads/billing SDKs
respond as expected, or whether the release build shrinks to the ~33 MB budget. See
**§4.3**, and read bug **#28** before trusting any standalone-harness result again.

**Do this first:**
1. Read **§2** (constraints) and **§3** (do/don't). Two minutes, prevents the worst mistakes.
2. Check the latest CI run. `resources`, `compile` and `test` should be green now;
   **`release` is the unverified one** (R8 + resource shrinking + AAB size).
3. If a job is red, fix the **FIRST error only**. The following twenty are fallout from it.
4. Then take **§6 P1** (the `ExportFlow` refactor) — that is now the top of the queue.

**Do not** re-litigate settled decisions (§2, `docs/DECISIONS.md`), and do not fold the
6 compiler warnings or the 29 hardcoded English strings into a build-fix commit — both
are deliberate, tracked and scheduled (§6 P2).

---

## 1. What this project is

| | |
|---|---|
| **Product** | Photo Studio Pro — 8-tool on-device photo toolkit |
| **Audience** | Online sellers (Etsy/eBay/resellers), creators, small businesses |
| **Platform** | Android, Google Play. Category **Photography** |
| **Package** | `com.photostudio.pro` |
| **Play title** | `Photo Studio Pro: BG Remover` (28 chars) |
| **Short description** | *"Remove backgrounds & make ID photos — your photos never leave your phone."* (73 chars) |
| **Monetisation** | **Hybrid: ads AND subscription.** One entitlement, `pro` |
| **Min / target / compile SDK** | 24 / **36** / **37** — compileSdk had to move to 37 (bug #16); `targetSdk` is what Play enforces and stays 36 |
| **Expected download size** | ~33 MB (AAB, per-ABI split). Universal APK would be ~99 MB — never ship that |

**The 8 tools** (all implemented in source, all routed to real screens):

| # | Tool | Engine | Truly offline from first run? |
|---|---|---|---|
| M1 | Background Remover | ML Kit Subject Segmentation (**unbundled**) | ❌ **No** — ~200 KB stub, model downloads via Play services on first use |
| M2 | ID / Passport Photo | ML Kit Face Detection (bundled) + `IdPhotoMath` | ✅ Yes |
| M3 | Batch Resize / Convert | Classical `BitmapOps` | ✅ Yes |
| M6 | White Background | Classical flood fill (`LayoutMath`) | ✅ Yes |
| M7 | Collage Maker | Classical (`LayoutMath`, 20 templates) | ✅ Yes |
| M8 | Photos → PDF | PdfBox-Android | ✅ Yes |
| M9 | Text Scanner (OCR) | ML Kit Text Recognition v2 (**bundled**) | ✅ Yes |
| M10 | Photo Enhance | Classical (`EnhanceOps`) — **not generative** | ✅ Yes |

**Why 8 tools and not 1.** Hybrid monetisation needs daily sessions and natural break
points for ads. A single-purpose utility gets opened once, produces one file, and is
uninstalled. A toolkit gets reopened. This is the reason the scope is what it is —
do not cut it down to "just the background remover".

**The three marketing pillars** (in this order, they are the differentiator):
1. **NO UPLOAD** — your photos never leave your phone
2. **UNLIMITED** — no credits, no "3 free then pay"
3. **Works offline after one-time setup** — ⚠️ *never* "100% offline", see §3.1

---

## 2. The constraints that shaped everything

These came from the owner and are **non-negotiable**. Full text with the consequence
of violating each is in `docs/DECISIONS.md` §1.

| ID | Constraint | What it forced |
|---|---|---|
| **C1** | **No AI APIs of any kind.** No Gemini, OpenAI, Replicate, fal.ai — not even "for one premium feature" | All ML is on-device ML Kit. All image work is classical Android graphics. M10 Enhance is histogram/curve maths, **not** AI upscaling |
| **C2** | **No backend the owner maintains.** Zero lines of his own server code | RevenueCat (purchase validation) + Firebase Spark (analytics/crash/remote-config) + Cloudflare Pages (static files) + GitHub Actions (CI). That is the entire server surface |
| **C3** | Modern Play requirements | compileSdk/targetSdk **36**, minSdk 24, **16 KB page-size aligned** native libs, AAB with per-ABI splits. Target API 36 is mandatory from 31 Aug 2026 |
| **C4** | **Never sign up for anything requiring a credit card** | Ruled out: Fly.io, Firebase Blaze/GCP, paid UA, paid testers. Ruled in: everything in C2's free tiers |
| **C5** | **$25 TOTAL budget** | The $25 is the Play registration fee. Nothing else costs money, ever |
| **C6** | **AI writes the code; owner is solo and non-programmer** | Everything must be AI-buildable and verifiable by someone who cannot read Kotlin. Hence: CI as the build machine, linters that need no SDK, docs that say *what to click* |
| **C7** | **Maximise earnings** | Category chosen on revenue data, not interest. Photo & Video has the **highest median revenue** of any Play category ($124/mo) and the best $10K odds (7.3%) |

### ⚠️ Five pieces of earlier advice that were WRONG

`docs/DECISIONS.md` §2 documents these in full. **Do not reintroduce them:**

1. **"Use the bundled Subject Segmentation variant for a 100%-offline USP."**
   → **No bundled `segmentation-subject` artifact exists.** Verified against the live
   ML Kit doc. APK estimate went ~41 MB → ~33 MB, and the offline USP had to be
   repositioned. Only `segmentation-selfie` is bundled, and it segments *people* only —
   useless for product photos.
2. **"100% offline" as a marketing claim.** → False for BG Remover on first run, and a
   **Play Deceptive Behavior** policy risk. Use "works offline after one-time setup".
3. **"AdMob Server-Side Verification is mandatory for rewarded ads."** → It is
   **optional**. `onUserEarnedReward` alone grants the reward; there is **no retry logic**
   in the SDK; and **callbacks never fire for test ads**, so it is untestable during
   closed testing. Deferred to Phase 2. Build zero backend at launch.
4. **"`app-ads.txt` needs paid hosting."** → Free on Cloudflare Pages at
   `https://<project>.pages.dev/app-ads.txt`.
5. **"The manifest needs one permission."** → It declares **two**: `INTERNET` and
   `ACCESS_NETWORK_STATE` (the latter is required by `ModuleInstallClient`).

**Meta-lesson, and it has now been proven six times:** ML Kit **beta** API signatures
are stale in training data. Before writing or fixing any ML Kit call, fetch the live
official doc page and copy the import block from it. Do not write it from memory.

---

## 3. DO / DON'T — the operating rules

### 3.1 Never do these

| ❌ Never | Why | Where it is enforced |
|---|---|---|
| Write **"100% offline"** anywhere | False for M1; Play Deceptive Behavior risk | `tools/check_resources.py` (CI job `resources`) |
| Name a feature **"AI Enhance / AI Restore / AI Generator / AI Avatar / AI Colorize / AI Retouch / AI Beauty / AI Photo Studio"** | Only real-ML features may carry "AI". M10 is **"Photo Enhance"** | same linter |
| Claim **"guaranteed acceptance"** for ID photos | Governments reject for reasons the app cannot see | `id_disclaimer` string must stay on screen |
| Enable **banner** or **app-open** ads | Banners destroy the export UX; app-open is disabled until Day 90 | Both are unconditionally `false` with **no enable flag** — a test asserts it, so adding a flag fails the build |
| Add an ad decision **outside `AdPolicy`** | A paying user seeing a forced ad is the #2 revenue killer and a direct cause of refunds | `AdCoordinator` is the only caller of `AdManager.showInterstitial` |
| Add an export path **outside `ExportFlow`** | Free-tier cap, watermark, destination, counters and the post-export ad decision all live there | 3 tools still violate this — see §6 P1 |
| Let **rewarded ads grant Pro / a subscription / a trial extension / lifetime** | There is no server-side verification, so the defence is making a forged callback worthless | `RewardGuard.RewardKind` has exactly 5 members; a test forbids any name containing PRO, LIFETIME, TRIAL_EXTEND, SUBSCRIPTION |
| Hardcode a **version** outside `gradle/libs.versions.toml` | Versions were resolved against live Maven on 2026-10-29; scattering them makes drift invisible | Single file, `gradle/libs.versions.toml` |
| Leave a **parameter that the function ignores** | It makes the signature lie. A caller passing `dailyCap = 0` expected ads off and got ads (bug #12); `floodWhiten` accepted `high` and never used it, so its own documented hysteresis did not exist (bug #5) | `kotlinc` warns "parameter is never used" — **treat that warning as an error** |
| Leave a **UI control that cannot do what its label says** | Play Deceptive Behaviour exposure, even when it fails in the safe direction | Bug #10: the EXIF switch was inert because `Bitmap.compress()` cannot carry EXIF |
| Hardcode a **price** in the UI | Prices must come from `storeProduct.price.formatted` — Play localises them | `PaywallScreen` buttons stay disabled until real prices load |
| Add a `-keep` rule for **material-icons-extended** | ~7,000 icon objects / ~15 MB unshrunk; only 13 are used. Release minify+shrink is what makes it safe | `app/proguard-rules.pro` carries the verification steps |
| Log **OCR text** to analytics | OCR output is often invoices and IDs; sending it contradicts the Data Safety form and privacy policy | `Events.logOcrCompleted` logs the **character count only** |
| Propose any **cloud AI / generative** feature, user accounts, login, cloud sync, paid UA, or a universal APK | All permanent dead ends | `docs/DECISIONS.md` §3 |

### 3.2 Always do these

- **Fix the FIRST compile error only.** Later errors are usually fallout.
- **Grep the API surface before calling it.** `grep -n "fun log" Events.kt` would have
  caught a call to a non-existent `Events.logOcrCompleted`. Guessing signatures is the
  most common failure mode in this codebase.
- **Say when something is unverified.** The owner has been burned twice by
  confident-sounding advice that turned out to be wrong (both documented in §2).
- **Reply to the owner in Hinglish.** He writes entirely in Hinglish.
- **Explain *what to click*, not *what to understand*.** He has little coding experience.
- **Keep EN and HI strings in exact sync** — currently **140/140**. The linter fails the
  build on parity breaks and on duplicate resource names.
- **Test every milestone in airplane mode on a mid-range phone** (owner's own rule).

---

## 4. Current status

### 4.1 What is DONE

| Layer | Status |
|---|---|
| Gradle / manifest / resources / themes / icons | ✅ Complete. All versions resolved from **live Maven 2026-10-29** |
| M0 app shell, nav, theme, onboarding | ✅ Complete — onboarding is **load-bearing**, see §8 bug #6 |
| M1 Background Remover | ✅ Complete (incl. `SubjectSegmentationEngine`) |
| M2 ID / Passport Photo | ✅ Complete — 38 countries in `assets/countries.json`, print-sheet builder |
| M3 Batch | ✅ Complete — SAF folder picker, `takePersistableUriPermission`, **no cacheDir fallback** |
| M4 Monetisation | ✅ Complete — `AdPolicy` + `AdCoordinator` + `RewardGuard` + `UmpConsent` + `ExportFlow` + real RevenueCat purchase call |
| M5 Remote Config | ✅ Complete — 8 `tool_enabled_*` keys + kill switches, **all default to safe/off** |
| M6 White Background | ✅ Complete |
| M7 Collage | ✅ Complete — 20 templates, 10 free |
| M8 Photos → PDF | ✅ Complete — FIT is the default, FILL crops |
| M9 OCR | ✅ Complete — bundled, offline from first run |
| M10 Photo Enhance | ✅ Complete — classical, with an on-screen honesty note (`enh_honest`) |
| Home screen | ✅ All 8 rows `built = true`; all 8 routes point at real screens |
| Settings | ✅ "Upgrade to Pro" + "Delete my data" |
| Unit tests | ✅ **117 pass**, 6 classes, run against real production sources |
| CI | ✅ 4 jobs, YAML-validated |
| Resource lint (`check_resources.py`) | ✅ Clean, negative-control verified |
| Wiring lint (`check_wiring.py`) | ✅ Clean, negative-control verified — **7 injected violations all caught** |
| Store deliverables | ✅ 7 files written, all with `[BRACKETED]` placeholders |

**Source size:** 55 Kotlin files in `app/src/main` = **7,881 lines**, plus 5 test files;
**9,093 lines** across `app/src`. `core` 15 / `feature` 26 / `monetization` 10 / `analytics` 1 / top-level 3.
Strings **140/140** EN↔HI in sync.

### 4.2 What is NOT done

**Code work:**
- ❌ **Never compiled.** The `compile` CI job has not run yet. This is the #1 blocker.
- ❌ **3 tools bypass `ExportFlow`** — `BgRemoverViewModel`, `IdPhotoViewModel`,
  `BatchViewModel` each hand-roll cap → watermark → encode → save. Consequence: they
  only support the share sheet, **not** a user-chosen SAF folder, unlike the other 5. See §6 P1.
- ❌ **RevenueCat API styles are inconsistent** across 3 files — see §7.4. This will
  produce compile errors and both files must converge on one style.
- ⚠️ **One dead Remote Config lever.** `paywall_after_screens` is unwired at *both* ends:
  nothing calls `Prefs.incrementPaywallScreens()`, nothing reads `RemoteFlags.paywallAfterScreens`,
  and the key is absent from the defaults map. **Harmless in code, but the owner will see the
  knob in the Firebase console and expect it to work.** Decision required before launch:
  wire it or delete it. Parked explicitly in `tools/check_wiring.py` → `PARKED`. See §6 P2.
- ✅ `ssvStrictMode` is also never read, but that one is **deliberate** — SSV is deferred to
  Phase 2 (§6 P4). Also parked in `PARKED`, with the reason.
- ❌ `feature/later/` (`LaterMilestoneScreen`) is **unrouted** — deliberately parked for M13+.
- ❌ 22 strings are defined but unreferenced (`home_title`, `save`, `share`, `reward_*`).
  Harmless UI-polish spares. **Do not delete** without checking the Hindi file.

**Owner-only work (an agent cannot do any of this):**
- ❌ Play Console account + **identity verification** (started, not finished)
- ❌ Firebase project → `app/google-services.json` (git-ignored on purpose; CI writes a placeholder)
- ❌ AdMob app + **4 ad units** → real IDs into `AdUnitIds.kt` and the manifest
- ❌ RevenueCat project → real `REVENUECAT_PUBLIC_KEY` into `app/build.gradle.kts`
- ❌ Cloudflare Pages deploy → real privacy-policy URL into `SettingsScreen.privacyUrl`
- ❌ Play Console products (`pro_weekly` / `pro_yearly` / `pro_lifetime`) + **Licence Testing ON**
- ❌ Release signing keystore (never commit it; `PUSH-TO-GITHUB.sh` aborts if it finds one)
- ❌ **12 testers opted in continuously for 14 days** — ⏱️ the biggest schedule risk.
  Personal Play accounts created after 13 Nov 2023 cannot reach production without this.
- ❌ Any on-device testing. No emulator ever ran.

### 4.3 The verification pyramid — what is actually PROVEN

Be precise about this. Overclaiming here is how the two §2 mistakes happened.

```
        ┌───────────────────────────────────┐
   NEVER│ Runs on a phone · renders · real   │  ← 0% verified. No emulator has ever
  DONE  │ model download · real purchase     │     run (no /dev/kvm in the sandbox).
        ├───────────────────────────────────┤
   NEVER│ dex + shrink + package:            │  ← blocked locally by 2 GB RAM (D8 is
  DONE  │ assembleDebug, bundleRelease,      │     OOM-killed). The CI runner's 7 GB
        │ R8, 16 KB zipalign, AAB size       │     is the only place this can be proven.
        ├───────────────────────────────────┤
  DONE  │ Resources · manifests · aapt2      │  ← mergeDebugResources, all 3 manifest
        │                                    │     tasks, processDebugResources,
        │                                    │     checkDebugAarMetadata: green
        ├───────────────────────────────────┤
  DONE  │ COMPILES with the real Android SDK │  ← :app:compileDebugKotlin
        │ — all 55 main + 6 test sources     │     BUILD SUCCESSFUL, 0 errors,
        │                                    │     6 deprecation warnings (§6 P2)
        ├───────────────────────────────────┤
  DONE  │ 117 unit tests pass                │  ← :app:testDebugUnitTest
        │ on the REAL classpath              │     117 tests, 0 failures, 0 skipped
        ├───────────────────────────────────┤
  DONE  │ Static validation: XML/JSON/YAML,  │  ← check_resources.py +
        │ resource refs, wiring, strings     │     check_wiring.py — 0 errors
        ├───────────────────────────────────┤
  DONE  │ Dependency versions, policy facts, │  ← live Maven + official docs +
        │ API availability, ad/subscription  │     javap on the downloaded AARs,
        │ economics                          │     2026-10-29 / 2026-10-30
        └───────────────────────────────────┘
```

**Proven by compilation, NOT yet proven by execution.** These used to be guesses; they
now match the real artifacts (verified with `javap` on the downloaded AARs and then
compiled), but no device has ever run them:
- `SubjectSegmentationEngine.kt` — uses `ModuleInstall` from **inside**
  `play-services-base` (bug #19). Compiles. Whether the ~200 KB stub actually pulls the
  model, how long it takes, and how it behaves on a device without Play services: unknown.
- `PurchaseFlow.kt` / `EntitlementManager.kt` — match RevenueCat **6.5.2 umbrella**
  shapes (`p.product`, `sp.price.amountMicros`, `PurchasesErrorCode.OperationAlreadyInProgressError`).
  No real purchase has ever been made.
- ML Kit `Face` probabilities are nullable `Float?` — `FaceEngine.kt` now passes nulls
  straight through (bug #26). Runtime behaviour with a real face: untested.

**Still genuinely unverified — treat as likely to break:**
- **R8 + resource shrinking.** `isMinifyEnabled`/`isShrinkResources` have never run.
  Classic failure mode: *"Missing class detected while running R8"* → add the `-dontwarn`
  to `app/proguard-rules.pro`. Known-safe today: Coil is gone (bug #29), so nothing
  references `okhttp3`; pdfbox has `-dontwarn java.awt.**`.
- **Real AAB download size** — the CI `release` job produces the first measurement, and
  it is the check that `material-icons-extended` really shrank from ~7,000 icons to ~13.
- **Compose at runtime** — every screen compiles; none has ever rendered.
- Whether AGP 9.1.1's built-in Kotlin + KSP 2.3.12 annotation processing (Room) works
  end to end — Room's KSP step ran clean in `compileDebugKotlin`, but the generated DAO
  implementations have never been exercised.

---

## 5. Architecture — how to implement things here

### 5.1 Package map

```
app/src/main/java/com/photostudio/pro/
├── PhotoStudioApp.kt        Application; SDK init order matters (see below)
├── MainActivity.kt          single-Activity, Compose, edge-to-edge
├── Nav.kt                   ⭐ Routes + the onboarding gate + the ad host
│
├── analytics/
│   └── Events.kt            every analytics event in one file. OCR logs counts, never text
│
├── core/
│   ├── data/local/Prefs.kt              DataStore. ⭐ every state-mutating setter must have a caller
│   ├── data/repository/PrefsUsageCounters.kt   daily counters + timestamps for AdPolicy
│   ├── image/                           PURE image maths, no Compose, no ML Kit
│   │   ├── BitmapOps.kt        scale / crop / rotate / EXIF orientation
│   │   ├── MaskOps.kt          alpha masks, featherInt
│   │   ├── ExportOps.kt        encode, watermark, toShareUri, saveToTree, searchQualityForTargetSize
│   │   ├── EnhanceOps.kt       M10 — classical curves/histogram
│   │   ├── WhiteBgOps.kt       M6 — delegates maths to LayoutMath
│   │   ├── CollageOps.kt       M7 — 20 templates, delegates to LayoutMath
│   │   ├── PdfOps.kt           M8 — delegates to LayoutMath
│   │   ├── QualitySearch.kt    ⭐ PURE (no android.graphics) → harness-testable
│   │   └── LayoutMath.kt       ⭐ PURE → harness-testable. The maths surface for M6/M7/M8
│   ├── ui/{Theme,Components}.kt         design system + shared widgets
│   └── util/
│       ├── ServiceLocator.kt   hand-rolled DI. No Koin/Hilt — fewer moving parts for a solo build
│       └── ExportFlow.kt       ⭐ THE SINGLE MONEY PATH (see §5.3)
│
├── feature/
│   ├── home/        8 rows, each gated by a Remote Config `tool_enabled_*` flag
│   ├── onboarding/  ⭐ sets the flag AdPolicy reads — without it, no ad can ever show
│   ├── bgremover/   M1  ┐
│   ├── idphoto/     M2  │ these three hand-roll their export
│   ├── batch/       M3  ┘ (see §6 P1)
│   ├── whitebg/     M6  ┐
│   ├── collage/     M7  │ these three use ExportFlow correctly
│   ├── enhance/     M10 ┘
│   ├── pdf/         M8  own path — outputs a .pdf, not an image
│   ├── ocr/         M9  no export — copies text to clipboard
│   ├── paywall/     soft-hard hybrid, 9 triggers
│   ├── settings/
│   └── later/       unrouted, parked for M13+
│
└── monetization/
    ├── ads/
    │   ├── AdPolicy.kt        ⭐ THE ONLY ad decision point. Every reason string is test-locked
    │   ├── AdCoordinator.kt   ⭐ the only caller of AdManager.showInterstitial
    │   ├── AdManager.kt       thin AdMob wrapper
    │   ├── RewardGuard.kt     ⭐ 5 RewardKinds; cap check is `x in 0 until cap`
    │   ├── UmpConsent.kt      GDPR/UMP consent flow
    │   ├── AdUnitIds.kt       ⚠️ Google's SAMPLE ids — owner must replace
    │   └── LocalIdentity.kt   install timestamp, days-since-install
    ├── billing/
    │   ├── EntitlementManager.kt  ⭐ one entitlement `pro`; cached locally so Pro works offline
    │   └── PurchaseFlow.kt        ⚠️ written, never executed. API-shape risk — §7.4
    └── config/RemoteFlags.kt  Remote Config, 8 tool flags + kill switches, all default safe
```

### 5.2 The three single-path objects

The whole architecture rests on **three objects that must not be duplicated**:

1. **`AdPolicy`** decides whether an ad shows. It is a pure function of an `AdContext`
   built from persisted counters. It never decides *when* to be called — `AdCoordinator`
   does that. Every rejection reason is a stable string asserted by `AdPolicyTest`:
   `remote_kill_switch`, `user_is_pro`, `user_in_trial`, `purchase_intent_detected`,
   `never_on_app_open`, `never_during_onboarding`, `not_a_break_point`,
   `day0_2_suppression`, `first_ad_delay`, `gap_not_elapsed`, `daily_cap`,
   `banners_permanently_disabled`, `app_open_disabled_until_day90`, `allowed`.
   **If you change a reason string, you change the test in the same commit.**

2. **`ExportFlow`** handles every image export. See §5.3.

3. **`EntitlementManager`** owns the `pro` state. `State` carries `isPro`, `isInTrial`,
   `hasPurchaseIntent`, `activeProductId`, `expiresAtMillis`, `loaded`. Constants:
   `ENTITLEMENT_PRO = "pro"`, `PRODUCT_WEEKLY = "pro_weekly"`,
   `PRODUCT_YEARLY = "pro_yearly"`, `PRODUCT_LIFETIME = "pro_lifetime"`, `TRIAL_DAYS = 7`.
   **Entitlements are cached locally so Pro keeps working offline.**
   Nothing outside `monetization/billing/` should touch `Purchases.sharedInstance`.

### 5.3 `ExportFlow` — the single money path

Two entry points, one set of rules. **Every** image export in the app goes through one of
them; a tool that does not is a bug — that is how #11 (no ad hook at all) and #30 (no cap
check at all) happened.

```kotlin
suspend fun run(
    ctx: Context, activity: Activity?, source: Bitmap, toolId: String, fileName: String,
    format: ExportOps.Format = JPEG,
    treeUri: Uri? = null,          // non-null → SAF folder; null → share sheet
    plan: Plan = Plan.Standard(),  // what the FREE tier gives up — closed set, see below
    afterSave: () -> Unit = {},
): Outcome                          // Saved(uri,bytes,w,h) | NeedsPro | Failed(reason)

suspend fun runBatch(
    activity: Activity?, toolId: String, fileCount: Int,
    afterSave: () -> Unit = {},
    write: suspend (index: Int) -> Long,  // LAST on purpose — a trailing lambda binds to
): BatchOutcome                           // the final parameter (bug #32). Returns bytes,
                                          // or a negative value if that file failed.
                       // Finished(done,failed,totalBytes) | NeedsPro | Failed(reason)
```

`Plan` is a **closed set of two**, and that is the point: a tool cannot quietly opt out of the
watermark or the daily cap, it can only pick a shape whose reason is written down here.

| Plan | Free tier | Pro | Used by |
|---|---|---|---|
| `Standard(quality = 92)` | downscale to `FREE_MAX_EDGE = 1080`, watermark, encode at `quality` | untouched, no watermark, same `quality` | BG Remover (passes 96 Pro / 88 free), Enhance, Collage, White BG |
| `FixedDimensions(maxBytes, tolerancePct = 8)` | watermark, **never downscaled**, quality searched to land under `maxBytes` | the same, minus the watermark | **ID Photo only** — portals validate pixel dimensions, so SD would fail the very check the tool exists to pass, and a print sheet would come out at the wrong DPI |

Sequence, in order — **do not reorder**:
1. Free daily cap → `NeedsPro` if exceeded and not Pro (`runBatch` checks it **once per run**)
2. Free-tier transform: downscale unless `FixedDimensions`, then watermark (private `encodeFor`)
3. Encode — fixed quality, or searched against the KB ceiling
4. `ExportOps.saveToTree` when `treeUri != null`, else `toShareUri(bytes)` — **the exact bytes
   that were counted**, never a re-encode (re-encoding could put an ID photo back over its
   portal's KB limit after the search had hit it)
5. `prefs.noteExport()` → `usage.refresh()` → `Events.logExportCompleted`
6. Recycle intermediates — **never the caller's `source`**
7. `adCoordinator.afterExportCompleted(activity) { afterSave() }` ← the natural break point:
   **once** per export, **once** per batch run

Step 7 is why no Screen may call `afterExportCompleted` itself — the double-ad trap is in
§6 P1. And `runBatch` exists so a 40-file batch is **one** commercial event, not forty: forty
decisions would be denied thirty-nine times by `AdPolicy`'s 180 s gap while still burning the
daily interstitial budget that the *next* tool needs.

### 5.4 Adding a new tool — the checklist

1. Pure maths into `core/image/`, **no Android imports if you want it unit-testable**
   (anything importing `android.graphics.*` cannot be compiled in the local harness — see §7.2)
2. `feature/<tool>/<Tool>ViewModel.kt` + `<Tool>Screen.kt`
3. Export **through `ExportFlow.run()`** — never hand-roll it
4. Route in `Nav.kt` (`Routes` + `composable`)
5. Row in `HomeScreen` with `built = true`
6. `tool_enabled_<tool>` flag in `RemoteFlags` **and** wire it into the `HomeScreen` gate
7. Strings in **both** `res/values/strings.xml` and `res/values-hi/strings.xml`
8. A paywall trigger if it has a Pro boundary
9. `python3 tools/check_resources.py` must stay green
10. If the maths is pure, add a test class **and register it in the harness `Runner.kt`**

**Step 6 is the one that gets forgotten.** A flag that exists in `RemoteFlags` but is not
read by `HomeScreen` is a no-op — and the inverse (read but not defined) is a compile error.

### 5.5 Why the pure-maths split exists

`QualitySearch.kt` and `LayoutMath.kt` were deliberately extracted so they import
**nothing** from `android.graphics`. That is what makes them compilable and testable in
the local kotlinc harness without an Android SDK. **This split is what caught 3 real
product bugs** (§8) that no amount of reading would have found. Preserve it: when you
touch image maths, ask whether the logic can move into a pure file.

---

## 6. Remaining work, in priority order

### ✅ P0 — Make it compile. **DONE 2026-10-30.**

Six compile rounds, 15 separate bugs (**#15–#29** in §8). Every one of them was in the
build files or in an API shape invented from memory — **not one was a product-design
fault**. The ranked list of "likely first errors" in `README-BUILD.md` is now history;
the real sequence was:

| round | task | outcome |
|---|---|---|
| #1 | `:app:compileDebugKotlin` | Firebase `-ktx` coordinates do not exist under BOM 35.1.0 → **#15** |
| #2 | same | `checkDebugAarMetadata` — 36 issues; the 2026 AndroidX generation needs compileSdk 37 **and** AGP ≥ 9.1.0 → **#16** |
| #3 | same | `org.jetbrains.kotlin.android` is incompatible with AGP 9's built-in Kotlin → **#17** |
| #4 | same | **114 errors / 12 files** — wrong RevenueCat coordinate (69 of them) + `ModuleInstall`/ML Kit APIs invented from memory → **#18, #19** |
| #5 | same | **37 errors / 11 files** — Compose import and extension traps, one real logic bug in `EnhanceOps.adjust`, a typo'd flag name → **#20–#27** |
| #6 | same | 🟢 **BUILD SUCCESSFUL** — 0 errors, 6 warnings |
| #7 | `:app:testDebugUnitTest` | run 1 **FAILED**: 11 errors in `AdPolicyTest.kt` → **#28**. Run 2 🟢 **117 tests, 0 failures** |
| — | `:app:assembleDebug` | 🔴 **sandbox limit, not a project error**: D8 is OOM-killed at `dexBuilderDebug` (2 GB RAM total). Every task before dexing passed. The CI runner must prove it — see **§4.3** |

**Do not re-open any of this.** The exact command lines that reproduce both green results
are in **§7.2**. If you ever change a toolchain version, re-run them before touching
anything else — and if you must build an APK locally, use a machine with ≥ 6 GB of RAM.

### ✅ P1 — Refactor 3 tools onto `ExportFlow`. **DONE 2026-10-30.**

`BgRemoverViewModel.export()`, `IdPhotoViewModel.export()` and `BatchViewModel.run()` each
used to reimplement the §5.3 sequence by hand. All three now go through `ExportFlow` and
their Screen-level ad calls are **deleted**. `check_wiring.py` confirms it: the three
"hand-rolls its export" notes are gone and only `feature/pdf` remains — legitimately, since
it writes a `.pdf` document rather than an image (its ad hook is wired, bug #11).

**What actually changed for a user:**
- **BG Remover and ID Photo can now save to a folder**, not only to the share sheet. Both
  screens got the same `OpenDocumentTree` + persistable-grant pattern the other four tools
  already used, with a "Choose folder / Save to" button under Export. It matters most for
  the ID-photo **print sheet**: eight copies used to arrive through a viewer app.
- **Batch is now gated by the daily cap.** It counted an export on every run but never
  *checked* the cap, so a free user could resize unlimited photos all day by running
  one-file batches (**bug #30**). One run = one export, gated like every other tool.
- **A failed batch file no longer shows `✔ -1 B`** (**bug #31**): `processOne` signals
  failure both with a negative sentinel and with an exception, and only the exception half
  was handled.
- Sharing now writes the **exact bytes that were counted** — `ExportOps.toShareUri` gained a
  bytes overload. The share path used to re-encode from the bitmap, which for ID Photo could
  push the file back over a portal's KB ceiling *after* the quality search had hit it.
- The free-tier rules now have exactly **one** home. `ExportFlow.Plan` is a closed set of two
  shapes: `Standard` (downscale to 1080 + watermark + fixed quality) and `FixedDimensions`
  (watermark, **never** downscale, search for a KB ceiling). ID Photo is the only
  `FixedDimensions` user, and the reason is written where it gets audited instead of buried
  in a feature folder: government portals validate pixel dimensions, so shrinking a 35×45 mm
  photo to SD would break the very check the tool exists to pass. A print sheet (1200×1800)
  would also come out at the wrong DPI.
- Batch fires **one** ad decision per run, not one per file. 40 decisions for a 40-photo
  batch would either spam the user or — far more likely — be denied 39 times by `AdPolicy`'s
  180 s gap while still burning the daily interstitial budget, which would then deny the
  *next* tool its ad.

**⚠️ THE DOUBLE-AD TRAP — closed for these three, still live for the next tool you add.**
`ExportFlow.run()` and `runBatch()` both call `afterExportCompleted` as their final step, so
a Screen that also calls it fires **two** ad decisions for one export:

```kotlin
// ❌ NEVER, once a tool exports through ExportFlow:
vm.export(...) { uri ->
    if (uri != null && adsReady && activity != null) scope.launch {
        ServiceLocator.adCoordinator.afterExportCompleted(activity) { }
    }
}
// ✅ The Outcome is the only thing the Screen reacts to:
vm.export(ctx, activity, picked.value, ...) { out ->
    if (out is ExportFlow.Outcome.NeedsPro) onGoPro()
}
```

No ad shows twice (`gap_not_elapsed` denies the second), but the `afterSave()` semantics
break, the daily budget is burned by a decision that can never show, and the code lies about
who owns the placement. `check_wiring.py` reports this as an **error**, not a note.

One behavioural difference came with the move, deliberately: the old Screen-level call was
gated on `adsReady && activity != null`; `ExportFlow` gates only on `activity != null`.
`adsReady` means "the SDK finished initialising", and `AdCoordinator` already refuses to show
before then — gating twice only ever *lost* inventory. `adsReady` stays in every Screen's
signature because the **rewarded** placements still use it (Batch's "+5 photos", OCR, PDF).

**Open question for the owner (§6 P2):** Batch is now the only image tool that does **not**
watermark free-tier output. Its free limit is "batch of 1" and the daily cap now applies, so
the leak is bounded at 3 clean exports a day — but that should be a decision, not an
inheritance. Watermarking a resize tool is user-hostile; leaving it clean is defensible.
Pick one and write it into the SPEC's free-tier table.

### 🟡 P2 — Decisions and placeholders

**One decision an agent CAN make (or must escalate):**

`paywall_after_screens` — wire it or delete it. Wiring it means choosing what counts as a
"screen" (probably each tool opened), where to count it (`Nav.kt`), and a guard so the
paywall does not re-fire on every subsequent screen. It is a real growth lever — the research
in `docs/DECISIONS.md` §7 shows hard paywalls earning **$2.32 D14 RPI vs $0.27** for
freemium, so paywall aggression matters — but it is also a retention risk, and it must stay
remotely controllable and default to "never". `compile` is green now, so this is unblocked;
it is still parked until the `release` job passes, because a growth lever is worthless in an
app that cannot ship.

**Tracked and deliberately NOT fixed yet — do not fold these into an unrelated commit:**

| Item | Size | Why it waits |
|---|---|---|
| **29 hardcoded English `Text("…")` literals** across 10 screens (`BatchScreen` 88/114/189/194, `WhiteBgScreen` 110/124/136, `CollageScreen` 107/143/145/179, `PdfScreen` 116/119, `IdPhotoScreen` 96/137, `SettingsScreen` 137/140, `OcrScreen` 66, `BgRemoverScreen` 132, `EnhanceScreen` 122) | 29 strings × 2 locales | A Hindi user currently sees a mixed-language screen. This is one dedicated EN↔HI pass, not 29 opportunistic edits: doing it piecemeal multiplies the hand-off file list for zero build benefit. `check_resources.py` reports 140/140 parity and **cannot see literals** — that is exactly why the count is written down here |
| **22 unreferenced strings** | 0 work | Deliberate. They are the vocabulary for the P2 localisation pass and for screens still in `feature/later/`. Do not delete them |

**Closed by the post-green cleanup commit (2026-10-31)** — OcrScreen's warning one commit later via #36 —:** the six compiler warnings · the PDF
failure-path bitmap leak · unused Room (#35) · and the owner decisions — interstitials **ON at
launch** (one per export cycle, post-export; the D30–60 gate now governs frequency ramp-up only)
and **Batch free output stays clean until 100k+ downloads**, watermark being the late-stage lever.

**Owner-only placeholders (an agent cannot fill any of these):**

| File | Placeholder | Needs |
|---|---|---|
| `AndroidManifest.xml` | AdMob **app ID** `ca-app-pub-3940256099942544~3347511713` (Google's sample, flagged in a comment) | Real app ID — **ads will not initialise until replaced** |
| `monetization/ads/AdUnitIds.kt` | Google's sample ad unit IDs | 4 real ad units |
| `app/build.gradle.kts:32` | `REVENUECAT_PUBLIC_KEY` placeholder | Real public key (public keys are safe to embed; there is deliberately **no private key** anywhere) |
| `feature/settings/SettingsScreen.kt` | `privacyUrl` placeholder | Cloudflare Pages URL |
| `app/google-services.json` | absent, git-ignored | Firebase project file. Plugin lines are **commented out** in `app/build.gradle.kts` until it exists; CI writes a placeholder so the build proceeds |
| signing | `keystore.properties` absent | Owner generates locally. **Never commit.** `PUSH-TO-GITHUB.sh` aborts if it finds one |
| `assets/countries.json` | 38 entries, each with a `sourceUrl` | Spot-check the top-10 markets — governments change dimensions without notice |

### 🟢 P3 — Accounts, store listing, closed testing

All step-by-step in Hinglish in `YOUR-TURN.md` (steps 0–16), with copy-paste content in
`deliverables/`. The critical path is: **Play Console + identity verification on day 1**
→ accounts → **recruit 18–25 testers to land 12 who stay opted in for 14 days** → upload
AAB to closed testing (**the 14-day clock starts here**) → production.

The 14-day tester requirement is the **schedule risk**, not the code. Start recruiting early.

### 🔵 P4 — Post-launch, deliberately deferred

Interstitials **ON from day 1** — one per export cycle, post-export (owner decision 2026-10-31;
rails: daily cap, 180 s gap, first-ad delay, pro = zero ads) → Day 30–60 frequency ramps to
1/day **only if** D7 retention >15% **and** crash-free >99.5% → Day 60–90 lifetime IAP + 2–3/day →
Day 90+ mediation, win-back campaigns, `es` / `pt-BR` localisation.

Also deferred: **AdMob SSV** (Phase 2 — only build it if weekly rewarded impressions ÷
Firebase `reward_granted` exceeds **3.0**; ≈1.0 is healthy, >1.5 investigate).

---

## 7. Build & test

### 7.1 CI — the build machine

`.github/workflows/ci.yml`, **4 jobs**. Gradle **9.3.1** is installed straight from
`services.gradle.org`, so the missing `gradle-wrapper.jar` does not matter. JDK 17
temurin; `sdkmanager "platforms;android-37.0" "build-tools;36.0.0" "platform-tools"`
— note the **`.0`**: `platforms;android-37` does not exist, fails, and silently skips
everything after it on the same command line (bug #13). A placeholder
`google-services.json` is auto-written.

| Job | Command | What it proves |
|---|---|---|
| **`resources`** | `tools/check_resources.py` **and** `tools/check_wiring.py` | Resource integrity *and* cross-file wiring. **~10 s, no Android SDK. Gates the other three** |
| **`compile`** | `assembleDebug` | Kotlin compilation is already green locally; this job adds the stage the sandbox **cannot** run — D8 dexing + packaging. ⭐ **Start here** |
| **`test`** | `testDebugUnitTest` + HTML artifact | **117 tests, 0 failures** — green locally on 2026-10-30; CI re-proves it on a real runner |
| **`release`** | `bundleRelease` (unsigned) + `zipalign -c -P 16 -v 4` + arm64-v8a check + size report + AAB artifact | R8 + resource shrinking + the release-only `lintVital` — the three stages that first executed in runs #7–#8 and produced **#33** and **#34** — plus the two Play gates that **cannot be fixed after a rejection** (16 KB page size, 64-bit) which passed in run #9 together with the first real download-size measurement (AAB, all ABIs: 54,280,259 B). Since run #13 (fixed #37: flags verified against `bundletool help` + `sdkVersion` added to the device spec) the job also measures the **per-device slice** (arm64-v8a / en-IN / 420 dpi) with bundletool and publishes it as a `::notice::` annotation — first measured: **24,227,813 B ≈ 23.1 MB**. The step is `continue-on-error`: reporting, never a gate |

`compile` / `test` / `release` run in parallel, each with `needs: resources`.

**Cost:** public repo = unlimited Actions minutes. Private repo = 2,000 min/month
(≈200+ Android builds). Either is free.

**The CI run ledger — every failure so far, oldest first:**

| run | id | verdict | cause |
|---|---|---|---|
| #1 | `41004249229` | 🔴 | SDK-install step died in **0 s** → **#13** |
| #2 | `41008766723` | 🔴 | `kotlinOptions { jvmTarget }` is a hard error in Kotlin 2.3 → **#14** |
| #3 | `41010957634` | 🔴 | SDK + Gradle install ✅, died inside `assembleDebug` |
| #4 | `41011189767` | ⚪ | cancelled by the concurrency group (superseded by #5) |
| #5 | `41011278278` | 🔴 | `resources` ✅ · `test` 🔴 step 6 · `compile` 🔴 step 7 · `release` 🔴 step 7 — root cause **#15 + #16** |
| #6 | `41109024673` | 🔴 | Ran against the **broken flattened upload** (`3fad0b5`): `resources` ✅ but `compile` / `test` / `release` all died — the root `build.gradle.kts` *was* the app module's script. Proof the git cleanup was mandatory, not cosmetic |
| #7 | `41111250516` | 🔴 | **First run on the clean 100-file tree.** `resources` ✅ · `compile` ✅ (**first D8 dexing + packaging ever**) · `test` ✅ (117/117 on a real runner) · `release` 🔴 step 7 → **#33**: R8 missing classes `com.gemalto.jp2.*` (pdfbox's optional JPX codec) |
| #8 | `41115825371` | 🔴 | **R8 passed** — the #33 fix worked. Then the next first-run gate spoke: `lintVitalRelease` (release-only lint) failed with 7 `[FullBackupContent]` errors across the two backup XMLs → **#34** |
| #9 | `41119231916` | 🟢 | **First fully green run — all four jobs.** `bundleRelease` completed end-to-end for the first time ever: R8 ✅ · resource shrinking ✅ · lintVital ✅ · 16 KB zipalign ✅ · 64-bit ✅ · AAB artifact uploaded (54,280,259 B wrapped). **The app is release-proven** |
| #10 | `41124485332` | 🔴 | The cleanup commit's own regression: `resources` ✅ but all three build jobs died on the OcrScreen "warning fix" — `LocalClipboard` is an API migration, not a rename → **#36** |
| #11 | `41184706523` | 🟢 | **Second fully green run.** The #36 hotfix verified end-to-end; the release gates re-proven after the Room removal. Two consecutive full-green runs (#9, #11) — the code side is stable |
| #12 | `41185899132` | 🔴 | Every real gate passed again (16 KB ✅ 64-bit ✅ AAB 53M built + uploaded) but the new bundletool step died in one second — its flags were written from memory → **#37** |
| **#13** | `41187145733` | 🟢 | **4/4 jobs, 12/12 release steps.** The bundletool step ran clean and published its number as a `::notice::` — **per-device download = 24,227,813 B ≈ 23.1 MB** (arm64-v8a/en-IN/420dpi; AAB upload 54,145,529 B). Recorded in SPEC §size-budget |

GitHub's REST API exposes run / job / step *conclusions* publicly, but **log text needs a
signed-in session** (401 otherwise). That is why every fix since run #3 was reproduced
with a local Gradle install instead (§7.2). Do not burn a round trying to read CI logs
anonymously — install the toolchain and run the task.

**Failures that are still plausible, and where each is answered:**
1. `Missing class detected while running R8` (**`release` job only**) → add the named
   package as `-dontwarn` in `app/proguard-rules.pro`. Nothing references `okhttp3` any
   more, because Coil was removed (**#29**); pdfbox already has `-dontwarn java.awt.**`.
2. `Execution failed for task ':app:processDebugGoogleServices'` → the workflow writes a
   placeholder `google-services.json`; if this fires, that heredoc broke.
3. Compose/BOM version drift → edit `gradle/libs.versions.toml` **only**, never a
   `build.gradle.kts`.
4. `OutOfMemoryError` → `org.gradle.jvmargs` (CI sets `-Xmx3g`; the sandbox needs
   `-Xmx1000m --max-workers=1`, see §7.2).

### 7.2 Local checks — two linters (no SDK needed) and the real Gradle build

**Resource lint (~10 s, Python 3 stdlib only):**
```bash
python3 tools/check_resources.py
```
Exit 0 = clean. It was **verified with a negative control** — an injected duplicate name,
a broken locale parity and two banned strings were all caught with correct line numbers —
so a green run means something. Run it before every commit.

**Wiring audit (~0.4 s, Python 3 stdlib only):**
```bash
python3 tools/check_wiring.py
```
The resource linter catches problems **inside** a file; this one catches problems
**between** files — the class that produced bugs #6 and #7, which both compiled perfectly
because each piece was individually correct and only the *connection* was missing. It checks:

| Check | Catches |
|---|---|
| HomeScreen tool ↔ RemoteFlags defaults parity | A tool id missing from the defaults map makes `getBoolean()` return `false` and the tool **silently vanishes** from the home screen |
| Navigation route integrity | A route referenced but never declared; a route declared but never `composable()`-routed (a dead button) |
| `Prefs` mutators with zero callers | **Bug #6 exactly** — a setter nobody calls means the state is never written |
| The double-ad trap | A feature that exports through `ExportFlow` (which already calls `afterExportCompleted`) **and** calls it from its Screen too |
| The inverse | A feature that hand-rolls an export but never triggers an ad decision (**bug #11**) |
| `showInterstitial` outside `AdCoordinator` | Bypassing `AdPolicy` — how a paying user sees a forced ad |
| `Purchases.sharedInstance` outside `monetization/billing` | Scattering the RevenueCat surface, which turns an API migration into a 20-file change |
| Hardcoded prices in `feature/` or `monetization/` | Prices must come from `storeProduct.price.formatted` |
| `treeUri = null` at a call site | **Bug #7 exactly** |
| Remote Config knobs nobody reads | A Firebase key the owner would configure for **no effect** |
| Version coordinates outside `libs.versions.toml` | Version drift becoming invisible |

**Deliberate parking is explicit.** `PARKED` at the top of the file lists items that are
dead *on purpose* with a reason, and the audit reports them every run. If a parked item
gains a caller, the audit warns that the entry is now obsolete. Without this, a lint that
cannot tell "parked" from "broken" gets ignored — and then it catches nothing.

**Both linters were negative-control verified.** Seven violations were injected into the
real sources — a missing tool flag, an undeclared route, a direct `showInterstitial`,
RevenueCat touched outside billing, a hardcoded price, a new zero-caller `Prefs` mutator,
and a caller added to a parked item — plus a **block comment that merely mentions**
`Purchases.sharedInstance` and `showInterstitial()` as a decoy. All seven violations were
caught with correct file:line, the decoy was **not** flagged (comment masking works), and
restoring the sources returned exit 0. A green run therefore means something.

⚠️ **YAML trap found while wiring this in:** a multi-line `run:` written as a *plain scalar
continuation* parses as valid YAML but joins the lines with a space, so the shell ran
`python3 tools/check_resources.py python3 tools/check_wiring.py` — the second script became
an **argument** to the first and silently never executed. CI would have stayed green while
doing nothing. It must be a block scalar: `run: |`. **Always print the parsed value back and
check the line count**, not just that `yaml.safe_load` succeeded.

**The real check: Gradle.** Since 2026-10-30 the authoritative local gate is an actual
Android build, not a harness. Everything below was verified to work inside a **2 GB**
sandbox with no Android Studio — if it runs there, it runs anywhere.

Toolchain (all free, no credit card, ≈2.5 GB of downloads). Install it **outside** the
repo — `~/.cache/tc/` is excluded from workspace snapshots, so a fresh session must
re-download it:

| what | where from |
|---|---|
| JDK 17 | `api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse` |
| Gradle **9.3.1** | `services.gradle.org/distributions/gradle-9.3.1-bin.zip` |
| cmdline-tools | `dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip` |
| SDK packages | `sdkmanager --licenses`, then `"platform-tools" "platforms;android-37.0" "build-tools;36.0.0"` |
| kotlinc 2.3.21 *(only for the fast logic harness below)* | `github.com/JetBrains/kotlin/releases/download/v2.3.21/kotlin-compiler-2.3.21.zip` |

Then, from the repo root — **this exact command reproduced both green results**:
```bash
JAVA_HOME=~/.cache/tc/jdk17 ANDROID_HOME=~/.cache/tc/sdk ANDROID_SDK_ROOT=~/.cache/tc/sdk \
PATH=~/.cache/tc/jdk17/bin:$PATH ~/.cache/tc/gradle-9.3.1/bin/gradle \
  --no-daemon -g ~/.cache/tc/gh --max-workers=1 \
  -Dorg.gradle.jvmargs="-Xmx1000m -Dfile.encoding=UTF-8" \
  :app:compileDebugKotlin :app:testDebugUnitTest
```
* `--no-daemon` still forks one single-use daemon, and `-g` gives it a **private**
  GRADLE_USER_HOME so the project's own `gradle.properties` (which asks for `-Xmx3072m`)
  cannot kill it.
* `--max-workers=1` plus a deliberately **small** heap is what fits in 2 GB. On a normal
  machine drop both.
* **Never run `assembleDebug` or `bundleRelease` in a 2 GB sandbox.** D8 gets OOM-killed at
  `dexBuilderDebug` and Gradle reports only *"Gradle build daemon disappeared unexpectedly"*
  — which reads exactly like a project failure and is not one. `testDebugUnitTest` skips
  dexing entirely, which is why it can run here.
* Proof the tests really ran: parse `app/build/test-results/testDebugUnitTest/*.xml`, do not
  trust the console summary alone — AdPolicy 21 · LayoutMath 42 · IdPhotoMath 31 ·
  RewardGuard 11 · QualitySearch 9 · CollageTemplateContract 3 = **117**.

**Fast logic harness (optional, ≈5 s).** To iterate on a single pure file, `kotlinc` over a
scratch dir with hand-written stubs is ~100× faster than Gradle (`/tmp/rf` was used for
`RemoteFlags.kt`). Keep it for that purpose only.

> ⚠️ **Bug #28 is the price of confusing the two.** The 117-test harness "passed" for weeks
> while `AdPolicyTest.kt` did not compile *at all*: its fakes subclassed `final` classes
> whose constructors need a `Context` / `FirebaseRemoteConfig`, and overrode `final` members.
> The harness had substituted its own stubs, so the real classpath was never involved.
> **A stub harness proves logic; only Gradle proves compilation.** End every session with
> the command above.

Harness ground rules (still true): only **pure** files can go in it — anything importing
`android.graphics.*` cannot, which is exactly why `QualitySearch.kt` and `LayoutMath.kt`
were extracted (§5.5); when you add a test class you must register it in `Runner.kt` or it
silently never runs; keep test-source strings ASCII — an emoji once produced
`error: unsupported [literal prefixes and suffixes]`.

### 7.3 Local build with Android Studio

`README-BUILD.md` covers it. Note **`gradlew`, `gradlew.bat` and `gradle-wrapper.jar` are
absent** from this repo — the `.jar` is binary and could not be written here. Android
Studio's Gradle sync (or `gradle wrapper`) generates them. CI does not need them.

### 7.4 RevenueCat billing — RESOLVED and verified against the real artifact

On 2026-10-30 the actual `purchases:6.5.2` AAR **and its sources jar** were downloaded
from Maven Central and inspected, so this is no longer a guess. Facts:

| Question | Answer from the artifact |
|---|---|
| Do the `*With` extensions exist in 6.5.2? | **Yes** — `listenerConversions.kt`: `fun Purchases.getOfferingsWith(onError: (PurchasesError) -> Unit = stub, onSuccess: (Offerings) -> Unit)` and `fun Purchases.purchaseWith(purchaseParams: PurchaseParams, onError: (PurchasesError, Boolean) -> Unit = stub, onSuccess: (StoreTransaction?, CustomerInfo) -> Unit)` |
| What are the callback interfaces called? | `ReceiveCustomerInfoCallback` / `ReceiveOfferingsCallback` — **not** `...Listener`. The old `Listener` names are gone |
| Is `updatedCustomerInfoListener = { … }` legal? | **Yes** — `UpdatedCustomerInfoListener` is a **Java** interface with one method, so Kotlin SAM-converts the lambda |
| Which style does each of our files use? | `PurchaseFlow.kt` uses `*With`; `EntitlementManager.kt` uses callbacks. Both are now **correct against 6.5.2**, so the two styles coexist on purpose and neither needs rewriting |

Three compile errors this inspection caught and fixed (workspace, not yet committed):
1. `EntitlementManager` imported `ReceiveCustomerInfoListener` → renamed to
   `ReceiveCustomerInfoCallback` (import + 2 anonymous objects)
2. `PurchaseFlow.purchaseWith(onError = { e -> … })` — the real `onError` takes
   **two** parameters `(error, userCancelled)`; a one-parameter lambda does not compile
3. `PurchaseFlow.purchaseWith(onSuccess = { (_, customerInfo) -> … })` — that is
   destructuring one parameter, but `onSuccess` has **two**; now `{ _, customerInfo -> }`

`SubjectSegmentationEngine.kt`'s three ML Kit imports were checked the same way against
the `play-services-mlkit-subject-segmentation-16.0.0-beta1` AAR: the public package is
`com.google.mlkit.vision.segmentation.subject` and contains exactly
`Subject`, `SubjectSegmentation`, `SubjectSegmentationResult`, `SubjectSegmenter`,
`SubjectSegmenterOptions`. **All three of our imports match.** The remaining risk there
is the `com.google.android.gms.common.moduleinstall.*` imports, which live in a
different artifact and are still unverified.

**If billing ever misbehaves again, re-run this check instead of guessing:** download
`purchases-<version>-sources.jar` from Maven Central and read the signature. Training
data mixes three API generations; the jar does not.

## 8. Bug ledger — 36 found, 35 fixed, 1 parked

Full write-ups with root cause and the locking test are in `docs/DECISIONS.md` §6.
Read them: **each one is a class of mistake that will recur.**

**How they were found — this is the point of the table.** 5 by unit tests, 3 by the resource
linter or by reading, **4 by the wiring audit**, 2 by CI runs, **15 by pointing a real
Android toolchain at the code** (six compile rounds, `javap` on the downloaded AARs, and one
real `testDebugUnitTest`), and **3 during the P1 `ExportFlow` refactor** — two by reading the
code that was being moved, one by the compiler. None of the wiring bugs could have been
caught by a compiler — every individual piece compiled fine, only the *connection* was
missing. None of **#15–#29** could have been caught by reading: every one of them was a
plausible-looking API that does not exist, or a dependency that does exist but is never
called. And **#30–#31** were invisible to both — they surfaced only when somebody had to
*move* that code, and therefore read every line of it.

| # | Bug | How it was found | The lesson |
|---|---|---|---|
| 1 | `RewardGuard.canGrant(-1)` returned `true` → unlimited rewards on a corrupted/clock-rolled-back counter | **Unit test** | Cap checks must be `x in 0 until cap`, never `x < cap` |
| 2 | `IdPhotoMath.checkBackground` gated unconditionally on `saturation <= 0.18f` → rejected every RED (Indonesia) and BLUE (Malaysia) background | **Unit test** | Test with real-world data, not just the happy case |
| 3 | `IdPhotoViewModel.buildPrintSheet` tried one orientation → 6 copies instead of 8 on a 4×6 sheet | **Unit test** | Try both orientations |
| 4 | `LayoutMath.computeThresholds()` put the gate **ABOVE** the observed border luminance → White Background whitened **zero pixels while reporting success** | **Unit test** | A silent-success bug. No crash, no error. Only running it revealed it |
| 5 | `floodWhiten(px,w,h,low,high,…)` **accepted `high` and never used it** — the "hysteresis" in its own docstring did not exist | **Reading, after #4** | An unused parameter is a lie in the signature |
| 6 | `Prefs.setOnboardingDone()` had **ZERO callers** → `AdPolicy` always returned `never_during_onboarding` → **no ad could ever show on any device** | **Wiring grep** | Would have masqueraded as "AdMob isn't filling". Grep for callers of every state-mutating setter |
| 7 | `BatchScreen` passed `treeUri = null` → exports went to `cacheDir`, wiped under storage pressure and un-browsable | **Wiring grep** | Grep every null-defaulted parameter at its call sites |
| 8 | `<string name="cancel">` declared **twice** in BOTH `strings.xml` files (EN L54+L199, HI L40+L163), with **two different Hindi translations** | **Resource linter** | `aapt2` fails deep inside a ~10-min Gradle run without naming the line. Count `grep -c` lines **AND** unique names — 140 lines / 139 unique was the tell |
| 9 | **`paywall_after_screens` is dead at both ends** — nothing calls `Prefs.incrementPaywallScreens()`, nothing reads the flag, and the key is missing from the defaults map | **Wiring audit** | **PARKED, not fixed.** Harmless in code, but the owner would configure the Firebase knob for no effect. Wire it or delete it before launch (§6 P2) |
| 10 | The Batch **"Remove location and camera data" switch was inert.** `Bitmap.compress()` cannot carry EXIF, so exports were *always* stripped — the OFF position promised to keep data the app never keeps | **Wiring audit** | A UI control that cannot do what its label says is a Play Deceptive-Behaviour exposure even when it fails in the safe direction. Replaced with honest static text; deleted the dead `STRIP_EXIF` pref |
| 11 | **M8 PDF produced no ad decision at all.** `PdfScreen` received `adsReady` from `Nav.kt` and never read it — the only one of the eight tools with no post-save ad hook | **Wiring audit** | An unused parameter *that is already plumbed in* is the same tell as an unused variable. Now wired, and the linter asserts every exporting tool has an ad decision |
| 12 | `AdPolicy.canShowInterstitialPure()` accepted `dailyCap` and **ignored it** — a caller passing `dailyCap = 0` expected ads off and silently got ads | **Compiler warning** | Behaviour was correct (the cap is enforced in the impure wrapper) but the signature lied. Parameter removed, verified by re-running all 117 tests. Same shape as bug #5 |
| 13 | **CI assumed `sdkmanager` was on the runner's PATH.** GitHub's Ubuntu images ship the Android SDK at `/usr/local/lib/android/sdk` but no longer export `sdkmanager` ([runner-images#13674](https://github.com/actions/runner-images/issues/13674)), and Google deleted the legacy `tools/` directory — so **workflow run #1 died in 0 seconds** at the SDK step, before a single Kotlin file was touched | **CI run #1** | The step now resolves `cmdline-tools/latest/bin/sdkmanager`, falls back to downloading cmdline-tools from `dl.google.com` if a future image drops it, exports `ANDROID_HOME`, and on failure prints a directory listing instead of dying silently. **Lesson: a step that fails in 0 seconds means the tool was not found — it says nothing about your code** |
| 14 | **`kotlinOptions { jvmTarget = "17" }` is a hard error in Kotlin 2.3.** It was a deprecation warning for two years and became an error, so the *build script itself* failed to compile and nothing else ran — run #2 reported exactly "1 error" and never reached a single `.kt` file | **CI run #2 log** | Migrated to `kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }` with the `org.jetbrains.kotlin.gradle.dsl.JvmTarget` import. **Lesson: a "1 error" build that dies in under a minute is usually the build script, not the sources — read the `* Where:` block first** |
| 15 | **Firebase `-ktx` artifacts do not exist under BOM 35.1.0.** Gradle said `Could not find com.google.firebase:firebase-analytics-ktx:` — note the *trailing colon and no version*: the BOM simply does not manage that coordinate. `-ktx` metadata stops at 22.5.0; BOM 35.1.0 manages analytics 24.0.0, config 24.0.1, crashlytics 21.0.1 | **Compile round #1** | When a dependency error shows no version, the BOM is the suspect, not Maven. `curl` the BOM's `.pom` and grep it — 30 s instead of a guessing loop |
| 16 | **`checkDebugAarMetadata` failed with 36 issues.** The 2026 AndroidX generation (Compose 1.12.1, core-ktx 1.19.1, navigation 2.10.2, lifecycle 2.11.0) declares `minCompileSdk=37` **and** `minAndroidGradlePluginVersion=9.1.0` in its AAR metadata; the project sat on compileSdk 36 + AGP 8.13.2 | **Compile round #2** | Never fix this by downgrading one library — the next one fails the same check. Move the toolchain: AGP **9.1.1**, Gradle **9.3.1**, compileSdk **37**, Build-Tools **36.0.0**, and `targetSdk` stays **36** (that is what Play enforces) |
| 17 | **`org.jetbrains.kotlin.android` is incompatible with AGP 9**, which has *built-in* Kotlin. Applying both is an error; so is the old variant API | **Compile round #3** | Deleted the plugin from `app/build.gradle.kts`. KGP 2.3.21 now arrives via a root `buildscript { classpath }` override, and `kotlinOptions` became `kotlin { compilerOptions { … } }`. Do **not** paper over it with `android.builtInKotlin=false` — that escape hatch is removed in AGP 10 |
| 18 | **69 errors at once, every RevenueCat type unresolved** → wrong coordinate. `purchases-store-google` has no `Purchases`, no `PurchasesConfiguration`, no `*With` extensions, and `purchases-ui-ktx` is **empty** | **Compile round #4** | When *every* type from one library is unresolved it is an artifact problem, not a code problem. The umbrella **`com.revenuecat.purchases:purchases:6.5.2`** (142 classes) is the only artifact carrying the API we call |
| 19 | **The `ModuleInstall` API was invented from memory** — 11 errors. There is no `registerListener`; `installModules` returns `Task<ModuleInstallResponse>`; and the whole `com.google.android.gms.common.moduleinstall` package (47 classes) ships **inside `play-services-base`** | **Compile round #4** | `javap` the AAR you actually downloaded (`~/.cache/tc/gh/caches/modules-2/files-2.1/`) instead of recalling it. Also: a second-order message like *"expected ModuleInstallRequest"* names the **expected** type — that is the useful half of the sentence |
| 20 | **`PAYWALL_AFTER_SCREONS`** — a plain typo in the *usages*; the declaration was spelled correctly. Grepping for the correct spelling silently skips every typo'd site, so the hunt went down homoglyph / encoding / blank-line-formatting dead ends | **Compile round #5** | When a symbol is "declared but unresolved", hex-dump **both** the declaration and the usages and count the identifier bytes. Fixed with a byte-level replace plus a re-count assertion — retyping it by hand reproduced the typo twice |
| 21 | `Icons.AutoMirrored.Filled.ArrowBack` written as a **fully-qualified receiver chain** cannot resolve — it is an extension property, and it lives in `material-icons-core`, not `-extended` | **Compile round #5** | Extension properties need an `import`, full stop. Same round: `LocalActivity` → `androidx.activity.compose`; `TextButton` is compiled into material3's `ButtonKt` |
| 22 | `android.graphics.Color` and `androidx.compose.ui.graphics.Color` collide in `CollageScreen`; the state fields hold an **Int** | **Compile round #5** | Compare and assign via `toArgb()`, and write `Color.White` / `Color.Black` in Compose. The *same* identifier is correct in `CollageOps` and wrong on a screen |
| 23 | **Three background-colour swatches in Collage were inert** — no `clickable`, so tapping them did nothing at all | Found while fixing #22 | Same class as #10: an unresponsive control is a Play Deceptive-Behaviour exposure. No compiler will ever flag a missing click handler — only a wiring audit or a finger will |
| 24 | `EnhanceOps.adjust()` was called **positionally** by `EnhanceViewModel`, so values landed in the wrong slots (the 5th parameter is `gamma`); and the parameter `denoise: Int` shadowed the object's own `fun denoise(...)` | **Compile round #5** | Call with **named arguments**, and inside an object qualify the call: `EnhanceOps.denoise(cur, threshold)`. The colour loop moved verbatim into `private fun adjustColour(...)`, the order became denoise → colour → sharpen, and intermediates are recycled only when `!== src` |
| 25 | ML Kit's `Face` has **no `UNCOMPUTED_PROBABILITY`** constant. `getSmilingProbability()` and friends return a nullable `java.lang.Float`, where `null` already *means* not computed | `javap` on `play-services-mlkit-face-detection-17.1.0.aar` | Deleted the three `.takeIf { it != Face.UNCOMPUTED_PROBABILITY }` guards and passed `Float?` straight to `FaceGeometry`. Note `com.google.mlkit:face-detection:16.1.7` is a **stub** — the real classes ship in the play-services AAR |
| 26 | `EntitlementInfo.getExpirationDate()` returns a nullable `java.util.Date`, and Kotlin refuses to smart-cast a **cross-module** platform type | **Compile round #5** | Bind it to a local first: `val expiry = e?.expirationDate`, then `isActive && expiry != null && isWithinTrialWindow(expiry.time)` |
| 27 | AdMob's `onDismissed` / `onEarned` are plain `() -> Unit` but our handlers needed `suspend` writes. Same round: `Package.getProduct()`, `StoreProduct.price.amountMicros`, and `PurchasesErrorCode.OperationAlreadyInProgressError` were each slightly off | **Compile round #5** | A singleton-owned `callbackScope` (`SupervisorJob() + Dispatchers.Main.immediate`, deliberately never cancelled) with `try { … } finally { callback() }`. RevenueCat has **no** `PurchaseInProgressError` — 27 codes, and guessing one costs a compile round |
| 28 | **The 117 unit tests had never been through a real compile.** `AdPolicyTest.kt`'s fakes subclassed the `final` classes `EntitlementManager` / `RemoteFlags`, whose constructors need a `Context` / `FirebaseRemoteConfig`, and overrode `final` members → **11 errors** on the first genuine `testDebugUnitTest` | **Real Gradle** | A standalone `kotlinc` harness with hand-written stubs proves *logic*, never *compilation*. Fix: narrow seam interfaces — `EntitlementSource { snapshot() }` and `FlagSource { … }` — beside the existing `UsageCounters`, with the real classes implementing them. Not `open` classes, not Mockito |
| 29 | **Coil 3.6.3 was a dependency nobody used.** Every image in the app is painted from a locally-decoded bitmap (`Image(...)` / `BitmapPainter`); the only mention of Coil anywhere was its name in the open-source attribution string | Reading the resolved classpath after green | `coil-core` + `coil-compose` + `okio` were ~2 MB of a ~33 MB AAB budget for zero features. Removed, and the attribution text corrected — claiming a licence for a library you do not ship is its own small lie. **Confirm a dependency is referenced before you pay its size cost** |
| 30 | **Batch never checked the daily export cap.** `BatchViewModel.run()` called `Prefs.noteExport()` on every run — it *counted* exports but nothing ever **gated** on the count. `allowedCount` limits the number of files **per run** (free = 1 + rewarded bonuses), not the number of runs | **Reading the code during the P1 refactor** | A free user could resize unlimited photos all day by running one-file batches — the 3-exports/day cap, the single most important line in the free tier, did not exist for this tool. Worse, it *looked* correct: the counter was being incremented, so any reader assumed something read it. Same shape as #6 (a state write with no matching gate). Now `ExportFlow.runBatch` checks the cap before the first file is written, and returns `NeedsPro` so the Screen shows the paywall |
| 31 | **A failed batch file was reported as a successful export.** `processOne` returns `-1L` when the bitmap cannot be decoded, but the caller only tested for `null` (the exception path) — so `-1` was stored as `done = true, outBytes = -1` and the row rendered `✔ -1 B` | **Reading the code during the P1 refactor** | Two failure channels, one handled. A silent-success bug in the same family as #4: no crash, no error, just a tick next to a photo that was never written and a run total that is wrong. `runBatch` now treats any negative return as a failure |
| 32 | **A trailing lambda binds to the LAST parameter, not the most important one.** `runBatch(…, write: suspend (Int) -> Long, afterSave: () -> Unit = {})` called with a trailing lambda attached that lambda to `afterSave` → *"No value passed for parameter 'write'"* plus three cascade errors (unknown `index` type, then a bogus *"'operator' modifier is required on compareTo"*) | **Compiler, P1 round 1** | Put the required lambda last even if that means a defaulted parameter comes before it — legal in Kotlin, and it keeps the call site readable. And the standing rule held: fix the FIRST error, the other three were inference fallout |
| 33 | **R8 refused to build the release: pdfbox references an optional JPEG2000 codec it does not ship.** `com.tom_roush.pdfbox.filter.JPXFilter` references `com.gemalto.jp2.JP2Decoder` / `JP2Encoder`; pdfbox-android treats JPX as optional and never bundles the Gemalto codec, so R8's missing-class check failed `minifyReleaseWithR8` — CI run #7, the **first release job ever to execute**. Thirty green local rounds could not see it because debug builds never run R8 | **The CI `release` job, on its first execution** | Fixed with `-dontwarn com.gemalto.jp2.**` in `app/proguard-rules.pro` — honest, not a mask: the classes are absent at runtime too, and `PdfViewModel` already wraps `pdfToImages` in `runCatching` (which catches `Throwable`), so a JPX-encoded page degrades to `Ui.Failed("pdf_render_failed")` instead of crashing. **Every build stage you have never run is a bug category you have never met** — R8 was the last unexecuted stage |
| 34 | **The backup-rule XMLs were invalid against the `FullBackupContent` schema, and their includes pointed at storage that does not exist.** `<exclude domain="cache">` names a domain that is not in the vocabulary (valid: root, file, database, sharedpref, external); every `<exclude>` sat outside the `<include>` set, which is a dead rule the moment any include exists; and the includes named `sharedpref` + `usage.db` while the app actually keeps Prefs *and* every daily counter in one DataStore file (`files/datastore/…`) and ships no database at all. So the old rules backed up nothing and excluded nothing | **The CI `release` job, second consecutive first-run gate** (`lintVitalRelease`, run #8 — release-only lint; debug builds and both project linters cannot see it) | Include-only rules pointing at `datastore/` in both XMLs: with includes present, Auto Backup backs up exactly that and nothing else, so photos / exports / caches stay out *by definition* — SPEC §11's promise holds with fewer rules than before. Same lesson as #33 one gate later: **an unexecuted checker is an unread file** |
| 35 | **Room 2.8.5 was a dependency nobody used.** No `@Database`, `@Dao`, `@Entity` or `databaseBuilder` anywhere in main or test sources, yet `room-runtime`, `room-ktx` and `ksp(room-compiler)` were declared and proguard kept `* extends androidx.room.RoomDatabase` — about a megabyte of AAB and KSP seconds on every build for zero features. The old backup rules even named a `usage.db` that never existed (#34) | **Reading while fixing #34** — a grep for Room annotations returned nothing | Removed the three dependency lines, the KSP line and the dead keep rules. Same class as #29 (Coil): **a dependency you never reference is a tax on every build and every download** |
| 36 | **The deprecation-replacement batch shipped without a compile: `LocalClipboard` is not a drop-in for `LocalClipboardManager`.** compose-ui 1.12's `Clipboard` interface exposes only `suspend getClipEntry()` / `suspend setClipEntry(ClipEntry)`, and `ClipEntry` is constructed from an `android.content.ClipData` — the old one-line `setText(AnnotatedString)` has no non-suspend equivalent, so run #10's compile died in all three build jobs. The deprecation message names the replacement but not the shape change | **CI compile, run #10** — the first red run ever caused by one of my own patches | `rememberCoroutineScope()` + `scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(...))) }`, javap-verified against `ui-android-1.12.1.aar` before shipping, then compile + 117 tests re-proven **locally**. **A deprecation replacement is an API migration, not a rename — without a local compile, javap the AAR first** |

**Four meta-lessons:**
- **A parameter that is accepted and ignored is a bug, not a style issue.** Bugs #5 and #12
  are the same shape. `kotlinc` warns about it — **treat "parameter is never used" as an error.**
- **Bugs 4, 5 and 8 were invisible to reading.** Only execution — a test run, a linter —
  found them. This is why the linter is a CI gate and not a suggestion.
- **The wiring bugs (#6, #7, #9, #10, #11) are all "the connection is missing".** Each file
  was individually correct. This is why `tools/check_wiring.py` exists and is a CI gate: a
  compiler cannot see them, and neither can a careful reader, once the codebase is 9,000
  lines long.
- **A test of mine was also wrong once.** `degenerateGridIsClampedNotCrashed` asserted
  `grid(-3,2).size == 1`; clamping is **per axis**, so 1×2 = 2 cells is correct.
  **When a test fails, check the test's expectation before "fixing" the product.**

---

## 9. Commercial model

Full numbers and sources in `docs/DECISIONS.md` §7. Do not re-litigate without new data.

**Free tier:** 3 exports/day · SD 1080px · watermark · 10 collage templates · batch of 1 ·
10 countries · 5 OCR scans · **unlimited BG removal** · capped ads.
**Pro:** unlimited · HD/4K · batch 100 · all 20 templates · 30+ countries · unlimited OCR ·
**zero forced ads**.

**Pricing:** `pro_weekly` $6.99/₹99 · `pro_yearly` $34.99/₹999 (anchor, default) ·
`pro_lifetime` $59.99/₹1,999 (Day 60+). **Trial ≥7 days** — trial→paid conversion is
37–43% at ≥7 days vs 25.5% at ≤4 days.

**Ad rules (all in `AdPolicy`, all test-locked):** first-ad delay 90 s · gap 180 s ·
daily cap 3 · zero full-screen ads on Day 0–2 · interstitials **only** after an
export/save completes · banners disabled forever · app-open disabled until Day 90 ·
Remote Config default `ads_interstitial_enabled = false`.

**Earnings model** (blended ARPDAU, photo/video utility $0.04–0.09):

| DAU | Tier-1 share | Est. monthly revenue |
|---|---|---|
| 150 | 25% | ₹5,400 |
| 800 | 35% | ₹45,600 |
| 3,000 | 45% | ₹2.28 lakh (top ~17% of apps) |
| 10,000 | 50% | ₹8.7 lakh (top ~4.6%) |

**Geography is the dominant variable.** 3,000 DAU at 0% Tier-1 earns only ₹24,900 — a
**9× gap** from geography alone. Hence launch on r/EtsySellers, r/Flipping, r/eBay,
r/smallbusiness, r/reselling — **not** r/AndroidDev or r/india.

**North Star metric: blended ARPDAU.** Raise ad frequency only if ARPDAU rises **and**
D7 retention drops ≤1pp, over a **7-day minimum** test.
**Guardrails:** D1 >35% · D7 >15% · D30 >8% · trial starts >25% of installs ·
trial→paid >35% · rewarded engagement 15–20% of DAU · crash-free >99.5% ·
refunds <2% · rating >4.3★.

**Reality check:** 83% of Play apps never reach $1K/month in two years. Photo & Video has
the best odds of any category (21.4% → $1K, 7.3% → $10K) but that still means most likely
outcome is modest. Paid UA is loss-making (Android CPI ~$3.40 vs ~$0.34/install earned by
day 60) — organic only.

---

## 10. Document index

| File | For | What it is |
|---|---|---|
| **`README.md`** | **agent + owner** | ⭐ **This file. Start here.** State, rules, architecture, next steps |
| `AGENTS.md` | agent | 9 non-negotiables. Auto-read by several AI tools |
| `HANDOFF.md` | agent | First-session playbook — the *procedure*. Knowledge lives here in §0–§10 |
| `docs/DECISIONS.md` | agent | ⭐ The *rationale*: C1–C7 in full, the 5 corrections, dead ends, verified-vs-assumed, 8 architecture rules, bug write-ups, commercial model |
| `docs/SPEC.md` | agent | The build spec, §0–§17, milestones M0–M12, hard constraints |
| `docs/master-plan-2026.html` | **owner** | The only doc the owner needs to read end to end. Research, market data, 8-week plan, §17.2 = his own five rules |
| `YOUR-TURN.md` | **owner** | ⭐ 20 steps in Hinglish — everything only he can do |
| `README-BUILD.md` | owner + agent | How to build in Android Studio, expected errors, pre-ship edit list, release signing |
| `PUSH-TO-GITHUB.sh` / `.md` | owner | Pushes this repo. Two paths. Aborts if it finds a keystore; never force-pushes |
| `tools/check_resources.py` | agent + CI | Resource lint — problems **inside** a file. No Android SDK. CI job `resources` |
| `tools/check_wiring.py` | agent + CI | ⭐ Wiring audit — problems **between** files. Found bugs #9–#11. No Android SDK. Same CI job |
| `.github/workflows/ci.yml` | CI | 4 jobs — see §7.1 |
| `gradle/libs.versions.toml` | agent | ⭐ Every version, one file, live-Maven verified 2026-10-29 |
| `deliverables/` | owner | privacy-policy.html, app-ads.txt, store-listing.md, data-safety-answers.md, play-console-checklist.md, tester-recruitment.md, launch-reddit-posts.md — all with `[BRACKETED]` placeholders |

### Verified dependency versions

All in `gradle/libs.versions.toml`. Resolved against live Maven on 2026-10-29, then
**actually downloaded and compiled against** on 2026-10-30:

**AGP 9.1.1** · **Kotlin 2.3.21** (via a root `buildscript { classpath }` override) ·
KSP 2.3.12 · **Gradle 9.3.1** · **JDK 17** · **compileSdk 37 (`platforms;android-37.0`)** ·
**Build-Tools 36.0.0** · **targetSdk 36** · **minSdk 24** ·
core-ktx 1.19.1 · activity-compose 1.13.0 · lifecycle 2.11.0 · navigation-compose 2.10.2 ·
**compose-bom 2026.09.00** (Compose 1.12.1; material-icons core **and** extended frozen at
**1.7.8**) · room 2.8.5 · datastore 1.3.0 · documentfile 1.1.0 · exifinterface 1.4.2 ·
mlkit face-detection 16.1.7 (**a stub** — the real classes ship in
**play-services-mlkit-face-detection 17.1.0**) · text-recognition 16.0.1 ·
text-recognition-devanagari 16.0.1 · **play-services-mlkit-subject-segmentation 16.0.0-beta1** ·
play-services-ads 25.5.0 · user-messaging-platform 4.0.0 · **firebase-bom 35.1.0 with the
NON-`-ktx` coordinates** (bug #15) · **revenuecat `com.revenuecat.purchases:purchases:6.5.2`**
— the *umbrella* artifact (bug #18) · pdfbox-android 2.0.27.0 · coroutines 1.11.0 ·
serialization-json 1.12.0 · junit 4.13.2.

Deliberately **dropped**: **Coil 3.6.3** (bug #29 — declared, never used), every Firebase
`-ktx` artifact, Koin, `purchases-ui-ktx` (empty on Maven — the paywall is hand-built),
WorkManager, `androidx.splashscreen`.
⚠️ **androidx artifacts live on `dl.google.com`, not Maven Central.** So does `com.google.android.ump`.
⚠️ `material-icons-extended` needs **no** explicit `material-icons-core` dependency — the
extended `.module` api-depends on core, and core is where `AutoMirrored.Filled.ArrowBack`
lives (extended only has `ArrowBackIos` / `ArrowBackIosNew`). Compile round #6 proved it.

**AGP 9.1.1 requirements, from Google's official AGP 9.0 release notes plus
`kotl.in/gradle/agp-built-in-kotlin`:** Gradle minimum **9.1.0** (9.1.1 defaults to 9.3.1),
Build Tools **36.0.0**, JDK **17**, KGP minimum/default 2.2.10, max API **37.0**, new-DSL
interfaces only (`androidComponents.onVariants` / `beforeVariants` / `sdkComponents` —
`BaseExtension` and the old variant API are gone), **built-in Kotlin** so
`org.jetbrains.kotlin.android` is incompatible, a non-final R class, and `targetSdk`
defaulting to `compileSdk` (we set 36 explicitly). Gradle properties that now **error**:
`android.r8.integratedResourceShrinking`,
`android.experimental.enableNewResourceShrinker.preciseShrinking`; `splits.density` is gone.
These facts were confirmed by six compile rounds, not only by reading.

---

## 11. Working with the owner

- **Reply in Hinglish.** He writes entirely in Hinglish.
- He is **solo, non-programmer, and has no team** — no designers, no QA.
- **Total budget $25.** Never suggest anything paid.
- **Never suggest a service requiring a credit card** (C4).
- He cannot run an emulator or a local Android build without setup — **CI is his build machine.**
- When something is unverified, **say so plainly.** He has been burned twice by
  confident advice that was wrong (§2).
- His own five rules are in `docs/master-plan-2026.html` §17.2: one milestone at a time;
  test every milestone in airplane mode on a mid-range phone; verify Gradle versions
  against Google's official pages; **learn Play Billing himself because it is AI's weakest
  area**; keep AdMob TEST MODE automated via `BuildConfig.DEBUG`.
- **Sequencing he set explicitly:** *"pahale app ko complete karate hai, bad me Play Store
  ke liye integration karenge"* — **finish the app first, store integration afterwards.**
  Do not front-load account/listing work while app features remain unbuilt.

---

*Last verified: 2026-10-30 — by execution, not by reading.*

| Check | Result |
|---|---|
| `:app:compileDebugKotlin` — AGP 9.1.1 / Gradle 9.3.1 / Kotlin 2.3.21 / compileSdk 37 | 🟢 **BUILD SUCCESSFUL** — 0 errors, 6 deprecation warnings (§6 P2). Re-run after the P1 `ExportFlow` refactor: 1 m 57 s, **same six warnings, no new ones** |
| `:app:testDebugUnitTest` — real Gradle, real classpath | 🟢 **117 tests, 0 failures, 0 skipped**, verified by parsing `app/build/test-results/**/*.xml` rather than trusting the console. Re-run after the P1 refactor: still 117 |
| `:app:assembleDebug` | 🔴 **cannot run in the sandbox** — D8 is OOM-killed at `dexBuilderDebug` (2 GB RAM). Everything before dexing passed. This is the CI runner's job |
| Resources · manifests · aapt2 | 🟢 `mergeDebugResources`, all 3 manifest tasks, `processDebugResources`, `checkDebugAarMetadata`, `compileDebugJavaWithJavac`, `desugarDebugFileDependencies` |
| `tools/check_resources.py` | **exit 0** — 55 Kotlin files, 10 resource XMLs scanned |
| `tools/check_wiring.py` | **exit 0** — 0 errors, 0 warnings, 12 notes (3 of them deliberate `PARKED` entries). Previously verified with 7 injected violations, all caught, then reverted |
| Kotlin files in `app/src/main` | 55 files |
| Strings EN ↔ HI | **140 / 140**, unique, identical name sets — plus 22 unreferenced entries (§6 P2) and 29 hardcoded literals the linter cannot see (§6 P2) |
| CI | 4 jobs, YAML-valid via `yaml.safe_load`, `run:` blocks verified as genuinely multi-line |
| Secrets | **none** — no keystore, no `google-services.json`, no `local.properties`, no `.env` |
| Rendered on a real device | **never** — no emulator has ever run anywhere (§4.3) |
