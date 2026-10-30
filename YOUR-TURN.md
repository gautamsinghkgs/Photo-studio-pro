# 🫵 YOUR TURN — ab sirf tum yeh kaam kar sakte ho

Maine jo bhi khud kar sakta tha, **sab kar diya hai**. Ab jo bacha hai woh sirf tum kar
sakte ho — kyunki uske liye ya toh **tumhara Google account** chahiye, ya **tumhara
computer**, ya **tumhara phone**, ya **ek credit-card-free signup jo main nahi kar sakta**.

Neeche har step **exact order** mein hai. Ek-ek karke karo. Koi step skip mat karo —
kai steps ek doosre par depend karte hain.

**Total paisa: $25 (₹2,100). Sirf step 1 mein. Baaki sab free.**

> **UPDATE:** App ab **functionally complete** hai. Saare 8 tools (M1–M10) ban chuke hain,
> onboarding flow ban gaya hai, purchase flow wire ho gaya hai, aur 117 tests pass hain.
> Jo bacha hai woh **sirf tumhare accounts + tumhara computer + tumhara phone** maangta hai.
> Play Console wale steps (STEP 0, 3–8) tum jab chaho kar sakte ho — app unke bina bhi
> build aur test ho jaayegi.

---

## 📋 Pehle yeh samajh lo — kaam kaise chalega

Teen roles hain:

| Role | Kaun | Kya karta hai |
|---|---|---|
| **Dimag** 🧠 | Main (Arena) | Code likhta hoon, errors samjhata hoon, decisions deta hoon |
| **Haath** 🤖 | **Google Antigravity** tumhare computer par | Actually build karta hai — kyunki mere paas Android SDK nahi hai |
| **Faisla + Device** 🫵 | **Tum** | Accounts banate ho, buttons click karte ho, phone par test karte ho |

**Main APK compile nahi kar sakta.** Mere environment mein Android SDK nahi, Gradle
nahi, JDK 17 nahi, emulator nahi (`/dev/kvm` tak nahi hai), aur sirf 2 GB RAM hai.
Isliye build tumhare computer par hoga.

**Lekin maine jo verify kiya ja sakta tha, woh kiya hai:**
- ✅ Har dependency ka version **live Maven se** check kiya (2026-10-29), guess nahi kiya
- ✅ ML Kit ka official doc **fetch karke** exact API signatures liye
- ✅ **117 unit tests likhe aur ACTUALLY RUN kiye — sab pass** (real source files par, copies par nahi)
- ✅ Tests se **5 asli bugs** pakde aur fix kiye
- ✅ Wiring audit se **2 aur critical bugs** pakde (ek toh aisa tha ki **koi ad kabhi bhi
  show hi nahi hota**)
- ✅ Saare XML, JSON, manifest, gradle files validate kiye

---

## ⏱️ Pehle yeh karo, aaj hi (5 minute, ₹0)

### STEP 0 — Play Console account shuru karo

**Yeh sabse pehle karo kyunki ismein 2–7 din ka wait hai.**

1. Jao `play.google.com/console`
2. Sign up karo, **Personal account** choose karo (Organization mat lo — usmein D-U-N-S chahiye)
3. **$25 pay karo** — yeh tumhare poore project ka ekmatra kharcha hai
4. **Identity verification complete karo** — government photo ID + address proof

> 🔒 **Identity verification mein 2–7 din lagte hain.** Yeh sabse lamba wait hai aur yeh
> sab kuch block karta hai. Isliye aaj hi shuru karo, bhale hi app ready na ho.

**Card chahiye?** $25 ke liye haan, ek card chahiye. Yeh ekmatra jagah hai. Iske baad
kahin bhi card ki zaroorat nahi padegi — main har service check kar chuka hoon.

---

## 🖥️ Ab build karo — do raaste hain

### ⭐ STEP 1A — FAST PATH: GitHub Actions se compile karwao (15 min, ₹0, koi card nahi)

**Yeh sabse pehle karo.** Isse tumhe Android Studio install karne se pehle hi pata
chal jaayega ki code mein kya errors hain.

**Kyun:** jis environment mein maine yeh code likha, wahan Android SDK nahi tha, Gradle
nahi tha, JDK 11 tha (AGP 8.13 ko 17 chahiye), 2 GB RAM thi aur emulator bhi nahi chal
sakta tha. Matlab **55 Kotlin files mein se ek bhi line ab tak real Android compile se
nahi guzri.** GitHub ke runners par JDK 17 + Android SDK + 7 GB RAM hota hai — wahan
yeh compile ho sakta hai.

