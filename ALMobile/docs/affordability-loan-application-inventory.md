# Affordability & Loan Application — Reusable Feature Inventory

## 1. Executive Summary & Context
This document catalogs the reusable components, modified files, new production and test artifacts, Hilt bindings, wire contract specifications, and dispatch order for the **Affordability & Loan Application** feature in AL-Mobile (`LA-01` through `LA-22`), derived from the implementation specification (`ALMobile_Affordability_Loan_Application_Spec.md`).

- **Subproject**: `:app`
- **Root Directory**: `/var/home/dim4s/work/agentic_lender/ALMobile`
- **Package Prefix**: `com.mortgee.al_mobile`
- **Target API Endpoint**: `POST /applications` (ALCore borrower-authenticated create endpoint)

---

## 2. Inventory Action Summary

- **R (Reuse / Read-Only)**: Existing owner or reusable dependency in `ALMobile` or `ALCore`.
- **M (Modify)**: Existing file in `ALMobile` updated for this feature.
- **C (Create)**: New production or test file created in `ALMobile`.
- **D (Delete)**: None (`0` files deleted).

| Action | Production Files | Test Files | Total |
|---|---:|---:|---:|
| **R · Reuse (Read-Only)** | 20 | 7 | 27 |
| **M · Modify** | 4 | 0 | 4 |
| **C · Create** | 14 | 10 | 24 |
| **D · Delete** | 0 | 0 | 0 |

---

## 3. Reusable Foundation & Existing Owners (R)

### 3.1 Android Production Components (`ALMobile`)

| Symbol / Component | Action | File Path | Reusable Capability / Role |
|---|---|---|---|
| `MainActivity` | **R** | `app/src/main/java/com/mortgee/al_mobile/MainActivity.kt` | Hosts `AppNavigation` and observes session infrastructure. |
| `AppNavigation` | **R** | `app/src/main/java/com/mortgee/al_mobile/ui/navigation/AppNavigation.kt` | Renders `AuthenticatedLandingRoute` scoped inside `key(authenticatedState.key)`. |
| `NavigationConfig` | **R** | `app/src/main/java/com/mortgee/al_mobile/ui/navigation/NavigationConfig.kt` | Maps `BORROWER`, `BROKER`, and `UNDERWRITER` via `getDestinationForRole` to `AuthenticatedLanding`. |
| `BackendRole` | **R** | `app/src/main/java/com/mortgee/al_mobile/data/model/BackendRole.kt` | `BackendRole.BORROWER` identifies the only role permitted to create a mortgage application. |
| `SessionOwner` | **R** | `app/src/main/java/com/mortgee/al_mobile/data/session/SessionOwner.kt` | Exposes `sessionState: StateFlow<SessionState>` and `currentKey` as the session identity authority. |
| `SessionKey` | **R** | `app/src/main/java/com/mortgee/al_mobile/data/session/SessionKey.kt` | `SessionKey(uid: String, generation: Long)` isolates drafts and in-flight requests across logins, including same-UID re-logins. |
| `SessionState` | **R** | `app/src/main/java/com/mortgee/al_mobile/data/session/SessionState.kt` | `SessionState.Authenticated` carries the verified `key`, `uid`, `email`, and `role`. |
| `PrivateTokenSeam` | **R** | `app/src/main/java/com/mortgee/al_mobile/data/network/PrivateTokenSeam.kt` | `executePrivate(entryKey, isMutation = true, block)` acquires the Firebase ID token and enforces session-key ownership. |
| `DefaultPrivateTokenSeam` | **R** | `app/src/main/java/com/mortgee/al_mobile/data/network/DefaultPrivateTokenSeam.kt` | Verifies session generation before/after dispatch and handles thrown `HttpException(401)` on mutation paths. |
| `PrivateRequestInterceptor` | **R** | `app/src/main/java/com/mortgee/al_mobile/data/network/PrivateRequestInterceptor.kt` | Restricts outbound `Authorization: Bearer` headers to the configured `BuildConfig.API_BASE_URL` origin. |
| `ProfileApiService` | **R** | `app/src/main/java/com/mortgee/al_mobile/data/network/ProfileApiService.kt` | Reference pattern for relative Retrofit paths, explicit `@Header("Authorization")`, and Moshi `@Body` DTOs. |
| `CreateProfileRequest` | **R** | `app/src/main/java/com/mortgee/al_mobile/data/network/model/CreateProfileRequest.kt` | Reference pattern for `@JsonClass(generateAdapter = false)` with `@Json(name = ...)` snake_case wire mappings. |
| `UserPrivateRepository` | **R** | `app/src/main/java/com/mortgee/al_mobile/data/repository/UserPrivateRepository.kt` | Reference pattern for delegating authenticated operations to `PrivateTokenSeam`. |
| `PropertyFormValidator` | **R** | `app/src/main/java/com/mortgee/al_mobile/ui/form/PropertyFormValidator.kt` | Reuses `PropertyFormData`, `PropertyFormValidator.validate(...)`, `VALID_PROPERTY_TYPES`, and `VALID_BER_RATINGS` for 5 of the 7 property fields (`COUNTY` via location check, `AREA`, `BEDS`, `TYPE`, `BER`). |
| `PropertyListing` | **R** | `app/src/main/java/com/mortgee/al_mobile/data/model/PropertyListing.kt` | Catalogue domain model (note: lacks separate `county`, `bathrooms`, and `year_built`, requiring manual entry in loan form). |
| `CatalogueItemDto` | **R** | `app/src/main/java/com/mortgee/al_mobile/data/network/model/CatalogueDtos.kt` | Catalogue wire DTO reference. |
| `RegisterRoute` | **R** | `app/src/main/java/com/mortgee/al_mobile/ui/register/RegisterRoute.kt` | Reference pattern for `hiltViewModel()`, `collectAsStateWithLifecycle()`, and `BackHandler`. |
| `RegisterViewModel` | **R** | `app/src/main/java/com/mortgee/al_mobile/ui/register/RegisterViewModel.kt` | Reference pattern for `MutableStateFlow` and single-flight submission in `viewModelScope`. |
| `RegisterScreen` | **R** | `app/src/main/java/com/mortgee/al_mobile/ui/register/RegisterScreen.kt` | Reference pattern for Material 3 `OutlinedTextField`, inline supporting text errors, and submit button gating. |
| `app/build.gradle.kts` & `libs.versions.toml` | **R** | `app/build.gradle.kts`, `gradle/libs.versions.toml` | Provides Retrofit `2.11.0`, OkHttp `4.12.0`, Moshi `1.15.2` (`KotlinJsonAdapterFactory`), Hilt/KSP, MockWebServer, and Compose test dependencies. |

