# tournamentleaguetables

League tables for a tournament. This is only available for the current season. For non-restarted tournaments this is available for 2 seasons after the tournament finished.

## Changelog
### Version 1.1 (2021-06-01)
- Added Input: worldCupRound
- Added Output: HattrickData → WorldCupRound (Element)

### Version 1.0 (2013-10-17) {default}
- First version

## Input
Only available inputs in this file version are shown.
required file = tournamentleaguetables
optional version = 
1.1
1.0
required tournamentId : unsigned Integer
The id for the tournament to get data for.
optional season : unsigned Integer
The season of the tournament.
optional worldCupRound : unsigned Integer
Specifies which particular world cup stage data is requested for. Used only for World Cup tournaments. Otherwise not used.

## Output
- **HattrickData** — Hattrick Data Container
  - TournamentId : unsigned Integer — The globally unique id for the tournament.
  - Season : unsigned Integer — The season of the tournament.
  - WorldCupRound : unsigned Integer — The fetched World Cup round. Only exists for World Cup tournaments!
  - **TournamentLeagueTables** — Container for the list of league tables.
    - **TournamentLeagueTable** — Container for the data about the tournament.
      - GroupId : unsigned Integer — The id for the tournament group, 1=A 2=B and so on.
      - **Teams** — Container for the data about the teams in the league table.
        - TeamID : unsigned Integer — The globally unique team indentifier.
        - TeamName : String — The team's full name.
        - Position : unsigned Integer — Position in the table.
        - PositionChange : Integer — Show if the team has moved up or down from previous match round.
        - Matches : unsigned Integer — How many matches the team has played.
        - GoalsFor : unsigned Integer — How many goals the team has scored.
        - GoalsAgainst : unsigned Integer — How many goals the team has allowed their opponents to score.
        - Points : unsigned Integer — How many points the team has.
        - Won : unsigned Integer — How many matches the team has won.
        - Draws : unsigned Integer — How many draws the team has.
        - Lost : unsigned Integer — How many matches the team has lost.
