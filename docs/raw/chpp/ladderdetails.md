# ladderdetails

Information about teams in the ladder and posistions in it

## Changelog
### Version 1.0 (2013-11-20) {default}
- First version

## Input
Only available inputs in this file version are shown.
required file = ladderdetails
optional version = 
1.0
required_1 ladderid : unsigned Integer
Ladder id to get data for.
optional teamid : unsigned Integer{Default:Your primary club senior teamId}
If pageindex is -1, the page where this team is positioned will be shown.
optional pagesize : unsigned Integer{Default:25}
How many results to be shown in each page, the maximum value is 100.
optional pageindex : unsigned Integer{Default:-1}
What page to be shown, starts from 0.
required_1: ladderID is required!

## Output
- **HattrickData** — Hattrick Data Container
  - **Ladder** — Container for the data about the ladder.
    - LadderId : unsigned Integer — The globally unique id for the ladder.
    - Name : String — The name of the ladder.
    - NumOfTeams : unsigned Integer — How many teams there is in the ladder.
    - PageSize : unsigned Integer — How many results are being shown.
    - PageIndex : unsigned Integer — What page is being shown.
    - KingTeamId : unsigned Integer — The team id of the leader.
    - KingTeamName : String — The teamname of the leader.
    - KingSince : DateTime — Since when has the team been the king.
    - **Team** — Container for the data about the team.
      - TeamId : unsigned Integer — The globally unique id for the team.
      - TeamName : String — The full team name.
      - Position : unsigned Integer — The position in the ladder.
      - Wins : unsigned Integer — Total numbers of wins.
      - Lost : unsigned Integer — Total number of losses.
      - WinsInARow : unsigned Integer — Number of wins in a row.
      - LostInARow : unsigned Integer — Number of losses in a row.
