# Codebase Audit & Security Inspection Report

**Application:** Nirbhaya-Who Safe (Package: `com.womensafety.app`)  
**Audit Type:** Full Defensive Security, Architecture, Logic, and Code Quality Audit  
**Auditor:** Senior Android Security Reviewer & Systems Engineer  
**Date:** October 18, 2026  
**Scope:** Entire repository (Root files, Gradle configurations, Android manifests, active `app` module, orphaned root `src` module, resources, and build scripts).

---

## 1. Project Overview

### 1.1 Application Purpose
Nirbhaya-Who Safe is an Android personal emergency and safety response application designed to operate in two roles:
1. **Sender (Device Owner / Wearable Companion):** Connects conceptually with an IoT pendant device (or hardware SIM). Upon pressing a manual in-app SOS button or receiving an incoming SMS trigger (`emergency`) from a designated IoT SIM number, the app dispatches emergency SMS notifications to pre-configured emergency contacts.
2. **Receiver (Alert Contact):** Installed on trusted family/friend devices. It listens in the background for incoming emergency SMS alerts, and upon detection, triggers a foreground service that sounds a high-decibel alarm buzzer (`STREAM_ALARM`) and activates a repeating Morse code vibration pattern while displaying a full-screen emergency alert over the lock screen.

### 1.2 Tech Stack & Build Configuration
* **Language:** Kotlin (`1.9.10`)
* **JVM Target / Compatibility:** Java 11 (`JavaVersion.VERSION_11`, `jvmTarget = "11"`)
* **Android Gradle Plugin (AGP):** `8.5.0`
* **Gradle Wrapper:** `8.7`
* **Compile SDK:** 34 (Android 14)
* **Target SDK:** 34 (Android 14)
* **Min SDK:** 26 (Android 8.0 Oreo)
* **UI Toolkit:** Jetpack Compose (BOM / UI `1.5.4`, Material3 `1.1.2`, Foundation `1.5.4`, Icons Extended `1.5.4`, Activity Compose `1.8.0`, Compose Compiler Extension `1.5.3`)
* **Key Libraries & Versions:**
  - AndroidX Core KTX: `1.12.0`
  - AndroidX AppCompat: `1.6.1`
  - Material Components: `1.10.0`
  - Room Database: `2.6.1` (with KSP `1.9.10-1.0.13`)
  - Kotlinx Coroutines: `1.7.3` (Android & Core)
  - AndroidX Lifecycle: `2.6.2` (Runtime, ViewModel, Compose)
  - AndroidX Security Crypto: `1.1.0-alpha06` (`EncryptedSharedPreferences`)
  - Unused Declared Dependencies: `androidx.navigation:navigation-compose:2.7.5`, `androidx.datastore:datastore-preferences:1.0.0`
* **Networking & Third-Party SDKs:** None. The app is entirely local and cellular/SMS-based. No Firebase, no analytics, no ads, no crash reporting, no payment gateways.

### 1.3 Module & Folder Structure
The repository contains a disjointed structure resulting from an incomplete migration or refactoring:
```
<root>/
├── AndroidManifest.xml                  # Orphaned legacy manifest
├── build.gradle.kts                     # Root build file (empty, 2 lines)
├── gradle.properties                    # JVM memory and build cache settings
├── gradlew.bat                          # Gradle wrapper batch script
├── INSTALLATION_SCRIPT.bat              # ADB installation script
├── local.properties                     # COMMITTED local SDK path (defect)
├── loud-emergency-alarm-54635.mp3       # 2.37 MB duplicate audio file in root (defect)
├── proguard-rules.pro                   # Orphaned root ProGuard rules
├── QUICK_SHARE_GUIDE.txt                # Plaintext guide exposing keystore password (defect)
├── settings.gradle.kts                  # Includes only ':app' module
├── gradle/wrapper/                      # Gradle wrapper JAR and properties
├── app/                                 # ACTIVE module compiled by Gradle
│   ├── build.gradle.kts                 # Main module build configuration
│   ├── proguard-rules.pro               # Active ProGuard/R8 obfuscation rules
│   ├── release-key.jks                  # COMMITTED production signing key (defect)
│   ├── release-keystore.jks             # COMMITTED production release keystore (defect)
│   ├── release/                         # Pre-built release APK and baseline profiles
│   │   ├── app-release.apk
│   │   └── output-metadata.json
│   └── src/main/
│       ├── AndroidManifest.xml          # Active production manifest
│       ├── java/com/womensafety/app/    # Active Kotlin source files
│       │   ├── BatteryOptimizationHelper.kt
│       │   ├── EmergencyAlertActivity.kt
│       │   ├── MainActivity.kt
│       │   ├── MainViewModel.kt         # Dead code (superseded by SimpleMainViewModel)
│       │   ├── PermissionManager.kt
│       │   ├── SimpleMainViewModel.kt
│       │   ├── SOSAlertManager.kt
│       │   ├── SOSManager.kt
│       │   ├── XiaomiAutostartHelper.kt
│       │   ├── data/
│       │   │   ├── ContactRepository.kt
│       │   │   ├── OTPManager.kt        # Dead code (superseded by SimpleOTPManager)
│       │   │   ├── SecurePreferences.kt
│       │   │   ├── SimpleOTPManager.kt
│       │   │   ├── database/
│       │   │   │   ├── AppDatabase.kt
│       │   │   │   └── ContactDao.kt
│       │   │   └── models/
│       │   │       └── EmergencyContact.kt
│       │   ├── receiver/
│       │   │   ├── EmergencyAlarmPlayer.kt
│       │   │   ├── IoTSOSForwardingService.kt
│       │   │   ├── SOSAlertReceiver.kt
│       │   │   ├── SOSAlertService.kt
│       │   │   └── SOSBroadcastReceiver.kt
│       │   └── ui/
│       │       ├── screens/
│       │       │   ├── MandatoryPhoneNumberSetup.kt # Dead code
│       │       │   ├── OnboardingFlow.kt
│       │       │   ├── OTPVerificationScreen.kt     # Dead code
│       │       │   ├── SettingsScreen.kt
│       │       │   ├── SimpleContactManagementScreen.kt
│       │       │   ├── SimpleOTPScreen.kt
│       │       │   └── XiaomiSetupScreen.kt
│       │       └── theme/
│       │           └── Theme.kt
│       └── res/                         # Drawables, mipmaps, values, raw audio
└── src/main/kotlin/com/womensafety/app/ # ORPHANED dead code tree (17 files, completely uncompiled)
```

---

## 2. How It Works (Architecture & Flow)

### 2.1 Architectural Pattern
The active codebase follows an **MVVM (Model-View-ViewModel)** pattern with Jetpack Compose for the presentation layer, Room for persistence, and Android framework services/receivers for background execution.

