# Locked decisions and constraints — READ BEFORE CHANGING ANYTHING

This file exists so that a fresh agent, or a fresh chat session, does not undo ten
rounds of research. Everything below was decided deliberately, and several entries
are **corrections of earlier advice that turned out to be wrong**. If you are about
to suggest something that contradicts this file, stop and say why.

---

## 1. Hard constraints from the owner (non-negotiable)

| # | Constraint | Consequence if violated |
|---|---|---|
| **C1** | **No AI APIs of any kind.** No Gemini, OpenAI, Replicate, fal.ai, Stability, no cloud inference endpoint — not even "for just one premium feature". Owner stated this explicitly. | Recurring per-call cost breaks the $0/month budget, and it destroys the "your photos never leave your phone" USP which is the only structural advantage this app has |
| **C2** | **Zero backend written by the owner.** No Cloud Functions, no Workers code, no API server, no database he maintains. | He is solo with no ops experience. Anything needing a server is unmaintainable for him |
| **C3** | **Total budget $25, one time.** That is the Play registration fee. Everything else $0/month. | — |
| **C4** | **Never sign up for a service that requires a credit card.** | Firebase Blaze, Fly.io, and anything with "no hard spending cap" are banned for this reason |
| **C5** | **No dishonest stubs.** No "Coming Soon" buttons, no hidden features behind a paywall that do not exist, no placeholder screens. | Play rejects on misleading metadata; a visible button that does nothing is the fastest route to 1-star reviews |
| **C6** | **All development is done by AI.** Owner has little/no coding experience. Recommendations must be AI-buildable and simple — no DI framework, no microservices, no exotic build setup | He cannot debug sophistication |
| **C7** | **Hybrid monetization: ads AND subscription.** Not ads-only, not one-time-IAP-only. | This is why the product is an 8-tool toolkit rather than a single-purpose utility: a single tool has no daily sessions and no natural break points for ads |

---

## 2. Corrections — earlier advice that was WRONG

Do not re-introduce any of these. Each one was verified against a primary source.

### 2.1 ML Kit Subject Segmentation has NO bundled variant
Earlier rounds said "use the bundled variant for a 100% offline USP, ~8 MB".
**That artifact does not exist.** Verified 2026-10-29 against Maven Central, Google's
`com/google/mlkit/group-index.xml`, and the official doc
(`developers.google.com/ml-kit/vision/subject-segmentation/android`, updated 2026-10-28):

- Only `com.google.android.gms:play-services-mlkit-subject-segmentation:16.0.0-beta1`
  was ever published. **`beta1` is the only version that exists.**
- App-size impact ~200 KB; the model is delivered via Google Play services.
- *"Requests you make before the download has completed **produce no results**."*
  That is a silent empty output, **not a crash** — hence the explicit
  `READY / DOWNLOADING / NEEDS_NETWORK / UNSUPPORTED` state machine in
  `SubjectSegmentationEngine.kt`.
- **Selfie Segmentation IS bundled (`16.0.0-beta6`) but segments PEOPLE only.** It
  cannot substitute for product photos (handbags, shoes, bottles). Do not swap it in.

Consequences already applied: APK estimate **~41 MB → ~33 MB**, and the "100% offline"
pitch is retired (see 2.2).

### 2.2 "100% OFFLINE" is a false claim — never write it
Background Remover downloads its model on first run. Claiming otherwise is exactly the
misleading-metadata pattern Play's Deceptive Behavior clause targets, and that clause is
used to sweep apps **retroactively**.

- ✅ *"Your photos never leave your phone"* — always true. The model comes down; photos never go up.
- ✅ *"Works offline after one-time setup"*
- ❌ ~~"100% offline"~~, ~~"No internet needed"~~

The other 7 tools ARE genuinely offline from first run (Face Detection 16.1.7 and Text
Recognition 16.0.1 are both bundled).

### 2.3 AdMob Server-Side Verification is NOT required at launch
An earlier round called a Cloudflare Worker SSV endpoint "the ONE thing you need".
**Retracted.** AdMob's own help docs (9603226 / 9311747) say SSV is *"used **in addition
to** the standard client-side callback"*, that *"there is no retry logic from our SDK to
ping the server"*, and that *"callbacks fire only for production ads"* — so it is
**untestable during closed testing**. `onUserEarnedReward` alone grants the reward.

At ~150 DAU the abuse loss is roughly ₹50–100/month; building SSV costs 2–4 days. The
trade is deliberate. Replaced by client-side `RewardGuard` plus an analytics-only abuse
detector (compare AdMob rewarded impressions A vs Firebase `reward_granted` B weekly;
investigate at B/A > 1.5, build SSV only at **B/A > 3.0**).