### 3.2 Reusable Test Patterns & References (`ALMobile`)

| Test File | Action | File Path | Reusable Pattern |
|---|---|---|---|
| `ProfileApiServiceTest` | **R** | `app/src/test/java/com/mortgee/al_mobile/data/network/ProfileApiServiceTest.kt` | `MockWebServer`, `Retrofit.Builder`, and `MoshiConverterFactory` transport test setup. |
| `PrivateTokenSeamTest` | **R** | `app/src/test/java/com/mortgee/al_mobile/data/network/PrivateTokenSeamTest.kt` | Regression suite for mutation `401` invalidation and stale session generation checks. |
| `PropertyFormValidatorTest` | **R** | `app/src/test/java/com/mortgee/al_mobile/ui/form/PropertyFormValidatorTest.kt` | Existing validation tests (including studio `0` bedrooms and optional BER in general form). |
| `RegisterViewModelTest` | **R** | `app/src/test/java/com/mortgee/al_mobile/ui/register/RegisterViewModelTest.kt` | `StandardTestDispatcher`, `Dispatchers.setMain`, `runTest`, and `runCurrent` coroutine test harness. |
| `AppNavigationTest` | **R** | `app/src/test/java/com/mortgee/al_mobile/ui/navigation/AppNavigationTest.kt` | Role-landing and session-generation navigation regression suite. |
| `HiltModulesTest` | **R** | `app/src/test/java/com/mortgee/al_mobile/di/HiltModulesTest.kt` | Hilt module provider and binding verification patterns. |

### 3.3 Backend Contract References (`ALCore`)