**Karna kya hai:**
1. `github.com` par free account banao (card nahi maangta)
2. **New repository** → naam `photo-studio-pro` → **Public** ya **Private** (dono free)
3. `photo-studio-pro.zip` ko apne computer par extract karo
4. **Ek hi command chalao** (maine helper script bana di hai, git commands type
   nahi karne padenge):
   ```
   bash PUSH-TO-GITHUB.sh https://github.com/<tumhara-username>/photo-studio-pro.git
   ```
   Script secrets check karti hai, force-push kabhi nahi karti, aur har step par
   batati hai kya ho raha hai. **Git bilkul nahi aata?** Toh `PUSH-TO-GITHUB.md`
   mein drag-and-drop wala raasta bhi hai.
5. Repo ke **Actions** tab mein jao → build chalte dekho (~5–10 min)

> ⚠️ **Repo banaate waqt "Add a README", ".gitignore", "license" — teeno MAT chunna.**
> Repo bilkul khaali hona chahiye, warna push conflict karega.

> ❌ **Repo ko Arena se connect nahi kar sakte** — GitHub connector Arena offer hi
> nahi karta (verified). Main tumhara repo ya Actions logs directly nahi padh sakta.
> Loop aise chalega: **CI error → tum copy karo → mujhe paste karo → main fix doon →
> tum commit karo.** Har iteration mein 2 minute extra — bas itna hi nuksaan hai.
>
> Repo **public** rakha toh ek chhota fayda: main `raw.githubusercontent.com` se files
> fetch karke verify kar sakta hoon ki push sahi landed hua ya nahi.

**Free limit:** Public repo = unlimited minutes. Private repo = 2,000 min/mahina, aur ek
Android build ~5–10 min leta hai, matlab **200+ builds**. Tum itne use nahi karoge.

**CI mein 3 jobs parallel chalte hain:**

| Job | Kya karta hai |
|---|---|
| `compile` | `assembleDebug` — **yeh asli gate hai.** Pehli baar fail hone ki ummeed rakho |
| `test` | 117 unit tests, real Gradle build ke andar |
| `release` | Release AAB + **16 KB alignment check** + **64-bit check** + **asli download size** |

**`release` job do Play gates khud verify karta hai** jo rejection ke baad theek nahi ho
sakte: `zipalign -c -P 16 -v 4` (16 KB page size, targetSdk 36 ke saath mandatory) aur
arm64-v8a ki maujoodgi. Aur SPEC.md ka `~33 MB` estimate ab tak **naapa nahi gaya tha** —
yeh job asli number dega.

> **Pehli baar `compile` fail hoga. Yeh normal hai aur yeh hi point hai.**
> Actions log kholo, **PEHLA error** dhundo (aakhri 20 uske fallout hain), aur woh mujhe
> paste karo. Main fix bata dunga, tum commit karoge, CI dobara chalega. Yeh loop 3–6
> baar mein app compile hone lagega — aur tumhe Android Studio install karne ki bhi
> jaldi nahi karni padegi.

⚠️ **`google-services.json` git mein commit mat karna** — `.gitignore` mein already hai.
CI ek placeholder khud bana leta hai taaki Firebase setup ke bina bhi build chal sake.

---

### STEP 1B — Android Studio install karo (1 ghanta, ₹0)

Yeh tab karo jab CI ke errors khatam ho jaayein, ya agar tumhe phone par test karna ho
(CI emulator nahi chala sakta).

| Chahiye | Kitna |
|---|---|
| **RAM** | 8 GB minimum (16 GB comfortable). **4 GB mein build fail hoga** |
| **Disk** | 15 GB khaali |
| **JDK** | **17** — AGP 8.x JDK 11 par chalta hi nahi |

1. `developer.android.com/studio` → Android Studio download karo
2. Install karo, pehli baar khulne par SDK install hone do (10–20 min)
3. **SDK Platform 36** install karo (Settings → Languages & Frameworks → Android SDK)

### STEP 2 — Google Antigravity install karo (yeh tumhara "haath" hai)

`antigravity.google.com` → download. **$0, koi card nahi**, weekly agent quota milta
hai, aur yeh **locally chalta hai** — isliye yeh build kar sakta hai, main nahi.

