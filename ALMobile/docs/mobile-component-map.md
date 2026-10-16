# Mobile Component Map & Backlog Implementation Plan

## 1. Executive Summary & Inventory
This document establishes the architecture mapping, technology choices, existing building block inventory, and target slice paths for the application based on the repository inspection and completed foundation setup.

### Existing Environment & Building Blocks
- **Build System & Java Baseline**: AGP 9.4.1, Kotlin 2.2.10, KSP 2.3.12, Java 17 (`JavaVersion.VERSION_17`), `compileSdk` / `targetSdk` 37, Google Services Plugin 4.4.2, Firebase Crashlytics Plugin 3.0.3.
- **Build & Security Logic**:
  - Dynamic `apiBaseUrl` Gradle property configuration with strict security validation in `app/build.gradle.kts` (HTTPS required, must end with `/`, no query/fragment/credentials/quotes/backslashes allowed).
  - `debug` buildType configured with `applicationIdSuffix = ".dev"`, `versionNameSuffix = "-dev"`, default `API_BASE_URL` (`"https://example.invalid/"`), and optional CI keystore signing.
  - `release` buildType configured with default production `API_BASE_URL` (`"https://api.example.com/"`) and R8 optimization scope (`setOf("androidx.**", "kotlin.**", "kotlinx.**")`).
- **Dependency Injection**: Hilt 2.60.1 (`hilt-android`, `hilt-compiler` via KSP, `hilt-navigation-compose` 1.2.0). Application class `ALMobileApplication` annotated with `@HiltAndroidApp` and registered in `AndroidManifest.xml`; `MainActivity` annotated with `@AndroidEntryPoint`.
- **UI Framework**: Jetpack Compose (BOM `2026.02.01`), Material 3, Material 3 Adaptive Navigation (`1.1.0`).
- **Networking & Serialization**: Retrofit 2.11.0 (`retrofit`, `converter-moshi`), OkHttp 4.12.0, Moshi 1.15.2 (`moshi-kotlin`). `ApiClient` singleton configured in `com.mortgee.al_mobile.data.network.ApiClient` using `BuildConfig.API_BASE_URL`.
- **Backend & Cloud Services (Firebase Platform)**:
  - Firebase BOM (`33.10.0`)
  - Firebase Authentication (`firebase-auth`)
  - Cloud Firestore (`firebase-firestore`)
  - Cloud Storage (`firebase-storage`)
  - Firebase Analytics (`firebase-analytics`)
  - Firebase Crashlytics (`firebase-crashlytics`)
  - Firebase Remote Config (`firebase-config`)
  - Firebase Cloud Messaging (`firebase-messaging`)
  - Configured via `app/google-services.json`
- **Async & Concurrency**: Kotlin Coroutines 1.11.0 (`core`, `android`, `test`), `kotlinx-coroutines-play-services` 1.11.0 (Firebase `Task.await()` support).
- **Optimization & Rules**: Initial R8 optimization rules defined in `app/src/main/keepRules/rules.keep`.
- **Planned Building Blocks** (To be added as feature slices are implemented):
  - **Navigation**: Jetpack Navigation 3 (`androidx.navigation3`).
  - **Persistence**: Room 2.8.4 (`room-runtime`, `room-ktx`, `room-compiler` via KSP), DataStore Preferences 1.2.1.
  - **Image Loading**: Coil Compose 2.7.0.
  - **Device Capabilities**: CameraX 1.6.1, Play Services Location 21.3.0, Accompanist Permissions 0.37.3.

---

## 2. Component Mapping Matrix

