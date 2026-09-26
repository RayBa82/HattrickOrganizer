# live

Get (live) match ticker

## Changelog
### Version 2.3 (2022-05-04)
- Fix MatchType for national teams,

### Version 2.3 (2022-03-03)
- Identify single match (61) and ladder match (62) on MatchType,

### Version 2.1 (2021-08-25)
- Add useLiveEventsAndTexts input.
- Added Input: useLiveEventsAndTexts

### Version 2.0 (2020-09-01)
- Show all match events, even if text is empty.

### Version 1.9 (2016-08-02)
- Added MatchPart and NextEventMatchPart.
- Added Output: HattrickData → MatchList → Match → Substitutions → Substitution → MatchPart (Element)
- Added Output: HattrickData → MatchList → Match → EventList → Event → MatchPart (Element)
- Added Output: HattrickData → MatchList → Match → NextEventMatchPart (Element)

### Version 1.8 (2012-09-01)
- Removed Input: isYouth
- Added Input: sourceSystem
- Removed Output: HattrickData → MatchList → Match → IsYouth (Element)
- Added Output: HattrickData → MatchList → Match → SourceSystem (Element)

### Version 1.7 (2011-11-14)
- Added Output: HattrickData → MatchList → Match → NextEventMinute (Element)

### Version 1.6 (2011-09-26)
- Added Input: includeStartingLineup
- Added Output: HattrickData → MatchList → Match → HomeTeam → StartingLineup (Container)
- Added Output: HattrickData → MatchList → Match → HomeTeam → StartingLineup → Player (Container)
- Added Output: HattrickData → MatchList → Match → HomeTeam → StartingLineup → Player → PlayerID (Element)
- Added Output: HattrickData → MatchList → Match → HomeTeam → StartingLineup → Player → RoleID (Element)
- Added Output: HattrickData → MatchList → Match → HomeTeam → StartingLineup → Player → PlayerName (Element)
- Added Output: HattrickData → MatchList → Match → HomeTeam → StartingLineup → Player → Behaviour (Element)
- Added Output: HattrickData → MatchList → Match → AwayTeam → StartingLineup (Container)
- Added Output: HattrickData → MatchList → Match → AwayTeam → StartingLineup → Player (Container)
- Added Output: HattrickData → MatchList → Match → AwayTeam → StartingLineup → Player → PlayerID (Element)
- Added Output: HattrickData → MatchList → Match → AwayTeam → StartingLineup → Player → RoleID (Element)
- Added Output: HattrickData → MatchList → Match → AwayTeam → StartingLineup → Player → PlayerName (Element)
- Added Output: HattrickData → MatchList → Match → AwayTeam → StartingLineup → Player → Behaviour (Element)
- Added Output: HattrickData → MatchList → Match → Substitutions (Container)
- Added Output: HattrickData → MatchList → Match → Substitutions → Substitution (Container)
- Added Output: HattrickData → MatchList → Match → Substitutions → Substitution → TeamID (Element)
- Added Output: HattrickData → MatchList → Match → Substitutions → Substitution → SubjectPlayerID (Element)
- Added Output: HattrickData → MatchList → Match → Substitutions → Substitution → ObjectPlayerID (Element)
- Added Output: HattrickData → MatchList → Match → Substitutions → Substitution → OrderType (Element)
- Added Output: HattrickData → MatchList → Match → Substitutions → Substitution → NewPositionId (Element)
- Added Output: HattrickData → MatchList → Match → Substitutions → Substitution → NewPositionBehaviour (Element)
- Added Output: HattrickData → MatchList → Match → Substitutions → Substitution → MatchMinute (Element)
- Added Output: HattrickData → MatchList → Match → LastShownEventIndex (Element)
- Added Output: HattrickData → MatchList → Match → StartingLineup (Container)
- Added Output: HattrickData → MatchList → Match → StartingLineup → Player (Container)
- Added Output: HattrickData → MatchList → Match → StartingLineup → Player → PlayerID (Element)
- Added Output: HattrickData → MatchList → Match → StartingLineup → Player → RoleID (Element)
- Added Output: HattrickData → MatchList → Match → StartingLineup → Player → PlayerName (Element)
- Added Output: HattrickData → MatchList → Match → StartingLineup → Player → Behaviour (Element)

### Version 1.5
- Added Output: HattrickData → MatchList → Match → HomeTeam → HomeTeamShortName (Element)
- Added Output: HattrickData → MatchList → Match → AwayTeam → AwayTeamShortName (Element)

