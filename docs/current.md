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

## Hattrick limits the age window, and the limit widens at 19

A search with playmaking 7-9 and age **17-20** fails:

```
CHPP Fehler - 10 - Invalid parameter specified (10) Additional Info: ageMin
URL: https://chpp.hattrick.org/chppxml.ashx?file=transfersearch&version=1.1
     &ageMin=17&ageMax=20&skillType1=8&minSkillValue1=7&maxSkillValue1=9&pageSize=50&pageIndex=0
```

The identical search with age **20-22** - Hattrick's own documented example - returns hits.

**The rule is on the width of the age window, and the allowed width depends on `ageMin`:**

| `ageMin` | Largest accepted `ageMax - ageMin` |
|---|---|
| up to 18 | 1 |
| 19 and above | 2, and no wider value has been tried |

`ageMin` is only the parameter Hattrick names in the error; the value it objects to is the pair. 17
and 18 year olds *can* be searched for — one year at a time, e.g. `17..18` or `17..17`.

Everything past this is unmapped: whether the allowance widens again at some higher age, and whether
`ageDaysMin`/`ageDaysMax` shift the boundary (`17y0d..18y111d` is nearly two years of real age and is
accepted, so the count is of the year numbers, not of elapsed time).

### Ruled out by experiment

- **Parameter names, skill and specialty mappings, transport** - all verified against the spec, and
  the same request shape works with a different age pair.
- **Missing day bounds.** Sending `ageDaysMin=0&ageDaysMax=111` explicitly (the documented defaults,
  which is what Hattrick's own web form always does) reached the server and changed nothing - the
  17-20 search still failed.
- **A floor on `ageMin`.** This was the previous reading of the same data and it was wrong; see
  below.

### The observations, and the false floor they first suggested

Run from the MCP `search_transfers` tool on 2026-08-31
(`~/Library/Application Support/HO/logs/HO-1788133014.log`, `HO-1788138555.log`,
`HO-1788142064.log` hold the exact URLs):

| `ageMin`..`ageMax` | Width | `ageMin` | Result |
|---|---|---|---|
| 17..19, 18..20 | 2 | ≤18 | rejected, empty answer |
| 18..21 | 3 | ≤18 | rejected, empty answer |
| 18..30 | 12 | ≤18 | rejected, empty answer |
| 19..21, 20..22, 22..24, 23..25 | 2 | ≥19 | hits |

Every row fits the width rule. It also *appeared* to fit a floor at `ageMin >= 19`, because no probe
in the set was both narrow and young - the width and the `ageMin` value moved together in all
fourteen requests.

The one apparent counter-example, a `17..17` search that failed, is not one: that request carried
`minSkillValue1=7&maxSkillValue1=11`, a 4-level skill span, and was rejected for *that* (§ *Fixed:
`transfersearch` rejected every 4-apart skill window* - it is the very request that discovered the
skill rule). It was the only evidence against a width rule and it never was evidence.

**`17..18` is the probe that separates the two readings for good** - it works under the width rule
and cannot work under a floor.

### Why none of this rests on the error text

`Additional Info: ageMin` is not usable as evidence. CHPP error 10 has already misnamed its cause
once here - the skill-span violation above came back as "No primary skill with min and max range has
been specified". The conclusions are built from which requests returned data, and the rival
explanations die on their own data points:

- **`pageSize` / `pageIndex`** - omitted on both sides, so not the discriminator. `TransferTools`
  had never sent `pageIndex` (`git log -S pageIndex` on it is empty); only
  `TransferSearchPanel.java:265` did. Every request in the three MCP-path logs carried `pageSize=50`
  and no `pageIndex`, failing and working alike. **Both are required and are now always sent** -
  `TransferSearchQuery.toQueryString()` fills in `pageSize=50` / `pageIndex=0` when the caller sets
  neither, so the one guard covers the panel and `search_transfers` together.
- **The skill filters** - two matched pairs settle it. `18..20` and `19..21` with an identical
  `skillType1=8 9..11 / skillType2=7 6..8` differ only in the age pair and land on opposite sides
  (`HO-1788138555.log`, 03:52:34 and 03:52:15); `min1=7` likewise appears on both sides.
- **`ageMax` alone** - `18..30` fails while `ageMax=30` is far outside anything else tried, and
  `23..25` works; neither bound decides on its own.

Success and failure were read off the caller, not the log, because the silent path recorded nothing.
That is fixed below, so the re-runs carry their own evidence.

### Enforced client-side

`TransferSearchQuery.validationErrorKey()` rejects `ageMin <= 18 && ageMax - ageMin > 1` with
`ls.transfersearch.error.youngagespan`, so a young window that Hattrick would refuse never leaves HO
and never costs a search against `mcpSearchesPerSession`. `TransferSearchService.search()` is the
only caller of `Connector.getTransferSearch()` and validates before fetching, so the search tab and
`search_transfers` are both covered by that one guard; `TransferTools.comparableQuery()` clamps an
18-or-younger player's window to a year before building the query at all.

The `ageMin >= 19` half is deliberately **not** guarded. A width of 2 is confirmed to work there and
nothing wider has ever been tried, so capping at 2 would refuse searches that may well be legal —
the guard covers only what is known to fail. Extend it if a wider window is confirmed to be refused.

The `search_transfers` schema states the rule in the `ageMin` description too, so a model reads it
before spending a search rather than after.

### Silent downloads no longer swallow the CHPP error

That table was expensive to produce because `Connector.getCHPPWebFile()` used to log the error and
show the dialog *only* when `silentDownload` was off — and `TransferSearchService.searchForAgent()`
turns it on deliberately, to keep a modal dialog off the MCP worker thread. Every MCP-side failure
was therefore an empty string with no trace anywhere, and `SearchFailed`'s "The CHPP error is in
HO's log" was simply false.

`Connector` now keeps the message in a `lastError` field, logs it on every path, and shows the
dialog only when not silent; `TransferSearchService` puts `lastError()` into the `SearchFailed`
message. An MCP client asking for 17 year olds now gets
`Hattrick rejected the transfer search: CHPP Fehler - 10 - Invalid parameter specified (10)
Additional Info: ageMin` instead of a blank, which is what let a model conclude "the market has no
17 year olds" from what was actually a rejected request.

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

## MCP server

Implemented per `docs/superpowers/plans/2026-08-30-mcp-server.md`:

- The MCP server ships off by default and is enabled in Options → MCP server. It binds `127.0.0.1`
  only and rejects any request carrying an `Origin` header or a non-loopback `Host`. **There is no
  authentication beyond that** — while it is on, any process running as this user can read the squad
  and write the lineup. That is the reason the switch is off by default.
- Transfer market search is always allowed; the per-session cap (Options → MCP server) is what
  bounds CHPP traffic for both callers. The former unlock button and `mcpMarketUnlockMinutes`
  parameter are gone.
- Scratch lineups must always be scored on a private `RatingPredictionModel`, because
  `LineupRatingCache` is keyed on `Lineup.ratingRevision` alone. **`optimize_lineup` is the known
  exception**: `Lineup.optimizeLineup` → `LineupAssistant` fetches the shared model itself, so that
  one tool does touch HO's own rating state. Fixing it means rewriting `LineupAssistant`.
- The HTTP executor is deliberately single threaded: handlers call `DBManager`, and
  `ConnectionManager` shares one JDBC `Statement` across all tables.
- `McpServerPanel` writes both `UserParameter.instance()` and `UserParameter.temp()`, because it
  takes effect immediately rather than on the options dialog's OK.
- The new `ls.options.mcp.*` keys are English-only until a maintainer adds them to POEditor.
- **Money crosses the tool boundary in the user's currency, not in swedish krona.** Everything HO
  stores internally — `AmountOfMoney`, and the raw `Salary` / `AskingPrice` numbers straight out of
  a CHPP file — is swedish krona, and the UI divides by `AmountOfMoney.getExchangeRate()` before
  showing anything. The first version of the tools handed the model `getSwedishKrona()`, so a euro
  team's wages and finances came back a factor of ten too large and the model reported them as
  euro. `McpJson.money` now does the conversion for every amount, `McpJson.swedishKrona` converts
  the price arguments of `search_transfers` back for the CHPP query, and `get_team`, `get_finances`
  and `search_transfers` each carry a `currency` field so the model can name the unit.

### Second pass: training, coach, and the assumptions behind a rating

A peer agent driving the server reported that HO's headline feature — training — was unreachable,
and that it could not tell what a rating had been computed against. Eleven tools now, not ten:

- **`get_training`** (`core/mcp/TrainingTools.java`) reports the current settings out of
  `HOModel.getTraining()` — type, intensity, stamina share, coach level, assistant level, plus the
  role ids that get full and partial training from `WeeklyTrainingType` — and, per player, the
  predicted skill changes from `FutureTrainingManager` with its `trainingSpeed` to rank by.
  It reads the **future training plan** (`DBManager.getFutureTrainingsVector()`), which stays empty
  until the user has opened the Training tab once; the tool then returns the settings plus
  `status: no_future_training_plan` and a note rather than an empty prediction. The status is a
  stable marker on purpose — from outside, "no plan stored" and "nobody is being trained" look
  identical, and only the first is something the user can fix by clicking. `FutureTrainingManager`'s constructor previews over
  `UserParameter.futureWeeks` before we ask for our own window, so a plan shorter than that logs a
  warning per player and changes nothing — the second `previewPlayer(weeks)` is what the numbers
  come from.
- **`get_team`** gained a `mood` block (team spirit, sub team spirit, confidence) and a `coach`
  block. The head coach is *not* in `HOModel.getStaff()` — he is `HOModel.getTrainer()`, a `Player`
  with `getCoachSkill()`/`getLeadership()`. That method never returns null; it fabricates an empty
  `Player` for NT teams, so a coachless team reads as `coachSkill` 0.
- **`get_squad`/`get_player`** now carry `skillsWithSublevel` (level + `Player.getSub4Skill`) next to
  the integer `skills`, plus `suspended` and `available`. Without sublevels there is no way to see
  which skill is about to pop, which is the whole point of tracking training.
- **Rating outputs** carry `hatStats90` and an `assumptions` block (team spirit, confidence,
  location, weather, plus `hrfId` and `snapshotDate` — team spirit and confidence move between
  downloads, so two evaluations are only comparable when the `hrfId` matches). Team spirit and confidence always did feed the numbers — the rating model is
  built from the `Team` object — they just were not reported, so two evaluations could not be
  compared honestly.
- **`set_player_group`** files players under HO's squad groups (`Player.setTeamInfoSmilie`, values
  `GroupTeamFactory.TEAMS_GROUPS`: `""` and `A-Team` … `F-Team`), and `get_squad`/`get_player` report
  the current one as `group`. The setter persists to `SPIELERNOTIZEN` on its own, so there is no
  store step; it lives in `LineupTools` for the `onEdt` hop, and refreshes the lineup panel
  afterwards the way HO's own group buttons do. All ids are resolved before the first write so a bad
  id cannot refile half the squad. Only the listed players change — moving an A/B split means one
  call per group, which is why `get_squad` had to report `group` in the first place.
