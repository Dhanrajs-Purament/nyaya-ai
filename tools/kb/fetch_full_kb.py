#!/usr/bin/env python3
"""Download the ENTIRE official Indian bare-act PDFs and convert each one to
a full-text markdown file inside app/src/main/assets/nyaya_kb/.

Run this once before building the APK to bundle the complete books:

    python3 tools/kb/fetch_full_kb.py

Requirements: Python 3.8+. Uses `pdftotext` (poppler-utils) if available,
otherwise falls back to the `pypdf` package (pip install pypdf).

All sources are OFFICIAL and openly available: India Code (indiacode.nic.in),
Legislative Department (legislative.gov.in), NALSA, MeitY, Dept. of Consumer
Affairs. If a URL changes, find the act on indiacode.nic.in, copy the PDF
link, and update SOURCES below — the app indexes any .md dropped into
assets/nyaya_kb/ automatically.
"""

import re
import shutil
import subprocess
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "app" / "src" / "main" / "assets" / "nyaya_kb"
PDF_DIR = Path(__file__).resolve().parent / "pdf"

UA = {"User-Agent": "Mozilla/5.0 (X11; Linux x86_64) NyayaKB/1.0"}

# (output name, title, [candidate URLs, first that works wins])
SOURCES = [
    ("90_full_bns_2023", "Bharatiya Nyaya Sanhita, 2023 (full text)", [
        "https://www.indiacode.nic.in/bitstream/123456789/20062/1/a202345.pdf",
    ]),
    ("91_full_bnss_2023", "Bharatiya Nagarik Suraksha Sanhita, 2023 (full text)", [
        "https://www.indiacode.nic.in/bitstream/123456789/20099/1/A202346.pdf",
    ]),
    ("92_full_bsa_2023", "Bharatiya Sakshya Adhiniyam, 2023 (full text)", [
        "https://www.indiacode.nic.in/bitstream/123456789/20063/1/aa202347.pdf",
    ]),
    ("93_full_lsa_1987", "Legal Services Authorities Act, 1987 (full text)", [
        "https://www.indiacode.nic.in/bitstream/123456789/19023/1/legal_service_authorities_act%2C_1987.pdf",
        "https://cdnbbsr.s3waas.gov.in/s32e45f93088c7db59767efef516b306aa/uploads/2025/04/202504081796627129.pdf",
    ]),
    ("94_full_dpdp_2023", "Digital Personal Data Protection Act, 2023 (full text)", [
        "https://www.meity.gov.in/writereaddata/files/Digital%20Personal%20Data%20Protection%20Act%202023.pdf",
        "https://prsindia.org/files/bills_acts/acts_parliament/2023/Digital_Personal_Data_Protection_Act,_2023.pdf",
    ]),
    ("95_full_cpa_2019", "Consumer Protection Act, 2019 (full text)", [
        "https://consumeraffairs.nic.in/sites/default/files/CP%20Act%202019.pdf",
        "https://prsindia.org/files/bills_acts/acts_parliament/2019/Consumer%20Protection%20Act,%202019.pdf",
    ]),
    ("96_full_rti_2005", "Right to Information Act, 2005 (full text)", [
        "https://rti.gov.in/rti-act.pdf",
        "https://www.indiacode.nic.in/bitstream/123456789/2065/1/a2005-22.pdf",
    ]),
    ("97_full_contract_act_1872", "Indian Contract Act, 1872 (full text)", [
        "https://www.indiacode.nic.in/bitstream/123456789/2187/2/A187209.pdf",
    ]),
    ("98_full_constitution", "Constitution of India (full text)", [
        # Check https://legislative.gov.in/documents for the latest edition.
        "https://cdnbbsr.s3waas.gov.in/s380537a945c7aaa788ccfcdf1b99b5d8f/uploads/2024/07/20240716890312078.pdf",
    ]),
]


def download(url: str, dest: Path) -> bool:
    try:
        req = urllib.request.Request(url, headers=UA)
        with urllib.request.urlopen(req, timeout=120) as resp, open(dest, "wb") as f:
            shutil.copyfileobj(resp, f)
        if dest.stat().st_size < 10_000:  # too small to be a real act PDF
            return False
        with open(dest, "rb") as f:
            return f.read(5) == b"%PDF-"
    except Exception as e:
        print(f"    ! {e}")
        return False


def pdf_to_text(pdf: Path) -> str:
    if shutil.which("pdftotext"):
        out = pdf.with_suffix(".txt")
        subprocess.run(["pdftotext", "-enc", "UTF-8", str(pdf), str(out)], check=True)
        return out.read_text(encoding="utf-8", errors="ignore")
    try:
        from pypdf import PdfReader
    except ImportError:
        sys.exit("Install poppler-utils (pdftotext) or run: pip install pypdf")
    reader = PdfReader(str(pdf))
    return "\n".join((page.extract_text() or "") for page in reader.pages)


def clean(text: str) -> str:
    lines = []
    for line in text.splitlines():
        s = line.rstrip()
        if re.fullmatch(r"\s*\d{1,4}\s*", s):  # bare page numbers
            continue
        lines.append(s)
    text = "\n".join(lines)
    text = re.sub(r"-\n(?=[a-z])", "", text)      # de-hyphenate line breaks
    text = re.sub(r"\n{4,}", "\n\n\n", text)       # collapse blank runs
    # Promote SECTION / CHAPTER headings to markdown for better RAG chunking.
    text = re.sub(r"(?m)^(CHAPTER\s+[IVXLC0-9]+.*)$", r"## \1", text)
    text = re.sub(r"(?m)^(THE\s+[A-Z][A-Z ,]{8,})$", r"## \1", text)
    return text.strip()


def main() -> None:
    ASSETS.mkdir(parents=True, exist_ok=True)
    PDF_DIR.mkdir(parents=True, exist_ok=True)
    ok, fail = [], []
    for name, title, urls in SOURCES:
        print(f"==> {title}")
        pdf = PDF_DIR / f"{name}.pdf"
        got = pdf.exists() and pdf.stat().st_size > 10_000
        for url in urls:
            if got:
                break
            print(f"    GET {url}")
            got = download(url, pdf)
        if not got:
            fail.append(title)
            print("    FAILED — find the PDF on indiacode.nic.in and update SOURCES")
            continue
        md = ASSETS / f"{name}.md"
        body = clean(pdf_to_text(pdf))
        md.write_text(
            f"# {title}\n\nSource: official Government of India PDF "
            f"(India Code / ministry portal). Converted verbatim; refer to the "
            f"original PDF for authoritative text.\n\n{body}\n",
            encoding="utf-8",
        )
        ok.append(f"{md.name} ({md.stat().st_size // 1024} KB)")
        print(f"    -> {md.relative_to(ROOT)}")
    print("\nDone.")
    print("  bundled:", ", ".join(ok) if ok else "none")
    if fail:
        print("  failed :", ", ".join(fail))
        sys.exit(1)


if __name__ == "__main__":
    main()