### Version 1.4
- Removed Output: HattrickData → MatchList → Match → EventList → Event (Container)
- Removed Output: HattrickData → MatchList → Match → EventList → Event → Minute (Element)
- Removed Output: HattrickData → MatchList → Match → EventList → Event → EventKey (Element)
- Removed Output: HattrickData → MatchList → Match → EventList → Event → EventText (Element)
- Removed Output: HattrickData → MatchList → Match → EventList → Event → SubjectTeamID (Element)
- Removed Output: HattrickData → MatchList → Match → EventList → Event → SubjectPlayerID (Element)
- Removed Output: HattrickData → MatchList → Match → EventList → Event → ObjectPlayerID (Element)
- Added Output: HattrickData → MatchList → Match → EventList → Event (Container)
- Added Output: HattrickData → MatchList → Match → EventList → Event → Minute (Element)
- Added Output: HattrickData → MatchList → Match → EventList → Event → EventKey (Element)
- Added Output: HattrickData → MatchList → Match → EventList → Event → EventText (Element)
- Added Output: HattrickData → MatchList → Match → EventList → Event → SubjectTeamID (Element)
- Added Output: HattrickData → MatchList → Match → EventList → Event → SubjectPlayerID (Element)
- Added Output: HattrickData → MatchList → Match → EventList → Event → ObjectPlayerID (Element)
- Added Output: HattrickData → MatchList → Match → HomeTeam → HomeTeamShortName (Element)
- Added Output: HattrickData → MatchList → Match → AwayTeam → AwayTeamShortName (Element)

### Version 1.3 {default}
- First version

## Input
Only available inputs in this file version are shown.
required file = live
optional version = 
2.3
2.3
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
viewNew
viewAll
clearAll
addMatch
deleteMatch
Indicates what type of action the page should perform.
view {default}
Show the latest event(s) for all tracked matches. If matchID is supplied, that match will be shown with all events since the start of the game ('expanded' view).
optional matchID : unsigned Integer
The globally unique matchID.
optional sourceSystem : sourceSystem{Default:hattrick}
Specifies which source system the match belongs to, this replace the old isYouth param
optional includeStartingLineup : Boolean{Default:false}
Should <StartingLineup> and <Substitutions> be included in the response?
optional useLiveEventsAndTexts : Boolean{Default:false}
Should the events and texts be as on the live page on the site?
viewNew
Show those matches where new events have occured since last request of the page. You'll need to send the which was your last event according to LastShownIndexes
optional includeStartingLineup : Boolean{Default:false}
Should <Substitutions> be included in the response?
(Note that <StartingLineup> is never provided for viewNew)
optional useLiveEventsAndTexts : Boolean{Default:false}
Should the events and texts be as on the live page on the site?
optional lastShownIndexes : String
JSON formatted array of last shown indexes for each match.

See reference: #LastShownIndexes.
viewAll
Show all the events for all matches.
optional includeStartingLineup : Boolean{Default:false}
Should <StartingLineup> and <Substitutions> be included in the response?
optional useLiveEventsAndTexts : Boolean{Default:false}
Should the events and texts be as on the live page on the site?
clearAll
Remove all tracked matches from the tracking session.
addMatch
Add a new match to be tracked.
required matchID : unsigned Integer
The globally unique matchID.
optional sourceSystem : sourceSystem{Default:hattrick}
Specifies which source system the match belongs to, this replace the old isYouth param
optional includeStartingLineup : Boolean{Default:false}
Should <StartingLineup> and <Substitutions> be included in the response?
optional useLiveEventsAndTexts : Boolean{Default:false}
Should the events and texts be as on the live page on the site?
deleteMatch
Remove a particular match from the session.
required matchID : unsigned Integer
The globally unique matchID.
optional sourceSystem : sourceSystem{Default:hattrick}
Specifies which source system the match belongs to, this replace the old isYouth param

