# Chapa Cuts

Booking site for a single-chair barbershop. A customer picks a cut and a 30-minute slot, pays a KSh 1 deposit
by M-Pesa (Daraja sandbox) or card (Paystack test mode) to hold the slot, and pays the balance at the shop.

This is a sandbox demo. Everything is in memory and disappears on restart.

- Backend: Java 21, Spring Boot 3.3 (`web` + `validation` only), in `backend/`
- Frontend: Astro 7 + TypeScript, plain `.astro` pages and one CSS file, in `frontend/`
- Astro builds into `backend/src/main/resources/static`, so it ships as one Spring Boot app on one port

## Environment variables

Copy `.env.example` to `.env` and fill in what you have. `.env` is gitignored.

| Variable | What it is |
|---|---|
| `MPESA_CONSUMER_KEY` | Daraja app consumer key |
| `MPESA_CONSUMER_SECRET` | Daraja app consumer secret |
| `MPESA_PASSKEY` | Lipa na M-Pesa online passkey (sandbox one is on the Daraja test-credentials page) |
| `MPESA_SHORTCODE` | `174379` in sandbox |
| `MPESA_CALLBACK_URL` | Public HTTPS URL of `/api/mpesa/callback` on this app (see below) |
| `PAYSTACK_SECRET_KEY` | Paystack `sk_test_…` key |
| `CARD_CURRENCY` | `KES` |
| `CARD_RETURN_URL` | Fallback return page if Paystack ever redirects: `https://<your-host>/paid.html`. The card form normally opens on the page itself. |

The app starts fine with all of these blank. Trying to pay then returns a plain message saying that method
isn't set up. Spring Boot does not read `.env` itself; `run.sh` exports it before starting.

## Run it locally

```bash
cd frontend && npm install && npm run build && cd ..
./run.sh
```

Then open <http://localhost:8080>. `run.sh` uses `mvn` if you have it, otherwise the bundled `backend/mvnw`.

Pages:

- `/` — the booking flow
- `/paid.html?ref=…` — fallback page for a Paystack redirect; normally the card form opens in a Paystack popup on `/` (test mode shows Success / Declined buttons)
- `/barber.html` — the shop's day view (no login; see "Prototype-grade" below)

For frontend work with hot reload, `npm run dev` in `frontend/` serves on :4321 and proxies `/api` to :8080.

### Trying M-Pesa from your laptop

Daraja needs a public HTTPS URL to call back. Run a tunnel (ngrok, cloudflared) to :8080 and set
`MPESA_CALLBACK_URL=https://<tunnel-host>/api/mpesa/callback`. If the callback never arrives the app still
settles: every poll after 4 seconds asks Daraja's STK status query what happened, and a hold expires after 3
minutes anyway.

Sandbox notes: `254708374149` is Safaricom's simulated number (callback, no prompt on any phone). A real
Safaricom number may or may not get a prompt in sandbox.

### Demo escape hatch

`POST /api/bookings/{ref}/simulate` marks a pending booking paid without any provider. It's on by default
(`demo.allow-simulate` in `application.yml`) and shows up as a small "Demo: mark this as paid" link while a
payment is pending. Delete it before anything real.

## Deploy to Render

1. Push the repo to GitHub.
2. Render → New → Web Service → connect the repo. Runtime: **Docker**. The `Dockerfile` at the root builds
   the pages, then the jar, and runs on `$PORT`.
3. Add the environment variables above. Set:
   - `MPESA_CALLBACK_URL=https://<your-service>.onrender.com/api/mpesa/callback`
   - `CARD_RETURN_URL=https://<your-service>.onrender.com/paid.html`
4. In the Daraja portal nothing extra is needed — the callback URL is sent with every STK push. In Paystack
   nothing extra is needed either; the return URL is sent with every initialize call.

Free-tier Render sleeps when idle; the first request after that is slow, and everything in memory is gone.

## Prototype-grade, on purpose

- **No database.** Bookings, holds and uploaded photos live in memory. A restart wipes them.
- **No auth on `/barber.html`.** Anyone with the link sees names and phone numbers.
- **Days are fixed at startup** (today, tomorrow, day after in Africa/Nairobi). Run it past midnight and
  "today" is stale until restart.
- **No cancellation, rescheduling, refunds, SMS or email.**
- **The `/simulate` endpoint exists.** Turn it off or delete it before production.
- **Callbacks aren't authenticated.** Daraja's callback is matched on `CheckoutRequestID` only, which is
  unguessable but not a signature. Paystack is only ever consulted server-to-server by reference, so its
  webhook isn't used.
- **One JVM.** The slot lock is a `synchronized` block; it doesn't survive running two instances.

## How the payment state works

```
NEW ──pay──► AWAITING_PAYMENT ──► CONFIRMED   provider said the money moved
                    ├──────────► FAILED      provider said it didn't (cancelled, timeout, low balance, wrong PIN)
                    └──────────► EXPIRED     3 minutes with no answer (sweeper runs every 30 s)
```

The slot is held from the moment a payment is initiated and released the moment it fails or expires.
A booking is only ever confirmed by a provider result — the M-Pesa callback, the STK status query, or the
Paystack verify — never because a prompt was sent. Confirm is idempotent, since the callback and the poll
often both report the same success.
