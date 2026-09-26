# matchlineup

Match lineup for finished matches

## Changelog
### Version 2.1 (2022-03-03)
- Replaced isYouth with sourceSystem in output.
- Removed Output: HattrickData → IsYouth (Element)
- Added Output: HattrickData → SourceSystem (Element)

### Version 2.0 (2016-08-02)
- Added MatchPart.
- Added Output: HattrickData → Team → Substitutions → Substitution → MatchPart (Element)

### Version 1.9 (2015-10-01)
- Added Output: HattrickData → Team → StyleOfPlay (Element)

### Version 1.8 (2012-03-06)
- Removed Input: isYouth
- Added Input: sourceSystem

### Version 1.7 (2012-02-13)
- Removed Output: HattrickData → Team → StartingLineup → Player → PlayerName (Element)
- Removed Output: HattrickData → Team → Lineup → Player → PlayerName (Element)
- Added Output: HattrickData → Team → StartingLineup → Player → FirstName (Element)
- Added Output: HattrickData → Team → StartingLineup → Player → LastName (Element)
- Added Output: HattrickData → Team → StartingLineup → Player → NickName (Element)
- Added Output: HattrickData → Team → Lineup → Player → FirstName (Element)
- Added Output: HattrickData → Team → Lineup → Player → LastName (Element)
- Added Output: HattrickData → Team → Lineup → Player → NickName (Element)

### Version 1.6 (2011-09-20)
- Added Output: HattrickData → Team → StartingLineup (Container)
- Added Output: HattrickData → Team → StartingLineup → Player (Container)
- Added Output: HattrickData → Team → StartingLineup → Player → PlayerID (Element)
- Added Output: HattrickData → Team → StartingLineup → Player → RoleID (Element)
- Added Output: HattrickData → Team → StartingLineup → Player → PlayerName (Element)
- Added Output: HattrickData → Team → StartingLineup → Player → Behaviour (Element)
- Added Output: HattrickData → Team → Substitutions (Container)
- Added Output: HattrickData → Team → Substitutions → Substitution (Container)
- Added Output: HattrickData → Team → Substitutions → Substitution → TeamID (Element)
- Added Output: HattrickData → Team → Substitutions → Substitution → SubjectPlayerID (Element)
- Added Output: HattrickData → Team → Substitutions → Substitution → ObjectPlayerID (Element)
- Added Output: HattrickData → Team → Substitutions → Substitution → OrderType (Element)
- Added Output: HattrickData → Team → Substitutions → Substitution → NewPositionId (Element)
- Added Output: HattrickData → Team → Substitutions → Substitution → NewPositionBehaviour (Element)
- Added Output: HattrickData → Team → Substitutions → Substitution → MatchMinute (Element)

### Version 1.5 (2011-09-12)
- Removed Output: HattrickData → Team → Lineup → Player → RoleID (Element)
- Removed Output: HattrickData → Team → Lineup → Player → PositionCode (Element)
- Added Output: HattrickData → Team → Lineup → Player → RoleID (Element)

### Version 1.3
- Added Output: HattrickData → Team → Lineup → Player → RatingStarsEndOfMatch (Element)

### Version 1.2 {default}
- First version

## Input
Only available inputs in this file version are shown.
required file = matchlineup
optional version = 
2.1
2.0
1.9
1.8
1.7
1.6
1.5
1.3
1.2
required matchID : unsigned Integer{Default:last match}
Specifies which particular match data is requested for.
required teamID : unsigned Integer{Default:Logged on user's team}
Specifies which of the two participating teams data should be shown for.
optional sourceSystem : sourceSystem{Default:Hattrick}
Specifies which source system the match belongs to, this replace the old isYouth param

## Output
- **HattrickData** — Hattrick Data Container for matchEvents=true
  - MatchID : unsigned Integer — The globally unique identifier of the match.
  - SourceSystem : sourceSystem — Specifies which source system the match belongs to, this replace the old isYouth param
  - **HomeTeam** — The Home (formal, not taking arena location into account) team of the match.
    - HomeTeamID : Integer — The teamID of the Home team in the match. (Negative for street teams)
    - HomeTeamName : String — The team name of the Home team in the match.
  - **AwayTeam** — The Away (formal, not taking arena location into account) team of the match.
    - AwayTeamID : Integer — The teamID of the Away team in the match. (Negative for street teams)
    - AwayTeamName : String — The team name of the Away team in the match.
  - MatchType : MatchTypeID — Integer defining the type of match.
  - MatchContextId : unsigned Integer — This will be either LeagueLevelUnitId (for League), CupId (Cup, Hattrick Masters, World Cup and U-20 World Cup), LadderId, TournamentId, or 0 for friendly, qualification, single matches and preparation matches.
  - **Arena** — Container for the data about the arena where the match was played.
    - ArenaID : unsigned Integer — The globally unique arenaID.
    - ArenaName : String — The name of the arena.
  - **Team** — Container for the data for the requested team.
    - TeamID : unsigned Integer — The globally unique teamID.
    - TeamName : String — The team's full name.
    - ExperienceLevel : SkillLevel — The aggregated experience level of the team.
    - StyleOfPlay : coachModifier — The style of play used in the match. Value will always be 0 for youth matches.
    - **StartingLineup** — List of players in the hometeam who started the game
      - **Player** — Container for a particular player that is part of the starting lineup.
        - PlayerID : unsigned Integer — Globally unique ID of the player.
        - RoleID : MatchRoleID — An integer indicating which formal 'slot' (Role) the player has filled in the match.
        - FirstName : String — The first name of the player.
        - LastName : String — The last name of the player.
        - NickName : String — The nick name of the player.
        - Behaviour : MatchBehaviourID — An integer indicating the individual order or repositioning that the player has played with. Not provided for Captain and Set Pieces taker.
    - **Substitutions** — Container for the substitutions in the game.
      - **Substitution** — Container for a particular substitution.
        - TeamID : unsigned Integer — The globally unique TeamID for which the orders were sent.
        - SubjectPlayerID : unsigned Integer — If a/ substitution: The player leaving. If b/ behaviour change: The player changing his behaviour. Or c/ position swap: the first player that will change his position.
        - ObjectPlayerID : unsigned Integer — The player entering (or once more the player changing behaviour). Or if position swap, the player to swap with.
        - OrderType : matchOrderTypeID — The type of the order.
        - NewPositionId : MatchRoleID — An integer representing the new position for the (entering or staying) player.
        - NewPositionBehaviour : MatchBehaviourID — An integer representing the new behaviour for the (entering or staying) player.
        - MatchMinute : unsigned Integer — An integer indicating in which minute the order was executed.
        - MatchPart : matchPart — Which part of the match the order was executed.
    - **Lineup** — Container for the lineup of the requested team.
      - **Player** — Container for a particular player that is part of the lineup.
        - PlayerID : unsigned Integer — The globally unique playerID.
        - RoleID : MatchRoleID — An integer indicating which formal 'slot' (Role) the player has filled in the match. All games are 553 based.
        - FirstName : String — The first name of the player.
        - LastName : String — The last name of the player.
        - NickName : String — The nick name of the player.
        - RatingStars : Float — The number of stars the player is rated for.
        - RatingStarsEndOfMatch : Float — The number of stars the player is rated for at the end of the game. This element is not provided for youth.
        - Behaviour : MatchBehaviourID — An integer indicating the individual order or repositioning that the player has played with.