| Component Domain | Action / Status | File Path | Technology / API |
| :--- | :--- | :--- | :--- |
| **Dependency Catalogue** | REUSED & UPDATED | `gradle/libs.versions.toml` | Version Catalog (Hilt 2.60.1, Retrofit 2.11.0, OkHttp 4.12.0, Moshi 1.15.2, Firebase BOM 33.10.0, Coroutines 1.11.0) |
| **Build Configuration** | REUSED & UPDATED | `build.gradle.kts`<br>`app/build.gradle.kts` | AGP 9.4.1, Java 17, Hilt, KSP, Google Services, Crashlytics, strict `apiBaseUrl` HTTPS validation, `debug` / `release` buildTypes |
| **Firebase Configuration** | CREATED | `app/google-services.json` | Google Services JSON configuration |
| **App Manifest & Registration** | CREATED & REGISTERED | `app/src/main/AndroidManifest.xml` | Registered `ALMobileApplication` (`android:name=".ALMobileApplication"`), Internet permission, Edge-to-Edge input adjustments |
| **Application & DI Root** | CREATED | `app/src/main/java/com/mortgee/al_mobile/ALMobileApplication.kt` | `@HiltAndroidApp class ALMobileApplication : Application()` |
| **App Entry Point** | UPDATED & WIRED | `app/src/main/java/com/mortgee/al_mobile/MainActivity.kt` | `@AndroidEntryPoint`, ComponentActivity, `@Inject lateinit var apiClient: ApiClient`, Edge-to-Edge Compose setup |
| **HTTP Client & Network Base** | CREATED & WIRED | `app/src/main/java/com/mortgee/al_mobile/data/network/ApiClient.kt` | `@Singleton class ApiClient @Inject constructor(val retrofit: Retrofit)` |
| **Dependency Injection Modules** | CREATED | `app/src/main/java/com/mortgee/al_mobile/di/NetworkModule.kt` | Hilt `@Module`, `@InstallIn(SingletonComponent::class)` providing Moshi, OkHttpClient, Retrofit |
| **R8 Optimization Rules** | CREATED | `app/src/main/keepRules/rules.keep` | R8 keep rules |
| **Theme & Typography** | REUSED | `app/src/main/java/com/mortgee/al_mobile/ui/theme/Theme.kt`<br>`Color.kt`, `Type.kt` | Material 3 Theme |
| **App DI Module (General)** | PLANNED | `app/src/main/java/com/mortgee/al_mobile/di/AppModule.kt` | Hilt `@Module`, `@InstallIn(SingletonComponent::class)` |
| **Auth Interceptors & Transport** | PLANNED | `app/src/main/java/com/mortgee/al_mobile/data/network/AuthInterceptor.kt`<br>`TokenAuthenticator.kt` | OkHttp 4 Interceptors, Retrofit |
| **Navigation** | PLANNED | `app/src/main/java/com/mortgee/al_mobile/navigation/AppNavigation.kt` | Navigation 3 (`NavDisplay`, Scenes), `hiltViewModel()` |
| **Shared UI Components** | PLANNED | `app/src/main/java/com/mortgee/al_mobile/ui/components/ErrorView.kt`<br>`LoadingView.kt` | Compose Material 3 |
| **Cloud Auth & Database** | PLANNED | `app/src/main/java/com/mortgee/al_mobile/data/firebase/` | Firebase Auth, Cloud Firestore, Firebase Storage |
| **Coroutine Helpers & Result** | PLANNED | `app/src/main/java/com/mortgee/al_mobile/util/Result.kt`<br>`DispatchersProvider.kt` | Coroutines, Flow, `kotlinx-coroutines-play-services` (`.await()`) |
| **Error Models** | PLANNED | `app/src/main/java/com/mortgee/al_mobile/data/model/AppError.kt` | Kotlin Sealed Interface |
| **Session Persistence** | PLANNED | `app/src/main/java/com/mortgee/al_mobile/data/local/SessionDataStore.kt` | DataStore Preferences |
| **Database Persistence** | PLANNED | `app/src/main/java/com/mortgee/al_mobile/data/local/AppDatabase.kt` | Room 2.8.4 |
| **Repositories** | PLANNED | `app/src/main/java/com/mortgee/al_mobile/data/repository/SessionRepository.kt`<br>`ItemRepository.kt` | Kotlin Coroutines Flow, Hilt `@Inject` |
| **Telemetry & Messaging** | PLANNED | `app/src/main/java/com/mortgee/al_mobile/service/` | Firebase Crashlytics, Analytics, FCM, Remote Config |
| **Unit Testing** | REUSED / UPDATED | `app/src/test/java/com/mortgee/al_mobile/ExampleUnitTest.kt` | JUnit 4, Coroutines Test |
| **UI/Instrumented Testing** | REUSED / UPDATED | `app/src/androidTest/java/com/mortgee/al_mobile/ExampleInstrumentedTest.kt` | Espresso, Compose Test Rule |

---

## 3. Technology Replacements & Justification

