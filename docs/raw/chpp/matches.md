# matches

The most recent and upcoming matches for a particular team (Senior or Youth).

## Changelog
### Version 2.9 (2022-03-03)
- Added LeagueId to youthMatches.

### Version 2.8 (2015-02-09)
- Added CupLevel & CupLevelIndex.
- Added Output: HattrickData → Team → MatchList → Match → CupLevel (Element)
- Added Output: HattrickData → Team → MatchList → Match → CupLevelIndex (Element)

### Version 2.7 (2013-11-20)
- Added MatchContextID of that. There is from 1 to 9 types.
- Added Output: HattrickData → Team → MatchList → Match → MatchContextId (Element)

### Version 2.6

### Version 2.5
- Added Output: HattrickData → Team → MatchList → Match → SourceSystem (Element)

### Version 2.4
- Added Output: HattrickData → Team → ShortTeamName (Element)
- Added Output: HattrickData → Team → League (Container)
- Added Output: HattrickData → Team → League → LeagueID (Element)
- Added Output: HattrickData → Team → League → LeagueName (Element)
- Added Output: HattrickData → Team → League → LeagueLevelUnit (Container)
- Added Output: HattrickData → Team → League → LeagueLevelUnit → LeagueLevelUnitID (Element)
- Added Output: HattrickData → Team → League → LeagueLevelUnit → LeagueLevelUnitName (Element)
- Added Output: HattrickData → Team → League → LeagueLevelUnit → LeagueLevel (Element)

### Version 2.3 {default}
- First version

## Input
Only available inputs in this file version are shown.
required file = matches
optional version = 
2.9
2.8
2.7
2.6
2.5
2.4
2.3
optional teamID : unsigned Integer{Default:Your senior or youth teamID}
What team to show the matches for: Either teamID or youthTeamID depending on isYouth.
optional isYouth : Boolean{Default:false}
Is request for youth or not.
optional LastMatchDate : DateTime{Default:Datetime.Now + 28 days}
Last date to show matches to. If more than 50 matches are affected, only the 50 first will be returned.

## Output
- **HattrickData** — Hattrick Data Container
  - **Team** — The team that the list applies to.
    - TeamID : unsigned Integer — The team's TeamID.
    - TeamName : String — The full name of the team.
    - ShortTeamName : String — The short team name. Only available when isYouth is false.
    - **League** — Container for the data about the league ('Sverige', 'England' and so on) the team is part of.
      - LeagueID : unsigned Integer — The globally unique LeagueID.
      - LeagueName : String — The league name.
      - **LeagueLevelUnit** — Data about the team's LeagueLevelUnit ('series'). If the team is playing a qualification match the tag will be empty. Only available when isYouth is false.
        - LeagueLevelUnitID : unsigned Integer — The globally unique LeagueLevelUnitID.
        - LeagueLevelUnitName : String — The name of the LeagueLevelUnit.
        - LeagueLevel : unsigned Integer — Integer value stating the relative level of the LeagueLevelUnit, with 1 indicating the league's top series.
    - **MatchList** — Container for the matches.
      - **Match** — A match.
        - MatchID : unsigned Integer — The globally unique identifier of the match.
        - **HomeTeam** — The Home (formal, not taking arena location into account) team of the match.
          - HomeTeamID : Integer — The teamID of the Home team in the match. (Negative for street teams)
          - HomeTeamName : String — The team name of the Home team in the match.
          - HomeTeamShortName : String — The short team name of the Home team in the match.
        - **AwayTeam** — The Away (formal, not taking arena location into account) team of the match.
          - AwayTeamID : Integer — The teamID of the Away team in the match. (Negative for street teams)
          - AwayTeamName : String — The team name of the Away team in the match.
          - AwayTeamShortName : String — The short team name of the Away team in the match.
        - MatchDate : DateTime — The start date and time (kick-off) of the match.
        - SourceSystem : sourceSystem — SourceSytem tells from witch system the match is ex: hattrick, youth, htointegrated
        - MatchType : MatchTypeID — Integer defining the type of match.
        - MatchContextId : unsigned Integer — This will be either LeagueLevelUnitId (for League), CupId (Cup, Hattrick Masters, World Cup and U-20 World Cup), LadderId, TournamentId, or 0 for friendly, qualification, single matches and preparation matches.
        - CupLevel : unsigned Integer — 1 = National/Divisional cup, 2 = Challenger cup, 3 = Consolation cup. 0 if MatchType is not 3.
        - CupLevelIndex : unsigned Integer — In Challenger cups: 1 = Emerald (start week 2), 2 = Ruby (start week 3), 3 = Sapphire (start week 4). Always 1 for National/Divisional (main cups) and Consolation cups. 0 if MatchType is not 3.
        - HomeGoals : unsigned Integer — The current number of goals in the match for the home team.
        - AwayGoals : unsigned Integer — The current number of goals in the match for the away team.
        - Status : MatchStatus — Specifying whether the match is FINISHED, ONGOING or UPCOMING.
        - OrdersGiven : Boolean* — A boolean value only supplied for upcoming matches (haven't started yet) of your own team that signifies whether you have given orders or not. If the request is for another team than your own (even if it is for your opponent), this data is not sent.