```
                      ┌────────────────────────────────────────┐
                      │              MainActivity              │
                      │          (Jetpack Compose UI)          │
                      └───────────────────┬────────────────────┘
                                          │ Observes StateFlow
                                          ▼
                      ┌────────────────────────────────────────┐
                      │          SimpleMainViewModel           │
                      └─────────┬───────────────────┬──────────┘
                                │                   │
             ┌──────────────────┴────────┐          │
             ▼                           ▼          ▼
  ┌──────────────────────┐   ┌───────────────────────────┐   ┌───────────────────────┐
  │  ContactRepository   │   │     SimpleOTPManager      │   │      SOSManager       │
  └──────────┬───────────┘   └─────────────┬─────────────┘   └───────────┬───────────┘
             │                             │                             │
             ▼                             ▼                             ▼
  ┌──────────────────────┐   ┌───────────────────────────┐   ┌───────────────────────┐
  │   AppDatabase        │   │    android.telephony.     │   │   SmsManager          │
  │   (Room SQLite)      │   │        SmsManager         │   │   (Send Multipart)    │
  └──────────────────────┘   └───────────────────────────┘   └───────────────────────┘
```

* **Layers:**
  - **UI Layer:** Composable screens (`SimpleWomenSafetyScreen`, `ReceiverHomeScreen`, `OnboardingFlow`, `SimpleContactManagementScreen`, `SimpleOTPScreen`, `SettingsScreen`, `XiaomiSetupScreen`) driven by `MainActivity`.
  - **State Holder:** `SimpleMainViewModel` exposes state via `StateFlow` (`contacts`, `otpState`, `settingsState`).
  - **Data Layer:** `ContactRepository` wraps `ContactDao` and Room (`AppDatabase`). Sensitive key-value pairs are managed through `SecurePreferences` (AES-256 GCM backed by Android KeyStore).
  - **Hardware/Telephony Layer:** `SOSManager` and `SimpleOTPManager` interface directly with `android.telephony.SmsManager`.
  - **Background / Receiver Layer:** Broadcast receivers (`SOSAlertReceiver`, `SOSBroadcastReceiver`) and foreground services (`SOSAlertService`, `IoTSOSForwardingService`) interact with `PowerManager` (wake locks), `AudioManager`, and `MediaPlayer`.

### 2.2 App Startup Flow & Screen Navigation
* **Routing Mechanism:** The app does **not** use the declared `androidx.navigation.compose.NavHost`. Navigation is handled via state branching in `MainActivity.kt` (`SimpleWomenSafetyApp` and `AppContent`):
  1. **Launch:** `MainActivity.onCreate()` initializes `PermissionManager` and binds `SimpleMainViewModel`.
  2. **Onboarding Check:** If permissions are missing, or user phone number/role is blank, or the role is `"sender"` and IoT SIM number is blank:
     - Displays `OnboardingFlow` (Welcome -> Permissions Info -> Role Selection ["sender" vs "receiver"] -> Number Setup).
  3. **OEM Optimization Check:** If device manufacturer is Xiaomi/Redmi/Poco and setup has not been flagged complete, displays `XiaomiSetupScreen` to guide through MIUI Autostart and battery exemption.
  4. **Role Branching:**
     - **Receiver Mode (`userRole == "receiver"`):** Renders `ReceiverHomeScreen` showing the configured contact number, standing by for incoming alerts.
     - **Sender Mode (`userRole == "sender"`):**
       - If `otpState.isActive`: Displays `SimpleOTPScreen` (for verifying a newly added emergency contact).
       - If `showContactList`: Displays `SimpleContactManagementScreen`.
       - If `showSettings`: Displays `SettingsScreen`.
       - Default: Displays `SimpleWomenSafetyScreen` with the animated pulsing/rippling SOS button.

### 2.3 Data Flow
* **Local Storage:**
  - Emergency contacts stored in SQLite via Room database `"womensafety_database"` (`EmergencyContact` entity: id, name, phoneNumber, relationship, isActive).
  - Sensitive settings (user phone number, IoT SIM number, user role, manual delay, Xiaomi setup flag) stored via `SecurePreferences` in encrypted preferences file `"women_safety_secure_prefs"`.
  - Registration role validation sets (`registered_senders`, `registered_receivers`) stored in unencrypted `SharedPreferences` (`"women_safety_prefs"`).
* **Network & API Calls:**
  - Zero external HTTP/REST network calls. The application is completely offline.
* **Background Work, Services & Receivers:**
  - `SOSAlertReceiver`: Exported broadcast receiver listening for `android.provider.Telephony.SMS_RECEIVED`. Upon an incoming SMS:
    - If from IoT SIM and body matches `emergency`, starts `IoTSOSForwardingService`.
    - If containing SOS keywords and not from the user's own number, starts `SOSAlertService`.
  - `IoTSOSForwardingService`: Runs as an Android 14+ `specialUse` foreground service with a partial wake lock; queries active contacts from Room database and invokes `SOSManager.sendSOSSAlerts()` to forward emergency messages to all contacts.
  - `SOSAlertService`: Starts as a `specialUse` foreground service, posts an ongoing `IMPORTANCE_MAX` notification with a full-screen intent, plays an alarm tone via `EmergencyAlarmPlayer` on `STREAM_ALARM`, and vibrates continuously.
  - `SOSBroadcastReceiver`: Listens for explicit intent action `com.womensafety.app.SOS_TRIGGER` protected by signature permission `com.womensafety.app.permission.SOS_TRIGGER`.

### 2.4 Third-Party Services
* None integrated. No Firebase Cloud Messaging, Crashlytics, AdMob, Google Play Billing, or third-party analytical SDKs are present.

---

## 3. Security Issues

### Finding SEC-01 [CRITICAL]: Hardcoded Release Keystore Credentials & Committed Production Keys
* **File & Line:** 
  - `app/build.gradle.kts#L33-L36`
  - `QUICK_SHARE_GUIDE.txt#L124-L125`
  - Files on disk: `app/release-key.jks`, `app/release-keystore.jks`
* **Vulnerability:** The release signing configuration embeds plaintext keystore passwords (`andr****`), key alias (`womensafety`), and key passwords (`andr****`). Furthermore, the binary keystore files (`release-key.jks` and `release-keystore.jks`) are committed directly to the repository. `QUICK_SHARE_GUIDE.txt` further prints the file location and plain password.
* **Exploitation / Threat Model:** Anyone obtaining access to this repository can extract the private key and produce authentic, cryptographically signed malicious updates for this application. Attackers can distribute trojanized builds that replace existing installations, intercepting emergency alerts and SMS streams without triggering OS signature mismatch errors.
* **Remediation:**
  1. Revoke and discard these compromised keystores immediately.
  2. Generate a new signing key locally or use Google Play App Signing.
  3. Store keystore paths, alias names, and passwords in environment variables or a local untracked `keystore.properties` file:
     ```kotlin
     signingConfigs {
         create("release") {
             storeFile = file(System.getenv("KEYSTORE_FILE") ?: "release-keystore.jks")
             storePassword = System.getenv("KEYSTORE_PASSWORD")
             keyAlias = System.getenv("KEY_ALIAS")
             keyPassword = System.getenv("KEY_PASSWORD")
         }
     }
     ```
  4. Add `*.jks`, `*.keystore`, and `keystore.properties` to `.gitignore`.

