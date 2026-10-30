#!/usr/bin/env bash
# =============================================================================
#  Photo Studio Pro -> GitHub, ek hi command mein.
#
#  Kaise use karein:
#    1. github.com par ek KHAALI repository banao (naam: photo-studio-pro).
#       "Add a README" / ".gitignore" / "license" — teeno KOI MAT chuno,
#       repo bilkul khaali hona chahiye warna push conflict karega.
#    2. Terminal kholo, is folder mein aao, aur chalao:
#           bash PUSH-TO-GITHUB.sh https://github.com/<username>/photo-studio-pro.git
#    3. Bas. GitHub ke Actions tab mein build chalta dekho.
#
#  Yeh script JAAN-BOOJH KAR safe hai: force-push nahi karti, remote ko
#  overwrite nahi karti, aur koi secret commit nahi karti.
# =============================================================================
set -euo pipefail

REMOTE="${1:-}"
BRANCH="main"

say()  { printf '\n\033[1;36m==>\033[0m %s\n' "$1"; }
warn() { printf '\033[1;33m    ! %s\033[0m\n' "$1"; }
die()  { printf '\033[1;31m    X %s\033[0m\n' "$1"; exit 1; }

say "Step 0 — git installed hai?"
command -v git >/dev/null 2>&1 || die "git nahi mila. Windows: git-scm.com se install karo. Mac: 'xcode-select --install'. Linux: 'sudo apt install git'."
git --version

say "Step 1 — secrets check (yeh sabse zaroori step hai)"
# In files ko kabhi commit nahi karna. .gitignore mein already hain, lekin
# ek explicit check sasti insurance hai.
for f in keystore.properties app/google-services.json local.properties .env; do
  if git check-ignore -q "$f" 2>/dev/null; then
    warn "$f maujood hai lekin git-ignored hai — theek hai."
  fi
done
if find . -name '*.jks' -o -name '*.keystore' | grep -q .; then
  die "Keystore file mil gayi. Use is folder se bahar le jao — keystore KABHI git mein nahi jaata."
fi
echo "    koi secret commit nahi ho raha."

say "Step 2 — repo init"
if [ -d .git ]; then
  warn ".git pehle se hai — init skip kar rahe hain."
else
  git init -q
  git branch -M "$BRANCH" 2>/dev/null || true
fi

say "Step 3 — git identity (sirf is repo ke liye)"
# Commit ke liye naam/email chahiye. Global set nahi kar rahe, taaki tumhari
# doosri repos par asar na pade.
if [ -z "$(git config user.name  || true)" ]; then
  read -r -p "    Tumhara naam (commit par dikhega): " NAME
  git config user.name "$NAME"
fi
if [ -z "$(git config user.email || true)" ]; then
  read -r -p "    Tumhara email: " EMAIL
  git config user.email "$EMAIL"
fi
echo "    identity: $(git config user.name) <$(git config user.email)>"

say "Step 4 — files stage"
git add -A
COUNT=$(git diff --cached --name-only | wc -l | tr -d ' ')
echo "    $COUNT files staged."
[ "$COUNT" -gt 0 ] || warn "kuch stage nahi hua — shayad pehle hi commit ho chuka hai."

say "Step 5 — commit"
if git diff --cached --quiet 2>/dev/null; then
  warn "commit karne ko kuch nahi. Aage badh rahe hain."
else
  git commit -q -m "Photo Studio Pro — M0 to M10, 8 tools, 117 unit tests

On-device photo toolkit. No server, no AI API, no user accounts.
All 8 tools implemented (BG remover, ID photo, batch, white bg,
collage, PDF, OCR, enhance). Ad policy centralised in AdPolicy.kt.
CI compiles + tests + verifies 16 KB alignment and 64-bit."
  echo "    commit ban gaya: $(git rev-parse --short HEAD)"
fi

say "Step 6 — remote"
if [ -z "$REMOTE" ]; then
  cat <<MSG
    Remote URL nahi diya. Dobara chalao URL ke saath:

        bash PUSH-TO-GITHUB.sh https://github.com/<username>/photo-studio-pro.git

MSG
  exit 0
fi
if git remote get-url origin >/dev/null 2>&1; then
  warn "origin pehle se set hai: $(git remote get-url origin)"
  read -r -p "    Use $REMOTE se badalna hai? (y/N): " ANS
  [ "${ANS:-N}" = "y" ] || [ "${ANS:-N}" = "Y" ] && git remote set-url origin "$REMOTE"
else
  git remote add origin "$REMOTE"
fi

say "Step 7 — push"
warn "Agar yahan username/password maange: GitHub par ab password kaam nahi karta."
warn "Settings -> Developer settings -> Personal access tokens se token banao,"
warn "aur password ki jagah woh token paste karo. (Ya 'gh auth login' use karo.)"
echo
git push -u origin "$BRANCH"

cat <<MSG

============================================================
  HO GAYA.
============================================================
  Ab browser mein jao:  ${REMOTE%.git}/actions

  3 jobs parallel chalenge (~5-10 min):
    compile  -> assembleDebug   (yeh asli gate hai)
    test     -> 117 unit tests
    release  -> AAB + 16 KB alignment + 64-bit + asli size

  PEHLI BAAR 'compile' FAIL HONE KI UMMEED RAKHO. Yeh normal hai —
  is code ki ek bhi line ab tak real Android compile se nahi guzri.

  Kya karna hai:
    1. Actions -> sabse upar wali run -> 'compile' job click karo
    2. Log mein SABSE PEHLA error dhundo
       (aakhri 20 errors uske fallout hain, unhe ignore karo)
    3. Woh error Arena par paste kar do
    4. Fix milega -> commit -> push -> CI dobara

  3-6 iteration mein app compile hone lagega.
============================================================
MSG