| Backend File | Action | Role in Contract Verification |
|---|---|---|
| `ALCore/app/models.py` | **R** | Defines `PropertyDetails`, `LoanApplicationCreate` (including `validate_deposit`), and `LoanApplicationResponse`. |
| `ALCore/app/main.py` | **R** | `submit_application` (`POST /applications`) returning HTTP `201` receipt `{"applicationId": ..., "message": ...}`. |
| `ALCore/app/applications.py` | **R** | `create_application` (computes `requestedLoanAmount` and `estimatedLTV`) and `select_least_busy_broker` (raises `503` when no broker is available prior to Firestore write). |
| `ALCore/app/auth.py` & `permissions.py` | **R** | `verify_firebase_token` (`401`) and `require_role("borrower")` (`403` on missing profile or non-borrower role). |
| `ALCore/tests/test_application_endpoints.py` | **R** | `test_borrower_can_create_application` (canonical Kilkenny fixture) and `test_real_permissions_reject_broker`. |

---

## 4. Modified Production Files (M)

| Action | File Path | Planned Modification | Cards |
|---|---|---|---|
| **M** | `app/src/main/java/com/mortgee/al_mobile/di/NetworkModule.kt` | Add `@Provides @Singleton fun provideLoanApplicationApi(retrofit: Retrofit, okHttpClient: OkHttpClient): LoanApplicationApi` with a derived `OkHttpClient` (`followRedirects(false)`, `followSslRedirects(false)`, `retryOnConnectionFailure(false)`) and a one-shot `RequestBody` interceptor (`isOneShot() = true`) so `POST /applications` is never silently re-transmitted. | `LA-04` |
| **M** | `app/src/main/java/com/mortgee/al_mobile/di/RepositoryModule.kt` | Add `@Binds @Singleton abstract fun bindLoanApplicationRepository(impl: ApiLoanApplicationRepository): LoanApplicationRepository`. | `LA-14` |
| **M** | `app/src/main/java/com/mortgee/al_mobile/ui/landing/AuthenticatedLandingScreen.kt` | Add optional `onStartLoanApplication: (() -> Unit)? = null` parameter and render a Material 3 `"Apply for a mortgage"` `Button` only when `authenticatedState?.role == BackendRole.BORROWER && onStartLoanApplication != null`. | `LA-20` |
| **M** | `app/src/main/java/com/mortgee/al_mobile/ui/landing/AuthenticatedLandingRoute.kt` | Delegate the authenticated `BackendRole.BORROWER` branch to `LoanApplicationRoute(authenticatedState, onSignOut, modifier)` while preserving existing landing behavior for other roles and previews. | `LA-21` |

---

## 5. Created Production Files (C)

