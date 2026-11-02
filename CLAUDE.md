# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew assembleRelease        # Build release APK
./gradlew installDebug           # Install on connected device/emulator
./gradlew test                   # Unit tests
./gradlew connectedAndroidTest   # Instrumented tests (requires device/emulator)
./gradlew clean                  # Clean build outputs
```

Use `gradlew.bat` instead of `./gradlew` on Windows.

## Architecture

**Native Android barcode scanning app** for warehouse inventory operations backed by Dynamics NAV 2018. Kotlin + Jetpack Compose, minSdk 26 (Android 8.0+), compileSdk 36.

The app is fully wired end-to-end: Room DB + DAOs, Hilt-injected ViewModels, Ktor/NTLM networking against NAV OData, and DataWedge + CameraX/ML Kit scanning are all live — this is not a design-to-code-in-progress prototype.

### Key Stack

| Layer | Technology |
|---|---|
| UI | Jetpack Compose + Material 3 (BOM 2026.02.01) |
| Navigation | Compose Navigation 2.8.2 |
| Networking | Ktor Client 2.3.12 over OkHttp, custom NTLMv2 `Authenticator` (no external NTLM lib) |
| Local DB | Room 2.8.5 (KSP-generated DAOs) |
| DI | Hilt/Dagger 2.60.1 |
| Barcode | Zebra DataWedge hardware scanning, intent output only; ML Kit 17.3.0 + CameraX 1.4.2 **only on devices without a scanner**. Every screen registers its scan receiver through `DataWedgeManager.register`, which is **exported** on API 33+ — DataWedge is another app, and a not-exported receiver silently hears nothing from it |
| Secure storage | androidx.security:security-crypto (NAV credentials, TTL-bound) |
| Logging | Timber |

Dependency versions are centralized in `gradle/libs.versions.toml` (and inline in `app/build.gradle.kts` for libraries not yet promoted to the catalog).

### Source Layout

```
app/src/main/java/com/prima/barcode/
├── MainActivity.kt              # Entry point, NavHost, top-level app state (settings, location/RC, dialogs)
├── di/                          # Hilt modules (DatabaseModule)
└── ui/
    ├── theme/                   # Color.kt, Type.kt, Shape.kt, Theme.kt, Language.kt
    ├── component/               # Reusable Compose components (ScanBar, ScanField, CameraPreview, ...)
    ├── screen/                  # Full screens
    └── viewmodel/                # AppViewModel (app-wide state/sync), RecordingViewModel (per-document)
data/
├── model/                       # Domain models (Document, Line, DocState, ...) — data/model/Models.kt
├── db/                          # Room entities, DAOs, PrimaDatabase, Mappers (entity ↔ domain)
├── repository/                  # DocumentRepository — merge/state-machine logic over Room
├── extsystem/                   # ExtSystemODataClient, NtlmAuthenticator, NAV DTOs (ExtSystemPayload)
├── auth/                        # ExtSystemConfig(Store), ExtSystemCredentialStore (encrypted), AppSettings(Store)
├── barcode/                     # BarcodeAnalyzer (ML Kit), DataWedgeManager (Zebra profile + broadcasts)
├── haptic/                      # HapticEngine
├── sound/                       # SoundEngine — confirm beep, synthesised warning, sign-in QR rejection
└── export/                      # DatabaseExporter (JSON dump via SAF)
i18n/                            # AppLanguage.kt — Context.inAppLanguage()
```

Outside the app:

- **`tools/`** — `login_qr.html` (offline page that makes encrypted sign-in QR codes, one at a time or in batches from CSV/Excel, printed two per A4 page) and `make_login_qr.py` (the same from the command line). Both must stay byte-compatible with `LoginQrPayload.kt`.
- **`NAV/`** — C/AL text exports of the NAV 2018 side (tables, codeunit 50112 Barcode App Mgmt, pages). **CP852 with CRLF** — never edit them with a UTF-8 tool; go through a script that round-trips `cp852`. Every NAV change is wrapped in the request's tag (`// <PRICO-2025-67>` for the scanner app; `// <PRICO-2026-43>` for complaint shipment/receipt, whose export is `../PRIMA NAV/REK-OTPR-ZAPR.txt`) and old code is commented out, never deleted. They cannot be compiled here. See TECHNICAL_GUIDE §B.18.
- **`docs/`** — `TECHNICAL_GUIDE.md`, `USER_GUIDE.md` and its Croatian translation `USER_GUIDE_HR.md`. A change to one user guide goes into both.

### Screens