---

### Finding SEC-02 [HIGH]: Storage Discrepancy Causes Plaintext Storage & Loss of Sender Identification
* **File & Line:** 
  - `app/src/main/java/com/womensafety/app/receiver/SOSBroadcastReceiver.kt#L36-L37`
  - `app/src/main/java/com/womensafety/app/SimpleMainViewModel.kt#L41`, `L83-L90`
  - `app/src/main/java/com/womensafety/app/data/SecurePreferences.kt#L23`
* **Vulnerability:** `SecurePreferences` writes the user phone number into an encrypted preferences file named `"women_safety_secure_prefs"`. However, `SOSBroadcastReceiver.kt` lines 36–37 attempts to retrieve the user phone number from a regular, unencrypted SharedPreferences file named `"women_safety_prefs"`:
  ```kotlin
  val prefs = context.getSharedPreferences("women_safety_prefs", Context.MODE_PRIVATE)
  val senderphonennumber = prefs.getString("user_phone_number", "") ?: "Unknown"
  ```
  Additionally, `SimpleMainViewModel.kt` lines 83–90 writes registered sender and receiver phone numbers into unencrypted `women_safety_prefs` under `registered_senders` and `registered_receivers`.
* **Exploitation / Threat Model:**
  1. In `SOSBroadcastReceiver`, `senderphonennumber` is **always blank**, resolving to `"Unknown"`. When emergency alerts are broadcasted, contacts receive: *"Please try to contact the person at Unknown immediately"*. In a life-threatening crisis, contacts do not know whom to call.
  2. Plaintext phone numbers stored in unencrypted SharedPreferences are exposed to backup extraction or rooted device inspection.
* **Remediation:**
  Update `SOSBroadcastReceiver.kt` to read exclusively from `SecurePreferences.getUserPhoneNumber(context)`:
  ```kotlin
  val senderPhoneNumber = com.womensafety.app.data.SecurePreferences.getUserPhoneNumber(context)
      .ifBlank { "Unknown" }
  ```
  Migrate `registered_senders` and `registered_receivers` into `SecurePreferences`.

---

### Finding SEC-03 [HIGH]: Application Backup Enabled Without Encryption Rules (Data Leak via ADB)
* **File & Line:** `app/src/main/AndroidManifest.xml#L52`
* **Vulnerability:** `android:allowBackup="true"` is declared on the `<application>` tag without specifying `android:dataExtractionRules` or `android:fullBackupContent`.
* **Exploitation / Threat Model:** An attacker or forensic investigator with physical access and USB debugging enabled can execute `adb backup -f app_backup.ab com.womensafety.app`. While `SecurePreferences` data is encrypted with KeyStore-backed AES keys, the Room database `womensafety_database` is standard, unencrypted SQLite. The backup file will contain the entire SQLite database file, exposing all emergency contacts' full names, relationships, and phone numbers in plaintext.
* **Remediation:**
  Set `android:allowBackup="false"` in `app/src/main/AndroidManifest.xml`, or define XML data extraction rules to exclude Room database files:
  ```xml
  <application
      android:allowBackup="false"
      tools:replace="android:allowBackup" ...>
  ```

---

### Finding SEC-04 [HIGH]: Direct Boot Receiver Crashes Accessing Credential-Encrypted (CE) Storage
* **File & Line:** 
  - `app/src/main/AndroidManifest.xml#L102`
  - `app/src/main/java/com/womensafety/app/receiver/SOSAlertReceiver.kt#L68`, `L80`, `L174`
* **Vulnerability:** `SOSAlertReceiver` is flagged with `android:directBootAware="true"`. During Direct Boot mode (after device reboot before the user enters their PIN/Pattern), only Device Protected (DE) storage is accessible. `SOSAlertReceiver.onReceive()` calls `SecurePreferences` (which uses KeyStore-backed `EncryptedSharedPreferences` on standard Context) and `AppDatabase.getDatabase(context)` (standard CE storage).
* **Exploitation / Threat Model:** If the phone boots up and receives an incoming SMS before the user unlocks the screen, `SecurePreferences` attempts to access locked Credential-Encrypted files and Android KeyStore keys. This throws unhandled KeyStore/File exceptions, causing the receiver process to crash. Consequently, emergency alerts received during Direct Boot are completely dropped.
* **Remediation:**
  Either remove `android:directBootAware="true"` from `SOSAlertReceiver`, or split preferences into Device Protected Storage using `context.createDeviceProtectedStorageContext()` for pre-unlock alerts:
  ```xml
  <!-- In AndroidManifest.xml -->
  <receiver
      android:name=".receiver.SOSAlertReceiver"
      android:exported="true"
      android:directBootAware="false" ...>
  ```

---

### Finding SEC-05 [MEDIUM]: Complete Exposure of Incoming SMS Messages and Contacts in System Logs
* **File & Line:** 
  - `app/src/main/java/com/womensafety/app/receiver/SOSAlertReceiver.kt#L105`, `L118`
  - `app/src/main/java/com/womensafety/app/receiver/SOSAlertReceiver.kt#L69`
  - `app/src/main/java/com/womensafety/app/SOSManager.kt#L43`, `L51`
* **Vulnerability:** `SOSAlertReceiver.kt` logs the full content of **every incoming SMS received on the device** via `Log.d`:
  ```kotlin
  Log.d("SOSAlertReceiver", "Checking message: '$messageBody'")
  Log.d("SOSAlertReceiver", "SOS message check result: $isSOS for message: '$messageBody'")
  ```
  `SOSAlertReceiver.kt#L69` also prints the IoT SIM sender number, and `SOSManager.kt` prints emergency contact names.
* **Exploitation / Threat Model:** Any application with `READ_LOGS` on older/rooted Android versions, or any USB-connected computer with ADB enabled, can read all incoming SMS messages (including bank OTPs, two-factor authentication codes, personal conversations, and medical messages) transmitted to the victim's phone.
* **Remediation:**
  Remove all log statements that dump SMS body strings or phone numbers. If logging is needed in debug builds, wrap with `if (BuildConfig.DEBUG)` and log only message hashes or non-sensitive status flags:
  ```kotlin
  // Remove: Log.d("SOSAlertReceiver", "Checking message: '$messageBody'")
  if (BuildConfig.DEBUG) {
      Log.d("SOSAlertReceiver", "SMS received, length: ${messageBody.length}")
  }
  ```

