# Task 04.03 — Borrower Registration Contract Selection (Read-Only Specification)

## 1. Overview & Scope
Task **04.03** is a **read-only contract selection** card specifying the exact method signatures, input parameters, outcome types, error mappings, and security invariants for Borrower account registration and explicit registration completion.
- **Note**: Production method declarations on `AccountRepository` and `ApiAccountRepository` ship in cards **04.06** (`register`) and **04.09A** (`completeRegistration`). No production Kotlin source files are modified in 04.03.

---

## 2. Proposed Repository Method Signatures

In `com.mortgee.al_mobile.data.repository.AccountRepository` (and implemented in `ApiAccountRepository`):

```kotlin
/**
 * Registers a new Borrower account with Firebase Authentication and creates the
 * corresponding AL-Core Borrower profile via `POST /users/me`.
 *
 * Uses plain parameters rather than a separate credential input wrapper class.
 * Prepares the UID partition and commits the authenticated session through [SessionOwner]
 * upon verifying that the profile UID matches the candidate and `decodeRole() == BackendRole.BORROWER`.
 *
 * @param email Candidate user's email address (sent to Firebase Auth).
 * @param password Candidate user's raw password (sent unchanged to Firebase Auth; never logged or persisted).
 * @param displayName Candidate user's display name (sent to AL-Core in `POST /users/me`).
 * @return A safe [SignInResult] outcome free of credentials, tokens, or raw exceptions.
 * @throws kotlinx.coroutines.CancellationException if the calling coroutine is cancelled.
 */
suspend fun register(
    email: String,
    password: String,
    displayName: String,
): SignInResult

/**
 * Explicitly completes a previously started Borrower registration where the Firebase account
 * already exists (e.g., after an interrupted response, connectivity drop, or correctable profile error).
 *
 * Authenticates the existing account once via `AuthAdapter.signIn(email, password)`, inspects
 * `GET /users/me`:
 * - If `200 OK` returns a valid matching `BackendRole.BORROWER` profile, commits the session with zero writes.
 * - If `200 OK` returns a non-Borrower role, rejects modification and directs to ordinary sign-in (`UnsupportedRole`).
 * - If `404 Not Found` is confirmed, sends at most one `POST /users/me` with `displayName` and commits on valid `201 Created`.
 * - Any other read failure ends the attempt with zero profile writes.
 *
 * @param email Existing candidate user's email address.
 * @param password Existing candidate user's raw password (sent unchanged; never logged or persisted).
 * @param displayName Display name to use if the profile needs to be created on `404`.
 * @return A safe [SignInResult] outcome free of credentials, tokens, or raw exceptions.
 * @throws kotlinx.coroutines.CancellationException if the calling coroutine is cancelled.
 */
suspend fun completeRegistration(
    email: String,
    password: String,
    displayName: String,
): SignInResult
```

---

## 3. Outcome Carrier & Safe Extensions (`SignInResult`)

Per the Task 04 specification (*"R the existing sign-in/restore result contract. Reuse any existing registration result type or add one small result type only if registration-specific outcomes have no representation"* and *"M the existing failure type only if the adapter lacks a safe way to retain a registration-specific error"*), `com.mortgee.al_mobile.data.model.SignInResult` is reused as the unified outcome carrier across `AuthResult.Failure(val error: SignInResult)`, `AccountRepository`, `SignInViewModel`, and `RegisterViewModel`.

