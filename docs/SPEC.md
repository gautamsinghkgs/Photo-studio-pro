# SPEC.md — Photo Studio Pro (Android)

> **How to use this file (Hinglish):**
> Yeh file tum apne AI coding tool (Google Antigravity / Jules / Copilot) ko doge.
> 1. Pehle GitHub par ek repo banao, yeh file usmein `SPEC.md` naam se commit karo.
> 2. AI ko bolo: *"Read SPEC.md completely. Then start with Milestone 0. Do not skip the HARD CONSTRAINTS section."*
> 3. Ek milestone poora hone par test karo, phir agla maango. Poora app ek saath mat mangwana.
> 4. Jahan bhi ML Kit use ho, **official doc page ka code snippet khud paste karo** — AI ke memory se signature mat likhwana.

---

## 0. HARD CONSTRAINTS — READ FIRST, NEVER VIOLATE

These are non-negotiable. If any requirement below conflicts with a HARD CONSTRAINT,
**the HARD CONSTRAINT wins. Stop and ask instead of improvising.**

### C1 — ZERO CLOUD APIs
- **No cloud AI / image / LLM APIs.** Not Gemini, not OpenAI, not Replicate, not fal.ai,
  not remove.bg, not Clipdrop, not Stability, not any of them.
- **All image processing MUST run on-device** using ML Kit + Android `Bitmap` / `Canvas` /
  `RenderEffect` / OpenGL.
- If a feature cannot be done on-device, **DO NOT implement it.** Write
  `// SKIPPED: requires cloud API — see SPEC.md section 12` and move on.
- There is **no API key anywhere in this project.** If you find yourself writing
  `BuildConfig.SOME_API_KEY`, you are violating C1.

### C2 — ZERO BACKEND
- **Do not write any server code.** No Cloud Functions, no Workers, no Express, no
  Supabase edge functions, no REST API, no database server.
- There is no `/api` folder in this project. Ever.
- Server-side work is delegated to third-party SDKs:
  - Receipt validation + entitlements → **RevenueCat SDK**
  - Analytics / crashes / remote config → **Firebase (Spark plan) SDKs**
  - Ads → **Google Mobile Ads SDK** (entirely client-side)
- **Do not enable or reference any Firebase Blaze-only product**
  (Cloud Functions, Cloud Storage, Cloud Messaging with backend). Spark plan only.