- **MainMenuScreen** — Document type list with counts/status bars; context strip for location/RC switching; a single RECORDINGS row opening `RecordingsListScreen`
- **RecordingsListScreen** — Every document carrying a scan, newest first; a row opens its recordings tree
- **RecordingScreen** — Core scanning interface: per-line progress, docked ScanBar, hardware wedge + camera fallback, keypad entry, over-scan/UoM-mismatch warnings. An accepted scan scrolls the list to the line it landed on (only when that line is off screen) and deepens its status tint for five seconds before fading back
- **DocumentListScreen** — Per-doc-type list with tabs, create/delete, filter, upload
- **DocumentOverviewScreen** — Cross-type dashboard (errors/ready/partial/over tabs)
- **DocumentFilterScreen** / **DownloadFilterScreen** — Filter builders for the list/overview and for NAV downloads
- **LocationRcPickScreen** — Location/RC switcher with NAV refresh
- **ExtSystemConfigScreen** — NAV endpoint URLs, doc type codes, credential TTL, per-type enable switch and scope, JSON import/export. A configuration file carries a `DeviceConfiguration`: the whole of `ExtSystemConfig` **plus** the device half of `AppSettings` (`disabledDocTypes`, `docTypeFilters`, `debuggerActive`). The personal half is deliberately excluded — it belongs to a person, not to a deployment. Export writes what is on the device now, so a hand-tuned device can be the template for its fleet
- **SettingsScreen** — Text size, language, scan behavior toggles, cache/export/sign-out, embeds ext-system config
- **SignInScreen** — The gate. Required at every app launch: picks the operator and takes their password, so every recording can carry its author from the moment it is written. Signs in by typed name/password or by QR, from the hardware trigger or the camera — both feed `parseLoginQr`, the same as `LoginSheet`, because before this screen existed that sheet *was* the way in. Its one door out is an "External system setup" button that opens `ExtSystemConfigScreen` without signing in — a device out of the box has no server URL, and sign-in needs one, so without that door a fresh install could never be configured. A second, read-only door, "About the app", opens `AppInfoScreen`
- **AppInfoScreen** (`AppInfo.kt`) — Version, Android, SDK, database version (`PRIMA_DB_VERSION`, the same constant `@Database` uses), ERP environment and company. `AppInfoCard` is the one block, also shown as Settings → System info. The company is `ExtSystemConfig.companyName`, read from `Company('…')` in the document lines URL rather than stored separately
- **LoginSheet** — Full-screen NAV credential capture (summoned on Download/Upload); QR sign-in via hardware scanner or camera
- **ProfilesScreen** — The operators this device knows, and the only way to remove one; deleting a profile takes their database, credentials and personal settings with it
- **UploadErrorScreen** — Failure detail + retry for a single document

Helpers shared between screens live in their own file as `internal`, not as a private copy per screen. `DocumentTypeLabel.kt` holds the one `@Composable internal fun DocumentType.localizedDisplay()`; seven screens each carried an identical private version of it, which meant an eighth screen could not call any of them and adding a document type meant finding all seven. (`LineStatus.localizedLabel()` is still duplicated across three screens.)

**Operator-facing text built outside a composable** — anything a ViewModel or `ExtSystemODataClient` hands to the UI, including every `ExtSystemResult.Failure.message` — resolves through `Context.inAppLanguage()` (`i18n/AppLanguage.kt`), never the bare application context: below API 33 that context keeps the device's language, not the app's, and the MC3300 is on API 27. Text that depends on a count is a `<plurals>` resource; Croatian has three forms and Slovenian four.

### Design System

**4-state status language** drives the entire UX — every line and document resolves to one of:

| State | Condition | Color |
|---|---|---|
| Empty | 0 scanned | Red `#CE3A3A` |
| Partial | 0 < scanned < expected | Orange `#C7943A` |
| Exact | scanned == expected | Green `#2E8C5E` |
| Over | scanned > expected | Blue `#2D6CE0` |

**Brand palette** — Coral (`#C95B4D`), Slate (`#2E3539`), Cream (`#F2EBDE`), Teal (`#2F5455`).

**Typography** — Geist (primary) + GeistMono (codes/counters). GeistMono is specifically used for `monoCounter` and `monoLabel` text styles.

**Shape radii** — 4 / 8 / 12 / 14 / 22 dp tokens defined in `Shape.kt`.

### Data Model

Domain models live in `data/model/Models.kt`; Room entities in `data/db/Entities.kt`; `data/db/Mappers.kt` converts between them.

