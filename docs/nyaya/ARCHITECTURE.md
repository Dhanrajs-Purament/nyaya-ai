# Nyaya AI — Architecture

Nyaya (न्याय, "justice") is an offline-first AI legal-help layer built on top of the
bitchat-android mesh messenger. The entire bitchat stack (BLE mesh, Noise XX E2EE,
Nostr fallback, geohash channels, emergency wipe) is carried forward complete —
Nyaya lives in its own package `com.bitchat.android.nyaya` and its own launcher
activity. A small, documented set of upstream files is extended (never trimmed)
for the file-transfer upgrades described below.

## Why on top of bitchat-android

- MIT-licensed, 100% protocol-compatible Kotlin port of bitchat (the upstream fork
  `Dhanrajs-Purament/bitchat` is the Swift/iOS codebase and is used as a protocol
  reference only — Android cannot be built from Swift).
- We inherit peer-to-peer chat with real bitchat users for free.

## Module layout

```
com.bitchat.android.nyaya/
├── NyayaActivity.kt          # Launcher activity, permission handling, theme
├── ai/
│   ├── LlmEngine.kt          # Engine abstraction (ChatTurn, LlmEngine)
│   ├── PromptBuilder.kt      # Gemma-format prompt assembly
│   ├── OnDeviceLlmEngine.kt  # MediaPipe LLM Inference (Gemma .task/.litertlm)
│   ├── CloudLlmEngine.kt     # BYOK: any OpenAI-compatible endpoint over HTTPS
│   ├── AiRouter.kt           # Picks on-device vs cloud engine per settings/network
│   ├── ModelDownloadManager.kt # Resumable in-app model download (HF token aware)
│   └── LawyerSystemPrompt.kt # Indian legal guardrails + anti-hallucination rules
├── history/
│   ├── ChatHistoryStore.kt   # Encrypted saved conversations (EncryptedFile, atomic rewrite)
│   └── SavedChat.kt          # Transcript + Case File carried together
├── memory/
│   └── ConversationMemory.kt # Rolling recursive summarization + persistent Case File
├── transfer/
│   ├── MeshTransferLimits.kt # Honest mesh size limits derived from the real receiver caps
│   └── bulk/
│       ├── BulkFrames.kt         # Fast-lane wire format + AES-256-GCM framing
│       └── BulkTransferManager.kt# Chunked send/receive, resume, SHA-256, timeouts
├── voice/
│   └── VoiceManager.kt       # On-device STT (SpeechRecognizer, offline preferred)
│                             # + on-device TTS (android TextToSpeech) — zero cost
├── settings/
│   └── NyayaSettings.kt      # EncryptedSharedPreferences (Keystore-backed) for BYOK
└── ui/
    ├── NyayaViewModel.kt     # State: messages, engine status, download progress
    ├── NyayaHomeScreen.kt    # "Namaste" greeting + pill input (Gemini-style)
    ├── NyayaChatScreen.kt    # Chat bubbles + disclaimer banner
    ├── VoiceModeScreen.kt    # Full-screen voice conversation mode
    └── SettingsSheet.kt      # Engine mode, BYOK keys, model download
```

## AI engines

1. **On-device (default, offline)** — Google MediaPipe LLM Inference API running
   Gemma mobile bundles (e.g. Gemma 3n E2B / Gemma 4 E2B `.task` / `.litertlm` from
   the `litert-community` Hugging Face org). Model is downloaded once in-app
   (~2–3 GB) into app-private storage and then everything runs with zero network.
2. **BYOK cloud (optional)** — user pastes their own API key for any
   OpenAI-compatible endpoint (OpenAI, Gemini via compat proxy, Groq, OpenRouter,
   self-hosted). Key is stored in Android-Keystore-encrypted preferences and never
   leaves the device except to the user's chosen endpoint.
3. **Hosted tier (later)** — our own backend; not part of V1.

## Anti-hallucination / long-context strategy

- **Rolling recursive summarization**: when history grows past a token budget, the
  older half of the transcript is summarized by the model itself into a running
  summary that is re-injected as context (arXiv 2308.15022 pattern).
- **Case File**: durable facts (names, dates, FIR numbers, statutes discussed) are
  kept in a structured note that survives summarization and is prepended each turn.
- **Guardrail prompt**: the system prompt forbids invented citations, forces
  "I am not sure" over guessing, and requires clarifying questions.

## Voice (free, offline)

V1 uses Android's built-in on-device speech stack: `SpeechRecognizer` with
`EXTRA_PREFER_OFFLINE` and `TextToSpeech` (device voices, incl. Hindi and other
Indian languages where installed). No API cost. V1.1 upgrade path: sherpa-onnx
(Kokoro/Piper voices) for higher quality fully-bundled voices.

## File transfer: honest limits and the Wi-Fi fast lane

### The root problem

Every bitchat send — even over the Wi-Fi Aware TCP socket — goes through the
BLE-oriented fragmenting sender: 469-byte fragments with a hard cap of 256
fragments per message, a practical ceiling of roughly **100 KB** per file. The
old sender-side check accepted files up to a nominal 50 MB, so a large send
failed *silently* on the receiving side, leaving a stuck progress bar. Two
fixes, both at the root:

### Tier 0 — honest mesh limits (`transfer/MeshTransferLimits.kt`)

Sender-side validation is derived from the **real receiver reassembly caps**,
not an invented number. A file that cannot be delivered is refused *before*
sending, with a visible system message in the same conversation explaining why
and what to do. Oversized images are re-compressed down a quality ladder until
they fit instead of failing.