---

### Finding SEC-06 [MEDIUM]: Predictable Pseudo-Random Number Generator Used for Emergency Contact OTPs
* **File & Line:** 
  - `app/src/main/java/com/womensafety/app/data/SimpleOTPManager.kt#L73`
  - `app/src/main/java/com/womensafety/app/data/OTPManager.kt#L21`
* **Vulnerability:** The OTP generation algorithm uses `kotlin.random.Random.nextInt(100000, 999999)`. `kotlin.random.Random` is a pseudo-random number generator (PRNG) that is not cryptographically secure.
* **Exploitation / Threat Model:** The PRNG state is deterministic and can be predicted if internal state or seeds are observed, allowing an attacker to anticipate OTP verification codes.
* **Remediation:**
  Use `java.security.SecureRandom`:
  ```kotlin
  private fun generateSecureOTP(): String {
      val secureRandom = java.security.SecureRandom()
      val code = 100000 + secureRandom.nextInt(900000)
      return code.toString()
  }
  ```

---

### Finding SEC-07 [MEDIUM]: In-Memory OTP Rate-Limiting Easily Bypassed by App Restart
* **File & Line:** `app/src/main/java/com/womensafety/app/data/SimpleOTPManager.kt#L27-L28`, `L175-L183`
* **Vulnerability:** `verificationAttempts` (max 3) and `lockoutUntil` (5 minutes) are stored purely as transient in-memory variables in `SimpleOTPManager`.
* **Exploitation / Threat Model:** An attacker or unauthorized individual guessing the 6-digit OTP on the device can bypass the 3-attempt limit and 5-minute lockout simply by swiping the app away from recent tasks and reopening it, resetting `verificationAttempts` to 0.
* **Remediation:**
  Persist rate-limiting counters (`verificationAttempts`, `lockoutUntil`) in `SecurePreferences` so that lockouts survive process destruction.

---

### Finding SEC-08 [MEDIUM]: ProGuard Strips Line Numbers and Exposes Security Class Names
* **File & Line:** 
  - `app/proguard-rules.pro#L135-L136`
  - `app/proguard-rules.pro#L139`
* **Vulnerability:** 
  1. Line 139 defines `-keepattributes !SourceFile,!LineNumberTable`, which strips line number tables from bytecode.
  2. Lines 135–136 explicitly instruct R8 to preserve exact class names for security components:
     ```proguard
     -keep class com.womensafety.app.data.SecurePreferences { *; }
     -keep class com.womensafety.app.data.SimpleOTPManager { *; }
     ```
* **Exploitation / Threat Model:** 
  - Stripping `LineNumberTable` makes crash reports completely unusable in production because stack traces cannot be re-traced to line numbers.
  - Preserving specific security class names points reverse engineers directly to encryption and authentication logic in decompiled APKs.
* **Remediation:**
  Update `app/proguard-rules.pro`:
  ```proguard
  # Preserve line numbers for de-obfuscation
  -keepattributes SourceFile,LineNumberTable
  -renamesourcefileattribute SourceFile

  # Remove explicit keep rules for internal security helpers so they are obfuscated
  # (Delete lines 135-136)
  ```

---

### Finding SEC-09 [LOW / INFORMATIONAL]: Fallback to Unencrypted SharedPreferences in SecurePreferences
* **File & Line:** `app/src/main/java/com/womensafety/app/data/SecurePreferences.kt#L53-L60`
* **Vulnerability:** In `createEncryptedPreferences()`, if `EncryptedSharedPreferences.create()` throws an exception, the catch block falls back to `context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)`.
* **Exploitation / Threat Model:** If Android KeyStore encounters an issue (common on certain OEM ROMs or after OS upgrades with `security-crypto:1.1.0-alpha06`), the app silently downgrades to unencrypted storage under the same file name, leaving data unencrypted without alerting the user.
* **Remediation:**
  Upgrade `androidx.security:security-crypto` to `1.0.0` (stable) or handle KeyStore corruption by wiping corrupted keys and re-initializing rather than silent unencrypted fallback.

---

## 4. Bugs & Logic Issues

### Bug BUG-01 [CRITICAL]: Severe False Negative in SOS Filter Drops Emergency Alerts Containing Numbers
* **File & Line:** `app/src/main/java/com/womensafety/app/receiver/SOSAlertReceiver.kt#L107-L110`, `L132`
* **Bug Analysis:** `SOSAlertReceiver.kt#L132` includes a broad regex in `isOTPMessage()`:
  ```kotlin
  private fun isOTPMessage(messageBody: String): Boolean {
      val normalized = messageBody.lowercase()
      return normalized.contains("otp") || ... ||
             (messageBody.matches(Regex(".*\\d{4,6}.*")) && messageBody.length < 100)
  }
  ```
  In `isSOSMessage()`, if `isOTPMessage(normalized)` is true, the message is immediately ignored:
  ```kotlin
  if (isOTPMessage(normalized)) {
      Log.d("SOSAlertReceiver", "OTP message detected, ignoring")
      return false
  }
  ```
* **Failure Scenario:** If an emergency SOS message is sent with an address, building number, room number, or postal code (e.g., *"Help! SOS! Trapped in Room 4021"*, length = 32 chars, contains 4 consecutive digits `4021`), the regex `.*\\d{4,6}.*` matches, and the length is `< 100`. The app flags this critical emergency alert as an OTP and **silently drops it**. The alarm never sounds.
* **Fix:** Check for explicit SOS keywords BEFORE checking OTP heuristics, and require OTP-specific words (like "code", "pin", "verification", "otp") before checking for 4–6 digit numbers:
  ```kotlin
  private fun isOTPMessage(messageBody: String): Boolean {
      val normalized = messageBody.lowercase()
      val hasOtpKeyword = normalized.contains("otp") ||
                          normalized.contains("verification") ||
                          normalized.contains("one time password") ||
                          normalized.contains("your code is")
      val hasDigitPattern = messageBody.contains(Regex("\\b\\d{4,6}\\b"))
      return hasOtpKeyword && hasDigitPattern
  }
  ```

---

### Bug BUG-02 [HIGH]: Double Audio Playback & Dual Alarm Streams Fight for Audio Focus
* **File & Line:** 
  - `app/src/main/java/com/womensafety/app/receiver/SOSAlertService.kt#L73`, `L76`
  - `app/src/main/java/com/womensafety/app/EmergencyAlertActivity.kt#L42-L43`