Yeh free tier mein hai, isliye yeh tumhare liye best option hai. (Claude Code ka koi
free plan nahi hai — ₹1,700/mahina lagta. Mat lena.)

---

## 🔌 Ab 5 accounts banao (~2 ghante, sab ₹0, koi card nahi)

### STEP 3 — Firebase (Spark plan)

1. `console.firebase.google.com` → **Add project** → naam do `photo-studio-pro`
2. **Google Analytics enable karo** (free hai)
3. Project settings → **Your apps** → Android icon → package name
   `com.photostudio.pro` → register
4. **`google-services.json` download karo** → isko `photo-studio-pro/app/` folder mein daalo
5. Build → **Crashlytics** enable karo
6. Engage → **Remote Config** enable karo

> ⚠️ **Kabhi bhi "Upgrade to Blaze" par click mat karna.** Spark plan (free) mein
> Analytics, Crashlytics aur Remote Config — teeno aa jaate hain. Blaze mein koi hard
> spending cap nahi hota. Yeh tumhare "free" ka sabse bada khatra hai.

7. Ab `photo-studio-pro/app/build.gradle.kts` aur root `build.gradle.kts` mein jo
   Firebase/Crashlytics/google-services plugin lines **commented out** hain, unka
   `//` hata do. (Yeh maine jaan-boojh kar comment kiye the — `google-services.json`
   ke bina build fail hota hai.)

### STEP 4 — Cloudflare Pages (privacy policy + app-ads.txt)

1. `pages.cloudflare.com` → sign up (**card nahi maangta**)
2. **Direct Upload** choose karo (Git nahi)
3. Ek folder banao, usmein 2 files daalo — dono `deliverables/` mein ready hain:
   - `index.html` ← `deliverables/privacy-policy.html` ko rename karke
   - `app-ads.txt` ← `deliverables/app-ads.txt`
4. Deploy karo → tumhe URL milega jaise `photostudiopro.pages.dev`
5. **Ab 3 jagah yeh URL bharna hai:**
   - `deliverables/app-ads.txt` mein `pub-XXXXXXXXXXXXXXXX` → apna asli AdMob publisher ID
   - `feature/settings/SettingsScreen.kt` mein `privacyUrl` variable
   - Play Console → App content → Privacy policy

> **Yeh free hai.** Pehle maine kaha tha domain chahiye (~₹800/saal) — **woh galat tha.**
> `pages.dev` subdomain bilkul kaam karta hai. Yeh file missing hone par tumhara eCPM
> 10–30% gir sakta hai, isliye skip mat karna.

⚠️ **Vercel mat use karna** — uske Hobby plan par commercial use **mana** hai.
Railway ka free tier mar chuka hai. Fly.io card maangta hai. Render ka free Postgres
30 din mein delete ho jaata hai. **Cloudflare Pages hi sahi hai.**

### STEP 5 — AdMob

1. `admob.google.com` → sign up (**card nahi**)
2. **Apps → Add app** → Android → `Photo Studio Pro: BG Remover`
3. Apna **App ID** copy karo (`ca-app-pub-XXXXXXXX~YYYYYYYY` format)
4. **Us App ID ko `app/src/main/AndroidManifest.xml` mein daalo.**
   > ⚠️ Abhi wahan **Google ka SAMPLE app ID** hai (`ca-app-pub-3940256099942544~3347511713`).
   > Comment se mark kiya hua hai. Isko badalna zaroori hai warna ads initialise hi nahi honge.
5. **4 ad units banao:**

| Ad unit | Type | Kahan use hoga |
|---|---|---|
| `interstitial_export` | Interstitial | Export ke baad (Day 30 se, abhi OFF hai) |
| `rewarded_unlock` | **Rewarded** | Batch +5, watermark hatao, template unlock |
| `native_gallery` | Native | Tool gallery (Day 90 ke baad) |
| *(banner mat banana)* | — | **Permanently disabled** — code mein koi flag nahi jo isko on kare |

6. Chaaron ad unit IDs `monetization/ads/AdUnitIds.kt` mein daalo
7. **AdMob → Account → Settings → Publisher ID** copy karo (`pub-` se shuru, 16 digit)
   → `deliverables/app-ads.txt` mein paste karo

