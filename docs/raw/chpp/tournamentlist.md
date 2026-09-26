# tournamentlist

Information about tournaments that the user is currently playing in.

## Changelog
### Version 1.0 (2013-10-17) {default}
- First version

## Input
Only available inputs in this file version are shown.
required file = tournamentlist
optional version = 
1.0
optional teamId : unsigned Integer{Default:Your primary club senior teamId}
Team id to get data for.

## Output
- **HattrickData** — Hattrick Data Container
  - **Tournaments** — Container for the list of tournaments.
    - **Tournament** — Container for the data about the tournament.
      - TournamentId : unsigned Integer — The globally unique id for the tournament.
      - Name : String — The name.
      - TournamentType : TournamentType — The type.
      - Season : unsigned Integer — Current season.
      - LogoUrl : URI — The url to the logo.
      - TrophyType : unsigned Integer — The type of trophy.
      - NumberOfTeams : unsigned Integer — The total number of teams.
      - NumberOfGroups : unsigned Integer — The number of groups.
      - LastMatchRound : unsigned Integer — Last finished match round.
      - FirstMatchRoundDate : DateTime — The date of the first match round.
      - NextMatchRoundDate : DateTime — The date of the next match round. During matches this will be the Next match round.
      - IsMatchesOngoing : Bit — Is matches currently being played.
      - **Creator** — The user who created the tournament
        - UserId : unsigned Integer — The globally unique user id
        - Loginname : String — The 'username' or 'nickname' used in Forums and for login.