* **Bug Analysis:** 
  1. `SOSAlertService.onStartCommand()` calls `startAlarmPlayer()`, which instantiates `EmergencyAlarmPlayer` and plays `R.raw.emergency_alarm` on `STREAM_ALARM` at maximum volume while starting a repeating vibration.
  2. Simultaneously, line 76 calls `launchFullScreenAlert()`, launching `EmergencyAlertActivity`.
  3. `EmergencyAlertActivity.onCreate()` (lines 42–43) creates `SOSAlertManager(this)` and immediately calls `playEmergencyAlert(enableSound = true, enableVibration = true)`, initializing a **second** `MediaPlayer` instance playing the same raw audio on `STREAM_ALARM` and starting a **second** vibration loop.
* **Failure Scenario:** Two `MediaPlayer` instances compete for audio focus on the ALARM stream, resulting in distorted echo, audio stuttering, inconsistent volume override, and concurrent vibration conflicts.
* **Fix:** Consolidate alarm playback into a single controller. `SOSAlertService` should own the media player. `EmergencyAlertActivity` should only provide the UI and send a stop action to `SOSAlertService` when the user taps "STOP":
  ```kotlin
  // In EmergencyAlertActivity.onCreate:
  // Remove alertManager.playEmergencyAlert() completely!
  ```

---

### Bug BUG-03 [HIGH]: IoT Sim Trigger Rigid Equality Causes Missed Emergency Forwards
* **File & Line:** `app/src/main/java/com/womensafety/app/receiver/SOSAlertReceiver.kt#L95-L99`
* **Bug Analysis:**
  ```kotlin
  private fun isIotTriggerMessage(messageBody: String): Boolean {
      val normalized = messageBody.trim().lowercase()
      val isMatch = normalized == "emergency"
      return isMatch
  }
  ```
* **Failure Scenario:** The check requires an exact string match for `"emergency"`. If the IoT hardware modem sends `"EMERGENCY\r\n"`, `"Emergency!"`, `"emergency alert"`, or appends GPS coordinates / battery telemetry (e.g. `"emergency: lat=28.5 lon=77.2"`), `normalized == "emergency"` evaluates to `false`. The message is logged as `"Message from IoT SIM but not 'emergency', ignoring"` and discarded.
* **Fix:** Use a fuzzy or containment check:
  ```kotlin
  private fun isIotTriggerMessage(messageBody: String): Boolean {
      val normalized = messageBody.trim().lowercase()
      return normalized.contains("emergency") || normalized.contains("sos")
  }
  ```

---

### Bug BUG-04 [HIGH]: Permanent Scope Cancellation in Service Prevents Subsequent SOS Forwards
* **File & Line:** `app/src/main/java/com/womensafety/app/receiver/IoTSOSForwardingService.kt#L47`, `L230`
* **Bug Analysis:** `serviceScope` is declared as:
  ```kotlin
  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  ```
  In `stopServiceSafely()#L230`, `serviceScope.cancel()` is called.
* **Failure Scenario:** Once `stopServiceSafely()` runs, calling `cancel()` on a `CoroutineScope` permanently cancels its underlying `SupervisorJob`. If the Android runtime reuses the service instance or another `onStartCommand()` intent arrives before the process dies, all future `serviceScope.launch { ... }` calls will silently fail without executing.
* **Fix:** Cancel child jobs instead of cancelling the entire scope, or re-instantiate `SupervisorJob()`:
  ```kotlin
  private var serviceJob = SupervisorJob()
  private var serviceScope = CoroutineScope(serviceJob + Dispatchers.IO)

  private fun stopServiceSafely() {
      serviceJob.cancel()
      serviceJob = SupervisorJob()
      serviceScope = CoroutineScope(serviceJob + Dispatchers.IO)
      stopForeground(STOP_FOREGROUND_REMOVE)
      stopSelf()
  }
  ```

---

### Bug BUG-05 [MEDIUM]: Premature UI Success Indication Before SOS Alerts Are Dispatched
* **File & Line:** 
  - `app/src/main/java/com/womensafety/app/SimpleMainViewModel.kt#L209-L233`
  - `app/src/main/java/com/womensafety/app/MainActivity.kt#L280-L288`
* **Bug Analysis:** `triggerSOS()` launches a coroutine with `delay(settings.manualSosDelay * 1000L)` and immediately returns `true`:
  ```kotlin
  // SimpleMainViewModel.kt
  fun triggerSOS(): Boolean {
      val currentContacts = _contacts.value
      return if (currentContacts.isNotEmpty()) {
          viewModelScope.launch {
              if (settings.manualSosDelay > 0) {
                  delay(settings.manualSosDelay * 1000L)
              }
              val success = sosManager.sendSOSSAlerts(...)
          }
          true // Returns immediately!
      } else false
  }
  ```
  `MainActivity.kt` immediately displays:
  ```kotlin
  Toast.makeText(context, "SOS Alert sent to ${contacts.size} contacts!", Toast.LENGTH_SHORT).show()
  ```
* **Failure Scenario:** The user taps SOS with a 10-second manual delay configured. The UI immediately displays *"SOS Alert sent to 3 contacts!"* even though the messages haven't been sent yet. If the app is killed during the 10-second delay or SMS transmission fails, the user falsely believes help has been summoned.
* **Fix:** Represent SOS as an observable state flow (`SOSStatus.Pending(secondsRemaining)`, `SOSStatus.Sending`, `SOSStatus.Sent`, `SOSStatus.Failed`) and update the UI when transmission completes.

---

### Bug BUG-06 [MEDIUM]: Missing Delivery and Sent PendingIntents in SmsManager
* **File & Line:** `app/src/main/java/com/womensafety/app/SOSManager.kt#L41`
* **Bug Analysis:** `smsManager.sendMultipartTextMessage(contact.phoneNumber, null, parts, null, null)` is called with null `sentIntents` and null `deliveryIntents`.
* **Failure Scenario:** If the SIM card has no cellular service, no SMS balance, or carrier blocking occurs, `sendMultipartTextMessage` does not throw an exception; it fails silently at the radio layer. Because no `sentIntent` is registered, the app logs `"SOS sent"` and assumes success without knowing the SMS was undelivered.
* **Fix:** Supply `sentIntents` with a `BroadcastReceiver` listening for `Activity.RESULT_OK` / `SmsManager.RESULT_ERROR_GENERIC_FAILURE` to detect delivery failures and prompt the user.

---

### Bug BUG-07 [LOW]: Audio Focus Abandonment Uses Deprecated Call on Modern Android
* **File & Line:** `app/src/main/java/com/womensafety/app/SOSAlertManager.kt#L59`, `L118`
* **Bug Analysis:** `SOSAlertManager.kt` uses deprecated `requestAudioFocus(null, ...)` on API 26+ devices without checking SDK version, whereas `EmergencyAlarmPlayer.kt` handles `AudioFocusRequest` properly.

---

## 5. Performance Issues

