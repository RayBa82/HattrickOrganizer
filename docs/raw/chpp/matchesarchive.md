# matchesarchive

Matches Archive

## Changelog
### Version 1.5 (2022-03-03)
- Added sourceSystem name and CupId

### Version 1.4 (2018-04-03)
- Added tournament, ladder, preparation and single matches to the list, and MatchRuleId to the output.
- Added Input: includeHTO
- Added Output: HattrickData → Team → MatchList → Match → SourceSystem (Element)
- Added Output: HattrickData → Team → MatchList → Match → MatchRuleId (Element)
- Added Output: HattrickData → Team → MatchList → Match → CupId (Element)

### Version 1.3 (2015-02-09)
- Added CupLevel & CupLevelIndex.
- Added Output: HattrickData → Team → MatchList → Match → CupLevel (Element)
- Added Output: HattrickData → Team → MatchList → Match → CupLevelIndex (Element)

### Version 1.2 (2013-11-21)
- Added MatchContextID of that. There is from 1 to 9 types.
- Added Output: HattrickData → Team → MatchList → Match → MatchContextId (Element)

### Version 1.1 {default}
- First version

## Input
Only available inputs in this file version are shown.
required file = matchesarchive
optional version = 
1.5
1.4
1.3
1.2
1.1
optional teamID : unsigned Integer{Default:Your senior or youth teamID}
What team to show the matches for: Either teamID or youthTeamID depending on isYouth.
optional isYouth : Boolean{Default:false}
Is request for youth or not.
optional_1 FirstMatchDate : DateTime{Default:Datetime.Now - 3 months}
First date to show matches in archive from.
optional_1 LastMatchDate : DateTime{Default:Datetime.Now - 1 day}
Last date to show matches to. For performance reasons you may only specify an interval of 2 seasons back in time. If you specify a larger interval we'll automatically adjust it to the default which is: firstMatchDate = DateTime.Now.AddMonths(-3).Date lastMatchDate = DateTime.Now.Date
optional_1 season : unsigned Integer
Season to show matches for. (Only valid for senior teams, not for youth!)
optional includeHTO : Boolean{Default:false}
If HTO matches (tournaments, ladders, preparation, and single matches) should be included.
optional_1: You can set FirstMatchDate and LastMatchDate or season (which overrides FirstMatchDate and LastMatchDate when set)!

## Output
- **HattrickData** — Hattrick Data Container
  - **Team** — The team that the list applies to.
    - TeamID : unsigned Integer — The team's TeamID.
    - TeamName : String — The full name of the team.
    - FirstMatchDate : DateTime — First date to show matches in archive from.
    - LastMatchDate : DateTime — Last date to show matches in archive to.
    - **MatchList** — Container for the matches. If more than 50 matches have occured between FirstMatchDate and LastMatchDate, only the 50 first will be returned.
      - **Match** — A match.
        - MatchID : unsigned Integer — The globally unique identifier of the match.
        - **HomeTeam** — The Home (formal, not taking arena location into account) team of the match.
          - HomeTeamID : Integer — The teamID of the Home team in the match. (Negative for street teams)
          - HomeTeamName : String — The team name of the Home team in the match.
        - **AwayTeam** — The Away (formal, not taking arena location into account) team of the match.
          - AwayTeamID : Integer — The teamID of the Away team in the match. (Negative for street teams)
          - AwayTeamName : String — The team name of the Away team in the match.
        - MatchDate : DateTime — The start date and time (kick-off) of the match.
        - MatchType : MatchTypeID — Integer defining the type of match.
        - MatchContextId : unsigned Integer — This will be either LeagueLevelUnitId (for League), CupId (Cup, Hattrick Masters, World Cup and U-20 World Cup) or 0 for friendly and qualification
        - SourceSystem : String — The name of the source system that playes the match
        - MatchRuleId : MatchRuleID — This will contain the rules that's in place for the ladder or tournament in question. It's provided for all matchtypes and defaults to 0 = No rules
        - CupId : unsigned Integer — Integer defining the id of the cup.
        - CupLevel : unsigned Integer — 1 = National/Divisional cup, 2 = Challenger cup, 3 = Consolation cup. 0 if MatchType is not 3.
        - CupLevelIndex : unsigned Integer — In Challenger cups: 1 = Emerald (start week 2), 2 = Ruby (start week 3), 3 = Sapphire (start week 4). Always 1 for National/Divisional (main cups) and Consolation cups. 0 if MatchType is not 3.
        - HomeGoals : unsigned Integer* — The current number of goals in the match for the home team. If the match is still upcoming or ongoing, this tag is not sent.
        - AwayGoals : unsigned Integer* — The current number of goals in the match for the away team. If the match is still upcoming or ongoing, this tag is not sent.
