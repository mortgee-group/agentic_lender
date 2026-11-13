# Feature 01: Guest Journey — Manifest & Public Contracts

## 1. Executive Summary & Status
This document records the component reuse decisions, public HTTP API read contracts, error/retry policies, caching rules, and target implementation paths for the Guest Journey feature.

- **Current Status**: Unified Property Listing Attributes & Detail Flow [COMPLETED]
- **Supported Attributes**:
  1. `location`: `String? = null` — Public listing location text
  2. `propertySizeSqm`: `Double? = null` — Floor area in square metres
  3. `bedrooms`: `Int? = null` — Whole-number bedrooms (0 represents Studio)
  4. `propertyType`: `String? = null` — Property-type code (e.g. `"APARTMENT"`, `"DETACHED"`, `"SEMI_DETACHED"`, `"TERRACED"`)
  5. `berRating`: `String? = null` — BER rating code (e.g. `"A1"`–`"G"`, or `"EXEMPT"`)

---

## 2. Reused, Modified, and Created (R/M/C) Components

| Component / Path | Responsibility | Primary Caller | R / M / C Decision |
| :--- | :--- | :--- | :--- |
| `com.mortgee.al_mobile.data.network.CatalogueApiService` | Retrofit interface for public catalogue HTTP endpoints (`/api/v1/properties` and `/api/v1/properties/{id}`) | `ApiCatalogueRepository` | **MODIFIED (M)** — Added `getItemDetail(id)` |
| `com.mortgee.al_mobile.data.network.model.CatalogueDtos.kt` | Moshi JSON response DTOs (`CatalogueItemDto`, `CatalogueListResponseDto`) | `CatalogueApiService` | **MODIFIED (M)** — Added listing attributes & unified `CatalogueItemDto` |
| `com.mortgee.al_mobile.data.model.PropertyListing.kt` | Immutable domain data model for listing summary and details | ViewModels & Compose UI | **MODIFIED (M)** — Added 5 listing attributes & detail fields |
| `com.mortgee.al_mobile.data.repository.CatalogueRepository` | Interface defining public catalogue & detail data access | ViewModels | **MODIFIED (M)** — Added `getPropertyDetail(id)` |
| `com.mortgee.al_mobile.data.repository.ApiCatalogueRepository` | Network transport implementation & DTO-to-domain mapping | Hilt injection to `CatalogueRepository` | **MODIFIED (M)** — Mapped attributes & implemented `getPropertyDetail` |
| `com.mortgee.al_mobile.ui.catalogue.PropertyCatalogueScreen` | Compact catalogue card rendering location & attribute badges | Catalogue Route | **MODIFIED (M)** — Renders 5 listing attributes on `PropertyCard` |
| `com.mortgee.al_mobile.ui.detail.PropertyDetailScreen`<br>`PropertyDetailViewModel`<br>`PropertyDetailRoute` | Property detail screen rendering full listing specifications | MainActivity navigation | **CREATED (C)** — Detail UI & state management |
| `com.mortgee.al_mobile.ui.form.PropertyFormValidator` | Property form input validator & field error definitions | Property creation/editing flows | **CREATED (C)** — Validates location, size sqm, bedrooms (0=studio), property type & BER rating |
| `ApiCatalogueRepositoryTest`<br>`PropertyCatalogueViewModelTest`<br>`PropertyDetailViewModelTest`<br>`PropertyFormValidatorTest` | Unit test suite verifying deserialization, null handling, unknown codes, studios, and validation | Gradle `testDebugUnitTest` | **CREATED / MODIFIED (M/C)** — Comprehensive test coverage |

---

## 3. Public Read Semantics & Endpoints Agreement

### 3.1 Catalogue List Endpoint
- **HTTP Method & Path**: `GET /api/v1/properties`
- **Authentication**: Unauthenticated (Public access, no `Authorization` header required for guest reads).
- **Query Parameters**:
  - `page`: `Int` (Default: `1`, 1-based page numbering).
  - `pageSize`: `Int` (Default: `20`, range: `1..50`).
  - `sort`: `String?` (Default: `"createdAt,desc"`).
  - `query`: `String?` (Optional search filter).
- **Response Schema (`CatalogueListResponseDto`)**:
  ```json
  {
    "properties": [
      {
        "id": "string",
        "title": "string",
        "summary": "string?",
        "description": "string?",
        "price": 0.0,
        "currency": "string",
        "imageUrl": "string?",
        "category": "string?",
        "isAvailable": true,
        "location": "string?",
        "propertySizeSqm": 0.0,
        "bedrooms": 0,
        "propertyType": "string?",
        "berRating": "string?",
        "createdAt": "string?",
        "updatedAt": "string?"
      }
    ],
    "page": 1,
    "pageSize": 20,
    "totalCount": 100,
    "hasMore": true
  }
  ```

### 3.2 Catalogue Detail Endpoint
- **HTTP Method & Path**: `GET /api/v1/properties/{id}`
- **Authentication**: Unauthenticated (Public access).
- **Path Parameter**: `id`: `String` (Unique item string ID).
- **Lookup Rule**: Executes direct lookup by ID against backend.
- **Response Schema (`CatalogueItemDto`)**:
  ```json
  {
    "id": "string",
    "title": "string",
    "summary": "string?",
    "description": "string?",
    "price": 0.0,
    "currency": "string",
    "imageUrl": "string?",
    "category": "string?",
    "isAvailable": true,
    "location": "string?",
    "propertySizeSqm": 0.0,
    "bedrooms": 0,
    "propertyType": "string?",
    "berRating": "string?",
    "createdAt": "string?",
    "updatedAt": "string?"
  }
  ```

### 3.3 Write Endpoints & Guest Boundary
- Guest catalogue and detail access is strictly read-only.
- No write endpoints (POST/PATCH) currently exist on the mobile application or backend public API.

---

## 4. Error Handling, Cancellation & Retry Policy

### 4.1 Error Mapping
- **HTTP 404 (Not Found)**: Mapped to `CatalogueError.NotFound`.
- **HTTP 400 / 422 (Bad Request)**: Mapped to `CatalogueError.InvalidQuery`.
- **HTTP 5xx / Network Failure**: Mapped to `CatalogueError.NetworkError`.
- **Serialization Failure**: Mapped to `CatalogueError.ParseError`.

### 4.2 Cancellation & Coroutines
- `CancellationException` is explicitly preserved and rethrown in all catch blocks inside repositories and data streams.

---

## 5. Target Files and Symbols

- **Retrofit API Interface**: `com.mortgee.al_mobile.data.network.CatalogueApiService`
  - Symbols: `getItems(...)`, `getItemDetail(id: String)`
- **Network DTOs**: `com.mortgee.al_mobile.data.network.model.CatalogueItemDto`, `CatalogueListResponseDto`
- **Domain Models**: `com.mortgee.al_mobile.data.model.PropertyListing`
- **Repository**: `CatalogueRepository`, `ApiCatalogueRepository`
- **Form Validation**: `PropertyFormValidator`, `PropertyFormData`, `PropertyFormValidationResult`
- **UI Screens**: `PropertyCatalogueScreen`, `PropertyDetailScreen`