### 5.1 Main-Thread Database Queries
* **File & Line:** `app/src/main/java/com/womensafety/app/receiver/SOSBroadcastReceiver.kt#L80-L82`
* **Issue:** 
  ```kotlin
  private fun getEmergencyContacts(context: Context): List<String> {
      ...
      runBlocking { repository.getActiveContactPhoneNumbers() }
  }
  ```
  `SOSBroadcastReceiver.onReceive()` executes on the main application thread. Calling `runBlocking` to fetch Room database entries synchronously halts UI rendering and risks an Application Not Responding (ANR) error if SQLite is locked.
* **Fix:** Use `goAsync()` with a coroutine launched on `Dispatchers.IO`:
  ```kotlin
  override fun onReceive(context: Context, intent: Intent) {
      val pendingResult = goAsync()
      CoroutineScope(Dispatchers.IO).launch {
          try {
              handleSOSTrigger(context)
          } finally {
              pendingResult.finish()
          }
      }
  }
  ```

### 5.2 Nested Composable In-Function Reallocations
* **File & Line:** `app/src/main/java/com/womensafety/app/MainActivity.kt#L500-L539`
* **Issue:** Inside `SimpleWomenSafetyScreen`, the `@Composable fun createRipple(offset: Int)` function is defined locally inside an `if (hasContacts)` condition inside another Composable function. This causes unnecessary recompositions and lambda allocations on every recomposition cycle.
* **Fix:** Extract `SOSRippleEffect` into a top-level `@Composable` component.

### 5.3 Redundant Heavy Asset in Application Package
* **File & Line:** `<root>/loud-emergency-alarm-54635.mp3` and `app/src/main/res/raw/emergency_alarm.mp3`
* **Issue:** The MP3 alarm file is 2.37 MB. An identical copy (`loud-emergency-alarm-54635.mp3`) is stored in the project root. The file in `res/raw/` is uncompressed in the APK, inflating app download size.
* **Fix:** Remove the duplicate file in the root. Compress the audio file in `res/raw/` using OGG/Opus or AAC format at 96kbps mono, which will reduce the 2.37 MB file to under 350 KB with identical perceived loudness.

### 5.4 Unnecessary Build Feature Overhead (`viewBinding = true`)
* **File & Line:** `app/build.gradle.kts#L23`
* **Issue:** `viewBinding = true` is enabled in `buildFeatures`, but there is **not a single XML layout** in `app/src/main/res/layout/`.
* **Fix:** Set `viewBinding = false` in `app/build.gradle.kts` to reduce build times and dex generation overhead.

---

## 6. Code Quality & Maintainability

### 6.1 Massive Dead Code & Duplicate Implementations
The repository contains two entire parallel applications and multiple abandoned classes:
1. **Root `src/main/kotlin/` Tree (17 files, ~1,500 lines of code):**
   - Contains a complete alternative implementation using `AppViewModel`, `SettingsRepository` (DataStore), and `AppNavigation`.
   - Never included in Gradle compilation (`settings.gradle.kts` only includes `:app`).
   - Represents an unmaintained legacy code branch polluting the repository.
2. **Abandoned Root Files:**
   - `<root>/AndroidManifest.xml` (42 lines)
   - `<root>/loud-emergency-alarm-54635.mp3` (2.37 MB)
   - `<root>/proguard-rules.pro`
3. **Dead Classes Inside `app/src/main/java/`:**
   - `MainViewModel.kt` (238 lines): Completely unused; `MainActivity` uses `SimpleMainViewModel`.
   - `data/OTPManager.kt` (70 lines): Completely unused; replaced by `SimpleOTPManager`.
   - `ui/screens/MandatoryPhoneNumberSetup.kt` (79 lines): Completely unused; replaced by `OnboardingFlow`.
   - `ui/screens/OTPVerificationScreen.kt` (208 lines): Completely unused; replaced by `SimpleOTPScreen`.
   - `receiver/SOSAlertReceiver.kt#L151-L220`: `triggerAutoSOS()` (70 lines): Completely uncalled; `IoTSOSForwardingService.start()` is called instead.
   - `ui/screens/SettingsScreen.kt#L270-L311`: `SettingCard` composable: Never called.

### 6.2 Hardcoded Strings Everywhere
* **File & Line:** `app/src/main/res/values/strings.xml#L1-L10`
* **Issue:** `strings.xml` contains only 9 entries. All strings throughout `OnboardingFlow.kt`, `XiaomiSetupScreen.kt`, `SettingsScreen.kt`, `SimpleContactManagementScreen.kt`, and `SimpleOTPScreen.kt` are hardcoded in English, preventing localization and accessibility customization.

### 6.3 Missing Automated Test Coverage
* **Issue:** There is **zero test code** across the entire repository. No unit tests for `SimpleOTPManager`, `ContactRepository`, or phone number matching logic; no instrumentation tests for Room database or service lifecycles.

### 6.4 Hardcoded Regional Telephony Assumptions (`+91`)
* **File & Line:** 
  - `app/src/main/java/com/womensafety/app/ui/screens/OnboardingFlow.kt#L68`, `L71`, `L192`, `L200`
  - `app/src/main/java/com/womensafety/app/ui/screens/SimpleContactManagementScreen.kt#L278`, `L306`
* **Issue:** The Indian country code `+91` is hardcoded across text fields, prefixes, and validation routines. Users entering numbers with other country codes will have invalid numbers formed (e.g. `+91+1415...`).

### 6.5 Typo in Core API Method Name
* **File & Line:** `app/src/main/java/com/womensafety/app/SOSManager.kt#L20`
* **Issue:** Method name is misspelled as `sendSOSSAlerts` with double 'S'.

---

## 7. Play Store / Compliance Risks

### 7.1 Undeclared & Unused Dangerous Permissions (`CALL_PHONE`)
* **File & Line:** 
  - `app/src/main/AndroidManifest.xml#L8`
  - `app/src/main/java/com/womensafety/app/PermissionManager.kt#L262`
  - `app/src/main/java/com/womensafety/app/ui/screens/OnboardingFlow.kt#L378`
* **Risk:** `android.permission.CALL_PHONE` is declared in the manifest, requested in `PermissionManager`, and described to users during onboarding. However, **the application never places any phone calls**.
* **Play Store Policy Impact:** Google Play enforces the *SMS and Call Log Permissions Policy*. Declaring `CALL_PHONE` without demonstrating that placing calls is a core functionality that cannot be fulfilled via `Intent.ACTION_DIAL` results in **instant, automated rejection or account strike**.
* **Remediation:** Remove `android.permission.CALL_PHONE` from the manifest and `PermissionManager`.

