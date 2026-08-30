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

`docs/chpp_files.md` lists the file and its version but **no input parameters**. The docs page
`https://www.hattrick.org/Community/CHPP/NewDocs/File.aspx?name=transfersearch` renders them, but its
`#input` / `#input2` divs are empty in the served HTML — saving the page (even via view-source) does
not capture the parameter table. Line 1404 of that page loads it separately:

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

## Open bug: Hattrick rejects `ageMin`

A search with the panel's default form values fails with:

```
CHPP Fehler – 10 – Invalid parameter specified (10) Additional Info: ageMin
URL: https://chpp.hattrick.org/chppxml.ashx?file=transfersearch&version=1.1
     &ageMin=17&ageMax=20&skillType1=8&minSkillValue1=7&maxSkillValue1=9&pageSize=50&pageIndex=0
```

The message is Hattrick's own error XML, passed through verbatim by `XMLCHPPPreParser.getError()`
(`CHPP <Fehler> - <ErrorCode> - <Error>`).

### Ruled out

- **Parameter names** — `ageMin` is valid per the spec above. The earlier guess that it might be
  `minAge`/`maxAge` was wrong.
- **Skill and specialty mappings** — verified against `chppDataTypes.js`, see above.
- **Transport** — `Connector.getTransferSearch()` builds the URL the same way as every other CHPP
  file and goes through the shared, working `getCHPPWebFile()` OAuth path.
- **Missing required parameters** — all five are present.

So Hattrick is rejecting a **value**, not a name.

### Hypotheses

The failing call differs from the known-good documented example in the age pair alone
(`17–20` vs `20–22`):

- **A — age window too wide.** Omitting the day bounds means Hattrick applies `ageDaysMax=111`, so
  `ageMin=17&ageMax=20` is really 17y0d → 20y111d, about **four years**. For comparison, the
  website's own transfer search form (`/World/Transfers/`, ASP.NET `ddl*` fields) always sends four
  explicit age fields and produced a one-year window in a working request:
  `ddlAgeMin=19 ddlAgeDaysMin=0 ddlAgeMax=20 ddlAgeDaysMax=0`. Currently the stronger hypothesis.
- **B — a floor.** 17 is below the lowest accepted `ageMin`. Hattrick would name the first parameter
  of the pair either way, so the error text does not discriminate.

Neither constraint is documented in the CHPP spec.

### Next step

One live call decides it — keep playmaking 7–9 and search **age 20–22** (the documented example):

- Hits come back → hypothesis A. Fix: encode the real window limit in
  `TransferSearchQuery.validationErrorKey()` with a test, and correct the spinner defaults in
  `TransferSearchPanel.java:40-41` so it is caught client-side instead of round-tripping to Hattrick.
- Same error → not the window. Next probe: 20–22 with explicit `ageDaysMin=0&ageDaysMax=0`, mirroring
  what the website sends.

`Connector.getCHPPWebFile()` logs the full outgoing URL at INFO (`getCHPPWebFile <url>`), so `logs/`
has the exact request for any failing attempt.

Note that the website form cannot be used as a substitute: it is cookie-authenticated HTML scraping
against a different endpoint, not the OAuth CHPP API HO is built on.

## Follow-ups

- Add the `transfersearch` input table and constraints to `docs/chpp_files.md`. Its absence is why
  the file was implemented against assumptions in the first place.
- The 10 new `ls.transfersearch.*` keys exist only in `English.properties`. A maintainer has to add
  them to the POEditor project before release, or other languages will show `!key!`.
- Paging is deliberately not implemented (one page of 50). `ItemCount` is `-1` whenever more than 100
  players match, so exact totals are unavailable anyway.