### 2.4 `app-ads.txt` is FREE
An earlier round said to skip it because it needs a ~₹800/year domain. **Wrong.** Host it
at `https://<project>.pages.dev/app-ads.txt` and use that same URL as the Play Console
developer website. Missing it can cut eCPM 10–30%.

### 2.5 App naming
"AI Photo Studio" and every `AI Enhance` / `AI Restore` / `AI Generator` / `AI Avatar` /
`AI Colorize` / `AI Retouch` / `AI Beauty` label are **retired**. Play names *"AI photo
enhancers"* as facing immediate rejection without a native generative-AI report control.
The classical sharpen/denoise tool is called **"Photo Enhance"** and the UI states plainly
that it is not AI. Enforced as comment headers in both `strings.xml` files.

---

## 3. Permanent dead ends — do not propose these

Cloud AI or generative APIs of any kind · AI background generation · Remini-grade
restoration or colourisation · avatars · text-to-image · style transfer · magic-eraser
inpainting · AI super-resolution (v1) · **universal APK** (~78–99 MB across ABIs — AAB is
mandatory) · **banner ads** (India eCPM ~$0.08–0.40, and an accidental click near a bottom
nav bar is a real AdMob account-ban risk) · **app-open ads** at launch · user
accounts/login/cloud sync · Firebase **Blaze** · Cloud Functions · Cloud Storage · any
server code of his own · paid UA, paid testers, paid ASO · **Selfie Segmentation as a
product-photo substitute**

Also dead as of 2026 (do not recommend): Vercel Hobby (commercial use prohibited),
Railway free tier (gone), Fly.io (card required), Render free Postgres (deleted at 30
days), Supabase free (5 GB egress then HTTP 402), Gemini image free tier (gone),
`gemini-2.5-flash-image-preview` (shut down 2026-01-15), Gemini CLI free tier (ended
2026-06-18), Claude Code (no free plan, $20/mo).

---

## 4. What is VERIFIED vs what is ASSUMED

This distinction matters more than anything else in this repo.

### ✅ Verified against primary sources
- **Every dependency version** in `gradle/libs.versions.toml` was resolved from live Maven
  metadata on 2026-10-29. Note androidx artifacts live on `dl.google.com`, not Maven Central.
- **AGP 8.13.2 ↔ Kotlin 2.3 pairing** confirmed by the official AGP 8.13 release notes
  (updated 2026-10-28): *"AGP 8.13.2 uses R8 8.13.19 which supports Kotlin 2.3."* Same page:
  Gradle minimum **8.13**, JDK **17**, SDK Build Tools 35.0.0, max API 36.1.
- **Subject Segmentation is unbundled-only** (see 2.1).
- **`purchases-ui-ktx` does not exist** — Maven returned empty. That is why the paywall is
  hand-built rather than using RevenueCat's paywall UI.
- **117 unit tests pass**, compiled and executed against the real production sources with
  kotlinc 1.9.24 + JUnit 4 on 2026-10-29. Not mirrors, not copies.

### ⚠️ NEVER compiled by an Android toolchain
Not one of the 55 Kotlin files has been through a real Android compile. The authoring
environment had no Android SDK, no Gradle, JDK 11 only (AGP needs 17), 2 GB RAM, and no
`/dev/kvm` so no emulator. **Expect compile errors. That is the expected state, not a
defect.** `.github/workflows/ci.yml` exists precisely to close this gap.

### ⚠️ Specifically unverified
- The exact `com.google.mlkit.vision.segmentation.subject.*` and
  `com.google.android.gms.common.moduleinstall.*` import paths — beta surface, and AI
  training data is stale on beta packages. **Diff against the doc's import list.** Flagged
  in a header comment inside `SubjectSegmentationEngine.kt`.
- `monetization/billing/PurchaseFlow.kt` has never run against a real RevenueCat offering.
  The RevenueCat API has changed shape several times (callbacks → `awaitX` coroutines →
  `*With` scope extensions) and training data mixes all three. It is isolated in one file
  for exactly this reason.
- The ~33 MB size estimate is arithmetic, not a measurement. The `release` CI job reports
  the real number. If it exceeds 70 MB, stop and add no more models.
- Compose BOM `2026.09.00` and the rest will drift. All versions live in
  `gradle/libs.versions.toml` and nowhere else.

---

## 5. Architecture rules that must not be broken

