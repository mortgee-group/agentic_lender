# Backend Firestore & DTO Models Spec

Summary of DTO models and Firestore document schema requirements for property listing attributes:

---

### 1. Firestore Collection Schema (`items`)

**Collection Path:** `/items`  
**Document ID:** `{id}` (String)

| Firestore Field | Type | Required | Description / Default |
| :--- | :--- | :--- | :--- |
| `id` | String | **Yes** | Unique identifier for the item/property. |
| `title` | String | **Yes** | Title/heading of the listing. |
| `summary` | String | Optional | Short summary preview text (nullable). |
| `description` | String | Optional | Full description (for detailed item lookup, nullable). |
| `price` | Number (Double) | **Yes** | Listing price. |
| `currency` | String | **Yes** | ISO currency code (e.g., `"USD"`, `"EUR"`). |
| `imageUrl` | String | Optional | URL to the primary image (nullable). |
| `category` | String | Optional | Category name/tag (nullable). |
| `isAvailable` | Boolean | **Yes** | Availability status (Default: `true`). |
| `location` | String | Optional | Listing location text (e.g., `"Ballsbridge, Dublin 4"`, nullable). |
| `propertySizeSqm` | Number (Double) | Optional | Property area in square metres (nullable). |
| `bedrooms` | Number (Int) | Optional | Number of bedrooms; `0` represents a studio (nullable). |
| `propertyType` | String | Optional | Stable property type code (e.g., `"APARTMENT"`, `"DETACHED"`, nullable). |
| `berRating` | String | Optional | BER rating code (e.g., `"A1"`, `"B2"`, `"EXEMPT"`, nullable). |
| `createdAt` | Timestamp / String | Optional | ISO-8601 string or Firestore Timestamp. |
| `updatedAt` | Timestamp / String | Optional | ISO-8601 string or Firestore Timestamp. |

> **Boundary Privacy Rule:** Do not include internal seller IDs, internal financial ledgers, or admin flags in public Firestore reads.

---

### 2. DTO Models (JSON Payload / Transport Layer)

#### **A. `CatalogueItemDto`** (Unified Property Schema)
```json
{
  "id": "item_001",
  "title": "Modern Suburban Family Home",
  "summary": "Beautiful 3-bedroom home with modern amenities.",
  "description": "Detailed description containing full listing specifications...",
  "price": 450000.00,
  "currency": "EUR",
  "imageUrl": "https://example.com/images/item_001.jpg",
  "category": "Residential",
  "isAvailable": true,
  "location": "Ballsbridge, Dublin 4",
  "propertySizeSqm": 125.5,
  "bedrooms": 3,
  "propertyType": "APARTMENT",
  "berRating": "A2",
  "createdAt": "2026-03-01T12:00:00Z",
  "updatedAt": "2026-03-02T10:30:00Z"
}
```

#### **B. `CatalogueListResponseDto`** (Paginated List Response: `GET /api/v1/properties`)
```json
{
  "properties": [
    {
      "id": "item_001",
      "title": "Modern Suburban Family Home",
      "summary": "Beautiful 3-bedroom home with modern amenities.",
      "price": 450000.00,
      "currency": "EUR",
      "imageUrl": "https://example.com/images/item_001.jpg",
      "category": "Residential",
      "isAvailable": true,
      "location": "Ballsbridge, Dublin 4",
      "propertySizeSqm": 125.5,
      "bedrooms": 3,
      "propertyType": "APARTMENT",
      "berRating": "A2"
    }
  ],
  "page": 1,
  "pageSize": 20,
  "totalCount": 100,
  "hasMore": true
}
```

---

### Kotlin Data Classes Reference (`CatalogueDtos.kt`)

```kotlin
@JsonClass(generateAdapter = true)
data class CatalogueItemDto(
    @Json(name = "id") val id: String,
    @Json(name = "title") val title: String,
    @Json(name = "summary") val summary: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "price") val price: Double,
    @Json(name = "currency") val currency: String,
    @Json(name = "imageUrl") val imageUrl: String? = null,
    @Json(name = "category") val category: String? = null,
    @Json(name = "isAvailable") val isAvailable: Boolean = true,
    @Json(name = "location") val location: String? = null,
    @Json(name = "propertySizeSqm") val propertySizeSqm: Double? = null,
    @Json(name = "bedrooms") val bedrooms: Int? = null,
    @Json(name = "propertyType") val propertyType: String? = null,
    @Json(name = "berRating") val berRating: String? = null,
    @Json(name = "createdAt") val createdAt: String? = null,
    @Json(name = "updatedAt") val updatedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class CatalogueListResponseDto(
    @Json(name = "properties") val items: List<CatalogueItemDto> = emptyList(),
    @Json(name = "page") val page: Int = 1,
    @Json(name = "pageSize") val pageSize: Int = 20,
    @Json(name = "totalCount") val totalCount: Int = 0,
    @Json(name = "hasMore") val hasMore: Boolean = false
)
```
