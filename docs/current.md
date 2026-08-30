# Transfer search tab — current state

Working notes from the session that implemented `docs/superpowers/plans/2026-08-30-transfer-search-tab.md`
on branch `macos-fixes`. Covers what shipped, what is still open, and the CHPP findings that were
expensive to dig up.

## What shipped

Tasks 1–5 of the plan, one commit each:

| Commit | Change |
|---|---|
| `c8c1e682` | `TransferSearchQuery.validationErrorKey()` — age range, ≤4-level skill span, per-skill minimum levels, checked across all four skill slots (+7 tests) |
| `82c12c8f` | `module.transfer.search.TransferSearchConverter` — `Hit` → `ScoutEintrag`, null-details safe, injury/cards into the notes column (+5 tests) |
| `44407d02` | `ColumnModelId.TRANSFERSEARCH(23)`, a `ColumnModelId`-taking `TransferScoutingTableModel` constructor, `getTransferSearchTableModel()` + its `load()` line (+1 test) |
| `23e7f413` | `OnlineWorker.searchTransfers()` and `TransferSearchPanel` (+3 tests) |
| `08f3845f` | Tab wired into `TransfersPanel`, 10 new keys in `English.properties` only |

No DB schema change, so no `DBManager.DBVersion` bump and no `DBUpdater` case — as the plan specified.

### UI rework of the search tab

A later pass on the same branch reworked `TransferSearchPanel`:

- The search form moved from `FlowLayout` to `GridBagLayout`. In `BorderLayout.NORTH` a `FlowLayout`
  gets the full width but only its *single-row* preferred height, so as soon as the window was
  narrower than the whole row the wrapped controls were simply clipped away.
- Up to four skill filters, the maximum `transfersearch` accepts. The extra rows are laid out but
  invisible; `+`/`-` reveal and hide them, and `buildQuery()` fills `skillType2..4` only for the
  visible ones. `TransferSearchQuery` already carried all four slots, so nothing changed there.
- The hit details are shown below the table in a `JSplitPane`, in the very `TransferEingabePanel`
  the scout tab uses — full skills, position ratings and player id. Its own Add/Replace button is
  wired to the same `TransferScoutPanel`, so the panel's separate "add to scout" button is gone.
  The selected entry is passed as a `duplicate()`, so editing it there does not alter the hit list.
- The player id column is switched off in `TransferSearchPanel.emptyResultsModel()`. Doing it as a
  default in `TransferScoutingTableModel`'s constructor does **not** work: `UserColumnsTable.loadModel`
  calls `setDisplay(true)` for every column it finds in the database, so the default is undone as
  soon as the user has saved the column layout once. It also has to happen before `setValues()` —
  `HOTableModel.getDisplayedColumns()` caches its list on first use and never invalidates it.

No new translation keys — the `+`/`-` buttons carry glyphs, every label reuses an existing key.

**Verification status:** full suite green (541 tests, 41 suites, 0 failures). Task 6 of the plan
(manual verification against the live CHPP API) is **not** complete — it is blocked on the bug below.

## Where the tab is in the UI

The Transfers **module** is not opened at startup. In `ModuleManager.firstStart()` the line that would
make it one is commented out (`ModuleManager.java:178`), while Lineup, Misc, Series, Matches,
PlayerAnalysis, PlayerOverview, Statistics, Training and TeamAnalyzer all get `STATUS_STARTUP`.

- Open it via **Functions menu → Transfers**, or **F7** (`TransfersModule.getKeyStroke()`).
- Inside, the new tab is the 4th: `History | TransferTypes | TransferScout | Transfer search`.
- If Transfers is missing from the Functions menu entirely, the module is deactivated for that user:
  Options → *Module* tab (`ModuleConfigPanel`).

This is pre-existing behaviour, unrelated to the new tab.

## CHPP `transfersearch` — where the real spec lives

The full input table now lives in `docs/chpp_files.md` ("transfersearch — input parameters").

The docs page `https://www.hattrick.org/Community/CHPP/NewDocs/File.aspx?name=transfersearch` has
empty `#input` / `#input2` divs in the *served* HTML — view-source does not capture the parameter
table, but a **browser "save page as complete"** does, because the divs are filled in by then. Such a
save is checked in under `docs/raw/Transfer Search » … Hattrick_files/transfersearch.html`. Line 1404
of that page loads the spec separately:

```js
$.ajax({ url: 'Files/' + filename + '.js', async: false,
         success: function() { eval("file = " + filename + "JSON"); } })
```

**The authoritative, publicly fetchable spec is therefore:**

