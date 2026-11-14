# CHPP file docs, captured 2026-11-14

One Markdown file per CHPP file (60), plus `datatypes.md`, taken from the live CHPP docs at
`https://www82.hattrick.org/Community/CHPP/NewDocs/`. The docs are login-only and built in the
browser, so they are captured by `utils/chpp-docs/capture.js` in a logged-in Chrome tab, not
fetched directly. The script's header says how to run it; `utils/chpp-docs/split.py` then writes
the files here.

Each file holds:

- **Changelog** - every version, with its notes and its added/removed inputs and outputs
  (duplicates removed).
- **Input** - the "Simple" input view: every parameter, per `actionType`.
- **Output** - the "Simple" output tree for the **latest version**, once per `actionType`.
  Nesting is kept as an indented list: containers in bold, elements as `Name : Type — description`.
  An action whose tree equals an earlier one reads `Same as actionType=...`.
- **References** - the file's lookup tables, where it has any.

Not captured: the "Detailed", "XML" and "Example" tabs, and output trees for older versions. The
changelog says what changed between versions.

Type names such as `SkillLevel` or `GenderID` refer to `datatypes.md`. Error codes, the
`overrideIsSupporter` switch and which versions HO uses are in `docs/chpp_files.md`.

These are copies. When the live docs change, re-capture them rather than editing these files.