### 7.2 Strict Google Play SMS Policy Declaration Required
* **File & Line:** `app/src/main/AndroidManifest.xml#L6-L7`
* **Risk:** The app uses `SEND_SMS` and `RECEIVE_SMS`. Google Play strictly restricts these permissions to apps where SMS is the primary core purpose (e.g. default SMS handler or physical safety app).
* **Compliance Requirement:** The developer must submit the Google Play *Permissions Declaration Form*, selecting the "Physical Safety / Emergency Alert" use case, providing a detailed video demonstrating the IoT SMS trigger and contact dispatching.

### 7.3 Misuse of `SCHEDULE_EXACT_ALARM`
* **File & Line:** 
  - `app/src/main/AndroidManifest.xml#L31`
  - `app/src/main/java/com/womensafety/app/PermissionManager.kt#L179-L180`
* **Risk:** `SCHEDULE_EXACT_ALARM` is declared and requested, but `AlarmManager` is **never used** to schedule alarms. Google Play policy requires apps targeting Android 13+ (API 33+) to only request this permission if they are an alarm clock, timer, or calendar app. Apps declaring it without active scheduling are rejected.
* **Remediation:** Remove `SCHEDULE_EXACT_ALARM` from `AndroidManifest.xml` and `PermissionManager.kt`.

### 7.4 Dead Boot Permission (`RECEIVE_BOOT_COMPLETED`)
* **File & Line:** `app/src/main/AndroidManifest.xml#L37`
* **Risk:** `RECEIVE_BOOT_COMPLETED` is requested, but there is no broadcast receiver listening for `BOOT_COMPLETED` in the manifest. Unused permission declarations flag compliance audits.
* **Remediation:** Either implement a boot receiver to re-register alerts or remove the permission.

### 7.5 Missing Privacy Policy
* **Risk:** The application collects and processes emergency contact names and phone numbers, monitors all incoming SMS messages, and stores user and IoT SIM numbers. There is no Privacy Policy link inside the app or referenced in the repository. Google Play requires a prominent in-app privacy policy disclosure for any personal and sensitive user data access.

---

## 8. Prioritized Fix Plan

| Priority | Issue Description | Severity | File & Line | Effort | Suggested Fix |
|:---:|:---|:---:|:---|:---:|:---|
| **1** | Release Keystore & Passwords Exposed | **Critical** | `app/build.gradle.kts#L33-L36`<br>`QUICK_SHARE_GUIDE.txt#L125` | **S** | Revoke keys, remove `.jks` from git, use environment variables. |
| **2** | False Negative in SOS Filter Drops Real Alerts | **Critical** | `app/src/main/java/com/womensafety/app/receiver/SOSAlertReceiver.kt#L107-L132` | **S** | Require explicit OTP keywords in `isOTPMessage`; do not drop on digit count alone. |
| **3** | Sender Phone Always "Unknown" in SOS Broadcast | **High** | `app/src/main/java/com/womensafety/app/receiver/SOSBroadcastReceiver.kt#L36-L37` | **S** | Read `user_phone_number` from `SecurePreferences` instead of unencrypted prefs. |
| **4** | Unused `CALL_PHONE` & `SCHEDULE_EXACT_ALARM` Permissions | **High** | `app/src/main/AndroidManifest.xml#L8`, `L31` | **S** | Remove both unused permissions to avoid instant Play Store rejection. |
| **5** | Double Siren & Dual `MediaPlayer` Conflict | **High** | `app/src/main/java/com/womensafety/app/EmergencyAlertActivity.kt#L42-L43` | **S** | Remove playback from `EmergencyAlertActivity`; let `SOSAlertService` own audio. |
| **6** | Plaintext Incoming SMS Messages Logged to Logcat | **Medium** | `app/src/main/java/com/womensafety/app/receiver/SOSAlertReceiver.kt#L105`, `L118` | **S** | Remove all `Log.d` statements printing raw SMS message bodies. |
| **7** | Direct Boot Crash Accessing Credential Storage | **High** | `app/src/main/AndroidManifest.xml#L102` | **S** | Remove `android:directBootAware="true"` from `SOSAlertReceiver`. |
| **8** | Unencrypted Data Dump via `allowBackup` | **High** | `app/src/main/AndroidManifest.xml#L52` | **S** | Set `android:allowBackup="false"`. |
| **9** | Weak Pseudo-Random Number Generator for OTP | **Medium** | `app/src/main/java/com/womensafety/app/data/SimpleOTPManager.kt#L73` | **S** | Replace `kotlin.random.Random` with `java.security.SecureRandom`. |
| **10** | Premature "SOS Sent" Toast Notification | **Medium** | `app/src/main/java/com/womensafety/app/SimpleMainViewModel.kt#L209-L233` | **M** | Update UI state only when coroutine completes `sendSOSSAlerts()`. |
| **11** | Main-Thread Room Query in `SOSBroadcastReceiver` | **Medium** | `app/src/main/java/com/womensafety/app/receiver/SOSBroadcastReceiver.kt#L80-L82` | **S** | Use `goAsync()` with coroutine on `Dispatchers.IO`. |
| **12** | Rigid IoT String Matching (`== "emergency"`) | **High** | `app/src/main/java/com/womensafety/app/receiver/SOSAlertReceiver.kt#L97` | **S** | Change to `normalized.contains("emergency")`. |
| **13** | Orphaned Root `src/` Code & Duplicate MP3 | **Medium** | `<root>/src/`<br>`<root>/loud-emergency-alarm-54635.mp3` | **M** | Delete orphaned root source tree and 2.37MB duplicate MP3 file. |
| **14** | Delete Dead Code in `app/` Module | **Low** | `MainViewModel.kt`, `OTPManager.kt`, `OTPVerificationScreen.kt` | **M** | Remove unused classes and uncalled methods (`triggerAutoSOS`). |
| **15** | Add Automated Unit & Integration Tests | **High** | `app/src/test/` | **L** | Implement tests for OTP generation, phone normalization, and SMS parsing. |

---

## 9. Quick Wins (Top 10 High-Value, Low-Effort Fixes)

1. **Fix the False Negative SOS Filter (`SOSAlertReceiver.kt#L132`):**
   * Change `isOTPMessage` so that an emergency message with an address or room number (e.g., *"Help in Room 4021"*) is never discarded as an OTP.
2. **Fix the Sender Number in `SOSBroadcastReceiver.kt#L36`:**
   * Replace `context.getSharedPreferences("women_safety_prefs", ...)` with `SecurePreferences.getUserPhoneNumber(context)` so recipients receive the caller's actual phone number instead of `"Unknown"`.
3. **Remove `CALL_PHONE` and `SCHEDULE_EXACT_ALARM` Permissions (`AndroidManifest.xml`):**
   * Delete lines 8 and 31. Eliminates immediate Google Play policy rejection triggers with zero loss of functionality.
4. **Remove Double Siren from `EmergencyAlertActivity.kt#L42-L43`:**
   * Delete `alertManager.playEmergencyAlert()` inside `EmergencyAlertActivity`. Resolves audio stuttering and audio focus collision instantly.
