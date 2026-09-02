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