### Tier 1 — the Wi-Fi fast lane (`transfer/bulk/`)

When both phones have Wi-Fi Aware and an established Noise session, large
private files (up to **256 MB**) bypass the fragmenting path entirely and
stream over the existing Wi-Fi Aware `SyncedSocket` as a separate framed bulk
channel:

- **Frame format** (`BulkFrames.kt`): every bulk frame starts with a magic
  header that can never collide with a `BitchatPacket`, so both traffic types
  share one socket safely. Frame types: OFFER, ACCEPT, DECLINE, CHUNK, ACK,
  COMPLETE, CANCEL.
- **Key exchange inside Noise**: the OFFER (file name, MIME type, size,
  SHA-256, chunk plan, and a **fresh per-transfer AES-256-GCM key**) is
  encrypted with the Noise session. Chunks themselves are AEAD-encrypted with
  that per-transfer key — deliberately *not* Noise-encrypted individually,
  because a 5,000-chunk file would burn through Noise's rekey message budget
  (1,000 messages).
- **Authenticated framing**: frame type and transfer ID are bound as AAD on
  every encrypted frame, so a frame rewritten as another type or redirected at
  another transfer fails closed.
- **Chunking**: 48 KiB plaintext chunks, streamed from disk — the socket layer
  caps frames at 64 KB and the largest encrypted chunk frame stays safely
  under it. Nothing is ever held fully in memory.
- **Flow**: receiver checks free storage before ACCEPT; ACKs every 16 chunks
  drive the same progress UI as mesh transfers; COMPLETE is sent only after
  the receiver's own SHA-256 of the assembled file matches the offer.
- **Resume**: an interrupted transfer resumes via re-OFFER — the receiver
  replies with how many chunks it already holds.
- **Timeouts**: 30 s to accept, 60 s to confirm completion, 120 s idle sweep —
  so a peer on an old build produces a clean, visible failure, never a hang.
- **Fallback**: no Wi-Fi Aware, or no Noise session → Tier 0 behaviour
  (auto-fit images, visible explanation for everything else).

Upstream files extended for this: `mesh/MeshService.kt` (interface defaults),
`mesh/UnifiedMeshService.kt` (delegation), `wifi-aware/WifiAwareMeshService.kt`
(frame routing + delivery), `ui/MediaSendingManager.kt` (bulk-first send paths).

## UI layer

One design system, `ui/theme/NyayaTheme.kt`, owns colour, type, shape and the
brand gradients for every Nyaya screen. Before v1.9.0 each screen hard-coded its
own hex values, which is why a light theme was impossible; screens now read from
`MaterialTheme` and `NyayaTheme.gradients` only.

| File | Responsibility |
|---|---|
| `ui/theme/NyayaTheme.kt` | Light and dark colour schemes, type scale, shapes, canvas/hero/orb gradients |
| `ui/components/NyayaBrandMark.kt` | The four-point spark, drawn in Compose so it scales without an asset |
| `ui/components/NyayaTopBar.kt` | Drawer button, active-engine chip, incognito indicator, new chat |
| `ui/components/NyayaInputBar.kt` | Floating pill: actions, field, mic, and send *or* stop |
| `ui/components/NyayaDrawer.kt` | Mode switch, conversation search and list, library, settings |
| `ui/components/NyayaActionsSheet.kt` | The `+` sheet: voice, library, mesh, settings, incognito, helpline |
| `ui/components/VoiceOrb.kt` | Pearlescent orb driven by microphone amplitude |
| `ui/NyayaHomeScreen.kt` | Hero, suggestions, model setup |
| `ui/NyayaChatScreen.kt` | Transcript, unbubbled answers, copy and read-aloud |
| `ui/VoiceModeScreen.kt` | Hands-free mode |
| `ui/LegalLibraryScreen.kt` | Browse and read the bundled Acts |
| `ui/SettingsSheet.kt` | Engine, model, BYOK, voice, delete-all-conversations |

Navigation is a single `NyayaScreen` enum inside `NyayaActivity`, wrapped in a
`ModalNavigationDrawer`. There is no navigation graph: five screens with no deep
links or argument passing do not need one, and the enum keeps the whole flow
readable in one file.

## Conversation storage

`history/ChatHistoryStore.kt` keeps the user's conversations in one JSON document
written through `EncryptedFile`, rewritten atomically after each completed reply.
`history/SavedChat.kt` carries the transcript **and** the Case File summary —
without the latter, reopening a compacted conversation would silently lose the
facts the user already provided.

Two rules are structural rather than conventional:

- The store is pure Kotlin behind a `ChatFileCodec` seam, so its logic is unit
  tested on the JVM; the Android-specific encryption sits in the codec.
- `upsert` refuses to write a chat flagged incognito, and a test asserts the file
  is never even created.

Each mode owns its data: bitchat's panic wipe clears bitchat's, and Nyaya's
conversations are removed only by the user, from the drawer or Settings.

## Security

- BYOK keys: EncryptedSharedPreferences (AES-256-GCM, Android Keystore master key).
- Model + chats: app-private storage; `allowBackup=false` inherited from bitchat.
- Mesh chat security is bitchat's Noise XX (unchanged).
- Large file transfers: fresh per-transfer AES-256-GCM key exchanged inside the
  Noise session; per-frame AEAD with type and transfer ID bound as AAD; SHA-256
  verified end-to-end; direct phone-to-phone with no server or relay.
- Disclaimer: Nyaya provides **legal information, not legal advice** (Advocates Act
  1961); it routes users to NALSA free legal aid (helpline 15100) for representation.
