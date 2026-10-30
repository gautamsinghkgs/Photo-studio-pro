# GitHub par push karna — 2 raaste

## Raasta 1 (recommended): script

```
bash PUSH-TO-GITHUB.sh https://github.com/<username>/photo-studio-pro.git
```

Script har step par batati hai kya ho raha hai, secrets check karti hai, aur
force-push kabhi nahi karti. Detail mein: `PUSH-TO-GITHUB.sh` ka header.

## Raasta 2: bina command line ke (agar git se dar lagta hai)

GitHub ki website par folder drag-and-drop kar sakte ho:

1. `github.com` → **New repository** → naam `photo-studio-pro`
2. ⚠️ **"Add a README file", ".gitignore", "license" — TEENO MAT CHUNO.**
   Repo bilkul khaali hona chahiye, warna conflict hoga.
3. **Create repository**
4. `photo-studio-pro.zip` ko extract karo
5. Us extracted folder ke **andar ke saare files aur folders** ko browser mein
   drag karo (GitHub relative paths preserve karta hai)
6. Neeche **Commit changes**

⚠️ **Drag-drop mein `.github` folder miss ho jaata hai** — kyunki woh dot se
shuru hota hai aur macOS/Linux file managers use chhupa dete hain.
**Check karna ki `.github/workflows/ci.yml` upload hua hai.** Agar nahi hua toh
CI chalega hi nahi, aur poora fayda gaya.

- Windows Explorer: View → tick "Hidden items"
- macOS Finder: `Cmd + Shift + .`

Agar `.github` upload na ho paaye, toh GitHub web UI se manually banao:
**Add file → Create new file** → naam mein `/.github/workflows/ci.yml` type karo
(slash se folder ban jaata hai) → content paste karo.

---

## ⚠️ Jo cheez kaam NAHI karegi

**Repo ko Arena se connect nahi kar sakte.** GitHub connector Arena offer hi
nahi karta — verified. Toh main tumhara repo directly padh nahi sakta, aur
Actions ke logs bhi nahi dekh sakta.

**Loop aise chalega:**

```
tum: push  →  CI compile karta hai  →  tum pehla error copy karte ho
  ↑                                                    ↓
  └──────────  main fix deta hoon  ←──  tum mujhe paste karte ho
```

Yeh loop 3–6 baar mein app compile kar dega. Extra 2 minute lagte hain har
iteration mein — itna hi nuksaan hai connector na hone ka.

### Agar repo PUBLIC rakha toh ek chhota fayda

Main `raw.githubusercontent.com/<username>/photo-studio-pro/main/...` se files
fetch kar sakta hoon. Matlab main **verify kar sakta hoon ki push sahi landed
hua ya nahi** — jaise `.github/workflows/ci.yml` pahuncha ya nahi, ya
`libs.versions.toml` mein versions sahi hain.

Public rakhne ka matlab hai tumhara code sabko dikhega. Yeh tumhara faisla hai —
code mein koi secret hai hi nahi (verified), lekin business idea public ho jaata
hai. **Private bhi bilkul theek hai**, CI private repos par bhi free chalta hai
(2,000 min/mahina ≈ 200+ builds).

---

## Push ke baad pehla kaam

**Actions tab → `compile` job → SABSE PEHLA error mujhe bhejo.**

200 lines paste mat karna. Pehla error hi asli hota hai; baaki 20 uske fallout
hain aur unhe fix karne ki koshish karne se time barbaad hota hai.
