# youthleaguedetails

The youth league information.

## Changelog
### Version 1.1 (2026-02-26)
- Added team LeagueSystemId.
- Added Output: HattrickData → LeagueSystemID (Element)

### Version 1.0 (2013-12-02) {default}
- First version

## Input
Only available inputs in this file version are shown.
required file = youthleaguedetails
optional version = 
1.1
1.0
optional youthleagueid : unsigned Integer{Default:LeagueID of the youth team of the currently logged on user's primary club}
Specifies the 'series' (league) that you are looking for data on.

## Output
- **HattrickData** — Hattrick Data Container
  - YouthLeagueID : unsigned Integer — The ID number of the Youth League (series).
  - YouthLeagueName : String — The name of the Youth League.
  - YouthLeagueType : YouthLeagueType — The type of the league: regional, national or international.
  - Season : unsigned Integer — The season of the league.
  - LastMatchRound : unsigned Integer — The the last match round played for the league.
  - NrOfTeamsInLeague : unsigned Integer — Number of teams in the league.
  - LeagueSystemID : LeagueSystemID — The system type of the league.
  - **Teams** — Container for the teams.
    - **Team** — Temas in the league.
      - TeamID : unsigned Integer — The team id.
      - TeamName : unsigned Integer — The team name.
      - Position : unsigned Integer — The position in the league.
      - PositionChange : positionChange — How the team is moving in the league.
      - Matches : unsigned Integer — Matches played.
      - GoalsFor : unsigned Integer — Goals for.
      - GoalsAgainst : unsigned Integer — Goals against.
      - Points : unsigned Integer — Points.
      - Won : unsigned Integer — Number of Won.
      - Draws : unsigned Integer — Number of Draws.
      - Lost : unsigned Integer — Number of Lost.
