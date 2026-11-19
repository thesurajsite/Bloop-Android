# Payments (Bloop Premium, ₹99 / year)

## How it works

- Plans come from the backend (`GET /api/plans`). The app hard-codes no plan or price.
  The ₹99/year plan is stored in MongoDB (`plans` collection) and can be changed there.
- **New user** (no reminders): after OTP login → paywall → pays → Home.
  They cannot reach Home without paying.
- **Old user** (has reminders): goes to Home after login, even if not premium.
  If their plan has expired, Profile shows a "plan expired" badge that opens the paywall.
- After payment, the app checks the order with the backend every **3 seconds** (up to 25 times).
- If the payment fails, is cancelled or is not confirmed in time, the app asks the backend
  for the user details (`GET /api/me`, which also checks Razorpay). Home opens only if the
  user is premium.
- WhatsApp reminders are sent only to premium users.
- The backend decides whether a payment went through. What the app's checkout says is not trusted.

Key files: `PaywallScreen.kt` (paywall, checkout, polling), `MainActivity.kt` (where the
paywall opens), `ProfileScreen.kt` (expired badge), `BloopApi.kt` (API calls).
Backend: `payments.go`, `razorpay.go`.

## Test mode vs live mode

| | Test mode | Live mode |
|---|---|---|
| Money | Fake | Real |
| Keys | `rzp_test_...` | `rzp_live_...` |
| Needs Razorpay approval | No | Yes |

The keys go in the backend only (`RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET` in `Bloop-backend/.env`
and the `RazorpayKeyId` / `RazorpayKeySecret` SAM parameters). The app gets the key id from the
backend. Never put the secret in the app.

Test payments in test mode: UPI `success@razorpay` (succeeds) or `failure@razorpay` (fails).

## Why Razorpay has to approve us before live payments

Razorpay must check every business before it can collect real money. This is an RBI
(India's central bank) rule, to stop fake shops and scams. It usually takes 24–48 hours.

**"Website" means** the place where customers pay. For Bloop that is either:
- the **Play Store link** of the Android app, or
- the **Bloop website** (`Bloop-Web`), once it is live on a real domain.

**What Razorpay looks for:**
1. A real, working site or app that clearly shows what we sell (Bloop, ₹99/year).
2. Policy pages on the site:
   - Privacy Policy
   - Terms & Conditions
   - Refund / Cancellation Policy
   - Contact Us (email or phone)
3. The price shown on the site (₹99/year).
4. KYC: PAN, a bank account (where the money arrives) and business details.

**Common reasons for rejection:**
- The site is empty or "under construction".
- Policy pages are missing.
- The price is not shown.
- What the site says does not match what we sell.

## Going live: checklist

1. Add the policy pages and pricing to the Bloop website (or the Play Store listing).
2. In the Razorpay Dashboard → Account & Settings → Business website details, submit the site/app.
3. Finish KYC (PAN, bank account, business details).
4. After approval, switch the Dashboard to Live mode and generate live keys.
5. Put the live keys in the backend (`.env` and SAM parameters) and redeploy.
6. In Razorpay settings, check that payments are captured automatically (the backend also
   captures them if not, as a safety net).
7. Optional: Razorpay asks for an email at checkout by default; make it optional in the
   checkout settings if we don't want it.