1. **Java Baseline & SDK Target**: Updated compile & target SDK to SDK 37 (Android 16) with Java 17 target compatibility (`JavaVersion.VERSION_17`) for modern Gradle and AGP 9.4.1 runtime support.
2. **Dependency Injection**: Hilt (`2.60.1`) with KSP (`2.3.12`) is configured as the compile-time, type-safe DI container. `ALMobileApplication` is registered in `AndroidManifest.xml` with `@HiltAndroidApp`, and `MainActivity` is annotated with `@AndroidEntryPoint`.
3. **HTTP Transport & Base Client**: Retrofit `2.11.0` + OkHttp `4.12.0` with Moshi `1.15.2` is configured via `ApiClient.kt`. Base API URL is injected via `BuildConfig.API_BASE_URL` with strict Gradle-level HTTPS security validation in `app/build.gradle.kts`.
4. **Firebase Platform Services**:
   - **Firebase BOM 33.10.0**: Synchronizes dependencies across Auth, Firestore, Analytics, Storage, Crashlytics, Remote Config, and Cloud Messaging.
   - **Coroutines Play Services (`kotlinx-coroutines-play-services` 1.11.0)**: Converts Firebase `Task` instances to Kotlin coroutines `suspend` functions and `Flow` streams.
   - **Configuration File**: `app/google-services.json` provided for environment initialization.
5. **R8 Optimization**: R8 keep rules file initialized at `app/src/main/keepRules/rules.keep` with R8 optimization scope configured in release buildType.

---

## 4. Backend Capability Inventory & First Slice Endpoint Agreement

### Backend Capability Inventory
- **Auth Endpoint**: `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh`, `POST /api/v1/auth/logout`, Firebase Auth
- **Data Endpoint (First Slice)**: `GET /api/v1/items` (Supports query paging, JSON response mapping), Cloud Firestore collections
- **Status of Missing APIs**: Absence of server source/API access is explicitly documented as **unresolved external dependency** rather than mocking unverified endpoints.

### First Slice Endpoint Agreement
- **Agreed Endpoint**: `GET /api/v1/items`
- **Base Client**: `ApiClient.retrofit` pointing to `BuildConfig.API_BASE_URL`
- **Fallback / Local Mode**: Local Room cache / Cloud Firestore backing with `Result.Success` / `Result.Error` wrapper when remote endpoint is unreachable.

---

## 5. Session, Auth & Invalidation Ownership

| Sub-system | Owner Class | Responsibility |
| :--- | :--- | :--- |
| **Logout Policy** | `SessionRepository` | Clears local DataStore session, purges Room cache, signs out Firebase Auth, emits unauthenticated state. |
| **Credential Handling** | `SessionDataStore` & Firebase Auth | Encrypts / stores auth tokens and refresh credentials securely in DataStore; delegates federated or user auth to Firebase Auth. |
| **Refresh Transport** | `TokenAuthenticator` (OkHttp) | Intercepts 401 HTTP responses, calls refresh token endpoint synchronously, updates tokens. |
| **Session Invalidation** | `AuthInterceptor` & `SessionRepository` | Emits global session expiration flow to trigger Navigation 3 redirect to Login screen. |

---

## 6. Verification & Test Execution

### Automated Test Commands
- **Local Unit Tests**:
  ```bash
  ./gradlew testDebugUnitTest
  ```
- **Instrumented UI Tests**:
  ```bash
  ./gradlew connectedAndroidTest
  ```
- **Build & Verification**:
  ```bash
  ./gradlew assembleDebug
  ```

### Acceptance Verification Checklist
- [x] Dependency catalogue and build files verified (`libs.versions.toml`, `app/build.gradle.kts`).
- [x] Firebase Platform dependencies and Google Services Gradle Plugin configured (`app/google-services.json`).
- [x] Dependency injection configured using Hilt (`@HiltAndroidApp` in `ALMobileApplication.kt`, registered in `AndroidManifest.xml`, `@AndroidEntryPoint` in `MainActivity.kt`, KSP).
- [x] Base HTTP Client initialized (`ApiClient.kt` configured with `BuildConfig.API_BASE_URL` and Moshi converter).
- [x] Security and build configuration established (strict HTTPS `apiBaseUrl` validation, `debug` `.dev` suffix, Java 17 compile options).
- [x] R8 keep rules file initialized (`app/src/main/keepRules/rules.keep`).
- [x] Exact file creation paths mapped using `com.mortgee.al_mobile` package name across all existing and planned layers.
- [x] Session, Auth, and Invalidation owners designated.
- [x] Unresolved backend capabilities noted as missing API dependencies rather than fabricated source code.
