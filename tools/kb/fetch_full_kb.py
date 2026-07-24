#!/usr/bin/env python3
"""
Nyaya AI Lawyer - FULL Legal Library Fetcher
============================================
Downloads the COMPLETE official bare-act PDFs for every law in the Nyaya
library from Government of India sources (India Code, Legislative Dept,
MeitY, NALSA, Consumer Affairs) and converts each one into a clean
Markdown file inside app/src/main/assets/nyaya_kb/.

The app's LegalKnowledgeBase automatically indexes EVERY .md file in that
folder at launch (pre-warming), so after running this script the AI knows
the entire text of every act below - fully offline.

Usage (run once before building the APK):
    python3 tools/kb/fetch_full_kb.py            # fetch everything
    python3 tools/kb/fetch_full_kb.py --only bns # fetch one act by key

Requirements: Python 3.8+. Uses `pypdf` if installed (pip install pypdf),
otherwise falls back to the `pdftotext` binary (poppler-utils).
"""
import argparse
import io
import re
import subprocess
import sys
import tempfile
import urllib.request
from pathlib import Path

ASSETS_DIR = Path(__file__).resolve().parents[2] / "app/src/main/assets/nyaya_kb"
UA = {"User-Agent": "Mozilla/5.0 (X11; Linux x86_64) NyayaKBFetcher/1.0"}