5. **Sanitize Logcat Calls (`SOSAlertReceiver.kt#L105`, `L118`):**
   * Delete logs printing `messageBody` to prevent leaking banking OTPs and private texts to system logs.
6. **Disable Insecure Backup (`AndroidManifest.xml#L52`):**
   * Change `android:allowBackup="true"` to `android:allowBackup="false"` to prevent plaintext extraction of the Room SQLite database.
7. **Remove Direct Boot Flag from Receiver (`AndroidManifest.xml#L102`):**
   * Delete `android:directBootAware="true"` from `SOSAlertReceiver` to prevent runtime crashes prior to user device unlock.
8. **Upgrade OTP to `SecureRandom` (`SimpleOTPManager.kt#L73`):**
   * Switch from `kotlin.random.Random` to `java.security.SecureRandom()` in one line.
9. **Fix IoT Trigger Keyword Check (`SOSAlertReceiver.kt#L97`):**
   * Change `normalized == "emergency"` to `normalized.contains("emergency")` to support varied modem output.
10. **Delete Duplicate 2.37MB MP3 in Root:**
    * Delete `<root>/loud-emergency-alarm-54635.mp3`. Saves repository bandwidth and deployment clutter.

---

## Short Summary

### Overall Risk Rating: HIGH (CRITICAL in Release Operations)
While the core Compose UI is cleanly written and the application correctly leverages modern foreground service types (`specialUse` for Android 14), the project possesses **critical security and life-safety logic vulnerabilities**:
1. **Cryptographic & Keystore Compromise:** The production keystore and its passwords are committed directly into source control and deployment documentation.
2. **Life-Safety Logic Flaw:** Genuine emergency SMS messages containing building, house, or room numbers under 100 characters are incorrectly flagged as OTPs and silently dropped without triggering alarms.
3. **Storage Disconnect:** Sender identification is completely broken in `SOSBroadcastReceiver` because it queries a different, unencrypted preferences file.
4. **Play Store Non-Compliance:** Unused dangerous permissions (`CALL_PHONE`, `SCHEDULE_EXACT_ALARM`) guarantee rejection on the Google Play Store.

### Top 5 Things to Fix First:
1. **Keystore Revocation & Scrubbing:** Revoke `release-key.jks`, remove keystore files and plaintext passwords from Gradle and documentation, and inject via environment variables (`app/build.gradle.kts#L33-L36`).
2. **Fix `isOTPMessage` Filter:** Eliminate the `Regex(".*\\d{4,6}.*") && length < 100` rule in `SOSAlertReceiver.kt#L132` to prevent dropping emergency messages.
3. **Fix Phone Number Retrieval in `SOSBroadcastReceiver.kt#L36`:** Connect it to `SecurePreferences` so emergency alerts contain the user's actual phone number.
4. **Remove Unused Permissions from Manifest:** Remove `CALL_PHONE` and `SCHEDULE_EXACT_ALARM` from `app/src/main/AndroidManifest.xml`.
5. **Eliminate Duplicate Siren in `EmergencyAlertActivity.kt#L43`:** Remove redundant `MediaPlayer` instantiation to fix audio corruption during active alerts.


---

## 10. Audit Findings Resolution & Redesign Status (Redesign v2)

All items documented in the audit above have been resolved or re-architected across the phased redesign:

| Audit Finding ID / Topic | Original Issue | Status in Redesign-v2 | Resolution Detail |
|:---|:---|:---:|:---|
| **SEC-01 / SEC-02** (Exposed Keys) | Keystore files and passwords in plaintext in `app/build.gradle.kts` and docs | **RESOLVED** | Keystore credentials removed from code; signing config now cleanly reads from environment variables (`RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_PASSWORD`, etc.). |
| **SEC-03** (Unencrypted Backup) | `allowBackup="true"` permitted local ADB database dumps | **RESOLVED** | Set `allowBackup="false"` in `app/src/main/AndroidManifest.xml`. |
| **SEC-04 / SEC-05** (Plaintext Logging) | Incoming SMS bodies and phone numbers logged to Logcat | **RESOLVED** | Removed message body logging in `SOSAlertReceiver`; added `PhoneUtil.maskPhoneNumber` for UI display and logs. |
| **SEC-06 / SEC-07** (Weak OTP RNG) | Local OTP generated with pseudo-random generator | **OBSOLETE / RESOLVED** | Contact OTP verification removed; OTP architecture migrated to `OtpProvider` interface (Firebase Phone Auth stubbed). |
| **BUG-01** (False Negative SOS) | Emergency alerts dropped by OTP heuristic | **RESOLVED** | Messages identified unambiguously by `[NSOS]` protocol prefix. Heuristic deleted. |
| **BUG-02** (Double Siren Conflict) | `EmergencyAlertActivity` and `SOSAlertService` both playing audio | **RESOLVED** | Audio playback consolidated solely within `SOSAlertService` / `EmergencyAlarmPlayer`. |
| **BUG-03** (Sender Phone Disconnect) | `SOSBroadcastReceiver` queried unencrypted prefs file | **RESOLVED** | Connected to encrypted `SecurePreferences.getUserPhoneNumber`. |
| **BUG-04** (Direct Boot Crash) | `directBootAware="true"` accessing credential-encrypted storage before unlock | **RESOLVED** | Removed `directBootAware="true"` from manifest. |
| **BUG-05 / BUG-06** (Premature Sent Toast & Typo) | "SOS Sent" shown before radio transmission + typo `sendSOSSAlerts` | **RESOLVED** | Renamed to `sendSosAlerts`; status tracked via radio `PendingIntent` callbacks (`FLAG_IMMUTABLE`). |
| **COMPLIANCE** (Unused Permissions) | `CALL_PHONE` and `SCHEDULE_EXACT_ALARM` declared but unused | **RESOLVED** | Removed unused permissions from manifest to satisfy Google Play policies. |
| **ARCH-01** (Role / IoT Complexity) | Separate sender/receiver roles and fragile IoT SIM forwarding | **RESOLVED** | Single unified Home screen; IoT code, receivers, and preferences fully deleted. |
| **ARCH-02** (GPS & Follow-up) | No GPS tracking or periodic follow-ups | **RESOLVED** | Added `LocationHelper` (FusedClient -> GPS -> Network -> last known) and 2-min follow-up loop up to 30 min in `ProtectionService`. |
| **ARCH-03** (Guardian Auto-Reply) | No automatic responder | **RESOLVED** | `CallDetectionReceiver` auto-replies with `[NSAFE]` location SMS to calling guardians (with 2-min cooldown). |
| **TESTING** | No unit tests | **RESOLVED** | Unit tests implemented in `PhoneUtilTest.kt` for E.164 normalization and privacy masking. |
