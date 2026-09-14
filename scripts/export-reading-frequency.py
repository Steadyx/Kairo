#!/usr/bin/env python3
"""Export fixed lookup data, not fitted timing weights. Requires wordfreq==3.1.1.

The derived table is CC BY-SA 4.0; see app/src/main/assets/licenses/wordfreq-NOTICE.md.
"""
import gzip
import importlib.metadata
import re
from pathlib import Path

from wordfreq import top_n_list, zipf_frequency

assert importlib.metadata.version("wordfreq") == "3.1.1"
root = Path(__file__).resolve().parents[1]
target = root / "app/src/main/resources/reading/english-frequency.tsv.gz"
rows = []
for word in top_n_list("en", 30000):
    if re.fullmatch(r"[a-z]+(?:'[a-z]+)?", word):
        rows.append((word, round(zipf_frequency(word, "en") * 100)))
payload = "".join(f"{word}\t{score}\n" for word, score in sorted(rows)).encode()
target.parent.mkdir(parents=True, exist_ok=True)
target.write_bytes(gzip.compress(payload, mtime=0))
print(f"{len(rows)} entries, {target.stat().st_size} bytes")
