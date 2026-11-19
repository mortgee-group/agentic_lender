# Task 04.00 — Read-only Inventory & Codebase Mapping

## 1. Executive Summary & Context
This document records the exact declaration/file paths, Hilt bindings, test fakes, wire contract evidence, and symbol inventory for **Task 04 — Borrower Registration** in the ALMobile application.

- **Subproject**: `:app`
- **Root Directory**: `/home/dim4s/work/agentic_lender/ALMobile`
- **Environment & Dependencies**: AGP 9.4.1, Kotlin 2.2.10, Java 17, Compile/Target SDK 37, Hilt 2.60.1, Retrofit 2.11.0, Moshi 1.15.2, Firebase Auth (BOM 33.10.0), Compose BOM 2026.02.01.

---

## 2. Symbol & File Path Inventory (R / M / C)

| Class Diagram Symbol | Action | Exact File Path | Existing Declaration / Search Evidence |
|---|---|---|---|
| `UserProfileDto` | **Reuse (R)** | `app/src/main/java/com/mortgee/al_mobile/data/network/model/UserProfileDto.kt` | `data class UserProfileDto(val uid: String, val email: String, val role: String?, val displayName: String?, ...)` |
| `BackendRole` | **Reuse (R)** | `app/src/main/java/com/mortgee/al_mobile/data/model/BackendRole.kt` | `enum class BackendRole(val value: String) { BORROWER("borrower"), BROKER("broker"), UNDERWRITER("underwriter") }` |
| Profile Create Request DTO | **Created (C)** | `app/src/main/java/com/mortgee/al_mobile/data/network/model/CreateProfileRequest.kt` | Implemented `CreateProfileRequest(@Json(name = "role") val role: String?, @Json(name = "first_name") val firstName: String?, @Json(name = "last_name") val lastName: String?, @Json(name = "phone_number") val phoneNumber: String?, @Json(name = "display_name") val displayName: String?)` matching backend `UserProfileCreate` model. |
| `ProfileApiService` | **Modified (M)** | `app/src/main/java/com/mortgee/al_mobile/data/network/ProfileApiService.kt` | Added `createProfile(@Header("Authorization") bearerToken: String, @Body request: CreateProfileRequest): UserProfileDto` POST `/users/me` endpoint alongside `getProfile(...)`. |
| `ApiClient` | **Reuse (R)** | `app/src/main/java/com/mortgee/al_mobile/data/network/ApiClient.kt` | `@Singleton class ApiClient @Inject constructor(val retrofit: Retrofit)` |
| `AuthAdapter` | **Modified (M)** | `app/src/main/java/com/mortgee/al_mobile/data/auth/AuthAdapter.kt` | Added `createAccount(email, password): AuthResult` alongside `signIn`, `getToken`, `signOut`, `currentUid`. |
| `FirebaseAuthAdapter` | **Modified (M)** | `app/src/main/java/com/mortgee/al_mobile/data/auth/FirebaseAuthAdapter.kt` | Implemented `createAccount` under `mutationMutex` with cancellation reconciliation and `mapCreateAccountException` mapping (`AccountExists`, `WeakPassword`, `InvalidEmail`). |
| `AuthResult` | **Reuse (R)** | `app/src/main/java/com/mortgee/al_mobile/data/auth/AuthResult.kt` | `sealed interface AuthResult` (`Success(user)`, `Failure(error)`). |
| `AuthUser` | **Reuse (R)** | `app/src/main/java/com/mortgee/al_mobile/data/auth/AuthUser.kt` | Candidate identity interface: `val uid`, `val email`, `getIdToken()`. |
| `FirebaseAuthUser` | **Reuse (R)** | `app/src/main/java/com/mortgee/al_mobile/data/auth/FirebaseAuthAdapter.kt` | `FirebaseAuthUser(private val firebaseUser: FirebaseUser) : AuthUser` |
| `SessionOwner` | **Modified (M)** | `app/src/main/java/com/mortgee/al_mobile/data/session/SessionOwner.kt` | Authority interface for session state flow, attempts, atomic commits, and pending-attempt check `isAttemptCurrent(attempt)`. |
| `DefaultSessionOwner` | **Modified (M)** | `app/src/main/java/com/mortgee/al_mobile/data/session/DefaultSessionOwner.kt` | Concrete implementation using `gateMutex` and `activeAttempt` verification (`isAttemptCurrent`). |
| `SessionAttempt` | **Reuse (R)** | `app/src/main/java/com/mortgee/al_mobile/data/session/SessionAttempt.kt` | `data class SessionAttempt(val attemptId: String, val type: Type)` |
| `SessionState` | **Reuse (R)** | `app/src/main/java/com/mortgee/al_mobile/data/session/SessionState.kt` | `SignedOut`, `Establishing`, `Restoring`, `Authenticated`. |
| `SessionKey` | **Reuse (R)** | `app/src/main/java/com/mortgee/al_mobile/data/session/SessionKey.kt` | `data class SessionKey(val uid: String, val generation: Long)` |
| `UserPartitionManager` | **Reuse (R)** | `app/src/main/java/com/mortgee/al_mobile/data/session/UserPartitionManager.kt` | Interface: `preparePartition(uid)`, `isPartitionReady(uid)`, `clearPartition()`. |
| `CompletedSessionStorage` | **Reuse (R)** | `app/src/main/java/com/mortgee/al_mobile/data/session/CompletedSessionStorage.kt` | Interface: `getCompletedUid()`, `saveCompletedUid(uid)`, `clearCompletedUid()`. |
| `PrivateTokenSeam` | **Reuse (R)** | `app/src/main/java/com/mortgee/al_mobile/data/network/PrivateTokenSeam.kt` | Bearer token provider for AL-Core HTTP calls. |
| `AccountRepository` | **Modified (M)** | `app/src/main/java/com/mortgee/al_mobile/data/repository/AccountRepository.kt` | Defines `signIn`, `restoreSession`, `register(email, password, displayName): SignInResult` (04.06), and `completeRegistration(email, password, displayName): SignInResult` (04.09A/04.09B; see [`task-04-03-contract.md`](./task-04-03-contract.md)). |
| `ApiAccountRepository` | **Modified (M)** | `app/src/main/java/com/mortgee/al_mobile/data/repository/ApiAccountRepository.kt` | Concrete implementation of `AccountRepository` including `register` and `completeRegistration` (04.09A/04.09B: authenticates once via `AuthAdapter.signIn`, reads `GET /users/me`, commits valid existing `BackendRole.BORROWER` profile with zero POSTs, allows one `POST /users/me` on confirmed `GET 404` reusing the registration create and commit path, and prevents other read errors or non-Borrower profiles from creating or overwriting a profile). |
| `SignInResult` | **Reuse/Modify (R/M)** | `app/src/main/java/com/mortgee/al_mobile/data/model/SignInResult.kt` | Unified safe outcome carrier (`data object` variants with no credentials, tokens, or raw exceptions). Reuses existing variants (`Success`, `Busy`, `InvalidCredentials`, `DisabledAccount`, `Connectivity`, `RejectedAuthentication`, `MissingProfile`, `UnsupportedRole`, `GenericFailure`) and specifies registration extensions (`InvalidEmail`, `WeakPassword`, `InvalidDisplayName`, `AccountExists`, `CompletionNeeded`) in [`task-04-03-contract.md`](./task-04-03-contract.md). |
| `SignInFormState` | **Reuse (R reference)** | `app/src/main/java/com/mortgee/al_mobile/ui/signin/SignInFormState.kt` | Reference pattern for in-memory masked credential state. |
| `RegisterFormState` | **Created (C)** | `app/src/main/java/com/mortgee/al_mobile/ui/register/RegisterFormState.kt` | Implemented `RegisterMode` (`CreateAccount`, `CompleteExisting`) and `RegisterFormState` (04.10) exposing `displayName`, `email`, transient `password` (masked in `toString()`), field/form errors, local validation (`validate()`), and recovery mode transitions. |
| `RegisterViewModel` | **Created (C)** | `app/src/main/java/com/mortgee/al_mobile/ui/register/RegisterViewModel.kt` | Implemented `@HiltViewModel class RegisterViewModel` (04.11) managing `RegisterFormState`, validating local inputs, capturing snapshots, setting `isSubmitting` synchronously before launching, choosing `register` vs `completeRegistration` by `RegisterMode`, and clearing transient password state on completion. |
| `RegisterScreen` | **Create (C)** | `app/src/main/java/com/mortgee/al_mobile/ui/register/RegisterScreen.kt` | **Search evidence**: No registration screen exists in `ui/`. |
| `RegisterRoute` | **Create (C)** | `app/src/main/java/com/mortgee/al_mobile/ui/register/RegisterRoute.kt` | **Search evidence**: No registration route exists in `ui/`. |
| `AppNavigation` | **Modify (M)** | `app/src/main/java/com/mortgee/al_mobile/ui/navigation/AppNavigation.kt` | Entry navigation host connecting sign-in and registration to session state. |
| `MainActivity` | **Reuse (R)** | `app/src/main/java/com/mortgee/al_mobile/MainActivity.kt` | App entry activity initializing session restore and rendering `AppNavigation`. |