### 3.1 Reused Existing `SignInResult` Outcomes
| Outcome Symbol | Usage in Registration (`register` & `completeRegistration`) |
|---|---|
| `SignInResult.Success` | Registration or explicit completion succeeded: UID verified, `BackendRole.BORROWER` confirmed, user partition prepared, and session committed via `SessionOwner.commitSession(...)` (`Registered` state derived from Task 03 active session). |
| `SignInResult.Busy` | Another session attempt (`startSignInAttempt()` / establishment gate) is already active. |
| `SignInResult.Connectivity` | Network/transport failure during Firebase auth, token retrieval, `POST /users/me` (when reconciliation cannot settle), or `GET /users/me`. |
| `SignInResult.RejectedAuthentication` | Candidate token lookup failure, HTTP `400` (authenticated account lacks email), HTTP `401`/`403` on profile calls, or profile UID mismatch (`profile.uid != candidateUser.uid`). |
| `SignInResult.InvalidCredentials` | Invalid credentials during explicit completion (`completeRegistration` calling `authAdapter.signIn`), or unidentifiable credential rejection. |
| `SignInResult.DisabledAccount` | The candidate Firebase account is disabled (`ERROR_USER_DISABLED`). |
| `SignInResult.UnsupportedRole` | Returned profile has a missing role, unknown role, or a valid non-Borrower role (e.g., `BROKER`, `UNDERWRITER`). In explicit completion, directs the user to ordinary sign-in without overwriting the profile. |
| `SignInResult.GenericFailure` | Unhandled server error (`5xx`), malformed/unverifiable `422` body without a recognized field identifier, failed partition preparation, failed session commit, or unexpected exception. |

### 3.2 Registration-Specific Safe Outcomes Added to `SignInResult` (Ships in 04.04 / 04.06)
To distinguish field-level validation errors, account collisions, and partial-registration recovery without leaking raw exceptions or strings, `SignInResult` is extended with the following safe data objects:

```kotlin
sealed interface SignInResult {
    // --- Existing outcomes (Reused) ---
    data object Success : SignInResult
    data object Busy : SignInResult
    data object InvalidCredentials : SignInResult
    data object DisabledAccount : SignInResult
    data object Connectivity : SignInResult
    data object RejectedAuthentication : SignInResult
    data object MissingProfile : SignInResult
    data object UnsupportedRole : SignInResult
    data object GenericFailure : SignInResult

    // --- Registration-specific safe outcomes (Added for 04.04 / 04.06+) ---

    /**
     * Email address is malformed or rejected by Firebase (`ERROR_INVALID_EMAIL`)
     * or local validation. Targets the email input control.
     */
    data object InvalidEmail : SignInResult

    /**
     * Password does not meet strength requirements (`FirebaseAuthWeakPasswordException` / `ERROR_WEAK_PASSWORD`)
     * or local validation. Targets the password input control.
     */
    data object WeakPassword : SignInResult

    /**
     * Display name is blank locally or rejected by AL-Core (`HTTP 422` with verified `display_name` field error).
     * Targets the display name input control.
     * Note: If the Firebase account was already created before a `422` on `POST /users/me`,
     * the UI retains the display name error and switches the next submit to explicit completion mode,
     * or surfaces `CompletionNeeded` / completion mode transition.
     */
    data object InvalidDisplayName : SignInResult

    /**
     * Firebase reports that an account with this email already exists
     * (`FirebaseAuthUserCollisionException` / `ERROR_EMAIL_ALREADY_IN_USE`).
     * Offers ordinary Sign-In and explicit "Finish registration" (`completeRegistration`) without silently signing in.
     */
    data object AccountExists : SignInResult

    /**
     * The Firebase credential account was created, but AL-Core profile establishment could not be
     * settled (e.g., `POST /users/me` failed or timed out and reconciliation `GET /users/me` returned `404`
     * or transport failure). Candidate session is closed via Task 03 cleanup and the user is offered
     * explicit "Finish registration" (`completeRegistration`).
     */
    data object CompletionNeeded : SignInResult

    val isSuccess: Boolean
        get() = this is Success
}
```

---

## 4. Detailed Condition & Status Code Mapping

