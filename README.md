# BillBox

Save every bill, track warranties, get reminders and see where your money goes. Native Android app (Kotlin + Jetpack Compose), offline-first: all data stays on the phone.

## Run it

Open this folder in Android Studio and press **Run**, or from a terminal:

```
gradlew.bat :app:installDebug
gradlew.bat :app:testDebugUnitTest
```

Min Android 8.0 (API 26). Scanning and text recognition use Google Play services (the OCR model downloads on install), so test them on a device or emulator image with Play Store.

## Release build

1. Create an upload key once (keep the file and passwords safe; losing them means asking Play support to reset the key):
   ```
   keytool -genkeypair -v -keystore billbox-upload.keystore -alias upload -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Next to `settings.gradle.kts`, create `keystore.properties` (git-ignored):
   ```
   storeFile=billbox-upload.keystore
   storePassword=...
   keyAlias=upload
   keyPassword=...
   ```
3. Bump `versionCode` (and `versionName`) in `app/build.gradle.kts` for every upload, then build:
   ```
   gradlew.bat :app:bundleRelease
   ```
   Upload `app/build/outputs/bundle/release/app-release.aab` to Play Console.

Room schemas are exported to `app/schemas/`. Commit them: when the database changes, bump the version and add a migration so testers keep their bills.

The privacy policy is in `docs/PRIVACY_POLICY.md`. Host it publicly, then put the URL in `privacy_policy_url` (`app/src/main/res/values/strings.xml`) and in Play Console.

## What's built

| Area | Features |
| --- | --- |
| Capture | Camera scan with auto crop and edge detection (ML Kit Document Scanner), gallery (up to 5 pages), PDF import (pages rendered for preview), share-sheet import from WhatsApp/Gmail/Files, manual entry |
| Auto-fill | On-device OCR (ML Kit) + `ReceiptParser`: merchant, date, total, GST (CGST+SGST+IGST), GSTIN, invoice no., suggested category. Only empty fields are filled |
| Bills | Categories, payment method, tags, notes, business flag, income-tax section (80C/80D/80G/HRA/LTA), return window, duplicate detection, edit/delete/share |
| Search | Full-text over merchant, notes, tags, invoice no. and OCR text; filters by period (incl. Indian financial year), category, business; grouped by month with totals |
| Warranty | Products linked to bills, standard + extended/AMC months, expiry status, warranty card photo, claim card with "Call service" and "Share for claim", service history with costs |
| Recurring | Utilities, rent, EMI, insurance, subscriptions, document renewals; autopay; "Mark paid" saves a bill and moves the due date; fixed-cost and subscription totals |
| Insights | Month total vs last month, category donut, 12-month bars, budgets with progress, top merchants |
| Budgets | Per-category monthly budget; alerts at 80% and 100% |
| Reminders | Daily WorkManager job at ~9 am: warranty 30/7/1 days before, due dates, return windows, budgets; never sends the same reminder twice; max 3 notifications + a summary |
| Export | PDF report (summary table + one page per bill photo) or CSV (opens in Excel), filtered by period, category, tax-only or business-only — e.g. an ITR tax pack |
| Security | App lock with fingerprint/face/screen lock (re-locks after 1 minute in background), private storage, no backup to cloud, delete-all-data |

## Code map

```
app/src/main/java/com/billbox/app/
  BillBoxApp.kt          AppContainer (hand-wired dependencies), reminder scheduling
  MainActivity.kt        App lock, share-sheet intake
  data/                  Room entities, DAOs, database (seeds 12 categories), Repository
  ocr/                   ML Kit wrapper + ReceiptParser (unit-tested)
  reminders/             ReminderLogic (unit-tested) + ReminderWorker
  export/                CSV and PDF export
  settings/              DataStore preferences
  util/                  Money (paise, Indian grouping), Dates (financial year), FileStore
  ui/                    Compose screens: home, bills, warranty, recurring, more, lock
```

Money is stored as whole paise (`Long`) so totals never drift.

## Not built yet (needs a backend)

These spec items need a server and are the next phase:

- Cloud backup and multi-device sync (Supabase or Firebase)
- Family sharing with roles
- Email-forwarding inbox for e-invoices
- Claude vision OCR for line items, and "Ask your bills" chat
- Multi-currency, bill splitting
