# Play Console Checklist — every gate, in order

Tick these off literally. Each one has blocked real launches.
Items marked 🔒 **cannot be skipped or shortened** — they are Google-imposed waits.

---

## Phase A — Account (Week 1, Day 1)

| # | Task | Cost | Wait | Notes |
|---|---|---|---|---|
| A1 | Create a **personal** Google Play Developer account at `play.google.com/console` | **$25 one-time** | — | The ONLY money in this entire project |
| A2 | 🔒 **Complete identity verification** | Free | **2–7 days** | Mandatory since Sept 2026. Government photo ID + address proof. **Start this on day 1** — it is the longest single wait and it blocks everything |
| A3 | Fill in the public developer profile | Free | — | Name, contact email, website (use your Cloudflare Pages URL) |

⚠️ **Do not create an organisation account** unless you genuinely have a registered
company. It needs a D-U-N-S number and takes longer.

---

## Phase B — Third-party accounts (Week 1, ~3 hours, all free)

| # | Task | Card needed? | Notes |
|---|---|---|---|
| B1 | **RevenueCat** — `revenuecat.com`, sign up with Google/GitHub | ❌ No | Free until $2,500/month tracked revenue (then 1%) |
| B2 | **Firebase** — `console.firebase.google.com`, new project | ❌ No | ⚠️ Stay on the **Spark (free)** plan. **NEVER click "Upgrade to Blaze"** — Blaze has no hard spending cap |
| B3 | Enable **Analytics**, **Crashlytics**, **Remote Config** in that project | ❌ No | All three are free on Spark |
| B4 | **AdMob** — `admob.google.com` | ❌ No | Create the app + 4 ad units (see `YOUR-TURN.md` step 5) |
| B5 | **Cloudflare Pages** — `pages.cloudflare.com` | ❌ No | Hosts the privacy policy + `app-ads.txt`. Free, unlimited bandwidth, commercial use allowed |

⚠️ **Never sign up for anything that asks for a credit card.** If a free tier demands
one, stop — there is always a no-card alternative in this stack.

**Traps to avoid** (all verified dead or unsafe as of 2026): Vercel Hobby (commercial
use prohibited), Railway (free tier gone), Fly.io (card required), Render free Postgres
(deleted at 30 days), Supabase (5 GB egress then HTTP 402).

---

## Phase C — 🔒 The hardest 45 minutes: link Play Billing to RevenueCat

RevenueCat cannot validate purchases until you do this. **This is the step people give up on.**

| # | Task |
|---|---|
| C1 | In Google Cloud Console, create a project (or reuse the Firebase one) |
| C2 | Enable the **Google Play Android Developer API** |
| C3 | Create a **Service Account** (IAM & Admin → Service Accounts) |
| C4 | Create a **JSON key** for that service account and download it |
| C5 | Back in **Play Console** → *Users and permissions* → **Invite new user** |
| C6 | Paste the service account's email address (ends `@<project>.iam.gserviceaccount.com`) |
| C7 | Grant exactly two permissions: **"View financial data, orders and cancellation survey responses"** and **"Manage orders and subscriptions"** |
| C8 | ⚠️ **Wait up to 24–36 hours** for the invitation to propagate. Skipping this wait causes a confusing "401 unauthorized" in RevenueCat |
| C9 | In RevenueCat → *Project settings* → *Apps* → *Google Play* → upload the JSON key |
| C10 | RevenueCat shows a green ✓ when validation works |

---

## Phase D — In-app products (Week 4)

| # | Product ID | Type | Price | Notes |
|---|---|---|---|---|
| D1 | `pro_weekly` | Subscription | **$6.99 / ₹99** | Weekly base |
| D2 | `pro_yearly` | Subscription | **$34.99 / ₹999** | ⭐ The anchor. Push this one |
| D3 | `pro_lifetime` | Managed product (one-time) | **$59.99 / ₹1,999** | **Add at Day 60, not at launch** |

- Both subscriptions need a **free trial of ≥7 days**. 7-day trials convert
  **37–43%** versus **25.5%** for trials of 4 days or less.
- Put both subscriptions in **one base plan group** so users can switch between them.
- Then in RevenueCat: create the **`pro` entitlement** and attach all three products.
  The app reads `entitlements["pro"]?.isActive` — the entitlement id must be exactly `pro`.

---

## Phase E — Licence testing (Week 4) ⚠️ CRITICAL

| # | Task |
|---|---|
| E1 | Play Console → *Settings* → *License testing* |
| E2 | Add **your own Google account email** |
| E3 | Set the test card to **"Always approve"** and the renewal to **fast (e.g. 5 min)** |

Without this, testing purchases charges your real card and subscriptions take a month
to renew. **You cannot properly test the paywall without licence testing.**

Also verify: **AdMob test mode is driven by `BuildConfig.DEBUG`**, never by a manual
flag you might forget to turn off. Clicking real ads from your own device can get your
AdMob account banned.

