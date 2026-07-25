#!/usr/bin/env python3
"""
Nyaya AI - FULL Legal Library Fetcher
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

Notes on robustness (learned the hard way against the real sites):
  * indiacode.nic.in sits behind a WAF that returns 403 for any
    User-Agent carrying a custom bot token, so BROWSER_UA below must stay
    a plain, realistic browser string.
  * India Code is a DSpace instance whose /bitstream/<handle>/<seq>/<file>
    paths change when a document is re-uploaded. A stale path answers with
    "302 Moved Temporarily" and NO Location header. When every pinned URL
    for an act fails we therefore fall back to searching India Code by
    title and re-discovering the current English bitstream.
  * Downloads are validated as real PDFs (%PDF- magic) before parsing, so
    a WAF interstitial or HTML error page can never be silently written
    into the knowledge base as if it were law.
"""
import argparse
import io
import re
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ASSETS_DIR = Path(__file__).resolve().parents[2] / "app/src/main/assets/nyaya_kb"

# Must look like an ordinary browser: indiacode.nic.in's WAF 403s custom
# bot identifiers (a UA of "...NyayaKBFetcher/1.0" is rejected outright).
BROWSER_UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
)
HEADERS = {
    "User-Agent": BROWSER_UA,
    "Accept": "application/pdf,text/html;q=0.9,*/*;q=0.8",
    "Accept-Language": "en-US,en;q=0.9",
}

INDIACODE = "https://www.indiacode.nic.in"
TIMEOUT = 120
RETRIES = 3
RETRY_BACKOFF = 2.0      # seconds, doubled per attempt
POLITE_DELAY = 0.4       # seconds between requests to the same host
MIN_TEXT_CHARS = 2000    # below this the extraction is considered failed

