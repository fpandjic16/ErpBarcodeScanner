# PrimaBarcode — User Guide

**Audience:** warehouse and store staff who use the PrimaBarcode app day-to-day to scan and record inventory movements (shipments, receipts, transfers).

This guide explains what every screen does, what every button and toggle means, and what to do when something looks wrong. It assumes no technical background — if you can use a smartphone, you can use this guide.

---

## Table of Contents

1. [What This App Does](#1-what-this-app-does)
2. [Key Concepts You'll See Everywhere](#2-key-concepts-youll-see-everywhere)
3. [First-Time Setup](#3-first-time-setup)
4. [The Main Menu](#4-the-main-menu)
5. [Choosing Your Location & Responsibility Center](#5-choosing-your-location--responsibility-center)
6. [Document Types](#6-document-types)
7. [The Document List](#7-the-document-list-orders--errors)
7a. [The RECORDINGS Section](#7a-the-recordings-section)
8. [Downloading Documents](#8-downloading-documents)
9. [Scanning a Document (Recording Screen)](#9-scanning-a-document-recording-screen)
10. [Understanding Warnings You May See](#10-understanding-warnings-you-may-see)
11. [The Special Barcode Format (Barcode\|UoM\|Qty)](#11-the-special-barcode-format-barcodeuomqty)
12. [Uploading Documents](#12-uploading-documents)
13. [Fixing Upload Errors](#13-fixing-upload-errors)
14. [The Dashboard (Document Overview)](#14-the-dashboard-document-overview)
15. [Filtering Documents](#15-filtering-documents)
16. [Settings — Every Option Explained](#16-settings--every-option-explained)
17. [Signing In and Out](#17-signing-in-and-out)
18. [Common Situations & What To Do](#18-common-situations--what-to-do)
18a. [Removing an Operator From a Device](#18a-removing-an-operator-from-a-device)
18b. [Sign-in QR Codes (for administrators)](#18b-sign-in-qr-codes-for-administrators)
19. [Glossary](#19-glossary)

---

## 1. What This App Does

PrimaBarcode is a handheld scanning app used to record what's physically been picked, received, or moved in a warehouse or store, and to send that information back to the company's central business system (Dynamics NAV / Business Central) once you're done.

The everyday cycle looks like this:

```
1. DOWNLOAD  — pull the list of documents (orders) you need to work on
2. SCAN      — walk the warehouse, scanning barcodes against each document's lines
3. UPLOAD    — send your completed (or partial) work back to the central system
```

You always need to download a document before scanning against it — there's no offline/before-download scanning mode. A barcode you scan is checked against that document's expected lines immediately; anything that doesn't match is rejected on the spot (see [§2.3](#23-unmatched-scans)).

---

## 2. Key Concepts You'll See Everywhere

### 2.1 The four scan statuses (color language)

Every line on a document — and the document as a whole — is always in one of four states, shown with a consistent color everywhere in the app:

| Status | Meaning | Color |
|---|---|---|
| **Empty** | Nothing scanned yet (0 of expected quantity) | 🔴 Red |
| **Partial** | Some, but not all, of the expected quantity scanned | 🟠 Orange |
| **Ready** (internally "Exact") | Scanned quantity exactly matches expected quantity | 🟢 Green |
| **Over-qty** | You've scanned *more* than the expected quantity | 🔵 Blue |

A document's overall status follows simple rules:
- If **any single line** is Over-qty, the **whole document** shows as Over-qty — even if every other line is perfect.
- A document only shows **Ready** (green) when every line is exact.
- Otherwise it shows **Partial**.

### 2.2 Document lifecycle

Behind the scenes, every document also moves through a lifecycle as you work on it:

`Downloaded → In Progress → Completed → (Pending Upload) → gone (on successful upload)`

If an upload fails, the document instead becomes **Upload Failed**, shown on the **Errors** tab, and stays on your device until you retry (or the office fixes the underlying problem).

### 2.3 Unmatched scans

If you scan a barcode that isn't listed as an expected item on the current document, the app **doesn't record it at all** — you'll see a "Barcode not found" message, the scan bar briefly flashes red, and the device vibrates. Nothing is added anywhere; double-check you're on the right document and that you scanned the right item, then try again.

**Inventory is the exception.** There an item that is not on the batch is something you found on the shelf, so the app asks whether to count it instead (see [Inventory](#inventory)).

---

## 3. First-Time Setup

**You sign in every time the app opens.** Your name and password; nothing else in the app is reachable until you do. That is not only about privacy — every scan records who made it at the moment you make it, so the app has to know who is holding the device before the first scan.

**Your work is yours.** Documents you download and scans you make are visible only to you. Another operator signing in on the same device sees their own, not yours — and cannot send yours. If you forget to upload at the end of a shift, nothing is lost; it is waiting for you the next time you sign in on that device.

Two of you can work the same document at the same time. Each of you has your own copy with your own scans, and both go to the central system, where the quantities add up — which is what you want when you are splitting one job.

**Setting up a brand-new device comes before all of this.** A device fresh out of the box does not know where the central system is, and the sign-in screen cannot confirm a password without it. So the sign-in screen has an **External system setup** button that opens the connection settings without signing in — normally IT or a consultant picks the company there once, which fills in every address, and from then on everyone just signs in. The same screen is where the site switches on the document types it works with; a new installation starts with none. Beside it, **About the app** shows the app's details (see [§16](#16-settings--every-option-explained), System Info), also without signing in.

**Every sign-in needs the central system to be reachable**, because only it can confirm your password. If it is down, nobody can open the app — including you, and including work you have already scanned. That work is not lost: it stays on the device and is waiting when the system answers again.

When you first open the app:

1. Sign in with your user name and password. Names already used on that device are listed, so you can tap yours instead of typing it. Type your name however you like — `alice`, `PRIMA\alice` and `alice@prima.hr` are all the same person to the app. **If you have a sign-in QR code, pull the trigger or tap *Scan QR code*.** On a device with a scanner the button starts the scanner; only a device without one opens the camera. The code fills in both fields; you still tap the button to go in.
2. Enter your **Username** and **Password**, then tap the button shown (its label changes depending on what triggered it — "Sign in", "Test connection", or "Sign in & Sync"). If your company has a Windows domain configured (Settings → External System Configuration), you just type your plain username — otherwise type it as `user@domain` or `DOMAIN\user`. Submitting actually verifies your credentials against the central system before signing you in — if they're rejected, you'll see why on the same screen and can correct and retry.
3. Your session stays signed in for a configured period (commonly 24 hours, sometimes longer — named in the footer text below the button, e.g. *"Credentials stored encrypted with AES-256-GCM for 24 hours."*). After that period, you'll simply be asked to sign in again next time it's needed — your credentials are encrypted on the device the whole time.
4. Pick your **Responsibility Center** and **Location** (see [§5](#5-choosing-your-location--responsibility-center)) — this tells the app which warehouse/store you're working from and filters everything you see to that scope.

---

## 4. The Main Menu

This is the home screen you land on every time you open the app.

- **Top bar**: shows your name. The round button top-left with your initials opens your **User Info** — user ID, name, Responsibility Center, Location, and a sign-out button. A gear ⚙️ icon top-right opens **Settings**.
- **Today card** (tap it anywhere): a summary of today's activity — total lines scanned, and counts of Ready / Partial / Over / Error documents. Tapping this card opens the **Dashboard**.
- **Location/RC bar**: two side-by-side pills showing your current Responsibility Center code and Location code. Tap **either one** to open the Location & RC picker and change them. Beside them, once the device has been set up for one, the system it works with: **Test** (in amber) or **Production**. On Test, scans go to the test company, not to real stock.
- **Document type list**: one row per document type that is switched on for this device (Warehouse Shipment, Warehouse Receipt, Retail Shipment, Retail Whse. Receipt, Transport Sheet, Complaint Shipment, Complaint Receipt, Inventory), each showing:
  - An icon and the type's name.
  - A thin colored mini-bar (if there's any activity) previewing the mix of statuses across that type's documents.
  - A count of how many documents of that type you currently have.
  - Tapping a row opens that type's **Document List**. If the type can't be opened yet — no location or responsibility centre selected — the tap tells you so instead of doing nothing. Complaint Shipment and Complaint Receipt need a responsibility centre; every other type needs a location.
  - **On a new installation every type is switched off**, and the list says so, with a button to the External System Configuration, where each type has its own switch. A device updated from an older version keeps the types it had; the two complaint types start switched off there too.
- **RECORDINGS row**, under its own heading: opens the list of every document you have scanned anything into, across all types. Reads zero when you have scanned nothing. See [§7a](#7a-the-recordings-section).

---

## 5. Choosing Your Location & Responsibility Center

> **After an app update, do this first.** The list of locations is re-fetched rather than carried over, and your working location is now remembered per person rather than per device — so both start empty. Open this screen, tap **Refresh** to fetch the locations, then pick yours. Until you do, the strip at the top of the Main Menu shows `—` and no document list opens: tapping a document type tells you to choose a location or responsibility centre first. Anything you have already scanned stays visible in **RECORDINGS** throughout.

Open this screen by tapping either pill on the Main Menu.

- **Responsibility Center** row — tap to open a searchable list of RCs. Type to filter by name or code. If you leave the search blank, an extra **"Any responsibility center"** option appears at the top to clear your selection.
  - ⚠️ **Switching RC clears your currently selected Location** — you'll need to pick a location again, since locations belong to a specific RC.
- **Location** row — tap to open a searchable list of locations (already narrowed to your selected RC, if any). Selecting a location also automatically updates the RC to match, if you hadn't already set one.
- **Refresh icon** (top-right): pulls a fresh list of locations/RCs from the central system. If you're not signed in, it will ask you to sign in first (button reads "Sign in & Sync"). While refreshing, a small spinner replaces the icon. The subtitle under the title shows when the list was last synced, e.g. *"Synced 07.08.2026 · 14:32"*.
- **Apply** button at the bottom confirms your selection and returns you to where you came from.

---

## 6. Document Types

| Type | What it represents |
|---|---|
| **Warehouse Shipment** | Goods going out from a warehouse to a store |
| **Warehouse Receipt** | Goods coming in from a supplier into a warehouse |
| **Retail Shipment** | Goods going out from a store to a customer |
| **Retail Whse. Receipt** | Goods/returns coming in from a store back to a warehouse |
| **Transport Sheet** | Inter-location transfer documents |
| **Complaint Shipment** | Complaint packages going out on a complaint shipment (scoped by responsibility centre by default) |
| **Complaint Receipt** | The same complaint shipment's packages being received (scoped by responsibility centre by default) |
| **Inventory** | Counting stock: one inventory journal batch from the central system |

Which of these you see (and whether they're filtered by Location or by Responsibility Center) depends on how your company has configured the app — ask your administrator/consultant if a type behaves unexpectedly.

### Complaint Shipment and Complaint Receipt

Both are the same complaint shipment in the central system, at two points in its life.

- **Complaint Shipment** lists a complaint shipment while it is released and waiting to go. **Complaint Receipt** lists it once it has been shipped and is waiting to be received. Choose the responsibility centre first; neither type uses a location.
- **One line per complaint** on the shipment. The line's number is the complaint number, and the expected quantity is its number of **packages**. Scan each package's label once.
- **Every package has to be scanned.** The central system marks the scanning complete only when all packages of all complaints are scanned, and a complaint shipment cannot be received before that. If a package did not arrive, a new complaint is opened for it; the shipment is not received in part.
- Responsibility centres that work through a cross-dock warehouse do not use these two types.

### Inventory

An inventory document is one **inventory journal batch** from the central system: the items at your location, with the quantity the system expects to find.

- **You pick the batch when downloading** (see [§8](#8-downloading-documents)). Several can be open at once, and each is downloaded on its own.
- **Count everything on the shelf.** The expected quantity is shown, so you can see where your count differs from the system.
- **An item that is not on the batch is still counted.** The app asks "Not on the batch — count it?"; confirm with **Count it**. The item appears at the top of the list as its barcode, marked "Not on the batch", and further scans of it count without asking. If you added it by mistake, open it and set its quantity to 0, and it disappears.
- **Several people can count the same batch**, each on their own device. The central system adds up everyone's scans; each device shows only its own.
- Lines nobody scanned are counted as **0** when the central system processes the count.
- **The office sends the batch for scanning and finishes the count.** A batch appears in the download list once it has been sent and disappears when the count is finished. After that the device can no longer send scans for it, so send yours before the office finishes.
- **A set is counted either by its own label or by its packages, not both.** The batch lists the set and its packages, so the way you do not use stays at 0. Counted by packages, the central system counts a set only when all of its packages are there.
- **Quantity labels** (BARCODE|UOM|QTY) count the quantity printed on them.
- **The expected quantity can be 0 or negative** when the system's stock is wrong. Such a line turns blue at the first scan, without an over-scan warning.

---

## 7. The Document List (Orders / Errors)

Reached by tapping a document type on the Main Menu. Shows all documents of that type, split into two tabs:

| Tab | Shows |
|---|---|
| **ORDERS** | Documents you haven't finished yet — freshly downloaded, in progress, being sent, or failed |
| **ERRORS** | Documents that failed to upload |

**Each document row** shows: document number, a colored status chip (Ready/Partial/Empty/Over-qty), the source location, and the document date. If it's in the Errors tab, an extra red "ERROR" chip appears, along with the failure reason.

**Scanning/typing a document number** in the scan bar at the top jumps straight to that document if it exists. If it doesn't, you'll see a message that it needs to be downloaded from the central system first — there's no way to create or scan against a document that hasn't been officially released yet.

**Deleting a document's recordings** has moved to the **RECORDINGS** section of the Main Menu — see [§7a](#7a-the-recordings-section).

**Bottom buttons** change per tab:
- **Orders**: `DOWNLOAD` and `UPLOAD`.
- **Errors**: `CLEAR ERRORS` (removes failed documents from your device without uploading them — use with care) and `UPLOAD` (retries them).

`UPLOAD` is greyed out and does nothing when there is nothing waiting to be sent.

The funnel/filter icon (top-right) turns **coral/orange** when a filter is currently active — see [§15](#15-filtering-documents).

---

## 7a. The RECORDINGS Section

Below the document types on the Main Menu, under its own **SCAN RECORDINGS** heading, there is a single **RECORDINGS** row showing how many scans you are carrying and, on the right, across how many documents. It stays there when you have scanned nothing and simply reads zero — which at the end of a shift is worth being able to check.

Tapping it opens the list: every document you have scanned anything into, whatever its type, newest first. Each row shows the document number, its type, how many scans it holds, and how many of those have already gone to the central system. Documents stay on the list until they are uploaded — one that goes through cleanly is finished with and leaves.

Tapping a document there opens its scans as a **tree**:

```
ITEM-A            2/5
   1 PCS   Waiting   12.09. 08:14
   1 PCS   Waiting   12.09. 08:15
ITEM-B            1/1
   1 PCS   Sent      12.09. 08:17
ITEM-C            0/3
   No recordings on this line
```

Every line of the document appears, including ones you haven't touched — seeing `0/3` is the point. Under each line sit the individual scans that add up to its quantity, each with its quantity, the time, and whether it has been **Sent** or is still **Waiting**.

**Deleting one scan**: tap the red bin on its row and confirm. The line's quantity drops by that amount.

A scan that has already been **Sent** shows a padlock instead of a bin. It cannot be deleted here: it is already recorded in the central system, and removing it from the device would only make the device forget what it sent. Correct it in the central system instead.

**Deleting every unsent scan on the document**: press and hold the summary card at the top for about 5 seconds (a progress ring fills in; releasing early cancels). The confirmation tells you how many scans have already been sent — those stay.

If the central system removed a line *after* you scanned it, those scans appear at the bottom under **Recordings without a line**. You cannot delete them here — press UPLOAD on the document and the review will ask you what to do with them.

---

## 8. Downloading Documents

From the **Orders** tab, tap **DOWNLOAD**.

1. If you're not signed in, the sign-in screen appears automatically first.
2. You'll see a filter screen where you can narrow what gets pulled down:
   - **Document Date** — From/To date range.
   - **Destination Code** — free text.
   - **Source Code** or **Responsibility Center** — depending on how this document type is configured, you'll see *one* of these two as a picker (the other is locked to your current selection and hidden).
   - For **Inventory** the date range is replaced by the list of **inventory journal batches** ready for scanning at your location, each with its number, date and number of lines. Tap one to select it; **OK** stays unavailable until you do. Only that batch is downloaded, and batches already on the device stay as they are.
3. Tap **RESET** to clear the filter fields, or **OK** to start the download.
4. A progress indicator shows while documents are pulled in. If anything fails, you'll see an error message listing what went wrong.

**Important**: downloading **never deletes your existing scans**. If you already have local progress on a document that's no longer in the new download (e.g. it was completed and removed on the server side), it's kept safely on your device rather than silently discarded — it just won't appear in Orders until it's addressed.

---

## 9. Scanning a Document (Recording Screen)

This is the core screen — opened by tapping any document from a list.

### 9.1 Overview

You'll see:
- The **document number** as the screen's title, with how many lines are exact out of total beneath it.
- A summary bar: source/destination codes on the left, total scanned/expected on the right (turns green once everything is exact).
- A thin progress bar summarizing every line's status at a glance.
- The full list of expected lines — item number, item name, and a large scanned/expected counter in that line's status color.
- At the bottom: a **scan bar** to type/scan a barcode.

**Changing location hides the other location's documents**, including ones you have already scanned into — a document for CS165 is not listed while you are working at CS175. Nothing is lost: the **RECORDINGS** section lists everything you have scanned anywhere, whatever location you are on now, and that is where to go looking for work you left at another location.

To review what you have scanned, line by line and scan by scan, use the **RECORDINGS** section on the Main Menu ([§7a](#7a-the-recordings-section)). It replaced the old "last scans" tape, which forgot everything the moment you left this screen.

### 9.2 Scanning

You can scan in three ways:
1. **Hardware scanner trigger** (if your device has one) — just point and scan; it works anywhere on this screen.
2. **Scan bar** at the bottom — tap the keyboard icon to type a barcode manually if needed.
3. **Camera** — only on devices without a hardware scanner, where the scan bar shows a camera icon instead: tap it and point the camera at the barcode. It beeps and vibrates on a successful read, then closes itself. The camera is the fallback input, so it always does one scan at a time; use the hardware trigger for a run of items.

**What happens when you scan:**
- **Matches a line** → that line's scanned quantity goes up (usually by 1, or by whatever the document specifies per scan), and the line/document status updates live. **The list also jumps to that line and marks it** — its colour deepens for about five seconds and then fades back, so on a long document you can see at a glance what you just scanned instead of hunting for it. If the line was already on screen the list stays put; only the colour marks it.
- **Doesn't match any line** → nothing is recorded. You'll see a "Barcode not found" message and the scan bar briefly flashes red so you know it didn't match a real line (see [§2.3](#23-unmatched-scans)). On an **Inventory** document you are asked instead whether to count it.
### 9.3 Editing a line manually

Tap any line to open its detail view, where you can:
- Use **−1 / +1** buttons for quick adjustments.
- Tap the big number to open a **numeric keypad** and type an exact quantity, then confirm.
- Tap **Apply** to save your change and return to the overview.

Typing a total **replaces** the line's quantity, it doesn't add to it.

**What happens to the individual recordings.** Raising the quantity adds one new recording for the difference, exactly as a scan would. Lowering it undoes your **most recent** scans and stops — older recordings stay exactly as they were, with their own time and the name of whoever made them. So if three separate scans of 1 make up a line and you press −1, two of those three survive untouched. You can see all of this in the RECORDINGS tree ([§7a](#7a-the-recordings-section)).

**What you cannot lower.** If part of this line has already been sent to the central system — which happens when an upload was interrupted partway through — the detail view says so (*"3 already sent"*) and the quantity will not go below that number. The −1 button stops there, and typing something lower is refused with an explanation.

The reason is that the central system's recording table accepts everything and refuses nothing: there is no way to delete or correct a quantity from the device once it has gone. The app therefore never pretends to take it back. You can always go **up**, and to correct what was already sent, it has to be fixed in the central system.

### 9.4 Uploading from here

If there's any scanning activity on the document, a small **Upload** button appears in the top bar, letting you upload without going back to the list.

### 9.5 Leaving the screen

Tapping back takes you straight back to the document list — your progress is saved as you scan, so there's nothing to confirm. Upload separately when you're ready (see [§12](#12-uploading-documents)).

---

## 10. Understanding Warnings You May See

| Warning | When it appears | What to do |
|---|---|---|
| **Over-scanned** | You scanned more than the expected quantity for a line (if the "Warn on over-scan" setting is on) | Just informational — tap OK. The extra quantity is still recorded (shown as blue "Over-qty"). |
| **Unit of measure mismatch** | You used the [special barcode format](#11-the-special-barcode-format-barcodeuomqty) and the unit of measure you scanned doesn't match what the document expects for that item | Informational — the scan was still recorded with the quantity you scanned. Tap OK, and flag it to the office if it looks like a real discrepancy. |
| **Barcode not found** | You scanned something that isn't an expected item on this document | Nothing was recorded — double-check the item and document, then rescan. |
| **Not on the batch** | On an inventory, you scanned an item the batch does not list | Nothing is counted yet. **Count it** if the item really is on the shelf, **Cancel** if the scan was a mistake. |

None of these warnings block or undo your scan — they're all "heads up" notices. The scan has already been recorded by the time you see the dialog.

---

## 11. The Special Barcode Format (Barcode\|Uom\|Qty)

Some printed labels encode more than just the item — they can carry a unit of measure and quantity directly in the barcode, separated by pipe characters (`|`), for example:

```
NTR1234|M|5.6
```

This means: barcode `NTR1234|M|5.6` (the **whole string** is the barcode used for matching — not just the `NTR1234` part), unit of measure `M`, quantity `5.6`.

- If this format is recognized (exactly two `|` characters, with a valid number at the end), the app records **exactly that quantity** — you won't be asked to type one in, even if your Settings normally ask for quantity on unrecognized barcodes.
- If the unit of measure encoded in the label doesn't match what the document expects for that item, you'll see the **Unit of measure mismatch** warning (see above) — but the scan is still recorded as read.
- If the barcode doesn't fit this exact pattern, it's treated as an ordinary barcode.
- **On an inventory** the batch lists the plain barcode (`NTR1234`), because a label on the shelf prints whatever quantity it holds. Such a label lands on that line and counts the quantity printed on it.

**Note**: because some barcode label types (Code 39) cannot physically encode the `|` character, labels using this format must be printed as **Code 128** (or another symbology that supports it). If a special-format barcode scans as garbled text, check the label's barcode type with the office/print team.

---

## 12. Uploading Documents

Tap **UPLOAD** from any document list (or the in-screen upload button while scanning). This sends your recorded scans back to the central system.

- If you're not signed in, you'll be asked to sign in first.
- Depending on how your app is configured, upload either runs in the **background** (you can keep working immediately while it sends) or shows a **blocking progress screen** until it finishes.
- On success, the document is completely removed from your device — you're done with it.
- If something goes wrong (server error, network issue, missing configuration), the document moves to the **Errors** tab instead, with the specific reason recorded — nothing is lost, and only the parts that failed need to be resent (anything that did succeed before the failure is not resent).

---

## 13. Fixing Upload Errors

Open the **Errors** tab (from a Document List, or the Dashboard) and tap a failed document to see:

- A clear "Upload Failed" header.
- Document details (type, source, destination, RC, line count, date).
- The **full error message** returned by the central system — this is the most useful thing to relay to IT/support if the problem isn't obvious (e.g. "not signed in", "server rejected the request", a specific business-system validation message).
- A **Retry Upload** button at the bottom to try again — useful once the underlying issue (network, server, sign-in) has been resolved.

You can also **Clear Errors** in bulk from a document list's Errors tab or the Dashboard's Errors tab — this **removes the failed documents from your device without uploading them**. Only do this if you're sure the data doesn't need to reach the central system (e.g. it was a duplicate, or a test document).

---

## 14. The Dashboard (Document Overview)

Reached by tapping the "Today" card on the Main Menu. A cross-document-type view with three tabs:

- **Errors** — every failed document, across all types.
- **My Location** — every document at your current location, and every complaint at your current responsibility centre. Empty while nothing is selected.
- **All** — every document on the device, no filtering.

An empty tab shows a friendly green checkmark and "No issues" rather than a blank screen. If there's anything with scan progress on the current tab, an **Upload** button appears at the bottom to send it all at once.

---

## 15. Filtering Documents

The funnel icon (top-right of a Document List or the Dashboard) opens a filter screen where you can narrow down what's shown:

- **Status** — pick one or more of Empty / Partial / Ready / Over-qty.
- **Document Type** — pick one or more types (only shown on screens that span multiple types).
- **Document Date** — From/To range (picking a "From" date after "To", or vice versa, automatically adjusts the other to stay valid).
- **Destination Code**, **Source Code**, **Responsibility Center** — free text or pick-from-list, depending on context (some may be locked/pre-filled if you arrived here from a location-specific view).

Tap **RESET** to clear everything, or **APPLY** to confirm. The funnel icon turns coral/orange whenever a filter is currently active, so you always know at a glance if you're looking at a filtered (not full) list.

---

## 16. Settings — Every Option Explained

Open Settings via the gear icon on the Main Menu. **Settings are buffered** — nothing is saved until you leave the screen; if you've made changes, you'll be asked **"Save your settings before leaving?"** with **Yes** (save) / **No** (discard everything you just changed).

**Most settings are yours alone.** Text size, uppercase, language, scan behaviour and your current location/responsibility centre follow you, so the next shift does not inherit them. Three belong to the device and are shared by everyone on it: which document types are switched off, how each type is scoped, and the debugger flag — those are set up once for the site. The External System Configuration is device-wide too.

### Appearance
| Setting | What it does |
|---|---|
| **Text size** | Scales all text in the app up, for readability. A new operator starts on **Larger**. |
| **Uppercase text** | Displays all interface labels in CAPITALS. |
| **Language** | App display language — Croatian until you pick another, whatever language the device itself is set to. A restart may be needed for the change to fully apply everywhere. |

### Scanning
| Setting | What it does |
|---|---|
| **Debounce time** | How long the same barcode is ignored after it is read (200ms–2s; 200ms unless you change it). It stops one scan counting twice when the same code arrives again straight away — a double pull of the trigger, or the same label read twice in a row. To scan the same item again on purpose, pause at least this long. |
| **Haptic feedback** | Vibrate on scan confirmation and errors — on document scanning and QR sign-in alike. |
| **Scan sounds** | Two sounds, so you know without looking whether to check the screen: a **single beep** when a scan lands normally, and a **loud falling two-tone, played four times** ("dee-doo, dee-doo, dee-doo, dee-doo") whenever a scan brings up a warning — the barcode is not on the document, the scanned document number was not found, or the scan took a line over what the document expects (that last one only while the over-quantity warning is switched on). They follow the device's *media* volume, not its notification volume, so they still play when the scanner is on vibrate; use the volume keys for loudness and this switch to turn them off. Same two input paths as the vibration. |
| **Warn on over-scan** | Show a warning when you scan more than the expected quantity for a line. |

### Sync
| Setting | What it does |
|---|---|
| **Enable background sync** | Uploads run in the background so you can keep working instead of waiting on a progress screen. |

### External System Configuration
A single row, **"Server & endpoints"**, that opens the connection settings for the central system — this is normally only touched by IT/consultants during setup. See the Technical Guide for details.

### Debug
| Item | What it does |
|---|---|
| **Debugger active** | Shows the exact web addresses the app is about to contact before every download/upload — useful only for diagnosing connectivity issues with IT support. Leave off for normal daily use. |
| **Export data** | Saves a full dump of everything on your device to a file — useful if IT support asks for diagnostic data. |
| **Insert system defaults** | Lets IT/consultants load, download, or import a starter configuration for the External System connection. A downloaded copy deliberately leaves out the QR sign-in key, so it is safe to pass around; importing such a file leaves the key already on the device untouched. Not something you'd normally use day-to-day. |
| **Clear cache** *(red — destructive)* | Deletes **your** sign-in, documents and scans. Other operators on this device, and the device’s own setup, are untouched. Requires confirmation. Use only when told to by support. |
| **Operators on this device** | Under Account. Shows everyone who has ever signed in here, and lets you remove one — see [§18a](#18a-removing-an-operator-from-a-device). |
| **Delete all documents and recordings** *(red — destructive)* | Deletes all downloaded documents and scans, but **keeps** your settings and sign-in. Requires confirmation. Use this to fully reset your working data without having to sign in again. |

### System Info
Read-only details — app version, Android version, SDK, database version, which system the device is set up for (Test or Production) and which company — useful when reporting a bug to support. The same details are on the sign-in screen under **About the app**, for when you cannot sign in.

### Account
Shows who's currently signed in, and a **Sign out** option (immediate, no confirmation needed).

---

## 17. Signing In and Out

- You sign in every time the app opens — see [§3](#3-first-time-setup).
- Your access to the central system is stored encrypted on the device and expires after a set period. After that, the app asks for your password again the next time it needs the central system (download, upload, refreshing locations) — nothing is lost.
- To sign out, go to **Settings → Account → Sign out of External System**. This takes you back to the sign-in screen, ready for the next operator. It is refused while a transfer is still running — see [§18.2d](#182d-it-says-a-transfer-is-in-progress-and-wont-let-me-sign-out).

---

## 18. Common Situations & What To Do

### 18.1 "Tapping a document type says I need a location"
Every document type needs something selected first: **Complaint Shipment** and **Complaint Receipt** a responsibility centre, every other type a location. Pick them on the location and RC screen ([§5](#5-choosing-your-location--responsibility-center)) and try again — choosing a location fills in its responsibility centre as well. A location that has since disappeared from the list counts as none, so pick again. If there are no locations to choose from, tap **Refresh** on that screen first.

### 18.2 "I scanned the wrong quantity"
Tap into the line and either use −1/+1, or tap the number to type the correct total directly — this replaces the line's recorded quantity, it doesn't add to it. You cannot go below whatever has already been sent to the central system; see [§9.3](#93-editing-a-line-manually).

### 18.2a "I can't find the work I did yesterday"
Check the name you signed in with. Your documents and scans belong to you, so signing in as somebody else shows their work, not yours. Sign out and back in as yourself and everything is where you left it.

Nothing is deleted by signing out, by a colleague using the device, or by your password expiring.

### 18.2b "The previous shift left work on the device"
You will not see it, and that is on purpose — it is theirs to finish and send. Hand the device back, or ask them to sign in and upload.

### 18.2c "It won't let me sign in and says the system can't be reached"
**The central system is what checks your password, every single time.** There is no way into the app while it cannot be reached — not for anyone, including people who have used this device for months. Nothing you have scanned is lost in the meantime; it stays on the device and is there when the system comes back. Report the outage rather than trying another password.

If the system *is* reachable and your password is refused, it has probably been changed in the central system — sign in with the new one.

### 18.2d "It says a transfer is in progress and won't let me sign out"
Something is still going to or coming from the central system. Wait for it to finish. Changing operator mid-transfer would file "this was sent" against the wrong person's records, or drop a download into the wrong person's data.

### 18.3 "I keep getting 'Barcode not found' for an item that's clearly on the document"
Nothing is ever recorded for a rejected scan, so there's nothing to undo — just figure out the mismatch and rescan. Most common causes: you're on the wrong document (double-check the document number in the top bar), the label's barcode doesn't byte-for-byte match what's on the document (a re-print, a different symbology, or stray characters), or — if it's a special `Barcode|UoM|Qty` label — see [§18.7](#187-the-barcode-scanned-as-garbagewrong-characters). If you're confident the item genuinely belongs on the document and it's still not matching, flag it to the office — the document's data may need correcting on the central-system side.

### 18.4 "A document I was working on disappeared from Orders"
It stays on **Orders** all the way through, including while it's being sent and after a failed send. If it's genuinely gone, it uploaded successfully — documents leave the device once everything on them has been accepted. Anything you've scanned into is also listed under **RECORDINGS** on the Main Menu, so check there too.

### 18.5 "My upload failed"
Open the document from the **Errors** tab to read the exact reason, then tap **Retry Upload**. If the message isn't clear (e.g. a technical server error), relay the exact text to IT support.

### 18.6 "I want to start a document completely over"
Open it from **RECORDINGS** on the Main Menu, then press and hold the summary card at the top for ~5 seconds and confirm. This wipes every scan that hasn't been sent yet. Scans already accepted by the central system stay — they're recorded there, and the confirmation tells you how many there are.

### 18.7 "The barcode scanned as garbage/wrong characters"
If it's a special `Barcode|UoM|Qty` label, verify with the office that it was printed as **Code 128** — the older Code 39 symbology cannot represent the `|` character correctly and will scan as gibberish.

---

## 18a. Removing an operator from a device

When someone leaves, or a device is handed on for good, their name can be taken off it: **Settings → Account → Operators on this device**, then the red bin next to their name.

This is not just tidying the list. Removing an operator also deletes, from that device:

- every document they had downloaded,
- **every scan they had made**,
- their saved sign-in, and their personal settings (text size, language, working location).

Before it asks you to confirm, the app counts what would go and tells you — including how many of those scans have **never reached the central system**. Those exist on that device and nowhere else; once they are gone there is nothing to recover them from. If you see that number and it is not zero, the safe order is: have that person sign in and upload first, then remove them.

You cannot remove yourself while you are signed in — there is no bin on your own row. Removing someone does not touch anybody else's work, and does not touch the device's own setup (server addresses, locations).

Someone removed this way is not banned: they can sign in again on that device whenever they like, and they will start from an empty slate. That first sign-in back needs the central system to be reachable, the same as any first sign-in on a device.

## 18b. Sign-in QR codes (for administrators)

Operators can sign in by scanning a QR code instead of typing ([§3](#3-first-time-setup)). The codes are made on a computer with the page `tools/login_qr.html` from the app's repository. The page itself is in Croatian; its labels are quoted below.

- Open it by double-clicking, in Chrome or Edge. It works without internet, and nothing typed into it leaves the computer.
- Paste the **key** once: `loginQrKey` from the device configuration. It applies to both tabs. A code made with a different key does not work on the device.
- **"Jedan kod" (one code):**
  1. Enter the user name, the display name and the password. The display name is the caption under the code, and it follows the user name until you change it.
  2. Tap "Napravi QR kod".
  3. Tap "Ispiši" to print, or "Spremi PNG" to save the image.
- **"Više kodova" (many codes):**
  1. Tap "Preuzmi predložak" and fill the template in Excel: user name, password, display name.
  2. Load it with "Učitaj CSV", or paste the rows straight from Excel. A blank display name is made from the user name, e.g. `PRIMA\ana.kovacic` → Ana Kovacic.
  3. Tap "Napravi kodove". The table shows which rows became codes and why the others were skipped.
  4. "Ispiši" puts **two codes on each A4 page**, with a cut line between. "Spremi ZIP" saves every image in one file.
- **A printed code carries the password.** Keep it as you would the password itself, and delete the CSV after printing.

For the command line there is also `tools/make_login_qr.py`.

## 19. Glossary

| Term | Meaning |
|---|---|
| **RC (Responsibility Center)** | An organizational grouping (e.g. a region or business unit) that a location belongs to. |
| **Location / Source Code** | The specific warehouse or store you're working from. |
| **Destination Code** | Where a document's goods are headed. |
| **Downloaded** | A document pulled from the central system but not yet worked on. |
| **In Progress** | A document with some, but not all, scanning done. |
| **Completed** | Every line scanned exactly. |
| **Pending Upload** | Currently being sent to the central system. |
| **Upload Failed** | The send attempt didn't succeed — see the Errors tab for the reason. |
| **Inventory journal batch** | One stock count prepared in the central system for one location. You pick it when downloading an inventory. |
| **Not on the batch** | An item counted on an inventory that the batch did not list; shown at the top of the list. |
