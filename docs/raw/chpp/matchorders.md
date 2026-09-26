# matchorders

Match orders for upcoming matches

## Changelog
### Version 3.1 (2022-03-03)
- Replaced isYouth with sourcesystem in the output.
- Added support for man marking on setmatchorder.
- Removed Output: HattrickData → IsYouth (Element)
- Added Output: HattrickData → sourceSystem (Element)

### Version 3.0 (2017-05-18)
- Changed structure of input lineup object to support a bigger bench.
- Changed structure of output to support a bigger bench.
- Removed Input: lineup
- Added Input: lineup
- Removed Output: HattrickData → MatchData → Lineup (Container)
- Removed Output: HattrickData → MatchData → Lineup → Player (Container)
- Removed Output: HattrickData → MatchData → Lineup → Player → PlayerID (Element)
- Removed Output: HattrickData → MatchData → Lineup → Player → RoleID (Element)
- Removed Output: HattrickData → MatchData → Lineup → Player → FirstName (Element)
- Removed Output: HattrickData → MatchData → Lineup → Player → NickName (Element)
- Removed Output: HattrickData → MatchData → Lineup → Player → LastName (Element)
- Removed Output: HattrickData → MatchData → Lineup → Player → Behaviour (Element)
- Added Output: HattrickData → MatchData → Lineup (Container)
- Added Output: HattrickData → MatchData → Lineup → Positions (Container)
- Added Output: HattrickData → MatchData → Lineup → Positions → Player (Container)
- Added Output: HattrickData → MatchData → Lineup → Positions → Player → PlayerID (Element)
- Added Output: HattrickData → MatchData → Lineup → Positions → Player → RoleID (Element)
- Added Output: HattrickData → MatchData → Lineup → Positions → Player → FirstName (Element)
- Added Output: HattrickData → MatchData → Lineup → Positions → Player → NickName (Element)
- Added Output: HattrickData → MatchData → Lineup → Positions → Player → LastName (Element)
- Added Output: HattrickData → MatchData → Lineup → Positions → Player → Behaviour (Element)
- Added Output: HattrickData → MatchData → Lineup → Bench (Container)
- Added Output: HattrickData → MatchData → Lineup → Bench → Player (Container)
- Added Output: HattrickData → MatchData → Lineup → Bench → Player → PlayerID (Element)
- Added Output: HattrickData → MatchData → Lineup → Bench → Player → RoleID (Element)
- Added Output: HattrickData → MatchData → Lineup → Bench → Player → FirstName (Element)
- Added Output: HattrickData → MatchData → Lineup → Bench → Player → NickName (Element)
- Added Output: HattrickData → MatchData → Lineup → Bench → Player → LastName (Element)
- Added Output: HattrickData → MatchData → Lineup → Kickers (Container)
- Added Output: HattrickData → MatchData → Lineup → Kickers → Player (Container)
- Added Output: HattrickData → MatchData → Lineup → Kickers → Player → PlayerID (Element)
- Added Output: HattrickData → MatchData → Lineup → Kickers → Player → RoleID (Element)
- Added Output: HattrickData → MatchData → Lineup → Kickers → Player → FirstName (Element)
- Added Output: HattrickData → MatchData → Lineup → Kickers → Player → NickName (Element)
- Added Output: HattrickData → MatchData → Lineup → Kickers → Player → LastName (Element)
- Added Output: HattrickData → MatchData → Lineup → SetPieces (Container)
- Added Output: HattrickData → MatchData → Lineup → SetPieces → PlayerID (Element)
- Added Output: HattrickData → MatchData → Lineup → SetPieces → FirstName (Element)
- Added Output: HattrickData → MatchData → Lineup → SetPieces → NickName (Element)
- Added Output: HattrickData → MatchData → Lineup → SetPieces → LastName (Element)
- Added Output: HattrickData → MatchData → Lineup → Captain (Container)
- Added Output: HattrickData → MatchData → Lineup → Captain → PlayerID (Element)
- Added Output: HattrickData → MatchData → Lineup → Captain → FirstName (Element)
- Added Output: HattrickData → MatchData → Lineup → Captain → NickName (Element)
- Added Output: HattrickData → MatchData → Lineup → Captain → LastName (Element)

