# youthleaguefixtures

Fixtures for a Youthe League (series).

## Changelog
### Version 1.0 (2013-12-02) {default}
- First version

## Input
Only available inputs in this file version are shown.
required file = youthleaguefixtures
optional version = 
1.0
optional youthleagueid : unsigned Integer{Default:LeagueID of the youth team of the currently logged on user's primary club}
Specifies the 'series' (league) that you are looking for data on.
optional season : unsigned Integer{Default:The current season for the YouthLeagueID}
The season number for the league.

## Output
- **HattrickData** — Hattrick Data Container
  - YouthLeagueID : unsigned Integer — The ID number of the Youth League (series).
  - YouthLeagueName : String — The name of the Youth League.
  - YouthLeagueType : YouthLeagueType — The type of the league: regional, national or international.
  - Season : unsigned Integer — The season of the league
  - LastMatchRound : unsigned Integer — The the last match round played for the league.
  - NrOfTeamsInLeague : unsigned Integer — Number of teams in the league.
  - **Matches** — Container for the matches.
    - **Match** — Matches in the league.
      - MatchID : unsigned Integer — The globally unique match indentifier.
      - MatchRound : unsigned Integer — MatchRound that the match is part of.
      - Status : MatchStatus — Match status.
      - **HomeTeam** — The home team container.
        - HomeTeamID : unsigned Integer — The home team's TeamID.
        - HomeTeamName : String — The home team's full name.
      - **AwayTeam** — The away team container.
        - AwayTeamID : unsigned Integer — The away team's TeamID.
        - AwayTeamName : String — The away team's full name.
      - MatchDate : DateTime — The date indicating when kick-off takes/took place.
      - HomeGoals : unsigned Integer* — The final number of goals that the home team scored. This parameter is only sent for matches that are finished.
      - AwayGoals : unsigned Integer* — The final number of goals that the away team scored. This parameter is only sent for matches that are finished.
