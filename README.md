<div align="center">

<img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png" width="120" alt="Nyaya AI icon" />

# Nyaya AI

**Free legal help for India, on your phone, with no internet and no account.**

*न्याय — justice*

Gemma 4 running fully on-device · 25 complete Indian bare acts bundled offline · encrypted Bluetooth mesh messaging

[![Licence: GPL v3](https://img.shields.io/badge/Licence-GPL%20v3-blue.svg)](LICENSE.md)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-3ddc84.svg)](#install)
[![Price](https://img.shields.io/badge/Price-Free%20forever-brightgreen.svg)](#free-forever)
[![Privacy](https://img.shields.io/badge/Data%20collected-None-brightgreen.svg)](PRIVACY_POLICY.md)

[Install](#install) · [Legal library](#the-offline-legal-library) · [Privacy](PRIVACY_POLICY.md) · [Terms](TERMS_OF_USE.md) · [Licences](THIRD_PARTY_LICENSES.md) · [Contributing](CONTRIBUTING.md) · [Security](SECURITY.md)

</div>

---

## What this is

Nyaya AI answers legal questions for people in India who cannot afford a lawyer — students, workers, tenants, anyone. It does two things ordinary apps cannot:

**It works with the internet switched off.** A Gemma 4 model runs on the phone itself and the complete text of 25 Indian Acts is bundled inside the app. Ask *"police refuse to file my FIR, what are my rights?"* in airplane mode and you get an answer grounded in the actual words of BNSS Section 173, together with the free legal aid helpline.

**Nothing you type ever leaves your phone.** There is no account, no server and no telemetry. That matters for the people this is built for — someone asking about domestic violence, a leaked photo, or a police case does not want that question in anyone's logs.


It is built on top of [bitchat](https://github.com/permissionlesstech/bitchat-android), so it also carries bitchat's encrypted Bluetooth-mesh messaging: phones talk to each other directly, with no cell tower and no internet, using the Noise protocol.

### One app, two halves

Both features ship in a single APK behind a **single launcher icon**. Open **Nyaya AI** and the home screen has an **"Encrypted mesh chat"** card that opens the full bitchat messenger; Back returns you to the AI lawyer.

The messenger keeps its own activity rather than being redrawn as a screen inside the AI lawyer. That is deliberate: bitchat's `MainActivity` owns the mesh service lifecycle, the Bluetooth onboarding and permission flow, its own back-press handling and several broadcast receivers. Re-hosting its UI elsewhere would mean duplicating all of that, and **every bitchat source file in this repository is byte-identical to upstream**, which keeps future merges from upstream clean. Nyaya also listens for bitchat's force-finish broadcast, so quitting or panic-wiping closes the whole app rather than leaving the AI screen open behind it.

> **This app gives legal information, not legal advice.** It is not a lawyer and does not create a lawyer–client relationship. For anything serious — arrest, court dates, a live FIR — get a real advocate. Free legal aid is a right in India: call **NALSA on 15100**. In an emergency call **112**.

---

## The brand mark

<img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="88" align="left" hspace="16" vspace="4" alt="icon" />

The icon is one shape carrying all three halves of the product. A **shield** for encryption and privacy, the bitchat heritage. **Scales of justice** for Indian law. And the beam of those scales is a **network graph** — two amber mesh nodes with an AI node at the apex feeding into the fulcrum — so the legal symbol and the encrypted-AI-network symbol are the same object rather than two logos pushed together. Indigo shading into teal: institutional trust moving into on-device intelligence.

<br clear="left" />

---

## Two AI engines

| | On-device (default) | Cloud (bring your own key) |
|---|---|---|
| Privacy | Nothing leaves the phone | Sent to the endpoint you choose |
| Works offline | Yes | No |
| Cost | Free | Your own API billing |
| Setup | One 2.4 GB download | Paste a key |

### On-device: Gemma 4 via LiteRT-LM

| Model | Download | RAM | Notes |
|---|---|---|---|
| **Gemma 4 E2B** (default) | 2.4 GB | 4 GB+ | Recommended. Good answers on most mid-range phones. |
| **Gemma 4 E4B** | 3.4 GB | 6 GB+ | Stronger multi-step reasoning, needs a better phone. |

Both are the Apache-2.0 `.litertlm` builds from [litert-community](https://huggingface.co/litert-community), run through Google's **LiteRT-LM** runtime. They are **not licence-gated**, so no Hugging Face account or token is needed. The runtime applies each model's own chat template, tries the **GPU** first (faster and lighter on RAM) and falls back to CPU automatically.

On-device AI needs a **64-bit** phone (`arm64-v8a`). On 32-bit devices the mesh chat still works and you can use your own API key.

### Cloud: any OpenAI-compatible endpoint

OpenAI, Groq, OpenRouter, a self-hosted vLLM or Ollama — anything that speaks `/chat/completions`. Your key is stored in Android Keystore-encrypted preferences and is only ever sent to the endpoint you configured.

---

## The offline legal library

**36 files, ~12 MB, bundled inside the APK.** No download, indexed at launch, searchable with the network off.

**25 complete bare acts**, fetched from official Government of India sources (almost all from `indiacode.nic.in`), with the source URL recorded in every file:

- **Criminal law** — Bharatiya Nyaya Sanhita 2023, Bharatiya Nagarik Suraksha Sanhita 2023, Bharatiya Sakshya Adhiniyam 2023
- **Constitutional & legal aid** — Constitution of India, Legal Services Authorities Act 1987
- **Civil** — Contract Act 1872, Specific Relief Act 1963, Transfer of Property Act 1882, Code of Civil Procedure 1908, Limitation Act 1963
- **Family** — Hindu Marriage Act 1955, Hindu Succession Act 1956, Special Marriage Act 1954, Protection of Women from Domestic Violence Act 2005, Dowry Prohibition Act 1961
- **Consumer & digital rights** — Consumer Protection Act 2019, Information Technology Act 2000, DPDP Act 2023, RTI Act 2005
- **Corporate & tax** — Companies Act 2013, Insolvency and Bankruptcy Code 2016, CGST Act 2017, **Income-tax Act 1961**, **Income-tax Act 2025**
- **Everyday** — Motor Vehicles Act 1988

Plus **10 curated plain-language guides** that bridge everyday words to statute language — emergency helplines, police procedure and FIR, arrest rights, free legal aid, fundamental rights, consumer complaints, data protection, RTI, and online harassment / leaked photos / cyber crime.

### Both income-tax regimes, clearly labelled

India replaced the 1961 Act with the **Income-tax Act, 2025**, in force from **1 April 2026**. Both are bundled, because the old Act still governs every earlier year and any assessment, appeal or prosecution about those years. Each file opens with a status banner that the retriever surfaces alongside the law itself:

- `31_full_income_tax_act_1961_OLD_repealed.md` — **OLD LAW, REPEALED.** Use only for assessment years up to 2025-26.
- `34_full_income_tax_act_2025_NEW_in_force.md` — **NEW LAW, IN FORCE.** Cite this for current questions, and never carry a 1961 section number across, because the numbering differs.

A test enforces those labels, so the app cannot ship with the two regimes confused.

### How retrieval works

Every `.md` file is split into heading-aligned passages and ranked with **Okapi BM25** (`k1=1.2`, `b=0.75`). Length normalisation matters here: the two income-tax acts alone are ~4.5 MB of the library, and without it they drowned out consumer and cyber questions. The top passages are injected into the model's prompt with an instruction to cite section numbers **only** from those extracts, which is what stops the model inventing sections.

### Refreshing the library

```bash
pip install pypdf
python3 tools/kb/fetch_full_kb.py           # all 25 acts
python3 tools/kb/fetch_full_kb.py --only bns
```

The fetcher validates that every download is a real PDF before writing it, retries with backoff, and if a pinned India Code URL has rotted it re-discovers the current one by searching India Code by title. It exits non-zero if any act fails.

---

## Free forever

Nyaya AI is **free of charge to every user, permanently**. No subscription, no paid tier, no advertising, no in-app purchase, no "pro" version, no data sold. It was built to help people who cannot afford a lawyer, and charging them would defeat the point.

It is also **free software** under the GNU General Public License v3.0 or later: you may use it, read it, modify it and share it. If you distribute a modified version you must pass those same freedoms on. See [LICENSE.md](LICENSE.md) and [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md).

---

## Documentation

| Document | What it covers |
|---|---|
| [PRIVACY_POLICY.md](PRIVACY_POLICY.md) | What is stored, what never leaves your phone, every permission and why |
| [TERMS_OF_USE.md](TERMS_OF_USE.md) | Legal disclaimer, acceptable use, no-warranty terms |
| [LICENSE.md](LICENSE.md) | GNU GPL v3.0 full text |
| [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md) | bitchat, Gemma 4, LiteRT-LM, every library, and the legal texts' provenance |
| [SECURITY.md](SECURITY.md) | How to report a vulnerability privately |
| [CONTRIBUTING.md](CONTRIBUTING.md) | How to help, including without writing code |
| [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) | Community standards |
| [CHANGELOG.md](CHANGELOG.md) | Release history |
| [docs/nyaya/ARCHITECTURE.md](docs/nyaya/ARCHITECTURE.md) | Deeper technical design |

---

## Install

Go to [**Releases**](https://github.com/Dhanrajs-Purament/nyaya-ai/releases) and download the file marked **RECOMMENDED**. If you are unsure, that is the one.

| File | Who it is for | Size |
|---|---|---|
| `…-1-RECOMMENDED-most-phones.apk` | **Start here.** Every Android phone sold in roughly the last eight years. | 39 MB |
| `…-2-BACKUP-works-on-all-phones.apk` | Only if the recommended file refuses to install. Same app, contains every CPU type, so it is larger. | 93 MB |
| `…-3-old-32-bit-phones.apk` | Very old budget phones. Mesh chat works; **offline AI does not run on 32-bit devices.** | 16 MB |
| `…-4-emulator-and-ChromeOS-64-bit.apk` | Developers, and some ChromeOS devices. | 44 MB |
| `…-5-emulator-32-bit.apk` | Developers only. | 21 MB |

All of them are the same app — they differ only in which CPU type they carry.

Then: tap the downloaded file → allow **"install from unknown sources"** when Android asks (this app is not on Play Store) → open **Nyaya AI** → **Settings** → pick a model → **Download & load model** over Wi-Fi → ask your question.

Prefer not to download 2.4 GB? Skip the model and paste your own API key in Settings instead.

Verify a download against `SHA256SUMS.txt` on the release page:

```bash
sha256sum -c SHA256SUMS.txt --ignore-missing
```

---

## Build from source

Requires JDK 17+, Android SDK with platform 35 and build-tools 35, and about 3 GB of disk.

```bash
git clone https://github.com/Dhanrajs-Purament/nyaya-ai.git
cd bitchat-android
echo "sdk.dir=$ANDROID_HOME" > local.properties

pip install pypdf && python3 tools/kb/fetch_full_kb.py   # populate the legal library

./gradlew testDebugUnitTest      # 154 unit tests
./gradlew assembleDebug          # debug APKs
./gradlew assembleRelease        # release APKs (unsigned unless configured below)
```

### Signing a release

Create `keystore.properties` at the repository root — it is git-ignored, and the release build stays unsigned without it:

```properties
storeFile=/absolute/path/to/nyaya-release.jks
storePassword=…
keyAlias=nyaya
keyPassword=…
```

### Packaging a release

Gradle names its outputs after the CPU architecture, which is meaningless to end
users. This renames them to the self-explaining names used on the release page
and writes checksums:

```bash
./gradlew assembleRelease
tools/release/package_release.sh          # -> build/release-downloads/
```

### Regenerating the launcher icon

The vector drawables in `app/src/main/res/drawable/ic_launcher_*.xml` are the source of truth. To refresh the legacy density PNGs:

```bash
pip install pillow && python3 tools/branding/render_launcher_icons.py
```

### Building on an ARM64 Linux host

Google ships `aapt2` for Linux as x86-64 only, and Robolectric has no `linux-aarch64` native runtime. Both are handled: install `qemu-user-static` and export `QEMU_LD_PREFIX=/usr/x86_64-linux-gnu`, and the build automatically switches Robolectric to its legacy graphics/SQLite backends on aarch64 Linux only, leaving x86-64 CI on the native path.

---

## Architecture

```
app/src/main/java/com/bitchat/android/nyaya/
├── ai/
│   ├── LegalKnowledgeBase.kt   BM25 retrieval over the bundled acts
│   ├── OnDeviceLlmEngine.kt    Gemma 4 via LiteRT-LM, GPU→CPU fallback
│   ├── CloudLlmEngine.kt       BYOK, any OpenAI-compatible endpoint
│   ├── AiRouter.kt             picks the engine that is ready
│   ├── NyayaModelCatalog.kt    the Gemma 4 models, sizes and magic bytes
│   ├── ModelDownloadManager.kt resumable download + integrity verification
│   └── LawyerSystemPrompt.kt   scope, honesty and safety-routing rules
├── memory/ConversationMemory.kt  rolling "Case File" for long consultations
├── settings/NyayaSettings.kt     Keystore-encrypted preferences
├── ui/                            Compose screens: home, chat, voice, settings
└── voice/VoiceManager.kt          speech in, speech out
```

**Answering a question:** the question is tokenised → BM25 finds the most relevant passages across the bundled acts → those passages plus the guardrail system prompt plus the running Case File go to whichever engine is active → the reply is appended to memory, and older turns are compacted into the Case File so a long conversation never overflows the model's context.

**Model download:** verified against the exact expected byte count and the `LITERTLM` file header before the partial file is promoted, so an interrupted 2.4 GB transfer can never masquerade as a working model. Interrupted downloads resume.

---

## Privacy

- No account, no sign-up, no analytics, no crash reporting, no ads.
- In on-device mode, questions and answers never leave the phone. There is no server to leak.
- API keys and the optional Hugging Face token are held in Android Keystore-encrypted preferences.
- Mesh messages are end-to-end encrypted with the Noise protocol.
- The only network traffic the AI features ever make is the one-time model download, and whatever you explicitly send in BYOK cloud mode.

See [PRIVACY_POLICY.md](PRIVACY_POLICY.md).

---

## Testing

154 unit tests, of which 36 cover the Nyaya module:

```bash
./gradlew testDebugUnitTest          # JVM + Robolectric
./gradlew connectedDebugAndroidTest  # needs a device: AndroidKeyStore crypto
```

The Nyaya tests run against the **real bundled legal library**, not fixtures. They prove that an FIR-refusal question retrieves BNSS Section 173 with the Superintendent-of-Police and Magistrate remedies, that the NALSA 15100 helpline is reachable, that both income-tax regimes are labelled, that every act came from an official source, that a 30-turn conversation compacts without losing facts, and that no hard-coded Gemma chat-template markers ever creep back in.

Keystore-backed crypto is tested under `src/androidTest`, because `AndroidKeyStore` does not exist on the JVM and Robolectric does not emulate it.

---

## Honest status

Built, compiling and tested — **but not yet validated on a physical phone.** The build is clean, the release APK is signed, and the offline legal retrieval is covered by passing tests. What no test here can establish is **answer quality**: whether Gemma 4 E2B actually gives good, safe legal guidance on a real device. That needs a human reading real answers, and it is the next step.

Known limitations:

- A tenancy question about a "security deposit" retrieves company law, because "securities" and "deposits" are strong company-law terms. Pinned by a test so it stays visible; needs phrase-level matching to fix.
- The Income-tax Act 2025 is not published on `indiacode.nic.in` or any reachable `.gov.in` host, so it comes from PRS Legislative Research's copy of the text as passed by Lok Sabha. The file says so.
- 2.4 GB is a heavy download for the users this is aimed at. There is no smaller Gemma 4.

---

## Credits & licence

Built on [bitchat-android](https://github.com/permissionlesstech/bitchat-android). On-device inference by [Google AI Edge LiteRT-LM](https://developers.google.com/edge/litert-lm/overview) with [Gemma 4](https://huggingface.co/google/gemma-4-E2B-it). Legal texts from [India Code](https://www.indiacode.nic.in/), the Government of India's official repository.

See [LICENSE.md](LICENSE.md). Gemma models are covered by their own terms; the `.litertlm` bundles used here are Apache-2.0.
