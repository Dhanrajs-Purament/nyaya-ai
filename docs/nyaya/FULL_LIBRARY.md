# Nyaya AI Lawyer — Complete Legal Library

The user's mandate: **every book, every law — no user should suffer because something was left out.**

## How the library ships

1. `app/src/main/assets/nyaya_kb/00–08_*.md` — curated, section-accurate core (bundled in every build; guarantees arrest rights, FIR, bail, legal aid, consumer, RTI, DPDP knowledge even if the fetch step is skipped).
2. `python3 tools/kb/fetch_full_kb.py` — downloads the **complete official bare-act PDF of every act below** from Government of India sources and converts each to Markdown in the same assets folder. Run once before building the APK.
3. `LegalKnowledgeBase.kt` indexes **every** `.md` in the folder at app launch (pre-warm) and injects relevant passages into every answer (offline RAG).

## The complete catalog (all verified official sources)

| # | Act | Source |
|---|-----|--------|
| 1 | Bharatiya Nyaya Sanhita, 2023 | India Code |
| 2 | Bharatiya Nagarik Suraksha Sanhita, 2023 | India Code |
| 3 | Bharatiya Sakshya Adhiniyam, 2023 | India Code |
| 4 | Constitution of India (updated) | Legislative Dept |
| 5 | Legal Services Authorities Act, 1987 | NALSA / India Code |
| 6 | Indian Contract Act, 1872 | India Code |
| 7 | Specific Relief Act, 1963 | India Code |
| 8 | Transfer of Property Act, 1882 | India Code |
| 9 | Code of Civil Procedure, 1908 | India Code |
| 10 | Limitation Act, 1963 | India Code |
| 11 | Hindu Marriage Act, 1955 | India Code |
| 12 | Hindu Succession Act, 1956 | India Code |
| 13 | Special Marriage Act, 1954 | India Code |
| 14 | Protection of Women from Domestic Violence Act, 2005 | India Code |
| 15 | Dowry Prohibition Act, 1961 | India Code |
| 16 | Consumer Protection Act, 2019 | India Code / Consumer Affairs |
| 17 | Information Technology Act, 2000 (updated) | India Code |
| 18 | Digital Personal Data Protection Act, 2023 | MeitY / India Code |
| 19 | Right to Information Act, 2005 | India Code / DoPT |
| 20 | Companies Act, 2013 | India Code (MCA) |
| 21 | Insolvency and Bankruptcy Code, 2016 | India Code (IBBI) |
| 22 | Income-tax Act, 1961 | India Code |
| 23 | Central GST Act, 2017 | India Code (CBIC) |
| 24 | Motor Vehicles Act, 1988 | India Code |

## Size budget

Full bare-act text for all 24 acts is roughly 15–25 MB of Markdown before
compression; APK/AAB asset compression brings the on-disk cost well within the
30–40 MB app-size budget. The two largest books (Income-tax Act, Companies
Act) account for about a third of that — remove their lines from `SOURCES`
if a leaner build is ever needed.

## Case law (e-SCR) — V2

Supreme Court judgments (judgments.ecourts.gov.in/pdfsearch, free from 1950)
are lakhs of documents and cannot ship inside an APK. Planned as an optional
in-app downloadable "knowledge pack", exactly like the model download.