### Version 2.4 (2015-09-22)
- Added coachModifier to actionType=view.
- Added coachModifier and additional subs/orders for lineup input for actionType=setmatchorder. See references at the bottom of the page.
- Added Output: HattrickData → MatchData → CoachModifier (Element)

### Version 2.3 (2013-11-29)
- Added Action for 'actionType': predictratings (Predicts the sector and tactic ratings for the match specified, according to the sent lineup (using POST method). If no lineup is sent the predictions will be for the saved match orders.)

### Version 2.2 (2013-05-02)
- Added Input: teamId

### Version 2.1 (2012-03-15)
- Removed Output: HattrickData → MatchData → Lineup → Player → PlayerName (Element)
- Added Output: HattrickData → MatchData → Lineup → Player → FirstName (Element)
- Added Output: HattrickData → MatchData → Lineup → Player → NickName (Element)
- Added Output: HattrickData → MatchData → Lineup → Player → LastName (Element)

### Version 2.0 (2012-03-06)
- Removed Input: isYouth
- Added Input: sourceSystem

### Version 1.9 (2011-12-15)
- Added penalty takers.

### Version 1.8 (2011-08-30)
- Removed Input: matchID
- Added Input: matchID
- Added Input: lineup
- Added Output: HattrickData → MatchData → Reason (Element)
- Added Output: HattrickData → MatchData → PlayerOrders → PlayerOrder → OrderType (Element)

### Version 1.7 (2011-06-22)
- Removed Output: HattrickData → MatchData → PlayerOrders → PlayerOrder → MatchID (Element)
- Removed Output: HattrickData → MatchData → PlayerOrders → PlayerOrder → TeamID (Element)

### Version 1.6 (2010-11-08)

### Version 1.5
- Removed Output: HattrickData → MatchData → Lineup → Player → RoleID (Element)
- Added Output: HattrickData → MatchData → Lineup → Player → RoleID (Element)
- Added Output: HattrickData → MatchData → PlayerOrders → PlayerOrder → NewPositionId (Element)
- Added Output: HattrickData → MatchData → PlayerOrders → PlayerOrder → NewPositionBehaviour (Element)

### Version 1.4
- Added Output: HattrickData → MatchData → PlayerOrders (Container)
- Added Output: HattrickData → MatchData → PlayerOrders → PlayerOrder (Container)
- Added Output: HattrickData → MatchData → PlayerOrders → PlayerOrder → PlayerOrderID (Element)
- Added Output: HattrickData → MatchData → PlayerOrders → PlayerOrder → MatchID (Element)
- Added Output: HattrickData → MatchData → PlayerOrders → PlayerOrder → TeamID (Element)
- Added Output: HattrickData → MatchData → PlayerOrders → PlayerOrder → MatchMinuteCriteria (Element)
- Added Output: HattrickData → MatchData → PlayerOrders → PlayerOrder → GoalDiffCriteria (Element)
- Added Output: HattrickData → MatchData → PlayerOrders → PlayerOrder → RedCardCriteria (Element)
- Added Output: HattrickData → MatchData → PlayerOrders → PlayerOrder → SubjectPlayerID (Element)
- Added Output: HattrickData → MatchData → PlayerOrders → PlayerOrder → ObjectPlayerID (Element)
- Added Output: HattrickData → MatchData → PlayerOrders → PlayerOrder → PlayerOrderExtraInteger (Element)

### Version 1.3 {default}
- First version

## Input
Only available inputs in this file version are shown.
required file = matchorders
optional version = 
3.1
3.0
2.4
2.3
2.2
2.1
2.0
1.9
1.8
1.7
1.6
1.5
1.4
1.3
optional actionType = 
view {default}
setmatchorder
predictratings
Indicates what type of action the page should perform.
view {default}
Returns data on the matchorder for the match requested.
setmatchorder
Sets a matchorder for the match specified. Requests to set matchorder must be sent using POST method. All parameters except lineup (see below) should still be sent in querystring. (Requires "set_matchorder" scope.)
predictratings
Predicts the sector and tactic ratings for the match specified, according to the sent lineup (using POST method). If no lineup is sent the predictions will be for the saved match orders.
required matchID : unsigned Integer
Specifies which particular match data is requested for.
optional teamId : unsigned Integer{Default:Your primary club senior teamId}
Team id to get/set orders for. Must refer to a senior or youth team managed by requesting user.
optional sourceSystem : sourceSystem{Default:Hattrick}
Specifies which source system the match belongs to, this replace the old isYouth param
optional lineup : String
JSON formatted match orders to set for the match specified or to predict ratings. Must be specified as request body for POST. Required for actionType=setmatchorder and optional for actionType=predictratings, otherwise not used.