## Output (actionType=view)
- **HattrickData** — Hattrick Data Container for actionType=view
  - **MatchList** — Container for all the matches.
    - **Match** — The container for a match. An attribute called Index signifies the number of the match in the list.
      - MatchID : unsigned Integer — The globally unique identifier of the match.
      - MatchType : MatchTypeID — Integer defining the type of match.
      - SourceSystem : sourceSystem — Specifies which source system the match belongs to, this replace the old isYouth param
      - MatchDate : DateTime — The start date (kick-off) of the match.
      - **HomeTeam** — The Home (formal, not taking arena location into account) team of the match.
        - HomeTeamID : Integer — The teamID of the Home team in the match. (Negative for street teams)
        - HomeTeamName : String — The team name of the Home team in the match.
        - HomeTeamShortName : String — The short team name of the Home team in the match.
        - **StartingLineup** — List of players in the hometeam who started the game
          - **Player** — Container for a particular player that is part of the starting lineup.
            - PlayerID : unsigned Integer — Globally unique ID of the player.
            - RoleID : MatchRoleID — An integer indicating which formal 'slot' (Role) the player has filled in the match.
            - PlayerName : String — The full name of the player.
            - Behaviour : MatchBehaviourID — An integer indicating the individual order or repositioning that the player has played with. Not provided for Captain and Set Pieces taker.
      - **AwayTeam** — The Away (formal, not taking arena location into account) team of the match.
        - AwayTeamID : Integer — The teamID of the Away team in the match. (Negative for street teams)
        - AwayTeamName : String — The team name of the Away team in the match.
        - AwayTeamShortName : String — The short team name of the Away team in the match.
        - **StartingLineup** — List of players in the awayteam who started the game
          - **Player** — Container for a particular player that is part of the starting lineup.
            - PlayerID : unsigned Integer — Globally unique ID of the player.
            - RoleID : MatchRoleID — An integer indicating which formal 'slot' (Role) the player has filled in the match.
            - PlayerName : String — The full name of the player.
            - Behaviour : MatchBehaviourID — An integer indicating the individual order or repositioning that the player has played with. Not provided for Captain and Set Pieces taker.
      - **Substitutions** — Container for the substitutions in the game.
        - **Substitution** — Container for a particular substitution. An attribute named Index gives a counter.
          - TeamID : unsigned Integer — The globally unique TeamID for which the orders were sent.
          - SubjectPlayerID : unsigned Integer — If a/ substitution: The player leaving. If b/ behaviour change: The player changing his behaviour. Or c/ position swap: the first player that will change his position.
          - ObjectPlayerID : unsigned Integer — The player entering (or once more the player changing behaviour). Or if position swap, the player to swap with.
          - OrderType : matchOrderTypeID — The type of the order.
          - NewPositionId : MatchRoleID — An integer representing the new position for the (entering or staying) player.
          - NewPositionBehaviour : MatchBehaviourID — An integer representing the new behaviour for the (entering or staying) player.
          - MatchMinute : unsigned Integer — An integer indicating in which minute the order was executed.
          - MatchPart : matchPart — Which part of the match the order was executed.
      - **EventList** — Container for all the events.
        - **Event** — Attribute: 'Index' : unsigned Integer Indicates the order of the event relative to the other events sent in the same batch for this match.
          - Minute : unsigned Integer — The match minute when the event happened.
          - MatchPart : matchPart — Which part of the match the event happened.
          - EventKey : String — A unique key defining what type of event it was.
          - EventText : String — String describing the event as it would appear in the match report.
          - SubjectTeamID : unsigned Integer — Integer that, for goals and chances, is indicating the attacking team. For other event, this field usually indicates the team that is doing something. It may indicate other things for other events - exactly what is not documented here and the SubjectTeamID information, while free to use for CHPPs is 'unsupported' and used 'at your own risk'.
          - SubjectPlayerID : unsigned Integer — Integer that, for goals and chances, is indicating the scorer or the player failing to score. For other events, this field usually indicates the primarily active player. It may indicate other things for other events and like SubjectTeamID is 'unsupported' and used 'at your own risk'.
          - ObjectPlayerID : unsigned Integer — Integer that, for regular goals and chances, is indicating the defending team's goal keeper. For special event chances/goals, the ObjectPlayer is often the assisting player of the attacking team, but can be any other player that is involved in the event. For other events, this field is used very freely and can indicate any kind of relevant integer data. Like SubjectPlayerID and SubjectTeamID, it is 'unsupported' and used at 'your own risk'.
      - HomeGoals : unsigned Integer — The current number of goals in the match for the home team.
      - AwayGoals : unsigned Integer — The current number of goals in the match for the away team.
      - LastShownEventIndex : unsigned Integer — The last shown event. Use this as LastShownIndexes for actionType=viewNew.
      - NextEventMinute : unsigned Integer — The minute that the next matchevent will happen, this allows you to only ask for new events when needed. Only available while the match is ongoing. It should only be used by the application, it should NOT be shown to the users.
      - NextEventMatchPart : matchPart — The matchPart in which the next matchevent will happen, this allows you to only ask for new events when needed. Only available while the match is ongoing. It should only be used by the application, it should NOT be shown to the users.