| Action | File Path | Primary Symbols & Responsibilities | Reuse Lookup | Cards |
|---|---|---|---|---|
| **C** | `app/src/main/java/com/mortgee/al_mobile/data/network/model/LoanApplicationDtos.kt` | `LoanPropertyDto`, `CreateLoanRequest`, `CreateLoanReceipt`: Moshi DTOs with `@JsonClass(generateAdapter = false)` and exact `@Json(name = ...)` mappings for snake_case property keys (`floor_area`, `year_built`, `property_type`, `ber_rating`) and camelCase top-level keys. | `S1` | `LA-01`, `LA-02` |
| **C** | `app/src/main/java/com/mortgee/al_mobile/data/network/LoanApplicationApi.kt` | `fun interface LoanApplicationApi` with `@POST("applications") suspend fun create(@Header("Authorization") bearer: String, @Body request: CreateLoanRequest): Response<CreateLoanReceipt>`. | `S1` | `LA-03` |
| **C** | `app/src/main/java/com/mortgee/al_mobile/data/model/LoanField.kt` | `enum class LoanField(val path: String)` enumerating the 12 leaf wire paths (`INCOME`, `DEBT`, `DEPOSIT`, `TERM`, `PRICE`, `COUNTY`, `AREA`, `BEDS`, `BATHS`, `YEAR`, `TYPE`, `BER`). | `S2` | `LA-05` |
| **C** | `app/src/main/java/com/mortgee/al_mobile/ui/loan/LoanDraft.kt` | `data class LoanDraft(val values: Map<LoanField, String> = emptyMap(), val hasDebt: Boolean? = null)` with `get`, `edit`, and `chooseDebt` enforcing explicit Yes/No debt selection and clearing hidden debt input when `No` is selected. | `S2` | `LA-05` |
| **C** | `app/src/main/java/com/mortgee/al_mobile/ui/loan/LoanValidation.kt` | `Checked<T>`, `LoanFinances`, `loanMoney`, `validateFinances`, `validateProperty` (reusing `PropertyFormValidator` + required `BER`, `BATHS >= 0`, `YEAR in 1800..2030`), and `prepareLoan` building `CreateLoanRequest` only when all 12 checks succeed. | `S2` | `LA-06`, `LA-07`, `LA-08`, `LA-09` |
| **C** | `app/src/main/java/com/mortgee/al_mobile/data/repository/LoanApplicationRepository.kt` | `fun interface LoanApplicationRepository { suspend fun submit(key: SessionKey, request: CreateLoanRequest): LoanSubmitResult }` and `sealed interface LoanSubmitResult` (`Created`, `Invalid`, `SignInRequired`, `Forbidden`, `NoBroker`, `NotSent`, `Unconfirmed`, `Stale`). | `S3` | `LA-10` |
| **C** | `app/src/main/java/com/mortgee/al_mobile/data/repository/LoanHttpErrors.kt` | `decodeLoanValidation` (maps FastAPI `422` `detail[].loc` and root deposit `value_error` to `LoanField`) and `classifyLoanRejection` (`401 -> SignInRequired`, `403 -> Forbidden`, `422 -> Invalid`, exact `503 "No brokers are currently available" -> NoBroker`, everything else -> `Unconfirmed`). | `S3` | `LA-10`, `LA-11` |
| **C** | `app/src/main/java/com/mortgee/al_mobile/data/repository/ApiLoanApplicationRepository.kt` | `class ApiLoanApplicationRepository @Inject constructor(api, seam, moshi)`: executes one mutation via `seam.executePrivate(key, isMutation = true)`, throws `HttpException` on non-2xx so `401` triggers seam invalidation, verifies `201` with non-blank `applicationId`, rethrows `CancellationException`, and maps preflight/transport errors safely. | `S3` | `LA-12` |
| **C** | `app/src/main/java/com/mortgee/al_mobile/ui/loan/LoanUiState.kt` | `enum class LoanPhase` (`CLOSED`, `EDITING`, `SENDING`, `RECEIPT`, `UNCONFIRMED`, `DENIED`), `data class LoanUiState`, `visibleErrors`, `canSubmit`, and `LoanUiState.afterResult(r: LoanSubmitResult)`. | `S4` | `LA-13` |
| **C** | `app/src/main/java/com/mortgee/al_mobile/ui/loan/LoanApplicationViewModel.kt` | `@HiltViewModel class LoanApplicationViewModel`: owns transient in-memory `LoanUiState`, observes `SessionOwner.sessionState` to clear state on session/generation change, and exposes `open(key)`, `close(key)`, `edit(key, f, text)`, `chooseDebt(key, yes)`, and single-flight `submit(key)`. | `S4` | `LA-14`, `LA-15`, `LA-16` |
| **C** | `app/src/main/java/com/mortgee/al_mobile/ui/loan/LoanFinanceFields.kt` | `@Composable fun LoanFinanceFields` and `@Composable internal fun LoanTextInput` rendering the 5 financial controls, `FilterChip` Yes/No debt toggle, conditional debt input, and `Modifier.testTag(f.path)`. | `S4` | `LA-17` |
| **C** | `app/src/main/java/com/mortgee/al_mobile/ui/loan/LoanPropertyFields.kt` | `@Composable fun LoanPropertyFields` rendering the 7 property inputs (`County`, `Floor area (m²)`, `Bedrooms`, `Bathrooms`, `Year built`, and dropdown pickers for `Property type` and `BER rating` backed by `PropertyFormValidator.VALID_*`). | `S4` | `LA-18` |
| **C** | `app/src/main/java/com/mortgee/al_mobile/ui/loan/LoanApplicationScreen.kt` | `@Composable fun LoanApplicationScreen` rendering `Affordability` and `Property` sections in `EDITING`/`SENDING`, progress indicator, and terminal views for `RECEIPT` (`"Reference: <receiptId>"`), `UNCONFIRMED`, and `DENIED`. | `S4` | `LA-19` |
| **C** | `app/src/main/java/com/mortgee/al_mobile/ui/loan/LoanApplicationRoute.kt` | `@Composable fun LoanApplicationRoute` connecting `AuthenticatedLandingScreen` and `LoanApplicationScreen` to `LoanApplicationViewModel` with `BackHandler` protection during `SENDING`. | `S4` | `LA-21` |

