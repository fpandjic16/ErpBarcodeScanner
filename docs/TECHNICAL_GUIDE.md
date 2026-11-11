# Prima Barcode — Technical & Consultant Guide

**Audience:** functional consultants configuring/supporting the app, and developers maintaining or extending it.

This document supersedes the older `docs/ARCHITECTURE.md` (now a stub pointing here). It was generated from a full, current source-tree survey and is organized in two parts:

- **Part A — Consultant / Functional Reference**: business rules, document lifecycle, NAV/Business Central integration setup, configuration reference, common support scenarios. No code-reading required.
- **Part B — Developer / Technical Reference**: architecture, database schema, repository/DAO/ViewModel APIs, networking internals, screen-by-screen implementation notes, extension points.

For the end-user manual (how warehouse staff use the app day to day), see `docs/USER_GUIDE.md`, and its Croatian translation `docs/USER_GUIDE_HR.md` — the two are kept in step. The NAV side of the integration (C/AL objects exported to `NAV/`) is §B.18.

---

# Part A — Consultant / Functional Reference

## A.1 What the App Does, End to End

Prima Barcode is a native Android app (repository and Gradle project `ErpBarcodeScanner`) that lets warehouse/store staff scan barcodes against documents (shipments, receipts, transfers) sourced from a Dynamics NAV 2018 / Business Central–family ERP system, and sync results back. There is no offline "queue and forget" — every download and upload is a direct, synchronous OData call to the ERP over the local network (or VPN), authenticated per-user with the user's own Windows credentials via NTLM.

The functional loop:

1. **Reference data sync** — pull Locations + derive Responsibility Centers from the ERP.
2. **Download** — pull document lines for one or more document types from a single shared ERP table ("Barcode App Entry"), filtered by document type code + date range + destination/source/RC.
3. **Scan** — record barcode scans locally against document lines. A scan that doesn't match any line's barcode is rejected outright with a "Barcode not found" error — it is never recorded. The one exception is a stock count (`INVENTORY`), where it is asked about and can be added (§A.3).
4. **Upload** — push each individual recorded scan as its own row to a second shared ERP table ("Barcode App Recordings"); on success the local document is fully removed; on failure it's flagged with the exact server error for retry.

## A.2 The Document Lifecycle (functional view)

Every document is always in exactly one of these states, computed automatically from its recordings (except where noted):

| State | Meaning | How it's reached |
|---|---|---|
| **Downloaded** | Fresh from the ERP, zero scans | Initial state after download/merge if no recordings exist for the doc |
| **In Progress** | Some scanning has happened, not yet complete | Automatically entered the moment any scan/edit occurs on a Downloaded or Upload-Failed document; also the state a Completed document regresses to if a later scan/edit breaks its "everything exact" condition |
| **Completed** | Every line is exactly at its expected quantity | Computed on download/merge only — a document is *never* pushed into Completed by an in-app scan action directly; scanning to exact match keeps it "recomputed as Completed" on the next merge, but functionally the app treats "every line exact" as effectively complete in the UI regardless |
| **Pending Upload** | Upload in progress (background-sync mode only) | Set immediately when a background upload starts, before the network call resolves |
| **Upload Failed: \<reason\>** | The last upload attempt failed | Set when any row's POST to the ERP fails; the reason is the literal server/config error text |

**Key business rule: recordings are never silently deleted.** A re-download can refresh a document's header/lines from the ERP, but it will never discard a user's recorded scans, even if the ERP no longer lists that document, or the document's lines have changed. `DocumentRepositoryImpl.mergeDocument()` always refreshes header/lines while re-summing docState from whatever recordings already exist locally (see §B.4.1).

## A.3 Unmatched Scans

A scanned barcode that doesn't match any line's `Barcode` field on the document is **not recorded**. The app shows a hard "Barcode not found" error (red flash + error haptic) and the user must re-scan the correct item or check the document. There is no offline/placeholder-document scanning and no automatic reconciliation step — every scan either lands on a real line at scan time or is rejected outright; there's nothing left over to resolve later.

**The exception is a stock count** (`DocumentType.isStockCount`, which is `INVENTORY`). There an item NAV did not send is a finding the count has to report: it is on the shelf and not in the system. A scan that matches no line asks "Not on the batch — count it?" with the warning tone, as every scan that needs a look does; it is asked rather than counted straight away because a stray scan of the wrong label looks the same. Confirmed, `DocumentRepository.recordScanNotOnDocument` creates a **line added on the device** (`Line.isAddedOnDevice`):

- Its `lineNo` is negative (-1, -2, …). NAV numbers its lines from 1 up, so the two cannot collide, and sorting by line number puts these at the top of the list, newest first.
- Its item number is the barcode, it has no name (the screen shows "Not on the batch"), `expected` is 0, and its unit of measure comes from a `BARCODE|UOM|QTY` label if the scan was one.
- Every later scan of that barcode lands on it without asking, and it never raises the over-scan warning: it expects nothing, so every scan is "over" by design.
- It is uploaded with `Document_Line_No = 0` (§A.5.4).
- It survives a re-download until NAV sends a line with the same barcode. Then all its scans, **sent ones included**, move onto NAV's line and the device line goes. Re-attachment of orphans alone would move only queued scans and leave NAV's line looking unscanned.
- It is removed once its last scan is (`dropEmptyLinesAddedOnDevice`), so a line zeroed by mistake does not stay behind red with nothing expected.

## A.4 The Four-State Line Status Language

| Status | Rule | Color | Where it's used |
|---|---|---|---|
| Empty | scanned == 0 | Red `#CE3A3A` | Line & document status everywhere |
| Partial | 0 < scanned < expected | Amber `#C7943A` | " |
| Exact ("Ready" in UI) | scanned == expected | Green `#2E8C5E` | " |
| Over ("Over-qty" in UI) | scanned > expected | Blue `#2D6CE0` | " |

**Document-level aggregate** (`Document.scanStatus()`): Empty if the document has zero lines or every line is Empty; **Over wins over everything** — a single Over line makes the whole document show Over regardless of other lines; Exact only if every line is Exact; otherwise Partial.

## A.5 Configuring the NAV / Business Central Connection

All of this lives under **Settings → External System Configuration** (`ExtSystemConfigScreen`), normally touched only during setup or by support.

### A.5.1 Fields to configure

