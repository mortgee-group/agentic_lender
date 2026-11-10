# Mobile Architecture & Engineering Guidelines

## 1. Architectural Principles & Layers
The AL-Mobile application follows modern Android architecture guidelines (MVVM + Clean Architecture principles):
- **Data Layer**: Repositories abstract network data sources (`CatalogueApiService` via Retrofit + Moshi) and map DTO models to immutable domain models (`PropertyListing`).
- **Domain / Business Logic Layer**: Pure Kotlin domain models, input validators (`PropertyFormValidator`), and business rules.
- **UI / Presentation Layer**: Jetpack Compose UI (`androidx.compose`), Material 3 design system, ViewModels exposing `StateFlow` (`PropertyCatalogueViewModel`, `PropertyDetailViewModel`, `TestFeatureViewModel`), and screen routes.

## 2. Concurrency & Coroutines
- **Structured Concurrency**: All asynchronous work runs on Kotlin Coroutines with `viewModelScope`.
- **Cancellation Propagation**: `CancellationException` must *never* be swallowed in `try-catch` blocks within repositories or data flows. It must always be rethrown (`catch (e: CancellationException) { throw e }`).
- **Dispatchers**: Injected or default dispatchers conforming to coroutines best practices.

## 3. Error Handling & Resilience
- Network and serialization errors are caught in repositories and mapped to domain-specific error types (`CatalogueError`: `NotFound`, `InvalidQuery`, `NetworkError`, `ParseError`) wrapped in `Result.failure(CatalogueException(...))`.
- ViewModels observe `Result` values and reflect error states in immutable UI state holders (`UiState`).

## 4. Security & Privacy Rules
- **No Credentials in Storage**: Credentials, tokens, or auth bodies must never be stored in persistent storage or saved state.
- **No Sensitive Logging**: Never log credentials, auth tokens, authentication request bodies, or raw exceptions containing sensitive data.
- **HTTPS Enforcement**: API base URL enforces strict HTTPS validation in Gradle build scripts.

## 5. Dependency Injection (DI)
- Compile-time type-safe Dependency Injection via **Hilt** (`@HiltAndroidApp`, `@AndroidEntryPoint`, `@Module`, `@InstallIn(SingletonComponent::class)`).
- Repositories are bound via interfaces and `@Binds` in `RepositoryModule`.
- Retrofit clients and Moshi builders are provided in `NetworkModule`.