See reference: #Lineup_30

## Output (actionType=view)
- **HattrickData** — Hattrick Data Container for matchEvents=true
  - MatchID : unsigned Integer — The globally unique identifier of the match.
  - sourceSystem : sourceSystem — Specifies which source system the match belongs to, this replace the old isYouth param
  - **MatchData** — Attribute: 'Available' : Boolean True if the user is allowed to see matchorder for this match, false if too close to match or user is not in match. Only available for actionType=view.
    - **HomeTeam** — The Home (formal, not taking arena location into account) team of the match.
      - HomeTeamID : Integer — The teamID of the Home team in the match. (Negative for street teams)
      - HomeTeamName : String — The team name of the Home team in the match.
    - **AwayTeam** — The Away (formal, not taking arena location into account) team of the match.
      - AwayTeamID : Integer — The teamID of the Away team in the match. (Negative for street teams)
      - AwayTeamName : String — The team name of the Away team in the match.
    - **Arena** — Container for the data about the arena where the match was played.
      - ArenaID : unsigned Integer — The globally unique arenaID.
      - ArenaName : String — The name of the arena.
    - MatchDate : DateTime — The kick-off date and time of the match.
    - MatchType : MatchTypeID — Integer defining the type of match.
    - Attitude : MatchTeamAttitude — Integer value representing the the team attitude specified for the match. Only shown to the owner of the team - otherwise omitted.
    - TacticType : MatchTacticType — Integer value representing the team tactic type specified for the match.
    - CoachModifier : coachModifier — Integer value representing the style of play the team will use in the match. The value ranges from -10 (100% defensive) to 10 (100% offensive). The attribute Available specifies if the value has been set or not.
    - **Lineup** — Container for the lineup.
      - **Positions** — Container for the players on the field.
        - **Player** — Container for a particular player that is part of the lineup.
          - PlayerID : unsigned Integer — The globally unique playerID.
          - RoleID : MatchRoleID — An integer indicating which formal 'slot' (Role) the player has filled in the match, before the repositionings take effect. So, there can only be one (or zero) player with a particular RoleID (contrary to how PositionCode works).
          - FirstName : String — First name of the player.
          - NickName : String — Nick name of the player.
          - LastName : String — Last name of the player.
          - Behaviour : MatchBehaviourID — An integer indicating the individual order or repositioning that the player has played with.
      - **Bench** — Container for the players on the bench.
        - **Player** — Container for a particular player that is part of the lineup.
          - PlayerID : unsigned Integer — The globally unique playerID.
          - RoleID : MatchRoleID — An integer indicating which formal 'slot' (Role) the player has filled in the match, before the repositionings take effect. So, there can only be one (or zero) player with a particular RoleID (contrary to how PositionCode works).
          - FirstName : String — First name of the player.
          - NickName : String — Nick name of the player.
          - LastName : String — Last name of the player.
      - **Kickers** — Container for the penalty takers.
        - **Player** — Container for a particular player that is part of the lineup.
          - PlayerID : unsigned Integer — The globally unique playerID.
          - RoleID : MatchRoleID — An integer indicating which formal 'slot' (Role) the player has filled in the match, before the repositionings take effect. So, there can only be one (or zero) player with a particular RoleID (contrary to how PositionCode works).
          - FirstName : String — First name of the player.
          - NickName : String — Nick name of the player.
          - LastName : String — Last name of the player.
      - **SetPieces** — Container for a particular player that will be set pieces taker.
        - PlayerID : unsigned Integer — The globally unique playerID.
        - FirstName : String — First name of the player.
        - NickName : String — Nick name of the player.
        - LastName : String — Last name of the player.
      - **Captain** — Container for a particular player that will be captain.
        - PlayerID : unsigned Integer — The globally unique playerID.
        - FirstName : String — First name of the player.
        - NickName : String — Nick name of the player.
        - LastName : String — Last name of the player.
    - **PlayerOrders** — Container for the submitted playerorders of the team.
      - **PlayerOrder** — Container for a particular playerorder.
        - PlayerOrderID : unsigned Integer — The globally unique PlayerOrderID.
        - MatchMinuteCriteria : unsigned Integer — An integer indicating in which minute the order should be executed at the earliest. # See table MatchMinuteCriteria
        - GoalDiffCriteria : unsigned Integer — An integer representing different standings also required to trigger the order. # See table GoalDiffCriteria
        - RedCardCriteria : unsigned Integer — An integer representing different red card events also required to trigger the order. # See table RedCardCriteria
        - SubjectPlayerID : unsigned Integer — If a/ substitution: The player leaving. If b/ behaviour change: The player changing his behaviour. Or c/ position swap: the first player that will change his position.
        - ObjectPlayerID : unsigned Integer — The player entering (or once more the player changing behaviour). Or if position swap, the player to swap with.
        - OrderType : matchOrderTypeID — The type of the order.
        - NewPositionId : MatchRoleID — An integer representing the new position for the (entering or staying) player.
        - NewPositionBehaviour : MatchBehaviourID — An integer representing the new behaviour for the (entering or staying) player.
        - PlayerOrderExtraInteger : MatchBehaviourID — An integer representing the new behaviour for the (entering or staying) player. (It will always have the value 0, as it is not used by the new match engine.)

