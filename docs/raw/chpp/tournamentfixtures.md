# tournamentfixtures

Information about matches for a tournament. This is only available for the current season. For non-restarted tournaments this is available for 2 seasons after the tournament finished.

## Changelog
### Version 1.1 (2025-09-17)
- Added Input: matchRound

### Version 1.0 (2013-10-17) {default}
- First version

## Input
Only available inputs in this file version are shown.
required file = tournamentfixtures
optional version = 
1.1
1.0
required tournamentId : unsigned Integer
The id for the tournament to get data for.
optional season : unsigned Integer
The starting season of the tournament. This is available only for National Teams cups.
optional matchRound : unsigned Integer
Specific match round for this tournament. It will pick the last match round if the selected round is greater than the tournament’s last round.

## Output
- **HattrickData** — Hattrick Data Container
  - **Matches** — Container for the list of matches.
    - **Match** — Container for the data about the match
      - MatchId : unsigned Integer — The globally unique match id for tournament, ladder and single matches.
      - HomeTeamId : unsigned Integer — The teamID of the Home team in the match.
      - HomeTeamName : String — The team name of the Home team in the match.
      - HomeShortTeamName : String — The short team name of the Home team in the match.
      - AwayTeamId : unsigned Integer — The teamID of the Away team in the match
      - AwayTeamName : String — The team name of the Away team in the match.
      - AwayShortTeamName : String — The short team name of the Away team in the match.
      - MatchDate : DateTime — The start date and time (kick-off) of the match.
      - MatchType : MatchTypeID — Integer defining the type of match.
      - MatchRound : unsigned Integer — The matchround.
      - Group : unsigned Integer — The group that the game belongs to. If 0 it means this is a playoff match. Use the order of those matches is provided in to display the upcoming playoff rounds (winners of the first match in the list will face the winner of the second match in the list, and so on. The winner of the first match will be the home team).
      - Status : MatchStatus — Current status of the match (not started, ongoing or played).
      - HomeGoals : unsigned Integer — The current number of goals in the match for the home team. This will be 0 until the match is finished.
      - AwayGoals : unsigned Integer — The current number of goals in the match for the away team. This will be 0 until the match is finished.
      - HomeStatement : String — The home team statement.
      - AwayStatement : String — The away team statement.
