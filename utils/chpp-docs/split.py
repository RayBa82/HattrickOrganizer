#!/usr/bin/env python3
"""Splits the chpp-docs.txt download of capture.js into one Markdown file per CHPP file.

    python3 utils/chpp-docs/split.py ~/Downloads/chpp-docs.txt docs/raw/chpp

Every part starts with a line "===== <name>.md". Existing files are overwritten; files the
download does not contain are left alone, and so is README.md.
"""
import pathlib
import re
import sys


def main(source: str, target: str) -> None:
    text = pathlib.Path(source).read_text(encoding="utf-8")
    out_dir = pathlib.Path(target)
    out_dir.mkdir(parents=True, exist_ok=True)
    parts = re.split(r"^===== (\S+\.md)\n", text, flags=re.MULTILINE)
    written = 0
    for name, body in zip(parts[1::2], parts[2::2]):
        if "/" in name or name == "README.md":
            raise SystemExit(f"refusing to write {name!r}")
        (out_dir / name).write_text(body.rstrip("\n") + "\n", encoding="utf-8")
        written += 1
    print(f"{written} files written to {out_dir}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    main(sys.argv[1], sys.argv[2])
