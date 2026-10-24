# Play Store listing — BillBox

Everything to paste into Play Console for the closed-testing release. Graphics are in this folder.

## Graphics

| Play Console field | File | Size |
| --- | --- | --- |
| App icon | `icon-512.png` | 512 × 512, 32-bit PNG |
| Feature graphic | `feature-graphic-1024x500.png` | 1024 × 500 |
| Phone screenshots | `screenshots/phone/01…08` | 1080 × 1920 |
| 7-inch tablet screenshots | `screenshots/tablet-7in/01…08` | 1200 × 1920 |
| 10-inch tablet screenshots | `screenshots/tablet-10in/01…08` | 2560 × 1600 |

Upload the screenshots in file order. They are real screens from the app (debug build with sample data, see "Regenerating screenshots").

## Store listing text

**App name** (30 max)
```
BillBox: Bills & Warranty
```

**Short description** (80 max)
```
Scan bills, track warranties, get due-date reminders. Private and offline.
```

**Full description** (4000 max)
```
BillBox keeps every bill, warranty card and due date in one place, so you never hunt for a receipt or miss a payment again.

SCAN ONCE, DETAILS FILLED
• Scan paper bills with automatic edge detection and cropping
• Import photos, screenshots and PDF e-bills, or share them straight from WhatsApp, Gmail or Files
• Store name, date, total, GST, GSTIN and invoice number are read on your phone and filled in for you

NEVER MISS A WARRANTY
• Add products with serial number, model and warranty period; BillBox works out the expiry date
• Reminders 30, 7 and 1 day before a warranty ends
• Track extended warranty and AMC, attach the warranty card, and keep a service history with costs
• Claim card: bill, serial number and service-centre number on one screen, ready to share

PAY EVERY BILL ON TIME
• Rent, EMIs, electricity, broadband, insurance, subscriptions and document renewals like PUC
• Reminders before each due date, with autopay tracking
• "Mark paid" saves the bill and moves the next due date
• See your fixed costs and subscription spend per month and per year

SEE WHERE YOUR MONEY GOES
• Monthly total with change vs last month
• Spending by category and a 12-month trend
• Category budgets with alerts at 80% and 100%

TAX AND REIMBURSEMENT READY
• Mark bills under 80C, 80D, 80G, HRA or LTA
• Export a PDF with bill photos, or an Excel (CSV) file, for any period, including the Indian financial year
• Separate business expenses for reimbursement and GST claims

PRIVATE BY DESIGN
• No account, no sign-up, no ads
• Your bills stay on your phone; text is read on-device and nothing is uploaded
• Optional app lock with fingerprint, face or screen lock
• Delete all data any time

Made for India: ₹ amounts with Indian digit grouping, GST fields and the April–March financial year.
```

**Category:** Finance · **Tags:** Budgeting, Personal finance, Productivity

**Contact email:** sabitri.gorai@gmail.com · **Privacy policy:** URL where `docs/PRIVACY_POLICY.md` is hosted

## Release notes (closed testing, 1.0.0)
```
First test release of BillBox. Please try: scanning a bill, adding a product warranty, adding a recurring bill, and exporting a PDF. Send feedback through Play's "Send feedback" link or by email.
```

## App content answers

| Section | Answer |
| --- | --- |
| Privacy policy | Hosted URL of `docs/PRIVACY_POLICY.md` |
| Ads | No, the app does not contain ads |
| App access | All functionality is available without special access (no login) |
| Content rating | Questionnaire category "Utility, productivity, communication or other"; answer No to all content questions → expected rating: Everyone / 3+ |
| Target audience | 18 and over (financial records); not designed for children |
| News app | No |
| Government app | No |
| Financial features | "My app doesn't provide any financial features" (it records bills; it does not move money, lend or offer financial products) |
| Health | No health features |
| Data safety | See below |

### Data safety

- **Does your app collect or share any of the required user data types?** Yes, only because Google ML Kit / Play services may send diagnostics.
  - **App info and performance → Diagnostics**: Collected, not shared, processed ephemerally = No, required, purpose: App functionality / Analytics. (Covers ML Kit's anonymous usage and error metrics.)
- **Is all collected data encrypted in transit?** Yes (Google services use HTTPS).
- **Can users request data deletion?** Yes: Settings › Delete all data, or uninstall.
- Bills, photos, financial details, files: **not collected** (they never leave the device unless the user shares or exports them themselves; user-initiated sharing to another app is not "sharing" under Play's definition).

## Closed testing checklist

1. Create the app in Play Console (default language English (India), app, free).
2. Fill in **App content** using the table above, and **Store listing** with the text and graphics here.
3. **Testing › Closed testing › Create track**, add testers (email list or Google Group), choose countries (India).
4. Upload `app-release.aab` (signed with your upload key; see README "Release build"), add the release notes, roll out.
5. Share the opt-in link with testers. Personal developer accounts created after 13 Nov 2023 need **12+ testers opted in for 14 consecutive days** before applying for production.

## Regenerating screenshots

Debug builds include `DemoDataReceiver`, which replaces all data with the sample set used here:

```
adb shell am broadcast -n com.billbox.app/.DemoDataReceiver
```

Phone: emulator at 1080×1920 @ 420 dpi. 7-inch: `adb shell wm size 1200x1920` and `wm density 320`. 10-inch: `wm size 2560x1600` and `wm density 320`. Reset with `wm size reset` and `wm density reset`.