# Each entry: key, output filename, human title, list of candidate official
# URLs (tried in order until one succeeds). Primary source: indiacode.nic.in,
# the official digital repository of all Central Acts.
SOURCES = [
    # ---- The new criminal laws (2023) ----
    ("bns", "10_full_bharatiya_nyaya_sanhita_2023.md", "Bharatiya Nyaya Sanhita, 2023 (Act 45 of 2023)", [
        "https://www.indiacode.nic.in/bitstream/123456789/20062/1/a202345.pdf",
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
    ]),
    ("lsa", "14_full_legal_services_authorities_act_1987.md", "Legal Services Authorities Act, 1987", [
        "https://www.indiacode.nic.in/bitstream/123456789/19023/1/legal_service_authorities_act%2C_1987.pdf",
        "https://cdnbbsr.s3waas.gov.in/s32e45f93088c7db59767efef516b306aa/uploads/2025/04/202504081796627129.pdf",
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
        # NOTE: the English bitstream carries an "Eng" suffix; the older
        # A1955-25.pdf path is dead and answers 302 with no Location.
        "https://www.indiacode.nic.in/bitstream/123456789/1560/1/A1955-25Eng.pdf",
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
        "https://www.indiacode.nic.in/bitstream/123456789/15256/1/eng201935.pdf",
        "https://www.indiacode.nic.in/bitstream/123456789/16939/1/a2019-35.pdf",
    ]),
    ("itact", "26_full_information_technology_act_2000.md", "Information Technology Act, 2000 (updated, Act 21 of 2000)", [
        "https://www.indiacode.nic.in/bitstream/123456789/13116/1/it_act_2000_updated.pdf",
    ]),
    ("dpdp", "27_full_dpdp_act_2023.md", "Digital Personal Data Protection Act, 2023 (Act 22 of 2023)", [
        "https://www.indiacode.nic.in/bitstream/123456789/22037/1/a2023-22.pdf",
    ]),
    ("rti", "28_full_rti_act_2005.md", "Right to Information Act, 2005 (Act 22 of 2005)", [
        # The 17520 item is the "last updated" consolidated text; 2065 is the
        # as-enacted version kept as a fallback.
        "https://www.indiacode.nic.in/bitstream/123456789/17520/1/rti_act_2005_eng.pdf",
        "https://www.indiacode.nic.in/bitstream/123456789/2065/1/aa2005.pdf",
    ]),
    # ---- Corporate, insolvency & tax ----
    ("companies", "29_full_companies_act_2013.md", "Companies Act, 2013 (Act 18 of 2013)", [
        "https://www.indiacode.nic.in/bitstream/123456789/2114/5/A2013-18.pdf",
    ]),
    ("ibc", "30_full_insolvency_bankruptcy_code_2016.md", "Insolvency and Bankruptcy Code, 2016 (Act 31 of 2016)", [
        "https://www.indiacode.nic.in/bitstream/123456789/15479/1/the_insolvency_and_bankruptcy_code%2C_2016.pdf",
        "https://www.indiacode.nic.in/bitstream/123456789/2154/1/A201631.pdf",
    ]),
    # ---- Income tax: BOTH the old and the new law ----
    # India replaced the 1961 Act with the Income-tax Act, 2025. The new Act
    # applies from 1 April 2026 (tax year 2026-27 onwards); the 1961 Act still
    # governs every earlier year, and any dispute, assessment, appeal or
    # prosecution about those years. A legal-help app therefore needs both, and
    # must never present one as the other - hence the explicit labels below.
    ("incometax1961", "31_full_income_tax_act_1961_OLD_repealed.md",
     "Income-tax Act, 1961 (Act 43 of 1961) — OLD LAW, repealed with effect from 1 April 2026", [
        # India Code delisted this Act once it was repealed; this is the Wayback
        # Machine's capture of India Code's own official PDF, taken 24 March 2026
        # while the Act was still in force.
        "https://web.archive.org/web/20260324194825id_/https://www.indiacode.nic.in/bitstream/123456789/2435/1/a1961-43.pdf",
    ]),
    ("incometax2025", "34_full_income_tax_act_2025_NEW_in_force.md",
     "Income-tax Act, 2025 — NEW LAW, in force from 1 April 2026", [
        # Text as passed by Lok Sabha (Bill No. 104-C of 2025, the Income-tax
        # (No.2) Bill, 2025). Passed by Parliament 12 August 2025, received
        # Presidential assent 21 August 2025 and became the Income-tax Act, 2025.
        "https://prsindia.org/files/bills_acts/bills_parliament/2025/Bill_as_passed_by_LS_Income_Tax_(No.2)_Bill.pdf",
        "https://prsindia.org/files/bills_acts/bills_parliament/2025/Bill_Text-Income-tax(No.2)_Bill_2025.pdf",
    ]),
    ("cgst", "32_full_cgst_act_2017.md", "Central Goods and Services Tax Act, 2017 (Act 12 of 2017)", [
        "https://www.indiacode.nic.in/bitstream/123456789/15689/1/A2017-12.pdf",
    ]),
    # ---- Everyday-life laws ----
    ("mva", "33_full_motor_vehicles_act_1988.md", "Motor Vehicles Act, 1988 (Act 59 of 1988)", [
        "https://www.indiacode.nic.in/bitstream/123456789/1798/1/aA1988-59.pdf",
        "https://www.indiacode.nic.in/bitstream/123456789/9460/1/a1988-59.pdf",
    ]),
]

# Extra guidance written into the top of specific knowledge files. These lines
# are indexed along with the act text, so the model sees the old/new warning in
# the same retrieved passage as the law itself.
NOTES = {
    "incometax1961": (
        "**STATUS: OLD LAW — REPEALED.** The Income-tax Act, 1961 was repealed by "
        "the Income-tax Act, 2025 with effect from **1 April 2026**. Use this Act "
        "ONLY for assessment years up to 2025-26 and for any assessment, appeal, "
        "revision, penalty or prosecution relating to those earlier years. For "
        "income earned in tax year 2026-27 onwards the Income-tax Act, 2025 "
        "applies instead — see `34_full_income_tax_act_2025_NEW_in_force.md`. "
        "Never quote a 1961 Act section as current law without saying it comes "
        "from the repealed Act."
    ),
    "incometax2025": (
        "**STATUS: NEW LAW — CURRENTLY IN FORCE.** The Income-tax Act, 2025 "
        "replaces the Income-tax Act, 1961. It was passed by Parliament on "
        "12 August 2025, received Presidential assent on 21 August 2025, and "
        "applies from **1 April 2026** (tax year 2026-27 onwards). This is the "
        "law to cite for current income-tax questions. Section numbering differs "
        "from the 1961 Act, so never carry a 1961 section number across to this "
        "Act — for earlier years see "
        "`31_full_income_tax_act_1961_OLD_repealed.md`. The text below is the "
        "Bill as passed by Lok Sabha (Bill No. 104-C of 2025), which is the text "
        "that became the Act on assent."
    ),
}


