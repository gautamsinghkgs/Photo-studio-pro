# Closed Testing — Recruiting 12+ Testers for ₹0

## Why this matters more than anything else in your launch

Google changed the rules for personal developer accounts created **after 13 November 2023**.
You cannot go to production until:

> **At least 12 testers have opted in to your closed test and remained opted in for
> 14 continuous days.**

Not 12 installs. **12 people who opt in and stay opted in for 14 days.** If someone
opts out on day 10, they stop counting and your clock effectively resets for that seat.

**Budget available: ₹0.** So this is a personal-favour campaign, not a marketing one.
You need roughly **18–25 people** to end up with 12 who actually stay, because about
half will forget.

**Start this in week 1, before the app is finished.** The 14-day clock cannot start
until you have an AAB uploaded to the closed track, but recruiting the humans takes
longer than the upload. This is the single biggest schedule risk in your whole plan.

---

## Who to ask (in order of likelihood to say yes)

| Group | How many | Why they work |
|---|---|---|
| Family & close friends | 6–10 | Highest conversion. They will actually do it. |
| WhatsApp / Telegram groups you are already in | 4–8 | Zero cold outreach |
| Local shopkeepers & resellers | 3–5 | Genuine target users, and they give real feedback |
| College / school group chats | 4–6 | Students need passport photos constantly |
| Colleagues or ex-colleagues | 2–4 | Reliable |
| r/IndiaTechFeedback or similar | 0–3 | Low yield, but free |

**Do NOT post in r/AndroidDev asking for testers.** Developers are the wrong users and
they will critique your architecture instead of using the app.

---

## The message to send (WhatsApp — copy/paste)

```
Hey! 👋

I've built my first Android app — a photo toolkit that removes backgrounds and
makes passport-size photos, all offline. Your photos never get uploaded anywhere.

Google now requires 12 real testers to stay opted in for 14 days before a new
personal developer account can publish. I'd be really grateful if you could help.

What you need to do (takes about 2 minutes):
1. Open this link on your phone: [CLOSED-TEST OPT-IN URL]
2. Tap "Become a tester"
3. Tap "Download on Google Play" — leave it installed for 14 days

That's it. You don't even have to use the app, though I'd love your feedback if you do.
It's free, no ads will bother you during testing, and no account or email needed.

Thank you so much 🙏
```

**Why this message works:** it states the exact time cost, gives numbered steps, says
explicitly that they don't have to use it, and explains *why* you're asking. People
decline vague asks; they accept specific ones.

---

## The follow-up (day 3 and day 9)

Most people who say "sure!" never tap the link. Two nudges roughly double completion.

**Day 3:**
```
Small reminder 🙂 — the tester link: [URL]
Takes 30 seconds. Once you've tapped "Become a tester" you're done.
```

**Day 9:**
```
Almost there! 5 more days and my app can go live 🎉
If you're already opted in — thank you, you're a legend. Please don't opt out
until [DATE].
If you haven't yet: [URL]
```

⚠️ **Tell people the exact date not to opt out before.** The most common failure is
someone helpfully "cleaning up" their Play Store testing list on day 11.

---

## Tracking sheet — build this on day 1

| # | Name | Channel | Link sent | Opted in (date) | Day 14 | Status |
|---|---|---|---|---|---|---|
| 1 | | WhatsApp | | | | |

Keep it in any free spreadsheet. **You cannot see individual opt-in dates in Play
Console** — you only see the aggregate count. So your own sheet is the only way to
know whether you are genuinely safe on day 14.

Check the Play Console count on **day 12 and day 13**. If it shows fewer than 12,
you have time to message two more people. On day 15 it is too late.

---

## What to send them once they're in

A one-paragraph message with **three specific things to try**. Open-ended "let me know
what you think" produces silence.

```
Thanks for testing! 🙏 If you have 5 minutes, these 3 things would help most:

1. Background remover — try a photo with hair or a complicated edge. Does the cut-out
   look usable, or broken?
2. ID photo — pick India (or your country), and tell me whether the printed size and
   the head position look right.
3. Batch — select 10 photos and compress them. Was it fast enough? Did anything crash?

Then: Play Store → the app → leave any rating you honestly feel like leaving.
Screenshots of anything that looks wrong are gold.
```

---

## Reading the feedback

| Signal | Meaning | Action |
|---|---|---|
| "It works" and nothing else | Fine, but they didn't engage | Ask one specific follow-up |
| Screenshots of a bug | **The most valuable thing you will receive** | Fix before production |
| "The cut-out looks bad on hair" | Expected — ML Kit Beta is weaker than Photoroom here | Do NOT promise perfect edges. Widen the feather radius by 1–2px and soften the claim |
| "Why do I need internet the first time?" | Your onboarding does not explain the model download | Add a one-line explainer screen before the first BG removal |
| "It's slow" | Measure before optimising | Check whether it is decode time or inference time |
| Nobody reports anything | They did not use it | Normal. Do not read it as "the app is perfect" |

---

## ⚠️ Three traps

1. **Do not pay for testers.** Services selling "14-day opt-in testers" violate Play
   policy and get accounts terminated. Your ₹25 is the whole budget.
2. **Do not use your own Google account** as one of the 12. It does not count.
3. **Do not start the 14-day clock before the app is stable.** Crashes during closed
   testing are fine and expected, but a build that cannot complete one export will
   produce 12 people who opt out. Get one export working end to end first.
