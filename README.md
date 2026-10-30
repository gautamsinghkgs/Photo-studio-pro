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

**The single most important fact:**

> ### ⚠️ This code has NEVER been compiled by an Android toolchain.
> Not one of the 55 Kotlin files in `app/src/main` has been through `javac`/`kotlinc`
> with the Android SDK on the classpath. The authoring sandbox had no Android SDK,
> no Gradle, JDK 11 (AGP 8.13 requires 17), 2 GB RAM, and no `/dev/kvm` for an emulator.
>
> **Compile errors are the EXPECTED state, not a defect.** Do not treat a failing
> `compile` job as evidence the design is wrong. It is evidence that nobody has run
> the compiler yet.

**What 117 passing unit tests do and do not prove.** They prove the *pure logic* is
correct — ad-policy decisions, reward caps, ID-photo geometry, quality search, layout
maths. They were run with kotlinc 1.9.24 + JUnit 4 against the real production sources.
They prove **nothing** about whether the project builds, whether Compose renders,
whether ML Kit's beta API surface matches the imports, or whether RevenueCat's SDK
shape is right. See **§4.3**.

**Do this first:**
1. Read **§2** (constraints) and **§3** (do/don't). Two minutes, prevents the worst mistakes.
2. Check the latest CI run. Work the **`compile`** job.
3. Fix the **FIRST error only**. The following twenty are fallout from it.
4. Commit, let CI re-run, repeat. Expect **3–6 iterations**.

**Do not** start refactoring, adding features, or "cleaning up" before `compile` is green.
A refactor on top of code that has never compiled multiplies the unknowns.

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
| **Min / target / compile SDK** | 24 / 36 / 36 |
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
   NEVER│ Runs on a phone · renders · real   │  ← 0% verified
  DONE  │ model download · real purchase     │
        ├───────────────────────────────────┤
   NEVER│ Compiles with the Android SDK      │  ← 0% verified — THE BLOCKER
  DONE  │ (all 55 files)                     │
        ├───────────────────────────────────┤
  DONE  │ 117 unit tests pass on pure logic  │  ← verified by execution
        │ (kotlinc 1.9.24 + JUnit 4)         │
        ├───────────────────────────────────┤
  DONE  │ Static validation: XML/JSON/YAML   │  ← verified by execution
        │ manifest, resource refs, gradle    │
        ├───────────────────────────────────┤
  DONE  │ Dependency versions, policy facts, │  ← verified against primary
        │ API availability, ad/subscription  │     sources (live Maven, official
        │ economics                          │     docs) on 2026-10-29
        └───────────────────────────────────┘
```

**Specifically unverified — treat as likely-to-break:**
- `SubjectSegmentationEngine.kt` import paths. `play-services-mlkit-subject-segmentation:16.0.0-beta1`
  is the **only version ever published**; beta package paths are stale in training data.
- `PurchaseFlow.kt`. The RevenueCat SDK has changed shape several times
  (callbacks → `awaitX` coroutines → `*With` scope extensions).
- Real model download size and inference speed for Subject Segmentation.
- Real AAB download size — the CI `release` job produces the first measurement.
- Whether Compose BOM `2026.09.00` resolves cleanly with Kotlin 2.3.21 in a real build.
  (AGP 8.13.2's R8 **does** officially support Kotlin 2.3, so the pairing is sound.)

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

### 5.3 `ExportFlow.run()` — the single money path

```kotlin
suspend fun run(
    ctx: Context, activity: Activity?, source: Bitmap, toolId: String, fileName: String,
    format: ExportOps.Format = JPEG, quality: Int = 92,
    treeUri: Uri? = null,          // non-null → SAF folder; null → share sheet
    preEncoded: ByteArray? = null, // ID Photo supplies this to hit its KB ceiling
    afterSave: () -> Unit = {},
): Outcome                          // Saved(uri,bytes,w,h) | NeedsPro | Failed(reason)
```

Sequence, in order — **do not reorder**:
1. Free daily cap → `NeedsPro` if exceeded and not Pro
2. Downscale to `FREE_MAX_EDGE = 1080` **and** apply `ExportOps.watermark("Photo Studio Pro")` if not Pro
3. Encode (or use `preEncoded`)
4. `ExportOps.saveToTree` when `treeUri != null`, else `toShareUri`
5. `prefs.noteExport()` → `usage.refresh()` → `Events.logExportCompleted`
6. Recycle intermediates — **never the caller's `source`**
7. `adCoordinator.afterExportCompleted(activity) { afterSave() }` ← the natural break point

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

### 🔴 P0 — Make it compile. (This is the only thing that matters right now.)

Run the CI `compile` job. Fix the first error. Repeat. Details and the ranked list of
likely errors: **§7.1** and `README-BUILD.md`.

### 🟠 P1 — Refactor 3 tools onto `ExportFlow`

`BgRemoverViewModel.export()`, `IdPhotoViewModel.export()` and `BatchViewModel.run()`
each reimplement the §5.3 sequence by hand. (`PdfViewModel.saveTo()` also has its own path,
but that is legitimate — it writes a `.pdf` document, not an image. Its ad hook **is** now
wired; see bug #11 in §8.)

**What is actually lost today** (not hypothetical):
- BG Remover and ID Photo export **only** to the share sheet — the user cannot pick a
  folder, unlike in Batch / White BG / Collage / Enhance / PDF.
- Each copy is a place where the free-tier cap, watermark or counter can drift.

**⚠️ THE DOUBLE-AD TRAP — read before doing this refactor.**
Those three tools currently call the ad hook **from the Screen**, e.g.
`BgRemoverScreen.kt:146`, `IdPhotoScreen.kt:159` and `:166` (two *different* branches —
single photo vs print sheet, so not a double-fire), `BatchScreen.kt:181`:

```kotlin
vm.export(...) { uri ->
    if (uri != null && adsReady && activity != null) scope.launch {
        ServiceLocator.adCoordinator.afterExportCompleted(activity) { }
    }
}
```

`ExportFlow.run()` **already calls `afterExportCompleted` internally** (step 7). So when
you move a tool onto `ExportFlow`, **you must delete the Screen-level call** in the same
commit. If you forget, two ad decisions fire per export. `AdPolicy`'s 180 s gap would
deny the second with `gap_not_elapsed`, so no ad shows twice — but the `afterSave()`
callback semantics break and the code becomes misleading.

Also note a behavioural difference to reconcile: the Screen-level call is gated on
`adsReady && activity != null`, `ExportFlow`'s only on `activity != null`.

**Do this refactor only after P0 is green.** Never refactor uncompiled code.

### 🟡 P2 — Decisions and placeholders

**One decision an agent CAN make (or must escalate):**

`paywall_after_screens` — wire it or delete it. Wiring it means choosing what counts as a
"screen" (probably each tool opened), where to count it (`Nav.kt`), and a guard so the
paywall does not re-fire on every subsequent screen. It is a real growth lever — the research
in `docs/DECISIONS.md` §7 shows hard paywalls earning **$2.32 D14 RPI vs $0.27** for
freemium, so paywall aggression matters — but it is also a retention risk, and it must stay
remotely controllable and default to "never". **Do not wire it before `compile` is green.**

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

Day 0–30 rewarded + subscriptions only → Day 30–60 interstitials at 1/day **only if**
D7 retention >15% **and** crash-free >99.5% → Day 60–90 lifetime IAP + 2–3/day →
Day 90+ mediation, win-back campaigns, `es` / `pt-BR` localisation.

Also deferred: **AdMob SSV** (Phase 2 — only build it if weekly rewarded impressions ÷
Firebase `reward_granted` exceeds **3.0**; ≈1.0 is healthy, >1.5 investigate).

---

## 7. Build & test

### 7.1 CI — the build machine

`.github/workflows/ci.yml`, **4 jobs**. Gradle 8.13 is installed straight from
`services.gradle.org`, so the missing `gradle-wrapper.jar` does not matter. JDK 17
temurin; `sdkmanager "platforms;android-36" "build-tools;36.0.0"`; a placeholder
`google-services.json` is auto-written.

| Job | Command | What it proves |
|---|---|---|
| **`resources`** | `tools/check_resources.py` **and** `tools/check_wiring.py` | Resource integrity *and* cross-file wiring. **~10 s, no Android SDK. Gates the other three** |
| **`compile`** | `assembleDebug` | Does it build at all. ⭐ **Start here** |
| **`test`** | `testDebugUnitTest` + HTML artifact | Do the 117 tests still pass inside a real Gradle build |
| **`release`** | `bundleRelease` (unsigned) + `zipalign -c -P 16 -v 4` + arm64-v8a check + size report + AAB artifact | The two Play gates that **cannot be fixed after a rejection** (16 KB page size, 64-bit), plus the first real download-size measurement |

`compile` / `test` / `release` run in parallel, each with `needs: resources`.

**Cost:** public repo = unlimited Actions minutes. Private repo = 2,000 min/month
(≈200+ Android builds). Either is free.

**The errors most likely to appear, in order:**
1. `Unresolved reference: getOfferingsWith` / `purchaseWith` → see **§7.4**
2. `Unresolved reference: SubjectSegmenter` → `SubjectSegmentationEngine.kt`; copy imports
   from the **live doc**, `developers.google.com/ml-kit/vision/subject-segmentation/android`
3. `Execution failed for task ':app:processDebugGoogleServices'` → no `google-services.json`
4. Compose/BOM version drift → edit `gradle/libs.versions.toml` only
5. `OutOfMemoryError` → `org.gradle.jvmargs` in `gradle.properties`; do **not** enable
   `org.gradle.parallel` on an 8 GB machine

### 7.2 Local checks that need NO Android SDK

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

**Unit-test harness.** The 117 tests were run with kotlinc 1.9.24 + JUnit 4 against the
real sources, in a scratch dir outside the repo (`/tmp/kt3`, not persisted). To rebuild it:
```bash
# download kotlin-compiler-1.9.24.zip (91 MB) from
# github.com/JetBrains/kotlin/releases/download/v1.9.24/kotlin-compiler-1.9.24.zip
# copy QualitySearch.kt + LayoutMath.kt into src/main, stubs into src/stub,
# tests into src/test, register every class in Runner.kt, then:
java -cp out:lib/junit-4.13.2.jar:lib/hamcrest-core-1.3.jar:kotlinc/lib/kotlin-stdlib.jar RunnerKt
```
⚠️ Only **pure** files can go in the harness. Anything importing `android.graphics.*`
cannot be compiled this way — which is exactly why `QualitySearch.kt` and `LayoutMath.kt`
were extracted (§5.5). **When you add a test class, you must register it in `Runner.kt`**
or it silently never runs.

kotlinc 1.9.24 works on JRE 11. Keep test-source strings ASCII — an emoji once produced
`error: unsupported [literal prefixes and suffixes]`.

### 7.3 Local build with Android Studio

`README-BUILD.md` covers it. Note **`gradlew`, `gradlew.bat` and `gradle-wrapper.jar` are
absent** from this repo — the `.jar` is binary and could not be written here. Android
Studio's Gradle sync (or `gradle wrapper`) generates them. CI does not need them.

### 7.4 ⚠️ The RevenueCat API-style split — read before touching billing

**This is a live inconsistency and a near-certain compile error.** Three files assume
**two different** RevenueCat API shapes:

| File | Style used |
|---|---|
| `monetization/billing/PurchaseFlow.kt` | coroutine scope extensions — `getOfferingsWith`, `purchaseWith` |
| `monetization/billing/EntitlementManager.kt` | **callbacks** — `ReceiveCustomerInfoListener`, `updatedCustomerInfoListener` |
| `feature/paywall/PaywallScreen.kt` (comment block, ~L234) | **callbacks** — `getOfferings { offerings, error -> }` |

So the `HANDOFF`-era advice *"fix only `PurchaseFlow.kt`"* was **too narrow**. Whichever
style the real SDK 6.5.2 exposes, **both billing files must converge on it**:

- If callbacks compile → rewrite `PurchaseFlow.kt` to callbacks.
- If `*With` compiles → rewrite `EntitlementManager.kt`'s `refresh()` and
  `restorePurchases()` to the coroutine style.

Check `revenuecat.com/docs/getting-started/entitlements` for the **current** shape before
choosing. The SDK has moved callbacks → `awaitX` coroutines → `*With` extensions, and
training data mixes all three.

**Also false, and worth correcting:** the doc comment at `PurchaseFlow.kt:26` claims
*"Nothing else in the app touches `Purchases.sharedInstance`"*. `EntitlementManager`
does, in three places. Fix the comment when you fix the API style.

**This purchase flow has never run against a real offering.** Until the owner has
RevenueCat configured with real products and Licence Testing ON (§6 P2/P3), no agent can
verify it. Do not claim it works.

---

## 8. Bug ledger — 12 found, 11 fixed, 1 parked

Full write-ups with root cause and the locking test are in `docs/DECISIONS.md` §6.
Read them: **each one is a class of mistake that will recur.**

**How they were found — this is the point of the table.** 5 by unit tests, 3 by the resource
linter or by reading, and **4 by the wiring audit**. None of the wiring bugs could have been
caught by a compiler, because every individual piece compiled fine.

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

All in `gradle/libs.versions.toml`, resolved against live Maven on 2026-10-29:

**AGP 8.13.2** · **Kotlin 2.3.21** · KSP 2.3.12 · **Gradle 8.13** · **JDK 17** ·
core-ktx 1.19.1 · activity-compose 1.13.0 · lifecycle 2.11.0 · navigation-compose 2.10.2 ·
**compose-bom 2026.09.00** · room 2.8.5 · datastore 1.3.0 · documentfile 1.1.0 ·
exifinterface 1.4.2 · mlkit face-detection 16.1.7 · text-recognition 16.0.1 ·
text-recognition-devanagari 16.0.1 · **play-services-mlkit-subject-segmentation 16.0.0-beta1** ·
play-services-ads 25.5.0 · user-messaging-platform 4.0.0 · firebase-bom 35.1.0 ·
**revenuecat purchases-store-google 6.5.2** · **coil 3.6.3 (`io.coil-kt.coil3`)** ·
pdfbox-android 2.0.27.0 · coroutines 1.11.0 · serialization-json 1.12.0 · junit 4.13.2 ·
material-icons-extended (via compose-bom).

Deliberately **dropped**: Koin, `purchases-ui-ktx` (returned empty on Maven — the paywall
is hand-built), WorkManager, `androidx.splashscreen`.
⚠️ **androidx artifacts live on `dl.google.com`, not Maven Central.** So does `com.google.android.ump`.

**AGP 8.13.2 compatibility, from Google's official release notes:** Gradle minimum 8.13,
SDK Build Tools 35.0.0, JDK 17, max API 36.1, and *"AGP 8.13.2 uses R8 8.13.19 which
supports Kotlin 2.3"* — which is what validates the `AGP 8.13.2 + Kotlin 2.3.21` pairing.
(A GP **9.4.0** notes page needs Gradle 9.6.0 — that table does **not** apply here.)

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
| Unit tests (kotlinc 1.9.24 + JUnit 4, real sources) | **117 / 117 pass**, 73 ms |
| `tools/check_resources.py` | **exit 0** |
| `tools/check_wiring.py` | **exit 0** — after 7 injected violations were all caught, then reverted |
| Kotlin files in `app/src/main` | 55 files, 7,881 lines |
| Strings EN ↔ HI | **140 / 140**, unique, identical name sets |
| CI | 4 jobs, YAML-valid, `run:` blocks verified as genuinely multi-line |
| Secrets | **none** — no keystore, no `google-services.json`, no `local.properties`, no `.env` |
| Compiled by an Android toolchain | **never** — see §0 |