## Output (actionType=viewNew)
- **HattrickData** — Hattrick Data Container for actionType=viewNew
  - **MatchList** — Container for all the matches.
    - **Match** — The container for a match. An attribute called Index signifies the number of the match in the list.
      - MatchID : unsigned Integer — The globally unique identifier of the match.
      - SourceSystem : sourceSystem — Specifies which source system the match belongs to, this replace the old isYouth param
      - **Substitutions** — Container for the substitutions in the game.
        - **Substitution** — Container for a particular substitution. An attribute named Index gives a counter.
          - TeamID : unsigned Integer — The globally unique TeamID for which the orders were sent.
          - SubjectPlayerID : unsigned Integer — If a/ substitution: The player leaving. If b/ behaviour change: The player changing his behaviour. Or c/ position swap: the first player that will change his position.
          - ObjectPlayerID : unsigned Integer — The player entering (or once more the player changing behaviour). Or if position swap, the player to swap with.
          - OrderType : matchOrderTypeID — The type of the order.
          - NewPositionId : MatchRoleID — An integer representing the new position for the (entering or staying) player.
          - NewPositionBehaviour : MatchBehaviourID — An integer representing the new behaviour for the (entering or staying) player.
          - MatchMinute : unsigned Integer — An integer indicating in which minute the order was executed.
          - MatchPart : matchPart — Which part of the match the order was executed.
      - **EventList** — Container for all the events.
        - **Event** — Attribute: 'Index' : unsigned Integer Indicates the order of the event relative to the other events sent in the same batch for this match.
          - Minute : unsigned Integer — The match minute when the event happened.
          - MatchPart : matchPart — Which part of the match the event happened.
          - EventKey : String — A unique key defining what type of event it was.
          - EventText : String — String describing the event as it would appear in the match report.
          - SubjectTeamID : unsigned Integer — Integer that, for goals and chances, is indicating the attacking team. For other event, this field usually indicates the team that is doing something. It may indicate other things for other events - exactly what is not documented here and the SubjectTeamID information, while free to use for CHPPs is 'unsupported' and used 'at your own risk'.
          - SubjectPlayerID : unsigned Integer — Integer that, for goals and chances, is indicating the scorer or the player failing to score. For other events, this field usually indicates the primarily active player. It may indicate other things for other events and like SubjectTeamID is 'unsupported' and used 'at your own risk'.
          - ObjectPlayerID : unsigned Integer — Integer that, for regular goals and chances, is indicating the defending team's goal keeper. For special event chances/goals, the ObjectPlayer is often the assisting player of the attacking team, but can be any other player that is involved in the event. For other events, this field is used very freely and can indicate any kind of relevant integer data. Like SubjectPlayerID and SubjectTeamID, it is 'unsupported' and used at 'your own risk'.
      - HomeGoals : unsigned Integer — The current number of goals in the match for the home team.
      - AwayGoals : unsigned Integer — The current number of goals in the match for the away team.
      - LastShownEventIndex : unsigned Integer — The last shown event. Use this as LastShownIndexes for actionType=viewNew.
      - NextEventMinute : unsigned Integer — The minute that the next matchevent will happen, this allows you to only ask for new events when needed. Only available while the match is ongoing. It should only be used by the application, it should NOT be shown to the users.
      - NextEventMatchPart : matchPart — The matchPart in which the next matchevent will happen, this allows you to only ask for new events when needed. Only available while the match is ongoing. It should only be used by the application, it should NOT be shown to the users.

