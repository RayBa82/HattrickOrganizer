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
  (`McpServerPanel.resolveExpiredListings`): for an unresolved listing with no skill-bearing
  `TRANSFERLISTING` snapshot (`transferListingHasSkillData` = any row with `PLAYMAKER IS NOT NULL`)
  and a deadline inside `SKILL_FILL_GRACE`, one `OnlineWorker.downloadMarketPlayer` call writes a
  `TransferListing.fromPlayerDetails` snapshot — but **only if the player happens to be listed again
  at that moment**. Both CHPP calls (`transfersPlayer` + `playerdetails`) share the one
  `resolveCap` per run. Still a human-only button — an agent must not be able to start a batch of
  CHPP calls.
- This covers the narrow case of a player bid on via the Hattrick website and never searched in HO;
  a player found through `search_transfers` / `find_comparables` already has a rich `TRANSFERLISTING`
  row.
- Two things this paragraph got wrong until 2026-09-05, corrected in full further down under *the
  scout re-listing watch*: the old guard also required a `TRANSFERBID` row, and the call went
  through `downloadPlayerDetails`, which returns null for anybody else's player — so the pass
  stored nothing at all. The branch is now `TransferListingResolver.observeForCorpus`.

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

**Side bugs found on the way** (both fixed later - see *Three long-standing side bugs*):

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

**Found here, fixed later** (see *Three long-standing side bugs*): all eight of
`CalcPlayerBaseProvider`'s `setAs*` methods cascade
`if (age < 22) … else if (age >= 22) … else if (age >= 27) … else if (age >= 29) …` (25/28 in the
goalkeeper case). The second branch swallows every age above 21, so the third and fourth are
unreachable and every senior player is estimated on the 22-year-old base table. Left alone at the
time because it changes the numbers HO already shows in opponent-spy and special-event prediction,
which was a behaviour change nobody had asked for and which no test in this repo pinned down.

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

**The scout's price column is not the asking price** (2026-09-05). `SCOUT.Price` is a single column
and both `TransferSearchConverter.toScoutEintrag` overloads fill it with
`max(askingPrice, highestBid)` — the cost to take the player now, which silently becomes the leading
bid once bidding passes what the seller asked. `list_scout` used to publish that as `askingPrice`,
which was simply wrong. It is `price` now, and `listScout` re-reads the entry's newest
`TRANSFERLISTING` row to add the real `askingPrice` and `highestBid` alongside it — the corpus has
kept them apart since day one, so no schema change and no new CHPP call. Both are absent for an
entry typed into the TransferScout tab by hand. The re-read runs *after* the `onEdt` block, so the
database work stays off the EDT the way `add_to_scout`'s does.

## The three prices, across every transfer tool (2026-09-05)

Asking price / current price / sale price is what the model learns a market from, so each tool was
audited for all three. `McpJson.listing` (`estimate_market_value`) and `McpJson.hit`
(`search_transfers`, `find_comparables`) were already complete — `hit` has no sale price because a
live listing has not sold yet, which is the honest answer, not a gap. `get_team_transfers` has the
realized price only, and that is all CHPP `transfersTeam` sends.

`get_open_bids` was the one real hole: `currentbids` carries the highest bid and nothing else, so a
finished transfer reported the price it went for without ever saying that it *was* the price, and a
lost bid never showed what it lost by. `addLocalBidContext` now fills that from what HO already has
— `askingPrice` from the newest `TRANSFERLISTING` row, and `salePrice` / `outcome` / `ourBid` from
the `TRANSFERBID` ledger that `recordBidObservations` refreshes two lines earlier. No CHPP call, no
schema change. Asking price is skipped when it is 0, which is what a listing snapshotted from
`currentbids` alone carries.

**`list_scout`'s two new prices are the newest observation, not the add-time one.** `listScout`
re-reads through `loadLatestTransferListing`, so a later `search_transfers` over the same profile —
or a re-listing, which is a different transfer under the same player id — moves `askingPrice` and
`highestBid` while `price` and `deadline` stay frozen at the moment `add_to_scout` ran. Fresher is
the better number, so this is left as is, but it is no longer one instant: `observedAt` ships beside
them to say which. The alternative, storing both prices on the scout entry, costs a `SCOUT` column
and a `DBVersion` migration to buy a *staler* answer — declined.

**Hattrick does not always send an asking price.** Checked against the live database (271 stored
observations, read out of `database.log`): 255 carry a real `ASKINGPRICE`, 16 carry 0 — and those 16
have age and TSI filled in, so they are genuine `transfersearch` hits with details, not the
`fromBidItem` snapshots that never had an asking price to begin with. No rule found for which
listings omit it; the 16 span cheap and expensive players alike. Consequence: a stored 0 means "not
sent", never "the seller asked nothing", so both `list_scout` and `get_open_bids` leave the
`askingPrice` key out entirely rather than publish a zero the model would read as a price.
`highestBid` is published even at 0, where zero honestly means nobody has bid.

This is also the trap in verifying the scout-price mislabel from a small sample: the players that
show it most clearly are the ones with no asking price, where `max(asking, bid)` is trivially the
bid. The `hattrick` session sampled three of them (Krokus, Kilchherr, Lehel — all in the zero-asking
16). The mislabel is real, but it bites on the other 94% too, whenever bidding runs past the ask.

