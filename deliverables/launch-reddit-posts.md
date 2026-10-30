# Launch Posts — where to post, and exactly what to say

## The single most important strategic point

**Your revenue is 9× higher per user in Tier-1 countries than in India.**
3,000 DAU with 45% Tier-1 users ≈ ₹2.28 lakh/month. 3,000 DAU with 0% Tier-1 ≈ ₹24,900.
Same app, same effort, **9× difference — purely from geography.**

So: **do not launch in r/AndroidDev, r/india or r/androidapps.** Those give you
installs worth ~$0.34 each and users who will never convert. Launch where American,
British, Canadian and Australian **sellers** already are.

**One US user is worth 15–20× an Indian user.** A single r/EtsySellers thread that
converts 40 sellers beats a front-page r/india post.

---

## Where to post (in priority order)

| Sub | Size | Why | Risk |
|---|---|---|---|
| **r/EtsySellers** | Large, commercial | Sellers who pay for product photos. Exact ICP | Self-promo rules — read the sidebar |
| **r/eBay** / r/eBaySellers | Large | Same | Same |
| **r/reselling** | Mid | Volume sellers, cost-sensitive | Friendly to tools |
| **r/smallbusiness** | Very large | Broadest | Strict on self-promo |
| **r/Flipping** | Mid | Photographs 10–50 items a day | **Highest intent per user** |
| **r/passportphotos** / r/immigration | Small | ID-photo need is urgent | Don't spam |
| **r/SideProject** / r/InternetIsBeautiful | Mid | Launch-friendly by design | Low commercial intent |
| **r/androidapps** | Large | "Show and tell" threads | **Indian-heavy → low ARPDAU** |
| ❌ **r/AndroidDev** | — | Developers critique architecture, never buy | **Skip entirely** |

Post in **one** subreddit per day, not all at once. Reddit's sitewide spam filter
downranks accounts that post near-identical links across many subs in a short window.

---

## Post #1 — r/EtsySellers / r/Flipping (the seller angle)

**Title:**
```
I built a free Android app that removes product photo backgrounds offline —
because I got tired of paying per image
```

**Body:**
```
Selling on Etsy/eBay and photographing 20 items a week gets expensive if you use a
per-image cut-out service, and the free web tools mostly want you to upload your
product photos to their server.

So I built an Android app that does it on your phone instead. No upload, no account,
no email. The image never leaves the device — which matters if you're photographing
something you haven't listed yet.

It also does batch resize (select 50 photos, resize them all to 1000px for a listing),
passport/ID photos for 30+ countries, and photo-to-PDF.

Honest limitations, because I'd rather you know:
- Background removal is good on clearly separated objects. Fine hair, fur, glass and
  objects the same colour as the background are hard for every tool in this category.
- The first time you use background removal it downloads a small ML model through
  Google Play services. After that it works offline.
- It's free with a watermark and 3 exports/day. There's a subscription for unlimited,
  but background removal itself is never capped.

I'm a solo dev and this is my first app, so I'd genuinely appreciate brutal feedback.
What's broken? What would make you keep it?

[link]
```

**Why this works:** it leads with the seller's problem, not the technology. It states
limitations before a reviewer can find them (Play's Deceptive Behavior clause targets
exactly this gap). It asks for feedback, which is what these subreddits actually reward.
**Never** write "AI-powered" — Google names "AI photo enhancers" as facing immediate
rejection, and sellers read it as "fake".

---

## Post #2 — r/smallbusiness (the cost angle)

**Title:**
```
What are you actually paying for product photos? I built the offline alternative
after seeing how much per-image cut-out services cost
```

**Body:**
```
Genuine question first: what does product photography cost you per month?

I kept seeing per-image pricing for background removal and it didn't add up for small
sellers doing 20-50 items a week. The web tools that are free almost all upload your
image to a server, which is awkward for unreleased products.

So I made an Android app that processes everything on-device. Background remover,
batch resize to a target file size, ID/passport photos for 30+ countries, and
photo-to-PDF for invoices and documents.

No account, no email, no upload. Free tier: unlimited background removal, 3 exports a
day with a small watermark.

Two things I'd like honest answers on:
1. Is batch resize to a specific KB target actually useful to you, or is it a
   developer feature nobody asked for?
2. What's the one photo task that still costs you money?

[link]
```

---

## Post #3 — r/passportphotos / r/immigration (the ID-photo angle)

