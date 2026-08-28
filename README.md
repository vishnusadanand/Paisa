# Paisa

An Android expense tracker that reads your bank and card SMS alerts, sorts them into
categories, and shows you where the money went. Everything stays on the phone — no
account, no server, no network permission at all.

## What it does

- **Reads bank/card SMS automatically.** A background receiver parses each alert as it
  arrives; a one-off inbox scan imports everything already sitting in your messages.
- **Categorises spending** across 14 buckets (Food & Dining, Groceries, Transport, Bills
  & Utilities, Entertainment, Shopping, Subscriptions, Health, Education, Travel, Cash &
  ATM, Transfers, Investments, Other).
- **Asks when it isn't sure.** Anything it can't place lands in the Review tab with the
  original SMS visible. One tap assigns it; "Remember my choice" pins that payee forever
  and back-fills every earlier transaction from them.
- **Manual override everywhere.** Any transaction can be re-categorised, renamed,
  corrected, excluded from totals, or deleted. Cash spending can be added by hand.
- **Monthly analysis.** Donut chart by category, day-by-day bars, six-month trend,
  top payees, month-over-month comparison, and optional per-category budget caps.

## Building it

You need Android Studio (Ladybug or newer) or a command-line Android SDK with API 35.

```bash
cd paisa
./gradlew assembleDebug          # APK at app/build/outputs/apk/debug/app-debug.apk
./gradlew test                   # runs the SMS parser test suite
./gradlew installDebug           # with a phone connected over USB/ADB
```

First run takes a while — Gradle downloads the Android Gradle Plugin, Compose and Room.

The project was written and reviewed but **not compiled**, because the environment it was
authored in has no access to Google's Maven repository. Expect the possibility of a
version-alignment fix on the first build; the versions pinned in `app/build.gradle.kts`
(AGP 8.7.2, Kotlin 2.0.21, Compose BOM 2024.10.01, Room 2.6.1) are a known-good set.

## Installing it

Sideload the APK, or run `installDebug` from Android Studio. **This app cannot go on the
Play Store as-is**: Google restricts `READ_SMS` / `RECEIVE_SMS` to apps whose core
function is being the default SMS handler, and a personal expense tracker does not
qualify for that exemption. For your own phone this is irrelevant — sideloading works
fine. If you ever wanted to distribute it, the workaround is to read notifications via
`NotificationListenerService` instead of SMS, which needs no special review.

On first launch, go to **Settings → Grant SMS access**, then **Full rescan** to import
your message history.

## How the parsing works

`parse/SmsParser.kt` is the heart of it. Everything is content-driven rather than
sender-driven, because Indian SMS sender IDs (`AD-HDFCBK`, `JM-ICICIB`, …) vary by
telecom operator and circle.

The order matters:

1. **Reject non-events** — OTPs, statement reminders, declined transactions, mandate
   registrations, and marketing copy.
2. **Strip balance and limit figures** before reading the amount. Without this,
   "Avl Bal Rs.42,310" gets logged as a ₹42,310 expense — the single most common way
   these parsers go wrong.
3. **Read the amount.** Three patterns: `Rs.1,250.00`, `1250 INR`, and SBI's bare
   `debited by 150.0`.
4. **Read the direction.** Whichever of the debit/credit keyword sets appears first wins,
   with "credit card" masked out first so a card purchase isn't read as income.
5. **Require an identifier.** A real alert always names an account tail, a card tail, a
   reference number, or a UPI handle. Marketing SMS never do — this check is what stops
   "Get Rs.500 cashback on your first order!" from becoming ₹500 of phantom income.
6. **Extract the payee** — from a UPI path (`UPI/P2M/431203/Blinkit`), a VPA
   (`swiggy@axl`), or a positional phrase (`at AMAZON`, `trf to ZOMATO`, `Info: …`).

The parser is deliberately conservative: no amount *and* no direction means the message
is dropped entirely. A missed transaction you can add by hand in ten seconds; a phantom
one quietly poisons three months of charts.

`parse/Categorizer.kt` then assigns a category in three layers — your own pinned rules
first, then a ~350-keyword merchant dictionary (longest match wins, so "amazon prime"
beats "amazon"), then channel fallbacks for ATM withdrawals and NEFT/IMPS transfers.
Anything left over is `Uncategorized` and goes to you.

### When a bank changes its wording

Add the new message as a test case in `app/src/test/java/com/visa/paisa/SmsParserTest.kt`
first, watch it fail, then adjust the regex. Finding out from a red test is much cheaper
than finding out from a wrong pie chart three weeks later.

### Adding merchants

Append keywords to the relevant list in `Categorizer.kt`. In practice you may not need
to — categorising a payee once in the Review tab pins it permanently, so the dictionary
mostly matters for the first few weeks.

## Design decisions worth knowing

- **Duplicate alerts are collapsed.** Banks often fire two SMS for one purchase (account
  + card). Where a reference number exists it is the identity; otherwise amount + payee
  inside a five-minute window.
- **Transfers and Investments are excluded from spend totals** by default. Moving money
  between your own accounts isn't consumption, and counting it wrecks the monthly
  picture. Both still appear in the transaction list.
- **Credits are recorded but not netted off.** A refund shows as money in; it does not
  reduce that month's category spend. Exclude the original purchase if you want the
  refund to cancel it.
- **No cloud backup.** `dataExtractionRules` blocks both cloud backup and device
  transfer, so your transaction history cannot leak through a Google backup. The
  trade-off is that uninstalling takes the data with it — export CSV from Settings
  occasionally.

## Project layout

```
app/src/main/java/com/visa/paisa/
├── parse/          SmsParser, Categorizer, category list   ← the interesting part
├── data/           Room entities, DAOs, Repository (ingest, dedupe, export)
├── sms/            SmsReceiver — the live SMS listener
├── ui/             Compose screens: Home, Analysis, Review, Settings, sheets
│   ├── charts/     Donut, daily bars, trend bars, budget bars (no chart library)
│   └── theme/      Colour scheme and the fixed per-category palette
└── util/           Indian digit grouping (₹12,34,567), date helpers
```

## Known limitations

- The "today" figure is computed when the app starts; leaving it open across midnight
  needs a restart to roll over.
- Only SMS is read. Alerts that arrive by email or app push are invisible.
- Transaction dates come from the SMS timestamp, not any date inside the message body.
  These differ only when a bank sends an alert late.