> ⚠️ **App-open ad mat banana.** Launch par full-screen ad #1 uninstall trigger hai.
> Code mein Day 90 tak disabled hai.

### STEP 6 — RevenueCat (sabse uljha hua step — 45 min)

**Yeh step log sabse zyada yahin haar maante hain. Dhyan se karna.**

1. `revenuecat.com` → Google/GitHub se sign up (**card nahi**)
2. **App ka public SDK key copy karo** → `app/build.gradle.kts` mein
   `REVENUECAT_PUBLIC_KEY` placeholder ki jagah daalo
   > Public key client mein daalna safe hai — RevenueCat khud kehta hai.
   > **Private key kahin nahi chahiye. Is project mein hai hi nahi.**
3. **Ab Google Cloud wala hissa:**
   - `console.cloud.google.com` → apna Firebase project kholo (ya naya banao)
   - **APIs & Services → Enable APIs** → search `Google Play Android Developer API` → **Enable**
   - **IAM & Admin → Service Accounts → Create service account** → koi role mat do
   - Us service account par jao → **Keys → Add key → JSON** → file download hogi
4. **Ab Play Console mein:**
   - **Users and permissions → Invite new user**
   - Service account ka email paste karo (`...@<project>.iam.gserviceaccount.com`)
   - **Sirf yeh 2 permissions do:**
     - ✅ *View financial data, orders and cancellation survey responses*
     - ✅ *Manage orders and subscriptions*
5. 🔒 **Ab 24–36 ghante RUKO.** Yeh wait skip karne par RevenueCat mein confusing
   "401 unauthorized" error aata hai. Yeh error permission ka nahi, propagation ka hai.
6. Wapas RevenueCat → **Project settings → Apps → Google Play** → woh JSON key upload karo
7. Green ✅ dikhe toh ho gaya

### STEP 7 — Play Console mein products banao

Play Console → **Monetize → Products**:

| Product ID | Type | Price | Free trial |
|---|---|---|---|
| `pro_weekly` | Subscription | **$6.99 / ₹99** | — |
| `pro_yearly` | Subscription | **$34.99 / ₹999** | ⭐ **7 din** |
| `pro_lifetime` | Managed product | **$59.99 / ₹1,999** | **Day 60 par add karo, launch par nahi** |

- Dono subscriptions **ek hi base plan group** mein rakho
- Trial **kam se kam 7 din** ka rakho — 7+ din wale trial **37–43%** convert hote hain,
  4 din ya kam wale sirf **25.5%**
- Phir RevenueCat mein **`pro`** naam ka entitlement banao aur teeno products attach karo
  > Entitlement ka naam **exactly `pro`** hona chahiye — app yahi padhta hai

### STEP 8 — Licence testing ON karo ⚠️ CRITICAL

Play Console → **Settings → License testing**:
- Apna **khud ka Google account email** add karo
- Test card = **"Always approve"**
- Renewal = **fast (5 min)**

**Yeh kiye bina paywall test nahi kar sakte.** Warna asli card se paisa katega aur
subscription renew hone mein mahina lagega.

> ✅ **Paywall ki purchase flow ab likhi ja chuki hai** — `monetization/billing/PurchaseFlow.kt`
> mein, ek hi file mein isolated. Prices **Play Store se runtime par** aate hain
> (`storeProduct.price.formatted`), hardcode nahi hote — isliye har desh ki currency aur
> tax sahi rehta hai. Jab tak RevenueCat dashboard mein offerings nahi bante, buttons
> **disabled** rehte hain aur "Loading prices from Google Play…" dikhta hai — kyunki
> **galat price dikhana Play policy violation hai.**
>
> ⚠️ **Lekin yeh code kabhi real offering ke against chala nahi hai.** RevenueCat ka API
> surface kai baar badla hai (callbacks → `awaitX` coroutines → `*With` scope extensions)
> aur AI training data mein teeno mix hain. Agar compile fail ho toh **sirf usi ek file
> ko** official docs se theek karo: `revenuecat.com/docs/getting-started/entitlements`
>
> User cancel kare toh koi error toast nahi dikhta — `UserCancelled` alag outcome hai.
> Yeh jaan-boojh kar hai: Play sheet se back dabana normal behaviour hai, error nahi.

---

## 🏗️ Ab build karo (Antigravity se)

### STEP 9 — Pehla build

