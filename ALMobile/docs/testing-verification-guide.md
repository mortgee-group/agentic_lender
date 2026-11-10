# Testing & Verification Guide

## 1. Overview
This document defines the testing strategy, test suites, execution commands, and verification workflows for the AL-Mobile application.

## 2. Automated Test Suite

### 2.1 Test Frameworks & Libraries
- **Unit Testing**: JUnit 4 (`junit:junit`)
- **Coroutines Testing**: `kotlinx-coroutines-test` (`StandardTestDispatcher`, `runTest`)
- **Network Testing**: MockWebServer (OkHttp) for Retrofit API repository tests
- **UI State & ViewModel Testing**: Turbine / Coroutines flow testing principles

### 2.2 Test Suites in Project
1. **Repository Tests**:
   - `ApiCatalogueRepositoryTest` (verifies API requests, DTO mapping, error handling for 404/400/500, cancellation propagation, and zero-bedroom studios / unknown future codes).
2. **ViewModel Tests**:
   - `PropertyCatalogueViewModelTest`
   - `PropertyDetailViewModelTest`
   - `TestFeatureViewModelTest`
3. **Validator Tests**:
   - `PropertyFormValidatorTest` (verifies property form input validation for location, size sqm, bedrooms, property type, and BER rating including studio `0` bedrooms and `"EXEMPT"` BER).
4. **Navigation Tests**:
   - `NavigationConfigTest`

## 3. Running Automated Tests
Execute local unit tests via Gradle:
```bash
./gradlew testDebugUnitTest
```

Build and verify assemble debug:
```bash
./gradlew assembleDebug
```

## 4. Manual Verification Workflow
1. Deploy debug build to connected Android device or emulator.
2. Verify Property Catalogue screen loads property items with compact cards displaying location and attribute badges.
3. Tap on a property item to verify navigation to the full Property Detail screen.
4. Verify feature-flagged Test / Feature playground screen via top app bar action (when `NavigationConfig.ENABLE_TEST_FEATURE` is `true`).
