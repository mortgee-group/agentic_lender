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

---

## 2. Component Mapping Matrix

| Component Domain | Action / Status | File Path | Technology / API |
| :--- | :--- | :--- | :--- |
| **Dependency Catalogue** | REUSED & UPDATED | `gradle/libs.versions.toml` | Version Catalog (Hilt 2.60.1, Retrofit 2.11.0, OkHttp 4.12.0, Moshi 1.15.2, Firebase BOM 33.10.0, Coroutines 1.11.0) |
| **Build Configuration** | REUSED & UPDATED | `build.gradle.kts`<br>`app/build.gradle.kts` | AGP 9.4.1, Java 17, Hilt, KSP, Google Services, Crashlytics, strict `apiBaseUrl` HTTPS validation, `debug` / `release` buildTypes |
| **App Manifest & Registration** | CREATED & REGISTERED | `app/src/main/AndroidManifest.xml` | Registered `ALMobileApplication` (`android:name=".ALMobileApplication"`), Internet permission, Edge-to-Edge input adjustments |
| **Application & DI Root** | CREATED | `app/src/main/java/com/mortgee/al_mobile/ALMobileApplication.kt` | `@HiltAndroidApp class ALMobileApplication : Application()` |
| **App Entry Point & Navigation** | UPDATED & WIRED | `app/src/main/java/com/mortgee/al_mobile/MainActivity.kt` | `@AndroidEntryPoint`, ComponentActivity, `@Inject lateinit var apiClient: ApiClient`, state-based List/Detail navigation |
| **HTTP Client & Network Base** | CREATED & WIRED | `app/src/main/java/com/mortgee/al_mobile/data/network/ApiClient.kt` | `@Singleton class ApiClient @Inject constructor(val retrofit: Retrofit)` |
| **Network DTOs** | MODIFIED | `app/src/main/java/com/mortgee/al_mobile/data/network/model/CatalogueDtos.kt` | Moshi `@JsonClass`, `CatalogueItemDto`, `CatalogueListResponseDto` (`properties` key) |
| **Retrofit API Service** | MODIFIED | `app/src/main/java/com/mortgee/al_mobile/data/network/CatalogueApiService.kt` | `GET /api/v1/properties`, `GET /api/v1/properties/{id}` |
| **Domain Models** | MODIFIED | `app/src/main/java/com/mortgee/al_mobile/data/model/PropertyListing.kt` | `PropertyListing` unified domain data class |
| **Repositories** | MODIFIED | `app/src/main/java/com/mortgee/al_mobile/data/repository/CatalogueRepository.kt`<br>`ApiCatalogueRepository.kt` | Coroutines Result mapping, error handling, `getPropertyDetail(id)` |
| **Dependency Injection Modules** | CREATED | `app/src/main/java/com/mortgee/al_mobile/di/NetworkModule.kt`<br>`RepositoryModule.kt` | Hilt `@Module`, `@InstallIn(SingletonComponent::class)` |
| **Property Catalogue UI** | MODIFIED | `app/src/main/java/com/mortgee/al_mobile/ui/catalogue/PropertyCatalogueScreen.kt`<br>`PropertyCatalogueViewModel.kt`, `PropertyCatalogueRoute.kt` | Compose Material 3, compact card with location & spec badges |
| **Property Detail UI** | CREATED | `app/src/main/java/com/mortgee/al_mobile/ui/detail/PropertyDetailScreen.kt`<br>`PropertyDetailViewModel.kt`, `PropertyDetailRoute.kt` | Compose Material 3, full property specification view |
| **Form Validation** | CREATED | `app/src/main/java/com/mortgee/al_mobile/ui/form/PropertyFormValidator.kt` | Location, size (m²), bedrooms (0=studio), property type & BER validation |
| **Theme & Typography** | REUSED | `app/src/main/java/com/mortgee/al_mobile/ui/theme/Theme.kt`<br>`Color.kt`, `Type.kt` | Material 3 Theme |
| **Unit Testing** | CREATED / MODIFIED | `app/src/test/java/com/mortgee/al_mobile/data/repository/ApiCatalogueRepositoryTest.kt`<br>`PropertyCatalogueViewModelTest.kt`<br>`PropertyDetailViewModelTest.kt`<br>`PropertyFormValidatorTest.kt` | JUnit 4, Coroutines Test, MockWebServer |

---

## 3. Technology Replacements & Justification

1. **Java Baseline & SDK Target**: Updated compile & target SDK to SDK 37 (Android 16) with Java 17 target compatibility (`JavaVersion.VERSION_17`) for modern Gradle and AGP 9.4.1 runtime support.
2. **Dependency Injection**: Hilt (`2.60.1`) with KSP (`2.3.12`) is configured as the compile-time, type-safe DI container. `ALMobileApplication` is registered in `AndroidManifest.xml` with `@HiltAndroidApp`, and `MainActivity` is annotated with `@AndroidEntryPoint`.
3. **HTTP Transport & Base Client**: Retrofit `2.11.0` + OkHttp `4.12.0` with Moshi `1.15.2` is configured via `ApiClient.kt`. Base API URL is injected via `BuildConfig.API_BASE_URL` with strict Gradle-level HTTPS security validation in `app/build.gradle.kts`.
4. **Firebase Platform Services**:
   - **Firebase BOM 33.10.0**: Synchronizes dependencies across Auth, Firestore, Analytics, Storage, Crashlytics, Remote Config, and Cloud Messaging.
   - **Coroutines Play Services (`kotlinx-coroutines-play-services` 1.11.0)**: Converts Firebase `Task` instances to Kotlin coroutines `suspend` functions and `Flow` streams.
   - **Configuration File**: `app/google-services.json` provided for environment initialization.

---

## 4. Backend Capability Inventory & First Slice Endpoint Agreement

### Backend Capability Inventory
- **Auth Endpoint**: `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh`, `POST /api/v1/auth/logout`, Firebase Auth
- **Data Endpoints**:
  - `GET /api/v1/properties` (Paginated properties list response mapping `properties` array)
  - `GET /api/v1/properties/{id}` (Direct detail lookup returning single property listing)
- **Status of Missing APIs**: Absence of server source/API access is explicitly documented as **unresolved external dependency** rather than mocking unverified endpoints.

---

## 5. Verification & Test Execution

### Automated Test Commands
- **Local Unit Tests**:
  ```bash
  ./gradlew testDebugUnitTest
  ```
- **Build & Verification**:
  ```bash
  ./gradlew assembleDebug
  ```

### Acceptance Verification Checklist
- [x] Listing attributes (`location`, `propertySizeSqm`, `bedrooms`, `propertyType`, `berRating`) integrated end-to-end.
- [x] Unified DTO and domain model (`CatalogueItemDto` & `PropertyListing`).
- [x] Detail endpoint `GET /api/v1/properties/{id}` added to `CatalogueApiService`, `CatalogueRepository`, and `ApiCatalogueRepository`.
- [x] Compact list card and full detail screen implemented in Compose M3.
- [x] `PropertyFormValidator` created and unit tested for property create/edit fields.
- [x] Comprehensive unit tests passing cleanly via `testDebugUnitTest`.
