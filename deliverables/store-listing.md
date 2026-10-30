# Google Play Store Listing — copy/paste ready

⚠️ **Before you paste:** replace `[BRACKETS]`. Character counts are given because Play
enforces hard limits and will silently reject over-length text.

---

## 1. App name (30 char limit)

```
Photo Studio Pro: BG Remover
```
**28 characters.** The keyword "BG Remover" is in the title, which is the single
highest-weight ASO signal available to you.

**Do NOT rename it** to include "AI". Google Play's 2026 AI & deception policy names
"AI photo enhancers" as a category facing immediate rejection when a generative-AI
report control is missing. You have no generative features, so the word buys nothing
and risks a rejection.

---

## 2. Short description (80 char limit)

```
Remove backgrounds & make ID photos — your photos never leave your phone.
```
**73 characters.**

Alternates, A/B these later (each is under 80):
```
Offline photo toolkit for sellers: BG remover, ID photos, batch resize.
```
```
Cut out backgrounds, make passport photos, resize 100 images. No upload.
```

❌ **Never** write "100% offline" here. Background Remover downloads its ML model once
on first run. That claim is the exact pattern Play's metadata audit flags.

---

## 3. Full description (4000 char limit)

```
Photo Studio Pro is an offline-first photo toolkit built for online sellers, resellers,
small-business owners and creators — and for anyone who needs a passport or ID photo
without paying a shop.

YOUR PHOTOS NEVER LEAVE YOUR PHONE
There is no server, no account, no email and no signup. Every image is processed on
your device using on-device machine learning. Your unreleased product photos, your
family's ID documents and your client work stay exactly where they are. We could not
see them even if we wanted to.

WORKS OFFLINE AFTER ONE-TIME SETUP
Background removal downloads its machine-learning model once, through Google Play
services. After that, it works with no connection. The other tools work offline from
the very first launch.

———————————————
THE TOOLS
———————————————

AUTO BACKGROUND REMOVER
Cut a product, person or object out of any photo and export it with a transparent
background, or place it on white or black. Made for marketplace listings where a
clean cut-out is the difference between a scroll-past and a click.

PASSPORT & ID PHOTOS — 30+ COUNTRIES
Automatic face detection and cropping to each country's real dimensions, with live
checks for head height, eye line and background colour. Prints multiple copies on a
4x6, A4 or letter sheet at the correct DPI, and compresses the result to fit a
government portal's upload limit. Includes India 35x45, US 2x2, UK, Schengen, Canada,
Australia, UAE, China, Japan and more.

BATCH RESIZE & COMPRESS
Select many photos and resize them all to a longest edge, an exact width, a percentage,
or a target file size. Ideal for marketplace uploads, portfolio sites and email.

WHITE BACKGROUND FOR PRODUCTS
Even out lighting and push a background to pure white for Amazon, eBay, Etsy and
Shopify listings.

PHOTO COLLAGES & TEMPLATES
Grid layouts with adjustable spacing, border and corner radius.

PHOTO TO PDF AND PDF TO PHOTO
Combine images into a single PDF, or pull pages back out as images.

TEXT SCANNER (OCR)
Read text out of a photo and copy it. Supports Latin and Devanagari scripts, so Hindi
and Marathi documents work too.

PHOTO ENHANCE
Auto levels, auto white balance, sharpening and noise reduction — classical image
algorithms, applied honestly. We do not call this "AI" because it is not.

———————————————
WHAT IS FREE
———————————————
• Unlimited background removal — no daily cap, no credits
• ID photos for 10 countries
• 3 exports per day
• Batch processing of 1 photo at a time
• 10 collage templates

Free exports are standard definition and carry a small watermark. Watch a short video
any time to unlock a one-off benefit, such as removing the watermark from a single
export or adding 5 photos to a batch.

———————————————
PHOTO STUDIO PRO
———————————————
• Unlimited exports, no watermark
• HD and 4K output
• Batch processing of up to 100 photos
• All 30+ ID photo countries
• All collage templates
• Unlimited text scanning
• No forced ads, ever

Pro is available as a weekly subscription, a yearly subscription with a 7-day free
trial, or a one-time lifetime purchase. Prices are shown in your local currency by
Google Play at checkout. Subscriptions renew automatically until cancelled in the
Play Store app under Payments & subscriptions.

———————————————
HONEST LIMITATIONS
———————————————
We would rather tell you than have you find out in a review:
• Background removal works well on clearly separated subjects. Fine hair, fur, glass
  and objects the same colour as their background are hard for every tool in this
  category, ours included.
• ID photo dimensions follow each government's published specification, but rules
  change without notice. Always confirm against the official page before you submit.
  We cannot guarantee acceptance by any authority.
• Photo Enhance improves exposure, colour balance and sharpness. It cannot recover
  detail that was never captured, and it does not restore badly damaged old photos.

———————————————
PRIVACY
———————————————
No account. No email. No photo upload. Full policy:
https://[YOUR-PROJECT].pages.dev/

Questions: [YOUR-EMAIL@DOMAIN]
```

