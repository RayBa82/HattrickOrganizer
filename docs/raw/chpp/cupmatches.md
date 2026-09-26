# cupmatches

Information about cup matches

## Changelog
### Version 1.4 (2011-06-03)
- Removed Output: HattrickData → Cup → Match (Container)
- Removed Output: HattrickData → Cup → Match → MatchID (Element)
- Removed Output: HattrickData → Cup → Match → MatchDate (Element)
- Removed Output: HattrickData → Cup → Match → HomeTeam (Container)
- Removed Output: HattrickData → Cup → Match → HomeTeam → TeamId (Element)
- Removed Output: HattrickData → Cup → Match → HomeTeam → TeamName (Element)
- Removed Output: HattrickData → Cup → Match → AwayTeam (Container)
- Removed Output: HattrickData → Cup → Match → AwayTeam → TeamId (Element)
- Removed Output: HattrickData → Cup → Match → AwayTeam → TeamName (Element)
- Removed Output: HattrickData → Cup → Match → MatchResult (Container)
- Removed Output: HattrickData → Cup → Match → MatchResult → HomeGoals (Element)
- Removed Output: HattrickData → Cup → Match → MatchResult → AwayGoals (Element)
- Removed Output: HattrickData → Cup → Match → LeagueInfo (Container)
- Removed Output: HattrickData → Cup → Match → LeagueInfo → HomeLeagueID (Element)
- Removed Output: HattrickData → Cup → Match → LeagueInfo → AwayLeagueID (Element)
- Removed Output: HattrickData → Cup → Match → LeagueInfo → HomeLeagueName (Element)
- Removed Output: HattrickData → Cup → Match → LeagueInfo → AwayLeagueName (Element)
- Added Output: HattrickData → Cup → MatchList (Container)
- Added Output: HattrickData → Cup → MatchList → Match (Container)
- Added Output: HattrickData → Cup → MatchList → Match → MatchID (Element)
- Added Output: HattrickData → Cup → MatchList → Match → MatchDate (Element)
- Added Output: HattrickData → Cup → MatchList → Match → HomeTeam (Container)
- Added Output: HattrickData → Cup → MatchList → Match → HomeTeam → TeamId (Element)
- Added Output: HattrickData → Cup → MatchList → Match → HomeTeam → TeamName (Element)
- Added Output: HattrickData → Cup → MatchList → Match → AwayTeam (Container)
- Added Output: HattrickData → Cup → MatchList → Match → AwayTeam → TeamId (Element)
- Added Output: HattrickData → Cup → MatchList → Match → AwayTeam → TeamName (Element)
- Added Output: HattrickData → Cup → MatchList → Match → MatchResult (Container)
- Added Output: HattrickData → Cup → MatchList → Match → MatchResult → HomeGoals (Element)
- Added Output: HattrickData → Cup → MatchList → Match → MatchResult → AwayGoals (Element)
- Added Output: HattrickData → Cup → MatchList → Match → LeagueInfo (Container)
- Added Output: HattrickData → Cup → MatchList → Match → LeagueInfo → HomeLeagueID (Element)
- Added Output: HattrickData → Cup → MatchList → Match → LeagueInfo → AwayLeagueID (Element)
- Added Output: HattrickData → Cup → MatchList → Match → LeagueInfo → HomeLeagueName (Element)
- Added Output: HattrickData → Cup → MatchList → Match → LeagueInfo → AwayLeagueName (Element)

### Version 1.3 (2011-02-01)
- Removed Output: HattrickData → Cup → Match → HomeTeamName (Element)
- Removed Output: HattrickData → Cup → Match → AwayTeamName (Element)
- Added Output: HattrickData → Cup → Match → HomeTeam (Container)
- Added Output: HattrickData → Cup → Match → HomeTeam → TeamId (Element)
- Added Output: HattrickData → Cup → Match → HomeTeam → TeamName (Element)
- Added Output: HattrickData → Cup → Match → AwayTeam (Container)
- Added Output: HattrickData → Cup → Match → AwayTeam → TeamId (Element)
- Added Output: HattrickData → Cup → Match → AwayTeam → TeamName (Element)

### Version 1.2 {default}
- First version

## Input
Only available inputs in this file version are shown.
required file = cupmatches
optional version = 
1.4
1.3
1.2
required CupID : CupID
What cup to show the matches for.
optional Season : unsigned Integer{Default:Latest Season}
What season to show the matches for.
optional CupRound : unsigned Integer{Default:Latest CupRound}
What cupround to show the matches for.
optional StartAfterMatchID : unsigned Integer{Default:0}
For large cups in the first round, there will be more than 256 matches to display. To be able to fetch the next 256 matches, take the last MatchID you received from the XML-output. Send it as StartAfterMatchID=. This will return the next 256 matches with MatchID > StartAfterMatchID.

## Output
- **HattrickData** — Hattrick Data Container
  - **Cup** — Container for the cup and the matches.
    - CupID : CupID — The global unique CupID.
    - CupSeason : unsigned Integer — The season of the matches.
    - CupRound : unsigned Integer — The cupround of the matches.
    - CupName : String — The name of the cup.
    - **MatchList** — Container for matches.
      - **Match** — A match.
        - MatchID : unsigned Integer — The globally unique identifier of the match.
        - MatchDate : DateTime — The start date (kick-off) of the match.
        - **HomeTeam** — Container for the home team in the match.
          - TeamId : unsigned Integer — The hometeam's TeamId.
          - TeamName : String — The full name of the hometeam.
        - **AwayTeam** — Container for the away team in the match.
          - TeamId : unsigned Integer — The awayteam's TeamId.
          - TeamName : String — The full name of the awayteam.
        - **MatchResult** — Attribute: 'Available' : Boolean Specifies whether or not the match result is available.
          - HomeGoals : unsigned Integer — The number of goals for the home team.
          - AwayGoals : unsigned Integer — The number of goals for the away team.
        - **LeagueInfo** — Attribute: 'Available' : Boolean Specifies whether or not the league information is available. It is only available for international cups.
          - HomeLeagueID : unsigned Integer — The LeagueID of the home team in the match.
          - AwayLeagueID : unsigned Integer — The LeagueID of the away team in the match.
          - HomeLeagueName : String — The league name of the home team in the match.
          - AwayLeagueName : String — The league name of the away team in the match.
