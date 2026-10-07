# Google Play Store Release Roadmap & Backlog 🚀

This document outlines all technical and compliance requirements to publish **NextUp** on the Google Play Store.

---

## 1. Release Signing & Keystore Setup
- [ ] **Generate Production Keystore**:
  ```bash
  keytool -genkey -v -keystore nextup-release-key.jks \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -alias nextup
  ```
- [ ] **Configure Signing in `app/build.gradle.kts`**:
  Use environment variables or a local `keystore.properties` (never commit keys to Git):
  ```kotlin
  signingConfigs {
      create("release") {
          storeFile = file(System.getenv("KEYSTORE_FILE") ?: "nextup-release-key.jks")
          storePassword = System.getenv("KEYSTORE_PASSWORD")
          keyAlias = System.getenv("KEY_ALIAS") ?: "nextup"
          keyPassword = System.getenv("KEY_PASSWORD")
      }
  }
  ```
- [ ] **Build Release Android App Bundle (AAB)**:
  ```bash
  ./gradlew bundleRelease
  ```

---

## 2. Graphic Assets Checklist
Google Play Console requires specific graphic assets:
- [ ] **High-Resolution App Icon**:
  - Format: PNG (32-bit with alpha)
  - Dimensions: `512 x 512 px`
  - Max file size: 1 MB
- [ ] **Feature Graphic**:
  - Format: JPEG or PNG (no alpha)
  - Dimensions: `1024 x 500 px`
  - Purpose: Banner displayed at the top of your Play Store listing.
- [ ] **Screenshots**:
  - Phone: At least 2 screenshots, 16:9 or 9:16 aspect ratio (e.g., `1080 x 2400 px`), max 8MB each.
  - Recommended showcase:
    1. Hero Countdown Card (App Screen)
    2. Weekly Schedule & Predicted Alarms list
    3. Home Screen Widget in various sizes (1x1, 2x1, 3x2, expanded tall)

---

## 3. Play Console Privacy & Data Safety Declarations
NextUp is designed to be privacy-first and 100% offline.
- [ ] **Data Safety Questionnaire Responses**:
  - **Does your app collect or share user data?** -> **No**.
  - **Is data encrypted in transit?** -> N/A (no network transmission).
  - **Does the app access location, contacts, or accounts?** -> **No**.
- [ ] **Privacy Policy Document**:
  Host a public static privacy policy (e.g. GitHub Pages or raw GitHub markdown) stating:
  > *"NextUp operates entirely locally on your device. It does not collect, transmit, store on external servers, or share any personal data, alarm times, or behavioral statistics with third parties. All alarm event logs and routine predictions remain solely within an encrypted local SQLite database on your device."*

---

## 4. App Details & Listing Copy
- [ ] **Title**: NextUp - Alarm Countdown & Routine Widget
- [ ] **Short Description** (up to 80 chars):
  *Live countdown to your nearest alarms and smart routine learning widget.*
- [ ] **Full Description** (up to 4000 chars):
  Highlight:
  - Smart duration format (`no 0d, no 0h`, `< 1m`).
  - Adaptive Material You widgets from 1x1 up to expanded schedules.
  - Zero battery drain (no polling, no wake locks).
  - Open source (GPLv3).
- [ ] **Category**: Tools / Productivity.
- [ ] **Target Audience**: Everyone (Content rating: PEGI 3 / Everyone).

---

## 5. Production Code Shrinking & ProGuard Rules
- [ ] Enable R8 minification in `app/build.gradle.kts`:
  ```kotlin
  buildTypes {
      release {
          isMinifyEnabled = true
          isShrinkResources = true
          proguardFiles(
              getDefaultProguardFile("proguard-android-optimize.txt"),
              "proguard-rules.pro"
          )
          signingConfig = signingConfigs.getByName("release")
      }
  }
  ```
- [ ] Test release bundle on a physical device:
  ```bash
  bundletool build-apks --bundle=app/build/outputs/bundle/release/app-release.aab --output=app.apks --mode=universal
  ```
