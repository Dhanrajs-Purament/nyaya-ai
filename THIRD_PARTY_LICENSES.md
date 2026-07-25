# Licences and Attribution — Nyaya AI

Nyaya AI is free software. This file records the licence of the project itself,
of the code it is built on, of the AI model it runs, and of the legal texts it
bundles.

---

## 1. Nyaya AI itself

**GNU General Public License, version 3.0 or later (GPL-3.0-or-later).**
Full text: [LICENSE.md](LICENSE.md).

This is not optional. Nyaya AI is a derivative work of bitchat, which is
GPL-3.0, so the combined work must be GPL-3.0 as well. Practically, that means:

- You may use the app for any purpose, free of charge.
- You may read, study and modify the source.
- You may redistribute it, modified or not.
- **If you distribute it, you must pass on the same freedoms**: publish your
  source changes under GPL-3.0 and tell recipients where to get the source.
- It comes with **no warranty** (GPL sections 15 and 16).

The project is deliberately free of charge for users and has no paid tier.

---

## 2. bitchat — the encrypted mesh messenger

Nyaya AI is built on top of **bitchat for Android**.

- Upstream: https://github.com/permissionlesstech/bitchat-android
- Licence: **GPL-3.0**
- Every bitchat source file in this repository is byte-identical to upstream
  commit `b7f0b33`. The Nyaya AI code is additive, in
  `app/src/main/java/com/bitchat/android/nyaya/`.

Credit for the mesh protocol, the Noise transport encryption, the Nostr and Tor
integration and the messenger UI belongs to the bitchat authors and
contributors.

`GOOGLE_PLAY.md` in this repository is bitchat's own publishing authorisation
from its copyright holder and relates to bitchat, not to Nyaya AI.

---

## 3. The AI model — Gemma 4

The on-device model is **Gemma 4** (E2B and E4B), published by Google.

- Model cards: https://huggingface.co/google/gemma-4-E2B-it and
  https://huggingface.co/google/gemma-4-E4B-it
- The deployable `.litertlm` bundles the app downloads are published by
  litert-community: https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm
  and https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm
- Those bundles are published under **Apache-2.0** and are **not
  licence-gated**, which is why the app needs no Hugging Face account.
- Google's **Gemma Terms of Use** and its Prohibited Use Policy also apply to
  your use of the model weights. Read them before redistributing the weights.

**The model is not bundled in the APK.** The app downloads it on request, from
Google's/litert-community's own hosting. Nyaya AI does not redistribute Gemma
weights.

The runtime is **LiteRT-LM** (`com.google.ai.edge.litertlm:litertlm-android`),
Apache-2.0, by Google AI Edge.
Documentation: https://developers.google.com/edge/litert-lm/overview

---

## 4. The bundled legal texts

The app bundles the complete text of 25 Indian Acts, plus 10 plain-language
guides written for this project.

**Source of the Acts.** Almost all are downloaded from **India Code**
(`indiacode.nic.in`), the Government of India's official repository of Central
Acts, by `tools/kb/fetch_full_kb.py`. The exact source URL of every Act is
written into the header of its file, so any claim in the app can be traced back
to the document it came from. Two exceptions are documented explicitly:

- **The Constitution of India** comes from a Government of India CDN
  (`cdnbbsr.s3waas.gov.in`).
- **The Income-tax Act, 1961** was delisted from India Code after it was
  repealed on 1 April 2026, so it is taken from the Internet Archive's capture
  of India Code's own official PDF, made on 24 March 2026 while the Act was
  still in force.
- **The Income-tax Act, 2025** is not published on India Code or any reachable
  `.gov.in` host, so it is taken from **PRS Legislative Research**'s copy of the
  text as passed by Lok Sabha (Bill No. 104-C of 2025). The file header says so.

**Copyright status.** Under section 52(1)(q) of the Copyright Act, 1957, the
reproduction of any Act of a legislature, and of any judgment or order of a
court, tribunal or other judicial authority, is not an infringement of
copyright. Government of India material is generally reusable under the
Government Open Data Licence – India (GODL) unless stated otherwise. These texts
are reproduced here so that citizens can read the law that governs them.

**No endorsement.** The Government of India, India Code, PRS Legislative
Research and the Internet Archive have not endorsed, reviewed or approved this
app. Bundling their documents does not imply any relationship with them.

The 10 curated guides are original writing for this project and are covered by
this project's GPL-3.0 licence.

---

## 5. Libraries

All of the following are used under their own licences. This list covers the
direct dependencies; run `./gradlew :app:dependencies` for the full transitive
tree.

| Library | Licence | Used for |
|---|---|---|
| Kotlin, Kotlin Coroutines | Apache-2.0 | Language and concurrency |
| AndroidX Core, AppCompat, Activity, Lifecycle, Navigation | Apache-2.0 | Android platform support |
| Jetpack Compose, Material 3, Material Icons | Apache-2.0 | User interface |
| AndroidX Security Crypto | Apache-2.0 | Keystore-encrypted preferences |
| AndroidX CameraX, ExifInterface | Apache-2.0 | QR scanning, image handling |
| Google AI Edge LiteRT-LM | Apache-2.0 | On-device Gemma 4 inference |
| Google ML Kit Barcode Scanning | Apache-2.0 (ML Kit terms) | QR scanning |
| Google Play Services Location | Android SDK terms | BLE scanning prerequisite |
| Google Tink | Apache-2.0 | Cryptography |
| Bouncy Castle | MIT-style (Bouncy Castle Licence) | Ed25519 and crypto primitives |
| Nordic Semiconductor Android BLE Library | BSD-3-Clause | Bluetooth Low Energy |
| OkHttp | Apache-2.0 | Model download, cloud AI requests |
| Gson | Apache-2.0 | JSON |
| ZXing Core | Apache-2.0 | QR codes |
| Arti (Tor in Rust), tor-android-binary | MIT / Apache-2.0 | Optional Tor transport |
| JUnit, Robolectric, Mockito, Espresso | EPL-2.0 / Apache-2.0 / MIT | Testing only, not shipped |
| pypdf, Pillow | BSD-3-Clause / MIT-CMU | Build-time tooling only, not shipped |

---

## 6. Trademarks

"Gemma", "Google", "Android" and "Google Play" are trademarks of Google LLC.
"bitchat" belongs to its authors. Use of these names here is descriptive
attribution only and does not imply sponsorship or endorsement. Nyaya AI is not
affiliated with Google or with the Government of India.

---

## 7. Reporting a licensing problem

If you believe something here is attributed incorrectly or used contrary to its
licence, please open an issue. It will be corrected.