- **`evaluate_lineup`/`apply_lineup` reject an injured or suspended player**, overridable with
  `allowUnavailable`. The check sits in `LineupTools.applySpec`, the one path both tools share. It
  reads `HOVerwaltung.instance().getModel()` directly rather than `McpJson.model()`: with no
  snapshot loaded there is nothing to check ids against, and that keeps `scratchLineup` usable in
  the unit tests, which have no model.

Still missing, in the order the peer ranked them: match history and match details, league table,
finances beyond the three totals in `get_team`, stadium utilisation, a TSI/wage/form-aware player
valuation, the youth academy, substitutions and conditional orders in `apply_lineup`, and
minutes-played-at-trained-position per player.

## The rest of the MCP read surface

Five more tools, sixteen in total. None of them needed a new CHPP parser, a DB migration or a new
translation key — in every case HO already held the data and nothing exposed it.

### `get_matches` / `get_match` / `predict_match` — `core/mcp/MatchTools.java`

- **`get_matches`** wraps `DBManager.getOwnPlayedMatchInfo(count, officialOnly)`: result,
  competition, location and outcome, most recent first. Every row carries `matchTypeId` next to
  `matchId`, because `matchType` is half the key of every match row — a match id alone does not
  identify one.
- **`get_match`** joins `loadMatchDetails` (both sides' sector ratings, HatStats, tactic, attitude,
  formation, attendance) with `getMatchLineupPlayers` (our own per-player match ratings, which is
  what makes predicted-vs-actual checkable). `matchType` is an optional argument: passing it null
  makes `MatchesKurzInfoTable.getMatchesKurzInfoByMatchID` fall back to `WHERE MATCHID=?`, which is
  all a caller holding a bare id can do. The result row and the details are downloaded separately,
  so a match with no details returns `status: no_match_details` rather than an empty object — the
  same kind of stable marker `get_training` uses, and for the same reason: from outside, "never
  downloaded" and "nothing to report" look identical.
- **`predict_match`** drives `MatchPredictionManager`: our seven sectors out of the current lineup
  on a **private** rating model, the opponent's seven supplied as arguments (take them from
  `get_match` on one of their recent games), then `calculateNMatches` over both. Reports win / draw /
  loss probabilities plus goals and chances per match for either side. The tactic level is
  `getTacticRating(lineup, 0)` with the `+1` that `SimButtonListener.getTacticLevel` applies.
- **`simulate_lineup`** is `predict_match` on a scratch lineup: the body of `predict` moved into a
  `simulate(Lineup, args)` the two share, the schema's opponent half moved into `opponentProperties`.
  With `positions` given it runs `LineupTools.scratchLineup(args)` — the exact `evaluate_lineup`
  builder, private model, injured/suspended guard and all — so a model can loop XIs against the next
  opponent without ever calling `apply_lineup`. Without `positions` it is `predict_match`.

### `get_league_table` / `get_finances` — added to `TeamTools`

- **`get_league_table`** is `DBManager.getSpielplan(xtra.getLeagueLevelUnitID(), season)` →
  `MatchFixtures.getTable()` → all eight `SerieTableEntry` rows. Note the league *level unit* id out
  of `getXtraDaten()`, not `Basics.getLiga()`.
- **`get_finances`** reports the whole `Economy` statement with last week's copy beside it — the
  trend is the point, one week's numbers are not readable on their own — plus the `Verein` fan club
  and the staff levels that drive the cost line, and the stadium. The stadium block joins
  `tool.arenasizer.Stadium` (stands, under construction, future sizes, expansion cost) with
  `Matchdetails.getZuschauer()` over the last few home matches and reports the utilisation ratio:
  capacity on its own cannot answer an expansion question.