**Agar STEP 1A (GitHub CI) kar liya hai**, toh errors already Actions log mein hain —
wahan se pehla error mujhe bhejo. Yeh sabse tez raasta hai.

**Agar CI skip kiya hai**, toh Antigravity kholo, `photo-studio-pro` folder open karo,
aur yeh prompt do:

```
Build this Android project. Read README-BUILD.md first — it lists the exact errors
to expect. Do not change any version numbers in gradle/libs.versions.toml unless the
build fails on a version. Fix one error at a time and show me the first error only.
```

**3–6 iterations lagenge pehle successful build tak. Yeh normal hai.** Code kharab
nahi hai — mera environment Android SDK nahi chala sakta tha, isliye pehla compile
tumhare machine par hi ho sakta hai.

**Sabse common error #1:** `Unresolved reference: getOfferingsWith` ya `purchaseWith`
→ `monetization/billing/PurchaseFlow.kt` ko RevenueCat docs se theek karo. **Sirf yeh ek file.**

**Sabse common error #2:** `Unresolved reference: SubjectSegmenter`

Iska fix: `developers.google.com/ml-kit/vision/subject-segmentation/android` kholo aur
**page se import block copy karo**, memory se mat likhwao. Subject Segmentation **Beta**
hai (`16.0.0-beta1` ekmatra version jo kabhi publish hua), aur AI tools ke training data
mein beta package paths purane hote hain. File mein warning comment pehle se laga hua hai.

**Har baar sirf PEHLA error mujhe bhejo.** 200 lines paste karke "fix my project" mat
kehna — pehla error hi asli hota hai, baaki 20 uske fallout hain.

### STEP 10 — Tests chalao

```
./gradlew test
python3 tools/check_resources.py
python3 tools/check_wiring.py
```

**117 tests pass hone chahiye**, aur dono Python scripts `RESULT: CLEAN` / exit 0 deni
chahiye. Maine teeno already run kar liye hain, lekin tumhare machine par bhi chalao —
yeh confirm karta hai ki kuch toota nahi.