1. **`AdPolicy` is the only thing that decides whether an ad shows.** No screen, ViewModel
   or manager may call `AdManager.show*()` directly. They go through `AdCoordinator`, which
   builds a truthful `AdContext` from persisted counters and honours the `Decision`. This
   is what prevents a paying user seeing an ad after an entitlement desync — the #2 revenue
   killer and a direct cause of refunds.
2. **Banners and app-open ads have no enable flag at all.** They are `false` unconditionally
   in code. A test asserts this, so adding a flag fails the build.
3. **Interstitials ship disabled** (`ads_interstitial_enabled=false` in Remote Config
   defaults) and only ever fire after a completed export/save.
4. **`ExportFlow` is the only export path.** Free-tier cap, watermark, destination, counter
   persistence and the post-export ad decision all live there. Before it existed, eight
   tools each reimplemented that sequence — which is how one tool forgets a step and no test
   can catch it.
5. **Batch processing is sequential** (or a pool of at most 2). Parallelising 100 decodes is
   how photo apps die with OOM on mid-range devices.
6. **Pure maths lives in import-free files** — `core/image/LayoutMath.kt` and
   `core/image/QualitySearch.kt` — so it is JVM-testable with no emulator. If you add maths
   to a tool, add it there and add a test.
7. **No DI framework.** `ServiceLocator` is hand-written on purpose: about eight singletons,
   and a Koin/Hilt setup adds a KSP round plus a version-compatibility matrix for nothing.
   Owner constraint C6.
8. **Rewarded ads may only grant small, temporary rewards.** `RewardGuard.RewardKind` has
   exactly five members and a test forbids any name containing PRO, LIFETIME, TRIAL_EXTEND
   or SUBSCRIPTION. There is no path from a rewarded ad to Pro.
9. **No function may accept a parameter it ignores, and no UI control may promise something
   the code cannot do.** Bugs #5, #10 and #12 are all this rule being broken. Enforced by
   `kotlinc` warnings and by `tools/check_wiring.py`.
10. **Every Remote Config key must have a reader and a default.** A key with no reader is a
    knob the owner will turn in Firebase for no effect; a key with no default returns
    `false`/`0` from `getBoolean`/`getLong`, which for `tool_enabled_*` means the tool
    **disappears from the home screen**. Both are audited by `check_wiring.py`, and
    deliberately-parked keys are listed in its `PARKED` map with a reason.

---

## 6. Bugs found — 12 total, 11 fixed, 1 parked

Do not "simplify" these back. Each was a real defect.

**Found by:** 5 by unit tests · 3 by the resource linter or by reading · **4 by the wiring
audit**. The wiring bugs could not have been caught by a compiler — every individual piece
compiled fine, and only the *connection* was missing.

| # | Bug | Why it mattered |
|---|---|---|
| 1 | `RewardGuard.canGrant(-1)` returned `true` | A corrupted DataStore counter, or a user rolling the device clock back, got **unlimited rewards**. Now `grantsToday in 0 until cap` — fails closed |
| 2 | `checkBackground()` rejected every coloured background | Its low-saturation gate ran unconditionally, so **every** Indonesian (red bg) and Malaysian (blue bg) user got a false warning on a compliant photo |
| 3 | Print sheet tried only one orientation | Trying both takes a 4×6 sheet from **6 copies to 8** for a 35×45 mm photo — free paper and ink |
| 4 | `computeThresholds()` put the gate ABOVE the observed background | On a dimly lit white wall (luma ~120) the gate came out at ~173, so White Background **whitened nothing while reporting success**. No crash, no error — just an unchanged photo |
| 5 | `floodWhiten()` took a `high` threshold and never used it | The "hysteresis" in its own docstring did not exist. Now seeds at `high`, propagates at `low` |
| 6 | `setOnboardingDone()` had **zero callers** | `AdPolicy` blocks every ad while `isOnboarding`; `AdCoordinator` derived it from `onboardingDone()`. The flag stayed false forever, so **no ad could ever show on any device** — and it would have looked exactly like "AdMob isn't filling" |
| 7 | Batch exports were written to `cacheDir` | Android wipes cache under storage pressure and the user cannot browse there, so 40 resized photos could **silently vanish**. Replaced with a SAF folder picker + persistable grant; the fallback was deleted, not left as a safety net |
| 8 | `<string name="cancel">` was declared **twice** in both `strings.xml` files | `aapt2` fails the build on a duplicate resource name, deep inside a ~10-minute Gradle run, without naming the line. The Hindi copy was worse: the two entries had *different* translations, so which one won was arbitrary. Now blocked by `tools/check_resources.py`, which runs as a CI gate in ~10 seconds |
| 9 | **`paywall_after_screens` was dead at both ends** | Nothing called `Prefs.incrementPaywallScreens()`, nothing read `RemoteFlags.paywallAfterScreens`, and the key was absent from the defaults map — so `getLong` returned 0. **PARKED, not fixed**: it is harmless in code, but the owner would type the key into the Firebase console and see no effect. Wire it or delete it before launch. Listed in `PARKED` in `tools/check_wiring.py` with this reason |
| 10 | The Batch **"Remove location and camera data" switch was inert** | The pipeline is `BitmapFactory.decode` → `Bitmap` → `Bitmap.compress()`. A `Bitmap` carries no EXIF, so re-encoding always produced a file with no GPS, no camera model and no timestamps. The switch therefore could not do anything, and its OFF position **promised to keep data the app never keeps**. Fails in the privacy-safe direction, but a false UI claim is still a Play Deceptive-Behaviour exposure. Replaced with honest static text in both locales; the dead `STRIP_EXIF` key and its two accessors were deleted |
| 11 | **M8 PDF produced no ad decision at all** | `PdfScreen(adsReady, …)` received the flag from `Nav.kt` and never read it, so saving a PDF never asked `AdPolicy` anything. It was the only one of the eight tools with no post-save ad hook — lost inventory on a genuine natural break point. Now wired, and `check_wiring.py` asserts that every feature which exports either uses `ExportFlow` or calls `afterExportCompleted` |
| 12 | `AdPolicy.canShowInterstitialPure()` accepted `dailyCap` and **ignored it** | Behaviour was correct — the cap is enforced in the impure wrapper, which has the persisted counter — but the signature lied: a caller passing `dailyCap = 0` would expect ads off and silently get ads. Parameter removed and all 117 tests re-run to confirm. Found by a `kotlinc` warning, which is the same shape as bug #5 |

