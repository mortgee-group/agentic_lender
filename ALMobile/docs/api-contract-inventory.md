# API Contract & Transport Inventory

## 1. Overview
This document catalogs the public HTTP API contracts, endpoints, query parameters, DTO schemas, and serialization rules utilized by the AL-Mobile application.

## 2. Base Configuration
- **Base URL**: Injected via `BuildConfig.API_BASE_URL` (configured securely in Gradle build scripts with mandatory HTTPS validation).
- **Transport**: Retrofit `2.11.0` + OkHttp `4.12.0`.
- **Serialization**: Moshi `1.15.2` with Kotlin JSON adapter factory (`KotlinJsonAdapterFactory`).

## 3. Endpoints

### 3.1 Get Properties List
- **Method & Path**: `GET /api/v1/properties`
- **Authentication**: Unauthenticated (Public guest access).
- **Query Parameters**:
  - `page`: `Int` (Default: `1`)
  - `pageSize`: `Int` (Default: `20`)
  - `sort`: `String?` (Default: `"createdAt,desc"`)
  - `query`: `String?` (Optional search query)
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
    "totalCount": 0,
    "hasMore": false
  }
  ```

### 3.2 Get Property Detail
- **Method & Path**: `GET /api/v1/properties/{id}`
- **Authentication**: Unauthenticated (Public guest access).
- **Path Parameter**: `id`: `String`
- **Response Schema (`CatalogueItemDto`)**: Single property listing object matching the schema above.

## 4. Error Mapping Rules
- **HTTP 404**: Mapped to `CatalogueError.NotFound`
- **HTTP 400 / 422**: Mapped to `CatalogueError.InvalidQuery`
- **HTTP 5xx / IOException**: Mapped to `CatalogueError.NetworkError`
- **JSON Parsing Exceptions**: Mapped to `CatalogueError.ParseError`