class FetchError(RuntimeError):
    """A source could not be retrieved as a usable PDF."""


def _norm(text: str) -> str:
    """Collapse a title to comparable alphanumerics only."""
    return re.sub(r"[^a-z0-9]+", "", text.lower())


def _open(url: str) -> bytes:
    """GET a URL with retries. Raises FetchError with a precise reason."""
    last = None
    for attempt in range(1, RETRIES + 1):
        try:
            req = urllib.request.Request(url, headers=HEADERS)
            with urllib.request.urlopen(req, timeout=TIMEOUT) as resp:
                return resp.read()
        except urllib.error.HTTPError as e:
            # DSpace answers a stale/unknown bitstream path with 302 and no
            # Location header, which urllib surfaces as HTTPError. Retrying
            # cannot help, so fail fast with an actionable message.
            if e.code in (301, 302, 303, 307, 308) and not e.headers.get("Location"):
                raise FetchError(
                    f"HTTP {e.code} with no Location - the document path no "
                    f"longer exists on the server"
                ) from e
            if e.code == 403:
                raise FetchError(
                    "HTTP 403 - request rejected by the site's WAF "
                    "(check that BROWSER_UA still looks like a real browser)"
                ) from e
            if e.code == 404:
                raise FetchError("HTTP 404 - document not found") from e
            last = FetchError(f"HTTP {e.code} {e.reason}")
        except (urllib.error.URLError, TimeoutError, OSError) as e:
            last = FetchError(f"{type(e).__name__}: {e}")
        if attempt < RETRIES:
            time.sleep(RETRY_BACKOFF * attempt)
    raise last or FetchError("unknown transport failure")


def download_pdf(url: str) -> bytes:
    """Fetch a URL and prove it is really a PDF before we trust it."""
    data = _open(url)
    if not data.startswith(b"%PDF-"):
        head = data[:60].decode("utf-8", "replace").replace("\n", " ")
        raise FetchError(f"not a PDF (starts with {head!r})")
    return data


def pdf_to_text(pdf_bytes: bytes) -> str:
    """Extract text with pypdf, falling back to the pdftotext binary."""
    try:
        from pypdf import PdfReader  # type: ignore
    except ImportError:
        return _pdftotext(pdf_bytes)
    try:
        reader = PdfReader(io.BytesIO(pdf_bytes))
        return "\n".join((page.extract_text() or "") for page in reader.pages)
    except Exception as e:  # malformed PDF: poppler is often more tolerant
        try:
            return _pdftotext(pdf_bytes)
        except Exception:
            raise FetchError(f"PDF text extraction failed: {e}") from e


def _pdftotext(pdf_bytes: bytes) -> str:
    with tempfile.NamedTemporaryFile(suffix=".pdf") as f:
        f.write(pdf_bytes)
        f.flush()
        try:
            out = subprocess.run(
                ["pdftotext", "-layout", f.name, "-"],
                capture_output=True, text=True, check=True,
            )
        except FileNotFoundError as e:
            raise FetchError(
                "no PDF text extractor available - run `pip install pypdf` "
                "or install poppler-utils for `pdftotext`"
            ) from e
        return out.stdout


