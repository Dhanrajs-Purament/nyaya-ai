# Nyaya Legal Knowledge Base — pre-warmed offline RAG

## Goal
The moment the app opens, the AI is already "pre-warmed" with Indian law. The
very first question gets an answer grounded in the real text of the bare acts
— fully offline, with no network call.

## How it works
1. **Bundled knowledge** — markdown files in `app/src/main/assets/nyaya_kb/`.
   They ship inside the APK.
2. **Pre-warm at launch** — `NyayaViewModel` calls
   `LegalKnowledgeBase.warmUp()` on a background dispatcher in `init`. All
   `.md` files are parsed, split into heading-aligned passages (≤1,600 chars),
   and indexed (term frequency + document frequency) in memory. This takes
   milliseconds-to-seconds and never blocks the UI.
3. **Retrieval per question** — on every `send()`, the question is tokenized
   and scored against all passages (TF-IDF style, with a heading-match bonus).
   The top passages (≤4, ≤3,000 chars) are appended to the system prompt as
   *verified reference extracts*, with an instruction to cite section numbers
   only from those extracts — this is the anti-hallucination layer for legal
   citations.
4. **Generation** — the on-device Gemma model (or the BYOK cloud engine)
   answers using the grounded context plus conversation memory.

## What is bundled by default (pre-warm core)
Curated, section-accurate distillations sourced from official portals:
- Bharatiya Nyaya Sanhita, 2023 (offences, punishments, IPC→BNS mapping)
- Bharatiya Nagarik Suraksha Sanhita, 2023 (FIR/Zero FIR, arrest rights, custody limits, bail)
- Bharatiya Sakshya Adhiniyam, 2023 (evidence, electronic records, confessions)
- Legal Services Authorities Act, 1987 (free legal aid, NALSA, Lok Adalats)
- Constitution of India (fundamental rights, Articles 20–22, 32, 226, 39A, D.K. Basu)
- Consumer Protection Act, 2019 · DPDP Act, 2023 · RTI Act, 2005
- Emergency numbers and legal helplines (112, 15100, 1930, 1091, 1098)

## Bundling the ENTIRE books
The complete official PDFs are megabytes each, so they are fetched at build
time rather than committed to git:

```bash
python3 tools/kb/fetch_full_kb.py
```

This downloads each full bare act from India Code / ministry portals and
converts it to a full-text `.md` in `assets/nyaya_kb/`. The app indexes any
new `.md` automatically — no code change required. Expect the APK to grow by
roughly the size of the generated markdown (a few MB for all acts).

## Case law (future)
Supreme Court judgments from the free e-SCR portal
(judgments.ecourts.gov.in/pdfsearch) are ideal RAG material but far too large
to bundle in an APK (lakhs of judgments since 1950). Planned as a V2 feature:
an optional in-app “knowledge pack” download (like the model download) with a
pre-built index, plus High Court packs via ecourts.gov.in / NJDG.

## Sources policy
Official, openly available government sources only: indiacode.nic.in,
legislative.gov.in, egazette.gov.in, mha.gov.in, meity.gov.in,
consumeraffairs.nic.in, nalsa.gov.in, rti.gov.in. No private publishers
(SCC Online, Manupatra) — their content is licensed and must not be scraped.