### C3 — MODERN ANDROID COMPLIANCE
- `compileSdk = 37`, `targetSdk = 36`, `minSdk = 24`
  *(compileSdk was specified as 36 and had to move to 37: the 2026 AndroidX generation
  declares `minCompileSdk=37` **and** `minAndroidGradlePluginVersion=9.1.0` in its AAR
  metadata. `targetSdk` — what Play actually enforces — stays 36. See README §8 bug #16.)*
- Latest stable AGP (**9.1.1**), Kotlin 2.x (**2.3.21**), Compose BOM (**2026.09.00**),
  Gradle **9.3.1**, JDK **17**, SDK Build-Tools **36.0.0**. AGP 9 has *built-in* Kotlin, so
  `org.jetbrains.kotlin.android` must **not** be applied (bug #17).
- **All native libraries must be 16 KB page-size aligned** (required from 31 Aug 2026).
  After any build that adds a native dependency, verify alignment.
- Ship as **Android App Bundle (.aab)** only. Never a universal APK.
- Jetpack Compose for all UI. No XML layouts except where an SDK demands it.
- Kotlin coroutines + Flow. No RxJava.

### C4 — DO NOT INVENT VERSION NUMBERS
- ML Kit, AdMob, RevenueCat and Firebase versions change frequently.
- When you add a dependency, write the artifact name and put `/* TODO: confirm latest
  version from official docs */` — **never guess a version string from memory.**
- Official doc URLs are listed in section 4. Fetch or ask the human for the current
  version before finalising `libs.versions.toml`.

### C5 — NEVER SHIP PLACEHOLDERS
- No `TODO()` bodies, no "Coming Soon" screens, no lorem ipsum, no stub buttons.
- Every button in the UI must do something real before a milestone is called complete.
- Incomplete builds are a top-3 Google Play rejection reason.

---

## 1. PRODUCT

**Internal name:** Photo Studio Pro
**Play Store title:** `Photo Studio Pro: BG Remover` (28 chars, within the 30-char limit)
**Package:** `com.photostudio.pro`
**Platform:** Android only (minSdk 24 / Android 7.0+)
**Language:** Kotlin, Jetpack Compose, single-module app (modularise only if build times hurt)
**Primary audience:** Online sellers (Etsy, eBay, Amazon, Meesho, Flipkart, Instagram shops),
freelancers, students needing ID photos, small-business owners in India + Tier-1 English markets.

### One-line pitch
> Remove backgrounds & make ID photos — your photos never leave your phone. Unlimited, no upload, no signup.

### The three words that define this product
Every screen, every string, every store asset must reinforce these:
1. **OFFLINE** — ⚠️ **CORRECTED 2026-10-29.** Works in airplane mode *after a
   one-time setup*, NOT from the very first run. Subject Segmentation is
   unbundled-only, so Google Play services downloads its model once (≈200 KB of
   app code; the model itself arrives over the network). The other 7 tools are
   genuinely offline from first run. **Never write "100% offline" or "no model
   downloads" in the app or the listing** — that is exactly the misleading-metadata
   clause Play uses to reject and retroactively sweep apps. Ship the string
   `"Works offline after one-time setup"` (already in `res/values/strings.xml`).
2. **UNLIMITED** — free tier has no daily generation cap on the core tool.
3. **NO UPLOAD** — photos never leave the device. No account. No signup. No email.
   **This is now the #1 pillar, ahead of OFFLINE**, because it stays true in every
   scenario: the segmentation *model* comes down, but no photo ever goes up. Frame
   the pitch as *"Your photos never leave your phone."*

### Naming rules (Google Play compliance — see report section on the "AI naming trap")
- **NEVER** use these strings anywhere in the app, listing, or code identifiers:
  `AI Enhance`, `AI Restore`, `AI Generator`, `AI Avatar`, `AI Colorize`, `AI Retouch`,
  `AI Beauty`, `Magic AI`, `AI Photo Studio`.
- You **may** use "AI" **only** where a real ML Kit model runs, and only with a precise
  qualifier. Example: `"Auto Background Remover"` in UI; in the store description you may
  add `"powered by on-device machine learning — no upload"`.
- Classical image processing must be named classically:
  `Photo Enhance`, `Clarity Boost`, `Smooth Skin`, `Batch Resize`, `Photo Compressor`.
- No generative implication anywhere. The app must never suggest it can *create* pixels
  that were not in the source image (backgrounds, faces, restoration, upscaling "detail").

---

## 2. MILESTONES — BUILD ORDER

**Build strictly in this order. Each milestone must pass its acceptance criteria before
the next one starts.** Do not build ahead. Do not "helpfully" add features from later
milestones into earlier ones.

| # | Milestone | Depends on |
|---|---|---|
| **M0** | Project skeleton, gradle, theme, navigation shell, empty tool screens | — |
| **M1** | **Background Remover** (the main hook) | M0 |
| **M2** | **Passport / ID Photo Maker** (the install magnet) | M0 |
| **M3** | **Batch Resize + Compress** (the subscription driver) | M0 |
| **M4** | Monetization layer: AdManager, AdPolicy, RewardGuard, RevenueCat, Paywall | M1 |
| **M5** | Remote Config wiring + kill switches | M4 |
| **M6** | White Background / Product Cleanup | M1 |
| **M7** | Collage + Templates | M0 |
| **M8** | Image ↔ PDF | M0 |
| **M9** | OCR Text Scanner (Latin + Devanagari) | M0 |
| **M10** | Photo Enhance (classical only) | M0 |
| **M11** | Settings, About, Privacy, Rate-us, Share, License attribution | M4 |
| **M12** | Store-readiness: Data Safety, Play checklist, release build, 16 KB verify | all |

---

## 3. TECH STACK

### Allowed dependencies (nothing else without approval)

| Purpose | Artifact | Notes |
|---|---|---|
| UI | Jetpack Compose + `androidx.activity:activity-compose` | Material 3 |
| Navigation | `androidx.navigation:navigation-compose` | |
| Images | ~~`io.coil-kt.coil3:coil-compose`~~ **REMOVED** | Spec'd, then dropped — bug **#29**. Every image in the app is painted from a locally-decoded bitmap (`Image(...)` / `BitmapPainter`); nothing loads a URL, so Coil was ~2 MB of a ~33 MB AAB budget for zero features. Re-add only if URL-based image loading is ever introduced |
| Async | `org.jetbrains.kotlinx:kotlinx-coroutines-android` | |
| DI | `io.insert-koin:koin-android` **or** manual `ServiceLocator` | Hilt is fine too, but keep it simple |
| Local DB | `androidx.room:room-runtime` + `room-ktx` + KSP compiler | |
| Prefs | `androidx.datastore:datastore-preferences` | |
| File access | `androidx.documentfile:documentfile` + Storage Access Framework | **No `READ_EXTERNAL_STORAGE`** on API 33+ |
| ML: subject seg | `com.google.android.gms:play-services-mlkit-subject-segmentation:16.0.0-beta1` | ⚠️ **UNBUNDLED ONLY — no bundled artifact exists.** Verified 2026-10-29 against Maven Central + Google `com/google/mlkit/group-index.xml` + the official doc (updated 2026-10-28). `16.0.0-beta1` is the only version ever published. App-size impact **~200 KB**; the model is downloaded via Google Play services. **Selfie Segmentation IS bundled (`16.0.0-beta6`) but segments PEOPLE only — it cannot substitute for product photos.** |
| ML: face | `com.google.mlkit:face-detection:16.1.7` **+** `com.google.android.gms:play-services-mlkit-face-detection:17.1.0` | The `com.google.mlkit` artifact is a **stub** — the `Face` class itself ships in the play-services AAR (confirmed by `javap`). `Face` has **no** `UNCOMPUTED_PROBABILITY` constant: the probability getters return a nullable `Float` where `null` already means "not computed" (bug **#25**). `PERFORMANCE_MODE_FAST` |
| ML: OCR latin | `com.google.mlkit:text-recognition` | **Bundled** |
| ML: OCR devanagari | `com.google.mlkit:text-recognition-devanagari` | **Bundled** |
| PDF | `com.tom-roush:pdfbox-android` | Apache 2.0 |
| Ads | `com.google.android.gms:play-services-ads` | + UMP SDK |
| Consent | `com.google.android.ump:user-messaging-platform` | **Mandatory** for EEA/UK fill |
| Billing | **`com.revenuecat.purchases:purchases:6.5.2`** — the *umbrella* artifact | Bug **#18**: `purchases-store-google` carries only 45 store classes — no `Purchases`, no `PurchasesConfiguration`, no `*With` extensions. The umbrella (142 classes) is the only artifact with the API this app calls |
| Billing UI | ~~`com.revenuecat.purchases:purchases-ui-ktx`~~ **empty on Maven** | The paywall is hand-built in `feature/paywall/` — deliberately, so the copy and the pricing anchor stay under our control |
| Analytics | `com.google.firebase:firebase-analytics` | via Firebase BOM 35.1.0. **No `-ktx`** — those coordinates are no longer managed by the BOM (`-ktx` metadata ends at 22.5.0), bug **#15** |
| Crashes | `com.google.firebase:firebase-crashlytics` | via BOM, non-`-ktx`. **Declared but commented out** until the Crashlytics Gradle plugin is added (`YOUR-TURN.md` step 5) |
| Config | `com.google.firebase:firebase-config` | via BOM, non-`-ktx` |

### Explicitly forbidden
- Any HTTP client pointed at an AI service. (OkHttp/Retrofit is allowed **only** for
  RevenueCat/Firebase, which those SDKs manage internally — you should not need to write
  any network code at all.)
- Any `OkHttpClient` you construct yourself.
- TensorFlow Lite custom models (deferred; revisit only after v1 ships).
- RenderScript (deprecated; use `RenderEffect` on API 31+, and CPU/GL fallbacks below).
- Any SDK that collects more data than declared in the Data Safety form.

### Official doc URLs (paste snippets from these into the AI's context)
```
ML Kit Subject Segmentation : https://developers.google.com/ml-kit/vision/subject-segmentation/android
ML Kit Selfie Segmentation  : https://developers.google.com/ml-kit/vision/selfie-segmentation/android
ML Kit Face Detection       : https://developers.google.com/ml-kit/vision/face-detection/android
ML Kit Text Recognition v2  : https://developers.google.com/ml-kit/vision/text-recognition/v2/android
ML Kit overview + pricing   : https://developers.google.com/ml-kit
Google Mobile Ads (Android) : https://developers.google.com/admob/android/quick-start
UMP SDK                     : https://developers.google.com/admob/android/privacy
RevenueCat Android          : https://www.revenuecat.com/docs/platform-resources/sdk-reference/android-sdk
Firebase Remote Config      : https://firebase.google.com/docs/remote-config/get-started?platform=android
PdfBox-Android              : https://github.com/TomRoush/PdfBox-Android
Play 16 KB page size        : https://developer.android.com/guide/practices/page-sizes
```

---

## 4. GRADLE BASELINE

```kotlin
// app/build.gradle.kts — versions are now CONFIRMED by six compile rounds, not guessed.
// compileSdk had to move 36 → 37 (bug #16); targetSdk stays 36.
android {
    namespace = "com.photostudio.pro"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.photostudio.pro"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        // No API keys. No BuildConfig secrets. See HARD CONSTRAINT C1.
        resourceConfigurations += listOf("en", "hi")   // add "es","pt-BR" post-launch
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"),
                          "proguard-rules.pro")
            // ML Kit: keep model metadata
            // RevenueCat, AdMob and Firebase ship their own consumer rules.
        }
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
    }

    bundle {
        language  { enableSplit = true }
        density   { enableSplit = true }
        abi       { enableSplit = true }   // CRITICAL: the all-ABI AAB is ~52 MiB (run #9);
                                           // splits keep each device's download a fraction of that
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}
```

**Size budget (verify with Android Studio APK Analyzer after M3):**
```
Subject Segmentation (UNBUNDLED)   ~0.2 MB (Google doc, verified 2026-10-29)
Face Detection (bundled)           ~6.9 MB
Text Recognition Latin (bundled)   ~4 MB
Text Recognition Devanagari        ~4 MB
Compose + PdfBox                   ~5 MB   (Coil REMOVED #29, Room REMOVED #35: both declared, never used)
AdMob + UMP + RevenueCat + Firebase ~6 MB
App code + resources                ~4 MB
─────────────────────────────────────────
MEASURED AAB, all ABIs (CI run #13) 54,145,529 B (~52 MiB) — this is the UPLOAD size.
MEASURED PER-DEVICE DOWNLOAD (CI run #13, bundletool 1.18.3; device spec
arm64-v8a / en-IN / 420 dpi / SDK 36):  24,227,813 B ≈ 23.1 MiB  ✅
  — under the 70 MB stop-line below, far under Play's 150 MB cap, and far
  under the 200 MB over-cellular warning threshold. Mobile-data friendly.

NOTE: both numbers are now MEASURED BY CI ON EVERY PUSH TO main: step 10
reports the AAB upload size; step 11 runs `bundletool build-apks
--device-spec` and emits the per-device size as a public `::notice::`
annotation (the step is `continue-on-error` — reporting, not a gate; bug
#37). The old 35–40 MB estimate was high: device splits drop 3 of the 4
ABIs and every non-English locale; the single biggest item per device is
`libmlkit_google_ocr_pipeline.so` (arm64 slice) + the bundled OCR models.
```
If the measured total exceeds **70 MB**, stop and report before adding more models.

**16 KB verification (run after M1 and again at M12):**
```bash
./gradlew bundleRelease
zipalign -c -P 16 -v 4 app/build/outputs/apk/release/*.apk   # or inspect .so alignment in APK Analyzer
```

---

## 5. ARCHITECTURE

### Package layout
```
com.photostudio.pro/
├── PhotoStudioApp.kt              // Application: Firebase, RevenueCat, AdMob, UMP init
├── MainActivity.kt
├── core/
│   ├── data/
│   │   ├── local/                 // Room DB, DAOs, DataStore wrapper
│   │   └── repository/            // PhotoRepository, TemplateRepository, UsageRepository
│   ├── domain/
│   │   └── model/                 // PhotoItem, ToolSpec, ExportRequest, EntitlementState
│   ├── image/                     // BitmapOps, JpegOps, GpuOps, MaskOps  ← all on-device
│   ├── ui/                        // Theme, Type, Components, LoadingOverlay
│   └── util/                      // Saf.kt, Exif.kt, Dp.kt, Clock.kt
├── feature/
│   ├── home/                      // tool grid, recent projects
│   ├── bgremover/                 // M1
│   ├── idphoto/                   // M2
│   ├── batch/                     // M3
│   ├── whitebg/                   // M6
│   ├── collage/                   // M7
│   ├── pdf/                       // M8
│   ├── ocr/                       // M9
│   ├── enhance/                   // M10
│   ├── paywall/
│   └── settings/
├── monetization/
│   ├── ads/
│   │   ├── AdManager.kt           // load/show lifecycle, single source of truth
│   │   ├── AdPolicy.kt            // ⭐ the rules engine — see section 7
│   │   ├── RewardGuard.kt         // ⭐ daily caps — see section 8
│   │   └── UmpConsent.kt
│   ├── billing/
│   │   ├── EntitlementManager.kt  // RevenueCat wrapper, exposes StateFlow<EntitlementState>
│   │   └── Offerings.kt
│   └── config/
│       └── RemoteFlags.kt         // typed Remote Config access — see section 9
└── analytics/
    └── Events.kt                  // every event name in ONE file
```

### Non-negotiable architectural rules
1. **`AdPolicy` is the ONLY place that decides whether an ad may show.** No feature screen
   may call `AdManager.showInterstitial()` directly. Violating this is how payers end up
   seeing ads — the #1 revenue-killer in hybrid monetization.
2. **`EntitlementManager` exposes a single `StateFlow<EntitlementState>`.** Everything reads
   from it. It must work offline by using RevenueCat's local cache — never block the UI on a
   network fetch.
3. **Image processing runs on `Dispatchers.Default` inside a `withContext` block**, with
   progress reported via `Flow<Progress>`. Never on `Dispatchers.Main`.
4. **All bitmap work must recycle.** Use a `BitmapPool` or explicit `recycle()` in `finally`.
   OOM crashes on mid-range Indian devices are the top uninstalls driver for photo apps.
5. **Originals are never mutated.** Always produce a new output bitmap.
6. **No `INTERNET` permission is used by app code.** It is present only because AdMob,
   Firebase and RevenueCat SDKs require it. Declare it once, in the manifest, with a comment.

---

## 6. THE EIGHT TOOLS

### M1 — Background Remover ⭐ (main hook, build first)

> **🚨 M1 CORRECTION (verified 2026-10-29 against the official doc, updated
> 2026-10-28).** Subject Segmentation has **no bundled artifact**. Use
> `com.google.android.gms:play-services-mlkit-subject-segmentation:16.0.0-beta1`
> — the only version ever published. Google's doc states: *"Requests you make
> before the download has completed **produce no results**."* That is a silent
> empty output, **not** a crash, so it must be handled as an explicit UI state.
>
> **Required flow (all four already implemented in
> `feature/bgremover/SubjectSegmentationEngine.kt`):**
>
> 1. `<meta-data android:name="com.google.mlkit.vision.DEPENDENCIES"
>     android:value="subject_segment"/>` in the manifest → most devices get the
>    model at install time and never see a download screen.
> 2. `ModuleInstallClient` for an explicit state machine:
>    `READY / DOWNLOADING / NEEDS_NETWORK / UNSUPPORTED`. Never show a spinner
>    that can spin forever, and never show a blank result.
> 3. A **dummy warm-up inference** on a small bitmap after `READY`. Google notes
>    the first inference per session is slower because of model initialisation.
> 4. **Input must be ≥512×512 px.** The engine downscales to a
>    `WORKING_EDGE = 1280` but refuses anything under `MIN_EDGE = 512`.
>
> Poor focus degrades results badly, so the empty-result screen tells the user
> to retake the photo rather than blaming the app.
>
> ⚠️ **Unverifiable without an Android SDK:** the exact
> `com.google.mlkit.vision.segmentation.subject.*` and
> `com.google.android.gms.common.moduleinstall.*` import paths are beta surface.
> Diff them against the doc's import list before the first build — this is
> flagged in a header comment inside the engine file.

*API:** ML Kit **Subject Segmentation** (bundled).
**Flow:** pick photo → segment → preview with checkerboard → pick replacement
(transparent PNG / solid colour / gradient / preset pattern) → export.
**Requirements:**
- Output formats: **PNG with alpha**, JPEG (with chosen background), WebP.
- Replacement backgrounds: transparent, white, black, 8 solid colours, 6 gradients,
  6 subtle textures. **No generated/photographic scenes** (see C1 and naming rules).
- Show a **before/after swipe slider** — this exact screen is Play Store screenshot #1.
- Handle `SegmentationMask` at the mask's own resolution; upscale with bilinear filtering
  and apply a 1–2 px feather to the alpha edge to avoid a hard cut-out look.
- If ML Kit returns a low-confidence / empty mask, show a clear message with tips
  ("use a photo where the subject contrasts with the background") — **never** a silent failure.
**Honest limitation to encode:** Subject Segmentation is **Beta** and is weaker than
Photoroom / remove.bg on hair and fine edges. Do not promise "perfect" edges anywhere.
**Acceptance criteria:**
- [ ] Works fully in airplane mode on first launch (no model download prompt).
- [ ] Processes a 12 MP photo in under 3 s on a mid-range device.
- [ ] Exports a correct transparent PNG that opens in a desktop image viewer.
- [ ] No OOM on a 4000×3000 source image.
- [ ] Free users can do this **unlimited times** (this is the deliberate generous hook).

### M2 — Passport / ID Photo Maker ⭐ (the install magnet)
**API:** ML Kit **Face Detection** (bundled, `PERFORMANCE_MODE_FAST`, with landmarks enabled).
**Data:** a bundled `countries.json` asset with verified specs for **30+ countries**.
Per entry: `name`, `flagEmoji`, `widthMm`, `heightMm`, `dpi`, `backgroundColour`,
`headMinPercent`, `headMaxPercent`, `eyeLineMinPercent`, `eyeLineMaxPercent`,
`maxFileSizeKb`, `format`, `notes`, `sourceUrl`.
**Flow:** pick photo → detect face → auto-crop to the spec's head/eye-line ratios →
replace background with the spec's required colour (reuse M1's segmentation) →
lay out on a print sheet (4×6 in, A4) → export JPEG/PDF at the spec's DPI.
**Requirements:**
- Country picker with search; **India, US, UK, Canada, Schengen, UAE, Australia first**.
- Live guide overlay showing pass/fail: head size %, eye line %, background colour match,
  shadow detection. This is the feature that earns 5-star reviews.
- Print-sheet layout with correct physical dimensions at the target DPI.
- **Never claim "guaranteed acceptance"** anywhere. Use:
  *"Formatted to official government dimensions. Always check your country's latest rules."*
  False acceptance claims are a Deceptive Behavior policy risk.
**Acceptance criteria:**
- [ ] Indian 35×45 mm and US 2×2 in both produce a correctly dimensioned print sheet.
- [ ] Guide overlay flags a photo where the head is too small.
- [ ] Exports at 300 DPI with correct pixel dimensions (e.g. 2×2 in @300dpi = 600×600 px).
- [ ] Works offline, first run.

### M3 — Batch Resize + Compress ⭐ (the subscription driver)
**No ML Kit.** Pure `Bitmap` scaling + JPEG/WebP quality control.
**Requirements:**
- Multi-select via SAF (`ACTION_OPEN_DOCUMENT_TREE` or multiple documents).
- Modes: exact px, longest-edge px, percentage, target file size (binary-search the quality),
  print size at DPI.
- Formats: JPEG (quality slider), WebP (lossy + lossless), PNG.
- Preserve EXIF orientation. Strip GPS/EXIF by default with a visible toggle
  ("Remove location data" — a privacy selling point).
- **This is the free/Pro boundary: free = 1 photo per batch, Pro = up to 100.**
- Progress list with per-file status and a final summary (total MB saved).
**Acceptance criteria:**
- [ ] 50 photos process without OOM (process sequentially or in a bounded pool of 2).
- [ ] "Target 200 KB" mode reliably lands within ±10% of the target.
- [ ] EXIF orientation is respected (no sideways output).
- [ ] Free user hitting the 2nd photo sees the paywall, not a crash or a silent skip.

### M6 — White Background / Product Cleanup
Reuses M1's segmentation. Forces a pure white (#FFFFFF) or near-white background, then
applies: auto-levels, brightness/contrast normalisation, and an optional soft contact shadow.
**Positioning:** "Amazon / eBay / Etsy compliant white background."
**Acceptance criteria:** exported JPEG's background region samples at ≥ 250,250,250.

### M7 — Collage + Templates
Canvas-based. 20 layout templates at launch (2, 3, 4, 6, 9 grid + freeform), adjustable
border width/colour/corner radius, aspect ratios 1:1, 4:5, 9:16, 16:9, 3:4.
**Monetization surface:** 10 templates free, the rest unlocked by
**rewarded ad (24 h)** or **Pro (permanent)**. This is the primary rewarded-ad placement.

### M8 — Image ↔ PDF
PdfBox-Android. Images → single or multi-page PDF with page size choices
(A4, Letter, fit-to-image) and margin control. PDF → images (per page, choose DPI).
**Acceptance criteria:** a 20-image PDF generates in under 10 s and opens in a desktop reader.

### M9 — OCR Text Scanner
ML Kit **Text Recognition v2**, both `text-recognition` (Latin) and
`text-recognition-devanagari`. User picks the script, or run both and merge by confidence.
Output: plain text, copy-to-clipboard, share, save as `.txt`.
**Positioning:** "Scan text in English **and Hindi** — offline." Devanagari support is a
genuine differentiator; most competitors lack it.
**Acceptance criteria:** correctly extracts printed Hindi Devanagari text from a well-lit photo.

### M10 — Photo Enhance (CLASSICAL ONLY)
**No ML.** Implement with `Bitmap`/`RenderEffect`/GLSL:
- Unsharp mask (sharpen) with radius + amount sliders
- Denoise (bilateral or a simple 3×3 edge-preserving filter)
- Auto-levels (histogram stretch) + auto white balance (grey-world assumption)
- Brightness, contrast, saturation, exposure, warmth
- One-tap "Auto" that chains auto-levels → mild sharpen → mild saturation
**FORBIDDEN in this tool:** scratch removal, tear repair, colourisation of B&W photos,
face reconstruction, "restore old photo" claims, any "AI" label.
**UI name:** `Photo Enhance` / `Clarity`. **Never** `AI Enhance` or `AI Restore`.
**Acceptance criteria:** the Auto preset visibly improves a dull, slightly blurry photo
without introducing halos or over-saturation.

---

## 7. ⭐ AdPolicy — THE MOST IMPORTANT FILE IN THE PROJECT

This file decides whether the app makes money or gets 1-star reviews. It must be
**unit-tested exhaustively** (see section 13). Every threshold must be read from
`RemoteFlags` so it can be tuned live without an app update.

```kotlin
/**
 * SINGLE SOURCE OF TRUTH for every ad-show decision in the app.
 * No feature screen may bypass this. AdManager consults it before every show().
 *
 * Design principle: a paying user must NEVER see a forced ad.
 * A new user must NEVER see an ad in their first 90 seconds or first 2 days.
 * Every ad must land on a natural break point (after a completed export/save).
 */
@Singleton
class AdPolicy(
    private val entitlements: EntitlementManager,
    private val flags: RemoteFlags,
    private val usage: UsageRepository,      // persisted daily counters (DataStore/Room)
    private val clock: Clock,
) {

    data class Decision(
        val allowed: Boolean,
        val reason: String,   // logged to Firebase on every decision — this is your debug trail
    )

    /** Interstitials: only AFTER a successful export/save. Never on app open. Never mid-flow. */
    fun canShowInterstitial(ctx: AdContext): Decision {
        if (!flags.interstitialsEnabled)              return no("remote_kill_switch")
        if (entitlements.isPro)                       return no("user_is_pro")
        if (entitlements.isInTrial)                   return no("user_in_trial")
        if (entitlements.hasPurchaseIntent)           return no("purchase_intent_detected")
        if (ctx.isAppOpen)                            return no("never_on_app_open")
        if (ctx.isOnboarding)                         return no("never_during_onboarding")
        if (!ctx.isNaturalBreakPoint)                 return no("not_a_break_point")
        if (ctx.daysSinceInstall < 2)                 return no("day0_2_suppression")
        if (ctx.msSinceSessionStart < flags.firstAdDelayMs)   return no("first_ad_delay")
        if (ctx.msSinceLastInterstitial < flags.interstitialGapMs) return no("gap_not_elapsed")
        if (usage.interstitialsToday >= flags.interstitialDailyCap) return no("daily_cap")
        return yes()
    }

    /**
     * Rewarded ads are USER-INITIATED, so they are never "forced" — the caps below exist
     * to limit abuse, not to protect UX. See RewardGuard for the grant-side limits.
     */
    fun canShowRewarded(ctx: AdContext): Decision {
        if (!flags.rewardedEnabled)                   return no("remote_kill_switch")
        if (ctx.isAppOpen)                            return no("never_on_app_open")
        return yes()   // Pro users MAY still watch rewarded ads if they choose to
    }

    /** Native ads in the template/tool gallery. Suppressed for Pro. */
    fun canShowNative(ctx: AdContext): Decision {
        if (!flags.nativeEnabled)                     return no("remote_kill_switch")
        if (entitlements.isPro)                       return no("user_is_pro")
        if (ctx.daysSinceInstall < 2)                 return no("day0_2_suppression")
        return yes()
    }

    private fun yes() = Decision(true, "allowed")
    private fun no(r: String) = Decision(false, r).also { Events.logAdBlocked(r) }
}

data class AdContext(
    val isAppOpen: Boolean,
    val isOnboarding: Boolean,
    val isNaturalBreakPoint: Boolean,   // true ONLY after export/save completes
    val daysSinceInstall: Int,
    val msSinceSessionStart: Long,
    val msSinceLastInterstitial: Long,
)
```

### Default thresholds (all overridable via Remote Config)
| Rule | Default | Why |
|---|---|---|
| First interstitial delay | **90 000 ms** | A 60–90 s delay lifts D1 retention ~5–8% with no measurable revenue loss |
| Gap between interstitials | **180 000 ms** | Industry floor is 180–240 s |
| Interstitials per day | **3** | Max 3–4/user/day; one disruptive interstitial costs ~6–7% churn |
| Day 0–2 interstitials | **0** | Suppress all full-screen ads during onboarding |
| Rewarded grants per day | **8** | See RewardGuard |
| Banner ads | **DISABLED FOREVER** | India banner eCPM ≈ $0.08–0.40; accidental clicks risk an AdMob ban |
| App-open ads | **DISABLED at launch** | Never a full-screen ad on launch — top uninstall trigger |

### Ad placements (exact, no others)
| Format | Where | When |
|---|---|---|
| **Rewarded** | Remove watermark on export | User taps "Watch ad to remove" |
| **Rewarded** | Unlock a premium collage template for 24 h | User taps the 🔒 template |
| **Rewarded** | +5 photos on a free batch | User exceeds the free batch of 1 |
| **Rewarded** | +3 HD export credits | User hits the daily HD limit |
| **Rewarded** | One-off full-resolution export | User taps "Export in 4K (free)" |
| **Interstitial** | After export/save **completes** | Only via `AdPolicy`, only on a break point |
| **Native** | Template gallery, tool grid footer | Only for non-Pro, after Day 2 |

**Never** place an ad: on app open, before a navigation button, during processing, during
onboarding, on an error screen, or immediately after another ad.

---

## 8. ⭐ RewardGuard — abuse control WITHOUT a backend

There is no SSV server (see HARD CONSTRAINT C2). Abuse is limited client-side instead.
This is a deliberate, accepted trade-off — the leak is small and detectable.

```kotlin
/**
 * Limits the VALUE a forger can extract, since we cannot cryptographically prove
 * a rewarded ad was watched. Phase 2 may add AdMob SSV — see SPEC section 14.
 */
object RewardGuard {

    /** Hard cap on reward GRANTS per user per day. Read from RemoteFlags. */
    fun canGrant(kind: RewardKind, todayCount: Int, cap: Int): Boolean = todayCount < cap

    /**
     * RULE: a rewarded ad may only ever grant something SMALL, TEMPORARY and REPLACEABLE.
     * These are the ONLY allowed reward kinds. Adding a new one requires a spec change.
     */
    enum class RewardKind {
        REMOVE_WATERMARK_ONCE,   // single export
        TEMPLATE_24H,            // expires
        BATCH_PLUS_5,            // one batch
        HD_CREDIT_3,             // expires at end of day
        FULL_RES_EXPORT_ONCE,    // single export
        // ❌ NEVER: PRO_1_MONTH, LIFETIME_UNLOCK, SUBSCRIPTION_TRIAL_EXTEND
    }
}
```

**Rule: a rewarded ad must NEVER grant Pro, a subscription, a trial extension, or lifetime
access.** Those are purchasable only. This makes the maximum gain from forgery trivial.

### Abuse detection (analytics only — no backend needed)
Log `reward_granted` to Firebase on every grant. Weekly, a human compares:
```
AdMob dashboard → Rewarded impressions        = A
Firebase        → reward_granted event count  = B

B / A ≈ 1.0   → healthy
B / A > 1.5   → investigate
B / A > 3.0   → build Phase 2 SSV (section 14)
```
Exclude Play Console licence testers from this comparison. Test-mode impressions do not
appear in the AdMob dashboard, so this check is only valid after the production release.

---

## 9. RemoteFlags — every tunable number lives here

Firebase **Remote Config on the Spark plan** is the free, no-backend way to change ad
frequency, paywall copy and feature availability **without waiting for a Play review**.

```kotlin
object Keys {
    const val ADS_INTERSTITIAL_ENABLED   = "ads_interstitial_enabled"    // bool
    const val ADS_INTERSTITIAL_DELAY_MS  = "ads_interstitial_delay_ms"   // long
    const val ADS_INTERSTITIAL_GAP_MS    = "ads_interstitial_gap_ms"     // long
    const val ADS_INTERSTITIAL_DAILY_CAP = "ads_interstitial_daily_cap"  // long
    const val ADS_REWARDED_ENABLED       = "ads_rewarded_enabled"        // bool
    const val ADS_REWARDED_DAILY_CAP     = "ads_rewarded_daily_cap"      // long
    const val ADS_NATIVE_ENABLED         = "ads_native_enabled"          // bool
    const val PAYWALL_VARIANT            = "paywall_variant"             // string
    const val PAYWALL_SHOWN_AFTER_SCREENS= "paywall_after_screens"       // long
    const val FREE_EXPORTS_PER_DAY       = "free_exports_per_day"        // long
    const val FREE_BATCH_SIZE            = "free_batch_size"             // long
    const val TOOL_ENABLED_PREFIX        = "tool_enabled_"               // + tool id
    const val SSV_STRICT_MODE            = "ssv_strict_mode"             // bool (Phase 2)
}
```

**Launch defaults (critical):** `ads_interstitial_enabled = false`.
Interstitials stay OFF for the first 30 days and are switched on remotely, gradually,
only if retention holds. This is a business decision, not a code decision — but the code
must make it a one-line remote change.

Every tool gets a `tool_enabled_<id>` kill switch so a broken tool can be hidden
instantly instead of shipping an emergency update.

---

## 10. MONETIZATION — SUBSCRIPTION

### Entitlement model
One entitlement, named exactly: **`pro`**
```kotlin
val isPro: Boolean get() = purchases.entitlements["pro"]?.isActive == true
```
**This must work offline.** RevenueCat caches entitlements locally; read the cached value
and never gate the UI behind a network round-trip.

### Products (create in Play Console, then map in the RevenueCat dashboard)
| Entitlement / product | Global | India | Notes |
|---|---|---|---|
| `pro_weekly` | **$6.99 / week** | **₹99 / week** | 7-day free trial |
| `pro_yearly` | **$34.99 / year** | **₹999 / year** | 7-day free trial, **anchor product** — show the per-week equivalent to make it look cheap |
| `pro_lifetime` | **$59.99 once** | **₹1,999 once** | Non-consumable. Add at Day 60+, not at launch |

- **Trial length must be ≥ 7 days.** Trials of 7+ days convert at 37–43% versus 25.5%
  for short trials. Do not "optimise" this down to 3 days.
- Add an **introductory offer** and later a **win-back offer** — only ~9.3% of apps use
  offers at all, so this is easy upside.
- The paywall must display: exact price, exact billing period, that it auto-renews,
  the trial-to-paid conversion date, and how to cancel. **Missing any of these is a
  Play policy violation.**

### Free vs Pro
| | **Free** | **Pro** |
|---|---|---|
| Background removal | **Unlimited** (deliberate generous hook) | Unlimited |
| Exports per day | 3 | Unlimited |
| Export resolution | SD (up to 1080 px) | HD / 4K / original |
| Watermark | Yes on the five image tools; **Batch free exports stay CLEAN until the 100k+ downloads phase** (owner decision 2026-10-31 — ads are the early monetization lever, watermark is the late one) | **None** |
| Batch size | **1 photo** | **100 photos** |
| Collage templates | 10 | 200+ |
| ID photo countries | 10 | 30+ |
| OCR | 5 scans/day | Unlimited |
| Forced ads | Rewarded + capped interstitial + native | **ZERO** |

### Paywall placement — "soft-hard hybrid"
1. Show the paywall **after ~3 onboarding screens** (`PAYWALL_SHOWN_AFTER_SCREENS`).
2. It must contain a clearly visible escape: **"Continue with the free version"** —
   which drops the user into the ad-supported tier.
3. Rationale: hard paywalls convert far better at Day 35 (10.7% vs 2.1%), but a cold
   paywall with no escape destroys ad inventory in low-ARPU markets like India. The
   hybrid captures both.
4. Contextual paywalls (higher intent, higher conversion): on the **2nd batch photo**,
   on the **4th export of the day**, on a **🔒 template**, on the **HD export** button.
5. **Suppress every forced ad the moment `isPro` flips true**, including the cached case.
   Desync between the ad SDK and the entitlement state is the #2 revenue-killer and
   directly causes refunds.

---

## 11. DATA LAYER

### Room
```
ExportEntity   (id, toolId, createdAt, widthPx, heightPx, bytesOut, isPro, adShownAfter)
UsageDayEntity (dayKey: "2026-10-29", interstitialsShown, rewardedGranted, exportsDone,
                ocrScansDone, batchPhotosDone)
ProjectEntity  (id, toolId, sourceUri, createdAt, thumbnailPath)   // recent projects
```
`UsageDayEntity` is what backs every daily cap. `dayKey` is derived from the device's
local date; reset is implicit on key change.

### DataStore (preferences)
`onboarding_completed`, `consent_state`, `install_timestamp`, `session_start_timestamp`,
`last_interstitial_timestamp`, `strip_exif_default`, `preferred_export_format`,
`rate_prompt_shown_count`, `locale_override`.

### Privacy / storage rules
- **No photos are ever uploaded anywhere.** No analytics event may contain image data,
  a file path outside the app sandbox, or EXIF metadata.
- Use the **Storage Access Framework**. Take no `READ_EXTERNAL_STORAGE` /
  `READ_MEDIA_IMAGES` permission on API 33+. On API 24–32, request the minimum needed
  and explain why in a rationale dialog.
- Persisted URIs: take `PERSISTABLE_URI_PERMISSION` only for the user's chosen output folder.
- **No user accounts. No login. No signup. No email collection.** If any SDK suggests one,
  refuse it. This is a deliberate decision: accounts would trigger Play's account &
  data-deletion requirement plus GDPR / India DPDP Act liability for zero revenue benefit.
- The Data Safety form should be answerable as "no data collected" **except** for the
  AdMob advertising identifier and RevenueCat's anonymous purchase records — both of
  which must be declared honestly. Misdeclaring data safety is the #1 Play removal reason.

---

## 12. WHAT NOT TO BUILD

If asked for any of these, refuse and point at this section.

| ❌ Do not build | Why |
|---|---|
| Cloud AI / generative features | HARD CONSTRAINT C1. No API budget exists. |
| AI background **generation** ("put it on a beach") | Requires a diffusion model. Offer solid colours, gradients, patterns instead. |
| Old photo **restoration** / scratch removal / colourisation | Requires generative inpainting. `Photo Enhance` (M10) is classical only. |
| AI avatars, text-to-image, style transfer | Impossible on-device within the size budget. |
| Magic eraser / object removal | Inpainting = generative. Offer a manual clone-stamp instead. |
| AI super-resolution upscaling | Defer past v1. Lanczos/bicubic resize is in M3. |
| User accounts, login, signup, social sign-in | See section 11. Zero revenue benefit, large legal cost. |
| Cloud sync, cloud backup, photo library on a server | Egress cliff + destroys the "no upload" USP. Export to the user's own Drive via SAF instead. |
| Any server code, API, webhook, edge function | HARD CONSTRAINT C2. |
| AdMob **SSV** endpoint | Phase 2 only. See section 14. |
| Banner ads | India eCPM ≈ $0.08–0.40, bad UX, accidental-click AdMob ban risk. |
| App-open ads | Top uninstall trigger. Revisit only after Day 90. |
| Firebase Cloud Functions / Cloud Storage | Blaze-only; Firebase has no hard spending cap. |
| `READ_CALL_LOG`, location, contacts, camera-as-permission | Not needed. Every extra permission is a Play review risk. |
| Any string implying guaranteed ID-photo acceptance | Deceptive Behavior policy risk. |

---

## 13. TESTING

**Unit tests (required, must pass before a milestone is called done):**
- `AdPolicyTest` — the highest-value tests in the project. Cover at minimum:
  Pro user → no interstitial; trial user → no interstitial; Day 0 → no interstitial;
  Day 1 → no interstitial; Day 3 + 91 s elapsed + 180 s gap + 2 shown today → allowed;
  3 shown today → blocked; 89 s elapsed → blocked; on app open → blocked;
  not a break point → blocked; kill switch off → blocked.
- `RewardGuardTest` — cap enforcement, per-kind isolation, day rollover.
- `EntitlementManagerTest` — offline cached value is honoured; a lapsed subscription
  flips `isPro` to false; a restored purchase on a fresh install flips it to true.
- `BatchOpsTest` — target-file-size search converges; EXIF orientation preserved.
- `CountrySpecTest` — every entry in `countries.json` produces correct pixel dimensions
  at its declared DPI.

**Integration / manual:**
- **AdMob must be in TEST MODE for the entire build and closed-test period.**
  ```kotlin
  val testConfig = RequestConfiguration.Builder()
      .setTestDeviceIds(listOf(BuildConfig.DEBUG_DEVICE_ID))
      .build()
  MobileAds.setRequestConfiguration(testConfig)
  ```
  Clicking your own live ads, or asking testers to, causes a **permanent AdMob ban**.
- **Play Billing must use Licence Testing** with tester accounts added in Play Console —
  never real purchases during development.
- Airplane-mode test after every ML Kit milestone: the app must be fully functional.
- Low-memory test: enable "Don't keep activities" + a mid-range device profile.

---

## 14. PHASE 2 (POST-LAUNCH) — DO NOT BUILD NOW

Recorded here so the option is not lost. **Do not implement any of this before v1 ships
and the abuse ratio in section 8 exceeds 3.0.**

**AdMob rewarded SSV on Cloudflare Workers free tier** (100 K requests/day, no credit card,
commercial use permitted, exceeding the cap returns Error 1027 = throttle, never a bill):
- `GET /ssv` — verify AdMob's ECDSA signature against keys from
  `https://www.gstatic.com/admob/reward/verifier-keys.json`.
  **Cache the keys.** Fetching them per request exhausts the 10 ms CPU budget.
- D1 table `redemptions(transaction_id PK, user_id, item, amount, ts, claimed, created_at)`
  for replay protection.
- `GET /redeem` — confirm and set `claimed = 1`.
- Store only a random local UUID. **No personal data.**
- **Fail OPEN, never closed.** If the Worker is unreachable, grant the reward anyway and
  log `ssv_unavailable`. Never punish an honest user for your own server failure.
- Guard with the `SSV_STRICT_MODE` remote flag, default `false`.
- Known caveat: the Google Mobile Ads SDK has **no retry logic** for the SSV callback, so a
  user who briefly loses connectivity mid-ad will pass the client callback but never reach
  the server — and will look fraudulent. For an India-first, patchy-data, offline-positioned
  app this is a strong argument to keep failing open, or to never build SSV at all.

Also Phase 2: AdMob **Mediation with bidding** (only after ~10,000 daily impressions;
lifts revenue 20–40%), win-back offers, `es` / `pt-BR` localisation, native ad expansion.

---

## 15. M12 — STORE READINESS CHECKLIST

**Build**
- [ ] `bundleRelease` succeeds; `.aab` produced, no universal APK
- [ ] R8 minification on; no ML Kit / RevenueCat / AdMob class stripped (check release build runs)
- [ ] 16 KB page-size alignment verified for every `.so`
- [ ] `targetSdk = 36`, 64-bit only, measured AAB download size recorded
- [ ] Version code/name set; no debug flags in release
- [ ] AdMob **TEST MODE OFF** in the release variant, ON in debug — automated via `BuildConfig.DEBUG`, never manual

**Play Console**
- [ ] Target audience declared as **18+** (avoids the Families ad policy)
- [ ] "Contains ads" declared **Yes**
- [ ] Data Safety completed honestly, including the AdMob advertising identifier
- [ ] Privacy policy URL live (Cloudflare Pages) and reachable from inside the app
- [ ] **Developer website set to the Cloudflare Pages URL** and `app-ads.txt` hosted there:
      `google.com, pub-XXXXXXXXXXXXXXXX, DIRECT, f08c47fec0942fa0`
      (also paste this line into the AdMob dashboard)
- [ ] App category: Photography. Tags avoid the word "AI" as the primary keyword.
- [ ] Health / Financial declarations filled even if unused
- [ ] No AI-generated icon, feature graphic or screenshots → **no AI Asset Declaration
      needed, no public "AI-generated" badge.** Use real app screens only.
- [ ] Screenshots: #1 before/after background removal · #2 "100% Offline" ·
      #3 "Unlimited free edits" · #4 "Your photos never leave your phone" ·
      #5 ID photos for 30+ countries · #6 batch resize 100 photos
- [ ] Closed testing track uploaded, **12+ testers confirmed opted in** (recruit 18–25)
- [ ] The fixed **14-day** closed-test wait started
- [ ] Production questionnaire drafted

**Never do**
- [ ] Never click your own ads, and never ask testers to
- [ ] Never ship a screenshot showing a feature that does not exist in the build
- [ ] Never put "AI" in the title

---

## 16. NORTH STAR METRIC

**Blended ARPDAU** = (ad revenue + IAP + subscription revenue) ÷ DAU, per day.

Never report ads and subscriptions in separate silos — that is how teams "win" on ad
revenue while quietly destroying subscription conversion.

**Raise ad frequency only if:** blended ARPDAU rises **AND** D7 retention drops by ≤ 1 pp.
**Every test must run ≥ 7 days.** A 3-day test always falsely concludes "more ads wins".

| Guardrail | Target |
|---|---|
| D1 retention | > 35% |
| **D7 retention** | **> 15%** |
| D30 retention | > 8% |
| Trial starts / installs | > 25% |
| Trial → paid | > 35% |
| Rewarded engagement | 15–20% of DAU |
| Crash-free sessions | > 99.5% |
| Refund rate | < 2% |
| Play rating | > 4.3 ★ |

---

## 17. KICKOFF PROMPT

Paste this as your **first message** to Antigravity / Jules after committing this file:

```
Read SPEC.md in this repository completely, including section 0 (HARD CONSTRAINTS).

Confirm back to me, in under 150 words:
1. The three hard constraints C1, C2, C3, in your own words.
2. Which milestone you will start with, and which ones you will NOT touch yet.
3. Any place where you believe SPEC.md is ambiguous or contradictory. Ask me before guessing.

Then begin Milestone M0 only:
- Gradle project per section 4 (compileSdk/targetSdk 36, minSdk 24, AAB with abi splits,
  R8 on for release, version catalog in libs/gradle/libs.versions.toml).
- Package structure per section 5, with empty-but-navigable Compose screens for all
  8 tools plus Home, Paywall and Settings.
- Material 3 theme with a light + dark palette. No placeholder text like "Coming Soon":
  every screen shows its real tool name and a disabled state explaining it arrives in a
  later milestone.
- A single NavHost wiring everything.
- The `INTERNET` permission declared once with an explanatory comment.
- NO API keys, NO network calls, NO server code, NO ML Kit integration yet.

Where a dependency version is required, write the artifact name and mark it
/* TODO: confirm latest version */ instead of guessing (HARD CONSTRAINT C4).

Stop after M0 and report: files created, the exact gradle coordinates you used,
and the measured debug APK size. Wait for my approval before M1.
```

---

*Spec version 1.0 · 29 October 2026 · Companion report: `master-plan-2026.html`*