## Output (actionType=setmatchorder)
- **HattrickData** — Hattrick Data Container for actionType=setmatchorder
  - MatchID : unsigned Integer — The globally unique identifier of the match.
  - sourceSystem : sourceSystem — Specifies which source system the match belongs to, this replace the old isYouth param
  - **MatchData** — Attribute: 'OrdersSet' : Boolean True if setting the matchorder was successful. Only available for actionType=setmatchorder.
    - Reason : String — The reason for failing to set matchorder. Only available for actionType=setmatchorder and when OrdersSet=false.

## Output (actionType=predictratings)
- **HattrickData** — Hattrick Data Container for actionType=predictratings
  - MatchID : unsigned Integer — The globally unique identifier of the match.
  - sourceSystem : sourceSystem — Specifies which source system the match belongs to, this replace the old isYouth param
  - **MatchData** — Container for the data about the predicted match ratings.
    - TacticType : MatchTacticType — Integer value representing the the team tactic type specified for the match.
    - TacticSkill : SkillLevel — Integer value representing the predicted tactic skill of the team.
    - RatingMidfield : MatchRating — Integer value representing the predicted midfield rating of the team.
    - RatingRightDef : MatchRating — Integer value representing the predicted right defence rating of the team.
    - RatingMidDef : MatchRating — Integer value representing the predicted middle defence rating of the team.
    - RatingLeftDef : MatchRating — Integer value representing the predicted left defence rating of the team.
    - RatingRightAtt : MatchRating — Integer value representing the predicted right attack rating of the team.
    - RatingMidAtt : MatchRating — Integer value representing the predicted middle attack rating of the team.
    - RatingLeftAtt : MatchRating — Integer value representing the predicted left attack rating of the team.

## References
#Note
With version 1.6: Corrected the ID for Substitute (Winger) to 117 (from 118) and corrected the ID for Substitute (Forward) to 118 (from 117).
#MatchMinuteCriteria
MatchMinuteCriteria	Description
-1	Anytime
0 ... 119	After x minutes
46	Halftime
91	Before extratime
#GoalDiffCriteria
GoalDiffCriteria	Description
-1	Any standing
0	Match is tied
1	In the lead
2	Down
3	In the lead by more than one
4	Down by more than one
5	Not down
6	Not in the lead
7	In the lead by more than two
8	Down by more than two
9	Match is not tied
#RedCardCriteria
RedCardCriteria	Description
-1	Ignore red card status
1	My player red-carded
2	Opponent red-carded
11	My central defender red-carded
12	My midfielder red-carded
13	My forward red-carded
14	My wing back red-carded
15	My winger red-carded
21	Opponent central defender red-carded
22	Opponent midfielder red-carded
23	Opponent forward red-carded
24	Opponent wing back red-carded
25	Opponent winger red-carded
#Lineup_18
Value	Description