## Output (actionType=viewAll)
- **HattrickData** — Hattrick Data Container for actionType=viewAll
  - **MatchList** — Container for all the matches.
    - **Match** — The container for a match. An attribute called Index signifies the number of the match in the list.
      - MatchID : unsigned Integer — The globally unique identifier of the match.
      - MatchType : MatchTypeID — Integer defining the type of match.
      - SourceSystem : sourceSystem — Specifies which source system the match belongs to, this replace the old isYouth param
      - MatchDate : DateTime — The start date (kick-off) of the match.
      - **HomeTeam** — The Home (formal, not taking arena location into account) team of the match.
        - HomeTeamID : Integer — The teamID of the Home team in the match. (Negative for street teams)
        - HomeTeamName : String — The team name of the Home team in the match.
        - HomeTeamShortName : String — The short team name of the Home team in the match.
      - **AwayTeam** — The Away (formal, not taking arena location into account) team of the match.
        - AwayTeamID : Integer — The teamID of the Away team in the match. (Negative for street teams)
        - AwayTeamName : String — The team name of the Away team in the match.
        - AwayTeamShortName : String — The short team name of the Away team in the match.
      - **StartingLineup** — List of players in the hometeam who started the game
        - **Player** — Container for a particular player that is part of the starting lineup.
          - PlayerID : unsigned Integer — Globally unique ID of the player.
          - RoleID : MatchRoleID — An integer indicating which formal 'slot' (Role) the player has filled in the match.
          - PlayerName : String — The full name of the player.
          - Behaviour : MatchBehaviourID — An integer indicating the individual order or repositioning that the player has played with. Not provided for Captain and Set Pieces taker.
      - **Substitutions** — Container for the substitutions in the game.
        - **Substitution** — Container for a particular substitution. An attribute named Index gives a counter.
          - TeamID : unsigned Integer — The globally unique TeamID for which the orders were sent.
          - SubjectPlayerID : unsigned Integer — If a/ substitution: The player leaving. If b/ behaviour change: The player changing his behaviour. Or c/ position swap: the first player that will change his position.
          - ObjectPlayerID : unsigned Integer — The player entering (or once more the player changing behaviour). Or if position swap, the player to swap with.
          - OrderType : matchOrderTypeID — The type of the order.
          - NewPositionId : MatchRoleID — An integer representing the new position for the (entering or staying) player.
          - NewPositionBehaviour : MatchBehaviourID — An integer representing the new behaviour for the (entering or staying) player.
          - MatchMinute : unsigned Integer — An integer indicating in which minute the order was executed.
          - MatchPart : matchPart — Which part of the match the order was executed.
      - **EventList** — Attribute: 'Index' : unsigned Integer The order of the events
        - **Event** — Attribute: 'Index' : unsigned Integer Indicates the order of the event relative to the other events sent in the same batch for this match.
          - Minute : unsigned Integer — The match minute when the event happened.
          - MatchPart : matchPart — Which part of the match the event happened.
          - EventKey : String — A unique key defining what type of event it was.
          - EventText : String — String describing the event as it would appear in the match report.
          - SubjectTeamID : unsigned Integer — Integer that, for goals and chances, is indicating the attacking team. For other event, this field usually indicates the team that is doing something. It may indicate other things for other events - exactly what is not documented here and the SubjectTeamID information, while free to use for CHPPs is 'unsupported' and used 'at your own risk'.
          - SubjectPlayerID : unsigned Integer — Integer that, for goals and chances, is indicating the scorer or the player failing to score. For other events, this field usually indicates the primarily active player. It may indicate other things for other events and like SubjectTeamID is 'unsupported' and used 'at your own risk'.
          - ObjectPlayerID : unsigned Integer — Integer that, for regular goals and chances, is indicating the defending team's goal keeper. For special event chances/goals, the ObjectPlayer is often the assisting player of the attacking team, but can be any other player that is involved in the event. For other events, this field is used very freely and can indicate any kind of relevant integer data. Like SubjectPlayerID and SubjectTeamID, it is 'unsupported' and used at 'your own risk'.
      - HomeGoals : unsigned Integer — The current number of goals in the match for the home team.
      - AwayGoals : unsigned Integer — The current number of goals in the match for the away team.
      - LastShownEventIndex : unsigned Integer — The last shown event. Use this as LastShownIndexes for actionType=viewNew.
      - NextEventMinute : unsigned Integer — The minute that the next matchevent will happen, this allows you to only ask for new events when needed. Only available while the match is ongoing. It should only be used by the application, it should NOT be shown to the users.
      - NextEventMatchPart : matchPart — The matchPart in which the next matchevent will happen, this allows you to only ask for new events when needed. Only available while the match is ongoing. It should only be used by the application, it should NOT be shown to the users.

