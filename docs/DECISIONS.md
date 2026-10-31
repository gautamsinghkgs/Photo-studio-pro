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
- **RevenueCat `purchases:6.5.2` API shape** — inspected the AAR *and* sources jar from
  Maven Central on 2026-10-30. `*With` extensions exist; callback interfaces are named
  `*Callback`, not `*Listener`; `UpdatedCustomerInfoListener` is a Java SAM interface.
  Three compile errors fixed from this (README §7.4). **Method: when an SDK's history is
  messy, download its sources jar — do not trust training data.**
- **ML Kit Subject Segmentation public package** — `com.google.mlkit.vision.segmentation.subject`
  with exactly five public classes, confirmed by listing the beta AAR's `classes.jar`.
- **Every dependency version** in `gradle/libs.versions.toml` was resolved from live Maven
  metadata on 2026-10-29. Note androidx artifacts live on `dl.google.com`, not Maven Central.
- **AGP 9.1.1 ↔ Kotlin 2.3.21 ↔ Gradle 9.3.1** — no longer taken from a release-notes
  table: this combination **built the project** on 2026-10-30. Requirements per Google's
  AGP 9.0 notes + `kotl.in/gradle/agp-built-in-kotlin`: Gradle ≥ 9.1.0, JDK 17, Build Tools
  **36.0.0**, max API **37.0**, *built-in* Kotlin (so `org.jetbrains.kotlin.android` must
  NOT be applied), new-DSL interfaces only, non-final R class, `targetSdk` defaults to
  `compileSdk`.
- **Subject Segmentation is unbundled-only** (see 2.1).
- **`purchases-ui-ktx` is empty** on Maven — that is why the paywall is hand-built. The API
  we call lives only in the umbrella `com.revenuecat.purchases:purchases:6.5.2`.
- **`com.google.android.gms.common.moduleinstall` ships inside `play-services-base`**
  (47 classes, confirmed with `javap`) — there is no separate artifact to add.
- **ML Kit's `Face` has no `UNCOMPUTED_PROBABILITY`**; the three probability getters return
  a nullable `java.lang.Float`. The real classes live in
  `play-services-mlkit-face-detection-17.1.0.aar`; `com.google.mlkit:face-detection:16.1.7`
  is a stub that delegates to Play services.
- **117 unit tests pass under real Gradle** — `:app:testDebugUnitTest`, 2026-10-30, 0 failures,
  0 skipped, confirmed by parsing `app/build/test-results/**/*.xml`. The earlier
  kotlinc-1.9.24 stub-harness result was **not** equivalent; see bug **#28**.

### ✅ It compiles. Two stages still cannot be verified locally.
`:app:compileDebugKotlin` and `:app:testDebugUnitTest` are both **green** under AGP 9.1.1 /
Gradle 9.3.1 / JDK 17 / compileSdk 37 inside the authoring sandbox (2026-10-30), after six
compile rounds and bugs **#15–#29**. Resources, all three manifest tasks,
`processDebugResources`, `checkDebugAarMetadata` and javac are green too.

**Blocked by sandbox RAM, not by the code:** `assembleDebug` / `bundleRelease`. D8 is
OOM-killed at `dexBuilderDebug` with 2 GB total. So **dexing, R8, resource shrinking,
packaging, the 16 KB zipalign check and the AAB size measurement have never run anywhere** —
the CI runner (7 GB) is the first place they can. No emulator has ever run either (no
`/dev/kvm` in the sandbox).

### ⚠️ Specifically unverified
- **R8 and resource shrinking.** `isMinifyEnabled` + `isShrinkResources` have never executed.
  The classic first failure is *"Missing class detected while running R8"* → add that package
  as `-dontwarn` in `app/proguard-rules.pro`. The second thing only a release build can
  answer: whether `material-icons-extended` really shrinks from ~7,000 icons to the ~13 used.
- **Runtime behaviour of everything.** Compiling proves *shape*, not semantics: whether the
  Subject Segmentation model downloads and how fast, whether ML Kit returns usable
  probabilities for a real face, whether the Compose screens render, whether a real purchase
  round-trips. `PurchaseFlow.kt` is isolated in one file for exactly this reason.