---

## 3. Existing Hilt Bindings & Injection Mapping

All required Hilt modules are already defined and active in `app/src/main/java/com/mortgee/al_mobile/di/`:

1. **`AuthModule.kt`**:
   - `provideFirebaseAuth()` → `FirebaseAuth`
   - `provideAuthAdapter(firebaseAuth)` → `AuthAdapter` (`FirebaseAuthAdapter`)
2. **`NetworkModule.kt`**:
   - `provideMoshi()` → `Moshi`
   - `provideProfileApiService(retrofit)` → `ProfileApiService` (Retrofit interface)
   - `provideRetrofit(...)` → `Retrofit`
3. **`RepositoryModule.kt`**:
   - `@Binds bindAccountRepository(impl: ApiAccountRepository): AccountRepository`
4. **`SessionModule.kt`**:
   - `@Binds bindSessionOwner(impl: DefaultSessionOwner): SessionOwner`
   - `@Binds bindUserPartitionManager(impl: DefaultUserPartitionManager): UserPartitionManager`
5. **`StorageModule.kt`**:
   - `provideCompletedSessionStorage(impl: SharedPreferencesCompletedSessionStorage)` → `CompletedSessionStorage`

---

## 4. Test Fakes & Unit Test Suite Mapping

Existing test files to be extended for Task 04 implementation:

1. **`ApiAccountRepositoryTest.kt`** (`app/src/test/java/.../data/repository/ApiAccountRepositoryTest.kt`):
   - Contains: `FakeAuthAdapter`, `FakeProfileApiService`, `FakeUserPartitionManager`, `FakeCompletedSessionStorage`, `TestAuthUser`.
   - Action: Extend fakes to handle `createAccount`, `createProfile`, and add test cases for `register` and `completeRegistration`.
2. **`AuthAdapterTest.kt`** (`app/src/test/java/.../data/auth/AuthAdapterTest.kt`):
   - Contains: `FakeAuthAdapter`, `FakeAuthUser`.
   - Action: Test `createAccount`, mutation mutex concurrency, and Firebase exception mapping for creation collisions / weak passwords.
3. **`ProfileApiServiceTest.kt`** (`app/src/test/java/.../data/network/ProfileApiServiceTest.kt`):
   - MockWebServer tests.
   - Action: Add test for `POST /users/me` with `CreateProfileRequest` payload verification.

---

## 5. Fixed Wire Contract & Firebase Provider Evidence

- **Endpoint**: `POST /users/me`
- **Origin**: `BuildConfig.API_BASE_URL` (`https://agentic-lender-525895646792.europe-west2.run.app/` in dev)
- **Authorization Header**: `Authorization: Bearer <firebase_id_token>`
- **Backend Model (`UserProfileCreate`)**: Accepts `role`, `first_name`, `last_name`, `phone_number`, and optional `display_name`.
- **Request Body**:
  ```json
  {
    "role": "borrower",
    "first_name": "Test",
    "last_name": "Borrower",
    "phone_number": "+1234567890",
    "display_name": "Test Borrower"
  }
  ```
  *(Note: also supports `{"display_name": "Test Borrower"}` payload for optional field fallback)*
- **Expected Success Response**: `201 Created`
  ```json
  {
    "uid": "firebase_uid_123",
    "email": "borrower@example.com",
    "role": "borrower",
    "display_name": "Test Borrower",
    "first_name": "Test",
    "last_name": "Borrower",
    "phone_number": "+1234567890",
    "createdAt": "2026-11-06T10:00:00Z",
    "updatedAt": "2026-11-06T10:00:00Z"
  }
  ```
- **Validation Criteria**:
  - `profile.uid` MUST match `candidateUser.uid`.
  - `profile.decodeRole()` MUST evaluate to `BackendRole.BORROWER`.
  - Missing/mismatched UID or non-BORROWER role fails session establishment and cleans up candidate state.
- **Firebase Auth Provider**:
  - Bound via `AuthModule.kt` (`FirebaseAuth.getInstance()`).
  - Calls `firebaseAuth.createUserWithEmailAndPassword(email, password)` under `mutationMutex.withLock`.

---

## 6. Verification Status

- Automated Unit Test Command: `./gradlew testDebugUnitTest` → **PASS** (240 tests passed, 0 failures)
- Build Verification Command: `./gradlew assembleDebug` → **PASS**
