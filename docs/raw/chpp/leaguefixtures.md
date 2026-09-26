# leaguefixtures

Fixtures for a League Level Unit (series)

## Changelog
### Version 1.2 {default}
- First version

## Input
Only available inputs in this file version are shown.
required file = leaguefixtures
optional version = 
1.2
optional leagueLevelUnitID : unsigned Integer{Default:LeaguelevelUnitID of the team of the currently logged on user's primary club}
Specifies the 'series' (leagueLevelUnit) that you are looking for data on.
optional season : unsigned Integer{Default:The current season}
Specifies the season that you are looking for data on.

## Output
- **HattrickData** — Hattrick Data Container
  - LeagueLevelUnitID : unsigned Integer — The ID number of the LeagueLevelUnit.
  - LeagueLevelUnitName : String — The name of the LeagueLevelUnit.
  - Season : String — The season that the returned data applies to.
  - **Match** — Container for the data for a certain match within the fixtures.
    - MatchID : unsigned Integer — The globally unique match indentifier.
    - MatchRound : unsigned Integer — MatchRound that the match is part of.
    - **HomeTeam** — The home team container.
      - HomeTeamID : unsigned Integer — The home team's TeamID.
      - HomeTeamName : String — The home team's full name.
    - **AwayTeam** — The away team container.
      - AwayTeamID : unsigned Integer — The away team's TeamID.
      - AwayTeamName : String — The away team's full name.
    - MatchDate : DateTime — The date indicating when kick-off takes/took place.
    - HomeGoals : unsigned Integer* — The final number of goals that the home team scored. This parameter is only sent for matches that are finished.
    - AwayGoals : unsigned Integer* — The final number of goals that the away team scored. This parameter is only sent for matches that are finished.
