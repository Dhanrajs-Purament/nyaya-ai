# Security Policy — Nyaya AI

## Why this matters here

People use Nyaya AI to ask about police cases, domestic violence, leaked images
and debt, and they use its mesh messenger where there is no internet. A security
flaw in this app can put a vulnerable person at real risk. Reports are taken
seriously and are welcome.

## Reporting a vulnerability

**Please do not open a public issue for a security problem.**

Use GitHub's private vulnerability reporting on this repository:
**Security → Report a vulnerability**. That channel is private to the
maintainers.

Please include:

- what the flaw is, and what an attacker can achieve with it;
- the steps to reproduce it;
- the app version (Settings shows it), your Android version and device;
- whether it affects the AI side, the mesh side, or stored data.

What to expect: an acknowledgement as soon as it is seen, an assessment of
severity, and a fix released as a new version with credit to you unless you
prefer to stay anonymous. This is a volunteer project, so please allow
reasonable time before disclosing publicly.

## In scope

- Anything that leaks a user's questions, chats or Case File off the device.
- Anything that lets one app or one nearby device read another user's data.
- Weaknesses in how API keys, Hugging Face tokens or mesh identity keys are
  stored or transmitted.
- Flaws in the mesh transport, the Noise handshake, or contact verification.
- A tampered or substituted model file being accepted by the downloader.
- Prompt injection that causes the app to leak stored secrets or ignore its
  safety routing — for example content in a retrieved document making the model
  reveal a stored key or suppress the emergency helplines.
- Anything that defeats the "works fully offline" property, i.e. unexpected
  network traffic in on-device mode.

## Out of scope

- **The AI being wrong.** An inaccurate or incomplete legal answer is a quality
  problem, not a vulnerability — please open a normal issue for it, with the
  question and the answer you got. It is genuinely useful feedback.
- Issues that require an already-rooted or already-compromised device, or
  physical access to an unlocked phone.
- Vulnerabilities in Gemma, LiteRT-LM, Android itself or a third-party API
  provider. Report those upstream; tell us if the app can mitigate them.
- Denial of service that needs physical proximity and only affects the attacker's
  own Bluetooth range.
- Missing hardening that has no exploit path, reported without a scenario.

## Supported versions

The latest release is supported. Because there is no server and no forced
update, please install the newest APK from the Releases page. Older versions do
not receive fixes.

## For users right now

- Use a screen lock. The app's protections cannot help if someone has your
  unlocked phone.
- Tap **New chat** after a sensitive conversation.
- Verify the APK against `SHA256SUMS.txt` on the release page.
- If you are in immediate danger, call **112** rather than using an app.
