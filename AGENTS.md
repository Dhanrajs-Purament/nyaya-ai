# Nyaya AI — Agent Guide

This document provides context, architectural insights, and development standards
for AI agents working on the **Nyaya AI** codebase (repository `nyaya-ai`).

## 1. Project Overview

**Nyaya AI** is a free, open-source, offline-first AI legal-help app for India,
built on top of the **bitchat** mesh messenger. One app, two halves:

1. **The AI lawyer** (`com.bitchat.android.nyaya`): on-device Gemma 4 via
   LiteRT-LM, BM25 retrieval over 25 bundled Indian Acts, voice mode, encrypted
   saved conversations, incognito chats, BYOK cloud mode.
2. **The encrypted mesh messenger** (everything else): bitchat's decentralized,
   off-grid communication stack — Bluetooth LE mesh, Noise XX end-to-end
   encryption, Nostr/Tor integration — carried forward complete, plus the
   file-transfer upgrades (honest mesh limits and the Wi-Fi Aware bulk fast
   lane).

**Key Technologies:**
- **Language:** Kotlin
- **UI Framework:** Jetpack Compose (Material 3)
- **Asynchronous:** Kotlin Coroutines & Flow
- **AI:** Google AI Edge LiteRT-LM (on-device Gemma 4), OkHttp (BYOK cloud mode)
- **Networking:** Bluetooth Low Energy (BLE), Wi-Fi Aware, Tor (Arti Rust bridge), OkHttp
- **Architecture:** MVVM with Clean Architecture principles
- **Build System:** Gradle (Kotlin DSL); root project name `nyaya-ai`, application ID `in.nyaya.ai`

## 2. Architecture & Directory Structure

**Root Package:** `com.bitchat.android`

### The Nyaya half — where most work happens

| Directory | Purpose |
|-----------|---------|
| `nyaya/ai/` | Retrieval (BM25), LLM engines (on-device + BYOK cloud), model download, prompts |
| `nyaya/history/` | Encrypted saved conversations (`ChatHistoryStore`) |
| `nyaya/memory/` | Long-conversation Case File and rolling summarization |
| `nyaya/settings/` | Keystore-encrypted preferences |
| `nyaya/transfer/` | Honest mesh file limits; `transfer/bulk/` is the Wi-Fi Aware fast lane |
| `nyaya/ui/` | All Nyaya Compose screens, on the `NyayaTheme` design system |
| `nyaya/voice/` | On-device speech recognition and text-to-speech |

See `docs/nyaya/ARCHITECTURE.md` for the full design, including the bulk
file-transfer frame protocol.

### The bitchat half — upstream code

| Directory | Purpose |
|-----------|---------|
| `ui/` | **Presentation Layer**: Jetpack Compose screens, themes, and ViewModels. |
| `service/` | **Core Service**: Contains `MeshForegroundService`, managing persistent background connectivity. |
| `mesh/` | **Mesh Networking**: Logic for peer discovery, advertising, and message routing. |
| `protocol/` | **Wire Protocol**: Definitions of messages exchanged between peers. |
| `crypto/` | **Security**: Cryptographic primitives and key management. |
| `noise/` | **Encryption**: Implementation of the Noise Protocol Framework for secure channels. |
| `identity/` | **User Identity**: Management of user profiles and public/private keys. |
| `features/` | **App Features**: Sub-modules for `voice`, `file`, and `media` handling. |
| `nostr/` | **Relay Integration**: Logic for Nostr protocol integration and relay management. |
| `geohash/` | **Location**: Utilities for location-based features and geohashing. |
| `net/` | **Networking**: General network utilities and abstractions. |
| `wifi-aware/` | **Wi-Fi Aware transport**: direct phone-to-phone sockets; carries the bulk fast lane. |

**Upstream boundary rule:** bitchat's source is carried forward from upstream
commit `b7f0b33` and stays mergeable with future bitchat updates. Only four
upstream files are extended (never trimmed), all for the file-transfer
upgrades: `mesh/MeshService.kt`, `mesh/UnifiedMeshService.kt`,
`wifi-aware/WifiAwareMeshService.kt`, `ui/MediaSendingManager.kt`. Do not
modify other bitchat files; changes that belong upstream should go upstream.

## 3. Non-Negotiable Rules

1. **Legal accuracy.** Never add or "correct" a section number, act name or
   legal claim from memory — verify against `app/src/main/assets/nyaya_kb/` or
   India Code. Never present a repealed provision as current law. Never remove
   or weaken the safety routing (NALSA 15100, emergency 112, "information, not
   advice").
2. **Privacy.** No analytics, crash reporting, advertising, identifiers or
   telemetry, and nothing that sends a user's question anywhere they did not
   choose. The default on-device mode must keep working with zero network.
3. **Free forever.** No paid tier, no ads, no data monetisation. GPL-3.0-or-later.

## 4. Development Standards

### Code Style
- **Kotlin**: Adhere to official Kotlin coding conventions.
- **Compose**: Use functional components. Hoist state to ViewModels where possible.
- **Coroutines**: Use `suspend` functions for all I/O operations. Strictly avoid blocking the main thread.
- **Naming**: Clear, descriptive names. Follow standard Android naming patterns (e.g., `*ViewModel`, `*Repository`, `*Screen`).

### Testing
- **Unit Tests**: Located in `app/src/test/`. Use for business logic, protocols, and utility testing. Nyaya tests live under `app/src/test/java/com/bitchat/android/nyaya/`.
- **Instrumented Tests**: Located in `app/src/androidTest/`. Use for UI, permission and Keystore-dependent testing.
- **Execution**:
  - Unit: `./gradlew testDebugUnitTest` (must be 0 failures before any PR)
  - Instrumented: `./gradlew connectedAndroidTest`

## 5. Critical Constraints & Gotchas

1. **Permissions**: The app relies heavily on dangerous runtime permissions (Location, Bluetooth Scan/Connect/Advertise, Audio Recording). Always verify permission handling patterns before adding new hardware features. The AI half must work with **no** permissions granted.
2. **Hardware Dependency**: BLE and Wi-Fi Aware are difficult to emulate. Focus on robust error handling and defensive programming; hardware behaviour can be flaky.
3. **Background Limits**: Android enforces strict background execution limits. Network operations intended to persist must be tied to the `MeshForegroundService`.
4. **Transfer constraints** (root causes, do not rediscover them the hard way):
   - The BLE fragmenting path caps a message at 256 fragments × 469 bytes (≈100 KB). `nyaya/transfer/MeshTransferLimits.kt` derives sender-side limits from these real receiver caps.
   - The Wi-Fi Aware `SyncedSocket` rejects frames over 64 KB — bulk chunks are 48 KiB plaintext so the encrypted frame stays under it.
   - The Noise session rekeys after 1,000 messages — bulk chunks are therefore encrypted with a per-transfer AES-256-GCM key exchanged inside one Noise-encrypted OFFER, not Noise-encrypted individually.
5. **Model files**: The Gemma bundle (2.4–3.4 GB) is downloaded at runtime, never committed and never bundled in the APK.

## 6. Common Tasks
- **Build Debug APK**: `./gradlew assembleDebug`
- **Run unit tests**: `./gradlew testDebugUnitTest`
- **Lint Check**: `./gradlew lint`
- **Clean Build**: `./gradlew clean`
- **Refresh the legal library**: `pip install pypdf && python3 tools/kb/fetch_full_kb.py`

---
*Note: This file is intended to assist AI agents in navigating and modifying the codebase efficiently. Always verify context by reading the actual files before making changes.*