| Field | Purpose | Example |
|---|---|---|
| Server base URL | Root used only for the "Test connection" NTLM probe | `http://192.168.100.87:8048/NAV_TEST_HR/ODataV4/` |
| Domain | Windows/NTLM domain used when the user types a bare username at login (added 2026-08). A domain typed into the username overrides this, and `.\user` ignores it entirely — see §B.6.2 for the full resolution order | `PRIMA` |
| Session duration | How long a signed-in session's credentials stay valid before requiring re-entry (8h / 24h / 48h / 7 days) | 168h |
| Document lines URL | The **"Barcode App Entry"** OData endpoint — shared by all 5 document types | `.../Company('Prima Commerce d.o.o.')/BarcodeAppEntries` |
| Per-document-type: enabled switch | Whether that document type is offered in the app at all | — |
| Per-document-type: Document Type Code | The exact `Document_Type` filter value the ERP expects for that type (read-only in this screen; comes from imported/loaded config, not typed here). Retail and warehouse deliberately share codes — see the `Retail_Location` note in §A.5.3 | `SHIPMENT` (warehouse *and* retail shipment), `RECEIPT` (warehouse *and* retail receipt), `TRANSPORT` |
| Per-document-type: Filter by | Whether that type's "download" and "My Location" filtering scope by **Location** (source code) or by **Responsibility Center** | `LOCATION` (default) |
| Locations URL | Reference-data endpoint for Locations (RCs are derived client-side from distinct location RC codes, not fetched from a separate RC endpoint) | `.../LocationList` |
| Inventory batches URL | **`BarcodeInventoryBatches`** (NAV page 50208): the inventory journal batches sent for scanning, one row each. Required for a stock count; there is no fallback (3.6.27) | `.../BarcodeInventoryBatches` |
| Upload | **Per scan** (one OData POST per recording to the recording sync URL) or **Per document** (one SOAP call per document to the document upload URL, all or nothing) — §B.6.5. The bundled files say Per scan until codeunit 50117 is in NAV | `PER_SCAN` |
| Recording sync URL | The **"Barcode App Recordings"** OData endpoint — one POST per recorded scan, used in Per scan mode | `.../BarcodeAppRecordings` |
| Document upload URL | SOAP address of codeunit 50117, published as **`BarcodeAppUpload`**, used in Per document mode. The bundled files guess the SOAP port as the OData port minus one (NAV's default pairing, 7047/7048); check it per server | `http://host:7047/NAVBARCODE/WS/Prima%20Mebel/Codeunit/BarcodeAppUpload` |

### A.5.2 Loading configuration

Three interchangeable ways to populate the above (available both in `ExtSystemConfigScreen`'s "Load configuration" button and Settings' "Insert system defaults" row — functionally identical, differing only in when they're persisted, see §B.11.6):

1. **Load built-in defaults** — asks **which system** (Test / Production) and *then* presents a company picker scoped to that environment, built from the bundled `assets/ext_system_defaults_*.json` files (see §B.6.6). Environment first because that is the question that carries the risk; company second. A file missing any address the app cannot work without — `serverBaseUrl`, `documentLinesUrl` or `recordingSyncUrl` — is listed but not selectable, since loading it would replace a working configuration with blanks. (`locationsUrl` is not required: blank is a legitimate setting meaning locations are not managed.)
2. **Export this device's configuration** — writes what is on the device *now* to a file the user picks, **with `loginQrKey` stripped out** — see §B.6.6. No company picker: it is this device, not a bundled asset. That is what makes the round trip real — set one device up by hand, export, import on the rest. Exporting the shipped asset instead meant a hand-tuned device could never become the template for its fleet.
3. **Import from file** — reads an arbitrary JSON file the user picks (same shape) and fills the form from it.

None of these three actions saves anything by themselves — the screen's own **Save** (or Settings' exit-save flow) is still required afterward.

**Saving without an environment asks first** (3.6.27). If the form has a server URL but `environment` is null, `requestSave()` opens "No environment chosen": **Choose** (Test / Production, then save) or **Save anyway**. A null environment stays legitimate; it just can no longer happen unnoticed.

### A.5.3 The "Barcode App Entry" table (download)

One flat OData row per document **line** (header fields repeated on every row of the same document). All 5 document types share this single table/endpoint, discriminated purely by an OData filter on `Document_Type`.

| NAV field | Meaning |
|---|---|
| `Document_Type` | Which of the 5 document types this row belongs to (filtered by, not stored per-row after grouping) |
| `Document_No` | Document number — the grouping key |
| `Line_No` | Line number (rows with `Line_No <= 0` are treated as the header row and excluded from lines) |
| `Source_No` | Source location code |
| `Retail_Location` | Boolean — whether the source is a retail location. **Also a mandatory filter** (2026-09): retail and warehouse types share a `Document_Type` code, so this is what actually separates them. Warehouse shipment/receipt filter `Retail_Location eq false`, retail shipment/receipt filter `eq true`; transport sheets don't filter on it at all. Driven by `DocumentType.retailLocation` (`Models.kt`), not by anything user-configurable |
| `Destination_No` | Destination code |
| `Document_Date` | Document date |
| `Responsibility_Center` | RC code |
| `Item_No` | Item number |
| `Item_Description` | Item name |
| `Item_Qty` | Expected quantity |
| `Scanning_Qty` | Quantity added per single scan of this line's barcode (defaults to 1.0) |
| `Item_UoM` | Expected unit of measure |
| `Barcode` | The barcode string to match scans against |

### A.5.4 The "Barcode App Recordings" table (upload)

One flat OData row **per individual scanned recording** — not one row per document, not batched. `Document_Line_No` is always a real line number the app knows about — the app no longer has any concept of an unmatched/"extra" recording (see §A.3), so it never sends `0` here — except for a line added on the device during a stock count, which goes out as `0` (§A.3). NAV reads this field for no type: it matches every scan to an item by `Barcode` (§B.18), so the `0` only tells a reader of 50180 that the line was not on the document.

| NAV field | Meaning |
|---|---|
| `Document_Type` | Document type code |
| `Retail_Location` | Boolean, added 2026-09 — the same discriminator the download filtered on, since retail and warehouse share a `Document_Type` code. Sourced from `Document.isSourceRetail`, i.e. the value NAV itself reported for that document on download. Omitted entirely for transport sheets, which participate in neither bucket (Gson drops the null) rather than sending a `false` that would claim they're warehouse documents |
| `Document_No` | Document number |
| `Document_Line_No` | Which line this recording applies to |
| `Recording_Line_No` | Sequence number within that line's recordings |
| `Recording_Guid` | The recording's stable identity, generated once when the scan is stored and **the same on every upload attempt** (2026-09; a fresh GUID per attempt before that). **It is the table's primary key** (table 50180, `Recording Guid`, clustered — see §B.18), so a repeat is refused *only while the first copy is still in 50180*. NAV moves rows out of 50180 into Scanned Document Lines (50111) and deletes them when the document's scans are processed (`CreateScanningEntriesOnDocument` → `FindScannedEntries`). After that, the same GUID is accepted into 50180 again, but it does **not** count twice: `InsertScannedEntries` skips any row whose GUID is already in 50111 (its `uuid` field) and deletes it from 50180. The exception is a document whose scans have already been archived (moved to 50112) or deleted by hand. The one visible consequence is this: a row NAV committed whose response was lost, resent before processing, comes back as an error ("already exists") although NAV has it, so the device shows that row as refused. It is resolved by hand, either by sending again once NAV has processed the document or by discarding the row on the device. Support can trace one scan by its GUID either way |
| `Barcode` | The scanned barcode string. `Text100` in NAV since 2026-11 (it was `Text30`, and a longer `BARCODE\|UOM\|QTY` label was refused) |
| `Scanned_Quantity` | Quantity for this recording |
| `Unit_Of_Measure_Code` | UoM for this recording |
| `Source_Creation_DateTime` | When the scan happened (ISO-8601) |
| `Source_User_ID` | Who scanned it (blank if nobody was signed in at scan time) |
| `Source_Code` | Source location code |
| `Destination_Code` | Destination code |

**Two upload modes** (3.6.27, `ExtSystemConfig.uploadMode`). What follows describes **Per scan**. In **Per document** the same rows go in one SOAP call to codeunit 50117 instead (§B.6.5): NAV inserts them in one transaction, so a document is in 50180 whole or not at all, and a row whose GUID 50180 already holds is skipped rather than refused, which closes the "already exists" window described for `Recording_Guid` above.

**Upload semantics to know for support** (reworked 2026-09): rows are sent **sequentially, one POST per row**. A row the ERP accepts is **marked sent** (`recordings.sentAt`), not deleted — it stays on the device, still counting towards the document's scanned quantities, until the whole document is removed. A document is removed once nothing is left queued.

A refused row is recorded with its reason (`recordings.lastError`) and **the send continues with the next row**. Only a failure with no HTTP response at all — `ExtSystemResult.Failure.code` left at its `-1` default, meaning the server or network is gone — aborts the rest of the document; continuing there would spend a connect timeout per remaining row to learn the same thing.

Two consequences worth holding on to:

- A partly-sent document shows **all** of its scanned quantities, not just the unsent remainder. Before this, quantities fell as the upload progressed and a partial failure looked like lost work — which invites re-scanning, and since the ERP refuses nothing, the re-scan is silently recorded as surplus.
- A row refused for its own content (a field too long, a wrong type) fails identically on every retry. It no longer blocks the rows behind it, but it does keep the document open, so `UploadErrorScreen` lists refused rows with their reasons and offers an explicit discard.

## A.6 Business Settings Reference (functional)

These map 1:1 to the User Guide's Settings section but framed for support/consultants:

| Setting | Functional effect | Default |
|---|---|---|
| `warnOnOver` | Whether an over-scan pops a confirmation | On |
| `backgroundSync` | Uploads fire-and-forget in the background vs. block with a progress screen | Off |
| `debuggerActive` | Shows exact outbound URLs before every network call — diagnostic aid for support, not for daily use | Off |
| Per-document-type **Filter by** (Location vs Responsibility Center) | Governs both download-filter defaults and "My Location" dashboard matching for that type | Location |

## A.7 Common Support Scenarios

| Symptom | Likely cause | Where to look |
|---|---|---|
| Document type shows locked on Main Menu | No location selected, or the type has no valid endpoint/code configured | `ExtSystemConfigScreen` — confirm the type is enabled and has a Document Type Code |
| Upload fails with an ERP validation message | Business-system-side issue (e.g. a malformed field, a locked document) | `UploadErrorScreen`'s full error text — read verbatim, it's the ERP's own response body (truncated to 300 characters) |
| Upload fails with "Not signed in" / "not configured" | Session expired, or a URL/field is blank in External System Configuration | Re-authenticate; verify config fields |
| "NTLM not enabled" / "credentials rejected" on Test Connection | Distinguishes ERP-side NTLM misconfiguration (phase 0 — no challenge issued at all) from genuinely wrong credentials (phase 2 + 401) | `NtlmAuthenticator.phaseReached`, surfaced in the Test Connection result dialog's message |
| A document with real progress "disappeared" from Orders | Nothing but a successful upload removes it. Downloads never delete a document holding recordings | Check the RECORDINGS section on the main menu, and the Errors tab |
| An operator cannot see work they left yesterday | They are signed in as a different profile, or typed a name that normalises differently | The RECORDINGS section is per operator; check the name on the sign-in screen |
| Every document type answers "choose a location first", e.g. after an update or for a new operator | The device has no locations yet (`SharedDatabase` starts empty), or the operator has nothing selected (`lastLocationCode` / `lastRcCode` are personal and start blank), or the selected code is no longer on the list. A location-scoped type needs a location, a centre-scoped one (Complaint) a responsibility centre | Refresh on `LocationRcPickScreen`, then pick a location (its centre comes with it). The context strip on the main menu shows `—` while nothing is selected |
| "Could not sign in" and the server is down | Expected. The ERP checks the password on every sign-in and there is no local fallback | Restore the connection. Nothing on the device is lost meanwhile, but nobody can open the app |
| Sign-in succeeds against a URL that is not really NAV | It cannot any more: a 2xx with `NtlmAuthenticator.phaseReached == 0` is refused, because OkHttp only invokes the authenticator on a 401 and an endpoint that never challenges never checks the password | Enable Windows Authentication on the OData endpoint |
| Barcode with `|` scans as garbage | Printed with Code 39 symbology, which cannot encode `|` | Reprint the label as Code 128 |
| A scan is rejected as "Barcode not found" even though the item is on the document | The scanned value doesn't byte-for-byte match that line's `Barcode` field (wrong symbology, stray whitespace, wrong `\|UOM\|QTY` suffix) | Compare the raw scanned value against the line's `Barcode` field; reprint the label if needed |

### New application id (3.6.48)

The app's package and application id changed from `com.prima.barcode` to `com.erpbarcode.scanner`, and the code no longer names Prima or NAV (the display name stays *Prima Barcode*). To Android this is a new app, so on every device:

1. Send everything still unsent from the old app.
2. Install the new app (it can sit beside the old one meanwhile), load the configuration, sign in.
3. Uninstall the old app.

Nothing carries over: databases (`user_<hash>.db`, `shared.db`), credentials and settings start empty under the new id. The DataWedge profile is now *Prima Barcode*, created by the app on first start; the old *PrimaBarcode* profile goes with the old app. The state before is tagged `pre-rename` in git.

### The device is shared, the data is not (3.0.0)

Each operator gets their own database file. Documents, lines and recordings are scoped by which file a query runs in — not by a predicate — so two operators on one device cannot see or send each other's work, and both may hold the same NAV document independently, each with their own scans. The ERP refuses nothing, so those two copies add up there, which is the point when they are splitting one job between them.

Sign-in is required at every app launch and is a real sign-in, with a password. What that password is checked against depends on whether the ERP can be reached:

**Every sign-in goes to the ERP. There is no local fallback.** `testConnection` must both return 2xx *and* show `NtlmAuthenticator.phaseReached > 0`; a success enrols the profile (name only — no secret is stored) and opens its database. Anything else fails, including an unreachable server and a blank `serverBaseUrl`, which reports `R.string.signin_needs_server` and is answered by the sign-in screen's own door to the external-system setup.

An earlier version unlocked offline against a stored PBKDF2 digest, so an enrolled operator could get in while the server was down and keep scanning documents already on the device. It was removed on the rule that signing in without the external system is not a sign-in. The cost is real and was accepted knowingly: a device that cannot reach the server is a device nobody can open, including an operator whose unsent scans are sitting on it. Those rows stay safe on disk — nothing deletes them — but they are out of reach until the server answers.

Identity still must not come from the credentials: `ExtSystemCredentialStore.get()` wipes those the moment their TTL lapses, and scoping data to an identity that self-destructs on a timer would hide an operator's own unsent scans from them. So enrolment in `UserProfileStore` has no expiry even though it is no longer a secret — what it reaches does not expire either, and an operator must be able to find their own work after their credential has lapsed.

Identity is `UserProfileStore.normalise()` — the bare username, upper case, domain stripped, at most 50 characters. `alice`, `PRIMA\alice` and `alice@prima.hr` are one operator. Before this, `User.id` was the raw typed string, so one person could have had three identities and, under per-profile storage, three separate databases. The database filename is a SHA-256 prefix of that key, never the name itself.

`RecordingEntity.userId` is stamped at the moment a scan is written and travels to NAV as `Source_User_ID`. It can no longer be empty: nothing is reachable before sign-in, which is why the gate exists at all — a scan's author cannot be filled in afterwards.

Locations and responsibility centres stay device-wide in `SharedDatabase`, along with `ExtSystemConfig`, the disabled document types, the per-type filter modes and the debugger flag. Everything else in `AppSettings` is per profile.

---

# Part B — Developer / Technical Reference

## B.1 Stack & Source Layout

| Layer | Technology |
|---|---|
| UI | Jetpack Compose + Material 3 |
| Navigation | Compose Navigation |
| Networking | Ktor Client (OkHttp engine) + a hand-rolled NTLMv2 `okhttp3.Authenticator` — Ktor's own auth plugin and ContentNegotiation are **not** used; JSON is hand-built/parsed with Gson |
| Local DB | Room (KSP-generated DAOs) |
| DI | Hilt/Dagger |
| Barcode | ML Kit (camera path) + Zebra DataWedge (hardware wedge, Intent-based) |
| Camera | CameraX |
| Secure storage | `androidx.security:security-crypto` (`EncryptedSharedPreferences`, AES-256-GCM, Android Keystore-backed) for NAV credentials only |
| Logging | Timber |

```
app/src/main/java/com/erpbarcode/scanner/
├── MainActivity.kt               # NavHost, app shell, all settings state hoisting
├── data/
│   ├── auth/                     # AppSettings(Store), ExtSystemConfig(Store), credential store
│   ├── barcode/                  # BarcodeAnalyzer (ML Kit), DataWedgeManager (Zebra wedge)
│   ├── db/                       # Room entities, DAOs, AppDatabase, Mappers
│   ├── export/                   # DatabaseExporter (raw DB → JSON dump)
│   ├── extsystem/                # ExtSystemODataClient, NtlmAuthenticator, DTOs
│   ├── model/                    # Domain models (Models.kt, Filter.kt, Status.kt)
│   └── repository/               # DocumentRepository (the only repository in the app)
├── di/DatabaseModule.kt          # the only Hilt module
└── ui/
    ├── theme/                    # Color.kt, Type.kt, Shape.kt, Theme.kt, Language.kt
    ├── component/                # Reusable composables
    ├── screen/                   # Full screens
    └── viewmodel/                # AppViewModel, RecordingViewModel
```

There is deliberately **no separate "SyncRepository"** — NAV networking (download/upload/test-connection) is orchestrated directly in `AppViewModel`, which calls `ExtSystemODataClient` and `DocumentRepository`/DAOs itself.

## B.2 Database Schema (Room)

The schema version is `DB_VERSION` in `AppDatabase.kt` (20 as of 2026-11), referenced by the `@Database` annotation and shown in the app info; change it there and nowhere else. `SharedDatabase` (locations and responsibility centres) has its own version, 1.

Two databases, and which one a table lives in is the whole of the per-user isolation.

**`AppDatabase`** — one file **per operator**, `user_<sha256-prefix>.db`, **schema version 21**. Holds `documentHeader`, `documentLine` and `recordings`. Opened by `DatabaseProvider`, not by Dagger: a `@Singleton AppDatabase` would have pinned the first operator's file for the life of the process. Built with `.addMigrations(MIGRATION_7_8 … MIGRATION_20_21)`. `MIGRATION_19_20` changes no schema: it deletes every row of the retired `COMPLAINT` type (§B.17) and **no `fallbackToDestructiveMigration`** — see `DatabaseModule` for why that must never come back.

**`SharedDatabase`** — one file per ERP installation since 3.6.53, `shared_<sha256 prefix>.db`, opened by `SharedDatabaseProvider` (it was one `shared.db` for the device, provided by Dagger), version 1. Holds `locations` and `responsibility_centers`. They describe the site, not the person; per-profile copies would have forced every new operator to re-download them before they could pick a location, which needs a server that may not be reachable.

**The pre-split file is gone.** Everything once lived in one database file. Per-operator files replaced it, and until 3.6.48 a startup cleanup deleted the old file; the new application id (§A.7) starts with an empty data directory, so that cleanup was removed with it.

**Upgrading a device therefore starts with no locations and no responsibility centres.** `MIGRATION_18_19` drops those two tables from `AppDatabase` and `SharedDatabase` is created empty; `upsertLocations` has exactly one caller, `realDownloadLocations`, and nothing calls it automatically. The operator gets them back through **Refresh** on `LocationRcPickScreen`, which is deliberate — nothing downloads behind their back.

That empty state used to make the document filter look broken, because the two modes failed in opposite directions: with nothing selected, a missing centre passed **everything** while a missing location passed **nothing**. Both modes now require their selection (see `filteredDocs` in `MainActivity`):

```kotlin
DocTypeFilterMode.LOCATION              -> location != null && doc.sourceCode == location.code
DocTypeFilterMode.RESPONSIBILITY_CENTER -> rc != null && doc.rcCode == rc.code
```

and a type whose selection is missing cannot be opened at all (§B.11.4), so nothing selected now reads the same for every type: the main menu says to pick a location or centre.

The *selected* location is a separate thing from the *list*: `lastLocationCode` / `lastRcCode` are personal settings and moved to `app_settings_<profile id>` with the split, so they start blank for every operator. Nothing repairs a blank pair on its own — `LaunchedEffect(rcs, rcCode)` only acts when `rcCode` is non-blank, and the location effect only when `rc != null`. Each operator picks once, on `LocationRcPickScreen`.

`DatabaseProvider` keeps opened databases open rather than closing on switch. A background upload outlives the screen that started it, so a switch can land while rows are still being written; closing underneath that throws and loses the write, and those rows are the only copy of a shift's work until the ERP has them. **Never hold a DAO or a database in a field** — `DocumentRepositoryImpl` resolves `provider.current()` at each use, binds it once per transaction with a local `val db`, and passes that into its private helpers so no transaction can straddle two files.

### `documentHeader` (`DocumentHeaderEntity`)
PK: `(documentNo, type)`.

| Column | Type | Notes |
|---|---|---|
| documentNo | TEXT NOT NULL | |
| type | TEXT NOT NULL | serialized `DocumentType.key` |
| destinationCode | TEXT NOT NULL | |
| sourceCode | TEXT NOT NULL | |
| rcCode | TEXT NOT NULL | |
| isSourceRetail | INTEGER NOT NULL | boolean, added v13→v14, default 0 |
| creationDateTime | INTEGER NOT NULL | epoch millis |
| documentDate | INTEGER NULL | epoch millis |
| docState | TEXT NOT NULL | serialized `DocState` |

### `documentLine` (`DocumentLineEntity`)
PK: `(documentNo, type, lineNo)`. FK `(documentNo, type) → documentHeader`, `ON DELETE CASCADE`. Indices: `(documentNo, type)`, `(barcodeNo)`.

| Column | Type | Notes |
|---|---|---|
| documentNo, type, lineNo | — | PK |
| itemNo, itemName | TEXT NOT NULL | |
| barcodeNo | TEXT NOT NULL | |
| expected | REAL NOT NULL | |
| destinationCode, sourceCode, unitOfMeasureCode | TEXT NOT NULL | |
| scanningQty | REAL NOT NULL DEFAULT 1.0 | added v10→v11 |
| addedOnDevice | INTEGER NOT NULL DEFAULT 0 | added v20→v21: a stock count's line created on the device (`Line.isAddedOnDevice`). Such lines still get a negative `lineNo`, but are recognised by this field, not by the sign |

### `recordings` (`RecordingEntity`)
PK: `(documentNo, type, documentLine, recordingLineNo)`. FK `(documentNo, type) → documentHeader`, `ON DELETE CASCADE`. Indices: `(documentNo, type)`, `(documentLine)`. `documentLine` always references a real `DocumentLineEntity.lineNo` — every recording is tied to a line.

| Column | Type | Notes |
|---|---|---|
| documentNo, type, documentLine, recordingLineNo | — | PK |
| barcodeNo | TEXT NOT NULL | |
| quantity | REAL NOT NULL | |
| creationDateTime | TEXT NOT NULL | ISO-8601 string (changed from epoch-millis Long in v11→v12) |
| userId | TEXT NOT NULL | |
| destinationCode, sourceCode, unitOfMeasureCode | TEXT NOT NULL | |
| rcCode | TEXT NOT NULL DEFAULT '' | added v14→v15, backfilled from parent header |

Note: a nullable `format` TEXT column existed between v12 and v16 (barcode symbology, never actually populated by any code path) and was **dropped in v15→v16** along with the corresponding `DocumentRepository.getUploadableDocs`/upload-DTO field, after confirming zero write sites during a 2026-08 codebase audit.

### `locations` (`LocationEntity`)
PK `code`. Columns: `name`, `rcCode` — all `TEXT NOT NULL`.

### `responsibility_centers` (`ResponsibilityCenterEntity`)
PK `code`. Columns: `name TEXT NOT NULL`, `short TEXT NULL`.

### `DocumentHeaderWithLines`
Not a Room entity — a plain data class `{ document: DocumentHeaderEntity, lines: List<DocumentLineEntity>, recordings: List<RecordingEntity> }` manually assembled by the repository (avoids N+1 queries; see `assembleDocuments`).

### Migrations — full list and rationale

| Migration | Change | Why the "recreate" pattern (if used) |
|---|---|---|
| `MIGRATION_10_11` | `ALTER TABLE documentLine ADD COLUMN scanningQty REAL NOT NULL DEFAULT 1.0` | Plain additive column — no rebuild needed |
| `MIGRATION_11_12` | Rebuilds `recordings`: converts `creationDateTime` INTEGER (epoch millis) → TEXT (ISO-8601, via SQLite `strftime`); adds `format` column | SQLite `ALTER TABLE` cannot change a column's type — requires create-new/copy/drop/rename + recreate indices |
| `MIGRATION_12_13` | Rebuilds `documentHeader`, dropping an obsolete pre-v13 column | SQLite `ALTER TABLE` (at the time) could not `DROP COLUMN` — same recreate pattern |
| `MIGRATION_13_14` | `ALTER TABLE documentHeader ADD COLUMN isSourceRetail INTEGER NOT NULL DEFAULT 0` | Additive — no rebuild |
| `MIGRATION_14_15` | `ALTER TABLE recordings ADD COLUMN rcCode TEXT NOT NULL DEFAULT ''`, then `UPDATE recordings SET rcCode = (correlated subquery against documentHeader.rcCode)` | Additive column + backfill — no rebuild |
| `MIGRATION_15_16` | Rebuilds `recordings`, dropping the now-unused `format` column | Column drop — recreate pattern again |
| `MIGRATION_20_21` | `ALTER TABLE documentLine ADD COLUMN addedOnDevice INTEGER NOT NULL DEFAULT 0`, then `UPDATE documentLine SET addedOnDevice = 1 WHERE lineNo < 0` | Additive column + backfill; every negative line number was a device-added line. `21.json` is generated by the build |

**General rule**: SQLite's native `ALTER TABLE` only supports `ADD COLUMN`, `RENAME COLUMN`, `RENAME TABLE`. Any migration that needs to change a column's type or drop a column must: `CREATE TABLE x_new (...)` with the desired final shape → `INSERT INTO x_new SELECT ... FROM x` (explicit column list, applying any needed transform) → `DROP TABLE x` → `ALTER TABLE x_new RENAME TO x` → manually recreate indices (they don't survive `DROP TABLE`).

## B.3 DAOs (full function reference)

### `DocumentHeaderDao`
- `observeHeader(documentNo, type): Flow<DocumentHeaderEntity?>`
- `observeAllHeaders(): Flow<List<DocumentHeaderEntity>>`
- `getByKey(documentNo, type): DocumentHeaderEntity?` (suspend)
- `getAll(): List<DocumentHeaderEntity>` (suspend)
- `upsert(doc)` (suspend, `@Upsert`)
- `updateState(documentNo, type, state: String)` (suspend)
- `deleteByKey(documentNo, type)` (suspend) — cascades to lines/recordings
- `deleteAll()` (suspend)

### `DocumentLineDao`
- `getByKey(documentNo, type, lineNo): DocumentLineEntity?` (suspend)
- `getByDoc(documentNo, type): List<DocumentLineEntity>` (suspend)
- `observeByDoc(documentNo, type): Flow<List<DocumentLineEntity>>`
- `observeAll(): Flow<List<DocumentLineEntity>>`
- `getAll(): List<DocumentLineEntity>` (suspend)
- `upsertAll(lines)` (suspend, `@Upsert`)
- `deleteAllForDoc(documentNo, type)` (suspend)
- `deleteByKey(documentNo, type, lineNo)` (suspend) — removes one line; used for a line added on the device once its last scan is gone

### `RecordingDao`
- `observeByDoc(documentNo, type): Flow<List<RecordingEntity>>` — `ORDER BY documentLine, recordingLineNo`
- `observeAll(): Flow<List<RecordingEntity>>`
- `getNextRecordingLineNo(documentNo, type, documentLine): Int` (suspend) — `COALESCE(MAX(recordingLineNo),0)+1`, scoped per `(documentNo, type, documentLine)` triple (each line has its own independent sequence)
- `getByDoc(documentNo, type): List<RecordingEntity>` (suspend)
- `insert(recording)` (suspend, `@Insert` — plain insert, fails on PK conflict by design; recordings are append-only)
- `deleteByPk(documentNo, type, documentLine, recordingLineNo)` (suspend)
- `deleteAllForLine(documentNo, type, lineNo)` (suspend)
- `deleteAllForDoc(documentNo, type)` (suspend)
- `getAll(): List<RecordingEntity>` (suspend) — used by `DatabaseExporter`

### `LocationDao`
- `observeLocations(): Flow<List<LocationEntity>>` — `ORDER BY name ASC`
- `observeRcs(): Flow<List<ResponsibilityCenterEntity>>` — `ORDER BY name ASC`
- `upsertLocations(locations)` / `upsertRcs(rcs)` (suspend, `@Upsert`)
- `clearLocations()` / `clearRcs()` (suspend)

## B.4 `DocumentRepository` — Full Function Reference

`@Singleton class DocumentRepositoryImpl @Inject constructor(private val db: AppDatabase)` — talks directly to `db.documentHeaderDao()` / `db.documentLineDao()` / `db.recordingDao()`; no field-level DAO injection.

| Function | Behavior |
|---|---|
| `observeAll(): Flow<List<Document>>` | Combines all headers + lines + recordings, assembles domain `Document`s via `assembleDocuments` (in-memory groupBy, avoids N+1 queries) |
| `observeDocument(documentNo, type): Flow<Document?>` | Same, scoped to one document |
| `replaceDownloadedDocuments(type, docs, pruneMissing = true)` (suspend, transactional) | Deletes headers of `type` not present in `docs` **only if they have zero recordings**, and only when `pruneMissing` (false for a download of one picked inventory batch, which says nothing about the others); calls `mergeDocument` for every doc in `docs` |
| `mergeDocument(doc, type)` (private, suspend) | Refreshes header + fully replaces lines from the download, recomputes docState from whatever recordings already exist locally — see §B.4.1 for the exact code path |
| `computeStateAfterMerge(lines, recordings)` (private) | `Downloaded` if no recordings; else `Completed` if every line's summed recordings exactly equal `expected`; else `InProgress`. Never returns `PendingUpload`/`UploadFailed` — those are only set via `updateDocState` from outside, and any subsequent merge will silently overwrite them |
| `recordScan(documentNo, type, lineNo, barcodeNo, userId, quantity)` (suspend, transactional) | Looks up line+header, generates next `recordingLineNo`, **inserts** a new recording (always additive, never updates existing rows), calls `advanceToInProgressIfNeeded` + `regressFromCompletedIfNeeded` |
| `recordScanNotOnDocument(documentNo, type, barcodeNo, unitOfMeasureCode, userId, quantity)` (suspend, transactional) | Stock count only (§A.3). Finds the device-added line for the barcode or creates one (`lineNo` = lowest existing, capped at 0, minus 1), then calls `recordScan` in the same transaction, so a second scan arriving before the screen has seen the line still lands on it |
| `setLineScanned(documentNo, type, lineNo, scanned, userId): SetQuantityResult` (suspend, transactional) | Moves only the **queued** part of a line, and as a delta. Refuses with `BelowSent(sent)` if `scanned` is under what the ERP already holds. Otherwise: up appends one row like a scan would; down consumes queued rows newest-first and stops, trimming the last one it only partly needs. Runs all three state helpers (including `regressToDownloadedIfNeeded`, since zeroing a line could empty the doc entirely) |
| `updateDocState(documentNo, type, state)` (suspend) | Direct state write — used by the upload flow for `PendingUpload`/`UploadFailed` |
| `deleteDocument(documentNo, type)` (suspend) | Deletes header, cascades lines+recordings — used on upload success |
| `getQueuedRecordings(documentNo, type)` / `getOrphanedRecordings(...)` (suspend) | What the upload flow enumerates: line-attached rows still `sentAt IS NULL`, and the orphans that block the document before a single row goes out |
| `markRecordingSent(documentNo, type, documentLine, recordingLineNo)` (suspend) | Stamps `sentAt` the instant a row's POST succeeds. Replaced a delete — rows are kept until the whole document goes, so a partly-sent document still shows everything the operator scanned |
| `deleteQueuedRecording(documentNo, type, documentLine, recordingLineNo)` (suspend, transactional) | Single-row delete from the recordings tree, `sentAt IS NULL` only; recomputes state |
| `clearAll()` (suspend) | Deletes all headers, cascades everything. "All" means all in *this* file, so it is the signed-in operator's wipe and nobody else's — behind both "Clear cache" and "Delete all documents" |
| `deleteDocumentRecordings(documentNo, type)` (suspend, transactional) | Deletes a document's **queued** recordings and recomputes state from what remains — the 5s-hold action on the recordings tree |

**"Never delete recordings" — where deletions actually happen and why each is safe:**
- `setLineScanned`, `deleteQueuedRecording`, `deleteDocumentRecordings`, `discardOrphanedScans`, `discardFailedScans` — explicit, user-initiated corrections, and every one of them is scoped to `sentAt IS NULL`. Nothing the ERP has accepted is ever deleted except with its whole document.
- `deleteDocument`, `clearAll` — explicit whole-document/whole-DB deletion, never invoked from background sync/merge paths.

**The sent quantity is a floor (2026-09).** `Line.sentQuantity` carries the part of `Line.scanned` the ERP has already accepted, summed in `Mappers.toDomain` from the recordings already in hand. A manual edit may raise a line freely but may not go below that number, and the refusal is explicit — `SetQuantityResult.BelowSent(sent)` back to `RecordingViewModel`, which hands it to `RecordingScreen`'s dialog.

Why a refusal rather than a clamp: this ERP's recordings table takes any row with a new GUID and offers no delete or correction, so there is no way to unsend a posted quantity from the device. A silent clamp would write a number the operator did not type and let them walk away believing the correction went through.

Enforced in three places, deliberately: the stepper stops at the floor, the keypad checks the typed value, and the repository is the authority — the UI cannot see a floor a background upload raised while the screen was open.

**A manual edit is a delta, not a rewrite (2026-09).** Above the floor, `setLineScanned` appends one row when the number goes up, and when it goes down consumes the line's queued rows **newest-first** via `getQueuedForLineNewestFirst`, deleting whole rows and `updateQueuedQuantity`-trimming the last one it only partly needs. A trimmed row keeps its GUID, `userId` and timestamp: it was never sent, so the ERP has never seen that GUID, and it is still the same person's scan with part of it taken back. Comparisons run through `QTY_EPSILON` (1e-9) so a reduction landing exactly on the current total doesn't leave a ~1e-16 row behind.

Both of those replaced one destructive overwrite. `setLineScanned` used to delete **every** recording on the line — sent ones included — and re-queue the whole new total. On a partly-sent line the device forgot what the ERP held and sent the accepted quantity a second time, recorded there as surplus. On *any* line it collapsed the per-scan history into a single row stamped with the editing operator and the current time — invisible until the recordings tree started showing individual scans, and on a shared device a silent re-attribution of the previous operator's unsent work.
- `recordScan` — never deletes anything, purely additive.
- `replaceDownloadedDocuments` explicitly checks `hasRecordings` before deleting a header no longer present in a fresh download; `mergeDocument` itself never touches the `recordings` table at all, only `documentHeader`/`documentLine`.

### B.4.1 `mergeDocument` — exact algorithm

```kotlin
private suspend fun mergeDocument(doc: Document, type: String) {
    val recordings = db.recordingDao().getByDoc(doc.documentNo, type)
    val state = computeStateAfterMerge(doc.lines, recordings)
    db.documentHeaderDao().upsert(doc.toEntity().copy(docState = state.toDbString()))
    db.documentLineDao().deleteAllForDoc(doc.documentNo, type)
    db.documentLineDao().upsertAll(doc.lines.map { it.toEntity(type) })
}
```

That's the whole function — it never touches the `recordings` table. Recordings are keyed by `(documentNo, type, documentLine, recordingLineNo)`, and `documentLine` is a line number that's stable across re-downloads of the same document, so existing recordings simply keep applying to whichever line has that number after the lines table is replaced. `computeStateAfterMerge` re-sums those existing recordings against the *freshly downloaded* `expected` quantities to decide `Downloaded`/`Completed`/`InProgress` — this is what makes a re-download safe even if the ERP changed a line's expected quantity: the doc's state is always recomputed, never carried over.

## B.5 Domain Models

### `DocumentType` (`Models.kt`)
| Enum | `key` | `display` |
|---|---|---|
| WAREHOUSE_SHIPMENT | `WHSE_SHIP` | "Warehouse Shipment" |
| WAREHOUSE_RECEIPT | `WHSE_RCPT` | "Warehouse Receipt" |
| RETAIL_SHIPMENT | `RT_SHIP` | "Retail Shipment" |
| RETAIL_RECEIPT | `RT_RCPT` | "Retail Whse. Receipt" |
| TRANSPORT_SHEET | `TRANSPORT` | "Transport Sheet" |
| COMPLAINT_SHIPMENT | `CPL_SHIP` | "Complaint Shipment" |
| COMPLAINT_RECEIPT | `CPL_RCPT` | "Complaint Receipt" |
| INVENTORY | `INVENTORY` | "Inventory" |

`isStockCount` is true for `INVENTORY` only. It makes the download pick one inventory journal batch first (§B.6) and lets a scan of an item the document does not list become a line added on the device (§A.3).

`String.toDocumentType()` defaults to `WAREHOUSE_SHIPMENT` on an unrecognized key — which is why a retired type's rows are deleted by a migration rather than left behind (the `COMPLAINT` key, §B.17). A `key` is never reused for a different type.

### `DocState` (sealed interface, `Models.kt`)
```kotlin
sealed interface DocState {
    data object Downloaded : DocState
    data object InProgress : DocState
    data object Completed : DocState
    data object PendingUpload : DocState
    data class UploadFailed(val reason: String) : DocState
}
```
Serialized (`Mappers.kt`) as the bare state name, except `UploadFailed(reason)` → `"UploadFailed:$reason"` (deserialized via `removePrefix`). Unrecognized strings deserialize to `Downloaded`.

### `LineStatus` (`Status.kt`)
```kotlin
enum class LineStatus { EMPTY, PARTIAL, EXACT, OVER }
fun of(scanned: Double, expected: Double) = when {
    scanned == 0.0      -> EMPTY
    scanned < expected  -> PARTIAL
    scanned == expected -> EXACT
    else                -> OVER
}
```
Plus `.color`/`.bgColor` UI extension properties (the English `.label` was removed in 2026-10; screens use their own `localizedLabel()`), and `Document.scanStatus()` (aggregate — see §A.4).

### `DocTypeFilterMode`
`{ LOCATION, RESPONSIBILITY_CENTER }` — governs whether a document type's download/dashboard filtering scopes by source location code or RC code. Stored per-type in `AppSettings.docTypeFilters`.

### `DocumentFilter` / `DownloadFilter` (`Filter.kt`)
```kotlin
data class DocumentFilter(
    val dateFrom: LocalDate? = null, val dateTo: LocalDate? = null,
    val states: Set<LineStatus> = emptySet(), val types: Set<DocumentType> = emptySet(),
    val destinationCode: String = "", val sourceCode: String = "", val rcCode: String = "",
) { val isActive: Boolean }

data class DownloadFilter(
    val dateFrom: LocalDate? = null, val dateTo: LocalDate? = null,
    val destinationCode: String = "", val sourceCode: String = "", val rcCode: String = "",
    val documentNo: String = "",   // one document only: the picked inventory batch
)
```

Other `Models.kt` types: `User`, `Location`, `ResponsibilityCenter`, `Item(no, name)`, `Line` (computed `status`, `hasSentQuantity`, and `isAddedOnDevice`, true for a negative `lineNo`), `InventoryBatch` (one entry of the inventory batch list: `documentNo`, journal template and batch, date, line count), `Document` (computed `linesExact`, `linesTotal`, `scannedQty`, `expectedQty`, `hasProgress`, `totalScans`, `partlySent`), `ScanRecord` + `LineScans` (the recordings tree's UI models — see `RecordingsTreeScreen`), `Double.formatQty()` extension.

### Supporting: `DatabaseExporter`
`data/export/DatabaseExporter.kt` — `suspend fun exportTo(uri: Uri)`, reads all headers/lines/recordings via DAOs, wraps as `{exportedAt, documentHeaders, documentLines, recordings}`, Gson-pretty-prints to the given `Uri`. Debug/support tool, invoked from `SettingsScreen`'s "Export data".

## B.6 Networking / NAV Integration — Implementation Detail

### B.6.1 `ExtSystemODataClient`
`data/extsystem/ExtSystemODataClient.kt` — the only HTTP wrapper in the app. `HttpClient(OkHttp) { engine { preconfigured = okHttp }; expectSuccess = false }` (so 4xx/5xx return normally instead of throwing). Underlying `OkHttpClient`: `.proxy(Proxy.NO_PROXY)` (NAV is always on local LAN), `.authenticator(NtlmAuthenticator(domain, username, password))`, connect/read/write timeouts 30s/60s/60s.

`configure(config, creds)` only rebuilds the client when `(username, password)` changed since the last call (cached via a `clientKey`); the old client is `.close()`d first. No fixed Ktor base URL — every call takes a full absolute URL. Requests set `Accept: application/json`; downloads additionally force `Accept: application/json;odata=nometadata`.

```kotlin
sealed class ExtSystemResult<out T> {
    data class Success<T>(val data: T) : ExtSystemResult<T>()
    data class Failure(val message: String, val code: Int = -1) : ExtSystemResult<Nothing>()
}
```

| Function | Behavior |
|---|---|
| `testConnection(baseUrl)` (suspend) | `GET baseUrl`; resets `ntlmAuth.resetPhase()` first; on failure inspects `phaseReached` to distinguish "NTLM not enabled" (phase 0) vs "handshake completed, credentials rejected" (phase 2 + 401) vs a generic HTTP error (body, truncated 300 chars) |
| `uploadRecording(url, row: ErpRecordingRow)` (suspend) | `POST url`, `Content-Type: application/json`, body = `gson.toJson(row)`. On 401: the localized `ext_error_http_401`. On any other non-2xx: `Failure("HTTP ${status}: $body".take(300), status)` — NAV's own text is the most precise thing there is. On thrown exception: `Failure(describe(e), code=-1)` |
| `downloadRaw(url)` (suspend) | `GET url`; follows OData `@odata.nextLink` pagination, merging every page's `value` array into one combined JSON array before returning |
| `close()` | Closes/clears the underlying client |
| `companion.parseDomainUser(raw)` | Splits `DOMAIN\user` or `user@domain`; falls back to `("", raw)` |

**Every `Failure.message` is operator-facing.** The sign-in error dialog, the download error dialog and the upload error screen show it verbatim, so the client builds its messages from string resources (`ext_error_*`) through `inAppLanguage()` — it takes the application context for that. They used to be English literals: a wrong password read "NTLM handshake completed (phase 3) but server rejected the credentials" on an otherwise Croatian screen. The NTLM phase still goes to the log. A thrown exception goes through `describe()`, which walks the cause chain and names the cases an operator can act on — unknown host, timeout, connection refused or no route — and otherwise keeps the exception's own message, which is still the most useful thing to hand to IT. Timeouts are tested before refused connections, since a connect timeout can arrive as a `ConnectException` subclass depending on how Ktor wraps it.

### B.6.2 `NtlmAuthenticator` — NTLMv2 from scratch
`data/extsystem/NtlmAuthenticator.kt` — a custom `okhttp3.Authenticator`, invoked reactively by OkHttp on any 401 with `WWW-Authenticate`. No external NTLM library; uses `javax.crypto` `HmacMD5` plus a hand-rolled inline MD4 (RFC 1320, since Android's `MessageDigest` doesn't expose MD4, needed for the NT hash).

- `phaseReached: Int` (`@Volatile`, 0/1/2) — diagnostic only, reset via `resetPhase()` before each test connection.
- **Phase 1 (Negotiate)**: triggered when the challenge header is bare `"NTLM"` or missing a token. Builds a 32-byte Type-1 message (`"NTLMSSP\0"`, MessageType=1, `FLAGS=0x80800205`, empty Domain/Workstation), base64, sent as `Authorization: NTLM <b64>`. Guard: if the original request already carried an `Authorization: NTLM ` header, the server is re-negotiating after a completed handshake → returns `null` (bad credentials, stop retrying).
- **Phase 2 (Authenticate)**: triggered when the header is `"NTLM <base64 Type2>"`. Decodes the challenge, computes:
  - NT Hash = `MD4(UTF-16LE(password))`
  - NTLMv2 key = `HMAC-MD5(ntHash, UTF-16LE(uppercase(user+domain)))`
  - Target info extracted from Type2 offset 40/44
  - NTLMv2 blob: signature `0x00000101`, Windows FILETIME timestamp, random 8-byte client challenge (`SecureRandom`), target info
  - `ntResponse = HMAC-MD5(key, serverChallenge+blob) + blob`
  - `lmResponse = HMAC-MD5(key, serverChallenge+clientChallenge) + clientChallenge`
  - Manually lays out the Type-3 byte buffer (security-buffer descriptors + domain/username/lmResponse/ntResponse bytes), base64-encodes as the new `Authorization` header.

**Domain resolution** (2026-08, priority reversed 2026-09): there are two sources for the NTLM domain — `ExtSystemConfig.domain` (Settings → External System Configuration → Credential session, also carried in the bundled per-company defaults files, see §B.6.6) and a domain typed into the username itself. `ExtSystemODataClient.buildClient` resolves them in this order:

| Typed username | Domain used | Username sent |
|---|---|---|
| `DOMAIN\user` / `user@domain` | `DOMAIN` — the typed one wins over Settings | `user` |
| `.\user` (or bare `\user`) | none — deliberately overrides a configured domain | `user` |
| `user` | `ExtSystemConfig.domain` (may be blank) | `user` |

The typed value winning means a bad configured domain can be corrected at the login screen without going into Settings, and `.\user` (Windows' "this machine, not the domain" form) is the escape hatch for signing in with no domain at all against a configured one. `parseDomainUser` always runs, including when a domain is configured, so `DOMAIN\user` combined with a configured domain can't send the domain twice — the username handed to NTLM is always the bare one.

`parseDomainUser` maps `.` to an empty domain rather than returning it verbatim, so the fallback decision in `buildClient` can't be driven off "is the parsed domain blank" — blank would then mean both "no domain given" and "no domain wanted". It keys off whether the typed username contains a separator at all (`\` or `@`) instead: a name written with one states its own domain and is taken at its word, even when what it states is none.

**Credential TTL** (`ExtSystemCredentialStore`, `EncryptedSharedPreferences` file `ext_system_credentials`, AES-256-GCM/Keystore): one slot **per profile** — `save(profileId, username, password, ttlHours)` stores `<profileId>.expiry = now + ttlHours*3_600_000L`; `get(profileId)` checks `now > expiry` on every read and, if expired, clears that profile's slot and returns `null` (lazy expiry, not a background timer). `isValid(profileId) = get(profileId) != null`.

Keying by profile is what makes the TTL mean what it reads like: an operator signing in daily stays signed in, one who appears weekly is asked again, and neither affects the other. It used to be a single slot with fixed keys, so whoever signed in last overwrote everyone before them and the TTL measured the last sign-in *on the device* rather than by that person.

### B.6.3 DTOs — `data/extsystem/ExtSystemPayload.kt`

**`ErpRecordingRow`** (upload) — see field table in §A.5.4. `recordingGuid` is read straight off the stored `RecordingEntity`; it is generated once in `DocumentRepository.recordScan` when the scan is recorded and never regenerated, so every attempt for a row sends the same value. `setLineScanned` also mints one, but only when a manual edit raises a line — that added quantity is a genuinely new recording. Lowering a line mints nothing: it deletes or trims existing queued rows, and a trimmed row keeps its GUID. Sent rows are never touched either way, so a GUID the ERP already holds is never reused, altered or dropped.

**`ErpDocumentLineRow`** (download) — see field table in §A.5.3. Rows with `lineNo <= 0` are excluded from `lines` (treated as the header row). `documentDate` is parsed as `Instant.parse("${it}T00:00:00Z")`.

**`ErpLocationRow`**: `Code`→code, `Name`→name, `Responsibility_Center`→rcCode.

**`ErpResponsibilityCenterRow`**: `Code`, `Name`, `Short?` — defined but **not currently used**; RCs are derived client-side from distinct `ErpLocationRow.rcCode` values rather than fetched from a dedicated endpoint.

**`ODataList<T>`**: `{ "value": List<T> }` generic OData envelope, used for all three GET response types.

### B.6.4 Full download flow

1. `AppViewModel.realDownloadDocuments(filter, docType, onComplete)` — reloads `config`/`creds`, aborts with a specific message if not signed in / not configured / URL blank.
2. `extSystemClient.configure(config, creds)`.
3. For each `DocumentType` in scope (one, or all 5 if `docType == null`):
   - `typeCode = config.docTypeCodeFor(type)`.
   - `buildODataFilterString(filter, typeCode, type)`: `Document_Type eq '<typeCode>'` (always, if non-blank) + `Retail_Location eq true/false` (whenever `type.retailLocation` is non-null, i.e. everything except transport sheets — see §A.5.3) + `Document_Date ge/le` (if set) + `Destination_No eq` (if set) + `Source_No eq` (if set) + `Responsibility_Center eq` (if set) + `Document_No eq` (if set — the picked inventory batch) — joined with `" and "`.
   - `appendODataFilter`: appends `?$filter=<urlencoded>` (or `&$filter=` if `?` already present); `URLEncoder.encode(..., "UTF-8")` with a `+`→`%20` post-fix.
   - `downloadRaw(finalUrl)` — paginated GET.
   - Parses as `ODataList<ErpDocumentLineRow>`, groups by `documentNo`, builds `Document`+`Line`s, `state=Downloaded`.
   - `repository.replaceDownloadedDocuments(type, documents, pruneMissing = filter.documentNo.isBlank())`.
   - On failure: increments `failures`, appends the message, **continues to the next doc type** (doesn't abort the whole batch).
4. `onComplete(failures, errorMessages)`.

**Inventory batch list:** `AppViewModel.loadInventoryBatches(filter, onResult)`, called by `DownloadFilterScreen` for a stock count in place of the date range. Same checks and same lines URL, filtered by type and scope, plus `$select=Document_No,Line_No,Document_Date,Journal_Template_Name,Journal_Batch_Name`. Parsed as `NavInventoryBatchRow`, grouped by `Document_No` into `InventoryBatch` (template, batch, date, line count) and sorted by template, batch and number. The operator's pick becomes `DownloadFilter.documentNo`. Signed out, the screen's sign-in sheet loads the list instead of downloading (§B.18, Inventory).

Locations: `realDownloadLocations()` — single `GET config.locationsUrl` → `ODataList<ErpLocationRow>` → derives distinct non-blank `rcCode`s → `locationDao.clearRcs()+upsertRcs()` then `clearLocations()+upsertLocations()`. No `$filter` applied.

### B.6.5 Full upload flow

Entry points: `uploadToExtSystem` (blocking) and `uploadInBackground` (marks every doc `PendingUpload` immediately, then runs async) — both funnel into private `runUpload(docs: List<Document>): Int`.

**Per document mode** (3.6.27). `runUpload` takes the document's queued rows as usual and hands them all to `ExtSystemODataClient.uploadDocument`, which posts one SOAP envelope built by `DocumentUploadSoap` (unit-tested): body `UploadDocument` in `urn:microsoft-dynamics-schemas/codeunit/BarcodeAppUpload`, parameter `recordings`, one `Recording` element per row in `urn:microsoft-dynamics-nav/xmlports/x50008`, with `SOAPAction: "…BarcodeAppUpload:UploadDocument"`. The GUID goes in braces, the quantity as a plain decimal, the scan time cut to milliseconds (.NET reads at most seven fractional digits), `RetailLocation` only when the type has it. HTTP 200 marks every row sent and deletes the document; anything else records the same reason (the fault's `faultstring`, which is NAV's own error text) against every row and sets `UploadFailed`. On the NAV side see §B.18, *Upload in one call*.

The sketch below is **Per scan**:

```
for each doc in docs:
    rows = repository.getRecordings(doc.documentNo, doc.type.key)
    if rows is empty: continue                             // nothing to send, and the "else"
                                                           // branch below would delete the doc
    for each row in rows (sequential):
        result = extSystemClient.uploadRecording(url, row.toNavRecording(docTypeCode, retailLocation))
        if success: repository.deleteRecording(...)        // deleted immediately, one at a time
        if failure: failureMessage = result.message; break  // remaining rows NOT attempted this pass
    if failureMessage != null:
        repository.updateDocState(doc, UploadFailed(failureMessage)); failures++
    else:
        repository.deleteDocument(doc)                      // whole doc removed, cascade
return failures
```

**No automatic retry** anywhere in this path for any failure class (400/404/500/network) — a failed document simply sits in `UploadFailed(reason)` until the user taps Retry (which re-reads whatever `getRecordings` still returns, i.e. only rows that weren't already deleted by a prior partial success).

### B.6.6 Config file reference

**`app/src/main/assets/ext_system_defaults_*.json`** (bundled, **two files per company** — `_test` and `_prod`; as of 2026-09: `_commerce`, `_mebel`, `_pohistvo`, `_mobilis` in both flavours) — discovered dynamically at runtime rather than referenced by a fixed name, so adding a company or an environment is just adding a file. The grouping comes from each file's `environment` field, **not** from its name: a filename is a label, and a label that disagrees with the contents is exactly the failure this is meant to prevent. **Test** runs on `http://192.168.100.87:8048/NAV_TEST_HR/ODataV4/`, **production** on `http://192.168.100.113:7048/NAVBARCODE/ODataV4/`. Each endpoint URL is `{serverBaseUrl}Company('{company}')/{service}`, with the services `BarcodeAppEntries`, `LocationList` and `BarcodeAppRecordings`.

Two things about these files that are assumptions rather than facts, and are worth checking against NAV before a device goes live:

- **Each `_test` file points at its own company**, as its `_prod` file does. Until 2026-10 all four test files addressed `Prima Commerce d.o.o.`, so loading "Prima Mebel" on test worked on Commerce's data. The company the endpoints address is what the app info shows (`ExtSystemConfig.companyName`), which is how it surfaced.
- **The production company segment is each file's `companyName`, URL-encoded.** That is certain for Commerce, where the test file's `Company(...)` is exactly its `companyName`, and assumed for the other three. A wrong one passes sign-in — `testConnection` only hits `serverBaseUrl` — and fails at the first download.

`domain` is set only for Commerce (`PRIMA-COMMERCE`). The production files carry the **same `loginQrKey` as the test files**, by decision: the login codes already printed were made with that key and have to work on production. The cost is that production codes are protected exactly as test ones are — obfuscation-grade, the key being in the APK and in git. A device configured from the production files before the key was added has none; loading the production defaults again picks it up, since a file with a key sets it:
```json
{
  "companyName": "Prima Commerce d.o.o.",
  "serverBaseUrl": "http://192.168.100.87:8048/NAV_TEST_HR/ODataV4/",
  "credentialTtlHours": 168,
  "loginQrKey": "<base64, 32 bytes>",
  "domain": "",
  "documentLinesUrl": "http://192.168.100.87:8048/NAV_TEST_HR/ODataV4/Company('Prima%20Commerce%20d.o.o.')/BarcodeAppEntries",
  "documentTypeCodes": {
    "WAREHOUSE_SHIPMENT": "SHIPMENT", "WAREHOUSE_RECEIPT": "RECEIPT",
    "RETAIL_SHIPMENT": "SHIPMENT", "RETAIL_RECEIPT": "RECEIPT", "TRANSPORT_SHEET": "TRANSPORT"
  },
  "locationsUrl": "http://192.168.100.87:8048/NAV_TEST_HR/ODataV4/Company('Prima%20Commerce%20d.o.o.')/LocationList",
  "recordingSyncUrl": "http://192.168.100.87:8048/NAV_TEST_HR/ODataV4/Company('Prima%20Commerce%20d.o.o.')/BarcodeAppRecordings"
}
```
`companyName` and `environment` are read by `AppViewModel.listExtSystemDefaultsCompanies()` to label and group the "Load built-in defaults" picker shown in `ExtSystemConfigScreen`/`SettingsScreen`; `companyName` is not part of `ExtSystemConfig`, `environment` is. A file missing either is skipped rather than guessed at, and so is an `environment` value this build does not recognise. `domain` (added 2026-08) feeds `ExtSystemConfig.domain` — see §B.6.1/§B.6.2's domain-parsing note. `loginQrKey` (added 2026-09) is the AES-256 key for encrypted sign-in QR codes — see §B.10; distributing it here is what lets it be rotated without a new APK. It is the one field never written to an exported copy. `documentTypeCodes` keys use the enum's `.name` (matched via `dto.documentTypeCodes?.get(type.name)`).

**`prima_config.json`** (repo root) — an all-blank template of the identical shape (predates the per-company split); not read by any app code, purely a distributable seed for generating a deployment-specific defaults file.

**`AppViewModel.parseExtSystemConfigJson(json)`** — deserializes into a private nullable-mirror DTO, applies defaults (`credentialTtlHours ?: 24`, others `.orEmpty()`), never persists directly. `loginQrKey` is the one exception to `.orEmpty()`: absent or blank means *keep the device's current key*, not *clear it* — otherwise importing an exported file (which carries no key) would silently disable QR sign-in on a device that had a working one. A file that does carry a key still overwrites it, which is how rotation works. `loadExtSystemDefaults(fileName)` reads the given bundled asset then delegates here.

`exportConfigurationJson()` backs "export as file": it builds an `ExtSystemDefaultsDto` from the live `ExtSystemConfig` and `AppSettingsStore.get()`, and serializes it pretty-printed via a separate `exportGson`. `loginQrKey` is passed as `null` and Gson omits nulls, so the field is *absent* rather than empty — which is exactly what the import reads as "leave the device's key alone". It then checks the live key is not in the output and refuses to return anything if it is, so a renamed field or a value that happens to carry it fails the export (the caller's save-failed toast) rather than leaking. Every document type is written out explicitly, present or not, so the file shows the whole shape of what it controls.

**A configuration file is a `DeviceConfiguration`, not just an `ExtSystemConfig`.** Alongside the eight ERP fields it carries the device half of `AppSettings` — `enabledDocTypes`, `docTypeFilters`, `debuggerActive` — keyed in the file by `DocumentType.name` to match `documentTypeCodes`, and stored by `DocumentType.key`; a name nothing matches is dropped rather than carried, so a typo cannot invent a document type. Files list the types switched **on** (`enabledDocTypes`). A file from before the complaint split lists those switched off (`disabledDocTypes`); it is still read, but against `DOC_TYPES_BEFORE_COMPLAINT_SPLIT`, so it cannot switch on a type it never mentioned, and it is never written. The bundled files carry neither, so loading built-in defaults leaves the device's choice of types alone. A type the file has no `documentTypeCodes` entry for keeps the device's code. The personal half (text size, language, haptics, working location) is deliberately absent: it belongs to a person, and a deployment file that reset it on every load would undo the reason those settings are per-operator.

Every field of the DTO is nullable and **absent means "leave this as it is"** — the same rule the QR key has always had, now extended to the device settings. A configuration written before these fields existed must not silently switch every document type back on.

Two screens apply a loaded configuration, and they differ only in *when* it persists. `ExtSystemConfigScreen.applyConfiguration` fills its own form with the ERP half and pushes the device half straight through `onDisabledDocTypesChange` / `onDocTypeFiltersChange` / `onDebuggerActiveChange`, because those have no form fields there. `SettingsScreen.applyConfiguration` stages the ERP half in `pendingExtSystemConfig` and puts the device half into the local state `buildSettings()` reads, so both leave on the same exit-save.

`listExtSystemDefaultsCompanies()` lists `assets.list("")`, filters `ext_system_defaults_*.json`, and reads each file's `companyName`, `environment` and whether the required addresses are filled in — the last becomes `ExtSystemDefaultsCompany.isConfigured`, which the picker uses to show an incomplete file without letting it be loaded.

## B.7 Auth & Config Storage

### `SecureStore` (3.6.28)
Replaces androidx.security's `EncryptedSharedPreferences`, which Google deprecated. Values are encrypted with one AES-256-GCM key in the Android Keystore (alias `secure_store_v1`, created on first use, never exportable) and stored as `base64(iv ‖ ciphertext ‖ tag)` in a plain `SharedPreferences` file; names are stored as their SHA-256 digest, so the file does not list who has used the device. A value that no longer decrypts (a Keystore that lost its key after a restore or an OS update) reads as absent and is removed, where the old library crashed at startup. **Nothing was migrated, by decision:** each store deletes its old file unread on first use (`legacyFileName`), so every operator signs in once after the update, and the profile list refills as they do. A profile's database is named after its id (`databaseName`), so no documents or unsent scans are lost. The `security-crypto` dependency is gone.

### Profiles are bound to an ERP installation (3.6.53)

**The installation** is `ExtSystemConfig.serverIdentity`: the server base URL (trimmed, without trailing slashes, lower case) plus the company read from `Company('…')` in the document lines URL. A different host, port, instance or company is a different installation, and so are test and production; renaming a service in an endpoint address is not. `serverLabel` is what a screen calls it: the company, or the address when the URL names none.

**A profile is an operator on one installation.** `UserProfile(id, displayName, serverKey, serverLabel, environment)`; `UserProfile.key` = `profileKey(id, serverKey)` (the id's length first, so two different pairs can never read alike). Everything a profile owns is filed under the key:

| What | Where | Keyed by |
|---|---|---|
| Documents, lines, recordings | `user_<sha256(key) prefix>.db` (`DatabaseProvider`) | profile key |
| Saved sign-in | `ExtSystemCredentialStore` | profile key |
| Enrolment | `UserProfileStore`, one JSON list | profile key |
| Locations, responsibility centres | `shared_<sha256(identity) prefix>.db` (`SharedDatabaseProvider`) | installation, shared by its operators |
| Last location and centre | the operator's personal settings, as `lastLocationCode@<identity>` | operator + installation |
| Text size, language and the other personal settings | `app_settings_<id>` | operator only: they follow the person |

**Why.** (1) Security: with one file per operator, anyone who could change the server could point the device at one that accepts every password, sign in under an enrolled name and open that person's documents and unsent scans. Now that name on that server is a different, empty profile. The setup door closed in 3.6.52 stops this before sign-in; the binding stops a signed-in operator doing it from Settings. (2) Correctness: a device moved between companies used to keep one database, so company A's unsent scans were uploaded to company B (and booked there whenever B had a document with the same number), and B's download merged with or pruned A's documents.

**A session never outlives its installation.** `AppViewModel.saveExtSystemConfig` returns `Saved`, `SavedAndSignedOut` or `RefusedTransferRunning`: when the configuration being saved has another `serverIdentity` and somebody is signed in, it signs them out (refusing while a transfer is in flight, as sign-out itself does). `BarcodeApp.requestConfigSave` asks first with `ServerChangeDialog`, which adds a warning when the operator has unsent scans, since those stay with the installation they were made for and reappear when the device is pointed back at it. Both ways of saving a configuration while signed in go through it: the External System Configuration screen (which stays open while the question is asked) and the Settings exit-save. `testExtSystemConnection` keeps the tested credentials only when the server tested is the one the session belongs to.

**On screen.** The sign-in screen lists the operators enrolled on the configured installation (`profilesOn`). **Operators** lists every profile on every installation, each row naming its company and environment, and removes them one at a time; an operator's personal settings go with their last profile.

**Limits.** The identity is the address the device talks to, not proof of who answers there: without HTTPS, somebody able to impersonate the real server's address on the network is outside what the app can detect (§B.12). A server that moves to a new address is a new installation: send everything first, or point the device back at the old address to reach what was left.

**Upgrading from 3.6.48–3.6.52** (test devices only): the profile list and the database names changed shape, so an operator enrols again and starts empty; clear the app's data to remove the unreachable old files.

### `UserProfileStore`
`SecureStore("user_profiles_v2")` (was `EncryptedSharedPreferences("user_profiles")`). Holds the profiles this device knows — operator id, display name and the installation — and nothing else, as one JSON list. **No expiry**, deliberately: it is what reaches an operator's own data, and that data does not expire either. The PBKDF2 digests an older version kept went with the old file.

The current profile is held **in memory only**, not in the prefs. Persisting it would restore the session on the next launch, which is exactly what must not happen — a device left on a shelf would come back up inside the last operator's data with no password asked. A process restart means signing in again, against the ERP.

`normalise(raw)` is the identity function: `parseDomainUser().second.trim().uppercase()`, rejected if blank or over 50 characters. Callers refuse rather than truncate — a silently shortened name is a different person as far as this key is concerned. `databaseName(id)` hashes it, so a filename can never depend on the characters an operator typed.

### `AppSettings` / `AppSettingsStore`
Plain (unencrypted) `SharedPreferences`, split across two files while `AppSettings` stays one type, so nothing above the store knows the seam exists.

**Personal** — `app_settings_<profile id>`: text size, uppercase, language, debounce, haptics, sound, over-scan warning, background sync, last location and RC. **The device's** — `app_settings`: disabled doc types, per-type filter modes, debugger flag. Those last three describe the installation, the same way `ExtSystemConfig` does, and an administrator should not have to redo them for each new operator.

| Field | Type | Default |
|---|---|---|
| textSize | `TextSize` enum | LARGER |
| uppercaseText | Boolean | false |
| language | `Language` enum | CROATIAN |
| debounceTime | Int (ms) | 200 |
| hapticEnabled | Boolean | true |
| soundEnabled | Boolean | true |
| warnOnOver | Boolean | true |
| backgroundSync | Boolean | false |
| lastLocationCode / lastRcCode | String | "" |
| disabledDocTypes | `Set<String>`; stored inverted, as `enabledDocTypes` (comma-joined `DocumentType.key`) | every type — nothing switched on |
| docTypeFilters | `Map<String, DocTypeFilterMode>` (`"key:MODE,..."`) | emptyMap |
| debuggerActive | Boolean | false |

**Document types are stored as the set switched on** (`enabledDocTypes` in the device file), and handed out as `disabledDocTypes` so nothing above the store changed. Older builds stored the switched-off set, which meant a type added later arrived switched on. The first read after the update settles each device once and writes the answer down: an older list → everything that build knew and did not switch off stays on; no list but a server URL configured → an upgraded device that never saved settings, so all of the older types are on; neither → a fresh installation, nothing on. In every case the two complaint types start off. The first read happens at app start, before anything can be configured, so setting up a fresh device cannot make it look upgraded.

### `ExtSystemConfig` / `ExtSystemConfigStore`
Plain `SharedPreferences("ext_system_config")` (non-secret — no credentials stored here).
```kotlin
data class ExtSystemConfig(
    val serverBaseUrl: String = "",
    val credentialTtlHours: Int = 24,
    val documentLinesUrl: String = "",
    val documentTypeCodes: Map<DocumentType, String> = emptyMap(),
    val recordingSyncUrl: String = "",
    val locationsUrl: String = "",
    val domain: String = "",   // added 2026-08 — see B.6.2's domain-parsing note
    val loginQrKey: String = "",
    val environment: ExtSystemEnvironment? = null,
    val inventoryBatchesUrl: String = "",                         // 3.6.27, page 50208
    val uploadMode: RecordingUploadMode = RecordingUploadMode.PER_SCAN, // 3.6.27, PER_SCAN | PER_DOCUMENT
    val documentUploadUrl: String = "",                           // 3.6.27, codeunit 50117 over SOAP
) {
    fun docTypeCodeFor(type): String
    val isConfigured get() = serverBaseUrl.isNotBlank()
}
data class ExtSystemCredentials(val username: String, val password: String)
```

### `ExtSystemCredentialStore`
`SecureStore("ext_system_credentials_v2")` since 3.6.28 (was `EncryptedSharedPreferences("ext_system_credentials")`, deleted unread on first use). `save`/`get` (TTL-checked)/`isValid`/`clear` all take a profile id, as described in §B.6.2. There is deliberately no device-wide wipe: "Clear cache" used to call one, which meant one operator pressing a button in their own session revoked everybody else's server access. The per-profile `clear(id)` is also what `AppViewModel.deleteProfile` calls when an operator is removed from the device.

Holds the NAV password in the clear, because NTLM computes its response from the password itself and cannot work without it. It is also the only credential on the device now that `UserProfileStore` keeps no digest.

**Android auto-backup is off** (`android:allowBackup="false"`, no `dataExtractionRules`/`fullBackupContent` — those were the project template's empty samples and were removed). Left on, it copied this file and every operator's `user_<hash>.db` — unsent scans included — into whatever Google account happens to be signed in on a shared warehouse scanner. The restore side could not work regardless: the Keystore key is device-bound and is not backed up, so restored values would not decrypt.

### The gate, and the doors through it

**The setup door is for first setup only (3.6.52).** `onOpenConfig` is passed only while `ExtSystemConfig.serverBaseUrl` is blank, and the configuration screen behind it renders only then. The server decides whether a password is good, so changing it before signing in let anyone holding a configured device point it at a server that accepts everything and sign in as any enrolled operator, opening that person's database. Once an address is saved, the configuration changes only after signing in (Settings → Server & endpoints). A device with a wrong saved address, or whose server moved, is reset by clearing the app's data or reinstalling. The second half is the binding of each profile's data to its installation, below.

`BarcodeApp` (`navigation/AppNavHost.kt`) early-returns into `SignInScreen` whenever `currentProfile` is null — an early return rather than a branch around the `NavHost`, so no route is reachable signed-out, not even via a back stack left by the previous operator.

That gate had a chicken and egg in it. Signing in asks the server whether the password is good (`testConnection`), and a device out of the box has no `serverBaseUrl` to ask; with no server there is nothing to ask and the sign-in simply fails. A fresh install could not be configured, and so could not be signed into either.

So `SignInScreen` takes an `onOpenConfig` callback and shows a button for it, and the gate renders `ExtSystemConfigScreen` in place of the sign-in screen while `configuringAtSignIn` is true. That screen needs no database — it reads `ExtSystemConfigStore` and the device half of `AppSettingsStore`, both device-wide — so it composes perfectly well with nobody signed in, which is why this is a few lines rather than an audit. `savedCredentials` is left null (nothing to prefill, nowhere to store), and the test-connection call is **not** wrapped in `launchWithDebug`: that dialog composes below the early return and would never appear.

**QR sign-in lives on this screen too, not only in `LoginSheet`.** A DataWedge receiver is registered for as long as the gate is up (nothing else composes while it is, so no second receiver can double-deliver), plus a camera button gated on `FEATURE_CAMERA_ANY` for the MC3300 configurations that ship without one. Both call the same `parseLoginQr(raw, loginQrKey)` and fill the two fields; the operator still taps to submit, exactly as in the sheet. This is not decoration — before the gate existed the way into the app *was* `LoginSheet`, so putting a typed-only screen in front of it took the scanner away from the one moment a warehouse operator most wants it. A bad code reports inline (`login_qr_invalid`) rather than through the `error` dialog, because a mis-pull of the trigger is routine and a dialog would be in the way of the next attempt.

It stays open even on a device that already has operators enrolled. The same screen is reachable by anyone who can sign in anyway, the devices sit in a controlled area, and gating it behind sign-in would lock the device out of repair exactly when the configuration is what broke.

**A second, read-only door: "O aplikaciji".** `SignInScreen` takes `onOpenAppInfo` and the gate renders `AppInfoScreen` while `appInfoAtSignIn` is true. It shows what support asks first — build, Android, SDK, database version, ERP environment and company — and is open to anyone at the gate because nothing in it is personal or secret. The question usually comes from someone who *cannot* sign in. `AppInfoScreen` catches the system back button itself (`BackHandler`): it is drawn in place of the gate rather than as a route, so an uncaught back would close the app.

### Login flow (end-to-end)
1. `LoginSheet` (`ui/screen/LoginSheet.kt`) — reusable full-screen `Dialog` (`DialogProperties(usePlatformDefaultWidth = false)`, resized to `MATCH_PARENT` via `DialogWindowProvider`; changed from a `ModalBottomSheet` in 2026-08), reused by `ExtSystemConfigScreen` (Test connection), `LocationRcPickScreen` (refresh without credentials), `DownloadFilterScreen` (auto-opens if `!hasCredentials`), and the main-menu sign-in entry point.
2. Optional `onTestConnection` callback (2026-08): when provided, submit first verifies the typed credentials against the NAV server (same check as `ExtSystemConfigScreen`'s own "Test connection") before calling `onSubmit` — shows an inline spinner and error text on failure rather than blindly accepting whatever was typed. Left `null` for flows that already do their own testing.
3. `AppViewModel.saveCredentials(username, password)` → `extSystemCredentialStore.save(...)`, refreshes the `_credentials` `StateFlow`. **Returns `CredentialSave.WrongOperator` and stores nothing** when `UserProfileStore.normalise(username)` is not the signed-in profile's id: the sheet is reachable from inside a session, so someone handed an already-signed-in device could type their own ERP account into it and have it filed under the other person's profile — every row uploaded afterwards then travelled on the wrong session while still carrying the signed-in operator's name. `testExtSystemConnection` turns that refusal into a `Failure` (`R.string.signin_wrong_operator`), so all five `LoginSheet` call sites surface it inline. Testing with an account other than your own therefore means signing in as that account first. The third outcome, `CredentialSave.NoProfile`, is **not** a refusal and is not reported as one — it is the pre-sign-in configuration screen (see §"The gate" below) testing that the server answers at all, with nobody yet to file the answer under.
4. `AppViewModel.signOut()` → `extSystemCredentialStore.clear()`, resets `_credentials` to null.
5. `credentials: StateFlow<ExtSystemCredentials?>` is observed in `MainActivity` to derive the current `User` display name (parsed client-side from the username string — no live ERP profile lookup), shown via an avatar/initials button on `MainMenuScreen` that opens `UserInfoScreen` (2026-08).

## B.8 Hilt DI

Only one module: `di/DatabaseModule.kt`.
```kotlin
@Module @InstallIn(SingletonComponent::class)
abstract class DatabaseModule {
    @Binds @Singleton
    abstract fun bindDocumentRepository(impl: DocumentRepositoryImpl): DocumentRepository
    companion object {
        @Provides @Singleton fun provideDatabase(...): AppDatabase = ...
        @Provides @Singleton fun provideLocationDao(db): LocationDao = db.locationDao()
    }
}
```
Other DAOs (`DocumentHeaderDao`, `DocumentLineDao`, `RecordingDao`) are **not** separately provided — consumers inject the whole `AppDatabase` and call `db.documentHeaderDao()` etc. directly. No dedicated network/auth module — `ExtSystemODataClient`, `AppSettingsStore`, `ExtSystemConfigStore`, `ExtSystemCredentialStore`, `DatabaseExporter` are plain `@Singleton @Inject constructor(...)` classes. `DataWedgeManager` is a plain Kotlin `object` (no DI). Entry points: `BarcodeApplication` (`@HiltAndroidApp`), `MainActivity` (`@AndroidEntryPoint`), `AppViewModel`/`RecordingViewModel` (`@HiltViewModel`).

## B.9 Barcode Scanning Subsystem

Two independent input paths feed the same domain logic (`RecordingScreen.handleScan` → `RecordingViewModel`/callbacks → `DocumentRepository.recordScan`).

**Scan sounds** come from `data/sound/SoundEngine.kt`, for both paths, gated by the personal `soundEnabled` setting. `confirm()` (single beep) — the scan counted, nothing to look at. `warning()` (a loud falling "dee-doo", four times) — the scan brought up a warning: a barcode not on the document (`RecordingScreen`), a document number not found (`DocumentListScreen.handleDocScan`), or an over-scan, where it plays *instead of* the confirm and only when `warnOnOver` is on, exactly like the dialog it accompanies. One tone for all three on purpose: whether or not the scan counted, the operator's next move is the same — look at the screen. `error()` (low NACK) is now used only for a scanned code that is not a sign-in code, on `SignInScreen` and `LoginSheet`. They play on **`STREAM_MUSIC`**, not `STREAM_NOTIFICATION`: the notification stream is silenced whenever the device is on vibrate or silent, so a scanner left on vibrate went quiet while the app's own setting said "on", and the rejection tone was the first casualty. The UoM-mismatch warning still plays the plain confirm.

**The warning is synthesised, not picked from `ToneGenerator`.** It used to be `TONE_PROP_BEEP2`, two bursts of 400 Hz + 1200 Hz. On a Zebra that came out as a dull, bass "bloop" right after the scanner's own decode beep, which is high and comes from DataWedge on every read — good or bad, before the app has judged it. So the beep that says "read" drowned out the one that says "look". Nothing else on `ToneGenerator`'s list fits: its highest clean tone is 1200 Hz, which Android uses for acknowledgement, and the higher ones are telephony signals that differ by manufacturer. `SoundEngine` therefore renders its own: 2900 Hz → 1900 Hz, four times, about 1.4 s in all (`WARNING_PATTERN`; it was twice, 0.67 s, until doubled on request). 2–3 kHz is where a handheld's small speaker is most efficient and the ear most sensitive, so it is the loudest sound a given volume can make; the falling pitch is what reads as "no". Each tone is the first three terms of a square wave (brighter than a sine, nothing high enough to alias), normalised to 90 % of full scale, with a 3 ms ramp at each edge so it does not click. The samples are rendered once and shared; each engine loads them into its own static `AudioTrack` (`USAGE_MEDIA`, so the same stream and volume as the tones) and rewinds it per play, so a second warning restarts the sound rather than queueing behind the first. The scanner's decode beep is deliberately left alone — it is the operator's confirmation that the trigger read something, on every screen, whether or not the app's Sound setting is on. The pattern was chosen on paper; the constants at the top of `SoundEngine.kt` are where to tune it by ear.

### B.9.1 Camera path (ML Kit + CameraX)
- **`BarcodeAnalyzer.kt`** — `ImageAnalysis.Analyzer` using `BarcodeScanning.getClient()` (default options, all formats). Picks the **largest bounding box** among detected barcodes (avoids incidental captures of background labels). Debounces repeated identical values (emits only on value change or after `debounceMs`, default 1500ms internal / overridden by the caller's setting). Always `image.close()`s.
- **`CameraPreview.kt`** (`ui/component/`) — binds `Preview` + `ImageAnalysis` (`STRATEGY_KEEP_ONLY_LATEST`, single-thread executor) to `CameraSelector.DEFAULT_BACK_CAMERA`. Makes no sound of its own — the tone moved to `SoundEngine` so the hardware trigger gets one too (see above); auto-closes unless `continuous=true`; animated scanning-line + corner-bracket overlay; one-shot auto-focus at center (`FocusMeteringAction`, auto-cancel disabled — focus stays locked). Requires `android.permission.CAMERA` (optional — a device can rely solely on the hardware wedge). Camera bind failures are only logged (`Timber.e`), no user-facing error UI.

### B.9.2 Hardware wedge path (Zebra DataWedge)
- **`DataWedgeManager.kt`** — plain Kotlin `object`, talks to the DataWedge service via broadcast intents.
  - `configure(context)` — creates/updates a DataWedge profile `"Prima Barcode"` scoped to this app's package, configures the `INTENT` output plugin (`intent_action = "com.erpbarcode.scanner.SCAN"`, `intent_delivery = "2"`), **switches the `KEYSTROKE` plugin off**, and a `BARCODE` plugin that pins `aim_type = "0"` (single shot) and enables `decoder_qrcode`. `RESET_CONFIG = "false"` on the barcode plugin, so this merges rather than replacing a site's other decoder settings. Called once from `MainActivity.onCreate()`.
  - Continuous read was offered as a setting until 2026-09 and is gone. It changed the hardware `aim_type` app-wide while its label only mentioned the camera, and the two scan paths already need a debounce against each other, which continuous read pushed on. Single shot is now pinned rather than inherited, so a unit left in continuous read by another profile doesn't deliver a burst the app has no reason to expect.
  - `createReceiver(onScan)` — `BroadcastReceiver` extracting the scanned string from extra `com.symbol.datawedge.data_string`. Every screen registers it through `register(context, receiver)`, which uses **`RECEIVER_EXPORTED`** on API 33+ (no flag below). It used to be `RECEIVER_NOT_EXPORTED`, copied into each screen: on Android 13+ that drops DataWedge's broadcast without an error, because DataWedge is another app. Keystroke output hid it until 3.6.0 turned keystrokes off, after which an Android 14 TC21 beeped on every scan and nothing happened anywhere. The cost of exporting is that another app on the device could broadcast `com.erpbarcode.scanner.SCAN` and pass for a scan.
  - `intentFilter()` — `IntentFilter("com.erpbarcode.scanner.SCAN")`, registered via `DisposableEffect` for the composed lifetime of every screen that scans: `RecordingScreen`, `DocumentListScreen`, `SignInScreen` and `LoginSheet`.
  - `isAvailable(context)` — whether the DataWedge package is installed, i.e. whether the device has a scanner. Needs the `<queries>` entry for `com.symbol.datawedge` in the manifest, or from Android 11 the package is invisible and every device looks scanner-less.
  - `startScan(context)` — the soft trigger (`SOFT_SCAN_TRIGGER` = `START_SCANNING`): a read exactly like a trigger pull, its result arriving through the same intent. Behind the "Scan QR code" button on the sign-in screen and `LoginSheet`.

**One delivery per scan.** Keystroke output used to be left at DataWedge's default, which is on, so every scan arrived twice: as the intent, and typed into whichever text field had focus. On the sign-in screen that typed a login code's ciphertext into the user-name field. On the recording screen each scan went through `handleScan` twice, once from the intent and once from the scan bar, and only the debounce kept it from being counted twice. `DocumentListScreen` had relied on the typed copy alone and now registers the intent receiver like the rest. Side effect, accepted: scanning into an ordinary text field (a search, a filter) no longer works anywhere.

**Scanner first, camera only without one — everywhere.** Every screen computes `hasScanner = DataWedgeManager.isAvailable(...)` and offers the camera only when `hasCamera && !hasScanner`: the scan bar's camera icon on `RecordingScreen` and `DocumentListScreen`, and the "Scan QR code" button on `SignInScreen` / `LoginSheet`, which drives the scanner where there is one. On a camera-less MC3300 that button used to be missing, so the screen showed no way to scan a login code even though the trigger worked.

### B.9.3 `handleScan` — the shared entry point for every scan path

Implemented entirely client-side in `RecordingScreen.kt`'s local `handleScan(rawInput: String)` (not in `AppViewModel`/repository). All three input paths — the hardware DataWedge broadcast receiver, the camera (`CameraPreview.onBarcode`), and `ScanBar`'s manual entry field — call this same function; nothing about parsing or recording differs by source.

**Cross-path duplicate guard** (added 2026-08): the hardware trigger and the camera can both be "live" at once (the DataWedge receiver is registered for the whole screen lifetime regardless of whether the camera is open), so a single physical scan gesture could otherwise reach `handleScan` twice and record the quantity twice. The first lines of `handleScan` ignore a repeat of the identical raw value arriving within `debounceTime` ms of the last one handled, regardless of which path delivered it:
```kotlin
val now = System.currentTimeMillis()
if (rawInput == lastHandledBarcode && now - lastHandledAtMs < debounceTime) return
lastHandledBarcode = rawInput; lastHandledAtMs = now
```

**The `BARCODE|UOM|QTY` format**:
```kotlin
val barcode = rawInput                                     // ALWAYS the full raw string — matched & stored as-is
val pipeParts = rawInput.split("|")
val parsedQty = if (pipeParts.size == 3) pipeParts[2].toDoubleOrNull() else null
val parsedUom = if (parsedQty != null) pipeParts[1] else null
val matchedLine = doc.lines.find { it.barcodeNo == barcode }
```
- Trigger: **exactly 3 pipe-separated parts**, last part parses as `Double`. Anything else falls through to ordinary handling.
- **Matched line**: `qty = parsedQty ?: matchedLine.scanningQty`; calls `onScan(lineNo, barcode, qty)` (→ `RecordingViewModel.recordScan`), immediately. On a stock count a `BARCODE|UOM|QTY` label that matches no line exactly lands on the line whose barcode is the part before the first `|`, with the printed quantity: NAV sends plain barcodes for an inventory, because a quantity label on the shelf prints whatever it holds. If `warnOnOver` is on and the new total pushes the line into Over, an over-scan warning dialog shows, except on a line added on the device and on a stock count's line expecting 0 or less (`warnsOnOver`), where every scan would raise it (informational only — the scan is already recorded). If `parsedUom != null && parsedUom != matchedLine.unitOfMeasureCode` → UoM mismatch dialog (also informational only).
- **Unmatched**: nothing is recorded. `barcodeNotFoundError` is set (shows the "Barcode not found" dialog), the scan bar flashes red for 600ms (Slate → `#7A1A1A` → back), and an error haptic fires. On a stock count `notOnDocumentScan` is set instead and the "Not on the batch" dialog asks; **Count it** calls `onScanNotOnDocument` → `RecordingViewModel.recordScanNotOnDocument` (§A.3). A line added on the device never raises the over-scan warning.

**Fixed 2026-08**: the DataWedge receiver's callback is registered once via `DisposableEffect(Unit)` — since `handleScan` is a local function redefined on every recomposition, the receiver was pinned to the very first composition's `handleScan` (and therefore a stale `doc` snapshot) for the rest of the screen's lifetime, silently evaluating over-scan/UoM-mismatch checks against outdated data for every hardware scan after the first. Fixed via `rememberUpdatedState(::handleScan)` so the receiver always calls the current version.

## B.10 Screens (`ui/screen/`) — Reference

### `MainMenuScreen.kt`
`data class DocTypeSummary(type, count, statusMini: List<LineStatus>, blocked: Boolean = false)`. Home screen: `AppTopBar` → `DocumentStatsDashboard` (tap → Dashboard) → RC/Location pill row (either cell → Location/RC pick) → "DOCUMENTS" `LazyColumn` of type rows (icon, label, mini `StatusProgressBar`, count badge) → **"RECORDINGS"** section.

A blocked type is *not* dimmed: it stays tappable and the tap raises the "choose a location first" dialog. Greying it out only told the operator "no" without telling them why. A type is blocked while what it is scoped by is not selected — a location, or for Complaint a responsibility centre (§B.11.4).

The RECORDINGS entry is **one row under its own header**, not a list: title, the total number of scans behind it, and the number of documents as the trailing figure (greyed at zero), shaped like a document-type row because from the operator's side that is what it is. It opens `RecordingsListScreen`. It used to render the documents inline, which on a good shift is dozens of rows pushing the document types — what the menu exists for — off the top of the screen. The header (`main_recordings_section_header`) is what keeps it from reading as an eighth document type, and **both header and row stay visible at zero**, so the screen keeps one shape.

Its source either way is `MainActivity`'s `recordedDocs`, built from **all** `documents` on `totalScans > 0` rather than from `filteredDocs` on a scanned-line count — both of those narrowings key off a line's scanned quantity, and an orphaned scan belongs to no line, so a document whose scans were all orphaned would vanish from the one list meant to hold everything scanned. It is also not filtered by `disabledDocTypes` or by the working location, and a document that uploads cleanly is deleted locally and so leaves the list. **That breadth is now load-bearing**: since the location filter admits no exceptions, this is the only place in the app that shows an operator work they started somewhere else, and narrowing it would leave unsent scans with nowhere to be seen.

### `DocumentListScreen.kt`
Two tabs — **Orders** (`Downloaded`/`InProgress`/`PendingUpload`/`UploadFailed`) and **Errors** (`UploadFailed`). Client-side filters: `DocumentFilter` only. It does **not** re-check location — the caller hands it `typeDocs`, already filtered by the type's `DocTypeFilterMode`, and the check that used to live here compared `sourceCode` unconditionally, so on a responsibility-centre-scoped type it cut by location as well. `locationCode` is still a parameter, for the subtitle. Dark `ScanField` (`handleDocScan`) reports a document it cannot find; there is no doc creation. Bottom bar varies per tab (Download+Upload / Clear-errors+Upload), and UPLOAD is dimmed and inert unless something is actually queued.

The third tab, **Recordings**, was removed along with the 5-second long-press that lived on it: the tab listed documents Orders already held, and the thing it was really for — seeing what has been scanned — is now the main menu's RECORDINGS section, which shows individual scans rather than the documents carrying them. The long-press moved to `RecordingsTreeScreen`'s summary card.

### `DocumentOverviewScreen.kt` (the "Dashboard")
Cross-type view, 3 tabs: **Errors**, **My Location** (per-type `DocTypeFilterMode` match; with nothing selected it shows nothing, and the two complaint types only once a centre is selected), **All**. Opening the filter from the "My Location" tab locks source/RC to the current selection. Empty-state green checkmark + "No issues" shown on any empty tab, not just Errors.

### `DocumentFilterScreen.kt`
Generic filter editor shared by DocumentListScreen and DocumentOverviewScreen. Sections: Status (multi-select chips), Document Type (checkboxes, hidden if `showDocTypeFilter=false`), Document Date (from/to, cross-clamping date pickers), Destination/Source code, Responsibility Center (dropdown-only `ExposedDropdownMenuBox`, or locked/greyed field if `lockedSourceCode`/`lockedRcCode` passed). Reset / Apply footer.

### `DownloadFilterScreen.kt`
Pre-download filter form: date range, destination text field, and **exactly one** of Source/RC picker rows shown (the other hidden entirely, not just disabled) depending on which is fixed via `fixedSourceCode`/`fixedRcCode`. Auto-opens `LoginSheet` on entry if `!hasCredentials`.

For a stock count (`docType.isStockCount`) the date range is replaced by a batch picker built like the location one: a selection row (`InventoryBatchSelector`, on `LrcSelectionRow`) that opens a bottom sheet (`InventoryBatchPickerSheet`, the same shape as `LocationPickerSheet`, with search):
- It is loaded through `onLoadInventoryBatches` → `AppViewModel.loadInventoryBatches` from **`BarcodeInventoryBatches`** (NAV page 50208, one row per sent batch, `$filter` on `Barcode_Location_Code` and/or `Barcode_Resp_Center`; 3.6.27 — before that it read every 50179 row of the location with `$select` and grouped them here). It loads on entry, again whenever the location or centre changes, or right after the sign-in sheet when the operator was signed out. In that case the sheet loads the list instead of downloading, and a local `signedIn` stops the screen from asking again.
- **The row** reads "Select batch" until something is picked, then shows template · batch above document no. · date · line count (a `<plurals>` resource). While the list loads it shows a spinner and does nothing when tapped. After an error, or before the list has come back at all, a tap asks for the list again; an error also shows its message and **Try again** under the row.
- **The sheet** searches template, batch, description and document no., ticks the current pick, and says when no batch is ready here. A **refresh** button beside the search loads the list again without leaving the sheet (a spinner while it does, an error under the search). Picking closes it.
- **OK** is disabled until a batch is picked (unless signed out, where OK is how the sign-in is reached). The pick goes out as `DownloadFilter.documentNo`.

### `ExtSystemConfigScreen.kt`
Server URL, Test Connection (opens `LoginSheet`), TTL segmented buttons (8h/24h/48h/7d), per-`DocumentType` cards (enable switch — every type starts off on a new installation —, URL field, read-only doc-type-code, Filter-by segmented control), Locations URL, Inventory batches URL, Upload mode (Per scan / Per document), Recording sync URL, Document upload URL. Saving with a server URL and no environment goes through the "No environment chosen" dialog (§A.5.2). "Load configuration" dialog (3 options, see §A.5.2). `BackHandler` + top-bar back both route through `attemptExit()` → shows "Save changes?" only if the buffered form differs (structural equality) from `initial`.

Since 3.6.46 its pieces live beside it: `ExtSystemConfigComponents.kt` (`ConfigSectionHeader`, `ConfigCard`, `ConfigField`, `ConfigDivider`, `ConfigSegmentedChoice` for session duration / filter by / upload mode, `DocTypeConfigCard`) and `ExtSystemConfigDialogs.kt` (testing, connection result, missing environment). The "Load configuration" flow is `ConfigLoadFlow` in `ConfigLoadDialogs.kt`, shared with Settings since 3.6.44; the "which system?" prompt on saving is its `EnvironmentPickDialog`.

### `LocationRcPickScreen.kt`
Two picker rows (RC, then Location filtered to that RC) opening bottom sheets (`RcPickerSheet`/`LocationPickerSheet`, also reused by `DownloadFilterScreen`). Refresh icon triggers `onRefresh` directly if credentials exist, else opens `LoginSheet` first. Selecting a different RC clears the selected location; selecting a location auto-syncs its owning RC.

### `LoginSheet.kt`
Full-screen `Dialog` with a `AppTopBar` back arrow (not a bottom sheet, see §B.7's Login flow note), username/password fields (visibility toggle), footer line naming the credential TTL, submit enabled only when both non-blank; only username is `.trim()`'d, password is sent as-typed. If `onTestConnection` is supplied, submit blocks on a live NAV auth check first (spinner + inline error on failure) before calling `onSubmit`.

**Keyboard handling.** The dialog owns its own `Window`, which `MainActivity`'s `enableEdgeToEdge()` never reaches — so the sheet sets `WindowCompat.setDecorFitsSystemWindows(window, false)` and adjust-resize soft-input mode itself. Without both, the `Modifier.imePadding()` on the form's scrolling column can't see this window's keyboard and the password field sits behind the IME (the original MC3300 report). `SOFT_INPUT_ADJUST_RESIZE` is deprecated as of API 30, and its documented replacement is that same `setDecorFitsSystemWindows(false)` — but below API 30 `WindowInsets.ime` only reports the keyboard while the window is in adjust-resize mode, and the MC3300 is API 27. **Do not "clean up" the deprecation**: removing the flag reinstates the bug on the target device. It stays until `minSdk` reaches 30, isolated in a `private fun Window.resizeForSoftInput()` that carries the `@Suppress("DEPRECATION")`, so the suppression covers that one line rather than the whole composable.

**QR sign-in** (2026-09): both scanners fill the fields, through one shared `applyScannedQr`. The **hardware trigger** works with no button at all — the sheet registers the DataWedge intent receiver for its lifetime, the same pattern `RecordingScreen` uses; DataWedge already routes scans to the whole app (`ACTIVITY_LIST "*"`), so only a listener was missing. No other screen that hosts this sheet registers that receiver, so there is no double delivery. The **camera** button opens the shared `CameraPreview` (one read closes it), and is hidden entirely when `FEATURE_CAMERA_ANY` is absent — the MC3300 ships in camera-less configurations where the button used to open a black overlay with nothing behind it.

Either path fills only, never auto-submits, so the operator sees what was captured and the usual test-on-submit still runs. Parsing lives in `data/auth/LoginQrPayload.kt`:

**Scan-engine caveat**: QR needs a 2D imager. The TC21/TC22 (SE4710 or SE55) and the MC3300's SE4750/SE4770 options decode it; the MC3300's SE965 is a 1D laser and cannot, whatever the profile says. `DataWedgeManager.configure` now enables `decoder_qrcode` explicitly so the feature doesn't depend on how a device was left configured, but on a 1D unit the fallback is the camera, or typing.

The code is **encrypted** (2026-09), so a QR reader — or a photo of the code — yields nothing but base64. Base64 over:

```
[ 12-byte IV ][ AES-256-GCM ciphertext + 16-byte tag ]
```

wrapping this plaintext:

```json
{"username":"PRIMA-COMMERCE\\filip","password":"…"}
```

JSON rather than a delimited string because a Windows password may contain the very characters a delimiter would reserve (`|`, `:`, `,`); splitting on one would truncate such a password into something that merely looks like a wrong password at sign-in. The domain rides inside `username` exactly as if typed, so all the forms in §B.6.2 work. GCM authenticates as well as encrypts, so a code made under a different key fails outright instead of yielding garbage credentials that would fail confusingly against NAV. A payload that can't be read — a foreign code, a wrong-key code, or the camera catching an ordinary product barcode — sets the same inline error line the failed-sign-in path uses; the causes are deliberately not distinguished in the UI, since that distinction only helps someone probing the codes. Nothing about the payload is logged.

**The key** is `ExtSystemConfig.loginQrKey` (base64, 32 bytes) — part of the external system configuration, so it arrives the same way endpoints do: bundled in `assets/ext_system_defaults_*.json`, or imported from a file. That choice buys rotation **without a new APK**: change the key in a configuration, redistribute it, reissue the codes. The tradeoff is that the bundled copy is committed, so retired keys stay in git history; the practical harm is bounded, since an old key only opens codes that rotation has already taken out of service.

**The key never leaves the app through the export.** An exported file lands in shared storage and then travels by mail and chat; that is precisely how the one secret protecting printed codes ends up somewhere it cannot be recalled. `exportConfigurationJson` omits the field and verifies the key is not in the output before writing (§B.6.6). The counterpart rule is on import: a configuration with no key leaves the device's existing key alone, so a stripped file remains a usable template and does not quietly turn QR sign-in off. Devices get the key from the bundled assets or from a file prepared deliberately for a rotation — never from a copy someone exported. Note this narrows the *export* path only; the key is still in the APK and in git, so it stays obfuscation-grade (see below).

Because it lives in the config, `LoginSheet` takes it as a parameter and all five call sites thread it through. Note `ExtSystemConfigScreen` has no field for it but must still carry it through `buildConfig()`/`applyConfig()` — that screen rebuilds the whole config from local state, so omitting it would silently wipe the key every time those settings were saved.

`tools/make_login_qr.py` generates codes (`--new-key` mints a key, `--out` writes a PNG at high error correction, since these get printed and scanned in warehouse lighting). It prompts for the password rather than taking it as an argument, keeping it out of shell history and the process list. With `--out`, the PNG carries a caption under the code: the `--user` value unless `--name` gives another, and none for `--name ""`. It refuses a user name with two backslashes in a row, as the page does: that is how an escaped example reads when it is copied from a source file, and NAV knows no such user.

**The caption, in both tools,** is not part of the code; the device never sees it. It is drawn below the quiet zone, never into it, because those four light modules around the code are what a scanner uses to find its edge. It is centred and no wider than the code. Text starts at 4 modules high and may take two lines, then shrinks to 2 modules, and only then breaks inside a word. In both tools the code region of a captioned image was checked pixel for pixel against the same code without one.

`tools/login_qr.html` does the same job without Python: one self-contained page, opened by double-click in Chrome or Edge, that takes the key, user name and password and draws a printable code. Encryption is WebCrypto AES-GCM with a fresh random IV per code, so two codes for the same credentials never look alike, and the page decrypts its own output before showing it. The QR encoder is inline (byte mode, ECC H, versions 1–20) because the page loads nothing from outside: a Content-Security-Policy of `default-src 'none'` forbids every network request, so a password typed there cannot leave the machine even if the page is altered to try. It was checked module-for-module against `python-qrcode` (all 20 versions × 8 masks identical) and its output decrypted by a script that follows `LoginQrPayload` step by step. The password stays in its field after a code is made, so a misread or reprint does not mean typing it again; Očisti empties the user name, the display name, the password and the code.

"Ime za prikaz" is the caption. It follows the user name as it is typed until it is edited by hand, and a blank field means no caption. Code and caption are drawn into the one canvas that is shown, printed and saved, so all three are the same picture; editing the caption redraws it without touching the code. Changing the key, the user name or the password takes a finished code away, because it no longer matches the fields; it has to be made again. A new user name after a code has been made also makes the caption follow the user name again. Without that, a code made for the next operator would be saved under the previous operator's hand-typed name. The user name actually inside the code is shown under the picture on screen, not in print. WebCrypto needs a secure context; a page opened from disk qualifies in Chrome and Edge, but an embedded viewer that rewrites it to a `data:` URL does not, and the page then says so instead of failing silently.

**Many codes** ("Više kodova" tab). Rows come from a CSV file or from cells pasted out of Excel: user name, password, display name. "Preuzmi predložak" saves an empty template (UTF-8 with BOM, semicolons, as Croatian Excel expects). The delimiter is detected (tab, then `;`, then `,`), and so is the encoding: strict UTF-8 first, then Windows-1250, which is how Croatian Excel saves "CSV" unless told otherwise. Quoted fields may hold the delimiter, quotes or line breaks, and a password's edge spaces are kept. A header row is dropped. A blank display name is derived from the user name: no domain, split at `.` `_` `-`, words capitalised. Letters the user name never had (č, ć…) cannot come back. Before any code is made each row is checked: user name, password, no doubled backslash, and no user listed twice (the later row is skipped). The table shows the outcome per row, never a password. Every code is made exactly as a single one, through the same `drawCode`. The passwords, the pasted text and the loaded file are dropped as soon as the codes exist, and changing the key drops the codes too. Printing puts **two codes per A4 page**, top and bottom half, each 7 cm wide like a single printed code, with a dashed cut line between. "Spremi ZIP" saves every PNG in one archive, written by the page itself (stored, UTF-8 names, CRC-32) because it may load no library. Two domains sharing a bare name get `ana.png` and `ana_2.png`. Checked in the browser: per-row validation, both encodings, quoted CSV, the page layout, and the ZIP walked entry by entry with every CRC matching.

**What this protects against.** The key is in the APK's bundled defaults and on every provisioned device, so this defeats someone photographing or glancing at a printed code — the realistic exposure for a code taped to a device or left on a desk — but not someone who unpacks the app or reads a device's settings. It is obfuscation-grade, not a reason to treat printed codes as safe. Stronger options were considered and deferred: a per-device keypair in Android Keystore (private key non-exportable, so unpacking the APK gains nothing — but it binds each code to one device, and these are per-user), and a short-lived token redeemed against NAV (the only variant that is genuinely revocable, but it needs a NAV-side change). Either remains open if the threat model tightens.

### `RecordingScreen.kt` — the core scanning workflow (most complex screen)
Internal state machine, `RecordingView` enum: `OVERVIEW, ACTIVE_LINE, KEYPAD` — `OVERVIEW` is the line list, `ACTIVE_LINE` shows one line's detail with +1/-1 steppers, `KEYPAD` is manual quantity entry for the active line. `handleScan` — see §B.9.3. Registers the DataWedge broadcast receiver via `DisposableEffect`. `handleBack()` — per-view back navigation (KEYPAD/ACTIVE_LINE back to OVERVIEW, OVERVIEW back to the caller). Sub-composables: `OverviewContent`, `ItemQtyDetails` (ACTIVE_LINE), `ItemQtyExtraDetails` (KEYPAD — the name is a holdover from an earlier iteration where it was shared with a since-removed flow; it's an ordinary quantity-entry composable, nothing to do with extra lines), private `StatusChip` (status pill shown in the top bar).

**Finding the line a scan landed on.** A tone and, on completion, a haptic were the whole acknowledgement of a scan; neither says *which* of thirty lines moved, and that line is routinely off screen. `handleScan` now records `highlightedLineNo` plus a `highlightTick` counter, and a `LaunchedEffect(highlightTick)` scrolls `overviewListState` to it and runs an `Animatable` from 1 to 0 over `HIGHLIGHT_HOLD_MS` (5 s) plus `HIGHLIGHT_FADE_MS` (700 ms). `BigNumberLineRow` reads that as `alpha = 0.10f + 0.32f * highlight`.

Four decisions worth keeping:

- **A tick, not the line number.** Re-scanning the same line does not change `highlightedLineNo`, so keying the effect on that alone would leave repeat scans of one item — the common case — unacknowledged.
- **Scrolls only when the line is not already visible** (`layoutInfo.visibleItemsInfo`). Jumping the list under a line the operator is already looking at is a jolt that buys nothing.
- **Scrolls only in `OVERVIEW`.** The other two views do not compose the list, so there is nothing to scroll. The mark is still set, so returning within the hold still shows it.
- **Deepens the status tint rather than adding a colour.** The four-state language is the screen's whole vocabulary. It also means the fade lands on the status the line has *now*: a scan that completes a line fades into green, not back into orange.

`highlightAlpha` is passed to `OverviewContent` as a lambda, not a `Float`, so reading it inside the `items` block invalidates one row per frame instead of the whole list.

**On a stock count** (`doc.type.isStockCount`):
- **A scan that matches no line** sets `notOnDocumentScan` and shows "Not on the batch" with **Count it** / **Cancel**, instead of "Barcode not found". **Count it** calls `onScanNotOnDocument` → `RecordingViewModel.recordScanNotOnDocument` (§A.3).
- **A line added on the device** shows "Not on the batch" in place of the item name, in the list and in the detail view. It sorts first, because its `lineNo` is negative.
- **A `BARCODE|UOM|QTY` label** that matches no line exactly lands on the line whose barcode is the part before the first `|` (§B.9.3).
- **`warnsOnOver(line)`** keeps the over-scan dialog off a line added on the device and off a line expecting 0 or less.
- **A `LaunchedEffect`** returns to `OVERVIEW` when the active line disappears, which a line added on the device does once it is set to 0.

### `AppInfo.kt` — `AppInfoCard` and `AppInfoScreen`
One block, shown in two places: Settings → System info, and `AppInfoScreen` at the sign-in gate (§B.7). Rows: app version and build, Android and API, SDK (min/target, with the Android release each maps to), database version, ERP environment (`ExtSystemConfig.environment`, "Nije postavljeno" when null) and company. The database row reads `DB_VERSION`, the constant `@Database(version = …)` itself uses — it used to show its own constant, "7.0.0", that nobody updated while the schema went to 19. The company is `ExtSystemConfig.companyName`, read from `Company('…')` in the document lines URL (percent-decoded, OData's doubled apostrophe undone) rather than stored separately: it is the company the device actually addresses, and a stored name could disagree with the URL. That is how the test configurations of Mebel, Mobilis and Pohištvo were found still pointing at Prima Commerce (fixed 2026-10). `SettingsIcon` and `SettingsDivider` are `internal` in `SettingsScreen.kt` so the card can share them.

### `SignInScreen.kt`
The gate itself — see §B.7 for why it exists and what it may open. Lists the operators the device knows (tap to fill the name), takes name and password, and signs in by QR from the hardware trigger or, on a device without a scanner, the camera (`parseLoginQr`, the same as `LoginSheet`). Two outlined buttons below: "Postava vanjskog sustava" (`onOpenConfig`) and "O aplikaciji" (`onOpenAppInfo`).

### `SettingsScreen.kt`
Buffered-edit-then-confirm-on-exit pattern (identical to `ExtSystemConfigScreen`'s): every field is local `remember` state; `attemptExit()` compares the rebuilt `AppSettings` (plus `pendingExtSystemConfig != null`) against `initial`; only diverges → "Save changes?" dialog. Sections: Appearance, Scanning, Sync, External System Configuration (single row → `ExtSystemConfigScreen`), Debug (Debugger active, Export data, **Insert system defaults** [3-option picker, stages into `pendingExtSystemConfig`, only persisted via Settings' own save], **Clear cache** [red, wipes the signed-in operator's credentials + documents + recordings; leaves settings, the device’s ERP config, and every other operator alone], **Delete all documents and recordings** [red, wipes only documents/recordings, not settings/sign-in]), System Info (read-only version/schema info), Account (avatar/name, immediate Sign out — no confirmation). The System info section is `AppInfoCard` (§`AppInfo.kt`), shared with the sign-in screen.

Since 3.6.45 the rows come from `SettingsComponents.kt`: `SettingsNavRow` (chevron rows), `SettingsDangerRow` (the two red ones), `SettingsToggleRow`, `SettingsChoiceChip` (text size, debounce), `SettingsRowHeader`/`SettingsRowText`, `SettingsSectionHeader`, `SettingsIcon`, `SettingsDivider` (the last two also used by `AppInfoCard`), and `SettingsConfirmDialog` for clear cache and delete all. "Insert system defaults" opens the shared `ConfigLoadFlow`.

### `ProfilesScreen.kt`
Route `profiles`, reached from Settings → Account → "Operators on this device". Lists every enrolled profile; the signed-in one is marked and carries **no** delete affordance.

Deleting an operator removes four separate things, and `AppViewModel.deleteProfile` does them **data first, enrolment last**: `DatabaseProvider.deleteProfileData` (closes the handle, drops it from the `open` map, then `context.deleteDatabase`, which also takes the `-wal`/`-shm` files), `ExtSystemCredentialStore.clear(id)`, `AppSettingsStore.deletePersonal(id)`, and finally `UserProfileStore.delete(id)`. That order matters: a failure part-way leaves the profile still listed and the removal repeatable, where the reverse would leave a database file nothing can name or reach.

**The signed-in profile cannot be deleted**, and that is enforced three times — the row draws no button, `deleteProfile` returns early, and both `deleteProfileData` and `UserProfileStore.delete` refuse independently. It is also what keeps this clear of `duringErpWork` with no extra guard: every ERP transfer writes through `provider.current()`, so a background upload can only ever be touching the one profile this cannot remove.

Before the confirmation appears, `AppViewModel.profileFootprint` opens that operator's database (`DatabaseProvider.forProfile`, which exists for this and for the deletion and nothing else) and counts documents, scans, and scans with `sentAt IS NULL`. When there are scans at all the dialog says so in its own outlined block rather than a clause, and names the unsent count separately — documents come back on the next download, those rows do not come back at all. Confirm is disabled until the count arrives, so nobody agrees to an unknown.

### `RecordingsListScreen.kt`
Route `recordings_list`. The documents behind the main menu's RECORDINGS row, one tappable row each — document number, type, total scans, and how many have reached the ERP. Holds no state and makes no decisions: membership and ordering are the caller's (`recordedDocs`), so the screen cannot disagree with the count on the row that opened it. Its empty state is reachable two ways: the row that opens it stays on the menu at zero, and an upload started elsewhere can empty the list while it is already open.

### `RecordingsTreeScreen.kt` + `RecordingsTreeViewModel`
Route `recordings/{documentNo}/{type}`, same argument shape as `recording/...`. Shows every line of the document and, indented under each, the individual scans making up its quantity — including lines with none (`Item C 0/3`), because what is *missing* is as much of the picture as what was scanned.

The scans do not come from `Document`. `Mappers.toDomain` sums recordings into each line's quantity and only the exceptional ones (orphaned, failed) survive as objects, and `observeAll()` rebuilds every document on any database change — putting the individual scans on `Document` would multiply the cost of the app's busiest path for one screen. The ViewModel `combine`s `observeDocument` with the new `observeRecordings` (a thin wrapper over `RecordingDao.observeByDoc`, already sorted by `documentLine, recordingLineNo`) into `RecordingsTreeState(document, tree: List<LineScans>)`.

Deletion, two levels:
- **One scan** — only while `sentAt IS NULL`. A sent row shows a lock instead of a bin, and tapping it explains why: the row is in the ERP, so deleting it locally changes nothing there and only makes the device forget what it has sent. `deleteQueuedRecording` → `deleteQueuedByPk`, then state recomputed from what remains.
- **All queued scans** — the 5-second hold on the summary card, inherited from the old Recordings tab. `deleteDocumentRecordings` now takes only queued rows (see §B.6), and the dialog says how many are already sent and staying.

Orphans get their own branch at the bottom rather than being left out: the screen shows a total at the top, and omitting them would make it quietly disagree with itself. They are read-only here — discarding an orphan is the decision `UploadErrorScreen` asks for explicitly.

### `UploadErrorScreen.kt`
Read-only failed-upload detail: header card, document-info card, full raw error-message card (`(document.state as DocState.UploadFailed).reason`), "Retry Upload" button.

## B.11 Navigation & App Shell

### `MainActivity.kt`
`@AndroidEntryPoint class MainActivity : AppCompatActivity()`. `onCreate`: `enableEdgeToEdge()`, `hideNavBar()` (immersive nav-bar hiding, re-applied on `onWindowFocusChanged`), `DataWedgeManager.configure(this)`, then `setContent { ... }`.

Inside `setContent`: obtains `AppViewModel` via `hiltViewModel()`, loads `initialSettings` once, holds every setting as its own `remember { mutableStateOf(...) }`, with `buildSettings()` reassembling `AppSettings` and every `on...Change` callback persisting via `appVm.saveSettings(...)`. Wraps everything in `AppTheme(textSizeOffset, uppercaseEnabled) { BarcodeApp(...) }`.

**Split in 3.6.40–3.6.42** (no change in behaviour): the shell lives in `navigation/` — `AppNavHost.kt` (`BarcodeApp`), `Routes.kt` (every route pattern and the builders for the three with arguments) and `AppDialogs.kt` (processing, download error, debugger, upload errors, blocked change of operator). The same round split `RecordingScreen.kt` into four files and moved `AppViewModel`'s configuration files, upload loop and downloads into `ConfigController`, `UploadCoordinator` and `DownloadCoordinator` (§B.11.2). The state before is tagged `pre-split`.

### `BarcodeApp` composable (internal, in `navigation/AppNavHost.kt`) — the NavHost/app shell
Creates `nav = rememberNavController()`; derives `user` from `appVm.credentials`; collects `locations`/`responsibilityCenters`/`documents`/`extSystemConfig` (all `StateFlow`, `collectAsState()`); auto-recovers stale RC/location selections; filters `documents` per document type via `DocTypeFilterMode`; builds `docTypes: List<DocTypeSummary>`; computes shift-wide counters:
```kotlin
val shiftScans  = filteredDocs.sumOf { d -> d.lines.count { it.scanned > 0 } }  // count of lines-with-progress, not total qty
val errorDocs   = filteredDocs.filter { it.state is DocState.UploadFailed }
val readyDocs   = filteredDocs.filter { it.state !is DocState.UploadFailed && it.scanStatus() == LineStatus.EXACT }
val partialDocs = filteredDocs.filter { it.state !is DocState.UploadFailed && it.scanStatus() == LineStatus.PARTIAL }
val overDocs    = filteredDocs.filter { it.state !is DocState.UploadFailed && it.scanStatus() == LineStatus.OVER }
```
`ready`/`partial`/`over` are mutually exclusive by `scanStatus()` and deliberately exclude anything already `UploadFailed` (an over-scanned-and-failed doc counts only in `errorDocs`). Manages UI-only state (`selectedDocType`, `docFilter`, `overviewFilter`, dialogs, `processingMessage` blocking-progress state, debug-URL confirmation via `launchWithDebug(urls, onCancel, action)` gated by `debuggerActive` — see §B.11.5); `exportLauncher` (`CreateDocument("application/json")`) → `appVm.exportDatabase`.

**NavHost routes** (`startDestination = "main"`):

| Route | Screen | Notes |
|---|---|---|
| `main` | `MainMenuScreen` | `onTypeTap`→`docs`, `onDocumentOverview`→`dashboard?tab=1`, `onShowErrors`→`dashboard` |
| `location_rc_pick` | `LocationRcPickScreen` | wired to `isRefreshingLocations`, `lastLocationSyncAt`, `downloadLocations()`, `saveCredentials()` |
| `ext_system_config` | `ExtSystemConfigScreen` | save/discard/loadDefaults/testConnection/importJson delegate to `AppViewModel` |
| `settings` | `SettingsScreen` | all settings + export/clearCache/deleteAllDocuments/insertSystemDefaults/signOut |
| `docs` | `DocumentListScreen` | filtered by `selectedDocType` + location/RC per `DocTypeFilterMode`; upload branches on `backgroundSync` |
| `dashboard?tab={tab}` (Int, default 0) | `DocumentOverviewScreen` | `initialTab` from nav arg |
| `filter` | `DocumentFilterScreen` | edits `docFilter`, `showDocTypeFilter=false` |
| `overview_filter` | `DocumentFilterScreen` | edits `overviewFilter`, locked source/RC from dashboard drill-down |
| `download_filter` | `DownloadFilterScreen` | fixed source/RC per filter mode; URLs via `buildDownloadUrls`; downloads via `realDownloadDocuments`; for a stock count, the batch list via `loadInventoryBatches` |
| `recording/{documentNo}/{type}` (String args) | `RecordingScreen` | route-scoped `RecordingViewModel` via `hiltViewModel()` |
| `recordings_list` | `RecordingsListScreen` | the main menu's RECORDINGS row; hands `recordedDocs` straight through |
| `profiles` | `ProfilesScreen` | Settings → Account → Operators; re-reads `profileStore.profiles()` on a refresh counter after each deletion |
| `recordings/{documentNo}/{type}` (String args) | `RecordingsTreeScreen` | route-scoped `RecordingsTreeViewModel`; reached from `recordings_list` |
| `upload_error/{documentNo}` (String arg) | `UploadErrorScreen` | retry delegates to `AppViewModel` |

**App-level overlay dialogs** (outside/after the NavHost): blocking "processing" `Dialog` (spinner + message), download-error `AlertDialog`, debug-URL confirmation `AlertDialog` ("Proceed"/"Cancel"), sync-error `AlertDialog` ("See errors" → `dashboard` / "Dismiss").

### B.11.1 `RecordingViewModel` (`@HiltViewModel`)
Route-scoped (one instance per `recording/{documentNo}/{type}` navigation entry). Wraps `DocumentRepository`, exposing `document: StateFlow<Document?>` (via `observeDocument`) plus thin suspend wrappers around the repository's scan-mutation functions (`recordScan`, `setLineScanned`) that `RecordingScreen` calls directly from its callbacks.

### B.11.2 `AppViewModel` (`@HiltViewModel`)
Since 3.6.37–3.6.39 three helpers carry the bulk, each an unscoped `@Inject` class the ViewModel delegates to under unchanged public names: **`ConfigController`** (parse, bundled defaults, export, list of bundled files), **`UploadCoordinator`** (`runUpload`, both modes; takes the config and credentials) and **`DownloadCoordinator`** (locations, inventory batches, documents and the `$filter` builder; suspend functions taking the config and credentials). The ViewModel keeps the coroutine scope, the exposed state and `duringErpWork`, the guard against changing operator mid-transfer, which now wraps each whole download call.

Constructor deps: `Context`, `DocumentRepository`, `LocationDao`, `DatabaseExporter`, `ExtSystemConfigStore` (public `val`), `ExtSystemCredentialStore` (public `val`), `ExtSystemODataClient`, `AppSettingsStore`, private `Gson`. The orchestrator wiring repository + NAV networking together — there is no separate "SyncRepository" (see §B.1).

**Exposed state** (all `StateFlow`, all safe to `collectAsState()` from any composable):
- `credentials: StateFlow<ExtSystemCredentials?>`
- `locations: StateFlow<List<Location>>`
- `responsibilityCenters: StateFlow<List<ResponsibilityCenter>>`
- `isRefreshingLocations: StateFlow<Boolean>`
- `lastLocationSyncAt: StateFlow<Instant?>`
- `documents: StateFlow<List<Document>>`
- `extSystemConfig: StateFlow<ExtSystemConfig>` — converted from a plain getter to a real `StateFlow` in 2026-08 (see §B.13); all internal (non-composable) reads inside `AppViewModel` itself use `.value`, all composable reads go through `.collectAsState()`.
- `erpWorkInFlight: StateFlow<Boolean>` — true while any ERP transfer is running, in either direction. Backed by a **count**, not a flag, because a background upload and a download can overlap and whichever finished first would lower a shared boolean while the other was still writing. Every ERP path goes through the private `duringErpWork { }` wrapper, which raises and lowers it in a `finally`. `MainActivity.requestSignOut()` blocks on it: changing operator swaps the database underneath a transfer that is still running, and a download in particular only resolves `provider.current()` *after* the server answers — by which point sign-out may already have released it, which throws inside a coroutine and takes the process down.

**Key public functions**: `downloadLocations`, `buildDownloadUrls`, `getLocationsUrl`/`getRecordingSyncUrl`, `realDownloadDocuments`, `loadSettings`/`saveSettings`, `saveExtSystemConfig`, `saveCredentials` (returns `CredentialSave`; stores nothing unless the typed account is the signed-in operator’s — see §B.6.2), `signOut`, `testExtSystemConnection`, `parseExtSystemConfigJson` / `loadExtSystemDefaults` (both return `DeviceConfiguration`), `exportConfigurationJson`, `listExtSystemDefaultsCompanies` (scans bundled `ext_system_defaults_*.json` assets for the "Load built-in defaults" company picker — see §B.6.6), `uploadToExtSystem`/`uploadInBackground` (both call private `runUpload`), `exportDatabase`, `clearCache` (wipes the signed-in operator’s credentials, documents and recordings — nothing device-wide, nobody else’s), `deleteAllDocuments` (wipes only documents/recordings via `repository.clearAll()`), `clearDocumentRecordings`, `clearErrorDocs`.

There is no manual/offline document-creation path — a scanned or typed document number that doesn't exist locally is simply reported as not found (see §A.3); `AppViewModel` has no `createDocument`-shaped function.

### B.11.3 `Language` (`ui/theme/Language.kt`)
```kotlin
enum class Language(val tag: String, val label: String) {
    ENGLISH("en", "English"), CROATIAN("hr", "Croatian"),
    SLOVENIAN("sl", "Slovenian"), MACEDONIAN("mk", "Macedonian"),
}
```
Matching `values-hr/`, `values-sl/`, `values-mk/` string resource folders (all complete as of 2026-08) plus `res/xml/locales_config.xml` (required for the Android 13+ per-app-language system picker, referenced via `android:localeConfig` in the manifest).

**Runtime switching** (`MainActivity.applySettings()`):
```kotlin
if (language != s.language) {
    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(s.language.tag))
}
```
That is the Settings-save path, only when the language actually changed. On API 33+ this delegates to the platform `LocaleManager` (persisted by the OS); on older APIs AppCompat applies it to activities as they're created and recreates the current one to re-resolve string resources.

**Text built outside a screen goes through `inAppLanguage()`** (`i18n/AppLanguage.kt`). Below API 33 the per-app language reaches activities only; the application context keeps the device's language. `AppViewModel` and `ExtSystemODataClient` have nothing but the application context, so every message they built came out in the device's language: on an MC3300 (API 27) left in English, Croatian screens with English errors. `inAppLanguage()` returns a configuration context carrying `AppCompatDelegate.getApplicationLocales()`; `AppViewModel.inAppText()` and the client's `text()` resolve through it. From API 33 it changes nothing. A composable keeps using `stringResource`, whose activity context is already right.

**Plurals** are `<plurals>` resources, not `%d` strings: Croatian has three forms (1 sat, 24 sata, 48 sati) and Slovenian four. `login_ttl_hours` was the first; it printed "24 sati" under the default setting. The older `_single`/`_plural` string pairs (`upload_error_line_single` and friends) predate this and only cover two forms.

**On sign-in**, the `LaunchedEffect(signedInProfile?.id)` applies the operator's language by comparing it with the locale actually in effect (`AppCompatDelegate.getApplicationLocales().toLanguageTags()`), not with the `language` state. That state starts from the signed-out defaults, so comparing with it let an operator whose language equals the default — anyone who never chose one — inherit the previous operator's locale while their own Settings named another.

**Defaults** live once, in `AppSettings`: Croatian, `TextSize.LARGER`, 200 ms debounce. `AppSettingsStore.get()` falls back to `AppSettings()` for any key not stored, so an operator who has saved settings keeps them and a new one starts from these.

**Re-applied on cold start** (fixed 2026-09) by `BarcodeApplication.applySavedLanguage()`. This is required, not belt-and-braces: below API 33 `AppCompatDelegate` persists *nothing* unless the app declares `AppLocalesMetadataHolderService` with `autoStoreLocales` (this app does not), so the chosen locale used to survive only for the life of the process. The symptom was a language selection silently reverting after any restart, most visibly after an APK upgrade — `AppSettings.language` (plain SharedPreferences, survives upgrades) still said Croatian, so the Settings screen showed Croatian while every string rendered English. On the Zebra MC3300 (API 27) this hit on every launch.

The startup pass is deliberately narrow — it only fills a gap, never overrides:

| Startup state | Action |
|---|---|
| A locale already in effect | leave alone, including one set from Android's own per-app language settings on API 33+ |
| No locale in effect | apply `AppSettingsStore.get().language` — the stored one, or the default, Croatian |

**From API 33 the startup pass talks to the framework's `LocaleManager` directly** (fixed 2026-11), reached through the application context. Below 33 it still goes through `AppCompatDelegate`.
- **Why not AppCompat there.** On API 33+ AppCompat (1.7.0) forwards both `getApplicationLocales` and `setApplicationLocales` to a `LocaleManager` it finds through a live activity's delegate (`getLocaleManagerForApplication`). In `Application.onCreate` no activity exists yet, so both calls silently did nothing.
- **What it looked like.** A fresh install on a TC21 or TC22 showed sign-in, external system setup and About in the device's language, usually English. It switched to Croatian only after sign-in, when `MainActivity` set the operator's language with an activity alive. From then on the OS remembered the choice, so every later launch was right. The MC3300 (API 27) never showed it, because there AppCompat keeps the locale in memory without needing an activity.

Nothing stored used to mean "leave it to the device locale", which needed a separate `savedLanguageOrNull()` to tell "never chose" from "chose English". With Croatian as the default that distinction is gone on purpose: these handhelds usually ship in English, and the app is meant to start in Croatian regardless. Adopting `autoStoreLocales` instead would also fix the original bug, but would put a second persistence mechanism alongside `AppSettings.language`; keeping the app's own store authoritative avoids the two disagreeing.

### B.11.4 `DocTypeFilterMode` — full effect trace
Configured per document type in `ExtSystemConfigScreen` (persists **immediately**, not buffered like the rest of that screen — writes straight through `onDocTypeFiltersChange` → `appVm.saveSettings`). Consumed in `MainActivity.kt` for main-menu filtering and the `docs` route's `typeDocs`:
```kotlin
when (docTypeFilters[doc.type.key] ?: doc.type.defaultFilterMode) {
    LOCATION -> location != null && doc.sourceCode == location.code
    RESPONSIBILITY_CENTER -> rc != null && doc.rcCode == rc.code
}
```
`location` and `rc` are the *resolved* selections: null when nothing is selected, and equally null when the selected code is no longer on the fetched list.

**A type cannot be opened without its selection.** The doc-type `blocked` flag is `location == null` for a location-scoped type and `rc == null` for a centre-scoped one. A blocked type stays tappable, and the tap shows the existing "choose a location first" dialog. The operator then goes to `LocationRcPickScreen` as usual; there is no shortcut button.

The flag used to test the *list* instead (`locations.isEmpty()` / `rcs.isEmpty()`, and only when a locations URL was configured). Once any operator had refreshed locations, the next could open every list with nothing selected. Location-scoped types then showed nothing, and their download went out with no `Source_No` clause and fetched that type for every location: documents that landed on no list. A centre-scoped type showed every complaint in the company. The `download_filter` route repeats the check before sending anything and answers with the same text, as a second line behind the menu. The "My Location" tab of `DocumentOverviewScreen` follows the same rule: a blank code matches nothing. It used to match documents whose own code was blank.
**No exemption for work already started.** This used to be OR'd with `doc.hasProgress`, so a document scanned into at one location bypassed the filter for good: move from CS165 to CS175 and CS165's documents were still listed, which made the filter meaningless as soon as a shift got going. The escape existed because in-progress work was otherwise unreachable from another location — the RECORDINGS section removed that reason, and `DocumentOverviewScreen`'s `atLocation` had never had the escape anyway, so dropping it made the rest of the app agree with the dashboard.

Note the asymmetry that remains: LOCATION mode requires an explicit match (no location selected ⇒ nothing shows); RESPONSIBILITY_CENTER mode is permissive when nothing is selected (no RC selected ⇒ everything shows). Also drives which of Source/RC is fixed vs shown as a picker in `DownloadFilterScreen`, and gates `canCreateDoc` in the `docs` route.

### B.11.5 Debug mode (`debuggerActive`)
Central gate, `MainActivity.launchWithDebug(urls, onCancel = {}, action)`:
```kotlin
if (debuggerActive && urls.isNotEmpty()) {
    debugUrls = urls; pendingAction = action; pendingCancel = onCancel; showDebugDialog = true
} else action()
```
Wired at every network entry point: locations refresh, ext-system test-connection, all upload call sites (doc list, dashboard, recording screen, upload-error retry), and document download (`DownloadFilterScreen.onConfirm`, URLs formatted `"$type: $url"` per doc type).

### B.11.6 Settings' buffered-edit pattern, precisely
Both `SettingsScreen` and `ExtSystemConfigScreen` share the identical shape:
- All fields are local `remember { mutableStateOf(initial.X) }` — no persistence during editing.
- `BackHandler` and the top-bar back button both call `attemptExit()`.
- `attemptExit()`: `if (buildX() != initial [|| pendingExtSystemConfig != null for Settings]) showExitDialog = true else onDiscard()` — structural (data class) equality check.
- Exit dialog "Yes" → `onSave(buildX())` [+ `pendingExtSystemConfig?.let { onSaveExtSystemConfig(it) }` for Settings] → in `MainActivity`, Settings' `onSave` is wired to `applySettings` (bulk-copies every field back into `MainActivity`'s top-level `remember` state, triggers locale change if needed, then `appVm.saveSettings`) followed by `nav.popBackStack()`.
- Exit dialog "No" → `onDiscard()` = `nav.popBackStack()`, nothing written.
- No changes at all → back-navigates silently, no dialog.

**"Insert system defaults" persistence timing** — the three load/download/import actions only ever set local `pendingExtSystemConfig`; the row label appends `" (pending)"` while non-null. It is **only** actually saved when Settings' own exit-save flow commits (`pendingExtSystemConfig?.let { onSaveExtSystemConfig(it) }` → `AppViewModel.saveExtSystemConfig` → persists to `ExtSystemConfigStore` + updates the `extSystemConfig` StateFlow). Discarding the screen drops it with zero persistence.

## B.12 Network Security Config
`app/src/main/res/xml/network_security_config.xml` sets `<base-config cleartextTrafficPermitted="true" />` (referenced from the manifest) — required because the ERP is served over plain `http://` on the local LAN. **Do not remove this** unless the ERP endpoint is moved behind TLS.

What it does and does not cover: from Android 9 (API 28) the platform refuses an app's own plain-HTTP connections unless this file allows them, so without it the TC21/TC22 (Android 13/14) could not reach NAV at all; the MC3300 (Android 8.1) allows HTTP regardless. It concerns only this app's traffic, not the device's. A `domain-config` limiting cleartext to the NAV host was considered and left out (2026-11): the address differs per company and environment and is configurable, while that list is fixed at build time, so every new server would need a new APK. HTTPS on the NAV service tier is not possible in the current network, so the protection belongs to the network: a separate Wi-Fi or VLAN for the scanners, with WPA2-Enterprise. NTLMv2 never sends the password, but a captured exchange can be attacked offline, so passwords should not be weak.

## B.13 Recent Codebase Audit Findings (2026-08) — Applied

A full source audit was performed for dead code, missing wiring, and correctness gaps. The following were found and fixed:

| Finding | Fix |
|---|---|
| `RecordingDao.observeByLine()` — zero callers | Removed |
| `DocumentRepository.observeDocuments(sourceCode, rcCode)` — zero callers, superseded by `observeAll()` + client-side filtering | Removed (and the now-orphaned `DocumentHeaderDao.observeHeaders`) |
| `DocumentRepository.undoLastScan(...)` — fully correct but zero UI wiring; abandoned feature | Removed |
| `Int.flooredAtZero()` (`Status.kt`) — dead, referenced the same abandoned undo-scan feature | Removed |
| `DocumentRepository.clearByType(type)` — zero callers, unconditional delete with no recordings-safety check (contradicted the "never delete recordings" principle) | Removed (and the now-orphaned `DocumentHeaderDao.deleteAllByType`) |
| `DocumentRepository.getUploadableDocs()` — zero callers | Removed |
| `DocumentRepository.recordScan()` didn't call `regressFromCompletedIfNeeded()` unlike its siblings — an over-scan on an already-`Completed` document left `docState` incorrectly stuck at `Completed` | Fixed: now calls it, matching `setLineScanned`/`addExtraLine`/`updateExtraLineQuantity` |
| `AppViewModel.extSystemConfig` was a plain getter over a `MutableStateFlow`, not itself a `StateFlow` — Compose composables reading it directly weren't tracked by the snapshot system, and only "worked" via incidental recomposition from unrelated state | Converted to a real `StateFlow<ExtSystemConfig>`; all composable call sites now `collectAsState()`, all internal ViewModel reads use `.value` |
| Geist/GeistMono typefaces were bundled (`res/font/`, ~40 TTFs) but never wired — `Type.kt` used `FontFamily.Default`/`Monospace` behind a `TODO` | Wired up (`Font(R.font.geist_*, ...)`) — likely root cause of earlier recurring backspace-glyph mojibake rendering bugs |
| Mojibake em-dashes in two `AppViewModel.kt` doc comments | Fixed |
| Slovenian (`values-sl`) and Macedonian (`values-mk`) string resources missing — `Language` enum offered them but they silently fell back to English | Added, full 234-string translations |

This list is kept here so future contributors don't have to re-derive it from git history.

### 2026-08 — "not-on-document / extra line" feature removed end-to-end

The offline-before-download scanning workflow described in earlier drafts of this doc (a placeholder document, "extra"/not-on-document recordings, the barcode-waterfall reattribution algorithm on merge, `askQtyForUnknownBarcode`/`warnNotOnDocument`/`autoUploadCompleted` settings, the `UNKNOWN_BARCODE`/`EXTRA_LINE`/`EXTRA_KEYPAD` `RecordingView` states) was fully removed from the app — confirmed via a full-codebase grep sweep to have left zero orphaned fields, enum entries, or string resources behind. `handleScan` on an unmatched barcode now does exactly one thing: shows a "Barcode not found" error and records nothing (§B.9.3). This changelog wasn't updated at the time the feature was removed, so Parts A and B of this doc had drifted significantly out of sync with the actual source for a while — the whole doc was corrected against current source in the same pass that produced the next entry below.

### 2026-08 — dead-code sweep

A systematic sweep for orphaned files/functions/fields found and removed:
- **`ui/component/Chip.kt`** (whole file — `Chip()`, `StatusChip()`, `ChipTone`) and **`ui/theme/PrimaTheme.kt`** (whole file — the dead theme duplicate, see §B.14) — zero references anywhere.
- `RecordingDao.getLastForLine()`, `DocumentHeaderDao.upsertAll()`, `Mappers.kt`'s `Location.toEntity()`/`ResponsibilityCenter.toEntity()` (their `toDomain()` counterparts are used, just not this direction) — zero call sites.
- `Document.isSourceRetail` was flagged as write-only (set on every merge, never read) but deliberately **kept**, not removed — see §B.17.
- This document (`TECHNICAL_GUIDE.md`) and `USER_GUIDE.md` were both corrected in the same pass — see the entry above for what had drifted.
- Follow-up: 31 unused string resource keys removed from all 4 locale files (see §B.16) after a full audit, not just the sampled subset first found during the sweep.

## B.14 Design System / Theme

### `Color.kt` — `AppPalette` (brand palette)

| Token | Hex |
|---|---|
| Slate | `#2E3539` |
| SlateAlt | `#3A4146` |
| Cream | `#F2EBDE` |
| CreamAlt | `#E7DECF` |
| Coral | `#C95B4D` |
| CoralDeep | `#B04638` |
| Teal | `#2F5455` |
| Mustard | `#B89A3A` |
| Pink | `#E89AAA` |
| Oak | `#B49880` |
| Ink / Ink2 / Ink3 / Ink4 | `#1A1C1F` / `#3D4046` / `#6F7378` / `#A4A6AC` (darkest → lightest text hierarchy) |

`AppStatus` — the four-state semantic language (see §A.4), in two shades since 3.6.31:
- **Fill** (bars, dots, progress, tints; ≥ 3:1 on Cream and White): `Empty #CE3A3A`, `Partial #AC8032` (was `#C7943A`, 2.3:1 on Cream), `Exact #2E8C5E`, `Over #2D6CE0`, each with a ~10–12%-alpha `*Bg` tint. Read via `LineStatus.color`.
- **Ink** (counters, status text, solid badges with white text; ≥ 4.5:1 on the state's tint and with white): `EmptyInk #B33232`, `PartialInk #816026`, `ExactInk #25704B`, `OverInk #275DC2`. Read via `LineStatus.inkColor`. On the dark chrome keep the fill: the dashboard's Partial pill uses `PartialOnDark #C7943A`.

**Every other colour is a token too** (3.6.30): `AppSignal` (Danger = the Empty red, Warning = the Partial ink, `WarningOnDark`, tints and borders), `AppLine` (dividers, card borders, outlines, input borders on light surfaces), `AppOnDark` (the same on Slate) and `AppCamera`. There is no `Color(0x…)` literal left in a screen or component; the six near-duplicates merged on the way are listed at the top of `Color.kt`.

**Touch targets** (3.6.29): top bar buttons 60 × 48 dp (`TOP_BAR_BUTTON_HEIGHT`) in a 52 dp bar; the date chips' clear icon (14 dp) sits in a 48 dp `minimumInteractiveComponentSize()`.

**Rolling the look back.** The state before these three changes is the git tag `pre-design-tokens`, and each change is one commit (`6db6b2d` sizes, `50cb6a2` colour tokens, `e210655` status shades), so any one of them can be undone with `git revert` alone.

`AppLightColors` (Material3 `lightColorScheme`) is the **only** scheme actually applied: primary=Coral, secondary=Slate, tertiary=Teal, background=Cream, surface=White, error=`AppStatus.Empty`. **`AppDarkColors` is defined in `Color.kt` but never referenced anywhere else in the codebase — dead code.** The app always renders light, regardless of system dark-mode setting.

### `Type.kt` — Typography
`Geist`/`GeistMono` `FontFamily`s (wired to bundled `res/font/` TTFs as of the 2026-08 audit, see §B.13). `LocalTextSizeOffset` (Settings → Text size, adds an sp offset app-wide) and `LocalUppercaseEnabled` (Settings → Uppercase text, drives the `.uppercased` string extension used pervasively on labels/tabs/buttons/status chips). `TextSize` enum: `NORMAL(+2sp)`, `LARGER(+4sp)`. `monoCounter`/`monoLabel` composable vals are the two GeistMono styles used for quantity counters and codes/timestamps respectively.

### `Shape.kt` — `AppShapes`
`extraSmall=4dp`, `small=8dp`, `medium=12dp`, `large=14dp`, `extraLarge=22dp` (all `RoundedCornerShape`).

### Theme entry point
**`Theme.kt` → `AppTheme(textSizeOffset, uppercaseEnabled, content)`** — the only theme composable, wired into `MainActivity`; provides `LocalTextSizeOffset`+`LocalUppercaseEnabled`, then `MaterialTheme(colorScheme=AppLightColors, typography=scaledTypography(...), shapes=AppShapes)`.

(Until 2026-08 there was a second, dead `PrimaTheme.kt → PrimaTheme(textSizeOffset, content)` — an older, unreferenced duplicate. Removed in a dead-code sweep; if you're looking at git history from before that and see two theme files, that's why.)

## B.15 Build & Project Configuration

### `app/build.gradle.kts`
- `namespace`/`applicationId`: `com.erpbarcode.scanner`. `compileSdk` 36, `minSdk` 26, `targetSdk` 36. Java/Kotlin target `VERSION_11`. `versionCode`/`versionName` are bumped on every commit to this project (see `app/build.gradle.kts` for the current value — don't hardcode it here, it's stale the moment it's written).
- Only the `release` build type is customized (`isMinifyEnabled = false`); no `debug` overrides, no product flavors.
- `buildFeatures`: `compose = true`, `buildConfig = true`. KSP arg `room.schemaLocation = "$projectDir/schemas"` (Room schema JSON exports are committed to the repo per convention — include them in commits when the schema changes).
- Notable dependency facts:
  - **Ktor's `ktor-client-content-negotiation`, `ktor-serialization-kotlinx-json`, and `ktor-client-auth` are declared but unused** — confirmed via source grep (no `ContentNegotiation` plugin install, no `io.ktor...auth` imports anywhere). All JSON is Gson; all auth is the hand-rolled `NtlmAuthenticator`. Don't assume Ktor's request/response bodies are JSON-typed anywhere — they're always raw strings hand-parsed with Gson.
  - ML Kit `barcode-scanning:17.3.0` has an in-source comment flagging an unaligned `.so` — update when a newer release fixes it.
  - `androidx.security:security-crypto:1.1.0-alpha06` — still on an alpha release; watch for a stable release when upgrading.

### `settings.gradle.kts`
Single module `:app`. Repositories: `google()`, `mavenCentral()`, `gradlePluginPortal()`. `rootProject.name = "ErpBarcodeScanner"` (`PrimaBarcode` until 3.6.52).

### `build.gradle.kts` (root) — built-in Kotlin and why a `buildscript` block exists
AGP 9 compiles Kotlin itself, so there is **no `org.jetbrains.kotlin.android` plugin** in this project, and adding one back would break the build — that plugin is incompatible with AGP 9's DSL. The consequence is that nothing in the `plugins` block declares the Kotlin version: AGP pins KGP to its own runtime dependency (2.2.10 for AGP 9.x) unless a newer one is put on the buildscript classpath, which is [the documented way](https://developer.android.com/r/tools/built-in-kotlin) to raise it. Hence:

```kotlin
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21")
    }
}
```

This exists for KSP's sake (2026-09). Only KSP 2.3.1+ registers its generated sources through `android.sourceSets`; every older KSP uses the `kotlin.sourceSets` DSL that AGP 9 rejects, which is what previously forced `android.disallowKotlinSourceSets=false` into `gradle.properties` — see [google/ksp#2729](https://github.com/google/ksp/issues/2729). The KSP 2.3 line serves the Kotlin 2.3 line, so KGP had to move with it, and that property is now gone.

Two things to keep straight when touching versions here: the classpath version is a **literal because the version catalog is not visible inside a `buildscript` block**, and it must stay equal to `kotlin` in the catalog, which pins the Compose compiler plugin. And KSP is *not* on this classpath — it is resolved through the `plugins` DSL alias, deliberately, since requesting a version for a plugin already on the buildscript classpath is an error.

### `gradle/libs.versions.toml`
Only Compose/AndroidX/test/Hilt libraries and the 4 Gradle plugins are catalog aliases (`agp=9.2.1`, `hilt=2.60.1`, `kotlin=2.3.21`, `ksp=2.3.12`, `composeBom=2026.02.01`, etc.). Room/Ktor/CameraX/ML Kit/Gson/Timber/security-crypto/appcompat are plain string coordinates directly in `app/build.gradle.kts`, not catalog entries — keep this in mind when bumping versions.

Note the KSP version format changed with the 2.3 line: older releases encoded the Kotlin version (`2.2.10-2.0.2`), current ones are plain KSP versions (`2.3.12`) covering a whole Kotlin minor. KSP 2.3.12 embeds the 2.3.20 compiler while the project builds with 2.3.21; that gap is deliberate on KSP's side, but if KSP ever complains about the compiler version, dropping `kotlin` to `2.3.20` (in both places above) is the fix.

### `AndroidManifest.xml`
- Permissions: `INTERNET`, `CAMERA`, `VIBRATE`. `<uses-feature android:name="android.hardware.camera" android:required="false" />` — camera is optional; a device can run on the hardware wedge scanner alone.
- `networkSecurityConfig="@xml/network_security_config"` (cleartext HTTP allowed — required for the LAN-hosted ERP; see §B.12). `localeConfig="@xml/locales_config"` (four languages, see §B.11.3).
- Only one component: `MainActivity` (`MAIN`/`LAUNCHER`). No services/providers declared — the DataWedge `BroadcastReceiver` is registered dynamically in code (`RecordingScreen`'s `DisposableEffect`), not in the manifest.

## B.16 String Resources — Leftovers Worth Knowing About

A 2026-08 localization audit found every screen had been using `stringResource()` correctly for a while, *except* a substantial number of `Text()`/`Toast`/`contentDescription`/`ctaLabel` call sites that had hardcoded English literals directly — meaning those strings had never even become resource keys, so they'd never had a chance to be translated. That pass added the missing keys (with real hr/sl/mk translations, not copies of the English text) and fixed every call site found at the time. If you're adding new UI text, always add it as a `stringResource()` key in all 4 locale files from the start rather than a literal — see the memory note this rule is tracked under for the project's assistant.

Separately, a full audit (2026-08, same pass) checked every one of the then-247 keys in `res/values/strings.xml` (+ `values-hr`/`values-sl`/`values-mk`, kept in sync, see §B.11.3) against `R.string.*`/`@string/*` usage across all Kotlin source, the manifest, and `res/xml/`. 31 had zero references anywhere — remnants of earlier iterations of the login/settings/multiplier flows, an unused `doc_state_new/active/done/failed` status vocabulary, all 5 `doctype_*_desc` keys, and a handful of others (`btn_save`, `cd_settings`, `doc_qty_scanned`, `settings_change`, etc. — surprising ones were re-verified individually before removal, not just trusted from the automated sweep). All 31 were removed from all 4 locale files, leaving 216. If you're removing a key yourself later, confirm via grep for the exact key first — new UI could always reuse one of these names.

## B.17 Extension Points & Notes for Future Developers

- **Adding a new document type**: add an enum value to `DocumentType` (Models.kt) with a `key`/`display`, plus `retailLocation` if it shares a `Document_Type` code with another type and `defaultFilterMode` if location isn't the right scope; add it to the four bundled `ext_system_defaults_*.json` — `documentTypeCodes` **and** `docTypeFilters`, both keyed by the enum name; add a localized name in all four `strings.xml`. It will automatically appear in `MainMenuScreen`'s type list and all downstream screens, since they iterate `DocumentType.entries` — but note the compiler will also stop you in seven exhaustive `when` blocks that must each get a branch: six duplicated `localizedDisplay()` helpers (one per screen) and `MainMenuScreen`'s icon mapping. That the compiler catches every one of them is the reason the duplication is survivable.
- **`COMPLAINT_SHIPMENT` and `COMPLAINT_RECEIPT`** (2026-11, codes `REKOTPR` and `REKZAPR`) replaced the single `COMPLAINT` type. Both are scoped by responsibility centre and carry their own code, so neither filters on `Retail_Location`. Because no old complaint document could be told apart as shipment or receipt, and none were being scanned yet, `MIGRATION_19_20` deletes the `COMPLAINT` rows. `ExtSystemConfigStore` falls back to the two codes when a device has none stored (the code field is read-only), so a device configured before the split needs no reload. The NAV side is `PRICO-2026-43`, §B.18.
- **`COMPLAINT` (since replaced) and `INVENTORY`** (2026-09) were added this way. Both carry their own `Document_Type` code, so neither filters on `Retail_Location`. `INVENTORY` counts against quantities NAV supplies (`Qty. (Calculated)`), with two differences flagged by `DocumentType.isStockCount`: the download picks one inventory journal batch first (§B.6), and an item NAV didn't send is added on the device rather than rejected (§A.3).
- **Adding a new AppSettings field**: add to the `AppSettings` data class + `AppSettingsStore` serialization, add local `remember` state + callback wiring in `MainActivity.kt`, and a control in `SettingsScreen.kt`. Remember the buffered-edit pattern — don't persist directly from the toggle, let it flow through `buildSettings()`/exit-save.
- **The upload payload is intentionally flat and per-row**, not batched — if ERP-side batching is ever desired, `AppViewModel.runUpload`'s row loop is the place to change, but note the per-row-immediate-delete behavior is what makes partial-failure retries safe; batching would need an equivalent all-or-nothing safety guarantee.
- **`ErpResponsibilityCenterRow` DTO** is defined but unused (RCs are derived from Location rows) — if the ERP ever exposes a genuine RC list with richer fields (e.g. using the `short` field), this DTO is ready to wire into `AppViewModel.realDownloadLocations` (or a new `realDownloadResponsibilityCenters`).
- **`Document.isSourceRetail`** (`documentHeader.isSourceRetail`) — written on every download/merge from NAV's `Retail_Location` flag, and since 2026-09 it is what an uploaded recording sends back in `Retail_Location` (§A.5.4). Note the two sides use different sources on purpose: the *download filter* comes from `DocumentType.retailLocation` (the bucket being fetched), the *upload value* from `isSourceRetail` (what NAV said about that specific document). They agree whenever NAV populates the field. The one case where they don't is a document downloaded before `Retail_Location` was exposed on the OData page — `isSourceRetail` was stored `false` then, including for retail documents, and re-downloading is what refreshes it.

## B.18 The NAV side — C/AL objects in `NAV/`

The app's half of the integration is the OData pages it calls; the other half is C/AL in NAV 2018. `NAV/` holds text exports of those objects, as the user exported them from C/SIDE. Nothing in this repository can compile or run them: the user imports them back into C/SIDE.

### Files and objects

| File | Objects |
|---|---|
| `barcode_scanning.txt` | Tables 50111 Scanned Document Lines, 50112 Scanned Document Lines Archive, 50113 Scanned Document Lines Buffer, 50180 Barcode App Recordings; codeunits 50106 BarcodeMgmt, 50107 BEMF Mgmt, **50112 Barcode App Mgmt**; pages 50108–50110 (the three scanned-lines tables) |
| `pages_barcode.txt` | The document pages that start scanning work or change its status: 5768 Warehouse Receipt, 7332 Warehouse Receipts, 7335 Warehouse Shipment, 7339 Warehouse Shipment List, 50031–50034 Complaint (lists and cards), 50040 Transport Sheet, 50042 Transport Sheet List, 50055 Receiving Complaint Shpts., 50145 Retail Warehouse Receipt, 50146 Retail Warehouse Shipment |
| `transport.txt` | Tables 50015 Transport Sheet Header, 50016 Transport Sheet Line; codeunit 50093 Transport Sheet Mgmt; pages 50040–50042 |

Pages 50040 and 50042 are in both `pages_barcode.txt` and `transport.txt`; the two copies have to stay identical. Not exported, and therefore not visible here: the OData pages published over 50179/50180 and the location list, the Warehouse Shipment/Receipt header tables (7320/7316), and table 50161 (the sync records).

The later work lives beside this repository, in `../PRIMA NAV/`, also CP852 with CRLF:

| File | What it is |
|---|---|
| `REK-OTPR-ZAPR.txt` | Complaint shipment and receipt (`PRICO-2026-43`): 29 objects, among them tables 50111–50113, 50179 and 50180 and codeunit 50112 in their current form |
| `INVENTURA-BARKOD.txt` | Inventory (`PRICO-2026-43`): tables 91, 233, 50111, 50179, codeunits 241, 50112 and the new 50116, page 392. Imported **after** `REK-OTPR-ZAPR.txt`, whose versions of 91, 50111, 50179 and 50112 it builds on |
| `inventura.txt` | The unmodified export of 233, 241 and 392 that `INVENTURA-BARKOD.txt` was made from |
| `BARKOD-UNAPREDJENJA.txt` | The follow-ups to the system analysis (2026-11): tables 83 and 233, codeunit 50112, page 392 — built from the user's fresh export `NAV/barkod_new.txt`, so it carries what was changed directly in NAV — plus the new XMLport 50008, codeunit 50117 and page 50208. Imported **after** `INVENTURA-BARKOD.txt`. See *Follow-ups (2026-11)* at the end of this section |
| `complaint.txt`, `complaint_BCKP.txt` | The unmodified export `REK-OTPR-ZAPR.txt` was made from |

For anything in 50112, 50179 or 50111, the newest of these files is the current version, not `NAV/barcode_scanning.txt`.

### Editing rules

- **Encoding is CP852 with CRLF.** Read through `iconv -f CP852 -t UTF-8` into a scratch copy. Write back only through a script that decodes and encodes `cp852` and asserts a lossless round trip first. A UTF-8 editor corrupts every Croatian character in the file.
- **Every change is marked with its request's tag** — `PRICO-2025-67` for the scanner-app work, `PRICO-2026-43` for complaint shipment and receipt and for inventory — and **old code is never deleted**. NAV keeps no history of its own here, so the comment is the history:
  - A single replaced line is kept above the new one as `//`.
  - Two or more replaced lines go inside `{ }` at their original indentation, then the new code, then the closing tag.
  - A purely added line may carry a trailing `// <PRICO-2025-67/>`.
  - A variable declaration cannot hold a comment: the old one stays, and the new variable gets a free ID.

### How a scan travels

1. **Release** of a warehouse shipment (subscriber on codeunit 7310 `OnBeforeRelease`) or receipt (codeunit 50127) creates the document's rows in **Barcode App Entry (50179)** through 50112's `CreateBarcodeAppEntries` / `Fill*BarcodeEntries`. Transport sheets and complaint shipments have their own `Fill*` procedures. An inventory is sent by hand, with **Send for Scanning** on page 392 (`FillPhysInventoryBarcodeEntries`). **Reopen** deletes them again (`DeleteBarcodeEntries`), and refuses while the document's Barcode Status is Processed. 50179 is what the app downloads (§A.5.3).
2. **Upload:** every recording the app sends is one row in **Barcode App Recordings (50180)**. Its primary key is `Recording Guid`, so a repeated GUID is refused while the first copy is still there (§A.5.4). After processing it is accepted again but not counted twice, because processing skips a GUID already in 50111. `Barcode` is `Text30`.
3. **Processing:** `CreateScanningEntriesOnDocument` (50112, called from page 50110, the document cards and the bulk status updates) does the following:
   - `FindScannedEntries` moves the document's rows from 50180 into **Scanned Document Lines (50111)** and deletes them from 50180, then deletes the document's 50179 rows. An inventory keeps its 50179 rows until the count is finished. `InsertScannedEntries` writes a 50111 row only if none with the same `uuid` (the recording's GUID) exists for that document.
   - It loads the scans and lays the document's lines into an in-memory buffer. It allocates exact-quantity scans first, then the rest. Whatever no line can take becomes surplus lines, including items not on the document at all.
   - It writes the result once to **Scanned Document Lines Buffer (50113)**, the comparison page 50110 shows.
   - Last, `ChangeDocumentStatus` sets the document's Barcode Status. A completed document's 50179 rows are deleted. An inventory writes `Qty. (Phys. Inventory)` to its journal instead and keeps its status (§B.18, Inventory).
4. **Job queue:** 50112's `OnRun` runs `ProcessSyncedDocuments` (`PRICO-2026-43`). It collects the documents synced more than five minutes ago and runs each through the new codeunit **50116 Barcode App Sync Document** with `IF NOT CODEUNIT.RUN`, committing before each one.
   - **A failing document no longer stops the rest.** Its changes since its last commit are rolled back, its sync records stay unprocessed so the next run tries it again, and the other documents go on. Before this, the whole run was one `[TryFunction]`, and a document that kept failing was first in line on every run, so nothing after it was ever processed again.
   - **50116 marks the document's sync records processed** only once the document has gone through.
   - **The job queue entry must run 50112.** `AutoUpdateBarcodeStatusOnSync` is kept, unchanged, because 50107's `OnRun` still reaches it through its own copy. An entry on 50107 therefore still uses the old path, without the isolation.
5. **Transport sheets** are scanned at the *receiving* location against `Qty. to Receive`. Each posted partial receipt starts a new round: `StartNextTransportReceipt` archives the scans to 50112 and resets the Barcode Status.

### What the app's `Document_Line_No` means

It is the **Barcode App Entry (50179) line number**, not the warehouse document's line, and `0` for a line the device added during a stock count. NAV reads it for no type: matching goes by barcode to item. That is just as well, because 50179 rows are deleted once recordings are processed, and an inventory rebuilds them every time it is sent again. A `BARCODE|UOM|QTY` scan is resolved to its item on the NAV side too: older 50111 rows written before 2026-10-06 may have a blank `Item No.`, which `LoadScans` resolves when it reads them.

### Document type codes

The app sends `ExtSystemConfig.documentTypeCodes`; the bundled configurations use `SHIPMENT`, `RECEIPT`, `TRANSPORT`, `REKOTPR`, `REKZAPR` and `INVENTORY`. `REKOTPR` and `REKZAPR` are option values in 50111–50113 and in 50112's procedures, added under `PRICO-2026-43` (below). `INVENTORY` is the option `Inventory`, which was there from the start; a code has to match an option name exactly, because `FindScannedEntries` filters 50180 by `UPPERCASE(FORMAT(option))`. Retail and warehouse documents share `SHIPMENT`/`RECEIPT` and are told apart by `Retail_Location` (§A.5.4). NAV's own option list in 50112 also carries the cross-dock types (`WHCDSHIP`, `WHCDREC`, `WHCDSHFROM`, `CPCDSHIP`, `CPCDREC`, `CPCDSHFROM`), `Prereceipt` and `RetailShpt`/`RetailRcpt`.

### Complaint shipment and receipt (`REKOTPR` / `REKZAPR`, `PRICO-2026-43`)

These two types are done in NAV under the tag **`PRICO-2026-43`**, in `PRIMA NAV/REK-OTPR-ZAPR.txt`, a sibling folder of this repository rather than `NAV/`. That file is the whole of `PRIMA NAV/complaint.txt` (28 objects) plus one new page, so it can be compared object by object with the original. Changed: tables 50021 Complaint Shipment Header Prim, 50111, 50112 and 50113; codeunit 50112; pages 50033, 50034, 50055 and 50108. New: page **50206 Receiving Complaint Shpt. Card**, opened from 50055. The other 19 objects are unchanged.

- **One document, two phases.** Both types are the same Complaint Shipment (50021), told apart by its `Status`. While it is **Released**, it is a complaint *shipment* (`REKOTPR`). Once it is **Shipped** and not yet received (`Shipment Status` blank), it is a complaint *receipt* (`REKZAPR`). `ComplaintShptStatusChanged`, called from the Status field, keeps Barcode App Entry in step:
  - Released fills the `REKOTPR` rows.
  - Shipped deletes them, clears the Barcode Status and fills the `REKZAPR` rows.
  - Back to Released from Shipped deletes the `REKZAPR` rows and starts the receipt over.
  - Open deletes both, and refuses while the shipment's scanning is Processed.
- **What the app downloads.** `FillComplaintShipmentBarcodeEntries` writes one 50179 row per complaint on the shipment (50022 lines):
  - `Item No.` and `Barcode`: the complaint number. The package label carries it, so scanning a package is scanning that number.
  - `Item Qty.`: the complaint's `Package Quantity`; `Scanning Qty.` is 1, so one scan is one package.
  - `Item Description`: customer, shipment date, store and responsibility centre.
  - `Source No.` and `Responsibility Center`: the store's responsibility centre, which is what the app's RC scope filters on. `Destination No.`: the store. `Document Date`: the shipment date.
  - Not tied to a location, which is why both types are scoped by responsibility centre in the app.
- **Cross-dock responsibility centres keep their own flow** (`CPCDSHIP`/`CPCDREC`/`CPCDSHFROM`). `IsCrossDockRespCenter` excludes them everywhere above, and a Barcode Status cannot be set on them.
- **New field 50030 Barcode Status** on 50021: blank, Synchronized, Processed.
  - An upload sets Synchronized (`CheckDocumentStatus`). It refuses a `REKOTPR` row unless the shipment is Released, and a `REKZAPR` row unless it is Shipped and not yet received. Either is refused once the status is Processed.
  - Processing goes through `ChangeComplaintShipmentBarcodeStatus`. There is no "synchronized with discrepancies" as on a warehouse shipment: the status becomes Processed only when **every package of every complaint** has been scanned (every buffer line complete), and otherwise stays Synchronized.
  - Set by hand, it needs the user's **Scanned Barcodes Admin** (User Setup), and Processed only when the scan is complete (`IsComplaintScanComplete`, the same comparison without changing the status).
- **No partial receipt.** Receiving on page 50206 (`CheckComplaintShptReceivable`) requires the Barcode Status to be Processed. If a package did not arrive, a new complaint is opened for it.
- **Processing paths:** the job queue (`ProcessSyncedDocuments`), "Update Barcode Status" on cards 50034 and 50206 and on list 50055 (`BulkComplaintSheetStatusUpdate`), and opening card 50034 (for `REKOTPR`) or 50206 (for `REKZAPR`).
- **The legacy call** `BEMFMgmt.FindScannedEntries` (marked `//temporary` in 50112) is skipped for both types. 50107 has no case for them, and their scans are read from 50180 anyway.

### Inventory (`INVENTORY`, `PRICO-2026-43`)

Done in NAV under **`PRICO-2026-43`**, the same tag as the complaint types, in `PRIMA NAV/INVENTURA-BARKOD.txt`.

**Import order and what the file leaves out.**
- **The file holds eight objects:** tables 91, 233, 50111 and 50179, codeunits 241, 50112 and the new 50116, page 392.
- **Import it after `REK-OTPR-ZAPR.txt`, never instead of it.** Tables 91, 50111 and 50179 and codeunit 50112 are taken from `REK-OTPR-ZAPR.txt` with its changes included.
- **`inventura.txt` beside it** is the unmodified export of 233, 241 and 392.
- **Two things are added by hand in NAV**, because their objects were not exported:
  - the two new 50179 fields on the page published as `BarcodeAppEntries`. Their controls must be named after the fields, so OData calls them `Journal_Template_Name` and `Journal_Batch_Name`. Until they are there, the app's batch list fails;
  - the new User Setup field on page 119.

**One count per batch.**
- **Sending.** A Phys. Inventory batch is sent from page 392 with **Send for Scanning**. It refuses a batch that is not a Phys. Inventory template, or whose lines do not all share one `Document No.` and one location.
- **Why one location.** Processing matches scans to lines by item only, and 50111 does not record where a scan was made, so two locations in one batch would take each other's counts.
- **Sending always asks first**, and the last two cases default to No:
  - the first time: send the batch's lines (with the document no.) to the barcode devices?
  - already sent with the same document no.: are you sure you want to send it again?
  - already sent with a different one, which has no scans yet: are you sure you want to send the new document in its place?
- **Sending again** while the count is open only refreshes the entries. The devices keep their scans.

**The batch remembers its count.** Table 233 has two new fields:
- **Barcode Status**: blank / Synchronized / Processed, the same options as on a warehouse shipment.
- **Barcode Document No.**: the `Document No.` that was sent. Every procedure finds the journal from the document no. alone through it (`FindPhysInventoryBatch`).

Batch names are reused for every count, while the document no. from the batch's number series is new each time. That is why the app keys a count by the document no.

**What the app downloads.** One 50179 row per journal line. A set (BOM parent) is counted either by its own label or by its packages, never both, so it is sent as both: a row for the set itself and one per component (its packages).
- **`Line No.`** is a running number, as for every type. Processing finds the journal line by the item, never by this number, and NAV reads `Document_Line_No` from 50180 for no type.
- **Rows with the same barcode and item** are one row with the quantities added.

| 50179 field | Value |
|---|---|
| `Document Type` | `INVENTORY` |
| `Document No.` | the journal's `Document No.` |
| `Source No.` | the location, which the app's location scope filters on |
| `Destination No.` | the batch name, which the app shows next to the location |
| `Responsibility Center` | the location's centre (Responsibility Center Location) |
| `Document Date` | the posting date |
| `Item Qty.` | `Qty. (Calculated)`, times `Quantity per` for a component. Negative stock is sent as it is |
| `Barcode` | `GetItemBarcode`. For a unit with **Insert Qty Barcode** it is still the plain barcode: a quantity label on the shelf prints whatever it holds, so the app matches it on the part before the first `|` |
| `Scanning Qty.` | 1 for a quantity-label unit, whose label carries the quantity; otherwise the component's or the item's `Package Qty.`; otherwise 1 |
| **new** `Journal Template Name` (140), `Journal Batch Name` (150) | from the journal line |

**Upload.** `CheckDocumentStatus` accepts an `INVENTORY` row while a batch carries that `Barcode Document No.` and is not Processed, and sets it to Synchronized. Before this an `INVENTORY` row was refused as "Krivi tip".

**Processing.** It runs from the job queue after every sync, or from **Update from Barcode** on page 392. It is `CreateScanningEntriesOnDocument`, with these differences:
- **The 50179 rows stay.** `FindScannedEntries` does not delete them for an inventory, so the batch stays on the devices' download list however often it is processed.
- **The buffer comes from the journal.** 50113, shown by **Scanned Lines**, is built from the journal lines (`CreateInventoryJournalLines`), with the usual allocation and surplus lines. A set whose own barcode was scanned is shown as one line, otherwise by its packages.
- **The journal gets the counted quantity.** Instead of a status change, `UpdatePhysInventoryQty` writes `Qty. (Phys. Inventory)` on every line:
  - It is everything found for the line's item on all devices. Unlike the buffer, it is not capped at the calculated quantity.
  - It is recomputed every time, so processing twice changes nothing.
  - A line nobody scanned becomes 0.
  - A BOM parent gets what was scanned with its own barcode plus the number of complete sets made of its packages, and the scarcest package decides. The two are added because a set is counted one way or the other, never both. Packages that make no complete set are not written.
  - Each item's total is found through the key `Document Type,Document No.,Item No.` on the in-memory scan table, so a lookup is a search rather than a pass over every scan. This matters on a large count, where it runs for every line on every processing pass.
  - Quantities are taken once (`ConsumeScannedQty`), so two lines of one item, or two sets sharing a package, never count the same scan twice.
- **An item not on the count gets a journal line of its own** (`AddPhysInventoryLine`):
  - same posting date, document no. and location as the count;
  - `Qty. (Calculated)` is the item's `Net Change` at the location up to the posting date, which is what Calculate Inventory would have written;
  - a 50179 row with the barcode actually scanned is added, so a device that added the item itself moves its scans onto that row.

  Only a stock item of its own is added. These are not: an unknown barcode, a blocked or non-inventory item, and an item that is a component of any BOM. A component is a package of a set, and stock is kept on the set.
- **The status stays Synchronized.** An inventory has no "complete" state.

**Finishing.**
- **Finish Count** on page 392 needs the new User Setup field **Inventory Administrator** (50121). It runs a last processing pass, then asks for confirmation in two cases:
  - nothing was scanned at all. The quantities are then left as they are;
  - scanned units could not be written: unknown barcodes, incomplete sets, items not added.

  It then sets the batch to Processed and deletes its 50179 rows. The batch leaves the devices, and later uploads are refused.
- **Reopen Count**, also for an Inventory Administrator, goes back to Synchronized and sends the entries again.

**Posting.**
- **Codeunit 241** and page 392's **Post and Print** refuse a batch that was sent for scanning and is not Processed (`CheckPhysInventoryCanBePosted`).
- **After posting**, once the counted lines are gone, the scans are archived (50111 to 50112), the buffer and the 50179 rows are cleared, and the batch is reset to blank for the next count (`ClearFinishedPhysInventoryCount`).
- **A count whose lines are gone is over, however that happened.** Posting from the batch list goes around codeunit 241, and lines can be deleted by hand. So the same clearing also runs when the batch is sent again and before posting, whenever the remembered document no. has no lines left. It never runs from the job queue: lines deleted and calculated again under the same document no. are back before anyone sends or posts, and that count simply goes on with its scans.
- **Deleting a batch** removes its 50179 rows.

**Known limits.**
- Quantities are not converted between units of measure. A scan counts in the journal line's unit, as on every other type.
- An item listed twice in one BOM, and a component that is also a journal line of its own, are not handled specially.
- **Table 50111 has a new key, `Document Type,Document No.,uuid`.** Moving a scan from 50180 to 50111 (`InsertScannedEntries`) first checks whether its GUID is already there, once per scan and for every type. Through this key the check goes straight to the GUID instead of reading all of the document's scans, which on an inventory are many thousands. Importing the table builds the index on SQL, which takes a while on a large 50111.

### Follow-ups (2026-11, `PRIMA NAV/BARKOD-UNAPREDJENJA.txt`)

Scanner-app changes are tagged `PRICO-2025-67`, inventory changes `PRICO-2026-43`. Built from `NAV/barkod_new.txt`, which showed that NAV did not yet have the send confirmations from 3.6.24: they are in this file again.

**After importing, publish two web services** (Web Services page): page **50208** as `BarcodeInventoryBatches` and codeunit **50117** as `BarcodeAppUpload`. Until the first is there, the app's batch list fails; the second is only called in Per document mode.

**Upload in one call** (codeunit 50117 Barcode App Upload, XMLport 50008). `UploadDocument(VAR recordings : XMLport 50008) : Integer` imports the rows into a temporary 50180 (`GetRecordings`), refuses an upload that mixes documents, and inserts each row with `INSERT(TRUE)`, so the 50180 subscribers (status check, sync record) run exactly as for OData. The web service call is one transaction: the first refused row rolls back the rest. A GUID already in 50180 is skipped; one already processed into 50111 is skipped later by `InsertScannedEntries`, as before. `User ID` and `Creation DateTime` are set from the session. The XMLport was written by hand without an exported example to copy, so it is the object most likely to need a touch in C/SIDE on import.

**List of batches for the devices** (page 50208 over table 233). New batch fields: **Barcode Location Code** (50006) and **Barcode Resp. Center** (50007), written by **Send for Scanning** and cleared with the count; FlowFields **Barcode Posting Date** (50008, Min over the count's lines) and **Barcode Line Count** (50009, the number shown in the app's picker). The page lists batches with a `Barcode Document No.` that are not Processed. A batch sent before this import has no location yet and appears in the app once it is sent again (sending again only refreshes the entries).

**A quantity typed in by the office is kept** (table 83 fields 50121 **Barcode Counted Qty.**, 50122 **Barcode Qty. Applied**, 50123 **Manual Count Override**; page 392 columns and action **Reset to Scanned**).
- Typing `Qty. (Phys. Inventory)` on page 392 on a line of a barcode count sets Manual Count Override (`PhysInventoryQtyEnteredByHand`); setting it back to Barcode Counted Qty. clears it.
- Processing (`UpdatePhysInventoryQty`) leaves a flagged line's quantity alone and only refreshes Barcode Counted Qty. An edit made anywhere else is caught too: once processing has written a line, a quantity that differs from what it wrote counts as typed in.
- **Reset to Scanned** puts the selected lines back on the scanned figure and clears the flag. **Finish Count** asks once more when lines still carry a typed-in quantity.

**One sync record per document** (`CreateSyncRecord`). An unprocessed 50161 record for the document is moved forward (time, user) instead of a new one being inserted per scan; `LOCKTABLE` keeps two devices from colliding on it. The job queue marks processed only records up to its cutoff, so one moved forward during a run is taken by the next. Processed records older than 30 days are deleted at the end of `ProcessSyncedDocuments`.

**Commit every 500 scans** in `FindScannedEntries` instead of after each one. A scan's insert into 50111 and delete from 50180 are still in the same block.

**Job queue.** The entry runs codeunit 50112 (confirmed 2026-11), so every document is processed in its own `CODEUNIT.RUN` of 50116.

### Legacy

Codeunits **50106 BarcodeMgmt** and **50107 BEMF Mgmt** belong to the earlier BE-Mobile scanner integration. The scanning logic is being moved off them into 50112 step by step. Since 2026-10-06, 50112 has no reference to 50106. 50107's BEMF `Fill*` procedures still use it, and 50107's `Bulk*StatusUpdate` and `IsCrossDock*` remain as wrappers for callers not yet found. 50107's own `AutoUpdateBarcodeStatusOnSync` still hands over to 50112's old procedure of that name, not to `ProcessSyncedDocuments`, so a job queue entry on 50107 does not get the per-document isolation.