Deliberately **not** added: a market-statistics or price-index tool. Every tool here returns rows and
lets the model do the arithmetic (`estimate_market_value`'s own docstring says its median is "a
starting anchor, not an answer"), and an aggregate would freeze one weighting into Java where the
model cannot see past it. The binding constraint on market knowledge is the resolution rate of the
corpus — how many observations get a real sale price attached — not the field list.

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

**It returns the comparables, not just the number.** The first version handed back a median and hid
the rows, which is backwards when the caller is a model: a median collapses wage, TSI, form, the
skills outside the query window and how stale each observation is — exactly the things an LLM can
weigh and a median cannot. `comparables` now carries up to 40 matching listings, newest observation
first, each with those fields plus asking price, highest bid and the sale price where known, and
`comparablesMatched` says how many the query found in total. The subject player rides along under
`player`, so one call is self-contained. `estimate` stays as a labelled anchor and `estimateNote`
says plainly not to trust it over the rows.

The skill column name reaches the SQL by concatenation, so `TransferListingTable.loadComparables`
validates it against the table's own declared columns and throws otherwise.

**Verification:** full suite green — 685 tests, 57 suites, 0 failures.

## `add_to_scout` must not materialise the Transfers tab (2026-09-05)

The first version reached the scout panel through `HOMainFrame.getTransferScoutPanel()`
unconditionally. `HOTabbedPane.getModulePanel` **creates the tab and calls `setSelectedIndex`** when
it is not already there, so a scout write with the Transfers tab closed would have switched the
user's visible tab out from under them mid-task.

`HOMainFrame.storeUserSettings` already guards the very same call with
`isModuleTabVisible(IModule.TRANSFERS)` for this reason; the tools now do too:

- **Tab open** — write through the panel, as before. The in-memory `Vector` is the authority and
  `saveScoutListe()` would otherwise clobber a direct table write at shutdown.
- **Tab closed** — write straight to `SCOUT`. No in-memory list exists to disagree with, and the
  panel loads from the table when the user opens it.

Both branches still run on the EDT: the visibility check reads Swing state.

`add_to_scout` reports which branch it took in `visibleInUi`, so the agent can tell the user whether
the table refreshed in front of them or the entries are waiting for the tab to open.

### Scout entries are not re-priced

Worth being explicit, because it is easy to assume otherwise: a scout entry keeps the asking price
and deadline from the moment it was added, forever. Nothing updates it — not the resolve pass, not
`ScoutThread` (which only fires deadline alarms). Sale prices land in `TRANSFERLISTING` and
`TRANSFER`, which is what `estimate_market_value` reads; they do not flow back into `SCOUT`.
This is pre-existing HO behaviour, not something the MCP tools changed, and `list_scout` says so.

### …except for the sale, which is now explicit data (DB 1003)

A resolved sale writes two new SCOUT columns rather than a line of free text:

| Column | Type | Meaning |
|---|---|---|
| `SalePrice` | `DECIMAL`, nullable | what the player actually went for |
| `SoldAt` | `TIMESTAMP`, nullable | when that transfer completed |

Both null while he is still listed, and for an entry whose sale HO never saw. `ScoutEintrag.isSold()`
is the readable test. The **asking price column is left alone** — it means "asking price" everywhere
else in this table and HO's scout UI has read it that way for years.

A number rather than a note because it is one a tool can compare against a bid and the table can sort
and format; the note version could only be read. `list_scout` returns `salePrice` and `soldAt`
alongside the snapshot `askingPrice`, and the tool description spells out which is which — the two
are easy to confuse and mean opposite things about time.

Visible in HO too: a "Sold for" column (key `ls.scout.saleprice`, English only) directly right of the
asking price in the transfer scout and transfer search tables.

### The scout table's columns (2026-09-05)

`TransferScoutingTableModel`'s array order is now `id | name | speciality | price | sold for |
deadline | …`. Speciality is a new column (`ls.player.speciality`, `PlayerSpeciality.toString`), the
motherclub/HV column is gone, and "Sold for" moved up from the end.

**A new column in this model is invisible until the saved layout is dropped.** `TRANSFERSCOUT` and
`TRANSFERSEARCH` are deliberately *not* in `UserColumnController.getAllModels()`, so neither table can
be configured in Options → columns. `UserColumnsTable.loadModel` hides every column it finds no stored
row for and maps stored width/order by **array position**, so a reordered array both scrambles the
order and swallows anything appended — which is exactly why the first "Sold for" column never showed
up. `updateDBv1003` therefore deletes `USERCOLUMNS` rows `14000–14999` and `23000–23999`, letting
`insertDefault()` rebuild the layout from the array. Note a DEV build re-runs the newest migration on
every start (`DBManager.getDBVersion`), so both layouts reset each dev launch until `DBVersion` moves
past 1003. Any future column change here needs the same delete.

**Migration:** `DBVersion` 1002 → 1003, `previousDBVersion` 1001 → 1002, `updateDBv1003` adds the two
columns with `tryAddColumn`. That is guarded by `columnExistsInTable`, so the migration re-run DEV
builds do on every start is a no-op — the same property `createTable` has, and worth checking for the
same reason.

`module.transfer.scout.ScoutList` owns the open-tab / closed-tab rule that `add_to_scout`,
`list_scout` and the resolver all need, so the branching exists once rather than in each caller.
The resolver hops onto the EDT for it and swallows failures — recording a sale must never undo a
sale price that is already stored — and skips an entry that already carries one.

### The scout list is resolved on every download, and first

The pass originally iterated `TRANSFERLISTING` only, which would have left most scout entries
permanently unresolved: **only the MCP `search_transfers` snapshots hits into that corpus**.
`TransferSearchPanel` — HO's own transfer search tab — calls `OnlineWorker.searchTransfers` and
stores nothing, and a hand-typed entry was never there either. So a player added to the scout list
through the UI had no listing row for the resolver to walk.

`resolve` now walks the scout list first and the corpus with what is left of the cap. The scout list
is short, it is what the user is actually waiting on, and it must not starve behind hundreds of
listing rows. Both passes share the `seen` set, so a player on both costs one CHPP call, and that
call writes both places.

Trigger: `OnlineWorker.getHrf`, i.e. `DownloadDialog` → *download team data*. That runs on the EDT
(`actionPerformed`, no worker thread), which is also where the Options button runs, so the resolver
touches `ScoutList` — Swing state — from the right thread. The `invokeLater` that the first version
used is gone: deferring meant the scout write landed after the listing write and after the download
UI had closed, for no benefit.

### A default deadline must not match an unrelated transfer

`ScoutEintrag` defaults its deadline to the moment it was created, so an entry typed in by hand and
never given a real one looks expired immediately. Its player's most recent transfer — possibly
seasons old — would then be recorded as "the sale".

`ScoutList.isTheWatchedTransfer` guards it: the sale date has to sit within the same 2-day window of
the entry's deadline that `storeSalePrice` uses on the corpus, and a missing date on either side is
a no. A Hattrick transfer completes at its deadline, so the window only absorbs skew.

### The corpus has to get better as it grows, not worse

`loadUnresolved` selected `WHERE SALEPRICE IS NULL` and ordered oldest first. Nothing ever gives a
sale price to a listing that did not sell — withdrawn, expired with no bidder, relisted — and plenty
do not. Those rows would have sat permanently at the head of the queue and been retried on **every**
download. As they accumulated they would have consumed the whole 20-call budget and new listings
would have stopped resolving: the corpus getting worse the bigger it got, which is the opposite of
the point.

`RESOLVEDAT` now means "HO looked", not "it sold", and `loadUnresolved` adds `AND RESOLVEDAT IS
NULL`. `markResolveAttempted` stamps it after a lookup that *answered*, over that player's expired
rows still lacking a price. A thrown exception deliberately does not mark: a network blip must not
burn a listing's one attempt.

The scout list has the same failure mode and nowhere to record the attempt, so `scoutCandidates`
stops offering an entry once its deadline is more than a fortnight past. A completed transfer shows
up immediately, so anything still unresolved after that never sold.

### What a corpus row actually holds

Worth stating, because it changes how narrow the corpus really is: `transfersearch` returns the
**complete** skill block per hit — all seven positional skills plus stamina, form, experience,
leadership, specialty, TSI, salary, age/ageDays, cards and injury — and `TransferListing.fromHit`
stores every one of them as its exact level. Not the search window: the values.

So a row records skills *outside* the query that found it. A search for wing backs on DEF 12-14
also deposits those players' passing, playmaking, set pieces, keeper and scoring, all usable as
evidence for profiles never searched for. "The corpus only contains what you searched for" was too
pessimistic — it contains full skill vectors for everyone those searches happened to surface.

The rows that arrive without skills come from `currentbids` (bookmarks, mother-club, finished
transfers, your own listings), which carries market data only, plus the occasional search hit whose
deadline passed before it was fetched — Hattrick strips those to market data too. An earlier note
here said `fillMissingSkills` covers them with one `playerdetails` call. That was wrong twice over:

- the call never worked for a market player at all. `parsePlayerDetailsFromString` returns `null`
  for anyone outside your own team, so the pass spent one CHPP call per corpus row and stored
  nothing, silently, on every run;
- and it could not have worked even with that guard lifted, because those rows are unlisted players
  by definition and Hattrick prunes an unlisted player's `PlayerSkills` down to `StaminaSkill`.
  Worse, `xmlIntValue` defaults a missing child to `0`, so an unguarded parse would have filed seven
  fabricated zero-skills into the corpus.

That slice of the corpus is unfillable by any means. `observeForCorpus` (formerly
`fillMissingSkills`) is now honest about it: it stores a row only when the player happens to be back
on the market at the moment it runs, and stores nothing otherwise. The same call is what the scout
re-listing watch uses — see `docs/superpowers/specs/2026-09-05-scout-relist-watch-design.md`.


What the corpus can never hold is **sub-skills**. Hattrick publishes integer levels for market
players, so two "defending 13" players can differ by almost a level in real strength. That is an
irreducible noise floor under any valuation built from this data, and no amount of accumulation
removes it.

### Descriptions that had gone stale

Two were wrong in a way that costs CHPP budget, since a model routes on them:

- **`find_comparables`** never said it spends one of the session's ~20 searches, and never mentioned
  that `estimate_market_value` does the same age / two-skill / specialty matching against stored
  listings for free and with real sale prices. It now says to try the local one first and come here
  for today's live market or a too-thin sample.
- **`search_transfers`** never said its hits are stored permanently with their full skill set, or
  that HO later fills in what those players sold for. A model had no way to know a search keeps
  paying off after the results scroll past.

The rest were already accurate: `estimate_market_value` and `list_scout` were rewritten with their
behaviour, and `add_to_scout` reports which branch it took in `visibleInUi` rather than claiming
anything in prose.

### Nulls were being dropped on the wire

`McpHttpServer` serialized with `new Gson()`, which omits nulls by default. So every field this code
deliberately writes as null vanished: `details` on an expired market hit, `salePrice`/`soldAt` on a
player who has not sold, `estimate` on an empty sample. `McpJson.hit` adds `JsonNull.INSTANCE`
explicitly — clear intent, silently discarded.

The cost is not cosmetic. An absent key is indistinguishable from a field the running build does not
have, and a downstream session read a missing `salePrice` as "the feature is not shipped" and
published that. It would have drawn the same wrong conclusion on the correct build.

Now `new GsonBuilder().serializeNulls().create()`. As a side effect a parse-error response carries
`"id": null`, which is what JSON-RPC 2.0 actually requires. Covered by a test, since the failure mode
is invisible in normal use.

### `transfersearch` Details is conditional — sourced, not inferred

Several javadocs asserted that Hattrick drops the Details block once a deadline has passed. That was
unsourced. The authoritative spec
(<https://www.hattrick.org/Community/CHPP/NewDocs/Files/transfersearch.js>) confirms it verbatim:

> Container with more details. Empty if the deadline has passed.

So a search result *can* carry a hit with no skills. It needs a deadline to pass between the query
and the response, or Hattrick's index to lag, so it is rare — but it is a documented shape, not a
parser defensive check.

The part worth knowing: **`SellerTeam` lives inside Details**, so it disappears with it. HO's parser
substitutes `0` / `""` / `0` for `sellerTeamId`, `sellerTeamName` and `sellerLeagueId` rather than
null, and those sit at the *top* level of a hit. An expired hit therefore reads `details: null`
alongside a plausible-looking `sellerTeamId: 0` — a sentinel, not a team. `BidderTeam` is a separate
top-level container and survives.

Downstream this is already handled: `TransferListing.fromHit` stores such a row with market data
only, `fillMissingSkills` fills its skills from one `playerdetails` call, and until then it cannot
match `loadComparables` (the NULL age and skill columns exclude it), so it never pollutes a
valuation.

### The sale price is not in the HRF

Worth stating precisely, because the download is the trigger and it is easy to assume it is also the
source. A team download brings *this* team's own transfers. A player HO merely watched on the market
belongs to somebody else, so what he went for is not in the HRF at all — it takes one
`transfersPlayer` call per player. The pass piggybacks on the download because that is a moment the
user chose to spend CHPP traffic, not because the data arrives with it. Hence the cap.

That also fixes where it was hooked. It sat inside `OnlineWorker.getHrf`, which `DownloadDialog`
only calls when the **team data** box is ticked — so a match-only download resolved nothing. It now
runs at the end of the whole download routine, outside that branch:

- fires on any download the user asked for, not only a team-data one;
- runs after everything else, so it does not delay the parts being watched;
- skipped when `bOK` is false — a broken download should not be followed by a burst of further CHPP
  calls;
- new progress key `ls.update_status.transfer_prices`, since up to 20 lookups is long enough that a
  silent UI looks hung.

`OnlineWorker.resolveTransferListings()` went public for it. The scheduling now lives in
`DownloadDialog` next to every other download step, the logic stays in `OnlineWorker`.

### Never fetch what is already local

Every lookup the pass makes is now guarded against data HO already holds:

| Call | Skipped when |
|---|---|
| `playerdetails` (skills fill) | any snapshot of that player already carries skills (`hasSkillData`) |
| `transfersPlayer` (sale price) | the listing has a price, or was already looked at (`SALEPRICE`/`RESOLVEDAT`), or its deadline has not passed, or the player was handled earlier in this run |
| `transfersPlayer` (sale price) | **new:** HO's own TRANSFER table already contains the transfer |

The last one was the real hole. `DBManager.getTransfers(playerId, true)` is a plain local read, and
the table is populated for every player we bought or sold — a team download stores our own transfers
— and for anyone an earlier pass fetched. So the players we spent money on, the ones we most want
priced, were the ones guaranteed to cost a redundant CHPP call. `resolvePlayer` now reads locally
first and only calls out when the sale genuinely is not on disk; when it resolves locally it reports
zero calls, so the cap is spent only on real traffic.

Rainer's other condition was already met: a lookup only ever happens for a transfer whose deadline
has passed. A live listing is never asked about.

**A latent bug fell out of this.** The old code took `transfers.get(size - 1)` as "the most recent"
— but `XMLParser.updatePlayerTransfers` treats that same element as the player's *first* transfer
when deciding homegrown status, so the list is newest-first and that index was the **oldest**
transfer. The 2-day window was silently masking it: an ancient transfer never matched, so nothing was
written and the row looked simply unresolvable. `saleAt` now searches by date and does not care about
the order, which is also what makes the local table usable, since it comes back ordered differently
again. Covered by `TransferListingResolverTest`, which asserts the match holds with the history in
either order.

### The 20-per-download cap was a false analogy

It was copied from `mcpSearchesPerSession`, which exists because Hattrick genuinely restricts
transfer searches — market search has to be unlocked and is limited. `transfersPlayer` is nothing
like that. It is an ordinary read file, and **HO already loops it over every outdated player with no
limit at all**, in both `XMLParser.updateTeamTransfers` and `TransfersPanel`. There is no rate
limiting anywhere in `core.net`.

At 20 per download against 743 expired rows that meant ~37 downloads to clear the backlog, and the
corpus was still growing — `basis` would have stayed `observed_listings` for over a month for no
good reason.

The real constraint is that a download runs on the event dispatch thread, so every lookup is time
the UI is frozen. A count cannot express that: the same 200 calls are half a minute on a fast link
and five on a bad one. So the pass now stops on **either** bound —

- `mcpResolveCap` default raised 20 → 200, still user-visible in Options → MCP;
- a 60-second wall-clock budget, which is what actually protects the UI.

The current backlog clears in about four downloads instead of thirty-seven, and a slow connection
degrades into "fewer per download" rather than a frozen window.

**Verification:** full suite green — 700 tests, 60 suites, 0 failures. `ScoutTableTest` round-trips
both nullable columns, unset and set, and checks `duplicate()` carries them (it feeds the table model
and the details panel). `ScoutListTest` covers the window guard, including the hand-typed-entry case
it exists for.

## One vote per player, and no skills for a player long sold (2026-09-05)

Two corrections to the valuation, from a peer session reading `sampleSize: 57` as 57 comparable
players while shortlisting a real signing. It was 57 **rows**.

- **`estimate_market_value` deduped by player id.** The corpus appends on every observation, so a
  player who turned up in four searches held four rows and moved the median four times. The bias is
  systematic, not random: the more often a profile is re-searched, the more the players who sat on
  the market longest outvote everyone else. `TransferTools.betterObservation` keeps the row that
  carries a resolved sale price, else the newest observation, and the list is handed on
  newest-first, so the `comparables` evidence array is one row per player too. `comparablesMatched`
  now counts distinct players — the number a caller was always reading it as — and a new
  `observationsMatched` says how many rows they came from. **Expect `sampleSize` to drop; that is
  the fix working.** `MIN_RESOLVED_SAMPLE` (3) now means three distinct sales, which is what it
  should always have meant.
- **`fillMissingSkills` stops after `SKILL_FILL_GRACE` (14 days past the deadline).** The pass fills
  a skill-less row with one `playerdetails` call, but for the expired backlog that call happens long
  after the player changed clubs — it records today's trained player against a months-old listing,
  which then votes in a valuation under skills he did not have when he sold. A NULL-skill row cannot
  match `loadComparables`, so leaving it empty keeps it out of the corpus rather than putting a lie
  in it. Live and recently-expired listings are unaffected, which is the case the fill exists for.

### What the corpus actually holds (measured 2026-09-05)

Read off a read-only copy of the live database, since both changes above were argued from guesses
about its shape:

| | |
|---|---|
| rows / distinct players | 1306 / **778** |
| rows with no skills | 2 |
| players with no skills on any row (`fillMissingSkills`'s target) | 1 |
| unresolved, deadline passed | 695 rows / 475 players |
| resolved sale prices | 14 rows / 11 players |
| scout entries | 15 |

Two things follow. **The corpus is ~40% smaller than its row count** — 1306 observations of 778
players — which is the dedupe's effect in aggregate and the reason `sampleSize` was never the number
it read as. And **`SKILL_FILL_GRACE` strands nothing today**: two skill-less rows exist in the whole
corpus, the one player who lacks skills everywhere has a deadline inside the window, and the oldest
deadline in the entire backlog is five days old. The guard protects a future shape — an HO left
dormant while its expired rows age past a fortnight — not a present case.

With 11 players carrying a sale price, `basis: resolved_sales` (three distinct sales matching one
profile) is still far off for essentially every profile. `observed_listings` and its upper-bound
caveat are the normal answer for now, and that is drainage rate rather than a stall.

### Not done: merging SCOUT and TRANSFERLISTING

Proposed by the assistant session, declined after a design sync, on Rainer's call. The two tables
look alike and are not: `TRANSFERLISTING` has `idColumns = 2` — `(PLAYERID, OBSERVEDAT)`, append-only,
nearly everything nullable — and that time series is exactly what a valuation needs. `SCOUT` is keyed
on player id, replaces on add, is almost entirely `NOT NULL`, and carries `Info`, `Wecker`,
`Agreeability`, `Loyalty`, `MotherClub`, `Nationality` and `baseWage`, which the corpus has no source
for — a merge would have to invent columns it cannot populate.

The decisive one is `ScoutTable.saveScoutList`: `executePreparedDelete()` then store-each, driven
from `TransferScoutPanel.saveScoutListe()` via `HOMainFrame.shutdown()`. Back the F7 tab with a
1200-row table and one clean exit deletes every row the panel's `Vector` does not hold. Making it
safe means rewriting the panel's persistence model plus every `ScoutEintrag` consumer
(`ScoutThread`, `TransferScoutingTableModel`, the hand-entry panel, saved column layouts) against an
append-only key — a day-plus of Swing work with no test harness, for something no user sees, since
`TRANSFERLISTING` has no UI at all.

If it is ever revisited, the shape that works: physical survivor is `TRANSFERLISTING` (renaming it to
`SCOUT` collides with the existing table mid-migration), gaining `WATCHED BOOLEAN DEFAULT FALSE`,
`INFO`, `WECKER` and the nullable SCOUT-only columns; `DBVersion` 1003 → 1004 inserts the SCOUT rows
as observations with `OBSERVEDAT = Deadline` and `WATCHED = true`, then drops `SCOUT`; the panel
loads `WHERE WATCHED = TRUE` and writes per row; the resolver's two-pass
`scoutCandidates` / `loadUnresolved` split collapses into `ORDER BY WATCHED DESC, OBSERVEDAT ASC`.
The cost hinges on whether the hand-entry panel is used: if every entry arrives through
`add_to_scout`, the panel becomes read-only and the shutdown save is deleted rather than rewritten.

**Verification:** full suite green — 701 tests, 60 suites, 0 failures. `TransferToolsTest` pins the
merge rule (a resolved sale beats a newer asking price; otherwise newest wins). The dedupe itself is
a `Collectors.toMap` over it, and `fillMissingSkills`'s guard rides the existing untested CHPP path.

## Set pieces is not a positional skill (2026-09-05)

`comparableQuery` ranks `POSITIONAL_SKILLS` by level and takes the top two, and set pieces was in
that list. It is a specialist skill that says nothing about the position a player is bought for, and
it is frequently a player's accidental second-highest — so a scoring-14 / set-pieces-11 striker was
matched against set-piece takers rather than against strikers, and for a low-skilled player it could
even have taken the *primary* slot. Removed from the list; the six real positional skills stay.

This narrows and re-aims both `find_comparables` (the live CHPP query) and `estimate_market_value`
(the same windows against the corpus), since they share the one query builder. `column()` still maps
SETPIECES, and `minLevel`'s level-5 floor for it stays — both are cheap and neither is now reachable
from this path.

## Three long-standing side bugs (2026-09-05)

All three had been found in earlier passes and recorded as "not fixed". Fixed now, in one commit
separate from the valuation work, because the last of them changes numbers HO already shows.

- **`XMLMatchOrderParser`'s forward guards were always true.** The three forward cases tested
  `map.containsKey("RightForward")` while writing `"RightForwardID"`, so the key looked for never
  existed, the first player always won the slot and `addAdditionalPlayer` was dead code for forwards
  — alone among the roles that guard at all. Now `"...ForwardID"`, matching the winger and inner
  midfield cases.
- **`Lineup.toJson()` serialises positions positionally.** Each entry carries only the player id and
  the behaviour (`MatchRoleID`'s two `@Expose` fields), so Hattrick derives the slot from the array
  index: the order *is* the contract. Every builder happens to fill the vectors in ascending role id
  and nothing enforced it. `toJson` now sorts both the field and bench vectors by role id — a no-op
  today, and a future builder that is not ascending can no longer silently send a scrambled lineup.
- **`CalcPlayerBaseProvider`'s age bands were unreachable.** All eight `setAs*` methods cascaded
  `age < 22 / age >= 22 / age >= 27 / age >= 29` (25/28 for the goalkeeper), so the second branch
  swallowed every age above 21 and the last two never ran: every senior player was estimated on the
  22-year-old base table. The chains are now upper bounds — `< 22 / < 27 / < 29 / else` outfield,
  `< 22 / < 25 / < 28 / else` in goal.

  **This changes numbers HO already shows.** A 27-or-older opponent player now estimates higher in
  opponent-spy, in `SpecialEventsPredictionManager`, and in `get_opponent_squad`'s `estimatedSkills`
  — which is what the tables were written to say. Nothing else in the repo depended on the flat
  behaviour.

**Verification:** full suite green — 703 tests, 61 suites, 0 failures. The new
`CalcPlayerBaseProviderTest` walks one outfield chain and the goalkeeper's (which has its own
thresholds) across all four bands, so a re-introduced lower-bound cascade fails immediately. The
parser guard and the `toJson` sort have no test: the first needs a match-order XML fixture and the
second is a no-op against every lineup the repo can build, which is exactly the point of it.

## estimate_market_value returns three skills a row (2026-09-05)

The tool hands a model its subject plus up to forty comparables, and every one of them carried all
seven skill levels - close to three hundred numbers whose only job was to be ignored. The fourth
skill down has never decided a transfer price, and the reader has no way of knowing that, so it
weighs them all. Each row now keeps its three best positional skills under `topSkills` and nothing
else; wage, TSI, form, specialty and the prices are untouched, since those are what the judgement
actually rests on.

Set pieces is out for the reason `comparableQuery` already excludes it: a specialist skill, and a
frequent accidental second-highest. `topSkillsOnly` strips it by name, since neither skill map ranks
it.

`McpJson.listing` now writes the skills under `get_player`'s names (`defending`, `playmaking`,
`scoring`) rather than the CHPP's own (`defender`, `playmaker`, `scorer`), so the subject and the
comparables read side by side. It has exactly one caller, so nothing else moved.

**Verification:** full suite green - 705 tests, 0 failures. Two new `TransferToolsTest` cases pin the
trim: one over a squad player (skills nested in `skills`/`skillsWithSublevel`), one over a listing
(flat keys, nullable skills, set pieces highest but dropped).

## …except set pieces, which comes back (2026-09-05)

Reversing the paragraph above. Lumping set pieces in with "the fourth skill down" was the wrong
call: those two are excluded for different reasons and only one of the reasons survives scrutiny. A
fourth positional skill is largely redundant - the top three plus TSI and wage already say it. Set
pieces is orthogonal to all of that. A buyer wants a set piece taker whatever position the player
fills, so it can move a price on its own, and nothing else in the row exposed it. The tool was
deciding a priori that it does not matter while telling the model, in `estimateNote`, that it was
not there to look at.

It is back as a flat field beside `topSkills`, not inside it - a set piece specialist must not lose
a positional skill off his top three to it - and still out of `comparableQuery`'s matching. So the
comparables spread freely across it, which is the point: it is evidence for reading a row that
priced away from the rest, the same standing wage and form already have. The tool description now
says so, including that it is null on an observation Hattrick sent no `details` for.

`topSkillsOnly` lifts it out by name before dropping the skill blocks and puts it back after, which
covers both serialisations in one path (a player nests it in `skills`, a listing writes it flat).
`JsonNull` is re-added rather than skipped - an absent key reads as trimmed away, a null as unknown.

Cost is one integer per row against forty rows. Nothing else in the pipeline moved: the value was
already parsed (`XMLTransferSearchParser:99`), already stored (`TransferListingTable`), already
serialised (`McpJson.listing`) - `topSkillsOnly` was deleting it on the way out.

**Verification:** full suite green - 705 tests, 0 failures. The two `TransferToolsTest` cases from
the previous entry now assert the opposite for set pieces: 20 on the squad player survives into
`setPieces` and stays out of `topSkills`, 13 on the listing survives the flat-key trim.

## `get_stadium` — the arena sizer over MCP (2026-09-05)

`get_finances` already carried the stands, and it kept getting asked a question it could not answer:
how big *should* the stadium be. The maths for that had been sitting in `tool/arenasizer` since long
before the MCP server — `ArenaSizer.calcConstructionArenas` (Hattrick's 60 / 23.5 / 14 / 2.5 percent
split across terraces, basic seating, roof and VIP), `ArenaRebuild` (per-seat build cost plus the
flat 100 000 fee, and demolition credit for shrinking), `ArenaMaintenance` and `ArenaAdmission` —
reachable only from the Swing arena tool.

`get_stadium` (`TeamTools`, tool 33) exposes it. It reuses the existing `stadium()` block verbatim
so the stands, expansion in progress, recent home attendance and utilisation read the same as under
`get_finances`, then adds the current arena's weekly maintenance and its sold-out gate receipts, and
finally three sized arenas from the fan club: pessimistic, normal, optimistic (15 / 20 / 25
spectators per supporter, the sizer's own constants). Each plan carries the sectors it would have,
the delta to build per sector, the rebuild cost, and its own maintenance and sold-out gate — an
expansion is a running-cost decision, not just a build-cost one. `supporters` overrides the fan
club, `capacity` prices one specific target size instead of the three scenarios (via the sizer's
three-size overload, asked for the same number thrice).

Two things the sizer does that surprise on first read and are now pinned by tests: the sector splits
truncate, so a "12 500" arena is 12 499 seats; and a plan with nothing to build costs zero, not the
100 000 fixed fee — `ArenaRebuild.calculateCosts` short-circuits on all-zero deltas.

**Verification:** `./gradlew test --tests "core.mcp.*"` green, 92 tests. Unlike the other tool tests
this one is not surface-only — `TeamTools.plan` is pure, so the two new cases assert real sector
deltas and real money against hand-computed krona.

**Not shipped:** source only. The running server is the installed bundle, so this is invisible to
`~/hattrick` until `./gradlew dmg` and a relaunch.

## `download_team` — refreshing HO from the agent (2026-09-05)

Every read tool answers from `DBManager`/`HOVerwaltung`, so after the user fired a player in
Hattrick `get_squad` kept returning the old squad until someone clicked Download in HO. `download_team`
is that click, over MCP.

`OnlineWorker.getHrf` was split. The download — `ConvertXml2Hrf.createHrf` → `HRFStringParser.parse`
→ `storeModel` — is now `OnlineWorker.downloadTeamData()`, which shows nothing itself and throws a
typed `DownloadFailed(Failure reason, …)`. `getHrf` keeps its signature and is now only the five
dialogs (`report()`) plus `saveHRFToFile`. Both had exactly one caller each, so the blast radius was
two files.

Five things worth knowing next time:

- **"Silent" is three separate problems, not one.** `downloadTeamData()` showing nothing is the easy
  part. Underneath it, `Connector.getCHPPWebFile` opens `OAuthDialog` on an HTTP 401 (`:740`) and a
  `JOptionPane` on every other failure (`:765`), both gated only on `Connector.silentDownload`, and
  one download makes roughly a dozen requests through that method. `ConvertXml2Hrf.createHrf():79`
  opens a modal `TeamSelectionDialog` when HO holds no team id and the account has several teams,
  and `silentDownload` does *not* gate that one. `DBManager:150` shows a "Fatal DB Error" box.
  `download_team` wraps its body in `Connector.setSilentDownload(true)` (save/restore, like
  `ChppWriteService` and `TransferSearchService`), refuses up front when `getTeamId() <= 0`, and
  leaves the DB box alone on purpose — HO is dead at that point anyway and hiding a corrupt database
  from the user would be worse. The flag is set by the *tool*, not by `downloadTeamData()`: HO's
  Download button still needs those dialogs.
- **Suppressing the dialog costs you the error.** With the flag on, a CHPP failure makes
  `getCHPPWebFile` return `""`, `createHrf` bails at `teamDetails.isEmpty()`, and the result is the
  same null it returns when no team is selected — so an expired OAuth token reported as `NO_TEAM`,
  "no team is selected in HO", which the user cannot act on. `Connector.lastError()` is the way out:
  cleared at the start of every request, left set only by a failed one, so a non-null value at the
  null-hrf branch means Hattrick was the reason. That disambiguation lives in `TeamTools.explain()`
  and deliberately not in `downloadTeamData()`, where it would make the Download button pop an error
  dialog it currently does not.
- **The save chooser cannot be switched off.** `saveHRFToFile` opens it when the target file already
  exists, and the name is `<teamId>-YYYY-MM-DD.hrf` — one per calendar day. So the second download on
  any day opens it regardless of `showHRFSaveDialog`. Neutralising the flags is not a fix; the tool
  must not enter `saveHRFToFile` at all, and it doesn't.
- **`getHrf` returns `true` on three failures where nothing was stored** (`PLAYING_MATCH`, `NO_DATA`,
  `UNPARSEABLE`), and `DownloadDialog` then carries on downloading matches and prices. That looks
  wrong and was preserved deliberately — the refactor's contract was "the button behaves
  identically". The MCP tool does not inherit it: all five reasons are errors there.
- **A download overwrites HO's stored lineup** with the match order Hattrick currently holds
  (`HRFStringParser:177` → `HOModel.storeLineup`). Three call sites write that lineup and none records
  provenance, so there is no dirty flag and "refuse when unsaved" is not implementable. The tool
  snapshots the lineup either side of the download and reports `lineupReplaced` / `previousLineup`
  instead. Two traps there: the snapshot is taken with a null rating model, because ratings move on
  nearly every download and would make the flag always true (and computing them twice on the EDT is
  not free); and the comparison is only meaningful when `becameCurrent` is true, because otherwise
  `HOVerwaltung` still holds the previous model with the user's in-memory lineup and both snapshots
  come off the same object. In that case `lineupReplaced` is `null`, not `false` — the stored lineup
  *was* overwritten, HO just cannot see it.

Capped by the new `UserParameter.mcpDownloadsPerSession` (default 10 at the time, half the other two
caps because one download was roughly a dozen CHPP requests; now 5 — see the next section). The
counter increments only after a download
succeeded, so a Hattrick outage cannot eat the whole session's allowance. No `DBVersion` bump —
`UserParameter` persists through a plain string map.

Not refreshed, at first: matches, fixtures, played league results, hall of fame, youth data. That
lasted one day — see the next section.

## `download_team` reaches parity with the Download button (2026-09-05)

The user's position, and it is the right one: a "download" should bring everything down. The curated
subset above is gone; the tool now runs what `DownloadDialog.startDownload` runs.

**The dialog was taken apart, not copied.** Five blocks moved to `OnlineWorker` —
`downloadNtMatchesOfCappedPlayers`, `downloadHallOfFameIfActive`, `downloadCurrentMatches`,
`downloadCurrentFixtures`, `refreshWorldDetailsIfDue` — and `startDownload` now calls them in the
order it used to inline them. Copying 150 lines into the tool would have guaranteed drift the first
time someone touched the dialog. Behaviour was preserved exactly, including the two places it looks
wrong: the youth block still runs even when the match list failed, and `bOK` still latches the same
way.

**The filter settings are readable without the dialog — except where they aren't.** Eight of the
dialog's checkboxes round-trip through `UserParameter` (`downloadCurrentMatchlist`,
`downloadSingleMatches`, `downloadLadderMatches`, `downloadTournamentGroupMatches`,
`downloadTournamentPlayoffMatches`, `downloadDivisionBattleMatches`, `xmlDownload`, `fixtures`), so
`download_team` honours what the user last ticked. The parent "Current Matches" node has no flag of
its own — `CheckBoxTree.updatePredecessorsWithCheckMode` marks a parent selected when *any*
descendant is, so it is exactly the OR of the six match-type flags, which is what
`OnlineWorker.anyCurrentMatchTypeSelected()` computes. That is what the comment in `startDownload`
means by "checked, if any of the following is checked".

Two sections cannot have parity, and saying so is better than working around it:

- **The match archive** is a checkbox plus a date spinner, neither persisted, both starting off in a
  fresh dialog. So "whatever the user last ticked" is genuinely "off", and that is what the tool does.
- **Previous-season fixtures** are tree nodes that also start unchecked — *and* `downloadOldFixtures`
  opens a modal `LigaAuswahlDialog` per season, so it could not run silently even if they were
  ticked. Not called at all.

Both are named in the tool description and in a `notRefreshed` field on the result, so a caller does
not have to infer them from silence.

**`Helper.showMessage` had to learn about silent downloads.** This was the real blocker. `Connector`
gates its own two dialogs on `silentDownload`, but the ~11 error dialogs `OnlineWorker` raises around
matches, fixtures and lineups go through `Helper.showMessage`, which checked nothing — only
`downloadMatchData:452` bothered to test the flag itself. A modal `JOptionPane` from a silent
download blocks the EDT and the MCP server's single handler thread together, with nobody able to
dismiss it. One guard in `Helper.showMessage` covers every one of them, logs at warning instead, and
fixes the paths nothing was going to touch otherwise.

**Team transfers are still not part of button parity — so they were added separately.** The dialog
never calls `XMLParser.updateTeamTransfers`; its only callers are `TransfersPanel.refresh():55` and
`HistoryPane:40`. And `TransfersPanel.refresh()` guards that call on
`DBManager.getTransfers(0,true,true).isEmpty()`, so it fires once on a database that holds no
transfers at all and can never fire again. That is why a player bought since the last download showed
up in `get_squad` and nowhere else — the symptom that started all of this. It is an HO gap, not an
MCP one.

`updateTeamTransfersIncremental` is the bounded version. `updateTeamTransfers` re-pages the entire
lifetime history every call and then makes one `transfersPlayer` request per player it saw — for a
few hundred transfers that is a dozen pages plus twenty-odd lookups, every download. The incremental
one stops at the first page holding nothing new, and looks up only the players whose transfers were
new: one page and no lookups in the steady state.

Two traps in that loop, both covered by `XMLParserTest`:

- The stop test runs over the **unfiltered** page. A transfer outside the stored date window is never
  written, so it stays unknown for ever; testing the date-filtered list would make such a page look
  permanently new and the loop would never terminate. Testing the whole page costs one extra page
  fetch in that case and always terminates.
- Pages are newest-first, so "everything on this page is stored" really does imply everything older
  is too. A partly-new page is still followed by the next one, because one page can straddle the
  boundary.

**`lineupReplaced` is unaffected, and that was checked rather than assumed.** The worry was that
parity pulls in the match side. It does not touch the lineup: `getAllLineups(10)` stores *match*
lineups in `MATCHLINEUP`, and `startDownload` never downloads a next match order at all. The only
writer of HO's stored lineup is still `HRFStringParser:177` → `HOModel.storeLineup`, inside
`downloadTeamData`. So the snapshot pair was moved to bracket *only* that call, rather than the whole
widened tool — bracketing everything would compare the same two things across a much longer window
and would start reporting an unrelated EDT edit as a replacement.

**Costs, at the new size — counted, not estimated.** The unit that matters is not the section, it is
the *match*: `downloadMatchData` on one newly finished match is about six CHPP requests on its own —
`matchdetails` to identify it, `teamdetails` for the opponent, `matchlineup`, two more `teamdetails`
for each side's ratings (`downloadTeamRatings`), then `matchdetails` again for the highlights. So the
cost is dominated by how many matches were played since the previous download:

| | requests |
|---|---|
| `createHrf` (teamdetails, club, leaguedetails, worlddetails, players, avatars, youth players, economy, training, staff, arena, matches, next match order, last lineup) | 13–15 |
| hall of fame, current match list, league fixtures, transfer pages | ~4 |
| upcoming matches with an unsure weather forecast, re-fetched every download | 2–3 each |
| **each newly played match** (ours, and each other-team league match that just got a result) | **~6 each** |
| youth match backfill, world details, sale-price resolve | 0 in the steady state |

A quiet download is therefore ~20–25 requests. The first download after a league match day is ~50–70:
our own match plus the three other matches in an eight-team series, at six apiece. After a long gap it
is far worse — the youth backfill walks 90-day windows and `getAllLineups(10)` fetches whatever is
missing. The earlier "30–50 flat" figure in this document was an estimate made before the per-match
cost was counted; it was low for the match-day case.
`mcpDownloadsPerSession` therefore dropped from 10 to 5: the same allowance measured in requests
rather than in calls. `mcpResolveCap` (200) and the resolver's 60s `TIME_BUDGET` are unchanged, but
they now always run rather than only on `resolvePrices: true` — which is the point, since that pass
is the scout list's half of a refresh. `resolvePrices` is gone as a parameter.

## Two things the first live parity download exposed (2026-09-05)

### `TRANSFER` has three writers in one download, not one

`newTransfers` reported 0 on a download after which `get_team_transfers` plainly showed a new row.
The counter was not wrong about what it measured — it was measuring the wrong third of the problem.
One download writes `TRANSFER` from three places:

1. `ConvertXml2Hrf.createHrf` → `areTransfersMissing(economyDataMap)` → `PlayerTransfer.downloadMissingTransfers`.
   This is long-standing HO code inside `downloadTeamData`, and it is the one that actually catches a
   recent purchase: it compares the economy's `CostsBoughtPlayers`/`IncomeSoldPlayers` against the
   stored rows for the current and previous week and pages `transfersTeam` until they reconcile.
2. `updateTeamTransfers(fresh)` — the incremental pass storing the pages it fetched.
3. `updatePlayerTransfers(playerId)` — each affected player's full history, after (2).

So in the normal case (1) has already stored the purchase by the time (3) runs, the incremental pass
correctly finds nothing new, and `fresh.size()` is honestly 0. Reporting that number as
`newTransfers` was the bug. It is now the delta of `getTransfers(0,true,true).size()` across the whole
call, so it counts all three; the incremental pass's own contribution goes to the log.

This also corrects the earlier claim in this document that the download never refreshes transfers.
`updateTeamTransfers` is indeed never called from the dialog — but `downloadMissingTransfers` is, via
`createHrf`, on every download including the old binary's. It is narrow (two weeks, economy-driven,
skipped on an empty database), which is why the incremental pass still earns its place, but "never"
was wrong.

### `lineupReplaced` was comparing construction paths, not lineups

The flag came back `true` on the first download after an HO restart with a `previousLineup`
byte-identical to an earlier run's — the classic shape of a false positive. The cause is that the two
snapshots were being read from objects built two different ways:

- after a download, the live model's lineup is the object `HRFStringParser` just parsed;
- after a restart, it is `HOModel(int id)` calling `DBManager.loadNextMatchLineup(teamId)` — a
  database round trip.

`before` on the first download after a restart is therefore a round-tripped object while `after` is a
freshly parsed one, and any field the round trip does not preserve exactly renders as a difference in
`LineupTools.describe`. That makes the flag fire on the first download of every session regardless of
whether anything was lost — and it was not caused by narrowing the bracket, it predates it (the
same-session second download compared parsed-against-parsed and correctly said `false`).

`lineupSnapshot()` now reads `loadNextMatchLineup` for *both* snapshots. Representation cancels, and a
difference means the stored row genuinely changed. Two consequences worth knowing:

- The `becameCurrent` guard is gone. It existed because the live model keeps the old lineup when the
  swap does not happen; reading the stored row sidesteps that entirely, and `storeModel` writes the
  row either way. The old `null` answer — "HO cannot tell" — now only means "nothing was stored to
  compare against", i.e. a fresh install.
- The flag now reports what is in the database, which after a skipped swap is not what HO's window is
  showing. That is the right thing for a field whose whole subject is the *stored* lineup.

## Every transfer search asks for 100 hits (2026-09-05)

`pageSize` was 50 in three independent places — `TransferSearchQuery.DEFAULT_PAGE_SIZE`, a hardcoded
`.pageSize(50)` in `TransferSearchPanel.buildQuery`, and `TransferTools.DEFAULT_PAGE_SIZE`. Nothing
anywhere pages: the UI and the MCP tool both read page 0 and stop, so a query with 80 matches showed
50 and silently dropped the rest. Since nobody asks for a second page, a half-size first page is just
lost candidates.

The CHPP maximum is 100, so that is now the single default in `TransferSearchQuery`; the panel no
longer sets a page size at all, and `TransferTools` uses `MAX_PAGE_SIZE` for both its default and its
clamp (`transfer_search`'s `pageSize` argument still overrides it downwards). Paging is still not
built — a search that hits exactly 100 is still capped, and `resultText` shows `100 / <itemCount>` so
it is visible when that happens.


## Character over MCP, and the swap it uncovered (2026-09-05)

Asked from the strategy repo whether HO can screen on aggressiveness, honesty and agreeability.
It can, for the squad, and always could — nothing new had to be downloaded or migrated.

CHPP `players` carries `<Agreeability>`, `<Aggressiveness>` and `<Honesty>`;
`XMLPlayersParser.createPlayer` reads all three, `HRFStringBuilder` writes them into the HRF, and
they land in PLAYER as `iCharakter` / `iAgressivitaet` / `iAnsehen`. One row per HRF, so there is
history. `McpJson.player()` now reports them as `agreeability` / `aggressiveness` / `honesty`,
0–5 each, which puts them on `get_player` and `get_squad`.

**The German names are crossed and the getters are the only reliable guide.** `iCharakter` holds
agreeability (`getGentleness()`); `iAnsehen` holds honesty (`getHonesty()`). Three independent
readers agree — `HRFStringBuilder:492-497` pairs `gentleness` with `PlayerAgreeability`, and so do
`CsvPlayerExport:149-151` and `SpielerDetailDialog:402-406`.

`parseMarketPlayerFromString` had transposed the two: `setHonesty("Agreeability")` and
`setGentleness("Honesty")`. Nothing consumed those fields off a `MarketPlayer` yet, so no stored
data was wrong, but `TransferListing.fromPlayerDetails` would have inherited it the moment character
was added to the corpus. Fixed, with a test over both fixtures (3 / 2 / 4 are distinct on purpose, so
a swap cannot pass).

Also settled while looking: **character survives the unlisted pruning.** It sits at `<Player>` root,
outside `<PlayerSkills>`, and both live responses behind the `LISTED`/`UNLISTED` fixtures carry it.
So §1.3's pruning applies to the seven hidden skills and `TransferDetails` only.

### Character on the scout list (DB 1004)

`transfersearch` genuinely does not carry character — the 1.1 `<Details>` block is age/salary/TSI/
form/experience/leadership/specialty/cards/injury plus the eight skills, full stop. So the
TRANSFERLISTING snapshot an entry is built from cannot supply it, and `playerdetails` is the only
source. SCOUT gained `Aggressiveness` and `Honesty` (it has had an `Agreeability` column since
forever, with nothing ever writing it), and `list_scout` reports all three.

**Two writers, because one of them is too late on its own.** `TransferListingResolver`'s scout pass
already calls `playerdetails` on every candidate, so character rides along free — but
`scoutCandidates` only picks entries whose **deadline has already passed**. A freshly added,
currently-listed entry — exactly the one a buy decision is about — is not a candidate and would wait
out the whole listing. So `add_to_scout` fetches it itself, one call per player, best effort: the add
succeeds either way and leaves the three null on failure. The resolver's pass is then the retroactive
fill for entries added before this existed.

**Null, not 0.** 0 is a real level in all three scales ("tranquil", "nasty fellow", "infamous"), so it
cannot double as "nobody has looked". The two new columns are nullable; `Agreeability` is not, and
every pre-existing row holds its 0 default. `McpJson.scoutEntry` therefore gates all three on
`honesty != null` — honesty is written only by this fetch, so it is what distinguishes an observation
from the old default.

Not in the UI, deliberately: nothing outside `ScoutTable` reads these getters, so no scout table
column appears and `updateDBv1004` needs no USERCOLUMNS reset (unlike 1003, which did).

### Character in the valuation corpus too (DB 1004)

TRANSFERLISTING gained `AGREEABILITY`, `AGGRESSIVENESS` and `HONESTY`, nullable for the same reason
the SCOUT ones are. Only `fromPlayerDetails` can fill them: `fromHit` and `fromBidItem` build rows
from files that carry no character at all, so those stay null, and today that is most of the corpus.
`McpJson.listing` reports all three, which puts them on `estimate_market_value`'s evidence rows —
`topSkillsOnly` trims positional skills, not these.

They say nothing about price, and are not meant to: a comparable's character does not move what he
sold for. They are there so a row that is also a player you are looking at answers "would I want
him", and so the corpus can be asked later whether character moves a price at all.

**`add_to_scout` now stores a corpus row from the call it was already making.** It fetches
`playerdetails` for character; that response is also a fresh, character-bearing market observation of
a player who is listed right now, which is exactly what `observe()` stores in the resolver. Throwing
it away would have left the new columns almost always null. Guarded on `transferListed`, because
`fromPlayerDetails` refuses an unlisted response (§1.3's pruning).

Still not done: nothing *values* a player on character. The data is now recorded so that question can
be asked of real history later rather than guessed at now.
asked of real history later rather than guessed at now.

## The scout pass starved everything past its first few entries (2026-09-05)

A player sold at 21:37 and two downloads afterwards, at 21:55 and 21:57, left his SCOUT and
TRANSFERLISTING rows with `SALEPRICE` null. Reported as "the download does not reliably update the
transfer listing and the scout entries", and reliability was exactly the shape of it.

The log settles it without any guessing. Each run made precisely 20 CHPP calls in the resolver — five
`playerdetails`+`transfersPlayer` pairs for scout candidates, then ten `transfersPlayer` for the
corpus — and `513158949` appears in no HO log, ever. The stored `mcpResolveCap` is **20**, not the 200
this document had been assuming: the default was raised after this installation had already persisted
the old value, and `UserParameter` reads the stored one. So `scoutCap = cap / 2` is 10, two calls a
candidate, five entries a download.

Which five was the bug. `scoutCandidates` walked `ScoutList.entries()` in list order and stopped when
the budget ran out, so it re-asked about whichever entries sat near the head — four of them expired
hours earlier, already asked about on previous downloads, and never going to sell — while the player
who had actually just sold sat at position 85 of 92 and was never reached. Not a race, not a CHPP
lag: he was candidate number six against five slots, on every download, indefinitely.

### Two questions, two selection rules

Ordering the candidate list newest-expiry-first was the first fix and it is not enough, because the
cap stays the ceiling: the pass is still sampling a list when what one of its two questions needs is
completeness. They are not the same kind of question.

| | asked | needs |
|---|---|---|
| **the sale** — "what did he go for?" | once, and only just after the deadline | every newly-expired entry, every run |
| **the re-listing** — "is he back?" | over and over, for `SCOUT_WATCH_GRACE` | fair rotation, best effort |

So `resolve` now runs them as two passes over one `ScoutList.entries()` read, sharing the `looked`
set so an entry in both still costs one `playerdetails` call.

**Pass one, the sale, is scoped by a watermark instead of by the cap.** `salesToAskAbout` takes only
entries whose deadline falls between `mcpScoutResolvedThrough` and now — the handful that expired
since the last download, not the whole list. It is ordered *oldest* first so the watermark can be a
resume point: the loop parks it on the first candidate it cannot afford and everything before that is
done. The window is inclusive of the watermark, so that candidate comes back next run; two entries
sharing a deadline cost one repeat call, which is the cheap side of the mistake. A `transfersPlayer`
that throws also stops the pass and parks the watermark, because a network blip must not silently
consume the one moment a sale was askable — hence `Outcome.failed`, which is not `!resolved`: "he did
not sell" is an answer, "the call threw" is not.

**Pass two, the watch, keeps the rotating cap-bounded walk**, newest expiry first, and asks nothing
about sales — pass one has already done that, once, for every entry it can see. It is the pass that
degrades under a tight cap, which is the right one to degrade: "is he back on the market" has no
single moment to miss.

`scoutAction` and its `ScoutAction` record are gone. The branch table existed because one pass had to
decide, per candidate, which of the two questions it was still inside; with the questions split the
answer is structural — pass one always asks both, pass two only ever refreshes.

### The watermark

`UserParameter.mcpScoutResolvedThrough`, a CHPP-format timestamp in the existing USERCONFIGURATION
key/value table — no migration, same shape as `mcpResolveCap`. Written straight through with
`DBManager.saveUserParameter` rather than left to `HOMainFrame.shutdown()`: a download can come from
the MCP server with no window to close, and a watermark lost to a crash means every sale in the
window is asked about twice.

It is deliberately *not* the previous HRF row, which was the other candidate and is free — one HRF is
written per download, timestamped to the second, and `loadLatestHRFDownloadedBefore` already exists.
But that timestamp means "last download", not "last resolve": a run cut short by the cap would
advance it anyway and lose the entries it never reached, and `DBCleanupTool` pruning HRFs would widen
the window unpredictably. The watermark has to be owned by the pass that moves it.

`SCOUT_RETRY_GRACE` survives, repurposed as the floor on how far back a stale watermark may reach:
without it the first run after a fortnight away would ask about every entry that expired in the
meantime. Missing watermark means "never run", which the floor turns into the last fortnight.

### Still worth knowing

The cap is the user's to set and 20 is tight for a 92-entry list — it now buys *all* the newly-expired
sales plus whatever watch and corpus rows fit in what is left, but a long absence still works off its
backlog over several downloads. Options → MCP → resolve cap.

**Not done:** a scout entry still has no per-row `LOOKEDAT`, so pass two restarts from the head of
its (newest-first) ordering each run rather than rotating on last-observed. Bounded and best-effort
by design; a column would make it fair. Worth doing if the list keeps growing.


## The MCP tool surface, cut from 34 to 30 (2026-09-06)

An audit from the peer agent that drives the server, measured against a live `tools/list`: 34 tools
were **33,781 chars — roughly 8.4k tokens in every session's context before a single call is made**,
split 17,424 chars of descriptions and 16,357 of schemas. Five tools were redundant or broken, one
was missing. Descriptions are now ~7.9k chars over 30 tools.

### Five tools removed

- **`predict_match`** — fully subsumed. Its schema was `opponentProperties(new JsonObject())`;
  `simulate_lineup`'s is `opponentProperties(lineupSchema().properties)` with the same `required`.
  A strict subset, and `simulate_lineup`'s own description already said "omit positions and it falls
  back to the current lineup, exactly like predict_match".
- **`evaluate_lineup`** — same story on the other half: `lineupSchema()` is a strict subset of
  `simulate_lineup`'s properties.
- **`optimize_lineup`** — **deleted, not fixed.** `{"sectorsStrengthPriority":0}` had been returning
  `-32603 Internal error` since 2026-08-31. Root cause is
  `LineupAssistant.isSelectedByAssistant` (`LineupAssistant.java:441`):

  ```java
  var statusMap = Objects.requireNonNull(HOMainFrame.instance().getLineupPanel())
          .getAssistantPositionsStatus();
  ```

  It is the one tool that reaches HO's lineup assistant, and it does so off the EDT with no null
  guard. The note further up this file already recorded that fixing it means rewriting
  `LineupAssistant`; nothing has changed about that, and there is no cheaper fix hiding behind it.
  There is now **no baseline-builder tool** — a caller loops `simulate_lineup` instead.
- **`find_comparables`** — became `estimate_market_value`'s `live: true`. Same three arguments, same
  `comparableQuery` matching; the only difference was corpus versus a live `transfersearch`. As a
  flag it is strictly better than the tool was: `search()` writes every hit into `TRANSFERLISTING`
  *before* the corpus read, so one call now returns today's market **and** a median that already
  counts it. Two tools could not do that — `find_comparables` left the estimate untouched.
- **`get_stadium`** — it and `get_finances` shipped a byte-identical stadium block (all nine keys,
  `recentHomeAttendance[]` included). The arena sizer is now the opt-in half of `get_finances`:
  `sizeArena`, plus `supporters` / `capacity` as overrides, either of which implies it. Off by
  default, because the sizer is three more objects of arithmetic nobody asked for when the question
  was "how much cash do we have".

### `simulate_lineup` is now the only lineup-scoring tool

`required` is empty and both halves are optional:

| positions | opponent | result |
|---|---|---|
| omitted | omitted | HO's current lineup, sector ratings + formation |
| given | omitted | scratch XI, sector ratings + formation (was `evaluate_lineup`) |
| either | all seven | adds the Monte Carlo forecast (was `predict_match` / `simulate_lineup`) |

**A partial opponent is an `IllegalArgumentException` naming the missing sectors, not a silent
fallback** — six of seven is a caller who meant to simulate and dropped one, and answering that with
ratings would look like a working call. The check runs *before* any model access, so a malformed
call is a `-32602` rather than "no snapshot loaded" from whichever lookup happened to run first.

### `search_transfers` takes a `skills` array

Twelve flat props (`skillType1..4` + `min`/`maxSkillValue1..4`, ~800 chars) collapsed into
`skills: [{skill, min, max}]`, 1–4 entries, ~200 chars. `TransferSearchQuery` keeps its indexed
setters — they mirror the CHPP parameter names one for one and the search tab shares them — so
`TransferTools.applySkills` does the list-position-to-slot mapping, which is the part that can
silently go wrong and is what the new tests pin.

**Deliberately not touched: `simulate_lineup`'s seven flat opponent params.** They look like the
same smell and are not: they mirror `get_opponent_ratings`' output keys one for one, so the ratings
copy straight across. Nesting them would break that.

### `remove_from_scout` added

The gap, not a redundancy. The re-listing watch spends up to one `playerdetails` + one
`transfersPlayer` per scout entry on **every** download for 90 days, and the documented remedy for a
bloated list was "prune the F7 tab by hand". At ~74 entries that was a recurring per-download cost
only a human could stop. Removing an entry loses nothing: its market observations stay in
`TRANSFERLISTING` and keep feeding `estimate_market_value`; only the watch goes.

### Descriptions are capped, and the cap is a test

`McpServerToolsTest.noDescriptionGrowsBackIntoADocumentationPage` fails any description over 400
chars. Fifteen of the old 34 were over 400 and totalled 12,744. A description now carries three
things and no more: **what it returns, when to call it rather than its neighbour, and the one trap
that changes the call.** The long form lives in the assistant repo's `docs/mcp-tools.md`, which is
read on demand rather than loaded into every session — that repo's owner has taken it as the
source of truth and knows which caveats moved there.

Also dropped: `get_team`'s `economy.playersSalary`. It was a stale weekly settlement that never
matched `get_squad`, and it carried a 200-char disclaimer saying so. Removing the field removes the
disclaimer, and `get_squad` was already the correct source.

**Verification status:** full suite green (749 tests, 0 failures). Schema-level only, as every MCP
tool test in this repo is — the running HO still serves the old surface until it is restarted, so
none of this has been exercised against a live client.

### The character traits ARE shown in HO's UI — the deleted description was wrong

The `list_scout` description used to claim the three traits "are not shown in the UI, so this is the
only way to read them". That was false, and the trim removed it before anyone acted on it.

`module.playeroverview.PlayerDetailsPanel:304` — the Player Details panel on the Players tab —
renders all three as a prose sentence:

```java
Helper.getTranslation("ls.player_details.desc1",
        PlayerAgreeability.toString(m_clPlayer.getGentleness()),
        PlayerAggressiveness.toString(m_clPlayer.getAggressivity()),
        PlayerHonesty.toString(m_clPlayer.getHonesty()));
```

`module.matches.SpielerDetailDialog` (reachable from `SpielerSternePanel`) shows them again as
labelled fields, and `tool.export.CsvPlayerExport` exports two of the three.

**But the UI is not the authority — the enums are.** `core.constants.player.PlayerHonesty:14-19` and
`PlayerAgreeability:13-18` name all six levels as constants, so nothing has to be confirmed by
clicking:

| n | honesty | agreeability |
|---|---|---|
| 0 | infamous | nasty fellow |
| 1 | dishonest | controversial person |
| 2 | honest | pleasant guy |
| 3 | upright | sympathetic guy |
| 4 | righteous | popular guy |
| 5 | saintly | beloved team member |

Worth knowing while reading any of this: the legacy German column names are crossed against
Hattrick's. `SpielerTable`'s `iCharakter` holds **agreeability** (`getGentleness()`) and `iAnsehen`
holds **honesty** (`getHonesty()`); `SpielerDetailDialog`'s `m_jpAnsehen` / `m_jpCharakter` fields
are swapped the same way. What the user sees is correctly labelled in both places — only the
variable names mislead. `TransferListing:158`, `ScoutList:154` and `McpJson:136` each carry a
"do not fix these" comment for exactly this reason.

### Verified live, and the two output shapes that surprised a reader of the schema

The peer agent round-tripped the surface after a restart + `/mcp`. Measured: **20,021 chars ≈ 5.0k
tokens, down from 33,781 ≈ 8.4k**. Longest description 382 chars, so the 400 ceiling holds with
room. All three `simulate_lineup` modes work, the five removed tools are gone, and a partial
opponent errors as `-32602` naming every missing key. `remove_from_scout` is the one tool with no
live call yet — it is a write, and pruning a shortlist needs a reason.

Two shapes are worth writing down because the *input* schema does not imply them, and both were
found by calling the server rather than by reading the contract:

- **`get_finances`' sizer lands under its own `arenaSizer` key** (`recommendations`,
  `soldOutGateReceipts`, `supporters`, `weeklyMaintenance`), not spread across the top level. Plain
  and `sizeArena: true` responses differ by exactly that key.
- **`simulate_lineup`'s two modes are not a superset of each other.** With no opponent the sector
  ratings are top-level (`midfield`, `attack_central`, `hatStats90`, `formation`). With an opponent
  that whole block moves inside `ourRatings`, and the top level becomes `winProbability` /
  `drawProbability` / `lossProbability`, `goalsPerMatch`, `chancesPerMatch` and the `opponent*`
  twins. Reading `hatStats90` off the top level of a simulation gets you nothing.

**The `simulate_lineup` discontinuity is deliberate — do not "unify" it.** It is what `evaluate_lineup`
and `predict_match` each already returned, which is why folding them together broke no consumer. It
is also the right shape for each question: in ratings mode the ratings *are* the answer, in
simulation mode they are one input to a match result and belong under `ourRatings` beside the
opponent's. The alternatives are duplicating the block at both levels, or a third breaking change to
a surface that has just been rebuilt. Neither is worth it.

The `sizeArena` flag is likewise not optional-by-accident: `supporters` and `capacity` merely imply
it, so the plain "should we expand?" question is `get_finances({sizeArena: true})`. Passing a
`supporters` figure to switch the sizer on silently asks a what-if instead.

## get_training: trained minutes already banked in the week in progress

Shipped after a live miss — a trainee who had already taken 90 minutes in a trained slot in the
league match was put into a trained slot again in the same week's friendly, gaining nothing and
displacing a player who could still have banked minutes.

**The gap was not "the MCP omits minutes played". It was that `get_training` asserted the opposite.**
`FutureTrainingManager:110-119` credits a flat `addFullTrainingMinutes(90)` to every player whose
ideal position falls in a trained sector — the priority comes from
`Player.getFuturePlayerTrainingPriority`, which falls back to the ideal position when the user has
set no explicit plan — whether or not he played. So `skillChanges` claims full training for exactly
the players a lineup builder must exclude. Silent and confident, rather than obviously missing.
(It is *not* "every player regardless of position": someone whose ideal position is untrained gets a
null priority and no predicted gain at all. The overstatement is worth avoiding, because it is the
version that makes the tab look more broken than it is.)

**This is not confined to the MCP — HO's own Training tab renders the same wrong first row.**
`TrainingModel.adjustFutureTrainingsVector` builds the plan starting at
`TrainingManager.getNextWeekTraining().getTrainingDate() + 7 days`. `getNextWeekTraining()` resolves
to `XTRADATA.TRAININGDATE - 7d`, so that first future entry lands exactly on the week in progress —
the same week `currentWeek` reports. The tab and `skillChanges` therefore make the same
participation assumption about the same week, which means a human cross-checking `currentWeek`
against the Training tab gets the tab's confirmation of the wrong answer. The error is transient —
once the update lands, the week moves into `TrainingsTable` and past-training views use real
minutes — but it is wrong precisely during the window in which lineups are planned.

HO already computes the real number; nothing in it ever ran that computation over the *current*
week:

- `MatchLineupTeam.initMinutesOfPlayersInSectors()` (`:582`) builds a per-position appearance
  timeline from the starting eleven plus `getReplacedPositions()`/`getRedCardedPositions()`, walks
  `getSubstitutions()` (skipping `MAN_MARKING`) and the red-card highlights, and closes each
  appearance at the right minute. `getTrainingMinutesPlayedInSectors(playerId, sectors, walkover)`
  reads minutes *per sector* off it, so a substitution into a trained slot at 60' banks 30.
  Reconstructing this from `ourPlayers[].startMinute` is not equivalent.
- `Player.calculateWeeklyTraining()` aggregates a week of those into `TrainingWeekPlayer`, whose
  every `add*` caps at 90.

### The week-boundary trap, which is the part that actually bites

`TrainingsTable.loadTrainingPerWeek` reads `XTRADATA.TRAININGDATE` — the **next** update — and stores
`trainingDate = next - 7 days`. `TrainingPerWeek.loadMatches()` then takes
`[trainingDate - 7d, trainingDate + 23h]`. So **a `TrainingPerWeek` labelled D covers the week that
ENDED at D**, and `HOModel.getTraining()` compounds it by passing `getTrainingDateAfterWeeks(-1)`.

`get_training`'s `current.trainingDate` is therefore the week already applied, not the one being
played. The week in progress is `xtra.getTrainingDateAfterWeeks(0)` — build a `TrainingPerWeek` on
that date and its match window is exactly right, with unplayed fixtures contributing 0. That single
substitution is the whole feature.

`current` now also carries `weekStart`, `weekEnd` and `nextTrainingDate` so the ambiguity cannot
recur.

### The fixtures gap closed itself

`get_upcoming_matches` is the league Spielplan only, by design (`TeamTools.java:84`), which is why an
agreed friendly inside the training week was invisible. No new tool was needed:
`MatchesKurzInfoTable.getMatchesKurzInfo(teamId, from, to, types)` filters `STATUS in (UPCOMING,
FINISHED)`, and `MatchType.isOfficial()` covers league, cup, masters *and every friendly type*. So
`TrainingPerWeek.getMatches()` — already loaded for the minutes — **is** the
fixtures-in-this-training-week list, and is emitted as `currentWeek.fixtures`.

### The false negative worth guarding

`calculateWeeklyTraining` needs `match.getMatchdetails().getOwnTeamLineup()`; a played match whose
lineup has not been downloaded yields null, HO logs an error, and the minutes silently read 0 — a
maxed player looks available. That is the original bug re-entering through a stale cache, invisibly.
Hence per-fixture `lineupAvailable`, a top-level `lineupDataComplete`, and a `note` that says
UNDERSTATED and FALSE NEGATIVE in as many words when any played fixture is missing its lineup.

Two smaller traps found while writing it, both preserved in comments:

- A **walkover win** returns 90 trained minutes but *zero* played minutes
  (`getTrainingMinutesPlayedInSectors` returns 0 early when `acceptedSectors == null`). Deciding
  "did not appear" from played minutes alone drops walkover appearances; all three totals have to be
  read first.
- **MASTERS counts for experience only**, never for training — mirrored from `Player.java:1765`.

`currentWeek.players` lists only players with minutes; absent means nothing banked. `startRoleIds`
are starting positions only and are labelled as such in the note — the *minutes* are the
substitution-accurate figure, and `fullTrainingMinutes` is emitted beside `maxedForWeek` because 60
is still worth starting and 90 is not.

`TrainingTools.WeekMinutes` is package-private so the 90-cap accumulation is unit-tested without a
`MatchLineupTeam` fixture (`TrainingToolsTest`, 2 tests). Note the hard 400-char ceiling on tool
descriptions enforced by `McpServerToolsTest.noDescriptionGrowsBackIntoADocumentationPage` — the
first draft of the new `get_training` description was 680 and failed the suite.

### Confirmed against real data, 2026-09-06

The downstream agent verified the payload after the rebuild. Anchoring correct
(`nextTrainingDate` == `currentWeek.weekEnd` == 2026-09-11), the agreed friendly present in
`fixtures` alongside the league match — the gap `get_upcoming_matches` could never close — and the
seven `maxedForWeek` players exactly the seven who held trained slots on 09-05.

Two findings worth keeping:

- **The substitution handling earned its keep on live data.** `currentWeek` reports Behring
  (485877338) 53 minutes with no start role, O'shee (465315671) 40 minutes starting 103. Reading
  `get_match`'s `ourPlayers[]` directly gives the two *transposed* — Behring at 103, O'shee at 19.
  O'shee started, was injured around the 40th, Behring played the remaining 53. The appearance
  timeline gets it right; the naive start-position read does not. This is the theoretical failure
  mode from the section above, observed.
- **A named counterexample for the `skillChanges` contradiction.** Cameron Shell (491821388) is
  predicted PASSING +1 on 2026-09-11. He is 35, banked nothing this week, and is starting 103 — the
  back line, not a trained slot. He will gain nothing. `currentWeek` is *silent* on him rather than
  contradicting him, because it lists only players with minutes; absence is the signal. Use Shell
  when the contradiction needs to be made concrete.

**Changing a tool description needs a client restart downstream, not just a reconnect.** After the
rebuild the consumer session picked up the new `currentWeek` payload on a `/mcp` reconnect but kept
serving the *old* `get_training` description, and a second reconnect changed nothing — the schema
cache is pinned per session independently of the connection. Confirmed the jar was correct by
extracting `core/mcp/TrainingTools.class` and grepping it: new description present, old absent. Note
that "new fields, old description" is the signature of a stale client rather than a bad build, and
the description and the `currentWeek` builder are in the same class, so no compile could emit one
without the other.

Two paths remain **unexercised against real data**: the loud
`lineupDataComplete: false` note (no played fixture has yet been missing its lineup) and cup ties in
`fixtures` (the team is out of the cup).

`players[].skillChanges` and `currentWeek` **contradict each other by construction** and nothing in
the payload says which to trust, so the description spends its scarce characters on the precedence
rule: where they disagree, `currentWeek` wins. An agent that reads only `skillChanges` still gets
the old wrong answer. Fixing `FutureTrainingManager` to seed the in-progress week from real minutes
would remove the contradiction at the source, but that changes HO's own Training tab predictions and
was out of scope here.

## Scout list filter, reusing the transfer-search criteria (2026-09-12)

The transfer scout tab (`TransferScout`) gained a filter bar built from the same age/skill/
speciality/max-price fields as the `Transfer search` tab, plus a scout-only "future deadline"
checkbox — filtering the scout list already in memory rather than issuing a CHPP call.

- **`module.transfer.PlayerFilterForm`** (new) — the age spinners, up to four skill rows (`+`/`-`
  reveal/hide, same as before) with a min/max `PlayerAbility` combo each, speciality combo and max
  price field, extracted verbatim out of `TransferSearchPanel` into a standalone `JPanel`. Exposes
  a `Criteria` record (age range, `List<SkillRange>`, speciality id, max price in swedish krona) and
  keeps `maxPriceOrNull` as the one static helper worth unit testing on its own
  (`PlayerFilterFormTest`, replacing `TransferSearchPanelTest`). `TransferSearchPanel` now embeds it
  and builds its `TransferSearchQuery` off `form.criteria()`; behaviour is unchanged.
- **`module.transfer.scout.ScoutFilter`** (new, pure, no Swing) — `fromCriteria(Criteria,
  futureDeadlineOnly)` returns a `Predicate<ScoutEintrag>`. Each `TransferSearchQuery.Skill` maps
  straight onto the matching `ScoutEintrag` getter (`getTorwart`, `getKondition`, `getStandards`,
  `getVerteidigung`, `getTorschuss`, `getFluegelspiel`, `getPasspiel`, `getSpielaufbau`,
  `getLeadership`, `getErfahrung` — all ten already existed), age against `getAlterWithAgeDays()`,
  speciality against `getSpeciality()`, price via `AmountOfMoney.isGreaterThan`. The future-deadline
  flag is a separate `and()`-ed predicate: `getDeadline() != null && getDeadline().after(now)` —
  deliberately not tied to `isSold()`, since the two are not independent in practice (`ScoutFilterTest`
  covers all five branches with hand-built `ScoutEintrag`s).
- **`TransferScoutingTable.setEntryFilter(Predicate<ScoutEintrag>)`** (new) attaches a `RowFilter` to
  the `TableRowSorter` that `HOTableModel.initTable` already installs on every such table — the same
  pattern `PenaltyTakersView.setRowFilter()` already uses elsewhere in this codebase. This is a pure
  view filter: it never touches `TransferScoutingTableModel`'s backing `Vector<ScoutEintrag>`, so
  `ScoutList.save()` still persists every entry regardless of what is filtered out of sight — a
  filtered-out row cannot be silently dropped from the scout list.
- **`TransferScoutPanel`** gained a `BorderLayout.NORTH` filter bar: `PlayerFilterForm` + the
  future-deadline checkbox + a **Clear filter** button. Filtering is live — `PlayerFilterForm.
  addChangeListener(Runnable)` (new) hooks every control (age spinners, each skill row's
  skill/min/max, the `+`/`-` row count, speciality, a `DocumentListener` on the price field) and the
  checkbox's own listener, both re-applying `ScoutFilter.fromCriteria(...)` on every change - there
  is no CHPP call behind it, so a button click would only have added friction. The form starts wide
  open (`PlayerFilterForm.resetToWideOpen()`, new: full 17-40 age range, full 0-20 skill range, no
  speciality, no price cap) rather than at its CHPP-search defaults (20-22, playmaking 7-9), which
  would otherwise hide most of the scout list the moment the tab opens. **Clear filter** calls
  `resetToWideOpen()` plus unchecks the future-deadline box, then explicitly sets a `null` row
  filter (the live listeners it just triggered already converge on a match-everything filter, but
  `null` is what actually removes the `RowFilter` object rather than installing an always-true one).
  New keys `ls.scout.filter.clear`, `ls.scout.filter.futureonly` (`ls.scout.filter`, the button
  label, was added and then removed in the same pass once the button itself was dropped).
- **German translations added** for the `ls.transfersearch.*` block (shipped English-only per the
  transfer-search tab work above) and the three new `ls.scout.filter*` keys. Unrelated to this
  change but discovered on the way: `German.properties` is missing every `ls.*` key added since
  roughly the transfer-search / MCP-server / Hall-of-Fame-experttype work (`ls.hof.experttype.*`,
  `ls.options.mcp.*` — zero matches in German at all), not just this feature's keys. Left alone, out
  of scope here; a maintainer pass through POEditor is the real fix.

No DB migration, no CHPP change. Full suite green (758 tests, 64 suites, 0 failures).

## `backfill_character`, first slice of the estimate_market_value rework (2026-09-12)

Cross-session design discussion (this session + the `hattrick` peer session) on improving
`estimate_market_value`'s comparable weighting - full plan not yet implemented, but it surfaced a
standing gap worth fixing on its own: **most of the corpus can never get character**, because the
only two writers (`add_to_scout`, and the resolver's scout-sale pass) both require the player to be
on the scout list. A search hit or a bid snapshot never gets a follow-up `playerdetails` call, so
those rows sit at `AGREEABILITY IS NULL` forever. Confirmed against the live DB: 122 of 4086
TRANSFERLISTING rows had character, 31 with a resolved sale.

**Character survives Hattrick's unlisted-player pruning** (§ "Character over MCP" above already
established this for the XML shape) - unlike the seven hidden skills, a `playerdetails` call answers
it for *any* player, listed or not, on any account tier. So a dedicated backfill pass doesn't need
the "is he listed right now" guard `observeForCorpus`'s skill-fill has; it can walk the whole corpus,
not just players currently back on the market.

Added, no schema change (the three columns were already nullable):

- `TransferListingTable.loadPlayerIdsMissingCharacter(max)` - distinct players with
  `AGREEABILITY IS NULL`, oldest observation first.
- `TransferListingTable.storeCharacter(playerId, agreeability, aggressiveness, honesty)` - one
  `UPDATE ... WHERE AGREEABILITY IS NULL`, so one lookup fills every stored observation of that
  player at once (same pattern as `storeSalePrice`), plus the matching `DBManager` wrappers.
- `TransferListingResolver.backfillCharacter(max)` - the actual pass: one
  `OnlineWorker.downloadMarketPlayer` call per missing player, best-effort per player, returns a
  `CharacterBackfillResult(checked, updated)`. Lives in `TransferListingResolver`, not `TransferTools`,
  so it can share `resolve()`'s time-budget discipline (see `TIME_BUDGET`'s own doc comment on why
  this file replaced a call-count cap with wall-clock time) rather than duplicating a raw loop that
  a slow connection could stretch to minutes. Its own `CHARACTER_BACKFILL_BUDGET` is a smaller 20s,
  deliberately not shared with `resolve()`'s 60s: a sale price has a closing window, character does
  not, so it must not compete with the sale pass for the same budget on every download.
- **Runs automatically after every team download**, alongside sale-price resolution:
  `OnlineWorker.resolveTransferListings()` now calls both `TransferListingResolver.resolve()` and
  `.backfillCharacter(MAX_CHARACTER_BACKFILL)` as two independent try/catch blocks, so a failure or
  timeout in either does not cost the other its turn. This was the actual ask - `backfill_character`
  alone would only have filled character when an agent remembered to call it.
- MCP tool `backfill_character` (`maxPlayers`, default and hard cap `MAX_CHARACTER_BACKFILL` = 200) is
  a thin wrapper over the same resolver method, for a caller who wants to force a batch beyond
  waiting on downloads. Reports `checked`/`updated`/`moreLikely` (true when the cap or the time
  budget was hit, meaning call again).

Also corrected mid-discussion: the `hattrick` session initially reasoned that comparables shouldn't
be weighted on character because "bidders can't see it without scouting" - wrong per this project's
own docs (`chpp_files.md`'s playerdetails section, and the pruning note above); character is public
on any player's page, listed or not. The real obstacle was only ever corpus coverage, which this
backfill addresses.

Still open, not yet built: the weighted-comparable rework itself (C-skill + character + a
continuous age-days kernel favouring trainee sensitivity below ~19, replacing the flat
age/A-skill/B-skill hard filter's plain median) and the ask/bid fallback recalibration (`hattrick`'s
resolved-sale data shows early snapshot bids/asks closing 1.5-2.7x higher than the raw number
`estimate_market_value` reports today).

### Closed a double-`playerdetails`-call gap between `resolve()` and `backfillCharacter()`

Rainer caught it: `backfillCharacter()` had its own raw `OnlineWorker.downloadMarketPlayer` call,
separate from `resolve()`'s three passes (sale candidates, re-listing watch, corpus skill-fill) which
also call `playerdetails` per player via the shared `observe()` helper. `observe()` only ever
persisted character onto a *freshly inserted* row (`TransferListing.fromPlayerDetails`, and only when
the player happened to be listed again) - never backfilled onto the player's existing null-character
rows. So a player `resolve()` had just fetched `playerdetails` for could still show up in
`backfillCharacter()`'s `AGREEABILITY IS NULL` query moments later in the same
`resolveTransferListings()` call, costing a second, redundant `playerdetails` call for data already
in hand.

Checked with `impact_analysis` before touching `observe()`, since it is shared by three call sites:
exactly three callers, all internal to `resolve()`'s own passes, and exactly two callers of
`backfillCharacter()` (`OnlineWorker.resolveTransferListings()` and the MCP tool) - safe to change.

Root-cause fix, not a bigger merge: `observe()` now calls `storeTransferListingCharacter`
unconditionally on every successful response, listed or not - character survives Hattrick's
unlisted pruning, so it's always there to store, and the DB's `WHERE AGREEABILITY IS NULL` in
`storeCharacter` makes it a safe no-op when there is nothing to fill. `backfillCharacter()` was
rewritten to call `observe()` too, instead of duplicating the download-and-store logic - `observe()`
is now the one place that downloads `playerdetails` and applies everything a response can produce.

This closes the gap without merging `resolve()` and `backfillCharacter()` into one loop over a
unified candidate set: `resolveTransferListings()` still runs them as two sequential top-level calls,
but `resolve()` commits to the database immediately, and `backfillCharacter()` re-queries fresh
afterwards - so a player either pass already looked up this download is no longer missing character
by the time the other asks, with no in-memory state to thread between two otherwise-separate methods.
A fuller unification (one pass, one candidate set covering sale price + character + skill-fill +
relisting-watch together) was considered and set aside: the four passes select candidates on
genuinely different criteria (a sale watermark, a re-listing grace window, a skill-fill grace window,
"never fetched at all") that do not collapse into one query, and `resolve()` is already flagged at
cognitive complexity 33 against a 15 budget - folding a fifth concern in would make it worse for a
gap the sequencing fix above already closes. Full suite green (760 tests, 64 suites, 0 failures).

### The general corpus's sale-price pass was starved by the scout-list passes sharing its budget

Rainer restarted HO, ran several downloads, and asked why sale price (1705 -> 1724, +19 rows) grew so
much slower than character (122 -> 1333, +1211 rows) after the fixes above. The two passes are not
actually different in shape - `resolve()`'s corpus pass (pass three: unresolved rows, oldest
observation first, time-bounded) is structurally the same walk as `backfillCharacter()` - but they
were not on equal footing for time. `resolve()`'s three passes (sale candidates, re-listing watch,
corpus) all shared one `TIME_BUDGET` (60s), consumed in that order, and the corpus pass's own comment
said it as much: "with whatever is left of the time." Passes one and two are scout-list housekeeping
and can legitimately spend the whole 60 seconds on a run with many newly-expired entries, since a
sale has exactly one moment it can still be asked about - which regularly left pass three, the one
`estimate_market_value` actually reads, with nothing.

Fix: pass three now runs on its own budget, `CORPUS_BACKFILL_BUDGET` (20s) - the same constant
`backfillCharacter()` uses (renamed from `CHARACTER_BACKFILL_BUDGET`, since both are now instances of
the same policy: a lower-priority, general-corpus concern gets a guaranteed slice of its own instead
of only ever getting what scout-list housekeeping did not spend). Worst case per download is now up
to TIME_BUDGET + CORPUS_BACKFILL_BUDGET (resolve()) + CORPUS_BACKFILL_BUDGET (backfillCharacter, its
own top-level call) = 100s, versus 80s before - accepted, since the alternative is a corpus pass that
can go arbitrarily long without making progress on a busy scout list. Full suite green (760 tests).

### Baseline for the next test, live DB as of 2026-09-12 18:32:41

Recorded before the `CORPUS_BACKFILL_BUDGET` fix above has run against a real download - this build
was not yet rebuilt/restarted when these downloads happened, so it's the "before" number to compare
the fix against, not a result of it:

| Metric | Value |
|---|---|
| Total TRANSFERLISTING rows | 4170 |
| Rows with character | 1919 |
| Distinct players with character | 913 |
| Rows with resolved sale price | 1725 |
| Rows marked resolve-attempted | 3261 |
| Newest observation | 2026-09-12 18:32:41 |

Sale price barely moved against the prior checkpoint (1724 -> 1725) while character kept climbing
(1333 -> 1919) - consistent with the starvation bug above still being live in the running jar.
Expectation once rebuilt (`./gradlew createVersion createDistribution`) and re-run: sale price should
start climbing at a comparable rate to character, since pass three no longer waits on scout-list
leftovers.

### A modal CHPP error dialog per player could freeze a download (2026-09-12)

Rainer hit it live: `HO_error.png` showed "CHPP Fehler - 56 - Additional Info: null" on
`playerdetails?...&includeMatchInfo=true&playerID=512663039`, mid-download, with his working theory
being a currently-running match on that player's team.

Root cause, independent of what error 56 specifically means (no confirmed mapping for it in this
project's docs - `Connector`/`XMLCHPPPreParser` just relay whatever `<ErrorCode>` Hattrick sends):
`OnlineWorker.downloadMarketPlayer` - the single method behind both `TransferListingResolver.observe`
(the sale/character/skill-fill passes, and now `backfillCharacter`) and `TransferTools.observeForScout`
(`add_to_scout`) - never set `Connector`'s `silentDownload` flag. Any CHPP error on that call reaches
`Connector.getCHPPWebFile`'s catch block, which pops a `JOptionPane.showMessageDialog` whenever
`!silentDownload` - and that call **blocks** until the dialog is dismissed. `add_to_scout` only ever
spent one such call per player, so this was rare enough not to be noticed; `backfill_character` can
look up dozens of players per call (within its 20s `CORPUS_BACKFILL_BUDGET`), so a single unlucky
player mid-match now had the potential to freeze the whole download waiting for someone at the
keyboard - once for every such player it happened to hit.

Fix: `downloadMarketPlayer` now wraps its call in `Connector.instance().setSilentDownload(true)` /
restore, the same save-and-restore pattern `downloadYouthPlayers`'s unlock-skills fallback already
uses elsewhere in this file. Both callers already treat a failed lookup as best-effort - logged via
`HOLogger` and skipped, never surfaced - so the modal was always redundant with that handling, just
also actively harmful at the new call volume. Full suite green (760 tests).

### estimate_market_value: weighted comparables, outlier winsorizing, fallback recalibration (2026-09-12)

Implemented the design from the earlier cross-session discussion with the `hattrick` peer session
(character-weighting correction included). All in `TransferTools.java`, no schema change, no new
tool - `estimate_market_value`'s existing hard filter (age/A-skill/B-skill/specialty, unchanged) still
bounds `comparableQuery`; everything below is a re-ranking of what it already returns.

**Weighted median, not a plain one.** Each comparable gets a weight = product of three independent
Gaussian similarity kernels against the target player, each defaulting to a neutral 1.0 - not a
penalty - when the listing lacks the data to judge it (a bid-only observation has no skills at all;
most of the corpus still has no character):
- **Age**, on `age*112+ageDays` (a Hattrick season year is 112 days) rather than year-only, so a
  23y111/24y0 pair one day apart no longer reads as a full year gap. Sigma is banded by the target's
  own age (`ageSigmaDays`: tight below 19, loosest from 26 on) - sharper trainee sensitivity, matching
  both Hattrick's own search-window shape (1-year cap at 17-18, 2 years from 19) and the peak/decline
  band `docs/transfer-searches.md` already documents starting around 26-28.
- **Third-best positional skill** (`thirdHighestOrNull`), softer than the ±1-level hard window on the
  top two skills.
- **Character**, summed absolute gap across all three traits, wide sigma (weak signal by design,
  given current coverage - see the `backfill_character` work above).

Floored at 0.05 (`WEIGHT_FLOOR`) - a soft heuristic should narrow a row's influence, never zero it
out. `weightedMedian` walks the price-ascending list accumulating weight until it crosses half the
total, the weighted analogue of the existing plain `median`. Each evidence row in `comparables` now
also carries its own `weight`, so a row that barely counted is visible as such rather than reading
like equal evidence.

**Outlier winsorizing.** The kernel above only measures how similar a player is, not whether his
price is sane - a troll ask sitting close on every one of those axes (the hattrick session's example:
a mid-skill teenager asking EUR6.75M next to EUR150-400k comparables) would pass it undamped. Each
price is clamped to within `WINSORIZE_BAND` (3x) of the sample's own plain median before the weighted
median is computed over it. The reported `range` and the `comparables` evidence stay un-clamped, so
the outlier is still visible, just no longer able to become the estimate itself.

**Ask/bid fallback recalibration.** When too few resolved sales exist and the tool falls back to
asking/bid snapshots, each price is now nudged up by `fallbackClimb`: a linear ramp from 1.0x (at or
past the deadline) to 2.0x (a full `FALLBACK_FULL_WINDOW` of 5 days still left) based on how much time
remained when the snapshot was taken. `ponytail`: fit to four data points the hattrick session
collected from its own scouting (Bulnys 59k->152k, Knop 100k->273k, Pritchin 204k->466k, Außerbauer
266k->396k - a 1.5x-2.7x spread), not a real regression - revisit once enough resolved sales exist in
the corpus to calibrate against actual outcomes instead of a handful of anecdotes.

**Tests**: 10 new pure-function tests in `TransferToolsTest` (`thirdHighestOrNull`, `ageSigmaDays`,
`comparableWeight`, `fallbackClimb`, `weightedMedian`) - `estimateMarketValue` itself still has no
direct test, same as before this change, since it needs a loaded `HOVerwaltung` model; the pure
helpers underneath it are where the new logic actually lives. Full suite green (770 tests).

Still open from the original design discussion: nothing else - this was the last piece. Future work
would be calibrating the sigma bands and the fallback climb against real resolved-sale history once
there is enough of it, per the `ponytail:` comments left in the code.

### Per-row transparency: weightBreakdown and adjustedPrice on each comparable (2026-09-12)

Rainer, reasoning about the tool as its own LLM consumer: `weight` alone says how much a comparable
counted, not why, and the fallback-climb note above is a paragraph-level claim with no per-row number
to check it against - either meant recomputing the math by hand to verify it instead of the tool
having already done that.

Refactored `comparableWeight` into `comparableWeightBreakdown`, returning a `WeightBreakdown(age,
skill, character)` record (each kernel's own value, 0-1) with a `combined()` method doing the same
floor-then-product `comparableWeight` always did - `comparableWeight` is now a one-line wrapper over
it, so every existing call site and test keeps working unchanged. Each `comparables` evidence row now
adds:
- `weightBreakdown`: `{age, skill, character}`, so a row down-weighted for age versus one
  down-weighted for character no longer look the same at a glance.
- `adjustedPrice`: the sale price unchanged, or an ask/bid run through `fallbackClimb` - the actual
  number that row would contribute to the estimate, checkable per row instead of only described in
  `estimateNote`/`note`.

One new test, `comparableWeightBreakdown_reportsEachKernelSeparately`, specifically checking the
three factors independently - `comparableWeight`'s existing tests only ever checked the *product*,
which a bug swapping two kernels (one too high, one too low) could still pass by cancelling out.
Full suite green (771 tests).

## `estimate_market_value` for a player outside the squad (2026-09-13)

Design in `docs/superpowers/specs/2026-09-13-estimate-market-value-raw-profile-design.md`. The tool
required `playerId` to resolve against the current squad, so a departed player (sold this season,
visible only through `get_team_transfers` + `get_player_history`), a prospective buy, or an
opponent's player could never be valued. `profile` is now a sibling argument: `{age, skills: {one
of the six positional skills -> level, at least one}, plus optional playerName, ageDays, specialty,
tsi, agreeability, aggressiveness, honesty}`. Exactly one of `playerId`/`profile` per call.

`playerFromProfile` builds a throwaway `Player` (`new Player()` plus setters - the same pattern
`PlayerConverter.buildHTCopyButton` already uses for a copy-pasted transfer listing, never persisted)
and feeds it through the identical `comparableQuery`/`comparableWeightBreakdown`/`sameArchetype` code
every squad player already goes through. Two things the design doc got wrong or underspecified, both
caught before or via the tests:

- **Skill-name validation is narrower than `TransferSearchQuery.Skill`.** That enum has 10 constants
  (adds `STAMINA`, `SETPIECES`, `LEADERSHIP`, `EXPERIENCE` on top of the six `POSITIONAL_SKILLS`), but
  `profile.skills` must reject anything outside the six - a new `positionalSkill()` helper, not a
  reuse of `parseSkill`'s full-enum `valueOf`.
- **Omitted skills default to 1, not 0.** The design doc said 0; Hattrick has no skill level 0 (1-20
  is the real range), and `Player`'s own fields already default to 1 for exactly that reason. Caught
  by a failing test rather than by re-reading the doc closely enough the first time - the code (just
  leaving an unset skill at `Player`'s own default) was already right, the test's expectation wasn't.

`comparableWeightBreakdown`'s `targetChar` (`int[]`) is now nullable: a `profile` missing any of
`agreeability`/`aggressiveness`/`honesty` passes `null` rather than a fabricated `{0,0,0}`, so a
candidate with real character data isn't penalised against a target that never had one. The
`profile`-path response is built field-by-field from what the caller actually supplied (no wage/
contract/team fields a bare profile never had), unlike the `playerId` path's `topSkillsOnly`, which
is untouched. 8 new tests; full suite green (784 tests).

### The `basis`/evidence mismatch survived the profile work too (2026-09-13)

Flagged by another session testing the new `profile` path against three real sold players: all three
calls came back `basis: "resolved_sales"`, `sampleSize: 11`, but every one of the 40 `comparables`
rows had `sold: false`. Not a regression from the profile change - the same shape as an earlier,
already-documented caveat (see "What the corpus actually holds" above: 11 players with a resolved
sale exist in the whole corpus). Root cause: the `comparables` evidence array was built straight from
the newest-observed-first list regardless of which rows the estimate actually used. A resolved sale's
`observedAt` freezes the moment it resolves; every live re-search of the *same* age/skill window
keeps bumping unrelated, never-resolved asks to the front with a fresher timestamp, so once the
corpus for a window holds more than `MAX_COMPARABLES` (40) rows, the resolved sales backing
`sampleSize` can be pushed out of the array entirely - the tool ends up citing evidence it isn't
showing.

Fix: `evidenceOrder(comparables, sample)` - a stable sort that moves `sample`'s own rows (the ones
the estimate was actually computed from) to the front, leaving the rest in their existing
newest-first order. One new test pins it directly, without needing a DB fixture: two `TransferListing`
stubs that were never part of the sample, one that was, handed in newest-first, sample-member first
out. Full suite green (785 tests).