# Each entry: key, output filename, human title, list of candidate official
# URLs (tried in order until one succeeds). Primary source: indiacode.nic.in,
# the official digital repository of all Central Acts.
SOURCES = [
    # ---- The new criminal laws (2023) ----
    ("bns", "10_full_bharatiya_nyaya_sanhita_2023.md", "Bharatiya Nyaya Sanhita, 2023 (Act 45 of 2023)", [
        "https://www.indiacode.nic.in/bitstream/123456789/20062/1/a202345.pdf",
        "https://prsindia.org/files/bills_acts/bills_parliament/2023/Bharatiya_Nyaya_Sanhita,_2023.pdf",
    ]),
    ("bnss", "11_full_bharatiya_nagarik_suraksha_sanhita_2023.md", "Bharatiya Nagarik Suraksha Sanhita, 2023 (Act 46 of 2023)", [
        "https://www.indiacode.nic.in/bitstream/123456789/20099/1/A202346.pdf",
    ]),
    ("bsa", "12_full_bharatiya_sakshya_adhiniyam_2023.md", "Bharatiya Sakshya Adhiniyam, 2023 (Act 47 of 2023)", [
        "https://www.indiacode.nic.in/bitstream/123456789/20063/1/aa202347.pdf",
    ]),
    # ---- Constitution & legal aid ----
    ("constitution", "13_full_constitution_of_india.md", "The Constitution of India (updated)", [
        "https://cdnbbsr.s3waas.gov.in/s380537a945c7aaa788ccfcdf1b99b5d8f/uploads/2024/07/20240716890312078.pdf",
        "https://legislative.gov.in/constitution-of-india/",
    ]),
    ("lsa", "14_full_legal_services_authorities_act_1987.md", "Legal Services Authorities Act, 1987", [
        "https://cdnbbsr.s3waas.gov.in/s32e45f93088c7db59767efef516b306aa/uploads/2025/04/202504081796627129.pdf",
        "https://www.indiacode.nic.in/bitstream/123456789/19023/1/legal_service_authorities_act%2C_1987.pdf",
    ]),
    # ---- Core civil law ----
    ("contract", "15_full_indian_contract_act_1872.md", "Indian Contract Act, 1872 (Act 9 of 1872)", [
        "https://www.indiacode.nic.in/bitstream/123456789/2187/2/A187209.pdf",
    ]),
    ("specific_relief", "16_full_specific_relief_act_1963.md", "Specific Relief Act, 1963 (Act 47 of 1963)", [
        "https://www.indiacode.nic.in/bitstream/123456789/1583/7/A1963-47.pdf",
    ]),
    ("top", "17_full_transfer_of_property_act_1882.md", "Transfer of Property Act, 1882 (Act 4 of 1882)", [
        "https://www.indiacode.nic.in/bitstream/123456789/2338/1/A1882-04.pdf",
    ]),
    ("cpc", "18_full_code_of_civil_procedure_1908.md", "Code of Civil Procedure, 1908 (Act 5 of 1908)", [
        "https://www.indiacode.nic.in/bitstream/123456789/13813/1/the_code_of_civil_procedure%2C_1908.pdf",
        "https://indiacode.nic.in/bitstream/123456789/2191/1/A190805.pdf",
    ]),
    ("limitation", "19_full_limitation_act_1963.md", "Limitation Act, 1963 (Act 36 of 1963)", [
        "https://www.indiacode.nic.in/bitstream/123456789/1565/5/A1963-36.pdf",
    ]),
    # ---- Family / personal laws ----
    ("hma", "20_full_hindu_marriage_act_1955.md", "Hindu Marriage Act, 1955 (Act 25 of 1955)", [
        "https://www.indiacode.nic.in/bitstream/123456789/1560/1/A1955-25.pdf",
    ]),
    ("hsa", "21_full_hindu_succession_act_1956.md", "Hindu Succession Act, 1956 (Act 30 of 1956)", [
        "https://www.indiacode.nic.in/bitstream/123456789/1713/1/AAA1956suc___30.pdf",
    ]),
    ("sma", "22_full_special_marriage_act_1954.md", "Special Marriage Act, 1954 (Act 43 of 1954)", [
        "https://www.indiacode.nic.in/bitstream/123456789/15480/1/special_marriage_act.pdf",
        "https://www.indiacode.nic.in/bitstream/123456789/1387/1/A1954-43E.pdf",
    ]),
    ("dv", "23_full_domestic_violence_act_2005.md", "Protection of Women from Domestic Violence Act, 2005", [
        "https://www.indiacode.nic.in/bitstream/123456789/15436/1/protection_of_women_from_domestic_violence_act%2C_2005.pdf",
        "https://www.indiacode.nic.in/bitstream/123456789/2021/5/A2005-43.pdf",
    ]),
    ("dowry", "24_full_dowry_prohibition_act_1961.md", "Dowry Prohibition Act, 1961 (Act 28 of 1961)", [
        "https://www.indiacode.nic.in/bitstream/123456789/5556/1/dowry_prohibition.pdf",
        "https://www.indiacode.nic.in/bitstream/123456789/1679/4/a1961-28.pdf",
    ]),
    # ---- Consumer, digital & citizen rights ----
    ("cpa", "25_full_consumer_protection_act_2019.md", "Consumer Protection Act, 2019 (Act 35 of 2019)", [
        "https://www.indiacode.nic.in/bitstream/123456789/15256/1/a2019-35.pdf",
        "https://consumeraffairs.nic.in/sites/default/files/CP%20Act%202019.pdf",
    ]),
    ("itact", "26_full_information_technology_act_2000.md", "Information Technology Act, 2000 (updated, Act 21 of 2000)", [
        "https://www.indiacode.nic.in/bitstream/123456789/13116/1/it_act_2000_updated.pdf",
    ]),
    ("dpdp", "27_full_dpdp_act_2023.md", "Digital Personal Data Protection Act, 2023 (Act 22 of 2023)", [
        "https://www.meity.gov.in/writereaddata/files/Digital%20Personal%20Data%20Protection%20Act%202023.pdf",
        "https://www.indiacode.nic.in/bitstream/123456789/20065/1/a2023-22.pdf",
    ]),
    ("rti", "28_full_rti_act_2005.md", "Right to Information Act, 2005 (Act 22 of 2005)", [
        "https://www.indiacode.nic.in/bitstream/123456789/2065/1/a2005-22.pdf",
        "https://rti.gov.in/rti-act.pdf",
    ]),
    # ---- Corporate, insolvency & tax ----
    ("companies", "29_full_companies_act_2013.md", "Companies Act, 2013 (Act 18 of 2013)", [
        "https://www.indiacode.nic.in/bitstream/123456789/2114/5/A2013-18.pdf",
    ]),
    ("ibc", "30_full_insolvency_bankruptcy_code_2016.md", "Insolvency and Bankruptcy Code, 2016 (Act 31 of 2016)", [
        "https://www.indiacode.nic.in/bitstream/123456789/15479/1/the_insolvency_and_bankruptcy_code%2C_2016.pdf",
        "https://www.indiacode.nic.in/bitstream/123456789/2154/1/A201631.pdf",
    ]),
    ("incometax", "31_full_income_tax_act_1961.md", "Income-tax Act, 1961 (Act 43 of 1961)", [
        "https://www.indiacode.nic.in/bitstream/123456789/2435/1/a1961-43.pdf",
    ]),
    ("cgst", "32_full_cgst_act_2017.md", "Central Goods and Services Tax Act, 2017 (Act 12 of 2017)", [
        "https://www.indiacode.nic.in/bitstream/123456789/15689/1/A2017-12.pdf",
    ]),
    # ---- Everyday-life laws ----
    ("mva", "33_full_motor_vehicles_act_1988.md", "Motor Vehicles Act, 1988 (Act 59 of 1988)", [
        "https://www.indiacode.nic.in/bitstream/123456789/9460/1/a1988-59.pdf",
        "https://www.indiacode.nic.in/bitstream/123456789/1798/1/aA1988-59.pdf",
    ]),
]


