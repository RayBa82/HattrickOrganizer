# leaguedetails

Information about a League Level Unit (series)

## Changelog
### Version 1.6 (2022-03-03)
- Added ranking.
- Added Output: HattrickData → Rank (Element)

### Version 1.5 (2017-01-05)
- Added Output: HattrickData → Team → UserId (Element)

### Version 1.4 (2012-02-15)
- Added Output: HattrickData → CurrentMatchRound (Element)

### Version 1.3 (2012-02-12)
- Added Output: HattrickData → Team → Won (Element)
- Added Output: HattrickData → Team → Draws (Element)
- Added Output: HattrickData → Team → Lost (Element)

### Version 1.2 {default}
- First version

## Input
Only available inputs in this file version are shown.
required file = leaguedetails
optional version = 
1.6
1.5
1.4
1.3
1.2
optional leagueLevelUnitID : unsigned Integer{Default:LeaguelevelUnitID of the team of the currently logged on user's primary club}
Specifies the 'series' (leagueLevelUnit) that you are looking for data on.

## Output
- **HattrickData** — Hattrick Data Container
  - LeagueID : unsigned Integer — The ID number of the League (for instance Sverige, USA, England...) that this LeaguelevelUnit is part of.
  - LeagueName : String — The name of the League (for instance Sverige, USA, England...) that this LeaguelevelUnit is part of.
  - LeagueLevel : unsigned Integer — Indicates the division/level this LeaguelevelUnit is located on. 1 is the top division, the second best is 2 and so on.
  - MaxLevel : unsigned Integer — How many divisions/levels there are in this league.
  - LeagueLevelUnitID : unsigned Integer — The ID number of the LeagueLevelUnit.
  - LeagueLevelUnitName : String — The name of the LeagueLevelUnit.
  - CurrentMatchRound : String — Current match round.
  - Rank : unsigned Integer — Ranking of the LeaagueLevelUnit.
  - **Team** — Container for the data for a certain team.
    - UserId : unsigned Integer — The globally unique UserID.
    - TeamID : unsigned Integer — The globally unique team indentifier.
    - Position : unsigned Integer — Position in the table.
    - PositionChange : positionChange — How the team is moving in the league.
    - TeamName : String — The team's full name.
    - Matches : unsigned Integer — How many matches the team has played.
    - GoalsFor : unsigned Integer — How many goals the team has scored.
    - GoalsAgainst : unsigned Integer — How many goals the team has allowed their opponents to score.
    - Points : unsigned Integer — How many points the team has.
    - Won : unsigned Integer — How many matches the team has won.
    - Draws : unsigned Integer — How many draws the team has.
    - Lost : unsigned Integer — How many matches the team has lost.