**Verification status:** full suite green (585 tests, 51 suites, 0 failures). The tool bodies still
need a loaded `HOModel` and a database, so the new tests assert the surface — names, schemas,
required arguments — exactly like the other tool tests.

### `get_team_transfers` — added to `TransferTools`

The managed team's own buy/sell history. `DBManager.getTransfers(season, bought, sold)` already
selects it keyed on the current team id, and `ConvertXml2Hrf` calls
`PlayerTransfer.downloadMissingTransfers` on every team download, so the table is kept current with
no live CHPP call — the tool is a pure DB read like `get_finances`. Each row carries direction
(buy/sell relative to the managed team, derived from `buyerid == teamId` since the DB has no `type`
column), price, TSI at transfer time, the counterparty team, and the mother-club / previous-club
fees (`PlayerTransfer` computes those lazily off the loaded player info). `season` (Hattrick season
number, omit for all) and `direction` (`buy`/`sell`, omit for both) are optional filters. The result
is paged — `limit` (default 20, max 100) newest-first from `offset` (default 0) — with
`totalCount` / `returned` / `hasMore` beside the rows so a caller can ask for "the last ten" without
pulling a lifetime of history into context. `totalSpent` / `totalEarned` (from `getSumTransferPrices`,
lifetime, unaffected by the filters) and `currency` round it out. The full list is still loaded and
player-info-hydrated before the slice — same as HO's own Transfers tab — so the paging is a
context-size guard, not a DB-load one; push `LIMIT`/`OFFSET` into `TransferTable` if that ever bites.

### Deliberately not built: `refresh`

`OnlineWorker.getHrf(JDialog parent)` is Swing-coupled and puts progress dialogs on screen, so an
MCP-triggered download would have to marshal to the EDT and would visibly take over the user's
window. It is also a live CHPP call, the same territory as the transfer search. The
`assumptions` block on every rating output already carries `hrfId` and `snapshotDate`, which is
enough for a client to notice a stale snapshot and tell the user to press download itself.

Still missing after this pass, in the order the peer agent ranked them: substitutions and
conditional orders in `apply_lineup`, a TSI/wage/form-aware player valuation, the youth academy, and
minutes-played-at-trained-position per player.

## Fixed: `transfersearch` rejected every 4-apart skill window

Live symptom, `logs/HO-1788133014.log`:

```
?file=transfersearch&…&skillType1=8&minSkillValue1=7&maxSkillValue1=11&skillType2=7&minSkillValue2=5&maxSkillValue2=9
CHPP Fehler - 10 - Invalid parameter specified (10) Additional Info:
No primary skill with min and max range has been specified
```

The same query with `7..10` / `5..8` goes through. So **Hattrick counts the window's levels
inclusively**: "The difference between min and max can be up to 4 levels" in the CHPP spec means at
most *four levels in the window*, i.e. `max - min <= 3`. A difference of 4 is not reported as a range
error but as the primary skill being missing altogether, which is what made it hard to read.

- `TransferSearchQuery.check()` now rejects `max - min > 3`. Both callers (the search tab and
  `search_transfers`) validate through it, so one guard covers both.
- `TransferTools.maxLevel()` clamps the comparables window to `minLevel + 3`.
- `docs/chpp_files.md` records the discrepancy with the published spec.

### The dialog that froze HO

The same run left HO with a modal `Fehler` dialog on screen and the MCP request hanging until
`Broken pipe`. `Connector.getCHPPWebFile` shows `JOptionPane.showMessageDialog` on any CHPP error
unless `silentDownload` is set — on the MCP worker thread that blocks the tool call until a human
clicks OK. `TransferSearchService.searchForAgent` now sets `silentDownload` for the duration of the
call and restores it afterwards; a blank answer becomes a `SearchFailed` pointing at the log. The UI
path (`OnlineWorker.searchTransfers`) is unchanged and still shows the CHPP text.

Any future MCP tool that reaches CHPP has to do the same — no Swing off the EDT.

**Verification status:** full suite green (589 tests, 0 failures). Live CHPP verification of the
3-level window is the log evidence above, not a test.

## CHPP write operations over MCP (2026-09-03)

Shipped: `set_training`, `send_match_order`, `list_challenges` / `arrange_challenge` /
`respond_to_challenge`, and a two-step `place_bid`. All route through `core.net.ChppWriteService`
(same shape as `TransferSearchService`: `fetch*` seam, per-session cap
`UserParameter.mcpChppWritesPerSession`, typed `ChppWriteException`). URL building + raw calls in
`Connector`; `XMLChallengesParser` is new. Non-bid writes fire directly; `place_bid` needs a
confirm token (static map on `TransferTools`, 120 s TTL, single-use).