- The ~33 MB size estimate is arithmetic, not a measurement. The `release` CI job reports
  the real number. If it exceeds 70 MB, stop and add no more models.
- Versions will drift. All of them live in `gradle/libs.versions.toml` and nowhere else —
  except the one unavoidable literal inside the root `buildscript { }` block (catalog
  accessors do not exist on the build classpath). `check_wiring.py` reports that as a *note*
  so it stays visible instead of being silently forgotten.

---

## 5. Architecture rules that must not be broken

1. **`AdPolicy` is the only thing that decides whether an ad shows.** No screen, ViewModel
   or manager may call `AdManager.show*()` directly. They go through `AdCoordinator`, which
   builds a truthful `AdContext` from persisted counters and honours the `Decision`. This
   is what prevents a paying user seeing an ad after an entitlement desync — the #2 revenue
   killer and a direct cause of refunds.
2. **Banners and app-open ads have no enable flag at all.** They are `false` unconditionally
   in code. A test asserts this, so adding a flag fails the build.
3. **Interstitials ship ENABLED from day 1** (`ads_interstitial_enabled=true` in Remote Config
   defaults — owner decision 2026-10-31: ads are the early monetization, one interstitial per
   export cycle) and only ever fire after a completed export/save. Rails unchanged: daily cap,
   180 s gap, first-ad delay, banners off forever, pro = zero ads. The old D30–60 gate now
   governs frequency ramp-up, not enablement.
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

## 6. Bugs found — 39 total, 39 fixed, 0 parked

Do not "simplify" these back. Each was a real defect.

**Found by:** 5 by unit tests · 4 by the resource linter or by reading · **4 by the wiring
audit** · 6 by CI runs (first-ever `release`-job executions: R8 #7, `lintVital` #8, compile #10, bundletool flags #12) · **1 by the first-ever emulator boot** (#38 — a whole class of bugs CI cannot see, because CI builds and never runs) · **1 by the first living render** (#39 — a screenshot caught what no checker can: a broken product promise on screen) · **15 by pointing a real Android toolchain at the code** (six compile
rounds, `javap` on the downloaded AARs, and one real `testDebugUnitTest`) · **3 during the P1
`ExportFlow` refactor**.

There are three complementary classes here, and each one demands a different defence:

- **#1–#12 could not have been caught by a compiler.** Every piece compiled fine and only the
  *connection* was missing. Defence: `tools/check_wiring.py` as a CI gate.
- **#15–#29 could not have been caught by reading.** Every one was a plausible-looking API
  that does not exist, a coordinate that does not resolve, a name spelled wrong at the call
  site, or a dependency that is never called. Defence: **install the toolchain and run it** —
  README §7.2 has the exact command lines, and they work inside a 2 GB sandbox.
