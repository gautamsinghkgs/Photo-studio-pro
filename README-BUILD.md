# Photo Studio Pro — how to actually build this

> **`README.md` is the master document** — project state, rules, architecture and next
> steps. This file is only the build mechanics. Read `README.md` first.

This project is **complete source code**, not a template and not a snippet — and as of
**2026-10-30 it compiles**. `:app:compileDebugKotlin` and `:app:testDebugUnitTest`
(**117 tests, 0 failures, 0 skipped**) are both green under AGP 9.1.1 / Gradle 9.3.1 /
JDK 17 / compileSdk 37 — proven inside a sandbox with **2 GB of RAM** and no Android Studio,
which is the strongest possible evidence that it is not environment-dependent.

What has **never** run anywhere is dexing, R8 and packaging: the sandbox is OOM-killed at
`dexBuilderDebug`, so those stages belong to your machine or to CI. Expect **1–3 iterations**
to your first successful AAB rather than the 3–6 predicted before the compile went green —
and the most likely first failure is R8's *"Missing class detected"* (fix: add that package
as `-dontwarn` in `app/proguard-rules.pro`), followed by the AAB size measurement.

---

## What you need on YOUR machine

| Requirement | Minimum | Why |
|---|---|---|
| RAM | **8 GB** (16 GB comfortable) | Kotlin + AGP + Gradle OOM below this. 2 GB is enough to *compile and test* (proven) but **not** to dex or run R8 |
| Disk | **15 GB free** | SDK + Gradle caches + first build |
| JDK | **17** | AGP 9.x refuses to run on anything older |
| Android Studio | Latest stable (AGP **9.1.1** support) | Brings the SDK and Gradle. An older Studio will offer to "downgrade AGP" — **refuse it**; see bug #16 |
| Gradle | **9.3.1** | AGP 9.1.1's minimum is 9.1.0; 9.3.1 is its default and what CI installs |
| Android SDK Platform | **37.0** (`platforms;android-37.0`) | `compileSdk = 37`, forced by the 2026 AndroidX AAR metadata (bug #16). **`targetSdk` stays 36** — that is what Play enforces from 31 Aug 2026 |
| SDK Build-Tools | **36.0.0** | Required by AGP 9. Also what the 16 KB `zipalign` check runs from |

---

## First build — do these in order

```
1.  Install Android Studio. Let it install the SDK.
2.  File -> Open -> select the photo-studio-pro folder.
3.  Let Gradle sync finish. It will download ~1.5 GB of dependencies the first time.
    This can take 10-30 minutes. Do not cancel it.
4.  Build -> Build Bundle(s)/APK(s) -> Build APK(s)
```

**Read every error, top to bottom.** The first error is usually the real one; the next
twenty are fallout from it. Copy the *first* error into an AI tool and ask for that one
fix only. Do not paste 200 lines and ask "fix my project".

---

## The errors you will most likely hit, in order of probability

### 1. `Unresolved reference: SubjectSegmenter` or similar ML Kit imports

The Subject Segmentation API is **beta** (`16.0.0-beta1` is the only version ever
published). Beta package paths change and AI training data is stale on them.

**Fix:** open
`https://developers.google.com/ml-kit/vision/subject-segmentation/android`
and copy the import block **from the page**, not from memory. Then correct the imports
at the top of `feature/bgremover/SubjectSegmentationEngine.kt`. There is a warning
comment there marking exactly which imports to verify.

The same applies to `com.google.android.gms.common.moduleinstall.*` in that file.

### 2. `Execution failed for task ':app:processDebugGoogleServices'`

You have not added `app/google-services.json` yet.

**Fix:** Firebase console → Project settings → Your apps → Android → download
`google-services.json` into the `app/` folder. Until you do, the two Firebase plugin
lines in `app/build.gradle.kts` are **commented out on purpose**. Uncomment them
after the file is in place, and uncomment the matching `classpath`/`plugins` lines in
the root `build.gradle.kts`.

### 3. Compose / Kotlin version mismatch

`compose-bom = 2026.09.00` and `kotlin = 2.3.21` were resolved from live Maven metadata
on **2026-10-29**. If a newer BOM exists by the time you build, Gradle will tell you.
Update `gradle/libs.versions.toml` — **every version in this project lives in that one
file**. Do not hardcode versions anywhere else.

### 4. `RevenueCat: invalid API key`

`REVENUECAT_PUBLIC_KEY` is a placeholder in `app/build.gradle.kts`. Put your real
public key there. The public key is safe to embed in a client — RevenueCat documents
this. There is **no private key anywhere in this project** and there must never be one.

### 5. OutOfMemoryError during the build

Increase the heap in `gradle.properties` (`org.gradle.jvmargs`). Also close Chrome.
Do not enable `org.gradle.parallel` on a machine with 8 GB.

### 6. AdMob crash on launch

The manifest currently contains **Google's sample AdMob app ID**
(`ca-app-pub-3940256099942544~3347511713`). It is flagged with a comment. Replace it
with your own app ID before anything else, or ads will not initialise.

---

## Files you must edit before shipping (complete list)

| File | What to change |
|---|---|
| `app/build.gradle.kts` | `REVENUECAT_PUBLIC_KEY` placeholder → your real key |
| `app/src/main/AndroidManifest.xml` | Sample AdMob app ID → your app ID |
| `monetization/ads/AdUnitIds.kt` | Sample ad unit IDs → your 4 real ones |
| `feature/settings/SettingsScreen.kt` | `privacyUrl` → your Cloudflare Pages URL |
| `deliverables/app-ads.txt` | `pub-XXXXXXXXXXXXXXXX` → your AdMob publisher ID |
| `deliverables/privacy-policy.html` | `[YOUR LEGAL NAME]`, `[YOUR-EMAIL@DOMAIN]`, `[YOUR-PROJECT]` |
| `deliverables/store-listing.md` | `[YOUR-PROJECT]`, `[YOUR-EMAIL@DOMAIN]` |
| `app/google-services.json` | **Add this file** (does not exist yet) |
| RevenueCat dashboard | Create the `pro` entitlement + attach all 3 products, or the paywall shows "Loading prices…" forever |
| `keystore.properties` | **Create this file** for release signing (see below) |
| `assets/countries.json` | Spot-check the top 10 markets against each `sourceUrl` |

---

## Release signing

Create `keystore.properties` in the project root (**it is git-ignored**):

```
storeFile=../photostudiopro.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=photostudiopro
keyPassword=YOUR_KEY_PASSWORD
```

Generate the keystore once and **back it up somewhere other than your laptop**:

```
keytool -genkey -v -keystore photostudiopro.jks -keyalg RSA -keysize 2048 \
        -validity 10000 -alias photostudiopro
```

⚠️ **If you lose this keystore you can never update the app again.** Google Play
App Signing mitigates this, but keep your own backup regardless.

---

## Running the tests

```
./gradlew test
```

**117 unit tests already pass** — and since 2026-10-30 they pass under **real Gradle**
(`:app:testDebugUnitTest`: 0 failures, 0 skipped), which is strictly stronger than the
earlier kotlinc-1.9.24 stub harness: that harness could not see bug **#28** at all. The
counts below are parsed from `app/build/test-results/testDebugUnitTest/*.xml`, not copied:

| Test class | Tests | What it protects |
|---|---|---|
| `AdPolicyTest` | 21 | The most important logic in the app: a paying user never sees a forced ad, banners stay permanently off, every threshold and every denial reason |
| `RewardGuardTest` | 11 | The abuse defence: the daily cap fails closed, and no reward kind can ever grant Pro or lifetime |
| `IdPhotoMathTest` | 31 | mm→pixel conversion, print-sheet tiling, and the compliance checks |
| `QualitySearchTest` | 9 | Hitting a government portal's file-size ceiling at the highest possible quality |
| `LayoutMathTest` | 42 | Collage tiling, centre-crop geometry, PDF page placement, and the white-background flood fill |
| `CollageTemplateContractTest` | 3 | The free/Pro template boundary must stay at exactly 10 free |

The pure maths is deliberately split out of the Android-facing tools into two
import-free files — `core/image/QualitySearch.kt` and `core/image/LayoutMath.kt` —
so it can be tested on a plain JVM with no emulator. If you add maths to a tool,
add it there and add a test.

Writing those tests found **five real bugs**, all now fixed and locked by a
regression test. The full ledger is now **12 bugs** — see `README.md` §8 — of which 4 were
found by `tools/check_wiring.py` and could not have been caught by any test or compiler:

1. **`RewardGuard.canGrant(-1)` returned `true`.** A corrupted DataStore counter, or a
   user rolling the device clock backwards to reset the daily counters, would have been
   handed unlimited rewards. Now requires `grantsToday in 0 until cap` — fails closed.
2. **`IdPhotoMath.checkBackground()` rejected every coloured background.** Its
   low-saturation gate ran unconditionally, so **every** Indonesian (red background) and
   Malaysian (blue background) user got a false "background is tinted" warning on a
   perfectly compliant photo. The gate now only applies when the *spec* asks for an
   achromatic background.
3. **`IdPhotoViewModel` tried only one orientation when tiling a print sheet.** Trying
   both takes a 4×6 sheet from **6 copies to 8** for a 35×45 mm photo — free paper and ink.
4. **`LayoutMath.computeThresholds()` put the gate ABOVE the observed background.** On a
   dimly lit white wall (luminance ~120) the gate came out at ~173, so the White
   Background tool **whitened nothing while reporting success** — no crash, no error, just
   a photo that looked unchanged. Both gates now sit at or below the border percentile.
5. **`floodWhiten()` accepted a `high` threshold and never used it**, so the "hysteresis"
   described in its own docstring did not exist. It now seeds from `high` and propagates
   at `low`, which is what keeps a shadow attached to the subject from being blown out.

---

## Bugs found by auditing the wiring (not by tests)

> There are now **five** of these, not two. `README.md` §8 has the complete ledger.
> Four were found by `tools/check_wiring.py`, which is now a CI gate, so this class of
> bug is caught mechanically instead of by luck. Run it any time:
> `python3 tools/check_wiring.py`

These would not have been caught by any test, because the code compiled and the
logic was individually correct — the *wiring* was missing:

1. **No ad could ever have shown, on any device, for any user.** `AdPolicy` blocks
   every ad while `isOnboarding` is true, and `AdCoordinator` derives that from
   `Prefs.onboardingDone()`. But nothing in the app ever called `setOnboardingDone()` —
   the method existed with zero callers. So the flag stayed false forever and every
   single ad decision returned `never_during_onboarding`. It would have looked exactly
   like "AdMob isn't filling". Fixed by building `feature/onboarding/OnboardingScreen.kt`
   and gating `Nav` behind it on first launch (including the Skip path).

2. **Batch exports were written to `cacheDir`.** Android deletes cache contents whenever
   storage runs low, and the user has no way to browse there — so 40 resized photos could
   silently vanish. Fixed with a Storage Access Framework folder picker
   (`OpenDocumentTree` + `takePersistableUriPermission`), and the cacheDir fallback was
   deleted rather than left as a safety net.

---

## What is NOT in this project

- ❌ No server code of any kind. No Cloud Functions, no Workers, no API. That is deliberate.
- ❌ No AI API calls. No Gemini, no OpenAI, no Replicate, no fal.ai. Everything is on-device.
- ❌ No user accounts, no login, no email collection, no cloud sync.
- ❌ No banner ads and no app-open ads. Both are permanently disabled in code, with no
  configuration flag that can turn them on.
- ❌ No `google-services.json` (you must add it — it is tied to your Firebase project).
- ❌ No keystore (you must generate it).
- ✅ All eight tools (M1–M10) are implemented and routed. `feature/later/` is kept only
  for FUTURE milestones; nothing routes to it any more.
- ⚠️ **The RevenueCat purchase flow is written but has never run against a real
  offering.** `monetization/billing/PurchaseFlow.kt` is isolated in one file for exactly
  this reason: the RevenueCat API surface has changed shape several times and AI training
  data mixes the variants. If it fails to compile, fix that one file against
  `revenuecat.com/docs/getting-started/entitlements`. Until offerings exist in the
  dashboard the paywall keeps its buttons **disabled** rather than showing a placeholder
  price, because a wrong price is a Play policy violation.