Bugs 6, 7, 9, 10 and 11 were all found by auditing, not by tests. Each compiled fine and
every piece was individually correct — the *connection* was missing. That class of bug is
why `ExportFlow` and `AdCoordinator` exist as single chokepoints, and why
`tools/check_wiring.py` now exists as a CI gate: at 9,000 lines a careful reader cannot
hold all the connections in their head either.

**Bugs #5 and #12 are the same defect six files apart** — a parameter accepted and ignored.
`kotlinc` warns about this. **Treat "parameter is never used" as an error, not a warning.**

---

## 7. Commercial model (do not re-litigate without new data)

- **Category: Photography.** Photo & Video has the highest median revenue of any category
  ($124/mo) and the best 2-year odds of reaching $1K (21.4%). Business is the worst at 1.6%.
- **8 tools in one app** is the whole ASO strategy: one listing ranking for "background
  remover", "passport photo", "photo compressor", "collage" and "image to pdf" at once.
- **Free tier:** unlimited background removal (the hook, never capped), 3 exports/day, SD,
  watermark, batch of 1, 10 ID countries, 10 templates.
- **Pro:** unlimited, HD/4K, batch of 100, 30+ countries, all templates, zero forced ads.
  `pro_weekly` $6.99/₹99 · `pro_yearly` $34.99/₹999 (the anchor) · `pro_lifetime`
  $59.99/₹1,999 (**added at Day 60, not at launch**).
- **Trial ≥ 7 days.** 7+ day trials convert 37–43% vs 25.5% for ≤4 days.
- **Soft-hard hybrid paywall** with a visible free escape. Removing the escape hatch is how
  apps destroy their ad inventory in low-ARPU markets.
- **Geography is a 9× lever.** 3,000 DAU at 45% Tier-1 ≈ ₹2.28 lakh/mo; 3,000 DAU at 0%
  Tier-1 ≈ ₹24,900. So launch on r/EtsySellers, r/eBay, r/reselling, r/Flipping,
  r/smallbusiness — **never** r/AndroidDev or r/india.
- **Ad schedule:** Day 0–30 rewarded only. Day 30–60 interstitials 1/day *only if* D7 > 15%
  and crash-free > 99.5%. Every frequency change needs a minimum 7-day test. Ad overload is
  the #1 killer in this category: one disruptive interstitial costs +6–7% churn.
- **North Star = blended ARPDAU.** Raise ad frequency only if ARPDAU rises AND D7 retention
  drops by at most 1 point.
- **Owner runs it solo.** Median app in this category makes $72/mo at year one; 2-year odds
  of $1K are 17.3% overall and 21.4% for Photo & Video. Plan against that base rate, not
  against a success story.
