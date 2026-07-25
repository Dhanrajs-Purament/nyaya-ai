# Privacy Policy — Nyaya AI

**Last updated: 25 July 2026**
**Applies to: Nyaya AI for Android (application ID `in.nyaya.ai`), all versions from 1.8.0**

---

## The short version

**We do not collect anything about you. There is no account, no server, no analytics, no advertising, and no tracking.**

Nyaya AI has no backend. Nobody — not us, not anyone else — receives your questions, your chats, your location, your contacts, or a list of what you asked. We could not hand over your data if we were asked to, because we never have it.

This matters more than usual for this app. People use it to ask about police cases, domestic violence, leaked photos, debt and dowry. Those questions must not exist in anyone's logs, so the app is built so that they never leave your phone.

---

## Who is responsible

Nyaya AI is a free, open-source project. There is no company operating it, no subscription and no payment of any kind. The complete source code is public, so you do not have to take this policy on trust — you can read the code and confirm it.

Under India's Digital Personal Data Protection Act, 2023, an entity that determines the purpose of processing personal data is a "Data Fiduciary". Because Nyaya AI collects and processes **no personal data on any server**, there is no personal data held by anyone for you to make a request against. Everything the app stores is on your own device and under your own control.

---

## What stays on your phone, and nothing more

All of the following is stored only in the app's private storage on your device. Android prevents other apps from reading it. None of it is transmitted anywhere.

| What | Where | How to remove it |
|---|---|---|
| Your questions and the AI's answers | App memory during the session, plus the running "Case File" summary | Tap **New chat**, or clear the app's data |
| The downloaded AI model (2.4–3.4 GB) | App-private storage | Delete the model in Settings, or uninstall |
| Your settings, including any API key and Hugging Face token | Encrypted with a key held in the Android Keystore | Clear the field, clear app data, or uninstall |
| Mesh identity keys and nicknames | Encrypted with a key held in the Android Keystore | bitchat's panic/wipe action, or uninstall |

Uninstalling the app removes all of it. The app has Android backup disabled, so none of this is copied into a cloud backup.

---

## The only times the app uses the network

The app is designed to work with the internet switched off. There are exactly three situations where it makes a network connection, and you control all three.

**1. Downloading the AI model — once, if you choose to.**
When you tap "Download & load model", the app fetches the model file from Hugging Face (`huggingface.co`). Hugging Face will see your IP address and the fact that a file was downloaded, as with any file download from any website. **Your questions are not involved — this happens before you ask anything.** After this one download, the AI works with the network off, permanently. You can skip this entirely.

**2. Cloud AI mode — only if you deliberately turn it on.**
If you enter your own API key in Settings, your questions and the retrieved legal extracts are sent to the endpoint **you** configured (for example OpenAI, Groq, OpenRouter, or your own server). In this mode your data leaves your phone and is handled under **that provider's** privacy policy, not this one. The app never sends anything to any endpoint you did not enter yourself. On-device mode is the default; cloud mode is off unless you switch it on.

**3. The mesh messenger's optional internet features.**
bitchat's mesh chat works over Bluetooth with no internet. It also has optional features that use the internet (Nostr relays, and Tor for privacy). Those are part of the mesh messenger and are described in bitchat's own documentation.

There is no telemetry, no crash reporting, no analytics SDK, no advertising SDK, and no "phone home" check of any kind. The app does not contain them.

---

## The offline legal library

The complete text of 25 Indian Acts is bundled inside the app when you install it. There is no lookup, no search request and no server involved — searching the law happens entirely on your phone, and no record of what you searched for is created anywhere.

---

## Permissions, and why each is needed

The app asks only for what a feature actually needs, and only when you use that feature.

- **Microphone** — only for voice mode, and only while you are speaking. Speech recognition uses Android's own service on your device. Audio is not recorded to a file or uploaded by this app.
- **Bluetooth and Nearby devices** — for the mesh messenger to find phones near you.
- **Location** — Android **requires** location permission before any app is allowed to scan for Bluetooth devices. This app does not use your location for anything else, does not store it, and does not send it anywhere. If you never use mesh chat, you never need to grant it.
- **Notifications** — to show incoming mesh messages and the mesh service status.
- **Camera** — only for scanning a QR code to verify a contact in mesh chat.
- **Storage / media** — only for files you explicitly choose to share in mesh chat.

You can refuse any of these. Refusing a permission disables the feature that needs it and nothing else. The AI legal help works with **no** permissions granted at all.

---

## Children and students

This app is intended to be genuinely useful to students and young people, and that places extra duties on it.

- The app collects no personal data from anyone, of any age. There is no sign-up, so no name, age, school, email or phone number is ever requested.
- It is **not designed for children under 13** to use alone.
- If you are under 18 and dealing with something serious — police contact, abuse, threats, or anything involving your safety — please involve a parent, guardian, teacher or another trusted adult, and use the helplines below. The app is a source of information; it is not a substitute for an adult who can act on your behalf, and it will tell you the same thing.
- If the situation involves sexual content or images of anyone under 18, contact the police or **CHILDLINE 1098** immediately, and do not forward the material to anyone.

---

## Security

Your API keys, tokens and mesh identity keys are encrypted using a key held in the Android Keystore, which is backed by your device's secure hardware where available. Mesh messages are end-to-end encrypted using the Noise protocol. Screenshots of the app are disabled in the Android recents screen.

No system is perfect. If your phone itself is compromised, or if someone has your unlocked phone, the protections above cannot save your chat history — so use a screen lock, and use **New chat** to clear a sensitive conversation when you are done.

To report a security problem, see [SECURITY.md](SECURITY.md).

---

## Changes to this policy

If this policy changes, the updated version will be published in this repository with a new date at the top, and the change will be noted in [CHANGELOG.md](CHANGELOG.md). Because the app has no server, we cannot notify you directly — please check here.

---

## Emergency and free help in India

- **Emergency: 112**
- **Free legal aid (NALSA): 15100**
- **Women's helpline: 181**
- **Children (CHILDLINE): 1098**
- **Cyber crime: 1930** or **cybercrime.gov.in**

---

## Questions

Open an issue in this repository. Please do not include personal details, case facts or identifying information in a public issue.