**Title:**
```
Free Android app for passport/ID photos with country-specific dimensions and
live head-height and eye-line checks (30+ countries)
```

**Body:**
```
Passport photo shops charge a lot for what is really a crop-to-specification job, and
most free apps just resize without checking anything.

This one detects your face, crops to the exact millimetre dimensions for the country
you pick, and then tells you live whether your head height, eye line and background
colour are inside that country's band. It prints multiple copies on a 4x6 or A4 sheet
at the correct DPI with cut lines, and compresses the file to fit a government
portal's upload limit.

Includes India 35×45, US 2×2, UK, Schengen, Canada, Australia, UAE, China, Japan and
more. All processing is on-device — your ID photo is never uploaded anywhere.

⚠️ One important caveat: government photo rules change without notice, and no app can
guarantee acceptance. Always check the official specification page before you submit.
The app links to the source for every country.

Free for 10 countries; the rest need the paid tier. Background removal is unlimited free.

[link]
```

⚠️ **Never claim "guaranteed acceptance".** That is a Deceptive Behavior risk on Play
and an obvious lawsuit-shaped claim. The in-app disclaimer is already in
`res/values/strings.xml` as `id_disclaimer`.

---

## Post #4 — r/SideProject (the build-in-public angle)

**Title:**
```
My first Android app, built solo with AI coding tools on a $25 total budget
(the $25 is the Play registration fee)
```

**Body:**
```
Constraint I set myself: $25 total, no server, no paid APIs, no team.

$25 = the one-time Google Play developer registration. Everything else is genuinely
free-tier: Firebase Spark for analytics/crashlytics/remote config, RevenueCat for
purchase validation (free until $2,500/mo tracked), Cloudflare Pages for the privacy
policy and app-ads.txt, and on-device ML Kit for all the image work.

No AI API calls anywhere — no Gemini, no OpenAI, no Replicate. Everything runs on the
phone. That's partly a budget decision and partly the product: "your photos never
leave your phone" is only true if there's no API.

8 tools: background remover, ID photos (30+ countries), batch resize/compress, white
background, collages, photo↔PDF, text scanner with Devanagari support, and photo
enhance.

The part that surprised me: Google's ML Kit Subject Segmentation has no bundled
variant — it's Play-services-delivered only, so it downloads a model on first run. My
original "100% offline" pitch was simply not true and I had to reword it. Sharing in
case anyone else assumes bundled.

Happy to answer anything about the zero-backend setup.

[link]
```

---

## Rules for every post

1. **Read the subreddit's self-promotion rule first.** Some require a mod message.
   Getting removed in week 1 costs you the account's credibility.
2. **Never post the same text twice.** Rewrite the opener for each sub. Duplicate
   content triggers sitewide spam filtering.
3. **Reply to every comment for the first 3 hours.** Engagement in the first hours
   decides whether the post climbs.
4. **Never claim perfection.** "Works well on clearly separated subjects; hair and
   glass are hard" is more credible than "flawless AI cut-out" — and it is what
   protects you from a misleading-metadata strike.
5. **Never say "AI"** unless describing a real ML feature precisely. Your enhance tool
   is classical sharpening and denoising; calling it AI is the exact pattern that gets
   apps rejected and swept retroactively.
6. **Do not buy upvotes or use engagement pods.** Account termination risk.
7. **Answer criticism honestly.** If someone says the cut-out is bad on hair, agree and
   say which cases work. Defensive replies are what turn one critic into a thread.

---

## Timing

- **Post Tuesday–Thursday, 13:00–16:00 UTC** (that is 18:30–21:30 IST) — US morning,
  EU afternoon, your evening.
- Wait until the production rollout is at **100%** and crash-free is **>99.5%**.
  A launch post that drives 500 installs into a crashing build is worse than no post.
- Keep **`ads_interstitial_enabled = false`** in Remote Config through the launch week.
  Your first cohort decides your rating, and a forced ad on day 1 is how you get to 3.2★
  before you have any reviews.

---

## Also free, and often better than Reddit

| Channel | Why |
|---|---|
| **Etsy / eBay seller Facebook groups** | Higher intent than Reddit, less spam-filtering |
| **Answering questions on Quora** ("how do I make a passport photo at home") | Evergreen traffic, months of installs from one good answer |
| **A 60-second screen-recording on YouTube Shorts** | Product photo before/after is inherently visual |
| **WhatsApp status + 3 seller groups you're already in** | Your first 20 installs, and they convert to testers |
