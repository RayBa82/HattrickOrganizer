# `estimate_market_value` for a player outside the squad — design

`estimate_market_value` currently requires `playerId` to resolve to a player `McpJson.model()`
considers part of the current squad (`getCurrentPlayer(playerId)`), and throws a hard
`McpToolException` otherwise. That is a squad-membership gate on a tool whose actual computation —
`comparableQuery` plus the weighted-median machinery — needs nothing squad-specific: age, a
positional-skill profile, optionally specialty/TSI/character.

The gap surfaced from a real question: was the sale price fair for three players sold earlier this
season (Škerbinek, Flikweert, Spaltenstein)? Their profile at sale time is available from
`get_team_transfers` + `get_player_history`, but once sold they are gone from the squad and every
`estimate_market_value` call for them refuses outright. The same gate blocks valuing a prospective
buy (also not yet in the squad) or an opponent's player (never will be).

## Goals

- `estimate_market_value` accepts either `playerId` (existing behavior, unchanged) or a new `profile`
  object describing a hypothetical player by age + skills, and runs the identical comparable-matching
  and weighting logic either way.
- The `playerId` path's output is byte-for-byte unaffected.
- No fabricated data in the `profile` path's response: fields never supplied are never echoed back
  with an invented value.

## Non-goals

- No change to `comparableQuery`, the DB layer, or any other tool. This is scoped to
  `estimateMarketValue`'s own input handling and the one `comparableWeightBreakdown` nullability gap
  it exposes (see §3).
- No persistence of a `profile` call anywhere (no scout-list entry, no TRANSFERLISTING row). The
  caller already has the data; this tool only values it.