- `Document` — documentNo, type, destination/source/RC codes, lines[], `orphanedScans[]`, `DocState`
- `DocState` — sealed interface: `Downloaded / InProgress / Completed / PendingUpload / UploadFailed(reason)`; transitions are computed from recordings by `DocumentRepository` (advance/regress helpers), not set directly by the UI
- `Line` — documentNo, lineNo, item, barcodeNo, expected/scanned qty, UoM; `LineStatus` (`EMPTY/PARTIAL/EXACT/OVER`) is computed from expected vs. scanned
- `OrphanedScan` — a recording whose line NAV has since removed from the document. Surfaced on `Document.orphanedScans`, computed in `Mappers.toDomain` by diffing recordings against the line rows. Blocks upload (`runUpload` refuses the document and writes `UploadFailed`) until the operator discards them from `UploadErrorScreen`. There is no "scan an item not on the document" feature — that was removed in `247d903`; these arise only after the fact, from an ERP-side edit. The one exception is a stock count (`DocumentType.isStockCount`, i.e. `INVENTORY`): there an unlisted item is confirmed and becomes a **line added on the device** (`Line.isAddedOnDevice`, negative `lineNo`, item no = barcode, expected 0), uploaded with `Document_Line_No = 0`, kept across re-downloads until NAV sends a line with that barcode
- `DocumentType` — enum: `WAREHOUSE_SHIPMENT`, `WAREHOUSE_RECEIPT`, `RETAIL_SHIPMENT`, `RETAIL_RECEIPT`, `TRANSPORT_SHEET`, `COMPLAINT_SHIPMENT` (`REKOTPR`), `COMPLAINT_RECEIPT` (`REKZAPR`), `INVENTORY`. The two complaint types replaced `COMPLAINT` in schema 20, whose migration deleted its rows; a `key` is stored in every row and is never reused. Every type starts **switched off** on a new installation (stored as `enabledDocTypes`; see `AppSettingsStore`), and the main menu then offers the External System Configuration. Each carries `retailLocation` (the download/upload discriminator, null for types with their own `Document_Type` code) and `defaultFilterMode` (the scope a type falls back to until the user picks one — both complaint types default to responsibility centre, everything else to location). `INVENTORY` is one Phys. Inventory Journal batch per document: the download picks the batch first (`AppViewModel.loadInventoryBatches`, `InventoryBatch`), keyed by the journal's `Document No.` because batch names are reused. Its NAV side is not built yet; the contract is TECHNICAL_GUIDE §B.18, Inventory
- Local persistence is recording-first: every scan writes a `RecordingEntity` row; documents/lines are freely replaced on re-download, but recordings (real user progress) are never deleted by a sync. `DocumentRepository.mergeDocument` re-attaches recordings by barcode when NAV renumbers a line; anything that still has no line becomes an `OrphanedScan`, and only an explicit operator action ever deletes it.
- **Data is per operator, by file, not by predicate.** There is one `PrimaDatabase` (`prima_<hash>.db`) per signed-in profile, opened by `DatabaseProvider`; queries are still scoped only by `(documentNo, type)`, which is safe because a query cannot reach outside its own file. Two operators may hold the same NAV document independently. `SharedDatabase` (`prima_shared.db`) keeps locations and responsibility centres device-wide. **Never hold a `PrimaDatabase` or a DAO in a field** — resolve `provider.current()` at each use, bind it once per transaction, and pass that into helpers; a captured reference writes silently into the previous operator's file.

### NAV Integration

- `ExtSystemODataClient` (Ktor over a preconfigured OkHttp client) talks to NAV's OData V4 endpoints for locations, document lines, and recording upload; auth is a hand-rolled NTLMv2 `okhttp3.Authenticator` (`NtlmAuthenticator`, includes an inline MD4 implementation — Android's `MessageDigest` has no MD4).
- Login is username+password only; the domain travels inside the username as `user@domain` or `DOMAIN\user`, split by `ExtSystemODataClient.parseDomainUser`.
- `network_security_config.xml` permits cleartext traffic globally — NAV is reached over the local LAN, not HTTPS.
- Endpoint URLs, per-doc-type codes, and credential TTL are user-configurable (`ExtSystemConfigScreen`); `assets/ext_system_defaults_*.json` seed sane defaults and can be re-imported from Settings — **two per company**, `_test` and `_prod`, each declaring its own `environment`. "Load built-in defaults" asks which system before which company.
- **`ExtSystemConfig.environment` (`TEST`/`PRODUCTION`, nullable)** is what the device remembers about which ERP it is talking to, and the main menu's context strip shows it. Null means nobody has said — deliberately not defaulted, because a device that claims one while pointing at the other is worse than one that admits it does not know. The two mistakes this guards against are both silent and both expensive: a real shipment scanned into the test company, and test work scanned into production.
- Credentials are cached per operator in `ExtSystemCredentialStore` (`EncryptedSharedPreferences`, AES-256-GCM) with a TTL; expired credentials are wiped on next read. **The ERP is the only authority on a password, every time** — there is no local fallback and no offline unlock. An unreachable server means nobody signs in, including an operator with unsent scans on the device; that is a knowing trade of availability for the rule that signing in without the external system is not a sign-in. `UserProfileStore` therefore keeps no secret of its own, only the record that the ERP has accepted each operator here.