---

## 6. Created Test Files (C)

| Action | File Path | Test Scope & Verification Target | Cards |
|---|---|---|---|
| **C** | `app/src/test/java/com/mortgee/al_mobile/data/network/model/LoanApplicationDtosTest.kt` | `propertyKeys` and `createContract`: verifies exact JSON keys, number types, and receipt decoding with `NetworkModule.provideMoshi()`. | `LA-01`, `LA-02` |
| **C** | `app/src/test/java/com/mortgee/al_mobile/data/network/LoanApplicationApiTest.kt` | `createWire` and one-shot provider tests: verifies `POST /applications`, `Authorization` header, `307` no-redirect, `503` no-retry, and `SocketPolicy.DISCONNECT_AFTER_REQUEST` single transmission. | `LA-03`, `LA-04` |
| **C** | `app/src/test/java/com/mortgee/al_mobile/ui/loan/LoanDraftTest.kt` | Verifies distinct `LoanField.path` values, `hasDebt` transitions (`null` -> `true` -> `false`), and clearing hidden debt input. | `LA-05` |
| **C** | `app/src/test/java/com/mortgee/al_mobile/ui/loan/LoanValidationTest.kt` | `moneyParsing`, `financeRules`, `propertyRules`, and `prepareRequest`: verifies exact `BigDecimal`/`Double` parity, cross-field `deposit < price`, `1..40` term, `1800..2030` year built, required `BER`, and complete fixture assembly. | `LA-06`, `LA-07`, `LA-08`, `LA-09` |
| **C** | `app/src/test/java/com/mortgee/al_mobile/data/repository/LoanHttpErrorsTest.kt` | `validationLocations`, `malformedValidation`, and `httpOutcomes`: verifies FastAPI `422` field and root deposit mapping, `401`, `403`, known `503` (`NoBroker`) vs generic `503`/`502`/`307` (`Unconfirmed`). | `LA-10`, `LA-11` |
| **C** | `app/src/test/java/com/mortgee/al_mobile/data/repository/ApiLoanApplicationRepositoryTest.kt` | Verifies `executePrivate(key, isMutation = true)`, `HttpException(401)` propagation to seam, `201` with non-blank ID, `TokenFetchFailed -> NotSent` (0 HTTP calls), and `CancellationException` rethrow. | `LA-12` |
| **C** | `app/src/test/java/com/mortgee/al_mobile/ui/loan/LoanUiStateTest.kt` | Verifies `visibleErrors` precedence, `canSubmit` gating, and `afterResult` state transitions (`RECEIPT`, `UNCONFIRMED`, `DENIED`, `EDITING`). | `LA-13` |
| **C** | `app/src/test/java/com/mortgee/al_mobile/ui/loan/LoanApplicationViewModelTest.kt` | `sessionOwnsDraft`, `changeOnlyCurrentDraft`, double-tap protection, same-UID new-generation stale completion isolation, and explicit retry after `NoBroker`. | `LA-14`, `LA-15`, `LA-16` |
| **C** | `app/src/androidTest/java/com/mortgee/al_mobile/ui/loan/LoanApplicationScreenTest.kt` | `debtVisibility`, `propertyInputs`, and `submissionViews`: Compose UI instrumentation tests for financial inputs, conditional debt field, property dropdowns, progress state, and receipt reference display. | `LA-17`, `LA-18`, `LA-19` |
| **C** | `app/src/androidTest/java/com/mortgee/al_mobile/ui/landing/LoanLandingActionTest.kt` | Compose UI instrumentation test verifying `"Apply for a mortgage"` button visibility exclusively for `BackendRole.BORROWER` when callback is supplied. | `LA-20` |

---

## 7. Dispatch & Dependency Order (`LA-01` – `LA-22`)