- No attempt to let `profile` and `playerId` be combined (e.g. "value this squad player but override
  his age"). Exactly one input mode per call.

## 1. Schema and validation

`profile` is a new optional object parameter, sibling to `playerId` (also now optional):

```json
{
  "playerId": null,
  "profile": {
    "playerName": "Škerbinek",
    "age": 24,
    "ageDays": 40,
    "skills": {"playmaking": 14, "passing": 8},
    "specialty": 0,
    "tsi": 4200,
    "agreeability": 3,
    "aggressiveness": 2,
    "honesty": 4
  }
}
```

`skills` keys are the same names `skillName(Skill)` already produces (`keeper`, `defending`,
`playmaking`, `passing`, `winger`, `scoring`) — the same vocabulary `get_player` and
`get_player_history` report skills under, so a caller can pass those responses' values straight
through without renaming anything.

Validation, checked before anything else runs:

| Condition | Result |
|---|---|
| Neither `playerId` nor `profile` given | `McpToolException("Provide either playerId (a squad player) or profile (age + skills) to value.")` |
| Both given | `McpToolException("Provide playerId or profile, not both.")` |
| `profile` given, `age` missing | `McpToolException` naming `age` |
| `profile` given, `skills` missing or empty | `McpToolException` naming `skills` |
| a key in `skills` is not one of the six recognized skill names | `IllegalArgumentException` naming the bad key — same convention `queryFrom` already uses for an unknown skill in `search_transfers` (`queryFrom_namesTheUnknownSkill`) |

`ageDays`, `specialty`, `tsi`, `agreeability`, `aggressiveness`, `honesty`, `playerName` are all
optional and independent of each other. An omitted skill in `skills` defaults to level 0 — the same
default an untrained squad player's skill has — so a caller only needs to supply the skills that
matter for the profile being valued; `comparableQuery`'s own top-two ranking picks correctly out of
whatever is given.

## 2. Building the synthetic target

`Player` is not squad-locked: `PlayerConverter.buildHTCopyButton` already builds one from scratch
(copy-pasted transfer-market text, no HOModel/DB involvement) via `new Player()` plus setters, and a
handful of other call sites (`XMLParser`'s `dummy`, `SpielerTrainingsSimulatorPanel`'s `tempPlayer`)
do the same for their own throwaway purposes. `estimateMarketValue` follows the same pattern:

```java
private static Player playerFromProfile(JsonObject profile)
```

Sets `age`, `ageDays` (default 0), `setValue4Skill` for each key present in `skills`, `specialty`
(default 0 — already `comparableQuery`'s own "no specialty filter" value), and the three character
setters only if their JSON fields are present. The resulting `Player` is fed into the *same*
`comparableQuery` / `playerSkillLevels` / `comparableWeight` / `sameArchetype` code every squad
player already goes through — no branch anywhere in the matching or weighting logic.

## 3. `targetChar` becomes nullable

`comparableWeightBreakdown`'s character kernel today assumes the *target's* character is always
real; it only null-checks the *candidate* listing:

```java
final var charWeight = listing.getAgreeability() == null ? 1.0
        : gaussianWeight(..., targetChar[0], ...);
```

A squad player always has real character data from `HOModel`, so this has never mattered before. A
`profile` that omits `agreeability`/`aggressiveness`/`honesty` must not silently become `{0,0,0}` fed
into that kernel as if it were real — a candidate with genuine character data would then be penalised
for not matching a fabricated target, exactly backwards from the neutral-when-unknown behavior this
kernel already has on the candidate side.

Fix: `targetChar` (`int[]`) becomes nullable as a whole. `estimateMarketValue` passes `null` when
`profile` omitted all three character fields, and the kernel gains one clause:

```java
final var charWeight = listing.getAgreeability() == null || targetChar == null ? 1.0
        : gaussianWeight(..., targetChar[0], ...);
```

Every existing caller passes a real array (squad players always have character data) and is
unaffected. `comparableWeight`, which only forwards to the breakdown, needs no change.

## 4. Response shape

`estimateMarketValue`'s `"player"` field is built differently depending on which input mode was
used:

- `playerId` path: `topSkillsOnly(McpJson.player(player), playerLevels)`, unchanged.
- `profile` path: a small object built only from the fields the caller actually supplied —
  `playerName` if given, `age`, `ageDays`, `skills` as given, `specialty`, `tsi`,
  `agreeability`/`aggressiveness`/`honesty` if given. Nothing for wage, contract, or team fields a
  bare profile never had — the same "never publish a value the source didn't send" rule
  `list_scout`/`get_open_bids` already follow for `askingPrice`.

Everything downstream of the `"player"` field — `basis`, `sampleSize`, `estimate`, `range`,
`comparables`, `comparablesMatched`, `observationsMatched` — comes from the same code regardless of
input mode, since it only depends on the `Player` and the `TransferSearchQuery` built from it.

## 5. Testing

- Schema test: `profile` present and optional alongside `playerId` in `estimate_market_value`'s input
  schema; `required` no longer forces `playerId` alone.
- Validation: neither given, both given, `profile` missing `age`, `profile` with empty `skills`, and
  `profile.skills` containing an unrecognized skill name — one test per case, each asserting the
  specific exception type and message.
- `playerFromProfile`: given a profile JSON, the resulting `Player` reports the right age/ageDays,
  `getValue4Skill` for each supplied skill, specialty, and character getters — and character getters
  default sanely when omitted (whatever `Player`'s own unset default is; §3 is what keeps that default
  out of the weighting math, not this constructor).
- `comparableWeightBreakdown` with `targetChar == null`: character kernel is neutral (1.0) regardless
  of the candidate listing's own character data, including when the candidate *does* have real
  character data (the case §3 exists to fix).
- No full `estimateMarketValue` integration test through the `profile` path: the existing test file
  has no DB/model fixture for that method today (its one existing test only checks the tool's schema),
  and building one is out of scope for this change. The unit tests above cover every new branch of
  logic; the manual check below covers the wiring.

Manual verification: call `estimate_market_value` with a `profile` for a known departed player (age,
skills, specialty pulled from `get_player_history`) against the live corpus, and confirm the estimate
and comparables array come back non-empty and structurally identical in shape to a `playerId` call.

## 6. Files touched

| File | Change |
|---|---|
| `core/mcp/TransferTools.java` | `profile` schema property, validation, `playerFromProfile`, `targetChar` nullability in `comparableWeightBreakdown`, profile-path response building in `estimateMarketValue` |
| `src/test/java/core/mcp/TransferToolsTest.java` | schema test update, validation tests, `playerFromProfile` tests, `comparableWeightBreakdown` null-`targetChar` test |

No database migration, no change to `Player.java` itself (only how `TransferTools` constructs and
populates one), no change to `comparableQuery`, `comparableWeight`, `sameArchetype`, or
`clampedWeightedMedian` (added earlier this session) — all three compose with the synthetic `Player`
unchanged.
