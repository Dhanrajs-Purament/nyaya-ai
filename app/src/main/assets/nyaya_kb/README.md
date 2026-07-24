# Nyaya Legal Knowledge Base (assets/nyaya_kb)

Every `.md` file in this folder is loaded and indexed by `LegalKnowledgeBase.kt` when the app starts (background pre-warm). Relevant passages are retrieved for each user question and injected into the AI's context (offline RAG) so answers cite real sections of Indian law.

## Bundled pre-warm core
Curated, section-accurate distillations of: BNS 2023, BNSS 2023, BSA 2023, Legal Services Authorities Act 1987 (free legal aid), Constitution fundamental rights, Consumer Protection Act 2019, DPDP Act 2023, RTI Act 2005, and emergency helplines.

## Adding the ENTIRE bare acts (full books)
Run the fetch pipeline before building the APK:

```bash
python3 tools/kb/fetch_full_kb.py
```

It downloads the complete official PDFs from India Code / government portals and converts each into a full-text `.md` file in this folder. The app automatically indexes any new `.md` files — no code change needed.

Sources are official only: indiacode.nic.in, legislative.gov.in, meity.gov.in, consumeraffairs.nic.in, nalsa.gov.in. Case law (e-SCR, judgments.ecourts.gov.in) is intentionally not bundled — it is too large for an APK and belongs in a future on-demand download/RAG update feature.