- **#30–#31 were invisible to both.** A counter that was written but never gated, and a
  failure sentinel that only one of two paths handled. They surfaced only because somebody
  had to *move* that code onto `ExportFlow` and therefore read every line of it. Defence:
  **refactors are audits** — when you move a function, read for missing gates, not just for
  compile errors. That is also why the double-ad trap is documented *before* the refactor
  instead of after it.

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
| 9 | **`paywall_after_screens` was dead at both ends** | Nothing called `Prefs.incrementPaywallScreens()`, nothing read `RemoteFlags.paywallAfterScreens`. **FIXED 2026-10-31 — DELETED, not wired**: a Firebase knob with zero effect is an owner trap; if a paywall-timing lever is ever wanted it gets designed deliberately (README §6 P2). Removed: key constant, defaults entry, RemoteFlags property, Prefs mutator, and both `PARKED` entries in `tools/check_wiring.py` |
| 10 | The Batch **"Remove location and camera data" switch was inert** | The pipeline is `BitmapFactory.decode` → `Bitmap` → `Bitmap.compress()`. A `Bitmap` carries no EXIF, so re-encoding always produced a file with no GPS, no camera model and no timestamps. The switch therefore could not do anything, and its OFF position **promised to keep data the app never keeps**. Fails in the privacy-safe direction, but a false UI claim is still a Play Deceptive-Behaviour exposure. Replaced with honest static text in both locales; the dead `STRIP_EXIF` key and its two accessors were deleted |
| 11 | **M8 PDF produced no ad decision at all** | `PdfScreen(adsReady, …)` received the flag from `Nav.kt` and never read it, so saving a PDF never asked `AdPolicy` anything. It was the only one of the eight tools with no post-save ad hook — lost inventory on a genuine natural break point. Now wired, and `check_wiring.py` asserts that every feature which exports either uses `ExportFlow` or calls `afterExportCompleted` |
| 12 | `AdPolicy.canShowInterstitialPure()` accepted `dailyCap` and **ignored it** | Behaviour was correct — the cap is enforced in the impure wrapper, which has the persisted counter — but the signature lied: a caller passing `dailyCap = 0` would expect ads off and silently get ads. Parameter removed and all 117 tests re-run to confirm. Found by a `kotlinc` warning, which is the same shape as bug #5 |
| 13 | **CI assumed `sdkmanager` was on PATH** | It is not, on current GitHub Ubuntu runners (runner-images#13674); the legacy `tools/` dir is gone. Run #1 died in 0 s at the SDK step, before any Kotlin. Fixed by resolving `cmdline-tools/latest/bin/sdkmanager` with a download fallback. **A 0-second step failure means "tool not found", not "code wrong"** |
| 14 | **`kotlinOptions { jvmTarget }` is an error in Kotlin 2.3** | Build script died before any source compiled (run #2, "1 error"). Now `compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }` |
| 15 | **Firebase `-ktx` coordinates do not exist under BOM 35.1.0** | Gradle reported `Could not find com.google.firebase:firebase-analytics-ktx:` — a trailing colon and *no version* means the BOM does not manage that coordinate at all (`-ktx` metadata ends at 22.5.0; BOM 35.1.0 manages analytics 24.0.0 / config 24.0.1 / crashlytics 21.0.1). Every Firebase dependency switched to the non-`-ktx` name; the imports already pointed at those packages |
| 16 | **`checkDebugAarMetadata` failed with 36 issues** | The 2026 AndroidX generation declares `minCompileSdk=37` **and** `minAndroidGradlePluginVersion=9.1.0` in its AAR metadata. Downgrading one library only moves the failure to the next, so the whole toolchain moved: AGP **9.1.1**, Gradle **9.3.1**, compileSdk **37**, Build-Tools **36.0.0**. `targetSdk` stays **36** because that is what Play enforces |
| 17 | **`org.jetbrains.kotlin.android` is incompatible with AGP 9** | AGP 9 has *built-in* Kotlin; applying both is an error, and so is the old variant API. KGP 2.3.21 now arrives via a root `buildscript { classpath }` override and `kotlinOptions` became `kotlin { compilerOptions { … } }`. The escape hatch `android.builtInKotlin=false` was deliberately **not** used — it is removed in AGP 10, so it would have been a second migration |
| 18 | **Wrong RevenueCat coordinate → 69 errors at once** | `purchases-store-google` carries only 45 store classes: no `Purchases`, no `PurchasesConfiguration`, no `*With` extensions; `purchases-ui-ktx` is empty. Every billing type in the project was unresolved *simultaneously*, which is the signature of an artifact problem, not a code problem. Fixed by the umbrella **`com.revenuecat.purchases:purchases:6.5.2`** (142 classes) |
| 19 | **The `ModuleInstall` API was invented from memory** | There is no `registerListener` (only `unregisterListener`); `installModules` returns `Task<ModuleInstallResponse>`; the request is built with `ModuleInstallRequest.newBuilder().addApi(…).setListener(…)`; and the whole package ships **inside `play-services-base`**. Subject Segmentation's deferred-install path would have crashed at runtime even if it had compiled — it gates the BG Remover, the app's free-tier hook |
| 20 | **`PAYWALL_AFTER_SCREONS` — a typo at the *usage* sites** | The declaration was spelled correctly, so grepping for the correct name found nothing and the hunt went down homoglyph / encoding / blank-line-formatting dead ends. This flag gates the paywall-after-N-exports lever, i.e. directly gates revenue. Fixed with a byte-level replace plus a re-count assertion (retyping it by hand reproduced the typo twice) |
| 21 | **Fully-qualified extension properties do not resolve** | `Icons.AutoMirrored.Filled.ArrowBack` written as a receiver chain cannot compile — extension properties need an `import` — and it lives in `material-icons-core`, not `-extended` (which only has `ArrowBackIos`/`ArrowBackIosNew`). Same round: `LocalActivity` → `androidx.activity.compose`, `TextButton` → compiled into material3's `ButtonKt`. Three screens would not build |
| 22 | **`android.graphics.Color` vs Compose `Color` in `CollageScreen`** | Collage state holds the background colour as an **Int**, so comparing it to a Compose `Color` is a type error, and `Color.WHITE` is valid only in the pure ops files. Compare and assign via `toArgb()`. The same identifier being legal in one file and illegal in the next is why the pure/impure split (§5.5) has to stay |
| 23 | **Three Collage colour swatches were inert** | They rendered but had no `clickable`, so a user could tap a background colour forever and nothing would happen. Same deceptive-UI class as #10, and no compiler will ever flag a missing click handler — only a wiring audit or a finger |
| 24 | **`EnhanceOps.adjust()` was called positionally** | The 5th parameter is `gamma`, so the caller's `sharpness`/`denoise` values landed in the wrong slots: Enhance would have produced visibly wrong images while reporting success. The parameter `denoise: Int` also shadowed the object's own `fun denoise(…)`. Now named arguments, an explicit `EnhanceOps.denoise(cur, threshold)`, the colour loop extracted verbatim into `private fun adjustColour(…)`, the order fixed to denoise → colour → sharpen, and intermediates recycled only when `!== src` |
| 25 | **ML Kit's `Face.UNCOMPUTED_PROBABILITY` does not exist** | Three `.takeIf { it != Face.UNCOMPUTED_PROBABILITY }` guards in `FaceEngine.kt`. The getters already return a nullable `java.lang.Float` where `null` *means* not computed, so the guards were both unresolvable and redundant; nulls now pass straight to `FaceGeometry`. Note `com.google.mlkit:face-detection:16.1.7` is a stub — the classes ship in `play-services-mlkit-face-detection-17.1.0.aar` |
| 26 | **No smart-cast across modules for `EntitlementInfo.expirationDate`** | It returns a nullable `java.util.Date` from another module, which Kotlin will not smart-cast, so the trial-window check did not compile. Bound to a local first: `val expiry = e?.expirationDate`. This is the code that decides whether a user is inside the 7-day trial — i.e. whether they pay |
| 27 | **Suspend work inside AdMob's non-suspend callbacks, plus three RevenueCat shapes** | `onDismissed`/`onEarned` are plain `() -> Unit`, so the persisted counters had nowhere to be written; a dropped write means a daily cap or the 180 s gap silently not being enforced — the #1 revenue-killer class this architecture exists to prevent. Now a singleton `callbackScope` (`SupervisorJob() + Dispatchers.Main.immediate`, deliberately never cancelled) with `try { … } finally { callback() }`. Also `Package.getProduct()`, `StoreProduct.price.amountMicros`, and `PurchasesErrorCode.OperationAlreadyInProgressError` — there is no `PurchaseInProgressError` among the 27 codes |
| 28 | **The 117 unit tests had never been through a real compile** | `AdPolicyTest.kt`'s fakes subclassed the `final` classes `EntitlementManager` / `RemoteFlags`, whose constructors need a `Context` / `FirebaseRemoteConfig`, and overrode `final` members → 11 errors on the first genuine `testDebugUnitTest`. The stub harness had hidden this for weeks, so "117/117 green" was over-claimed in every document. Fixed with narrow seam interfaces — `EntitlementSource { snapshot() }` and `FlagSource { … }` — beside the existing `UsageCounters`, implemented by the real classes; no `open`, no Mockito. **Rule: a stub harness proves logic, only Gradle proves compilation** |
| 29 | **Coil 3.6.3 was declared and never used** | Every image in the app is painted from a locally-decoded bitmap (`Image(...)` / `BitmapPainter`); the only mention of Coil anywhere was its name in the open-source attribution string. `coil-core` + `coil-compose` + `okio` cost ~2 MB of a ~33 MB AAB budget for zero features, and the attribution claimed a licence for a library the app does not ship. Removed, and the string corrected. **Confirm a dependency is referenced before paying its size cost** |
| 30 | **Batch counted exports but never checked the daily cap** | `noteExport()` ran on every batch; nothing ever read the count. `allowedCount` limits files **per run** (free = 1 + rewarded bonuses), not runs per day — so a free user could resize unlimited photos by running one-file batches, and the 3-exports/day cap (the whole free tier) did not exist for this tool. It looked correct because the counter *was* being incremented: same shape as #6, a state write with no matching gate. `ExportFlow.runBatch` now checks the cap before writing anything and returns `NeedsPro` |
| 31 | **A failed batch file was recorded as a successful export** | `processOne` signals failure two ways — a negative sentinel and an exception — and only the exception was handled, so `-1L` became `done = true, outBytes = -1` and rendered `✔ -1 B`. Silent success, same family as #4: the user sees a tick next to a photo that was never written and the run total is wrong. Any negative return is now a failure |
| 32 | **A trailing lambda binds to the LAST parameter** | `runBatch(…, write, afterSave = {})` called with a trailing lambda attached it to `afterSave`: *"No value passed for parameter 'write'"* plus three cascade errors. Reordered so the required lambda is last. The standing rule held — fix the first error, the rest were inference fallout |
| 33 | **R8 blocked the first-ever release build on pdfbox's optional JPEG2000 codec** | `com.tom_roush.pdfbox.filter.JPXFilter` references `com.gemalto.jp2.JP2Decoder` / `JP2Encoder`, which pdfbox-android never bundles (JPX is an optional codec). R8's missing-class check therefore fails `minifyReleaseWithR8` — CI run #7. Debug builds never run R8, so no amount of local green could have surfaced it. Fixed with `-dontwarn com.gemalto.jp2.**`, which is honest rather than a mask: the classes are absent at runtime too, and `PdfViewModel` wraps `pdfToImages` in `runCatching` (catches `Throwable`), so a JPX-encoded page degrades to `Ui.Failed("pdf_render_failed")` instead of crashing. **An unexecuted build stage is an unmet bug category** |
| 34 | **Backup-rule XMLs failed the `FullBackupContent` schema and described storage that does not exist** | `lintVitalRelease` (release-only, ran for the first time in run #8) rejected 7 things: `domain="cache"` is not a domain at all; every `<exclude>` sat outside the `<include>` set, i.e. a dead rule once includes exist; and the includes named `sharedpref` + `usage.db` while the app stores Prefs and all daily counters in one DataStore file (`files/datastore/…`) and ships no database. Net effect before the fix: nothing was backed up and nothing was excluded. Fixed include-only on `datastore/` in both XMLs — with includes present, Auto Backup takes exactly those paths, so photos/exports/caches stay out by definition (SPEC §11). Same family as #33: the checker had never run, so the file had never been read |
| 35 | **Room 2.8.5 was a dependency nobody used** | No `@Database`/`@Dao`/`@Entity`/`databaseBuilder` in main or test sources, yet runtime+ktx+KSP-compiler were declared and proguard kept Room classes; the old backup rules even named a `usage.db` that never existed (#34). Removed with the post-green cleanup commit. Same tax-as-#29 class |
| 36 | **`LocalClipboard` is an API migration, not a rename** | compose-ui 1.12 `Clipboard` has only suspend `get/setClipEntry(ClipEntry)`; `ClipEntry` wraps `android.content.ClipData`; the old non-suspend `setText(AnnotatedString)` is gone. The cleanup batch applied the deprecation's named replacement blind (no local toolchain) and run #10's compile died in all three build jobs. Fixed with `rememberCoroutineScope` + `scope.launch { setClipEntry(ClipEntry(ClipData.newPlainText(...))) }`, javap-verified against `ui-android-1.12.1.aar`, then compile + 117 tests proven locally before shipping. Rule added: **never ship a deprecation replacement without either a compile or a javap of the artifact** |
| 37 | **bundletool flags written from memory** | `--alias` is not a bundletool flag (it wants `--ks-key-alias`); the device spec also missed the required `sdkVersion` — a second failure hiding behind the first. The size step died in 1 s on run #12 while all real gates had passed (16 KB ✅ 64-bit ✅ AAB 53M). Fixed by running the identical 1.18.3 jar locally against a dummy bundle: the old flags reproduced CI's error verbatim (control), the corrected flags + `sdkVersion` pass validation; the step is now `continue-on-error` — reporting can never red the pipeline. Rule from #36 extended: **new CI tooling gets exercised locally before it ships** |
| 38 | **Firebase optional at build time, mandatory at runtime — the app died at first launch on every device** | The `google-services` plugin is commented out until a real `google-services.json` exists, so no Firebase resources are generated; `FirebaseApp.initializeApp()` returns null (does not throw) and `FirebaseRemoteConfig.getInstance()` in `ServiceLocator.init` then throws `IllegalStateException`. Sixteen green CI runs were blind to it — CI builds, it never runs. Caught in minutes by the first emulator boot via `adb logcat -b crash`. Fixed by making Firebase truly optional per SPEC C2: `AppFlags` interface + `LocalFlags` in-memory fallback, both reading one `FlagDefaults` object (no drift possible), with `runCatching { RemoteFlags(...) }.getOrElse { LocalFlags() }` in ServiceLocator. Rule: startup-path changes get an emulator boot before shipping |
| 39 | **Home hid 5 of 8 built tools: milestone-era `false` kill-switch defaults** | `FlagDefaults.TOOL_ENABLED` kept `false` for whitebg/collage/pdf/ocr/enhance from when those tools did not exist; HomeScreen filters on `isToolEnabled`, so fresh installs showed 3 tools against the "eight tools" promise in onboarding and the store listing. Caught by the first living render (emulator screenshot) — no crash, no lint, just a broken promise on screen. Fixed: all defaults `true` (the switches exist for remote incidents), and `check_wiring.py` now errors on any `false` launch default for a built+listed tool |

**Owner decisions recorded 2026-10-31 (monetization policy):**
1. Interstitials ON at launch — one per export cycle, shown after the save completes.
2. Batch free output stays CLEAN (no watermark) until the 100k+ downloads phase; watermark is
   the late-stage lever, ads are the early one. Written into the SPEC free-tier table.
3. Subscribers see zero ads — confirmed already implemented, no change.
4. Defaults with owner veto, taken when the owner skipped the decision UI: watermark scope =
   the five image tools keep their shipped free-tier watermark (only Batch is clean); ad
   placement = post-export (pre-export would show an ad for an export that can still fail).

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
- **Ad schedule:** interstitials ON from day 1, one per export cycle, post-export (owner
  decision 2026-10-31). Day 30–60: ramp frequency to 1/day *only if* D7 > 15% and crash-free
  > 99.5%. Every frequency change needs a minimum 7-day test. Ad overload is the #1 killer in
  this category: one disruptive interstitial costs +6–7% churn.
- **North Star = blended ARPDAU.** Raise ad frequency only if ARPDAU rises AND D7 retention
  drops by at most 1 point.
- **Owner runs it solo.** Median app in this category makes $72/mo at year one; 2-year odds
  of $1K are 17.3% overall and 21.4% for Photo & Video. Plan against that base rate, not
  against a success story.