{
 "positions": [
  {"id":"283974499", "behaviour":"0"},
  {"id":"227622136", "behaviour":"1"},
  {"id":"0", "behaviour":"0"},
  {"id":"226747085", "behaviour":"0"},
  {"id":"0", "behaviour":"0"},
  {"id":"285319569", "behaviour":"2"},
  {"id":"234525976", "behaviour":"1"},
  {"id":"190611685", "behaviour":"0"},
  {"id":"255908009", "behaviour":"0"},
  {"id":"98722167", "behaviour":"0"},
  {"id":"0", "behaviour":"0"},
  {"id":"327345682", "behaviour":"4"},
  {"id":"312263029", "behaviour":"0"},
  {"id":"327875363", "behaviour":"0"},
  
  {"id":"319107771", "behaviour":"0"},
  {"id":"224242073", "behaviour":"0"},
  {"id":"266402745", "behaviour":"0"},
  {"id":"304433518", "behaviour":"0"},
  {"id":"214058496", "behaviour":"0"},
  
  {"id":"190611685", "behaviour":"0"},
  {"id":"226747085", "behaviour":"0"},
  
  {"id":"226747085", "behaviour":"0"},
  {"id":"190611685", "behaviour":"0"},
  {"id":"98722167", "behaviour":"0"},
  {"id":"283974499", "behaviour":"0"},
  {"id":"227622136", "behaviour":"0"},
  {"id":"0", "behaviour":"0"},
  {"id":"0", "behaviour":"0"},
  {"id":"0", "behaviour":"0"},
  {"id":"0", "behaviour":"0"},
  {"id":"0", "behaviour":"0"},
  {"id":"0", "behaviour":"0"}
 ],
 "settings":
 {
  "tactic":"0",
  "speechLevel":"0",
  "newLineup":"",
  "coachModifier":"0"
 },
 "substitutions": [
  {
   "playerin":"304433518",
   "playerout":"327875363",
   "orderType":"1",
   "min":"80",
   "pos":"-1",
   "beh":"-1",
   "card":"-1",
   "standing":"0"
  },
  {
   "playerin":"319107771",
   "playerout":"283974499",
   "orderType":"1",
   "min":"89",
   "pos":"-1",
   "beh":"-1",
   "card":"-1",
   "standing":"3"
  }
 ]
}

	This is a JSON-formatted string.

positions
An array of all positions.

First 14 positionslots (0-13) represent the players on the field, starting with the goalkeeper (0), followed by right defender (1), right central defender (2) and so on up to left forward (13).
The next 5 slots are substitutes in the following order: goalkeeper, defender, midfielder, forward, winger.
Line 20 and 21 is captain and set pieces taker.
The last 11 slots are penalty takers. All 11 of these should always be provided, sending {"id":"0","behaviour":"0"} where not provided by the user. In this example only 5 kickers were set by the user.
The id is the PlayerId of the player you want.
The behaviour is the matchBehaviourID
The order is very important and concequently its very important that you send id:0 on slots where no player is placed!
settings
tactic is matchTacticType.
speechLevel is the matchTeamAttitude.
newLineup should always be empty.
coachModifer is the style of play and is a value between -10 (100% defensive) and 10 (100% offensive). The available values depends on the level of the teams tactical assistant. If the coach is offensive you can go 2 steps, or 20%, per level towards defensive, and vice versa. If you have a neutral coach you can go 1 step, or 10%, per level in either direction. This value is optional and is recommended to not include if the user doesn't have a tactical assistant. Ignored for youth matches.
substitutions
An array of all Subs / Orders. You can have 0-5 of subs/orders, plus an additional one per level of the teams tactical assistant. Send an empty array for 0 substitutions.

playerin is the playerId of the player in the "in" box.
playerout is consequently the playerId of the player in the "out" box, this should be same as playerin if its a behaviour change.
orderType: 1 = substitution or behaviour change, 3 = position swap.
min: MatchMinuteCriteria
pos: the position the player should take after the substitution, 0-13, see positions above for the order. -1 means no change.
beh: see matchBehaviourID. -1 means no change.
card: RedCardCriteria
standing: GoalDiffCriteria
#Lineup_30
Value	Description