**Roughly 3,500 characters.** The "HONEST LIMITATIONS" section is deliberate: Play's
Deceptive Behavior clause targets listings that promise more than they deliver, and
competitors in this category get 1-star reviews precisely for hair-edge failures.
Stating it up front converts an angry review into an informed user.

---

## 4. Category & tags

| Field | Value |
|---|---|
| Application type | Application (not Game) |
| Category | **Photography** |
| Tags | background remover, passport photo, photo editor, batch resize |

Photography is the right category. Photo & Video has the **highest median revenue of any
category ($124/mo)** and the best 2-year odds of reaching $1K (21.4%). Business is the
worst at 1.6% — do not put it there even though the pitch targets sellers.

---

## 5. Content rating & declarations

| Question | Answer | Why |
|---|---|---|
| Contains ads | **Yes** | You have AdMob. Answering No is an instant policy strike. |
| In-app purchases | **Yes** | Subscriptions + lifetime |
| Target age group | **18+ (not for children)** | ⚠️ Critical. If you declare it for children, Play applies the **Families ad policy**: personalised ads off, eCPM drops sharply. |
| AI-generated content declaration | **No — not required** | You have no generative AI. Declaring it anyway attaches a public **"AI-generated"** badge to your listing for no benefit. |
| Generative AI report control needed? | **No** | Only required if you have a generative feature. You have none. |

---

## 6. Data safety form

See `data-safety-answers.md` — answer every question from that file, not from memory.

---

## 7. Graphics

| Asset | Spec | Notes |
|---|---|---|
| App icon | 512×512 PNG, 32-bit | Already generated as a vector adaptive icon in `res/mipmap-anydpi-v26/`. Render the 512 PNG from it. |
| Feature graphic | 1024×500 | No text smaller than ~24px; it is shown tiny in search |
| Phone screenshots | Min 2, max 8, at least 320px on the short side | **See the fixed order below** |
| 7-inch / 10-inch tablet shots | Optional | Skip for v1 |

### Screenshot order (fixed — do not reshuffle)

1. **The hook.** A product on a messy desk → the same product cut out on white. Before/after split. Headline: *"Cut out any background in one tap"*
2. **Airplane-mode icon + the app working.** Headline: *"Works offline — no upload, ever"* ⚠️ **Not** "100% offline".
3. **A batch grid of 40 photos processing.** Headline: *"Resize 100 photos at once"*
4. **A shield/lock icon over a phone.** Headline: *"Your photos never leave your phone"*
5. **A passport photo sheet with cut lines + a country dropdown showing flags.** Headline: *"ID photos for 30+ countries"*
6. **The Pro paywall benefits list.** Headline: *"Go Pro — no watermark, no ads"*

Use **real screenshots of the real app**. Composite mock-ups that show features you have
not built are the single most common reason for a misleading-metadata rejection.

---

## 8. Contact details you must supply

| Field | What to put |
|---|---|
| Developer website | `https://[YOUR-PROJECT].pages.dev/` — your Cloudflare Pages URL. This is free and satisfies the requirement. |
| Privacy policy URL | Same URL |
| Support email | A real inbox you check. Gmail is fine. |
| Phone | **Not required** — leave blank |
