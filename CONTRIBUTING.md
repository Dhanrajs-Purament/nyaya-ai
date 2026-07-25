# Contributing to Nyaya AI

Thank you for wanting to help. This app exists so that someone who cannot afford
a lawyer can still find out what their rights are. Contributions that make it
more accurate, more usable on cheap phones, or available in more Indian
languages are the most valuable kind.

You do not have to be a programmer to help. See "Ways to help that are not code"
below.

---

## Ground rules

**Legal accuracy is not negotiable.** This is the one rule with no exceptions.

- Never add, change or "correct" a section number, act name or legal claim from
  memory. Verify it against the official text in `app/src/main/assets/nyaya_kb/`
  or against India Code, and say in your pull request where you checked.
- Never present a repealed provision as current law. If an Act has been replaced,
  both texts must be labelled, as the two Income-tax Acts are.
- Never remove or weaken the safety routing: the free legal aid helpline
  (**15100**), the emergency number (**112**), or the "this is information, not
  advice" framing. Those exist to get a scared person to real help.
- If you are not sure whether something is current law, say so in the text rather
  than guessing. An honest "check this with a lawyer" is better than a confident
  error.

**Do not weaken privacy.** No analytics, no crash reporting, no advertising, no
identifiers, no telemetry, and nothing that sends a user's question anywhere they
did not choose. A pull request that adds any of these will be declined, however
useful the data would be.

**Child safety.** Some users are students and minors. Nothing may be added that
would sexualise a minor, help an adult contact a child secretly, or discourage a
young person from involving a trusted adult.

---

## Ways to help that are not code

- **Try it and report what the answers actually look like.** This is the single
  most useful thing right now. Ask a real question, and open an issue with the
  question and the answer. Wrong answers are exactly what we need to see.
- **Translate.** The AI already replies in the user's language, but the app's own
  UI strings need Indian-language translations. Add a `values-<code>/strings.xml`.
- **Write a curated guide.** The files numbered `00`–`09` in
  `app/src/main/assets/nyaya_kb/` translate everyday words ("my photos were
  leaked") into the language statutes use. Missing areas include tenancy, wages
  and labour, land records, and traffic. Every section number must be verified.
- **Test on a cheap, low-RAM phone** and tell us what breaks. That is the real
  target device and the hardest constraint.

---

## Working on the code

### Setup

```bash
git clone https://github.com/Dhanrajs-Purament/nyaya-ai.git
cd nyaya-ai
echo "sdk.dir=$ANDROID_HOME" > local.properties
pip install pypdf && python3 tools/kb/fetch_full_kb.py   # populate the legal library
./gradlew testDebugUnitTest
```

You need JDK 17+, the Android SDK with platform 35 and build-tools 35, and about
3 GB of disk.

### Before you open a pull request

```bash
./gradlew testDebugUnitTest    # must be 0 failures
./gradlew assembleDebug        # must build
```

- **Add a test for what you changed.** For a bug, write the failing test first.
- **Do not touch bitchat's source.** Every file outside
  `com/bitchat/android/nyaya/` is byte-identical to upstream, which is what keeps
  future bitchat updates mergeable. Changes belonging upstream should go
  upstream. If you genuinely must change one, explain why in the pull request.
- **Match the surrounding style.** Kotlin official style, Compose for UI. Explain
  *why* in comments, not *what*.
- Keep commits focused, in the imperative mood ("Add tenancy guide"), and keep
  the subject under 72 characters.

### Project layout

```
app/src/main/java/com/bitchat/android/
├── nyaya/           the AI lawyer — this is where contributions go
│   ├── ai/          retrieval, engines, model catalog, prompts
│   ├── memory/      long-conversation Case File
│   ├── settings/    Keystore-encrypted preferences
│   ├── ui/          Compose screens
│   └── voice/       speech in and out
└── (everything else) bitchat's mesh messenger — upstream, do not modify
app/src/main/assets/nyaya_kb/   the offline legal library
tools/kb/                       fetches the bare acts from India Code
tools/branding/                 renders the launcher icon
tools/release/                  names the release APKs
```

### Adding an Act to the library

Add an entry to `SOURCES` in `tools/kb/fetch_full_kb.py` with the official
India Code URL, run the fetcher, and confirm the generated file's header records
the source. If the Act replaces or is replaced by another, add a `NOTES` entry
saying which is in force, as the Income-tax Acts do. Then add a test in
`LegalKnowledgeBaseTest` proving a plain-language question reaches it.

Be aware that adding a very large Act shifts retrieval for everything else — that
is why ranking uses BM25 with length normalisation. Run the full suite.

---

## Licence

This project is **GPL-3.0-or-later**. By contributing you agree that your
contribution is licensed under the same terms, and that you have the right to
submit it. See [LICENSE.md](LICENSE.md) and
[THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md).

## Conduct

Be decent to people. See [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).

## Security

Do not report vulnerabilities in a public issue. See [SECURITY.md](SECURITY.md).