- <https://www.hattrick.org/Community/CHPP/NewDocs/Files/transfersearch.js>
- Datatype enumerations: <https://www.hattrick.org/Community/CHPP/NewDocs/JavaScript/chppDataTypes.js>

The same `Files/<name>.js` pattern works for every other CHPP file — useful for any future parser.

### Verified against that spec

Everything HO sends matches the documented vocabulary. Checked and correct:

- **Parameter names** in `TransferSearchQuery.toQueryString()` — `ageMin`, `ageDaysMin`, `ageMax`,
  `ageDaysMax`, `skillType1..4`, `minSkillValue1..4`, `maxSkillValue1..4`, `specialty`,
  `nativeCountryId`, `tsiMin/Max`, `priceMin/Max`, `pageSize`, `pageIndex`.
- **Required set** — `ageMin`, `ageMax`, `skillType1`, `minSkillValue1`, `maxSkillValue1`. All five
  are always sent.
- **`skillID`** — 1 Goaltending, 2 Stamina, 3 Set pieces, 4 Defending, 5 Scoring, 6 Winger,
  7 Passing, 8 Playmaking, **9 Trainer (not searchable)**, 10 Leadership, 11 Experience. The
  `TransferSearchQuery.Skill` enum matches, including the deliberate omission of 9.
- **`specialtyID`** — 0, 1, 2, 3, 4, 5, 6, **8** (there is no 7). `PlayerSpeciality` matches constant
  for constant.
- **Documented defaults** — `ageDaysMin=0`, `ageDaysMax=111`, `pageSize=25` (max 100), `pageIndex=0`.
- **Official worked example** — "players between 20 and 22 years old, with playmaking between level
  7 and 9" → `ageMin=20&ageMax=22&skillType1=8&minSkillValue1=7&maxSkillValue1=9`. This is exactly
  what `TransferSearchQueryTest.toQueryString_onlyMandatoryParameters` asserts.

Also worth recording: the file's own description says it is *"NOT meant to be fetched by bots to
automatically scan the transfer market, but instead upon manual user request"* — which is why the
panel is button-only and deliberately **not** registered with `RefreshManager`. Keep it that way.

## Hattrick rejects `ageMin` for a wide age range

A search with playmaking 7-9 and age **17-20** fails:

```
CHPP Fehler - 10 - Invalid parameter specified (10) Additional Info: ageMin
URL: https://chpp.hattrick.org/chppxml.ashx?file=transfersearch&version=1.1
     &ageMin=17&ageMax=20&skillType1=8&minSkillValue1=7&maxSkillValue1=9&pageSize=50&pageIndex=0
```

The identical search with age **20-22** - Hattrick's own documented example - returns hits. So the
rejection is about the age *values*, and `ageMin` is simply the parameter Hattrick names.

### Ruled out by experiment

- **Parameter names, skill and specialty mappings, transport, missing required parameters** - all
  verified against the spec, and the same request shape works with a different age pair.
- **Missing day bounds.** Sending `ageDaysMin=0&ageDaysMax=111` explicitly (the documented defaults,
  which is what Hattrick's own web form always does) reached the server and changed nothing - the
  17-20 search still failed.

### Still open: floor or window width

The two surviving explanations are not yet separated:

- **Floor** - 17 is below the lowest accepted `ageMin`.
- **Window width** - with `ageDaysMax` defaulting to 111, 17-20 is really 17y0d to 20y111d, a span of
  3 years and 111 days, against 2 years and 111 days for the working example.

**One probe separates them: age 17-19.** Works -> no floor, the span is the limit; fails -> 17 itself
is rejected. If it is the span, `20-23` then shows whether the limit is a two-year window.

Once known, encode it in `TransferSearchQuery.validationErrorKey()` with a test so it is caught
client-side. Neither constraint is documented in the CHPP spec.

The panel defaults are currently the documented example's `20-22` (`TransferSearchPanel.java:40-41`).

`Connector.getCHPPWebFile()` logs the full outgoing URL at INFO, so `logs/` has the exact request for
any attempt. Note that `createDistribution` runs `clean` first, which deletes `build/` - including
the distribution's `logs/` and `db/`.

## Follow-ups

- Add the `transfersearch` input table and constraints to `docs/chpp_files.md`. Its absence is why
  the file was implemented against assumptions in the first place.
- The 10 new `ls.transfersearch.*` keys exist only in `English.properties`. A maintainer has to add
  them to the POEditor project before release, or other languages will show `!key!`.
- Paging is deliberately not implemented (one page of 50). `ItemCount` is `-1` whenever more than 100
  players match, so exact totals are unavailable anyway.