## Output (actionType=clearAll)
- **HattrickData** — Hattrick Data Container for actionType=clearAll
  - **MatchList** — Container for all the matches.
    - **Match** — The container for a match. An attribute called Index signifies the number of the match in the list.
      - MatchID : unsigned Integer — The globally unique identifier of the match.
      - MatchType : MatchTypeID — Integer defining the type of match.
      - SourceSystem : sourceSystem — Specifies which source system the match belongs to, this replace the old isYouth param
      - MatchDate : DateTime — The start date (kick-off) of the match.
      - **HomeTeam** — The Home (formal, not taking arena location into account) team of the match.
        - HomeTeamID : Integer — The teamID of the Home team in the match. (Negative for street teams)
        - HomeTeamName : String — The team name of the Home team in the match.
        - HomeTeamShortName : String — The short team name of the Home team in the match.
      - **AwayTeam** — The Away (formal, not taking arena location into account) team of the match.
        - AwayTeamID : Integer — The teamID of the Away team in the match. (Negative for street teams)
        - AwayTeamName : String — The team name of the Away team in the match.
        - AwayTeamShortName : String — The short team name of the Away team in the match.
      - **EventList** — Attribute: 'Index' : unsigned Integer The order of the events
        - **Event** — Attribute: 'Index' : unsigned Integer Indicates the order of the event relative to the other events sent in the same batch for this match.
          - Minute : unsigned Integer — The match minute when the event happened.
          - MatchPart : matchPart — Which part of the match the event happened.
          - EventKey : String — A unique key defining what type of event it was.
          - EventText : String — String describing the event as it would appear in the match report.
          - SubjectTeamID : unsigned Integer — Integer that, for goals and chances, is indicating the attacking team. For other event, this field usually indicates the team that is doing something. It may indicate other things for other events - exactly what is not documented here and the SubjectTeamID information, while free to use for CHPPs is 'unsupported' and used 'at your own risk'.
          - SubjectPlayerID : unsigned Integer — Integer that, for goals and chances, is indicating the scorer or the player failing to score. For other events, this field usually indicates the primarily active player. It may indicate other things for other events and like SubjectTeamID is 'unsupported' and used 'at your own risk'.
          - ObjectPlayerID : unsigned Integer — Integer that, for regular goals and chances, is indicating the defending team's goal keeper. For special event chances/goals, the ObjectPlayer is often the assisting player of the attacking team, but can be any other player that is involved in the event. For other events, this field is used very freely and can indicate any kind of relevant integer data. Like SubjectPlayerID and SubjectTeamID, it is 'unsupported' and used at 'your own risk'.
      - HomeGoals : unsigned Integer — The current number of goals in the match for the home team.
      - AwayGoals : unsigned Integer — The current number of goals in the match for the away team.
      - LastShownEventIndex : unsigned Integer — The last shown event. Use this as LastShownIndexes for actionType=viewNew.
      - NextEventMinute : unsigned Integer — The minute that the next matchevent will happen, this allows you to only ask for new events when needed. Only available while the match is ongoing. It should only be used by the application, it should NOT be shown to the users.
      - NextEventMatchPart : matchPart — The matchPart in which the next matchevent will happen, this allows you to only ask for new events when needed. Only available while the match is ongoing. It should only be used by the application, it should NOT be shown to the users.

## Output (actionType=addMatch)
Same as actionType=viewAll.

## Output (actionType=deleteMatch)
Same as actionType=clearAll.

## References
#MatchEventTypes
Documentation of the match events in Hattrick here!
#Note
The viewAll and viewNew actionTypes are intended as the primary CHPP use of HT Live, in the following manner, which is the behaviour we recommend. We appreciate all developers who seek to minimize server load by using this recommended way:

Upon first access per session, use viewAll. Then store the events locally in your application, user disk or similar. Then use viewNew for the repeating requests.
#LastShownIndexes
Value	Description

{
 "matches": [
  {"matchId":"52383954", "sourceSystem":"youth", "index":"4"},
  {"matchId":"341643388", "sourceSystem":"hattrick", "index":"-1"},
  ,{"matchId":"341643388", "sourceSystem":"htointegrated", "index":"-1"}
  {"matchId":"345947495", "index":"-1"}
 ]
}

	This is a JSON-formatted string.

matches
An array of all matches.

matchId
The globally unique matchID.

sourceSystem
Specifies which source system the match belongs to. Defaults to "Hattrick" if not provided.

index
The EventIndex, you should pick this from the <LastShownIndex>-element.
You can provide -1 to get all the events if you like, but you might just as well not send that match at all and it will default to -1 anyway.