def download(url: str) -> bytes:
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=120) as resp:
        return resp.read()


def pdf_to_text(pdf_bytes: bytes) -> str:
    # Prefer pypdf (pure python), fall back to pdftotext binary.
    try:
        from pypdf import PdfReader  # type: ignore
        reader = PdfReader(io.BytesIO(pdf_bytes))
        return "\n".join((page.extract_text() or "") for page in reader.pages)
    except ImportError:
        pass
    with tempfile.NamedTemporaryFile(suffix=".pdf") as f:
        f.write(pdf_bytes)
        f.flush()
        out = subprocess.run(
            ["pdftotext", "-layout", f.name, "-"],
            capture_output=True, text=True, check=True,
        )
        return out.stdout


def clean(text: str) -> str:
    lines = []
    for raw in text.splitlines():
        line = raw.rstrip()
        # Drop bare page numbers and gazette header noise.
        if re.fullmatch(r"\s*\d{1,4}\s*", line):
            continue
        if re.search(r"THE GAZETTE OF INDIA|PUBLISHED BY AUTHORITY|EXTRAORDINARY", line, re.I):
            continue
        lines.append(line)
    text = "\n".join(lines)
    # Re-join words hyphenated across line breaks, collapse blank runs.
    text = re.sub(r"(\w)-\n(\w)", r"\1\2", text)
    text = re.sub(r"\n{3,}", "\n\n", text)
    # Promote section headings so the RAG chunker aligns to sections.
    text = re.sub(r"(?m)^(\d{1,3}[A-Z]?\.\s+[A-Z][^\n]{3,90})$", r"## \1", text)
    return text.strip()


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--only", help="fetch a single act by key (e.g. bns, cpc, companies)")
    args = ap.parse_args()

    ASSETS_DIR.mkdir(parents=True, exist_ok=True)
    ok, failed = [], []
    for key, filename, title, urls in SOURCES:
        if args.only and key != args.only:
            continue
        print(f"==> {title}")
        text = None
        for url in urls:
            try:
                print(f"    downloading {url}")
                text = clean(pdf_to_text(download(url)))
                if len(text) < 2000:
                    raise ValueError("extracted text suspiciously short")
                break
            except Exception as e:  # try next candidate URL
                print(f"    !! {e}")
                text = None
        if text is None:
            failed.append((key, title))
            continue
        out = ASSETS_DIR / filename
        header = (
            f"# {title} — FULL TEXT\n\n"
            f"> Official bare act downloaded from Government of India sources.\n"
            f"> This is the complete text for offline retrieval by Nyaya AI Lawyer.\n\n"
        )
        out.write_text(header + text, encoding="utf-8")
        print(f"    -> {out} ({len(text)//1024} KB)")
        ok.append(key)

    print("\n================ SUMMARY ================")
    print(f"fetched : {len(ok)} act(s): {', '.join(ok) if ok else '-'}")
    if failed:
        print(f"FAILED  : {len(failed)} act(s):")
        for key, title in failed:
            print(f"  - {key}: {title} (find the PDF on indiacode.nic.in and add its URL to SOURCES)")
    print("Now build the APK in Android Studio - the app indexes every .md automatically.")
    return 0 if not failed else 1


if __name__ == "__main__":
    sys.exit(main())