`place_bid` amounts follow the same currency contract as the rest of the MCP surface: the model
bids in the user's currency, `TransferTools` converts to swedish krona (`McpJson.swedishKrona`)
before the CHPP call and stores the krona value in the pending-bid entry; the preview echoes the
user-currency numbers back and every money field in the result is converted back with
`McpJson.money` and tagged with a `currency` field. `ChppWriteService.BidResult.highestBid` is
raw krona. The CHPP `playerdetails` doc confirms `bidAmount` / `maxBidAmount` are Money **in SEK**,
so the conversion is correct. The bid call is `playerdetails` **version 3.2** (the version whose
doc documents `placeBid`; HO's read path still uses 3.0), `actionType=placeBid`, with `teamId`
(the managed senior team) required. The post-bid response is the full `view` output; the bid state
is read from `<Player><TransferDetails>` (`HighestBid`, `Deadline`) and the leading bidder from
`<Player><BidderTeam><TeamName>`. A CHPP `<ErrorCode>` in the response (not a Supporter, bid too
low, deadline passed, ...) becomes a `ChppWriteException` with `errorKey "BidFailed"`.

OAuth scopes `set_training,manage_challenges,place_bid` added to the hard-coded `OAuthDialog`
string — a token authorized before this ships does NOT carry them and there is no recovery path
(by design); re-authorize to pick them up.

`set_training` is `training` **version 2.2**, `actionType=setTraining`, `teamId` required (CHPP
uses no default there), params `trainingType` / `trainingLevel` (0-100) / `trainingLevelStamina`
(5-100). The response is the training `view` output - `<Team>` carries the current
`TrainingType` / `TrainingLevel` / `StaminaTrainingPart`, plus `NewTrainingLevel` (attr
`Available`) when an intensity change is pending. There is **no** `TrainingSet` flag (the plan
invented one; it was dead code). `TrainingCommitted.intensity` is the pending `NewTrainingLevel`
when present, else the current `TrainingLevel`, and `intensityChangePending` says which; a CHPP
`<ErrorCode>` becomes a `ChppWriteException`.

The `challenges` doc (version 1.6) corrected `XMLChallengesParser`: under `<Team>`,
`<ChallengesByMe>` / `<OffersByOthers>` hold `<Challenge>` / `<Offer>` items whose
`TrainingMatchID`, `MatchID`, `MatchTime`, `FriendlyType`, `<Arena>` and `IsAgreed` are **siblings
of `<Opponent>`** - the parser had been reading `IsAgreed` and `ArenaName` from inside `<Opponent>`
(so both were always empty/false). `MatchID` (the real match once agreed, for `get_match` /
`send_match_order`) is now captured too. `MatchTime` is kept as a string (the schema types it
`integer` but it is a datetime in practice). `arrange_challenge` / accept / decline / withdraw
params match the doc (`opponentTeamId`, `matchType`, `matchPlace`, `neutralArenaId`,
`trainingMatchId`, `isWeekendFriendly`).

Verified against the code and the official CHPP docs (`playerdetails`, `training`, `challenges`)
for wire format, versions, params and response structure, but NOT against a live round-trip.
Capture real responses and adjust the fixtures if anything differs.

**Verification status:** full suite green (631 tests, 0 failures). No live CHPP round-trip;
all three write families' wire format and response shape are from the CHPP docs.

## `get_open_bids` — the transfers you've bid on (2026-09-03)

New read tool on `TransferTools`, 26 tools total. Answers "which players have I bid on that are
still open, and am I winning?" — which nothing exposed before, because HO never tracked its own
outgoing bids (`place_bid` persists nothing; `TRANSFERLISTING` is market-search snapshots only).

- **CHPP `currentbids` (version 1.0)** is the source — Hattrick's own bid-tracking list, so it also
  sees bids placed on the Hattrick website, not just through `place_bid`. `Connector.currentBids()`
  (`file=currentbids&version=1.0`, no params — `actionType=view` and `teamId` both default),
  `ChppWriteService.listCurrentBidsForAgent()` (a read, silent-fetch wrapper, no write-cap charge,
  same shape as `listChallengesForAgent`), `XMLCurrentBidsParser` is new.
- **Wire structure** (from `Files/currentbids.js` — the `File.aspx` page is Cloudflare-walled, the
  `.js` is not): `<HattrickData>` → `<TeamId>` then repeated `<BidItems TrackingTypeId="N">`, each
  holding `<BidItem>` with `TransferId` / `PlayerId` / `PlayerName` / `<HighestBid>` (`Amount`,
  `TeamId`, `TeamName` — the block is empty when there are no bids) / `Deadline`. The parser keeps
  `Deadline` as the raw `yyyy-MM-dd HH:mm:ss` string and returns `CurrentBids(teamId, items)` so
  `leading` = `HighestBid/TeamId == HattrickData/TeamId` needs no loaded `HOModel`.
- **Attribute casing bug (fixed 2026-09-03).** The real `currentbids` response names the group
  attribute `TrackingTypeID` (capital `ID`, like `TeamID`/`MatchID` elsewhere), not
  `TrackingTypeId`. The parser read the wrong casing, so every group came back as id 0, every row
  fell out of `isOwnBidCategory`, and `get_open_bids` reported "no open bids" even for a transfer
  the user had bid on and been outbid. `XMLCurrentBidsParser.trackingTypeAttr` now accepts both
  spellings. Casing confirmed against `pychpp` (`BidItems[@TrackingTypeID=...]`); no live round-trip.
- **`trackingTypeId` is only partly documented.** The doc names 5/8/9 for `ignoreTransfer` as
  "hotlisted, losing bids, finished". The rest of the mapping the tool uses —
  1 selling, 2 leading_bid, 3 mother_club, 4 previous_club, 10 transfer_prospect — is from the
  `lucianoq/hattrick` Go library, **not CHPP-verified**. `get_open_bids` carries the raw
  `trackingTypeId` on every row and says so in a `note`. Capture a real response and confirm 2 = "I
  hold the leading bid" before trusting the default filter.
- **Default** returns trackingTypeId 2 or 8 (the ones you've actually bid on): leading bids (2)
  need a future `Deadline`, losing bids (8) stay for 24h past their deadline so a recent loss is
  still reported (`LOSING_BID_GRACE`). `includeAll` returns every tracked transfer with its raw
  category. Each row:
  `playerId`, `playerName`, `transferId`, `deadline`, `highestBid` (user currency, `McpJson.money`),
  `highestBidTeamName`, `leading`, `trackingTypeId`, `category`.
- OAuth: `currentbids` `view` needs no extra scope beyond a normal read token (only `ignoreTransfer`
  / `deleteAllFinished`, not implemented, need `place_bid` scope which the write pass already added).

**Verification status:** full suite green (640 tests, 55 suites, 0 failures). No live CHPP round-trip — wire
format from the `currentbids.js` spec, `trackingTypeId` labels partly from a third-party library.

## `TRANSFERBID` — the bid ledger for market-value tracking (2026-09-03)

`get_open_bids` and `place_bid` now persist, so a losing bid still leaves a record of what the
player sold for. Nothing is exposed as a new tool yet — this is the data layer a later valuation
tool (or a `get_open_bids` enrichment) will read.

- **New table `TRANSFERBID`** (`core.db.TransferBid` / `TransferBidTable`, `DBVersion` 1001→1002,
  `DBUpdater.updateDBv1002`). Keyed on **`PLAYERID`**, one row per player — a re-listing overwrites
  the previous bid; the full observation history stays in `TRANSFERLISTING`. Columns: `OURBID` /
  `OURMAXBID` / `BIDPLACEDAT` (from `place_bid`, Swedish krona, null for a website bid), `DEADLINE`,
  `LATESTHIGHESTBID` / `LATESTLEADING` / `LATESTTRACKINGTYPEID` (refreshed each `get_open_bids`),
  `FINALPRICE` / `OUTCOME` (`won`/`lost`, set once), `FIRSTSEENAT` / `LASTSEENAT`.
  - No `TRANSFERID` column: `AbstractTable`'s load path reads every `BIGINT` with `rs.getInt`
    (`getInteger`), so a real Hattrick transfer id would overflow. The id was informational only, so
    it was dropped rather than working around the framework.
  - `TransferBid.fromObservation(existing, item, teamId, now)` is the pure merge: refresh the
    `latest*` fields, preserve `ourBid`, and on `trackingTypeId == 9` (finished) with a passed
    deadline and a non-zero highest bid, set `finalPrice` once and `outcome` from `latestLeading`.
  - VARCHAR nulls come back as `""` from `AbstractTable.getString`, so `outcome` is `""` while a
    transfer is open, never null. Consumers must treat `""` as "no outcome".
- **`TransferTools.recordBidObservations`** runs on every `get_open_bids`, for **all** tracked
  transfers (not just own-bid categories): upserts the ledger row and writes a thin
  `TransferListing.fromBidItem` snapshot (market data, skills null) so the existing resolve pass
  fills the sale price in. **`TransferTools.recordOurBid`** runs after a successful `place_bid`
  commit. Both are best-effort — the CHPP call already succeeded and spent its budget, so a DB error
  logs and is dropped, it does not fail the tool call. Same untested-glue status as `search()`'s
  `storeTransferListing`; the pure factories and the table are unit-tested.
- **Skills-fill** is a second branch in the Options → MCP server *resolve* button
  (`McpServerPanel.resolveExpiredListings`): for an unresolved listing whose player has a
  `TRANSFERBID` row but no skill-bearing `TRANSFERLISTING` snapshot
  (`transferListingHasSkillData` = any row with `PLAYMAKER IS NOT NULL`), one
  `OnlineWorker.downloadPlayerDetails` call writes a `TransferListing.fromPlayerDetails` snapshot.
  Both CHPP calls (`transfersPlayer` + `playerdetails`) share the one `resolveCap` per run. Still a
  human-only button — an agent must not be able to start a batch of CHPP calls.
- This covers the narrow case of a player bid on via the Hattrick website and never searched in HO;
  a player found through `search_transfers` / `find_comparables` already has a rich `TRANSFERLISTING`
  row.

**Verification status:** full suite green (653 tests, 0 failures). No live CHPP round-trip.

## The Lineup tab silently clears slots an MCP write filled (2026-09-03)

A peer MCP session reported `apply_lineup` returning a filled left forward (roleId 113) that a
later `get_lineup` showed as empty, with every other slot from the same write intact. Root cause is
in HO's own Lineup tab, not in the MCP layer.

**`PlayerPositionPanel.setPlayersList` empties any position whose occupant is not in the panel's
candidate list:**

```java
// Put back current player if new filters allows it
this.iSelectedPlayerId = -1;
if (oSelectedPlayer != null) { /* … only if oSelectedPlayer is in oCandidates … */ }

Lineup lineup = HOVerwaltung.instance().getModel().getCurrentLineup();
if (iSelectedPlayerId == -1) {
    switch (m_iPositionID) {
        case IMatchRoleID.setPieces -> lineup.setKicker(0);
        case IMatchRoleID.captain   -> lineup.setCaptain(0);
        default                     -> lineup.setSpielerAtPosition(m_iPositionID, 0);
    }
}
```

`oCandidates` is `LineupPositionsPanel.refresh`'s `filteredPlayers`, which applies the assistant
panel's two user toggles: the **squad-group filter** (`isAssistantGroupFilter` / `getAssistantGroup`
/ `isAssistantSelectedGroupExcluded`) and **exclude players from the last match**
(`isAssistantExcludeLastMatch`, which drops anyone in `getPreviousLineup()`'s starting eleven).
`refresh()` runs on every Lineup tab refresh — no click on the slot itself is needed. So a player
the filter hides gets wiped out of the *model*, not merely out of the combo box.

**The toggle in play was exclude-last-match, not the group filter.** Kruzslicz was in the starting
eleven of the 2026-09-01 friendly (matchId 771367534, at roleId 111) and was the *only* member of
the league eleven who had played it — every other starter from that friendly was a B-team player
who was not fielded, and every other member of the league eleven sat that match out. The set of
players the filter hid maps one-to-one onto the set of slots that got zeroed. His squad group is
A-Team, so the group filter could not have singled him out.

Confirmed reproducible: the same slot was wiped a second time on a later tab refresh, no download,
`hrfId` still 13, the other ten slots intact.

That matches the report exactly: the write landed, the user then looked at the Lineup tab during an
intervening turn, the tab refreshed, and the one player the filter excluded was zeroed. It also
explains why the lineup was a broken 3-4-0 to begin with.

For an agent it was silent: `hrfId` and `snapshotDate` never move (the model is not rebuilt), so
nothing in a tool response hinted that the state had changed underneath.

### The fix is in the tool, not in the tab

**The Lineup tab's behaviour is deliberate and stays as it is.** The filter is how the manager says
which players are in contention, and having it clear a slot is the intended gesture — the way to
undo it is to change the setting in HO. The bug was never that HO dropped the player; it was that
`apply_lineup` reported success for a write HO was about to undo.

Turning the write-back around in `PlayerPositionPanel.setPlayersList` was tried first and
**reverted**: it stops a filter from ever unfielding an already-fielded player, which changes what
the Swing UI does for every user, not just for MCP callers.

So `apply_lineup` now refreshes the Lineup tab *before* it stores or reports anything:

```java
final var team = model.getCurrentLineupTeam();
applySpec(team.getLineup(), spec);
final var lineupPanel = HOMainFrame.instance().getLineupPanel();
if (lineupPanel != null) {
    lineupPanel.update();          // let HO's own filters have their say, now
}
model.storeLineup(team);
final var result = describe(team.getLineup(), ratingModel);
final var refused = refusedByHo(team.getLineup(), spec);
// -> result.notApplied + result.warning
```

The eviction therefore happens *inside* the tool call rather than minutes later, so what gets
stored and reported is the lineup HO is really left holding. Two consequences:

- `describe` already re-reads the live `Lineup`, so the returned formation and ratings are now the
  settled ones — the reported 3-5-1 instead of a 3-5-2 that will not survive.
- `refusedByHo` diffs the spec against that settled lineup and adds a `notApplied` array (`roleId`,
  `requestedPlayerId`, `actualPlayerId`) plus a `warning` naming the two assistant settings and
  telling the caller not to report those positions as set. A payload an LLM might skim past is not
  enough on its own; the explicit list and warning are what make the failure unmissable.

This also fixes a smaller pre-existing gap: `apply_lineup` never notified the UI at all, so the
Lineup tab did not show an agent's write until it refreshed for some other reason. `set_player_group`
already called `lineupPanel.update()`; now both writers do.

Deliberately **not** done: predicting the filter verdict in `core.mcp` by reading
`isAssistantGroupFilter` / `isAssistantExcludeLastMatch` and reimplementing
`LineupPositionsPanel.refresh`'s rules. Asking the panel to reconcile and then reading the result
cannot drift from what the panel actually does.

**Residual gap:** if the Lineup module panel has not been built, `getLineupPanel()` returns `null`,
nothing reconciles during the call, and the report is optimistic again — the eviction then happens
when the tab is first opened. Lineup is a startup module (`ModuleManager.firstStart`), so the panel
normally exists, but an agent talking to an HO whose Lineup module the user deactivated is still
exposed.

**Ruled out, with evidence — for anyone who reaches for these first:**

- **The `apply_lineup` response is a real read-back, not an echo of the request.** `LineupTools.apply`
  runs `applySpec` → `HOModel.storeLineup(team)` → `describe(team.getLineup(), …)`, and `describe`
  iterates `lineup.getAllPositions()`.
- **The multi-slot write does not lose a slot.** `LineupApplyOrderTest` replays the reported batch
  (a broken 3-4-0 rewritten as a 3-5-2, including the 103→101, 104→103, 102→105, 109→107, 106→109
  moves) on a real `Lineup`. All fourteen slots land, in ascending roleId order and with the
  emptied slots written last. The eleven-player guard in
  `MatchRoleID.setPlayerIdIfValidForLineup` never trips, because each move frees its old slot
  before filling the new one.
- **The persist is complete.** `MatchLineupPlayerTable.storeMatchLineupPlayers` deletes every row
  for the match/team and re-inserts every position with `playerId > 0`. HSQLDB's own write log
  (`<dbFolder>/database.log`) holds such a cycle verbatim — 17 deletes, then 17 inserts including
  roleId 113.
- **Both tools see the same object.** `HOModel` caches `aufstellung`, and `getCurrentLineup()`
  returns `team.getLineup()` by reference.

**Separate, and also real:** every CHPP download rebuilds the model from a fresh HRF, and
`HRFStringParser` (~line 177) calls `hoModel.storeLineup(new MatchLineupTeam(…))` with the lineup
section `ConvertXml2Hrf` filled from `OnlineWorker.downloadNextMatchOrder` — i.e. **Hattrick's**
stored match order. A download therefore also discards a local lineup edit. Not what happened here
(`hrfId` stayed 13 throughout), but the same class of hazard.

Practical consequence for callers: `apply_lineup`'s return value proves the write reached HO, not
that it survives. Re-read with `get_lineup` immediately before `send_match_order`.

**Also fixed: `apply_lineup` dropped every bench write.** `SPEC_DOC` advertised bench roleIds
114-118. A `Lineup`'s bench vector holds 200-213 (`initPositionen553`); 114-118 are the *match
lineup file* ids and belong to no slot, and `setSpielerAtPosition` resolves through
`getPositionById` and returns without a word when that finds nothing. This is the cause of the
already-observed "the bench param does not take" note in the assistant repo's `docs/mcp-tools.md`.

Two changes: `SPEC_DOC` now names 200-206 by role plus 207-213 for the matching backups, and
`applySpec` routes both its loops through a new `LineupTools.requireSlot`, which throws
`McpToolException` for a roleId no slot carries. No silent drops in either loop now — the field
loop had the same hole for anything outside 100-113.

**Side bugs found on the way, not fixed:**

- `XMLMatchOrderParser`'s three forward cases guard on `"RightForward"` / `"CentralForward"` /
  `"LeftForward"` while writing `"…ID"` keys; every other role guards on the `…ID` key. The guard
  is therefore always true and `addAdditionalPlayer` is dead code for forwards.
- `Lineup.toJson()` serialises `positions` **positionally** — each entry is only
  `{"id": playerId, "behaviour": b}` with no roleId (`MatchRoleID`'s two `@Expose` fields), so
  Hattrick derives the slot from the array index. Correct today because `m_vFieldPositions` is only
  ever built in ascending order, but nothing enforces that.

**Verification:** full suite green (657 tests, 0 failures). `LineupApplyOrderTest` covers the batch
replay, the `requireSlot` refusal and `refusedByHo`. No Swing source was changed. The one path with
no coverage here is the `lineupPanel.update()` call itself — there is no harness for the Lineup tab,
so it wants a manual pass: turn on *exclude players from the last match*, apply a lineup that fields
someone who played the last match, and confirm the response comes back with `notApplied` naming
that slot rather than reporting it as set.

## `get_opponent_ratings` — the missing half of `predict_match`

`predict_match` and `simulate_lineup` require the opponent's seven sector ratings as arguments, and
the only tool that produced such numbers was `get_match`. But `get_matches` reads
`getOwnPlayedMatchInfo`, and HO's database only ever holds matches involving **us**. For the team we
are about to play — which is the only opponent anyone wants to simulate against — there was nothing
to read, so the two simulator tools were unusable unless the fixture happened to be a rematch. That
is the gap this closes: `get_upcoming_matches` gives an opponent's `teamId`, and
`get_opponent_ratings` turns that id into the arguments `predict_match` asks for, under exactly
those names.

**Flow** (`MatchTools.opponentRatings`): `Connector.getMatchesArchive(teamId, now-days, now)` →
`XMLMatchArchivParser` → drop the unfinished ones and, unless `competitiveOnly` is off, the
friendlies and tournaments (reserves play those, so their ratings say little about the side that
turns up for a league game) → sort by date descending → for the newest `count` (default 5), one
`Connector.downloadMatchdetails` + `XMLMatchdetailsParser` each. Roughly one CHPP request per match
plus one for the archive; it is a read, so the write cap is not involved.

Deliberately **not** routed through `OnlineWorker`. `getMatchArchive` and `downloadMatchData` are the
UI orchestration layer: they drive `HOMainFrame.setWaitInformation()` and pop modal
`Helper.showMessage` dialogs on failure, which would hang a tool call, and `downloadMatchData` also
persists the match, downloads both teams' logos and re-reads the arena. Going to `Connector` and the
parser directly is what the rest of `core.mcp` already does (`TransferTools` uses
`TransferSearchService`, the writes use `ChppWriteService`) and it stores nothing — a scouting read
should not rewrite the user's match history. `module.teamAnalyzer` remains the place that does
persist another team's matches, and when it has, `get_opponent_ratings` reuses the stored details
instead of downloading them again.

**Output.** `opponentRatings` is a copy-paste block: the seven averages under the
`opponentMidfield` … `opponentAttackRight` names, plus `opponentName`. `opponentTacticType` and
`opponentTacticSkill` come from the **most recent** match rather than an average — a tactic is a
choice, not a quantity, and averaging one invents a tactic nobody played. `matches[]` carries each
match through the existing `side()` serialiser, so the per-match keys are identical to `get_match`'s,
with `location` and the real opponent added. The averages are read back out of those same
`JsonObject`s (`SECTOR_KEYS`, in the same order as `OPPONENT_SECTORS`), which is why the average can
never disagree with the list it is an average of.

**Bug fixed on the way: `get_match`'s `no_match_details` status was dead code.**
`MatchDetailsTable.loadMatchDetails` returns `new Matchdetails()` — never null — for a match it holds
no row for, so `details == null` was never true and a match whose details were never downloaded came
back as `status: ok` with every rating at zero. `MatchTools.storedDetails` now asks the content
(`details.getMatchID() == matchId`), which is also how `get_opponent_ratings` decides whether it has
to download. `DBManager` itself was left alone: a dozen callers rely on the non-null return
(`MatchLineupTeam`, `MatchLineup`, `YouthTraining`, `module.series.Model`), and changing that
contract to fix one reader would be the wrong end of the stick. `TeamTools`'s attendance loop was
already safe — it guards on `getZuschauer() <= 0`.

**Verification:** full suite green (662 tests, 0 failures). `MatchToolsTest` now covers
`opponentReport` over a hand-built pair of `Matchdetails` — one home, one away for the same scouted
team — pinning the side pick (averaging the wrong end would hand `predict_match` the ratings of the
scouted team's *opponents*), the tactic-from-newest rule, and, against the live `predict_match`
schema, that every required argument is present in `opponentRatings`. Its four schema tests also
stopped indexing `tools()` positionally and use the `tool(name)` helper that was already in the file,
so inserting a tool no longer breaks them. The download path itself is untested — same as every other
CHPP path in the repo — and wants a manual pass against a real opponent id.

## `get_opponent_squad` — who they have, and who can play

The companion to `get_opponent_ratings`: that one says how well a team performed, this one says
*with whom*. `HattrickManager.downloadPlayers(teamId)` → `XMLPlayersParser` → `PlayerInfo`, one CHPP
request, mapped to JSON in `TeamTools.opponentPlayer`.

Routed through `module.teamAnalyzer.ht.HattrickManager` rather than `Connector` directly — the
opposite call from the one `get_opponent_ratings` makes, and deliberately so. `PlayerInfo` is the
only class in the codebase that knows what the players file yields for a **foreign** team: it reads
age, TSI, form, stamina, experience, loyalty, motherclub, salary, specialty, cards, injury level,
transfer-listed and the `LastMatch` block, and it reads **no skills at all**, because Hattrick does
not publish another team's skills. That omission is the whole shape of this tool, and it is
load-bearing knowledge worth reusing rather than re-deriving from the CHPP docs. The call also files
the result in the Team Analyzer's own player store (`TAPlayerTable`, keyed player+week) — the same
rows that module writes when the user opens its tab, so the write is additive rather than a
surprise.

**Three things the payload gives that nothing else does:**

- **Availability.** `injuryWeeks` (-1 fit, 0 bruised, above that weeks left) and `bookingStatus`
  (`none` / `yellow` / `doubleYellow` / `suspended`) collapse into one `available` flag. Knowing
  their first-choice keeper is suspended is worth more than any rating average.
- **Their last eleven, free.** Every player carries `lastMatch.matchId` and `lastMatch.roleId`, so
  the players sharing the highest `matchId` *are* that match's XI, positioned. No extra requests, no
  lineup downloads.
- **`estimatedSkills`.** Since there are no real skills, the payload runs
  `module.opponentspy.OppPlayerSkillEstimator` — HO's own estimator, the one
  `SpecialEventsPredictionManager` uses — over age, wage, TSI, form, stamina, specialty and the
  position the player last played. Named `estimated*` and captioned in the response `note`, because
  a model handed a bare `skills` block will quote it as fact.

The estimator is position-driven and `CalcPlayerBaseProvider.setBaseSkills`'s fall-through branch is
`setAsGoalkeeper`, so a player whose last position is unknown (never played, or came off the bench —
`MatchRoleID.getPosition` returns `UNKNOWN`) gets **no** `estimatedSkills` rather than a keeper's.
Emitting a striker with goalkeeping 15 would be worse than emitting nothing.

**Bug fixed: a player who has never played crashed the whole squad download.**
`XMLPlayersParser.createPlayer` fills the six `LastMatch_*` keys inside a try/catch, so for a player
with no `<LastMatch>` block they end up absent — and for an empty one they end up `""` via
`SafeInsertMap`. `PlayerInfo`'s constructor then ran `Integer.parseInt(null)` and
`Float.parseFloat(null)` on them, throwing `NumberFormatException` / `NullPointerException` straight
out of `HattrickManager.downloadPlayers`, whose try/catch only wraps the `Connector` call. One
matchless player therefore took the entire scouted squad with it — in the Team Analyzer too, not
just here. The three ints and two floats now go through `parseIntWithDefault` and a new
`parseFloatWithDefault` (`lastMatchDate` was already safe — `HODateTime.fromHT` returns null for a
null or empty string). Fixed in `PlayerInfo` rather than guarded per-player in `core.mcp`, since
teamAnalyzer is the older and more exposed caller.

**Found, not fixed:** all eight of `CalcPlayerBaseProvider`'s `setAs*` methods cascade
`if (age < 22) … else if (age >= 22) … else if (age >= 27) … else if (age >= 29) …` (25/28 in the
goalkeeper case). The second branch swallows every age above 21, so the third and fourth are
unreachable and every senior player is estimated on the 22-year-old base table. Left alone on
purpose: it would change the numbers HO
already shows in opponent-spy and special-event prediction, which is a behaviour change nobody asked
for and which no test in this repo pins down.

**Verification:** full suite green (668 tests, 0 failures). `TeamToolsTest` now drives
`TeamTools.opponentPlayer` over a hand-built `SafeInsertMap` shaped like the foreign-team players
file: the visible attributes and the wage conversion, the `lastMatch` block, the suspended /
injured / single-yellow availability cases, the estimate gating (present for roleId 112, absent for
roleId 0), and the matchless player that used to throw. The estimator's own arithmetic is
deliberately not pinned — only that the seven expected keys appear — since it is HO's model, not
this tool's. The download itself is untested, as every CHPP path in this repo is.

## `get_player_history` — the past, which nothing read before

Requested by the assistant repo's session: every tool so far reads the present (`get_squad`) or the
forecast (`get_training`), and their sell decisions hang on a question only the past can answer — is
this trainee's growth flattening? HO already holds the answer and is the *only* place that does:
`SPIELER` stores one row per player per downloaded HRF, i.e. a dated skill snapshot series, and
`DBManager.loadPlayerHistory(playerId)` already returns it ordered by date. No new query, no
migration, no CHPP request — the data was simply never exposed.

Added to `TrainingTools`, next to the forecast it mirrors. Arguments: `playerIds` (default the
current squad), `weeks` (default 26), `includeSnapshots` (default **false**).

**The series is irregular by nature** — HRFs exist only where the user actually downloaded one — and
that is the whole design constraint. Every derived number sits next to the coverage it came from:

- **`levelsPerHtYear`** is the trained skill's **sub-skill-inclusive** gain between
  `coverage.firstDate` and `coverage.lastDate`, over `coverage.observedWeeks`, times 16. Using
  `getValue4Skill + getSub4Skill` rather than counting whole level-ups is what makes it responsive:
  a player at 5.95 reads as growing, instead of as flat until he pops. It is **null** when there is
  no trained skill, fewer than two snapshots, or an observed span under `MIN_RATE_WEEKS` (3) — two
  snapshots two days apart would otherwise extrapolate one level into nearly sixty a year.
- **`coverage.gaps`** lists every hole longer than `GAP_DAYS` (8), with its length. Training resolves
  weekly, so anything longer leaves an update unobserved and a level-up inside it cannot be dated.
  A weekly downloader produces no gaps; a missed fortnight produces one.
- **`coverage.firstDate`** is the player's real first snapshot in the window, never padded to the
  window start — a player bought halfway through has a short span, and the span is what the rate is
  divided by.
- **`daysSinceLastSnapshot`** covers the other end: a series that stops three weeks ago says nothing
  about now. `weeksSinceLastLevelUp` is measured from `lastDate` rather than from today for exactly
  that reason, and `asOf` is returned so the caller can do the subtraction itself.

`derived.trainedSkill` is resolved live from `WeeklyTrainingType.instance(training.getTrainingType())
.getPrimaryTrainingSkill()` rather than hard-coded, since the trained skill changes; `levelUps`
covers all eight tracked skills, so when training switched mid-window the caller can see where the
growth actually went. Skill *drops* are reported the same way as rises (`from` above `to`) — a skill
falling back with age is as much a sell signal as one climbing.

Two things fell out for free: an id that has **left** the squad still works, because the snapshots
outlive the player (useful for costing a trainee already sold), and `snapshots[].skills` carries the
sub-level, so the raw curve is smooth rather than a staircase.

**Deliberately uppercase skill names** (`"PASSING"`), matching `get_training`'s existing
`primarySkill` and `skillChanges[].skill`, not `get_squad`'s lowercase `skills` map. Within one tool
group, consistency with the neighbour won.

**Verification:** full suite green (677 tests, 0 failures). `TrainingToolsTest` drives
`TrainingTools.historyOf` — the per-player report, split from the database read for exactly this
purpose — over hand-built `Player` series: seventeen weekly snapshots climbing passing 5.0 → 7.0
assert `observedWeeks` 16 and `levelsPerHtYear` 2.0 with no gaps; a three-week hole asserts one gap
of 21 days; a two-day span asserts a null rate beside a counted level-up; a rise-then-drop asserts
both are dated and only the rise counts; the same 16-week climb with every sub-skill at zero asserts
`rateBasis: "integer"` beside an identical-looking 2.0, and a series with one populated sub-skill but
whole-level endpoints asserts `"subskill"`; and the empty / single-snapshot cases assert their status
markers. TSI and wage drift are asserted in the user's currency.

**`derived.rateBasis`** — `"subskill"` or `"integer"` — added after the assistant repo asked for it,
and it is the sharpest thing in the payload. Sub-skills are not downloaded; they are HO's estimates
from replaying the training history, and a player HO never replayed carries zero in all of them. The
rate then silently becomes a count of whole levels while still arriving as a plausible float, and no
caller can tell from the numbers, because a genuine 5.00 and an estimate-less 5 are the same value.
`snapshotCount` / `observedWeeks` are a proxy for a *short* history, not for a sub-skill-less one,
and the two come apart. Computed over the whole series rather than the two endpoints: the replay
populates all of a player's rows at once, so it is all-or-nothing in practice, and testing only the
endpoints would call a player who happened to level at both ends estimate-less.

## Opponent scouting: still not done

The *predicted* XI, as opposed to the last one.
`module.teamAnalyzer.report.TeamReport` + `SpotReport` + `TeamLineupBuilder` already compute
who-usually-plays-where by appearance frequency across a filtered set of matches, which is strictly
better than "whoever played last". Not reused, because the path into it runs through
`MatchPopulator`, which reads `SystemManager.getActiveTeamId()` static state and needs
`DBManager.loadMatchLineup` — so driving it from a tool call would mean both mutating the team the
user has selected in the Team Analyzer tab and persisting the opponent's match lineups. `lastMatch`
covers the common case at zero cost; the appearance-frequency version wants either a teamId
parameter threaded through `MatchPopulator` or its own lineup fetch.

## `add_to_scout` / `list_scout` — the shortlist survives the session (2026-09-05)

Requested by the team-assistant session: `search_transfers` produces a shortlist that evaporates at
session end, and nothing could write it anywhere HO would show it again.

**HO's scout list is purely local.** `SCOUT` is a plain `AbstractTable`, written and read only by
`TransferScoutPanel`. It is *not* a Hattrick bookmark: `bookmarks` is a read-only CHPP file HO does
not use at all (`docs/chpp_files.md`), and there is no CHPP action that adds one. So these two tools
cost no CHPP request, and the user sees the entries only on HO's Transfers → TransferScout tab, never
on hattrick.org. Both tool descriptions say so, and point at `get_open_bids` for the Hattrick-side
tracking list.

**The write goes through the panel, never to the table.** `TransferScoutPanel.saveScoutListe()` is a
delete-all-then-insert of the panel's *in-memory* `Vector`, and `HOMainFrame.shutdown()` calls it
(`HOMainFrame.java:283`). A row written straight into `SCOUT` behind an already-open Transfers tab
would therefore be wiped at exit, hours later and silently — the same class of bug as the Lineup tab
clearing MCP-filled slots, but with the damage deferred to shutdown. Routing through
`TransferScoutPanel.addScoutEintrag` instead also refreshes the table and re-points the `ScoutThread`
deadline alarm at the new list. `getTransferScoutPanel()` creates the Transfers tab on demand, so the
tool works whether or not the user has opened it; that is exactly what shutdown already does.

Dedupe is free: `ScoutEintrag.equals` is by player id and `addScoutEintrag` removes an equal entry
first, so re-adding a player replaces him. After adding, the tool calls `saveScoutListe()` itself —
the panel otherwise only persists at exit, and writing the live list back is staleness-free by
construction.

**Where the data comes from.** `add_to_scout` takes player ids only, and builds the entry from the
newest `TRANSFERLISTING` row (`DBManager.loadLatestTransferListing`) — no CHPP call. Every hit of a
`search_transfers` is snapshotted into that corpus already, so the shortlist workflow is covered
end to end; a player never searched for comes back in `notFound` with the fix in the message.
`TransferSearchConverter.toScoutEintrag` gained a `TransferListing` overload next to the `Hit` one,
and `notes()` now takes the two boxed values so both call it. Nationality is not part of a listing
snapshot and stays at the default.

`list_scout` reads the panel's list back, market data as of when each player was added rather than
live. `LineupTools.onEdt` went package-private and is shared — every panel touch is on the EDT.

No `remove_from_scout`: deprioritised by the requester, and the user can prune in the UI.
No schema change, so no `DBVersion` bump.

## Market value: real sale prices, and `estimate_market_value` (2026-09-05)

The corpus existed but held nothing to value against — 200 observations, **zero** resolved sale
prices. Asking prices are what sellers want, not what players go for, so until sale prices land the
corpus can only ever produce an upper bound.

### The resolve pass was reinventing something HO already had

`McpServerPanel.resolveExpiredListings()` called `XMLParser.getAllPlayerTransfers(playerId)`,
took the last transfer's price, stamped it on one `TRANSFERLISTING` row and **threw the
`PlayerTransfer` records away**. `XMLParser.updatePlayerTransfers(playerId)` was already sitting
next to it: same single `transfersPlayer` call, but it stores every transfer in HO's own `TRANSFER`
table — which the per-player transfer history UI (`PlayerDetailPanel`) already reads, and which two
other call sites already use.

So the pass now reuses it, and `updatePlayerTransfers` returns the transfers instead of `void` (the
two existing callers ignore the value). One CHPP call, two places that keep the answer.

No pollution of the team views: `TransferTable.getTransfers(season, bought, sold)` and
`get_team_transfers` filter on `buyerid` / `sellerid` being the managed team, which a market
player's transfer never is.

The pass moved out of the Swing panel into `core.net.TransferListingResolver` so it has two callers:
the Options button, and `OnlineWorker.getHrf` after a successful download. Still never a tool call —
an agent cannot start a batch of CHPP calls, which was the point of keeping it in the UI.

Two correctness fixes while extracting it:

- **One call per player, not per observation.** A player seen in four searches is four rows, and the
  old loop would have made four identical `transfersPlayer` calls. It now dedupes by player id and
  over-fetches rows (`cap * 5`) so a handful of duplicates cannot starve a run whose real bound is
  the call count.
- **`storeSalePrice` is keyed on the deadline, not on one observation.** One lookup resolves every
  row of the listing that sold, within a 2-day window of the sale date — a Hattrick transfer
  completes at its deadline, so the window only absorbs skew and still leaves an earlier, separately
  listed spell of the same player unresolved.

`mcpResolveCap` (default 20) is a new `UserParameter`; the Options field was previously not
persisted at all and reset to 20 on every dialog open.

### `estimate_market_value`

Reuses `comparableQuery(player, ageTolerance, skillTolerance)` — the same age window, two best
positional skills and specialty that `find_comparables` derives — but runs it against the local
corpus instead of the CHPP market. No call, no search cap.

- `basis: resolved_sales` once at least 3 comparable sales are known, else `observed_listings`,
  which is flagged in the response as an upper bound. The threshold is flat and marked `ponytail:`;
  one real sale is a single draw, twenty asking prices at least bracket the market.
- Median, not mean — a corpus with one absurd asking price should not move the number.
- `observedWithinDays` (default 112, one Hattrick season) is **what stands in for pruning**.
  Nothing deletes from `TRANSFERLISTING`, and it shouldn't: a listing from three seasons ago is
  still evidence about that season. Bounding the *read* keeps the history without letting it vote.
- Empty sample returns `estimate: null` with the fix in the note, rather than a fabricated number.

The skill column name reaches the SQL by concatenation, so `TransferListingTable.loadComparables`
validates it against the table's own declared columns and throws otherwise.

**Verification:** full suite green — 685 tests, 57 suites, 0 failures.
