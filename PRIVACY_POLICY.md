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
| Your conversations — your questions, the AI's answers, and the running "Case File" summary | Saved on this phone, encrypted with a key held in the Android Keystore | Delete one from the menu, or **Settings → Delete all conversations** |
| The downloaded AI model (2.4–3.4 GB) | App-private storage | Delete the model in Settings, or uninstall |
| Your settings, including any API key and Hugging Face token | Encrypted with a key held in the Android Keystore | Clear the field, clear app data, or uninstall |

### Your conversations belong to you

Your conversations are **kept until you delete them**. They are not uploaded, not used to train anything, not shared, and not deleted behind your back — there is no expiry and no silent clean-up of old chats.

Deleting is available in three places, so you are never more than a tap or two from removing something:

- **One conversation** — open the menu and tap the bin next to it.
- **The conversation you are reading** — start a new one, then delete the old one from the menu.
- **Everything** — **Settings → Delete all conversations.** This is permanent and the app asks you to confirm.

### Incognito chats

For anything you would rather not have on the phone at all, open the **+** menu and turn on **Incognito chat**. An incognito conversation is:

- **never written to storage**, not even encrypted;
- **absent from your conversation list** — no title, no trace in the menu;
- **dropped from memory** when you leave it;
- **kept out of the Case File summary**, so it cannot leak into a later conversation.

While it is on, the top of the screen says **"Incognito · not saved"** for the whole conversation. An incognito mode you cannot see is one you cannot trust, so it is always visible.

If you switch incognito **on** part-way through a conversation, whatever had already been saved of it is deleted.

### The two modes keep separate data

Nyaya AI and the mesh messenger are two modes of one app, and each owns its own storage:

- Deleting your Nyaya conversations does **not** touch mesh chat's messages, contacts or identity.
- Mesh chat's **panic wipe** clears mesh chat's data and identity. It does **not** delete your saved Nyaya conversations, because those are yours to delete.

If you want both gone, use each mode's own control, or uninstall the app.
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
bitchat's mesh chat works over Bluetooth with **no internet and no servers at all**. It also has optional features that do use the internet — geohash channels over Nostr relays, and Tor. See the mesh section below.

There is no telemetry, no crash reporting, no analytics SDK, no advertising SDK, and no "phone home" check of any kind. The app does not contain them.

---

## The encrypted mesh messenger, in detail

The full bitchat messenger is included in this app. Its privacy properties are described here rather than summarised away, because they differ from the AI side.

### What it stores on your device

| What | Why it exists | How long |
|---|---|---|
| **Identity key** | A cryptographic key made on first launch, so peers you mark as favourites still recognise you after a restart | Until you wipe or uninstall. **Never leaves your device.** |
| **Nickname** | The display name you pick or are given | Until you change it |
| **Favourite peers** | Public keys of people you chose to remember | Until you remove them |
| **Message history** | Only if a channel owner turned retention on. Stored encrypted on your device | Until you clear it |
| **Active connections and routing info** | To deliver messages | Forgotten when the app closes |
| **Cached messages for offline peers** | Store-and-forward, so someone out of range still gets your message | Maximum 12 hours |

### What other people can see

Nearby peers on the mesh can see your chosen **nickname**, your **ephemeral public key** (which changes each session), the **messages you send** to a public channel or directly to them, and your approximate **Bluetooth signal strength**, which is used for connection quality.

In a password-protected channel, anyone with the password sees your messages and your nickname in the member list, and the owner can see that you joined.

They cannot see your phone number, your real name, your contacts, your location or your other messages, because the app never has them.

### Encryption used

- **Noise protocol** for the private-message transport
- **X25519 / Curve25519** for key exchange
- **AES-256-GCM** for message encryption
- **Ed25519** for digital signatures, so a message cannot be forged
- **Argon2id** to derive channel passwords
- **Packet padding and cover traffic**, so message size and timing reveal little to an observer

### Location, and why it is genuinely needed

- **For Bluetooth scanning:** Android itself *requires* location permission before any app may scan for Bluetooth LE devices, because scan results could in principle be used to infer position. The app uses it only to discover nearby peers. Your location is not recorded, stored or transmitted.
- **For geohash channels (optional):** if you turn this on, the app converts your location into a **geohash** — a short string describing a coarse *area*, not a point — to find channels for your region on Nostr relays. **Your precise GPS coordinates are never sent to any server or peer.** You can use Bluetooth mesh chat and never enable this.

No location history is collected, stored or shared, ever.

### Internet, only if you choose it

Bluetooth mesh mode uses **no internet and no servers**. Two optional features do use the internet: **geohash channels**, which relay through public Nostr relays, and **Tor**, which routes that traffic through the Tor network for stronger privacy. Both are yours to switch on or leave off.

### Wiping everything

The mesh messenger has an **emergency wipe (panic mode)** that immediately destroys the identity key and stored data. In this app it closes the entire application, the AI side included. Closing the app also makes your presence on the mesh disappear.

**You remain responsible for what you send.** Messages travel directly between phones with no server, but that does not make an unlawful message lawful. See [TERMS_OF_USE.md](TERMS_OF_USE.md).

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

No system is perfect. If your phone itself is compromised, or if someone has your unlocked phone, the protections above cannot help — encryption at rest defends against an offline extraction of app storage, not against someone holding your phone with the screen unlocked. So use a screen lock, delete conversations you no longer need, and use an **incognito chat** for anything you would rather never be saved.

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