def search_indiacode(title: str) -> list:
    """Re-discover an act's current English PDF bitstream on India Code.

    India Code is a DSpace repository; pinned /bitstream/ paths break when a
    document is re-uploaded. Searching by title and reading the item page
    recovers the live path instead of failing the whole run.
    """
    # "Hindu Marriage Act, 1955 (Act 25 of 1955)" -> "Hindu Marriage Act, 1955"
    short = re.sub(r"\s*\(.*?\)\s*", " ", title)
    short = re.sub(r"\s*[—–]\s*.*$", "", short).strip(" ,.")
    query = urllib.parse.quote(short)
    try:
        page = _open(f"{INDIACODE}/simple-search?query={query}")
    except FetchError:
        return []
    html = page.decode("utf-8", "ignore")
    handles = list(dict.fromkeys(
        re.findall(r'href="/handle/(123456789/\d+)\?view_type=search', html)
    ))
    wanted = _norm(short)
    found = []
    for handle in handles[:12]:
        time.sleep(POLITE_DELAY)
        try:
            item = _open(f"{INDIACODE}/handle/{handle}")
        except FetchError:
            continue
        item_html = item.decode("utf-8", "ignore")
        m = re.search(r"<title>India Code:\s*([^<]*)</title>", item_html)
        if not m:
            continue
        item_title = _norm(m.group(1))
        if wanted not in item_title and item_title not in wanted:
            continue
        pdfs = list(dict.fromkeys(
            re.findall(r'href="(/bitstream/123456789/[^"]+\.pdf)"', item_html)
        ))
        # Prefer the English text; India Code names Hindi copies H*/​*Hi.pdf.
        english = [p for p in pdfs
                   if not re.search(r"(?:Hi|hindi|-hindi)\.pdf$", p, re.I)
                   and not re.search(r"/H\d", p)]
        for p in (english or pdfs):
            found.append(INDIACODE + urllib.parse.quote(p, safe="/%"))
    return found


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


def fetch_act(title: str, urls: list) -> tuple:
    """Return (text, source_url). Tries pinned URLs, then India Code search."""
    attempted = set()
    for url in urls:
        attempted.add(url)
        print(f"    downloading {url}")
        try:
            text = clean(pdf_to_text(download_pdf(url)))
            if len(text) < MIN_TEXT_CHARS:
                raise FetchError(
                    f"extracted only {len(text)} chars, expected >= {MIN_TEXT_CHARS}"
                )
            return text, url
        except FetchError as e:
            print(f"    !! {e}")
        time.sleep(POLITE_DELAY)

    print("    ...pinned URLs exhausted, searching India Code by title")
    for url in search_indiacode(title):
        if url in attempted:
            continue
        attempted.add(url)
        print(f"    resolved candidate {url}")
        try:
            text = clean(pdf_to_text(download_pdf(url)))
            if len(text) < MIN_TEXT_CHARS:
                raise FetchError(f"extracted only {len(text)} chars")
            print("    (update SOURCES with this URL to skip the search next time)")
            return text, url
        except FetchError as e:
            print(f"    !! {e}")
        time.sleep(POLITE_DELAY)
    raise FetchError("no working source found")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--only", help="fetch a single act by key (e.g. bns, cpc, companies)")
    args = ap.parse_args()

    if args.only and args.only not in {k for k, _, _, _ in SOURCES}:
        print(f"unknown key {args.only!r}; valid keys: "
              f"{', '.join(k for k, _, _, _ in SOURCES)}")
        return 2

    ASSETS_DIR.mkdir(parents=True, exist_ok=True)
    ok, failed = [], []
    for key, filename, title, urls in SOURCES:
        if args.only and key != args.only:
            continue
        print(f"==> {title}")
        try:
            text, source_url = fetch_act(title, urls)
        except FetchError as e:
            print(f"    FAILED: {e}")
            failed.append((key, title))
            continue
        out = ASSETS_DIR / filename
        note = NOTES.get(key)
        header = (
            f"# {title} — FULL TEXT\n\n"
            f"> Official bare act downloaded from Government of India sources.\n"
            f"> Source: {source_url}\n"
            f"> This is the complete text for offline retrieval by Nyaya AI.\n\n"
        )
        if note:
            header += f"## Status of this Act — read this first\n\n{note}\n\n"
        out.write_text(header + text, encoding="utf-8")
        print(f"    -> {out} ({len(text)//1024} KB)")
        ok.append(key)

    print("\n================ SUMMARY ================")
    print(f"fetched : {len(ok)} act(s): {', '.join(ok) if ok else '-'}")
    if failed:
        print(f"FAILED  : {len(failed)} act(s):")
        for key, title in failed:
            print(f"  - {key}: {title} (find the PDF on indiacode.nic.in and add its URL to SOURCES)")
    print("Now build the APK - the app indexes every .md automatically.")
    return 0 if not failed else 1


if __name__ == "__main__":
    sys.exit(main())