{
 "positions": [
  {"id":283974499, "behaviour":0},
  {"id":227622136, "behaviour":1},
  {"id":0, "behaviour":0},
  {"id":226747085, "behaviour":0},
  {"id":0, "behaviour":0},
  {"id":285319569, "behaviour":2},
  {"id":234525976, "behaviour":1},
  {"id":190611685, "behaviour":0},
  {"id":255908009, "behaviour":0},
  {"id":98722167, "behaviour":0},
  {"id":0, "behaviour":0},
  {"id":327345682, "behaviour":4},
  {"id":312263029, "behaviour":0},
  {"id":327875363, "behaviour":0}
 ],
 "bench": [
  {"id":319107771, "behaviour":0},
  {"id":0, "behaviour":0},
  {"id":224242073, "behaviour":0},
  {"id":266402745, "behaviour":0},
  {"id":214058496, "behaviour":0},
  {"id":304433518, "behaviour":0},
  {"id":0, "behaviour":0},

  {"id":224242073, "behaviour":0},
  {"id":0, "behaviour":0},
  {"id":224242073, "behaviour":0},
  {"id":214058496, "behaviour":0},
  {"id":304433518, "behaviour":0},
  {"id":234525976, "behaviour":0},
  {"id":0, "behaviour":0}
 ],
 "kickers": [
  {"id":226747085, "behaviour":0},
  {"id":190611685, "behaviour":0},
  {"id":98722167, "behaviour":0},
  {"id":283974499, "behaviour":0},
  {"id":227622136, "behaviour":0},
  {"id":0, "behaviour":0},
  {"id":0, "behaviour":0},
  {"id":0, "behaviour":0},
  {"id":0, "behaviour":0},
  {"id":0, "behaviour":0},
  {"id":0, "behaviour":0}
 ],
 "captain": "190611685",
 "setPieces": "226747085",
 "settings":
 {
  "tactic":"0",
  "speechLevel":"0",
  "newLineup":"",
  "coachModifier":"0",
  "manMarkerPlayerId":"0",
  "manMarkingPlayerId":"0"
 },
 "substitutions": [
  {
   "playerin":"304433518",
   "playerout":"327875363",
   "orderType":"1",
   "min":"80",
   "pos":"-1",
   "beh":"-1",
   "card":"-1",
   "standing":"0"
  },
  {
   "playerin":"319107771",
   "playerout":"283974499",
   "orderType":"1",
   "min":"89",
   "pos":"-1",
   "beh":"-1",
   "card":"-1",
   "standing":"3"
  }
 ]
}

	This is a JSON-formatted string with support of a larger bench.

You must send the complete JSON-object as shown here, even when for example no captain was set. Always send "0" where no player was provided as the order is very important.

positions
An array of all positions.

id is the PlayerId of the player you want.
behaviour is the matchBehaviourID
This represent the players on the field, starting with the goalkeeper (0), followed by right defender (1), right central defender (2) and so on up to left forward (13).
bench
An array of the bench.
id is the PlayerId of the player you want.
behaviour is optional and will always be 0 regardless of value.
The first 7 slots represents the primary substitutes, in the following order: goalkeeper, central defender, wing back, inner midfielder, forward, winger, extra.
The next 7 slots represents the backup substitutes, in the same order as above.
To qualify as a backup substitute a player must first be a primary substitute.
There's no limit to how many slots a substitute can hold.
kickers
An array of all the penalty takers.
id is the PlayerId of the player you want.
behaviour is optional and will always be 0 regardless of value.
captain
The PlayerId of the player you want as captain. "0" if not set.
setPieces
The PlayerId of the player you want as set pieces taker. "0" if not set.
settings
tactic is matchTacticType.
speechLevel is the matchTeamAttitude.
newLineup should always be empty.
coachModifer is the style of play and is a value between -10 (100% defensive) and 10 (100% offensive). The available values depends on the level of the teams tactical assistant. If the coach is offensive you can go 2 steps, or 20%, per level towards defensive, and vice versa. If you have a neutral coach you can go 1 step, or 10%, per level in either direction. This value is optional and is recommended to not include if the user doesn't have a tactical assistant. Ignored for youth matches.
substitutions
An array of all Subs / Orders. You can have 0-5 of subs/orders, plus an additional one per level of the teams tactical assistant. Send an empty array for 0 substitutions.

playerin is the playerId of the player in the "in" box.
playerout is consequently the playerId of the player in the "out" box, this should be same as playerin if its a behaviour change.
orderType: 1 = substitution or behaviour change, 3 = position swap.
min: MatchMinuteCriteria
pos: the position the player should take after the substitution, 0-13, see positions above for the order. -1 means no change.
beh: see matchBehaviourID. -1 means no change.
card: RedCardCriteria
standing: GoalDiffCriteria