---

## Phase F — Store listing & policy declarations (Week 5)

| # | Item | Where | Notes |
|---|---|---|---|
| F1 | App name, short + full description | *Main store listing* | Copy from `store-listing.md` |
| F2 | Icon 512×512, feature graphic 1024×500 | *Main store listing* | |
| F3 | 6 screenshots **in the fixed order** | *Main store listing* | Real screenshots only |
| F4 | Category = **Photography** | *Main store listing* | Not Business (1.6% vs 21.4% odds) |
| F5 | Privacy policy URL | *App content* | Your Pages URL |
| F6 | **Data safety form** | *App content* | ⚠️ Follow `data-safety-answers.md` line by line |
| F7 | **Target audience = 18+** | *App content* | ⚠️ Declaring children triggers the Families ad policy and kills your eCPM |
| F8 | **Ads declaration = Yes** | *App content* | |
| F9 | Content rating questionnaire | *App content* | |
| F10 | News app declaration | *App content* | Answer **No** |
| F11 | **AI-generated content declaration** | *App content* | Answer **No** — you have no generative AI. Declaring yes attaches a public "AI-generated" badge for no reason |
| F12 | Government apps / financial features | *App content* | No / No |
| F13 | Health apps declaration | *App content* | No — face detection is not biometrics here |

---

## Phase G — Closed testing (Week 5) 🔒 THE 14-DAY CLOCK

| # | Task |
|---|---|
| G1 | Build a **release AAB** (not an APK) |
| G2 | 🔒 Verify **16 KB page alignment**: `zipalign -c -P 16 -v 4 app-release.aab` |
| G3 | Verify **64-bit** native libs are present |
| G4 | Upload to the **Closed testing** track |
| G5 | Add your **12+ testers'** email list (see `tester-recruitment.md`) |
| G6 | 🔒 **The 14-day clock starts the moment ≥12 testers are opted in.** This cannot be shortened, waived, or appealed |
| G7 | During the wait: ship an update every 3–4 days. **Updates do not reset the clock** |

**Target API level:** Play requires **targetSdk 36** from **31 August 2026**. The project
already sets `compileSdk = 36` and `targetSdk = 36`.

---

## Phase H — Production (Week 8)

| # | Task |
|---|---|
| H1 | Confirm Play Console shows **"12 testers opted in for 14 days"** ✅ |
| H2 | ⚠️ **Confirm the release build is `isDebuggable = false`** — this is what switches AdMob out of test mode |
| H3 | Promote the closed-test AAB to **Production** (or build a fresh release) |
| H4 | **Staged rollout: 10% → 50% → 100%**, watching crash-free rate at each step |
| H5 | Wait for review: **1–7 days**, typically 1–3 for a first app |
| H6 | In **Firebase Remote Config**, keep `ads_interstitial_enabled = false` |
| H7 | Launch post (see `launch-reddit-posts.md`) |

---

## Post-launch ad schedule (do not accelerate this)

| Window | What to turn on | Gate |
|---|---|---|
| Day 0–30 | **Rewarded only.** Interstitials stay OFF | — |
| Day 30–60 | Interstitials, **1/day**, via Remote Config | Only if **D7 retention > 15%** AND **crash-free > 99.5%** |
| Day 60–90 | Add `pro_lifetime`; raise to 2–3/day | Only if blended **ARPDAU rose** and D7 dropped ≤ 1pp |
| Day 90+ | Consider mediation/bidding (+20–40% eCPM), win-back offers, `es`/`pt-BR` | — |

**Every frequency change needs a minimum 7-day test.** Ad overload is the #1 killer of
apps in this category. One disruptive interstitial costs **+6–7% churn**; three on a
reward screen triples the quit rate.

---

## The 5 numbers to check every Monday

| Metric | Floor | Where |
|---|---|---|
| Crash-free users | **> 99.5%** | Firebase Crashlytics |
| D1 retention | **> 35%** | Firebase Analytics |
| D7 retention | **> 15%** | Firebase Analytics |
| Trial → paid | **> 35%** | RevenueCat |
| Rating | **> 4.3★** | Play Console |

And the North Star: **blended ARPDAU** = (ad revenue + subscription revenue) / DAU.
Raise ad frequency only if ARPDAU goes up **and** D7 drops by at most 1 point.

---

## Abuse detector (weekly, 2 minutes)

Compare **A** = AdMob rewarded impressions with **B** = Firebase `reward_granted` events.

| B / A | Meaning | Action |
|---|---|---|
| ≈ 1.0 | Healthy | Nothing |
| > 1.5 | Something is off | Investigate |
| **> 3.0** | Rewards are being forged | Build the Phase-2 SSV endpoint (`free-backend-guide-2026.html`) |

At ~150 DAU the abuse loss is roughly ₹50–100/month, which is why server-side
verification is deliberately **not** built at launch.
