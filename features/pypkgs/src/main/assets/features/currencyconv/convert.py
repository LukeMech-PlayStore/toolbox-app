#!/usr/bin/env python3
"""convert.py <db status> <amount> <from> <to>: prints the converted amount, rounded to 2 places."""
import os
import sys

from currency_converter import CurrencyConverter

DB = os.path.join(os.path.dirname(os.path.abspath(__file__)), "ecb.zip")

status, amount, from_cur, to_cur = sys.argv[1:5]
use_local = status in ("UPDATED", "OFFLINE_HAS_DB") and os.path.exists(DB)
c = CurrencyConverter(DB) if use_local else CurrencyConverter()
print(round(c.convert(float(amount), from_cur, to_cur), 2))
