# worldcup

World cup groups and matches

## Changelog
### Version 1.1 {default}
- First version

## Input
Only available inputs in this file version are shown.
required file = worldcup
optional version = 
1.1
optional actionType = 
viewMatches {default}
viewGroups
Indicates what type of action the page should perform.
viewMatches {default}
View a list of the matches of a particular group.
optional matchRound : unsigned Integer{Default:last initialized round of the chosen cup}
A key that indicates a certain round (which typically runs for either 10 weeks or 9 days). Qualification are always matchRound 1. You will retrieve a list of all available matchRounds for a certain cup in the output. Will default to the last initialized round of the chosen cup, if not supplied.
required cupSeriesUnitID : unsigned Integer
The globally unique identifier of the CupSeriesUnit (a 'group' of 4-6 teams).
viewGroups
View a list of the groups (with points, place etc) of a particular round, season and cup.
optional cupID : WorldCupID{Default:137 (World Cup)}
The globally unique cupID. Will default to 137 if not supplied.
required season : unsigned Integer
The starting season of the cup. For instance, World Cup V which started with qualifications 2004-03-19, started global season 22, so the season of World Cup V is 22, even while it will continue onto global season 23.

## Output (actionType=viewMatches)
- **HattrickData** — Container for the data about the matches of a group.
  - **Matches** — Container for matches
    - **Match** — Container for a match.
      - MatchID : unsigned Integer — The globally unique matchID.
      - **HomeTeam** — Container for the home team.
        - TeamID : unsigned Integer — The globally unique home team nationalTeamID.
        - TeamName : String — The home team name.
      - **AwayTeam** — Container for the away team.
        - TeamID : unsigned Integer — The globally unique away team nationalTeamID.
        - TeamName : String — The away team name.
      - MatchDate : DateTime — The starting date of the match.
      - FinishedDate : DateTime — The ending date of the match. Empty if the match is not finished.
      - HomeGoals : unsigned Integer* — The number of goals for the home team. Empty if the match is not finished.
      - AwayGoals : unsigned Integer* — The number of goals for the away team. Empty if the match is not finished.
  - **Rounds** — Container for the listing of rounds. Qualifications is a round. Round II (the set of 3 matches played between the 32 teams that advance from the qualification) is another round. Semifinals is a round. Final is a round, and so on.
    - **Round** — Container for a round. Attribute Index exists.
      - MatchRound : unsigned Integer — An integer used to identify the round among other rounds of the same cup.
      - StartDate : DateTime — The first match date of the round.

## Output (actionType=viewGroups)
- **HattrickData** — Hattrick Data Container for actionType=viewGroups
  - **WorldCupScores** — Container for teams in the group.
    - **Team** — Container for a team in the group.
      - TeamID : unsigned Integer — The globally unique nationalTeamID.
      - TeamName : String — The team name.
      - Place : unsigned Integer — The position of the team within the group (1 for the leader and so on).
      - CupSeriesUnitID : unsigned Integer — The globally unique cupSeriesUnitID of the Group that the team is part of.
      - CupSeriesUnitName : String — The name of the group.
      - MatchesPlayed : unsigned Integer — The number of matches that the team has played in the group.
      - GoalsFor : unsigned Integer — The number of goals that the team has scored.
      - GoalsAgainst : unsigned Integer — The number of goals that the team has allowed.
      - Points : unsigned Integer — The number of points for the team.
  - **Rounds** — Container for the listing of rounds. Qualifications is a round. Round II (the set of 3 matches played between the 32 teams that advance from the qualification) is another round. Semifinals is a round. Final is a round, and so on.
    - **Round** — Container for a round. Attribute Index exists.
      - MatchRound : unsigned Integer — An integer used to identify the round among other rounds of the same cup.
      - StartDate : DateTime — The first match date of the round.
