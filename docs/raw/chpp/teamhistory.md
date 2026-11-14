# teamhistory

A club's history, including its per-season league/cup results and its trophy history.

## Changelog
### Version 1.0 (2026-11-10) {default}
- First version

## Input
Only available inputs in this file version are shown.
required file = teamhistory
optional version = 
1.0
optional teamId : unsigned Integer{Default:Your primary club senior teamId}
The id of the team to fetch the history for. Defaults to the requesting user's senior team.

## Output
- **HattrickData** — Hattrick Data Container
  - **Team** — Container for the data about the team.
    - TeamID : unsigned Integer — The TeamID.
    - TeamName : String — The name of the team.
    - **League** — Container for the league the team belongs to.
      - LeagueID : unsigned Integer — The LeagueID.
      - LeagueName : String — The league name.
    - FoundedDate : DateTime — The date the team was founded for its current owner.
    - **TeamHistory** — Container for the team's per-season history, oldest season first. Only the seasons since the current owner took over the team are listed.
      - **Season** — Attribute: 'Index' : unsigned Integer The zero-based index of the season entry in the list. Container for a single season's result.
        - SeasonNumber : unsigned Integer — The season the entry refers to, in the local season numbering of the team's league.
        - LeagueLevelUnitID : unsigned Integer — The LeagueLevelUnitID (series) the team played in that season.
        - LeagueLevelUnitName : String — The name of the series the team played in that season.
        - LeagueLevel : unsigned Integer — The league level (division) of the series that season.
        - Position : unsigned Integer — The team's final position in the series that season.
        - Points : unsigned Integer — The team's league points that season.
        - GoalsFor : unsigned Integer — The number of goals the team scored that season.
        - GoalsAgainst : unsigned Integer — The number of goals the team conceded that season.
        - CupRound : Integer — The national cup round the team reached that season. If the team won the cup, this is the last round. -1 when the team has no national cup result that season.
        - CupRoundsLeft : Integer — The number of national cup rounds that were left when the team was knocked out that season, counting the round it lost. 0 means the team won the cup. -1 when the team has no national cup result that season.
    - **TrophyList** — Container for the trophies the team has won since the current owner took over the team. Empty if there are none.
      - **Trophy** — Container for the trophy.
        - TrophyTypeId : trophyID — TypeId for the trophy
        - TrophySeason : unsigned Integer — The season the trophy was won.
        - LeagueLevel : unsigned Integer — Depends on the trophy type. For series titles (17) it is the division level. For cup titles (16) it is the same value as CupLeagueLevel.
        - LeagueLevelUnitId : unsigned Integer — The id of the competition: the series id for series titles (17), the cup id for cup titles (16) and the tournament id for tournaments.
        - LeagueLevelUnitName : String — The name of the series, cup or tournament, when the trophy stores one.
        - GainedDate : DateTime — The date the team won the trophy.
        - ImageUrl : URI — The url to the trophy image.
        - CupLeagueLevel : unsigned Integer — The league level of the cup. 0 for the main cups, division 1-6. Empty when not a cup trophy.
        - CupLevel : CupLevel — The level of the cup. Empty when not a cup trophy.
        - CupLevelIndex : CupLevelIndex — Which type of challenger cup. Always 1 for national and consolation cups. Empty when not a cup trophy.
