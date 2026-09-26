# nationalteammatches

National team matches

## Changelog
### Version 1.4 (2022-03-03)
- Add teamId to national team matches.
- Fixed the matchType for national team matches.
- Added Output: HattrickData → Matches → Match → HomeTeamId (Element)
- Added Output: HattrickData → Matches → Match → AwayTeamId (Element)

### Version 1.3 (2013-03-04)
- Removed Output: HattrickData → UserIsSupporter (Element)
- Removed Output: HattrickData → UserHasSupporter (Element)
- Added Output: HattrickData → UserSupporterTier (Element)

### Version 1.2 {default}
- First version

## Input
Only available inputs in this file version are shown.
required file = nationalteammatches
optional version = 
1.4
1.3
1.2
optional LeagueOfficeTypeID : unsigned Integer{Default:2}
What national team type to list (2 = National teams, 4 = U-20 Teams).

## Output
- **HattrickData** — Hattrick Data Container
  - UserSupporterTier : supporterTier — Indicates which Supporter package the fetching user has, or empty if not a Supporter.
  - LeagueOfficeTypeID : unsigned Integer — The requested LeagueOfficeTypeID.
  - **Matches** — Container for the matches.
    - **Match** — A match.
      - MatchID : unsigned Integer — The globally unique identifier of the match.
      - MatchDate : DateTime — The start date (kick-off) of the match.
      - MatchType : MatchTypeID — Integer defining the type of match.
      - MatchContextId : unsigned Integer — This will be either LeagueLevelUnitId (for League), CupId (Cup, Hattrick Masters, World Cup and U-20 World Cup), LadderId, TournamentId, or 0 for friendly, qualification, single matches and preparation matches.
      - HomeTeamId : unsigned Integer — The teamID of the Home team in the match.
      - HomeTeamName : String — The team name of the Home team in the match.
      - AwayTeamId : unsigned Integer — The teamID of the Away team in the match.
      - AwayTeamName : String — The team name of the Away team in the match.
      - **MatchResult** — Container for the match results. The container is empty if the match result is not available.
        - HomeGoals : unsigned Integer — The number of goals for the home team.
        - AwayGoals : unsigned Integer — The number of goals for the away team.
