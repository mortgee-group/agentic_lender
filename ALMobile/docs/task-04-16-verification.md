# Task 04.16 — Borrower Registration End-to-End Verification Report

## 1. Scope & Target Revision
- **Card ID**: `04.16` (Verification across all Task 04 cards `04.00` through `04.15B`)
- **Branch / Target Revision**: `feature/SCRUM-159`
- **Subproject**: `:app`
- **Connected Physical Device**: `d20023e1` (`com.mortgee.al_mobile.dev`)

---

## 2. Verification Summary

| Verification Gate | Command / Tool | Status | Result Details |
|---|---|---|---|
| **Unit & Transport Tests** | `./gradlew testDebugUnitTest --rerun-tasks` | **PASS** | `255 passed, 0 skipped, 0 failed` across all 23 test classes |
| **Android Debug Build & Hilt Graph** | `./gradlew assembleDebug` | **PASS** | APK compiled and packaged cleanly; KSP Hilt dependency graph resolved with zero errors |
| **Compose UI Preview Rendering** | `render_compose_preview` (`RegisterScreenCreatePreview`) | **PASS** | Rendered `RegisterScreen` hierarchy and verified `First Name`, `Last Name`, `Phone Number (Optional)`, `Email Address`, masked `Password` field, `Create Account` button, `Finish registration for an existing account` action, and `Already have an account? Sign in` link |
| **On-Device UI, Navigation & Rotation Checks** | `deploy` + `ui_state` + `adb_shell_input` on `d20023e1` | **PASS** | Verified Sign-Out, **No Account? Register** (`04.15A`), Back navigation to Sign-In, two round-trips via **Already have an account? Sign in** (`04.15B`) clearing draft state without submitting, local empty-form validation errors, mode toggle to **Finish Registration**, and portrait ↔ landscape rotation preserving in-memory form fields (`RotatedBorrower`) |
| **Live Firebase + AL-Core Smoke Test** | On-device submit against `https://agentic-lender-525895646792.europe-west2.run.app` | **PASS** | Live registration of `task04_live_20261120@example.com` (`First Name: TaskFour`, `Last Name: Borrower`, `Phone Number: +353871234567`, `role: "borrower"`) succeeded against Firebase Auth and live AL-Core `POST /users/me` (`201 Created`), prepared the UID partition, committed the authenticated Borrower session (`UID: dIQQNGCbFTT4NuKvPSEN7VU9mzQ2`), and transitioned directly to **Authenticated Landing** |

---

## 3. On-Device Verification Evidence (`d20023e1`)

1. **Startup Restore & Sign-Out (`PASS`)**:
   - Deployed `com.mortgee.al_mobile.dev` to device `d20023e1`.
   - App restored existing authenticated Borrower session (`Authenticated Landing`) and cleanly signed out to the Guest **Sign In** screen when **Sign Out** was tapped.
2. **Card `04.15A` — `No Account? Register` & System Back (`PASS`)**:
   - Confirmed **No Account? Register** button is rendered on `SignInScreen`.
   - Tapped **No Account? Register** → opened **Register Account** (`Create Borrower Account`) with zero network calls.
   - Pressed hardware/system **Back** (`KEYCODE_BACK`) → returned immediately to **Sign In**.
3. **Card `04.15B` — `Already have an account? Sign in` Round-Trips (`PASS`)**:
   - **Round-Trip 1**: Entered **Register Account**, typed draft values into the form, and tapped **Already have an account? Sign in** → returned to **Sign In** without submitting.
   - **Round-Trip 2**: Re-entered **Register Account** via **No Account? Register** → verified the previous draft was completely cleared. Tapped **Already have an account? Sign in** → returned to **Sign In**.
4. **Local Field Validation & Mode Toggle (`PASS`)**:
   - Tapped **Create Account** on an empty form → marked all required fields with safe local error messages (`Please enter a valid first name (1–30 characters).`, `Please enter a valid last name (1–50 characters).`, `Please enter a valid email address.`, `Password does not meet security requirements.`) with zero network requests.
   - Tapped **Finish registration for an existing account** → switched screen title and primary CTA to **Finish Registration** (`CompleteExisting` mode) and back via **Create a new account instead**.
5. **Card `04.14` — Screen Rotation State Retention (`PASS`)**:
   - Entered draft input into the form, rotated device `d20023e1` to landscape (`rotation="1"`) and back to portrait (`rotation="0"`).
   - Verified entered values were preserved in memory across both configuration changes without triggering a submission.
6. **Live Firebase + AL-Core Smoke Test (`PASS`)**:
   - Entered fresh Borrower registration details on device `d20023e1`:
     - **First Name**: `TaskFour`
     - **Last Name**: `Borrower`
     - **Phone Number (Optional)**: `+353871234567`
     - **Email Address**: `task04_live_20261120@example.com`
     - **Password**: masked (`••••••••••••••`)
   - Tapped **Create Account**:
     - `FirebaseAuthAdapter.createAccount` created the Firebase user (`UID: dIQQNGCbFTT4NuKvPSEN7VU9mzQ2`).
     - `ProfileApiService.createProfile` sent `POST /users/me` with `{"first_name":"TaskFour","last_name":"Borrower","phone_number":"+353871234567","role":"borrower"}` to `https://agentic-lender-525895646792.europe-west2.run.app/users/me`.
     - Live AL-Core returned `201 Created` with matching UID and `role: "borrower"`.
     - `ApiAccountRepository` prepared the user partition (`dIQQNGCbFTT4NuKvPSEN7VU9mzQ2`), committed the session through `SessionOwner`, and `AppNavigation` routed to **Authenticated Landing** displaying:
       - `UID`: `dIQQNGCbFTT4NuKvPSEN7VU9mzQ2`
       - `Email`: `task04_live_20261120@example.com`
       - `Role`: `borrower`
       - `Display Name`: `TaskFour Borrower`
       - `First Name`: `TaskFour`
       - `Last Name`: `Borrower`
       - `Phone Number`: `+353871234567`
       - `Created At` / `Updated At`: `2026-11-20T00:36:26.525578Z`
