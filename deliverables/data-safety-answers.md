# Google Play Data Safety Form — exact answers

⚠️ **This form is where apps get suspended for lying.** It is not a marketing
exercise. Answer exactly as below. A Data Safety mismatch is one of the top reasons
for a Play policy strike, and it is checked against what your SDKs actually transmit
at runtime — not against what you intended.

**Golden rule for this app:** you collect **no photos, no account data, no location,
no contacts**. You **do** let third-party SDKs collect device identifiers and usage
events. Saying "we collect nothing" is a lie and will be caught.

---

## Step 1 — Does your app collect or share any of the required user data types?

**Answer: Yes.**

Do not answer No. AdMob transmits an advertising identifier and Firebase transmits an
app-instance identifier. Answering No while shipping those SDKs is a direct policy
violation.

---

## Step 2 — Is all of the user data collected by your app encrypted in transit?

**Answer: Yes.** All SDK traffic uses HTTPS/TLS.

---

## Step 3 — Do you provide a way for users to request that their data is deleted?

**Answer: Yes.**

You must provide a working path. Two are enough:
1. In-app: Settings → *Delete my data*, which clears App storage.
   ⚠️ **This screen does not exist yet** — `SettingsScreen.kt` has Privacy, Rate and
   Share. Add a "Delete app data" row that calls
   `activityManager.clearApplicationUserData()`, or answer No here and remove the claim.
2. An email address in the privacy policy.

---

## Step 4 — Data types table

Tick **exactly** these rows. Anything not listed here must be answered
*"No, we don't collect this"*.

### Location
| Type | Collect | Share | Purpose | Optional |
|---|---|---|---|---|
| Approximate location | ❌ **No** | ❌ No | — | — |

⚠️ **Read this carefully.** AdMob derives coarse location from IP address on its own
servers. Google's own guidance is that you do **not** declare data your SDKs collect
and process on their side without passing it to you. Declare it only if you see it in
Firebase/AdMob reporting. **Check your AdMob dashboard after two weeks of live traffic
and add it back if it appears.**

### Personal info
| Type | Collect | Share |
|---|---|---|
| Name, email, phone, address | ❌ No | ❌ No |
| Other info | ❌ No | ❌ No |

There is no account and no signup, so this section is genuinely empty.

### Financial info
| Type | Collect | Share |
|---|---|---|
| Purchase history | ✅ **Yes** | ✅ **Yes** (RevenueCat) |
| Payment info (card numbers) | ❌ **No** | ❌ No |

Purchase history: **collected = Yes**, **shared = Yes** with RevenueCat.
Purpose: *App functionality* and *Fraud prevention, security, and compliance*.
Optional: **No** (a purchase cannot be optional).

⚠️ Never declare payment info. Google Play handles cards; you never see them.

### Health & fitness
All ❌ No.

### Messages
All ❌ No.

### Photos and videos
| Type | Collect | Share |
|---|---|---|
| **Photos** | ❌ **No** | ❌ **No** |
| **Videos** | ❌ **No** | ❌ **No** |

⚠️ **This is the most important row in the entire form, and the one an AI tool is most
likely to get wrong.** Because the app *processes* photos it is tempting to declare
Photos. But "collect" means *transmitted off the device*. Yours never are. Declare No,
and make sure `AndroidManifest.xml` requests **no** `READ_MEDIA_IMAGES`,
`READ_EXTERNAL_STORAGE` or `CAMERA` permission — which it does not. It uses the system
photo picker, which grants per-selection access without a permission.

If you ever add a feature that uploads an image, this row must change to Yes first.

### Audio
All ❌ No. No microphone permission is declared.

### Files and docs
| Type | Collect | Share |
|---|---|---|
| Files and docs | ❌ No | ❌ No |

Exports are written to a location the user picks, on-device.

### Biometrics
| Type | Collect | Share |
|---|---|---|
| **Biometric identifiers** | ❌ **No** | ❌ **No** |

⚠️ **Do not declare biometrics just because you use Face Detection.** ML Kit Face
Detection runs entirely on-device and produces a bounding box and landmark points for
one frame. It does not create, store or transmit a facial template or a biometric
identifier. Declaring biometrics here would be inaccurate and would trigger extra
scrutiny.

### Device or other IDs
| Type | Collect | Share | Purpose | Optional |
|---|---|---|---|---|
| **Device or other IDs** | ✅ **Yes** | ✅ **Yes** | Advertising or marketing; Analytics; Fraud prevention, security, and compliance | ✅ **Yes** |

Shared with: **Google (AdMob, Firebase)** and **RevenueCat**.

"Optional = Yes" because the user can reset their advertising ID and can decline ad
personalisation through the UMP consent flow.

### App info and performance
| Type | Collect | Share | Purpose |
|---|---|---|---|
| **Crash logs** | ✅ Yes | ✅ Yes (Google) | Analytics; Developer communications |
| **Diagnostics** | ✅ Yes | ✅ Yes (Google) | Analytics |
| **Other app performance data** | ✅ Yes | ✅ Yes (Google) | Analytics |

All optional: **Yes**.

### Web browsing
All ❌ No. The app opens no in-app browser.

---

## Step 5 — Summary of what you should end up with

**Collected: 5 categories**
1. Purchase history
2. Device or other IDs
3. Crash logs
4. Diagnostics
5. Other app performance data

**Explicitly NOT collected:** Photos, Videos, Location, Personal info, Contacts,
Biometrics, Audio, Files, Health, Messages, Web browsing, Payment info.

---

## Before you submit — 3 checks

1. Open `app/src/main/AndroidManifest.xml` and confirm the **only two** permissions are:
   - `android.permission.INTERNET` — required by AdMob, Firebase and RevenueCat
   - `android.permission.ACCESS_NETWORK_STATE` — required by ML Kit's
     `ModuleInstallClient` to know whether the segmentation model can be downloaded

   Neither one reads user data. **If an AI tool has added `READ_EXTERNAL_STORAGE`,
   `READ_MEDIA_IMAGES` or `CAMERA`, delete it** — the system photo picker makes them
   unnecessary, and their presence directly contradicts the "Photos: No" answer above.
   That contradiction is exactly what a Play review looks for.
2. Confirm `res/xml/data_extraction_rules.xml` and `backup_rules.xml` exclude your
   exports directory, so user photos never enter a cloud backup you would then have to
   declare.
3. After two weeks of live traffic, open AdMob and Firebase and check whether anything
   appears that is not on this list. Update the form if it does. **Play allows you to
   revise the Data Safety section at any time; leaving it wrong is the problem, not
   changing it.**
