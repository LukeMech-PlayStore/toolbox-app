#!/usr/bin/env python3
"""Prints "<rates date>|<CUR>,<CUR>,..." for the downloaded (or bundled) ECB rates."""
import os
import sys

from currency_converter import CurrencyConverter

DB = os.path.join(os.path.dirname(os.path.abspath(__file__)), "ecb.zip")

use_local = len(sys.argv) > 1 and sys.argv[1] in ("UPDATED", "OFFLINE_HAS_DB") and os.path.exists(DB)
c = CurrencyConverter(DB) if use_local else CurrencyConverter()
try:
    last_date = str(max(bounds[1] for bounds in c.bounds.values()))
except Exception:
    last_date = "Unknown"
print(last_date + "|" + ",".join(sorted(c.currencies)))
