# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

Nothing yet — the most recent work is released as [1.10.0](#1100---2026-07-25) below.

## [1.10.0] - 2026-07-25

### Added
- **Large file transfer over Wi-Fi Aware.** Private files that are too big for the
  Bluetooth mesh (which caps at roughly 120 KB per transfer) now stream over a
  direct, Noise-encrypted Wi-Fi Aware socket in verified chunks, so photos,
  documents and larger media move in seconds instead of failing silently. New
  `nyaya/transfer/bulk/` protocol (BulkFrames, BulkTransferManager) with
  per-chunk SHA-256 verification, wired into the Wi-Fi Aware transport.
- Honest, enforced transfer limits (`MeshTransferLimits`) replacing the fictional
  50 MB cap that the mesh could never actually deliver.

### Fixed
- Hindi and Hinglish questions that previously retrieved nothing from the offline
  knowledge base are now grounded correctly.
- Hardened BYOK cloud response parsing against malformed replies.
- Repaired the release workflow, modernised CI, and dropped Jetifier.

### Changed
- README rewritten with a visual capability comparison against upstream
  bitchat, transfer-flow and answer-pipeline diagrams, and corrected build
  instructions; the privacy policy and terms of use now cover file transfers
  explicitly; the architecture document covers the fast lane design.

### Notes
- Adding the bulk fast path required changes to four bitchat transport files
  (MeshService, UnifiedMeshService, MediaSendingManager, WifiAwareMeshService), so
  those files are no longer byte-identical to upstream. The bulk path is additive
  and negotiated per peer, so iOS interoperability with the existing mesh format
  is preserved.
- 213 unit tests, 0 failures. The bulk transfer protocol cannot be exercised
  without two physical devices and has not been run on hardware.

## [1.9.1] - 2026-07-25

### Fixed
- **Leaf screens now return where you came from.** The legal library and settings
  sent the user back to the home screen unconditionally, which stranded a
  conversation in progress: it was still in memory but only reachable from the
  drawer, and an incognito conversation is never listed there, so it became a
  dead end. Entry into a leaf screen now records the previous screen.
- **The system back gesture matches the on-screen Back button.** Back from the
  library or settings finished the activity instead of returning, and back inside
  an open Act left the reader entirely. Back now closes the drawer, then the open
  document, then the leaf screen, in that order.

### Changed
- Documentation described incognito chats as "dropped from memory when you leave
  it", which overstated the implementation: they are never written to storage and
  exist only in memory, so they are gone when a new conversation starts or the app
  closes. Corrected in README, PRIVACY_POLICY, CHANGELOG and the UI notes.

## [1.9.0] - 2026-07-25

### Added
- **Saved conversations.** Your chats are kept on your phone, encrypted with a key
  held in the Android Keystore, until you delete them. Delete one from the
  navigation drawer, or all of them from Settings. Nothing is uploaded, and the
  Case File summary is saved with each conversation so reopening a long
  consultation does not lose the names, dates and FIR numbers already given.
- **Incognito chat.** Nothing written to storage, no entry in the conversation
  list, excluded from the Case File, and gone once a new conversation is started
  or the app is closed. Shown in the top bar for the whole conversation.
- **Offline legal library.** Browse and read the full text of every bundled Act,
  guides first, with repealed Acts badged. Works before any model is downloaded.
- **Navigation drawer** where Nyaya AI and Mesh chat are peer modes, with search
  over your conversations.
- **Actions sheet** behind the input bar's `+`: voice, library, mesh chat,
  settings, the incognito switch, and one tap to the NALSA legal-aid helpline
  (via the dialler — the app never places a call itself).
- **Stop button** to cancel a reply while it is being generated.
- **Copy and read-aloud** on every answer.

### Changed
- Complete UI redesign on a new design system (`ui/theme/NyayaTheme.kt`): full
  light and dark schemes, a cornflower-blue canvas gradient, a light-weight
  display type scale, pill-shaped floating controls, and a brand mark drawn in
  Compose so it scales without a second asset.
- Home screen reduced to one question and one input bar; suggestions are a single
  scrolling row rather than stacked cards.
- Assistant answers are no longer rendered in chat bubbles, which made
  multi-paragraph legal text harder to read.
- Voice mode rebuilt around a pearlescent orb driven by microphone amplitude.
- Mesh chat moved from a home-screen card into the drawer, as a peer mode.

### Fixed
- Every lint issue in the Nyaya code: the force-finish receiver is now registered
  through `ContextCompat` so the not-exported flag applies below API 33, the
  brand mark takes `Modifier` as its first optional parameter, and preference
  writes use the `edit {}` extension.
- Deprecated Compose APIs replaced at root: `LocalClipboard` in place of
  `LocalClipboardManager`, and the AutoMirrored variants of the `Article`,
  `VolumeUp` and `MenuBook` icons.
- `ChatHistoryStore` no longer depends on `android.util.Log`, so it is unit
  testable on the JVM instead of needing Robolectric.

### Notes
- Each mode owns its own data. Mesh chat's panic wipe clears mesh chat; it does
  not delete your Nyaya conversations, because those are yours to delete. Deleting
  Nyaya conversations does not touch mesh chat.
- 190 unit tests, 0 failures.

## [1.8.0] - 2026-07-25

Renamed to **Nyaya AI**. Application ID is now `in.nyaya.ai`.

### Added
- On-device Gemma 4 (E2B default, E4B optional) through Google's LiteRT-LM runtime, replacing MediaPipe. The models are ungated Apache-2.0 bundles, so offline AI no longer needs a Hugging Face account.
- Both income-tax regimes in the offline library, each with a status banner: the Income-tax Act 1961 as **repealed**, and the Income-tax Act 2025 as **in force from 1 April 2026**.
- Curated guide for online harassment, leaked photos and cyber crime, mapping everyday language onto verified BNS and IT Act sections, and recording that IT Act s.66A was struck down in *Shreya Singhal* (2015).
- Single launcher icon: bitchat's encrypted mesh messenger now opens from the Nyaya home screen. Nyaya also honours bitchat's force-finish broadcast, so panic-wipe closes the whole app.
- New brand icon: shield, scales of justice, and a mesh/AI node graph forming the scales' beam.
- Complete documentation: privacy policy, terms of use, third-party licences and attribution, security policy, contributing guide and code of conduct.
- Model downloads are verified against the expected byte count and file magic before use, and resume after interruption.

### Changed
- Retrieval now uses Okapi BM25 with length normalisation; without it the two large tax Acts swamped consumer and cyber questions.
- Android Gradle Plugin 8.10.1 to 8.13.2, Java 11 target, and the current Kotlin compilerOptions DSL. Release builds now have no R8 warnings.
- Release APKs are published under self-explaining names, with the recommended download listed first.

### Fixed
- The unit-test suite is green: the AndroidKeyStore-dependent encryption test was miscategorised as a JVM test, which Robolectric can never run, and is now an instrumentation test.
- Legal library fetcher repaired: the previous User-Agent was rejected by India Code with HTTP 403, and five pinned URLs had rotted. 25 of 25 Acts now fetch, and the fetcher re-discovers moved documents by title.
- BNS text now comes from the enacted Act on India Code rather than a Bill PDF.

## [1.4.0] - 2025-10-15
### Fixed
- fix: Resolve debug settings bottom sheet crash on some devices (Issue #472)
  - Fixed IllegalFormatConversionException in DebugSettingsSheet.kt when scrolling through debug settings
  - Corrected string formatting for debug_target_fpr_fmt and debug_derived_p_fmt string resources
  - Improved string resource parameter handling for numeric values

## [0.7.2] - 2025-07-20
### Fixed
- fix: battery optimization screen content scrollable with fixed buttons

## [0.7.1] - 2025-07-19

### Added
- feat(battery): add battery optimization management for background reliability

### Fixed
- fix: center align toolbar item in ChatHeader - passed modifier.fillmaxHeight so the content inside the row can actually be centered
- fix: update sidebar text to use string resources
- fix(chat): cursor location and enhance message input with slash command styling

### Changed
- refactor: remove context attribute at ChatViewModel.kt
- Refactor: Migrate MainViewModel to use StateFlow

### Improved
- Use HorizontalDivider instead of deprecated Divider
- Use contentPadding instead of padding so items remain fully visible

## [0.7]

### Added
- Location services check during app startup with educational UI
- Message text selection functionality in chat interface
- Enhanced RSSI tracking and unread message indicators
- Major Bluetooth connection architecture refactoring with dedicated managers

### Fixed
- **Critical**: Android-iOS message fragmentation compatibility issues
  - Fixed fragment size (500→150 bytes) and ID generation for cross-platform messaging
  - Ensures Android can properly communicate with iOS devices
- DirectMessage notifications and text copying functionality
- Smart routing optimizations (no relay loops, targeted delivery)
- Build system compilation issues and null pointer exceptions

### Changed
- Comprehensive dependency updates (AGP 8.10.1, Kotlin 2.2.0, Compose 2025.06.01)
- Optimized BLE scan intervals for better battery performance
- Reduced excessive logging output

### Improved
- Cross-platform compatibility with iOS and Rust implementations
- Connection stability through architectural improvements
- Battery performance via scan duty cycling
- User onboarding with location services education

## [0.6]

### Added
- Channel password management with `/pass` command for channel owners
- Monochrome/themed launcher icon for Android 12+ dynamic theming support
- Unit tests package with initial testing infrastructure
- Production build optimization with code minification and shrinking
- Native back gesture/button handling for all app views

### Fixed
- Favorite peer functionality completely restored and improved
  - Enhanced favorite system with fallback mechanism for peers without key exchange
  - Fixed UI state updates for favorite stars in both header and sidebar
  - Improved favorite persistence across app sessions
- `/w` command now displays user nicknames instead of peer IDs
- Button styling and layout improvements across the app
  - Enhanced back button positioning and styling
  - Improved private chat and channel header button layouts
  - Fixed button padding and alignment issues
- Color scheme consistency updates
  - Updated orange color throughout the app to match iOS version
  - Consistent color usage for private messages and UI elements
- App startup reliability improvements
  - Better initialization sequence handling
  - Fixed null pointer exceptions during startup
  - Enhanced error handling and logging
- Input field styling and behavior improvements
- Sidebar user interaction enhancements
- Permission explanation screen layout fixes with proper vertical padding

### Changed
- Updated GitHub organization references in project files
- Improved README documentation with updated clone URLs
- Enhanced logging throughout the application for better debugging

## [0.5.1] - 2025-07-10

### Added
- Bluetooth startup check with user prompt to enable Bluetooth if disabled

### Fixed
- Improved Bluetooth initialization reliability on first app launch

## [0.5] - 2025-07-10

### Added
- New user onboarding screen with permission explanations
- Educational content explaining why each permission is required
- Privacy assurance messaging (no tracking, no servers, local-only data)

### Fixed
- Comprehensive permission validation - ensures all required permissions are granted
- Proper Bluetooth stack initialization on first app load
- Eliminated need for manual app restart after installation
- Enhanced permission request coordination and error handling

### Changed
- Improved first-time user experience with guided setup flow

## [0.4] - 2025-07-10

### Added
- Push notifications for direct messages
- Enhanced notification system with proper click handling and grouping

### Improved
- Direct message (DM) view with better user interface
- Enhanced private messaging experience

### Known Issues
- Favorite peer functionality currently broken

## [0.3] - 2025-07-09

### Added
- Battery-aware scanning policies for improved power management
- Dynamic scan behavior based on device battery state

### Fixed
- Android-to-Android Bluetooth Low Energy connections
- Peer discovery reliability between Android devices
- Connection stability improvements

## [0.2] - 2025-07-09

### Added
- Initial Android implementation of bitchat protocol
- Bluetooth Low Energy mesh networking
- End-to-end encryption for private messages
- Channel-based messaging with password protection
- Store-and-forward message delivery
- IRC-style commands (/msg, /join, /clear, etc.)
- RSSI-based signal quality indicators

### Fixed
- Various Bluetooth handling improvements
- User interface refinements
- Connection reliability enhancements

## [0.1] - 2025-07-08

### Added
- Initial release of bitchat Android client
- Basic mesh networking functionality
- Core messaging features
- Protocol compatibility with iOS bitchat client

[Unreleased]: https://github.com/Dhanrajs-Purament/nyaya-ai/compare/v1.10.0-nyaya...HEAD
[1.10.0]: https://github.com/Dhanrajs-Purament/nyaya-ai/compare/v1.9.1-nyaya...v1.10.0-nyaya
[1.9.1]: https://github.com/Dhanrajs-Purament/nyaya-ai/compare/v1.9.0-nyaya...v1.9.1-nyaya
[1.9.0]: https://github.com/Dhanrajs-Purament/nyaya-ai/compare/v1.8.0-nyaya...v1.9.0-nyaya
[1.8.0]: https://github.com/Dhanrajs-Purament/nyaya-ai/releases/tag/v1.8.0-nyaya
[0.5.1]: https://github.com/permissionlesstech/bitchat-android/compare/0.5...0.5.1
[0.5]: https://github.com/permissionlesstech/bitchat-android/compare/0.4...0.5
[0.4]: https://github.com/permissionlesstech/bitchat-android/compare/0.3...0.4
[0.3]: https://github.com/permissionlesstech/bitchat-android/compare/0.2...0.3
[0.2]: https://github.com/permissionlesstech/bitchat-android/compare/0.1...0.2
[0.1]: https://github.com/permissionlesstech/bitchat-android/releases/tag/0.1