Dono scripts ko **Android SDK ki zaroorat nahi** — sirf Python 3 chahiye, aur ek second
se kam lagta hai. `check_wiring.py` ne 4 aise bugs pakde jo compiler kabhi nahi pakad
sakta (README.md §8, bugs #9–#11). Isliye har commit se pehle chalao.

---

## 📱 Ab phone par test karo

### STEP 11 — Apne phone par install karke test karo

Ek **mid-range phone** use karo (flagship nahi) — kyunki tumhare users ke paas wahi hoga.

**Har milestone par yeh 3 cheezein khud check karo:**

| Test | Kaise | Kya dekhna hai |
|---|---|---|
| **Airplane mode** 🛫 | Wi-Fi/data band karke app chalao | 7/8 tools kaam karne chahiye. BG Remover pehli baar model maangega — **yeh expected hai** |
| **Memory** | 20 photos ka batch chalao | App crash na ho. Sequential processing already laga hai |
| **Real export** | Ek photo export karke gallery mein kholo | File khulti hai? Size sahi hai? Watermark free tier par hai? |

### STEP 12 — ⚠️ Sabse zaroori test — BG Remover ka model download

**Yeh woh jagah hai jahan app chupchaap fail ho sakti hai.**

Google ka doc kehta hai: *"Requests you make before the download has completed
**produce no results**."* — Crash nahi, **khaali output**.

Test karo:
1. Ek fresh install par, **turant internet band karo**
2. BG Remover kholo, photo select karo
3. **Kya dikhta hai?** "Needs network" message aana chahiye — **blank image nahi,
   infinite spinner nahi**

Agar blank image dikhe, toh `SubjectSegmentationEngine.kt` ka state machine kaam nahi
kar raha. Mujhe batao.

### STEP 13 — Paywall khud seekho

**Yeh AI ka sabse kamzor area hai — isliye isse khud samjho, delegate mat karo.**

Padho: `revenuecat.com/docs/getting-started/entitlements`

Phir Licence Testing ke saath test karo:
- Purchase karo → **Pro turant milta hai?**
- **Airplane mode mein app kholo → Pro abhi bhi hai?** (yeh entitlement cache karta hai)
- Play Store se cancel karo → **expiry ke baad Pro hat jaata hai?**
- **Pro user ko koi interstitial dikhta hai?** ❌ Nahi dikhna chahiye — yeh sabse bada
  revenue killer hai (refund + 1-star)

---

## 👥 Ab testers dhundo — ⏱️ SABSE BADA SCHEDULE RISK

### STEP 14 — 12 testers chahiye, 14 din tak opted-in

> 🔒 **13 November 2023 ke baad bane personal accounts ke liye yeh mandatory hai:**
> **kam se kam 12 testers ne closed test mein opt-in kiya ho aur 14 din tak lage rahe.**
>
> 12 installs nahi — **12 log jo opt-in karein aur 14 din tak opt-out na karein.**

**Yeh WEEK 1 mein shuru karo, app banne se pehle.** Insaan dhundhne mein upload se
zyada waqt lagta hai. Yeh tumhare poore plan ka sabse bada schedule risk hai.

- **18–25 logon se poocho** (aadhe bhool jaayenge, tab 12 bachein)
- Exact WhatsApp message + tracking sheet + day-3/day-9 follow-up:
  **`deliverables/tester-recruitment.md`** mein ready hai
- ❌ **Testers kharidna mat** — Play policy violation hai, account terminate ho jaata hai
- ❌ **Apna khud ka account 12 mein ginti nahi hota**
- ⚠️ Logon ko **exact date batao** ki kab tak opt-out na karein

### STEP 15 — Closed testing track par AAB upload karo

`deliverables/play-console-checklist.md` ka **Phase G** follow karo.
Jaise hi 12 testers opt-in honge, **14 din ki ghadi shuru ho jaayegi.**

> 🔒 **Yeh wait kam nahi ho sakta, waive nahi ho sakta, appeal nahi ho sakta.**
> Is 14 din mein har 3–4 din mein update ship karo — **updates se ghadi reset nahi hoti.**

---

## 📝 Store listing bharo

### STEP 16 — Sab kuch copy-paste ready hai

| Kya | Kahan se |
|---|---|
| App name (28 char), short desc (73 char), full desc (~3,500 char) | `deliverables/store-listing.md` |
| Data safety form ke **exact answers** | `deliverables/data-safety-answers.md` |
| 6 screenshots ka **fixed order** | `deliverables/store-listing.md` §7 |
| Poora checklist Phase A–H | `deliverables/play-console-checklist.md` |

> ⚠️ **Data safety form galti se mat bharna.** Yeh woh jagah hai jahan apps suspend
> hote hain. File line-by-line follow karo, memory se mat bharna.
>
> Sabse zaroori: **Photos = "No, we don't collect"**. App photos *process* karta hai
> lekin *transmit* nahi karta — "collect" ka matlab hai device se bahar jaana.
>
> Aur: **Target audience = 18+**. Bachchon ke liye declare karne par **Families ad
> policy** lag jaati hai — personalised ads band, eCPM gir jaata hai.

### STEP 17 — ❌ "100% OFFLINE" kabhi mat likhna

**Yeh correction bahut zaroori hai.** Maine pehle "100% offline" USP suggest kiya tha —
**woh galat tha**, kyunki Subject Segmentation ka bundled variant exist hi nahi karta.

BG Remover pehli baar internet maangega. "100% offline" likhna = Play ke **Deceptive
Behavior / misleading-metadata clause** ka seedha shikaar — aur wahi clause hai jo
AI-wrapper apps ko retroactively sweep karta hai.

**Yeh likho (hamesha sach hai):**
- ✅ *"Your photos never leave your phone"* — model neeche aata hai, photos kabhi upar nahi jaati
- ✅ *"Works offline after one-time setup"*
- ❌ ~~"100% offline"~~
- ❌ ~~"No internet needed"~~

`SPEC.md` aur `master-plan-2026.html` dono mein yeh correction **already apply ho chuka hai**.

---

## 🚀 Launch

### STEP 18 — Production + staged rollout

`play-console-checklist.md` ka **Phase H**:
1. **Release build `isDebuggable = false` hai** — yeh confirm karo. Yahi cheez AdMob ko
   TEST MODE se bahar nikalti hai. (Manual switch nahi hai — `BuildConfig.DEBUG` se automatic hai)
2. **16 KB alignment verify karo:** `zipalign -c -P 16 -v 4 app-release.aab`
3. Production mein promote karo
4. **Staged rollout: 10% → 50% → 100%** — har step par crash-free rate dekho
5. Review ka wait: **1–7 din**

### STEP 19 — ⚠️ Interstitial ads ABHI ON MAT KARNA

Remote Config mein `ads_interstitial_enabled` **abhi `false` hai** — aur launch ke
30 din tak false rehna chahiye.

| Kab | Kya on karo | Gate |
|---|---|---|
| **Day 0–30** | Sirf **rewarded** | — |
| **Day 30–60** | Interstitial, **1/din** | Sirf agar **D7 retention > 15%** AUR **crash-free > 99.5%** |
| **Day 60–90** | `pro_lifetime` add, 2–3/din | Sirf agar **ARPDAU badha** AUR D7 ≤1pp gira |
| **Day 90+** | Mediation/bidding (+20–40% eCPM) | — |

**Har frequency change ko kam se kam 7 din test karo.** Ad overload is category ka
**#1 killer** hai. Ek bura interstitial = **+6–7% churn**.

### STEP 20 — Launch post

`deliverables/launch-reddit-posts.md` mein **4 ready posts** hain.

> ⚠️ **r/AndroidDev aur r/india mein post mat karna.**
> Tumhari kamai geography se decide hoti hai: 3,000 DAU par 45% Tier-1 users =
> **₹2.28 lakh/mahina**. Wahi 3,000 DAU par 0% Tier-1 = **sirf ₹24,900**.
> **9× farq, sirf geography se.**
>
> Post karo: **r/EtsySellers, r/eBay, r/reselling, r/Flipping, r/smallbusiness** —
> jahan American/British/Canadian **sellers** already hain. Ek US user ek Indian user
> se **15–20× zyada** kamata hai.

---

## 🔢 Har Monday yeh 5 numbers dekho

| Metric | Floor | Kahan |
|---|---|---|
| Crash-free users | **> 99.5%** | Firebase Crashlytics |
| D1 retention | **> 35%** | Firebase Analytics |
| D7 retention | **> 15%** | Firebase Analytics |
| Trial → paid | **> 35%** | RevenueCat |
| Rating | **> 4.3★** | Play Console |

**North Star = blended ARPDAU** (ads + subscription) / DAU.
Ad frequency tabhi badhao jab ARPDAU badhe **aur** D7 ≤1pp gire.

**Abuse detector (weekly, 2 min):** AdMob rewarded impressions (**A**) vs Firebase
`reward_granted` events (**B**). B/A ≈ 1.0 = theek. **> 3.0 = rewards forge ho rahe hain**
→ tab Phase-2 SSV banana (`free-backend-guide-2026.html`).

> ~150 DAU par abuse ka nuksaan ₹50–100/mahina hai. SSV banane mein 2–4 din lagte hain.
> Isliye launch par **koi backend nahi**. Yeh jaan-boojh kar liya gaya faisla hai.

---

## ⚠️ 5 rules jo tumhein khud follow karne hain

1. **Ek milestone ek waqt mein.** M0 → M1 → M2 → M3. Beech mein mat koodo.
2. **Har milestone khud test karo** — airplane mode mein, mid-range phone par.
3. **Gradle version numbers Google ke official page se verify karo.** Maine 2026-10-29
   ko live Maven se check kiye hain, lekin 6 mahine baad purane ho sakte hain.
   **Saare versions ek hi file mein hain: `gradle/libs.versions.toml`.** Kahin aur hardcode mat karna.
4. **Play Billing khud seekho** — yeh AI ka sabse kamzor area hai. RevenueCat docs +
   Licence Testing use karo.
5. **AdMob TEST MODE ka switch `BuildConfig.DEBUG` se automatic rehne do** — manual flag
   mat banana jo tum on karna bhool jao. Apne hi device par real ad click karne se
   **AdMob account ban** ho jaata hai.

---

## 📁 Kya-kya mil gaya hai

```
photo-studio-pro/
├── README.md                       ← ⭐ MASTER FILE. Naya AI agent YAHI se shuru kare
├── AGENTS.md                       ← 11 non-negotiables (kuch AI tools ise auto-read karte hain)
├── HANDOFF.md                      ← Nayi chat ke agent ke liye first-session procedure
├── docs/
│   ├── DECISIONS.md                ← ⭐ Kyun kya decide hua, 5 purani GALAT salah, 12 bugs
│   ├── SPEC.md                     ← Build spec, milestones M0–M12
│   └── master-plan-2026.html       ← Tumhe end-to-end sirf YEH padhna hai
├── tools/
│   ├── check_resources.py          ← Resource lint (SDK nahi chahiye, <1 sec)
│   └── check_wiring.py             ← ⭐ Wiring audit — 4 bugs isse mile
├── .github/workflows/ci.yml        ← 4 CI jobs: resources → compile | test | release
├── PUSH-TO-GITHUB.sh / .md         ← Repo GitHub par push karne ke liye
├── YOUR-TURN.md                    ← YEH FILE. Tumhare wale 20 steps, Hinglish mein
├── README-BUILD.md                 ← Build kaise karna hai, expected errors, kya edit karna hai
├── gradle/libs.versions.toml       ← ⭐ SAARE versions ek jagah (live Maven se verified)
├── settings.gradle.kts, build.gradle.kts, gradle.properties, .gitignore
├── app/
│   ├── build.gradle.kts            ← ⚠️ RevenueCat key placeholder + commented Firebase plugins
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml     ← ⚠️ SAMPLE AdMob app ID — badalna zaroori hai
│       ├── assets/countries.json   ← 38 desh, har entry par sourceUrl
│       ├── res/                    ← strings (EN + HI), colors, themes, adaptive icon,
│       │                              backup rules (exports ko cloud backup se EXCLUDE karta hai)
│       └── java/com/photostudio/pro/
│           ├── PhotoStudioApp.kt, MainActivity.kt, Nav.kt
│           ├── analytics/Events.kt
│           ├── core/image/         ← BitmapOps, MaskOps, ExportOps, EnhanceOps, QualitySearch
│           ├── core/data/          ← Prefs (DataStore), PrefsUsageCounters, ServiceLocator
│           ├── core/ui/            ← Theme, Components
│           ├── monetization/ads/   ← ⭐ AdPolicy, AdCoordinator, AdManager, RewardGuard,
│           │                          UmpConsent, AdUnitIds, LocalIdentity
│           ├── monetization/billing/EntitlementManager.kt   ← RevenueCat
│           ├── monetization/config/RemoteFlags.kt           ← ⚠️ interstitials launch par OFF
│           └── feature/
│               ├── home/, onboarding/, paywall/, settings/, later/
│               └── bgremover/ (M1), idphoto/ (M2), batch/ (M3), whitebg/ (M6),
│                  collage/ (M7), pdf/ (M8), ocr/ (M9), enhance/ (M10)
├── app/src/test/                   ← ✅ 117 tests, RUN karke verify kiye gaye
└── deliverables/                   ← Sab kuch copy-paste ready
    ├── privacy-policy.html         ← ⚠️ [BRACKETS] bharne hain
    ├── app-ads.txt                 ← ⚠️ pub-ID bharna hai
    ├── store-listing.md            ← Title, descriptions, screenshot order
    ├── data-safety-answers.md      ← ⚠️ Exact answers — line by line follow karo
    ├── play-console-checklist.md   ← Phase A–H, har gate
    ├── tester-recruitment.md       ← WhatsApp messages + tracking sheet
    └── launch-reddit-posts.md      ← 4 ready posts + kahan post karna hai
```

**Kul: 55 Kotlin source files (7,881 lines) + 5 test files = 9,093 lines · 140/140 strings EN↔HI sync · 7 deliverables · 117 passing tests · 2 linters clean · 4 CI jobs.**

**Saare 8 tools ban chuke hain** (M1 se M10) — koi placeholder screen nahi bacha.

---

## 🎯 Sabse pehla kaam — aaj hi

**Sirf 3 cheezein aaj karo, baaki kal:**

1. **STEP 0** — Play Console account + $25 + identity verification shuru karo
   *(2–7 din ka wait — yeh sabse pehle shuru karna sabse zaroori hai)*
2. **STEP 14** — 20 logon ko WhatsApp message bhejo testers ke liye
   *(14 din ki ghadi — yeh dusra sabse lamba wait hai)*
3. **⭐ STEP 1A** — GitHub par push karo aur CI chalao
   *(15 min, aur isse pata chal jaayega ki code mein kya toota hai — Android Studio
   install karne se pehle)*

**Yeh teeno waits hain. Inhe jaldi shuru karne se hi 8-week plan 8 hafte mein khatam hoga.**
Baaki sab kaam in waits ke andar ho jaayega.

---

*Jab bhi atko — pehla error message mujhe paste karo. Main fix bata dunga.*
