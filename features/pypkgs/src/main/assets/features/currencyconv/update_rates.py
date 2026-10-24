#!/usr/bin/env python3
"""Downloads the ECB exchange rates. Prints UPDATED, OFFLINE_HAS_DB or OFFLINE_NO_DB."""
import os
import sys
import urllib.request

ECB_URL = "https://www.ecb.europa.eu/stats/eurofxref/eurofxref.zip"
DB = os.path.join(os.path.dirname(os.path.abspath(__file__)), "ecb.zip")

try:
    # Download next to the old file and swap, so a failed download keeps the previous rates
    urllib.request.urlretrieve(ECB_URL, DB + ".part")
    os.replace(DB + ".part", DB)
    print("UPDATED")
except Exception as e:
    print(f"Download failed: {e}", file=sys.stderr)
    print("OFFLINE_HAS_DB" if os.path.exists(DB) else "OFFLINE_NO_DB")