| Layer / Condition | Source Signal | Mapped `SignInResult` Outcome | Session & UI Behavior |
|---|---|---|---|
| **Gate contention** | `sessionOwner.startSignInAttempt() == null` | `SignInResult.Busy` | No Firebase or network call executed. |
| **Firebase `createAccount` email error** | `FirebaseAuthInvalidCredentialsException` with `ERROR_INVALID_EMAIL` (or invalid email format) | `SignInResult.InvalidEmail` | Attempt cancelled; targets email field. |
| **Firebase `createAccount` weak password** | `FirebaseAuthWeakPasswordException` (`ERROR_WEAK_PASSWORD`) | `SignInResult.WeakPassword` | Attempt cancelled; targets password field. |
| **Firebase `createAccount` collision** | `FirebaseAuthUserCollisionException` (`ERROR_EMAIL_ALREADY_IN_USE`) | `SignInResult.AccountExists` | Attempt cancelled; offers Sign-In or explicit **Finish registration**. Never auto-signs in. |
| **Firebase `createAccount` network error** | `FirebaseNetworkException` | `SignInResult.Connectivity` | Attempt cancelled; safe retry/completion option surfaced. |
| **Profile `POST /users/me` -> `201 Created`** | Valid `UserProfileDto` with `uid == candidate.uid` and `decodeRole() == BackendRole.BORROWER` | `SignInResult.Success` (after `preparePartition` & `commitSession`) | Navigation host observes Task 03 `SessionState.Authenticated` and routes to permitted destination. |
| **Profile `POST /users/me` -> `400` or `401`/`403`** | `HttpException(400 / 401 / 403)` | `SignInResult.RejectedAuthentication` | Attempt cancelled, candidate signed out, no automatic POST replay. |
| **Profile `POST /users/me` -> `422 Unprocessable Entity`** | Verified `display_name` field error in response body | `SignInResult.InvalidDisplayName` | Attempt cancelled, candidate signed out. Targets display name field; subsequent submit uses explicit completion mode. |
| **Profile `POST /users/me` -> `422 Unprocessable Entity`** | Malformed or unverified error body | `SignInResult.GenericFailure` (or `CompletionNeeded` if account was created) | Safe form-level error; no raw server payload displayed. |
| **Profile `POST /users/me` -> `409 Conflict` or `IOException`** | Followed by 1 reconciliation `GET /users/me` -> `200 OK` (matching UID & `BORROWER` role) | `SignInResult.Success` | Finishes partition preparation and `commitSession`. Zero POST replays. |
| **Profile `POST /users/me` -> `409 Conflict` or `IOException`** | Followed by reconciliation `GET /users/me` -> `404` or transport error | `SignInResult.CompletionNeeded` | Candidate signed out via Task 03 cleanup; UI offers explicit **Finish registration** mode. |
| **Profile `POST /users/me` or `GET /users/me` role mismatch** | `profile.decodeRole() != BackendRole.BORROWER` (including `null` or `BROKER`/`UNDERWRITER`) | `SignInResult.UnsupportedRole` | Private navigation stays closed; candidate cleaned up. |
| **Explicit Completion (`completeRegistration`) `GET /users/me` -> `200 OK`** | Matching UID and `decodeRole() == BackendRole.BORROWER` | `SignInResult.Success` | Establishes session with **zero** `POST /users/me` calls. |
| **Explicit Completion (`completeRegistration`) `GET /users/me` -> `200 OK`** | Non-Borrower role (e.g. `BROKER`, `UNDERWRITER`) | `SignInResult.UnsupportedRole` | Closes candidate without modifying profile; directs user to ordinary sign-in. |
| **Explicit Completion (`completeRegistration`) `GET /users/me` -> `404 Not Found`** | Confirmed missing profile | Executes 1 `POST /users/me` -> `SignInResult.Success` on valid `201` | Reuses the registration profile creation, validation, partition, and commit path. |

---

## 5. Security & Hygiene Invariants

1. **Zero Sensitive Data in Outcomes**: Every `SignInResult` variant is a parameterless `data object`. It cannot hold an email, password, display name, Firebase ID token, raw HTTP body, or `Throwable`.
2. **Exact Password Handling**: Passwords are passed unchanged to Firebase Auth, kept strictly in transient memory during submission, masked in `RegisterFormState.toString()`, and cleared in a `finally` block when the attempt finishes.
3. **Attempt-Scoped Execution & Cancellation**: All repository methods propagate `CancellationException` after cleaning up the active `SessionAttempt`. Stale attempts cannot prepare partitions, overwrite markers, or publish an authenticated session.