| Card | Title / Outcome | Prerequisites | Target Files (`C` / `M`) | Status |
|---|---|---|---|---|
| **LA-01** | Encode the seven property fields | Ready now | `C LoanApplicationDtos.kt`, `C LoanApplicationDtosTest.kt` | **DONE** (`LoanPropertyDto`, `LoanApplicationDtosTest.propertyKeys`) |
| **LA-02** | Encode a create request and decode its receipt | `LA-01` | `M LoanApplicationDtos.kt`, `M LoanApplicationDtosTest.kt` | **DONE** (`CreateLoanRequest`, `CreateLoanReceipt`, `LoanApplicationDtosTest.createContract`) |
| **LA-03** | Send the create call to the existing HTTP route | `LA-02` | `C LoanApplicationApi.kt`, `C LoanApplicationApiTest.kt` | **DONE** (`LoanApplicationApi`, `LoanApplicationApiTest.createWire`) |
| **LA-04** | Give the create operation a single-use HTTP body | `LA-03` | `M NetworkModule.kt`, `M LoanApplicationApiTest.kt` | Ready now |
| **LA-05** | Keep an explicit debt choice and discard hidden debt input | Ready now | `C LoanField.kt`, `C LoanDraft.kt`, `C LoanDraftTest.kt` | Ready now |
| **LA-06** | Parse money without silently changing the entered amount | Ready now | `C LoanValidation.kt`, `C LoanValidationTest.kt` | Ready now |
| **LA-07** | Validate the five financial values together | `LA-05`, `LA-06` | `M LoanValidation.kt`, `M LoanValidationTest.kt` | Pending |
| **LA-08** | Validate the complete ALCore property | `LA-07`, `LA-01` | `M LoanValidation.kt`, `M LoanValidationTest.kt` | Pending |
| **LA-09** | Build a POST body only from a complete draft | `LA-08`, `LA-02` | `M LoanValidation.kt`, `M LoanValidationTest.kt` | Pending |
| **LA-10** | Map FastAPI validation errors to form fields | `LA-02`, `LA-05` | `C LoanApplicationRepository.kt`, `C LoanHttpErrors.kt`, `C LoanHttpErrorsTest.kt` | Pending |
| **LA-11** | Distinguish confirmed rejections from an uncertain write | `LA-10` | `M LoanHttpErrors.kt`, `M LoanHttpErrorsTest.kt` | Pending |
| **LA-12** | Submit once with the original borrower's session key | `LA-04`, `LA-11` | `C ApiLoanApplicationRepository.kt`, `C ApiLoanApplicationRepositoryTest.kt` | Pending |
| **LA-13** | Turn a submission outcome into form or receipt state | `LA-09`, `LA-10` | `C LoanUiState.kt`, `C LoanUiStateTest.kt` | Pending |
| **LA-14** | Retain one draft for the active borrower | `LA-12`, `LA-13` | `C LoanApplicationViewModel.kt`, `M RepositoryModule.kt`, `C LoanApplicationViewModelTest.kt` | Pending |
| **LA-15** | Apply form edits to the current open draft | `LA-14` | `M LoanApplicationViewModel.kt`, `M LoanApplicationViewModelTest.kt` | Pending |
| **LA-16** | Guard double taps and late submission results | `LA-15` | `M LoanApplicationViewModel.kt`, `M LoanApplicationViewModelTest.kt` | Pending |
| **LA-17** | Render the financial controls and reveal the debt amount | `LA-05` | `C LoanFinanceFields.kt`, `C LoanApplicationScreenTest.kt` | Pending |
| **LA-18** | Render the seven property controls with valid code choices | `LA-17` | `C LoanPropertyFields.kt`, `M LoanApplicationScreenTest.kt` | Pending |
| **LA-19** | Render editing, progress and the returned application reference | `LA-13`, `LA-18` | `C LoanApplicationScreen.kt`, `M LoanApplicationScreenTest.kt` | Pending |
| **LA-20** | Add a borrower action to the existing landing screen | Ready now | `M AuthenticatedLandingScreen.kt`, `C LoanLandingActionTest.kt` | Ready now |
| **LA-21** | Open and leave the form through the real authenticated landing | `LA-16`, `LA-19`, `LA-20` | `C LoanApplicationRoute.kt`, `M AuthenticatedLandingRoute.kt` | Pending |
| **LA-22** | Verify the borrower journey and report the evidence | `LA-21` | Verification only (`testDebugUnitTest`, `assembleDebug`, `connectedDebugAndroidTest`, `pytest`) | Pending |
