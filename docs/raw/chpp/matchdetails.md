# matchdetails

Detailed match information

## Changelog
### Version 3.1 (2022-03-03)
- Add number of chances for each team.
- Show sold seats even if not home team.
- Fixed the matchType for national team matches.
- Removed Output: HattrickData → Match → Arena → SoldTerraces (Element)
- Removed Output: HattrickData → Match → Arena → SoldBasic (Element)
- Removed Output: HattrickData → Match → Arena → SoldRoof (Element)
- Removed Output: HattrickData → Match → Arena → SoldVIP (Element)
- Added Output: HattrickData → Match → HomeTeam → NrOfChancesLeft (Element)
- Added Output: HattrickData → Match → HomeTeam → NrOfChancesCenter (Element)
- Added Output: HattrickData → Match → HomeTeam → NrOfChancesRight (Element)
- Added Output: HattrickData → Match → HomeTeam → NrOfChancesSpecialEvents (Element)
- Added Output: HattrickData → Match → HomeTeam → NrOfChancesOther (Element)
- Added Output: HattrickData → Match → AwayTeam → NrOfChancesLeft (Element)
- Added Output: HattrickData → Match → AwayTeam → NrOfChancesCenter (Element)
- Added Output: HattrickData → Match → AwayTeam → NrOfChancesRight (Element)
- Added Output: HattrickData → Match → AwayTeam → NrOfChancesSpecialEvents (Element)
- Added Output: HattrickData → Match → AwayTeam → NrOfChancesOther (Element)
- Added Output: HattrickData → Match → Arena → SoldTerraces (Element)
- Added Output: HattrickData → Match → Arena → SoldBasic (Element)
- Added Output: HattrickData → Match → Arena → SoldRoof (Element)
- Added Output: HattrickData → Match → Arena → SoldVIP (Element)

### Version 3.0 (2017-12-14)
- Added MatchRuleId.
- Added Output: HattrickData → Match → MatchRuleId (Element)

### Version 2.9 (2016-08-15)
- Added AddedMinutes.
- Added Output: HattrickData → Match → AddedMinutes (Element)

### Version 2.8 (2016-08-02)
- Added MatchPart.
- Added Output: HattrickData → Match → Scorers → Goal → MatchPart (Element)
- Added Output: HattrickData → Match → Bookings → Booking → MatchPart (Element)
- Added Output: HattrickData → Match → Injuries → Injury → MatchPart (Element)
- Added Output: HattrickData → Match → EventList → Event → MatchPart (Element)

### Version 2.7 (2015-02-09)
- Added CupLevel & CupLevelIndex.
- Added Output: HattrickData → Match → CupLevel (Element)
- Added Output: HattrickData → Match → CupLevelIndex (Element)

### Version 2.6 (2014-10-20)
- Added Output: HattrickData → Match → MatchContextId (Element)

### Version 2.5 (2013-05-02)
- Added Output: HattrickData → Match → MatchOfficials (Container)
- Added Output: HattrickData → Match → MatchOfficials → Referee (Container)
- Added Output: HattrickData → Match → MatchOfficials → Referee → RefereeId (Element)
- Added Output: HattrickData → Match → MatchOfficials → Referee → RefereeName (Element)
- Added Output: HattrickData → Match → MatchOfficials → Referee → RefereeCountryId (Element)
- Added Output: HattrickData → Match → MatchOfficials → Referee → RefereeCountryName (Element)
- Added Output: HattrickData → Match → MatchOfficials → Referee → RefereeTeamId (Element)
- Added Output: HattrickData → Match → MatchOfficials → Referee → RefereeTeamname (Element)
- Added Output: HattrickData → Match → MatchOfficials → RefereeAssistant1 (Container)
- Added Output: HattrickData → Match → MatchOfficials → RefereeAssistant1 → RefereeId (Element)
- Added Output: HattrickData → Match → MatchOfficials → RefereeAssistant1 → RefereeName (Element)
- Added Output: HattrickData → Match → MatchOfficials → RefereeAssistant1 → RefereeCountryId (Element)
- Added Output: HattrickData → Match → MatchOfficials → RefereeAssistant1 → RefereeCountryName (Element)
- Added Output: HattrickData → Match → MatchOfficials → RefereeAssistant1 → RefereeTeamId (Element)
- Added Output: HattrickData → Match → MatchOfficials → RefereeAssistant1 → RefereeTeamname (Element)
- Added Output: HattrickData → Match → MatchOfficials → RefereeAssistant2 (Container)
- Added Output: HattrickData → Match → MatchOfficials → RefereeAssistant2 → RefereeId (Element)
- Added Output: HattrickData → Match → MatchOfficials → RefereeAssistant2 → RefereeName (Element)
- Added Output: HattrickData → Match → MatchOfficials → RefereeAssistant2 → RefereeCountryId (Element)
- Added Output: HattrickData → Match → MatchOfficials → RefereeAssistant2 → RefereeCountryName (Element)
- Added Output: HattrickData → Match → MatchOfficials → RefereeAssistant2 → RefereeTeamId (Element)
- Added Output: HattrickData → Match → MatchOfficials → RefereeAssistant2 → RefereeTeamname (Element)

### Version 2.4 (2013-03-04)
- Removed Output: HattrickData → UserIsSupporter (Element)
- Added Output: HattrickData → UserSupporterTier (Element)

### Version 2.3 (2012-03-06)
- Removed Input: isYouth
- Added Input: sourceSystem
- Removed Output: HattrickData → IsYouth (Element)
- Added Output: HattrickData → SourceSystem (Element)

### Version 2.2 (2012-02-15)
- Added Output: HattrickData → Match → Injuries (Container)
- Added Output: HattrickData → Match → Injuries → Injury (Container)
- Added Output: HattrickData → Match → Injuries → Injury → InjuryPlayerID (Element)
- Added Output: HattrickData → Match → Injuries → Injury → InjuryPlayerName (Element)
- Added Output: HattrickData → Match → Injuries → Injury → InjuryTeamID (Element)
- Added Output: HattrickData → Match → Injuries → Injury → InjuryType (Element)
- Added Output: HattrickData → Match → Injuries → Injury → InjuryMinute (Element)

### Version 2.1 (2012-02-01)
- Removed Output: HattrickData → Match → EventList → Event → EventKey (Element)
- Added Output: HattrickData → Match → EventList → Event → EventTypeID (Element)
- Added Output: HattrickData → Match → EventList → Event → EventVariation (Element)

### Version 2.0 (2011-01-11)
- Added Output: HattrickData → Match → HomeTeam → RatingIndirectSetPiecesDef (Element)
- Added Output: HattrickData → Match → HomeTeam → RatingIndirectSetPiecesAtt (Element)
- Added Output: HattrickData → Match → AwayTeam → RatingIndirectSetPiecesDef (Element)
- Added Output: HattrickData → Match → AwayTeam → RatingIndirectSetPiecesAtt (Element)

### Version 1.9 (2010-11-08)
- Removed Output: HattrickData → Match → HomeTeam → Dress (Element)
- Removed Output: HattrickData → Match → AwayTeam → Dress (Element)
- Added Output: HattrickData → Match → HomeTeam → DressURI (Element)
- Added Output: HattrickData → Match → AwayTeam → DressURI (Element)

### Version 1.8
- Removed Output: HattrickData → UserHasClubhouse (Element)

### Version 1.7
- Removed Output: HattrickData → UserHasClubhouse (Element)
- Added Output: HattrickData → Match → HomeTeam → Formation (Element)
- Added Output: HattrickData → Match → AwayTeam → Formation (Element)

### Version 1.6
- Removed Output: HattrickData → Match → Scorers → Goal (Container)
- Removed Output: HattrickData → Match → Scorers → Goal → ScorerPlayerID (Element)
- Removed Output: HattrickData → Match → Scorers → Goal → ScorerPlayerName (Element)
- Removed Output: HattrickData → Match → Scorers → Goal → ScorerTeamID (Element)
- Removed Output: HattrickData → Match → Scorers → Goal → ScorerHomeGoals (Element)
- Removed Output: HattrickData → Match → Scorers → Goal → ScorerAwayGoals (Element)
- Removed Output: HattrickData → Match → Scorers → Goal → ScorerMinute (Element)
- Removed Output: HattrickData → Match → Bookings → Booking (Container)
- Removed Output: HattrickData → Match → Bookings → Booking → BookingPlayerID (Element)
- Removed Output: HattrickData → Match → Bookings → Booking → BookingPlayerName (Element)
- Removed Output: HattrickData → Match → Bookings → Booking → BookingTeamID (Element)
- Removed Output: HattrickData → Match → Bookings → Booking → BookingType (Element)
- Removed Output: HattrickData → Match → Bookings → Booking → BookingMinute (Element)
- Added Output: HattrickData → Match → Scorers → Goal (Container)
- Added Output: HattrickData → Match → Scorers → Goal → ScorerPlayerID (Element)
- Added Output: HattrickData → Match → Scorers → Goal → ScorerPlayerName (Element)
- Added Output: HattrickData → Match → Scorers → Goal → ScorerTeamID (Element)
- Added Output: HattrickData → Match → Scorers → Goal → ScorerHomeGoals (Element)
- Added Output: HattrickData → Match → Scorers → Goal → ScorerAwayGoals (Element)
- Added Output: HattrickData → Match → Scorers → Goal → ScorerMinute (Element)
- Added Output: HattrickData → Match → Bookings → Booking (Container)
- Added Output: HattrickData → Match → Bookings → Booking → BookingPlayerID (Element)
- Added Output: HattrickData → Match → Bookings → Booking → BookingPlayerName (Element)
- Added Output: HattrickData → Match → Bookings → Booking → BookingTeamID (Element)
- Added Output: HattrickData → Match → Bookings → Booking → BookingType (Element)
- Added Output: HattrickData → Match → Bookings → Booking → BookingMinute (Element)

### Version 1.5 {default}
- First version

## Input
Only available inputs in this file version are shown.
required file = matchdetails
optional version = 
3.1
3.0
2.9
2.8
2.7
2.6
2.5
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
optional matchEvents = 
false {default}
true
Whether match events shall be shown or not
false {default}
Show no match events.
true
Show match events.
required matchID : unsigned Integer
Which match to show details for.
required sourceSystem : sourceSystem
Specifies which source system the match belongs to, this replace the old isYouth param

## Output (actionType=false)
- **HattrickData** — Hattrick Data Container for matchEvents=false
  - SourceSystem : sourceSystem — Which sourceSystem the request was made for.
  - UserSupporterTier : supporterTier — Indicates which Supporter package the fetching user has, or empty if not a Supporter.
  - **Match** — A match.
    - MatchID : unsigned Integer — The globally unique identifier of the match.
    - MatchType : MatchTypeID — Integer defining the type of match.
    - MatchContextId : unsigned Integer — This will be either LeagueLevelUnitId (for League), CupId (Cup, Hattrick Masters, World Cup and U-20 World Cup), LadderId, TournamentId, or 0 for friendly, qualification, single matches and preparation matches.
    - MatchRuleId : MatchRuleID — This will contain the rules that's in place for the ladder or tournament in question. It's provided for all matchtypes and defaults to 0 = No rules
    - CupLevel : unsigned Integer — 1 = National/Divisional cup, 2 = Challenger cup, 3 = Consolation cup. 0 if MatchType is not 3.
    - CupLevelIndex : unsigned Integer — In Challenger cups: 1 = Emerald (start week 2), 2 = Ruby (start week 3), 3 = Sapphire (start week 4). Always 1 for National/Divisional (main cups) and Consolation cups. 0 if MatchType is not 3.
    - MatchDate : DateTime — The start date and time (kick-off) of the match.
    - FinishedDate : DateTime* — The date and time when the match was finished. Not supplied until it is finished.
    - AddedMinutes : Integer — How many minutes of added time the match had. Not supplied until it is finished.
    - **HomeTeam** — The Home (formal, not taking arena location into account) team of the match.
      - HomeTeamID : Integer — The teamID of the Home team in the match. (Negative for street teams.)
      - HomeTeamName : String — The team name of the Home team in the match.
      - DressURI : URI — URI to an image representing the dress of the team. FOR SENIORS ONLY
      - Formation : String — Character string (x-x-x) representing the formation the team started with.
      - HomeGoals : unsigned Integer — The current number of goals in the match for the home team.
      - TacticType : MatchTacticType — Integer value representing the the team tactic type specified for the match.
      - TacticSkill : SkillLevel — Integer value representing the tactic skill of the team.
      - RatingMidfield : MatchRating — Integer value representing the midfield rating of the team.
      - RatingRightDef : MatchRating — Integer value representing the right defence rating of the team.
      - RatingMidDef : MatchRating — Integer value representing the middle defence rating of the team.
      - RatingLeftDef : MatchRating — Integer value representing the left defence rating of the team.
      - RatingRightAtt : MatchRating — Integer value representing the right attack rating of the team.
      - RatingMidAtt : MatchRating — Integer value representing the middle attack rating of the team.
      - RatingLeftAtt : MatchRating — Integer value representing the left attack rating of the team.
      - TeamAttitude : MatchTeamAttitude — Integer value representing the the team attitude specified for the match. Only shown to the owner of the team - otherwise omitted.
      - RatingIndirectSetPiecesDef : MatchRating — Integer value representing the defensive indirect set pieces rating of the team.
      - RatingIndirectSetPiecesAtt : MatchRating — Integer value representing the attacking indirect set pieces rating of the team.
      - NrOfChancesLeft : unsigned Integer — Number of chances the team had on the left side.
      - NrOfChancesCenter : unsigned Integer — Number of chances the team had in the middle.
      - NrOfChancesRight : unsigned Integer — Number of chances the team had on the right side.
      - NrOfChancesSpecialEvents : unsigned Integer — Number of special event chances.
      - NrOfChancesOther : unsigned Integer — Number of chances of other types that doesn't fit into any of the above categories.
    - **AwayTeam** — The Away (formal, not taking arena location into account) team of the match.
      - AwayTeamID : Integer — The teamID of the Away team in the match. (Negative for street teams.)
      - AwayTeamName : String — The team name of the Away team in the match.
      - DressURI : URI — URI to an image representing the dress of the team. FOR SENIORS ONLY
      - Formation : String — Character string (x-x-x) representing the formation the team started with.
      - AwayGoals : unsigned Integer — The current number of goals in the match for the away team.
      - TacticType : MatchTacticType — Integer value representing the the team tactic type specified for the match.
      - TacticSkill : SkillLevel — Integer value representing the tactic skill of the team.
      - RatingMidfield : MatchRating — Integer value representing the midfield rating of the team.
      - RatingRightDef : MatchRating — Integer value representing the right defence rating of the team.
      - RatingMidDef : MatchRating — Integer value representing the middle defence rating of the team.
      - RatingLeftDef : MatchRating — Integer value representing the left defence rating of the team.
      - RatingRightAtt : MatchRating — Integer value representing the right attack rating of the team.
      - RatingMidAtt : MatchRating — Integer value representing the middle attack rating of the team.
      - RatingLeftAtt : MatchRating — Integer value representing the left attack rating of the team.
      - TeamAttitude : MatchTeamAttitude — Integer value representing the the team attitude specified for the match. Only shown to the owner of the team - otherwise omitted.
      - RatingIndirectSetPiecesDef : MatchRating — Integer value representing the defensive indirect set pieces rating of the team.
      - RatingIndirectSetPiecesAtt : MatchRating — Integer value representing the attacking indirect set pieces rating of the team.
      - NrOfChancesLeft : unsigned Integer — Number of chances the team had on the left side.
      - NrOfChancesCenter : unsigned Integer — Number of chances the team had in the middle.
      - NrOfChancesRight : unsigned Integer — Number of chances the team had on the right side.
      - NrOfChancesSpecialEvents : unsigned Integer — Number of special event chances.
      - NrOfChancesOther : unsigned Integer — Number of chances of other types that doesn't fit into any of the above categories.
    - **Arena** — Container for the data about the arena where the match was played.
      - ArenaID : unsigned Integer — The globally unique arenaID.
      - ArenaName : String — The name of the arena.
      - WeatherID : WeatherID — Integer defining the weather during the match.
      - SoldTotal : unsigned Integer — The total number of sold tickets (number of spectators).
      - SoldTerraces : unsigned Integer — The number of sold tickets for the teraces section.
      - SoldBasic : unsigned Integer — The number of sold tickets for the basic seating section.
      - SoldRoof : unsigned Integer — The number of sold tickets for the seating under roof section.
      - SoldVIP : unsigned Integer — The number of sold tickets for the VIP section.
    - **MatchOfficials** — Container for data about the match officials, only shown for matches with SourceSystem=Hattrick.
      - **Referee** — Container for the referee.
        - RefereeId : unsigned Integer — The globally unique PlayerID for the referee.
        - RefereeName : String — The name of the referee.
        - RefereeCountryId : unsigned Integer — The countryID of the country the referee is from.
        - RefereeCountryName : String — The name of the country the referee is from.
        - RefereeTeamId : unsigned Integer — The teamId of the team that the referee is a Hall of Fame member of.
        - RefereeTeamname : String — The name of the team that the referee is a Hall of Fame member of.
      - **RefereeAssistant1** — Container for the first assistant referee.
        - RefereeId : unsigned Integer — The globally unique PlayerID for the referee.
        - RefereeName : String — The name of the referee.
        - RefereeCountryId : unsigned Integer — The countryID of the country the referee is from.
        - RefereeCountryName : String — The name of the country the referee is from.
        - RefereeTeamId : unsigned Integer — The teamId of the team that the referee is a Hall of Fame member of.
        - RefereeTeamname : String — The name of the team that the referee is a Hall of Fame member of.
      - **RefereeAssistant2** — Container for the second assistant referee.
        - RefereeId : unsigned Integer — The globally unique PlayerID for the referee.
        - RefereeName : String — The name of the referee.
        - RefereeCountryId : unsigned Integer — The countryID of the country the referee is from.
        - RefereeCountryName : String — The name of the country the referee is from.
        - RefereeTeamId : unsigned Integer — The teamId of the team that the referee is a Hall of Fame member of.
        - RefereeTeamname : String — The name of the team that the referee is a Hall of Fame member of.
    - **Scorers** — Container for the data about the scorers.
      - **Goal** — Attribute: 'Index' : unsigned Integer The relative number of the goal in the list.
        - ScorerPlayerID : unsigned Integer — The globally unique PlayerID for the scorer.
        - ScorerPlayerName : String — The name of the scorer.
        - ScorerTeamID : Integer — The teamID of the team that scored.
        - ScorerHomeGoals : unsigned Integer — The number of goals for the home team after the goal.
        - ScorerAwayGoals : unsigned Integer — The number of goals for the away team after the goal.
        - ScorerMinute : unsigned Integer — The minute that the goal was scored in.
        - MatchPart : matchPart — Which part of the match the goal was scored in.
    - **Bookings** — Container for the data about the warned or sent off players.
      - **Booking** — Attribute: 'Index' : unsigned Integer The relative number of the booking in the list.
        - BookingPlayerID : unsigned Integer — The globally unique PlayerID for the booked player.
        - BookingPlayerName : String — The name of the booked player.
        - BookingTeamID : Integer — The teamID of the team that suffered the booking.
        - BookingType : unsigned Integer — Specifies whether it was a warning (BookingType=1) or red card (BookingType=2).
        - BookingMinute : unsigned Integer — The minute that the booking happened.
        - MatchPart : matchPart — Which part of the match the booking happened.
    - **Injuries** — Container for the data about injured players.
      - **Injury** — Attribute: 'Index' : unsigned Integer The relative number of the booking in the list.
        - InjuryPlayerID : unsigned Integer — The globally unique PlayerID for the injured player.
        - InjuryPlayerName : String — The name of the injured player.
        - InjuryTeamID : Integer — The teamID of the team that suffered the injury.
        - InjuryType : unsigned Integer — Specifies whether it was a bruise (InjuryType=1) or an injury (InjuryType=2).
        - InjuryMinute : unsigned Integer — The minute that the injury happened.
        - MatchPart : matchPart — Which part of the match the booking happened.
    - PossessionFirstHalfHome : unsigned Integer — Home team's ball possession in % for first half.
    - PossessionFirstHalfAway : unsigned Integer — Away team's ball possession in % for first half.
    - PossessionSecondHalfHome : unsigned Integer — Home team's ball possession in % for second half.
    - PossessionSecondHalfAway : unsigned Integer — Away team's ball possession in % for second half.

## Output (actionType=true)
- **HattrickData** — Hattrick Data Container for matchEvents=true
  - SourceSystem : sourceSystem — Which sourceSystem the request was made for.
  - UserSupporterTier : supporterTier — Indicates which Supporter package the fetching user has, or empty if not a Supporter.
  - **Match** — A match.
    - MatchID : unsigned Integer — The globally unique identifier of the match.
    - MatchType : MatchTypeID — Integer defining the type of match.
    - MatchContextId : unsigned Integer — This will be either LeagueLevelUnitId (for League), CupId (Cup, Hattrick Masters, World Cup and U-20 World Cup), LadderId, TournamentId, or 0 for friendly, qualification, single matches and preparation matches.
    - MatchRuleId : MatchRuleID — This will contain the rules that's in place for the ladder or tournament in question. It's provided for all matchtypes and defaults to 0 = No rules
    - CupLevel : unsigned Integer — 1 = National/Divisional cup, 2 = Challenger cup, 3 = Consolation cup. 0 if MatchType is not 3.
    - CupLevelIndex : unsigned Integer — In Challenger cups: 1 = Emerald (start week 2), 2 = Ruby (start week 3), 3 = Sapphire (start week 4). Always 1 for National/Divisional (main cups) and Consolation cups. 0 if MatchType is not 3.
    - MatchDate : DateTime — The start date and time (kick-off) of the match.
    - FinishedDate : DateTime* — The date and time when the match was finished. Not supplied until it is finished.
    - AddedMinutes : Integer — How many minutes of added time the match had. Not supplied until it is finished.
    - **HomeTeam** — The Home (formal, not taking arena location into account) team of the match.
      - HomeTeamID : Integer — The teamID of the Home team in the match. (Negative for street teams.)
      - HomeTeamName : String — The team name of the Home team in the match.
      - DressURI : URI — URI to an image representing the dress of the team. FOR SENIORS ONLY
      - Formation : String — Character string (x-x-x) representing the formation the team started with.
      - HomeGoals : unsigned Integer — The current number of goals in the match for the home team.
      - TacticType : MatchTacticType — Integer value representing the the team tactic type specified for the match.
      - TacticSkill : SkillLevel — Integer value representing the tactic skill of the team.
      - RatingMidfield : MatchRating — Integer value representing the midfield rating of the team.
      - RatingRightDef : MatchRating — Integer value representing the right defence rating of the team.
      - RatingMidDef : MatchRating — Integer value representing the middle defence rating of the team.
      - RatingLeftDef : MatchRating — Integer value representing the left defence rating of the team.
      - RatingRightAtt : MatchRating — Integer value representing the right attack rating of the team.
      - RatingMidAtt : MatchRating — Integer value representing the middle attack rating of the team.
      - RatingLeftAtt : MatchRating — Integer value representing the left attack rating of the team.
      - TeamAttitude : MatchTeamAttitude — Integer value representing the the team attitude specified for the match. Only shown to the owner of the team - otherwise omitted.
      - RatingIndirectSetPiecesDef : MatchRating — Integer value representing the defensive indirect set pieces rating of the team.
      - RatingIndirectSetPiecesAtt : MatchRating — Integer value representing the attacking indirect set pieces rating of the team.
      - NrOfChancesLeft : unsigned Integer — Number of chances the team had on the left side.
      - NrOfChancesCenter : unsigned Integer — Number of chances the team had in the middle.
      - NrOfChancesRight : unsigned Integer — Number of chances the team had on the right side.
      - NrOfChancesSpecialEvents : unsigned Integer — Number of special event chances.
      - NrOfChancesOther : unsigned Integer — Number of chances of other types that doesn't fit into any of the above categories.
    - **AwayTeam** — The Away (formal, not taking arena location into account) team of the match.
      - AwayTeamID : Integer — The teamID of the Away team in the match. (Negative for street teams.)
      - AwayTeamName : String — The team name of the Away team in the match.
      - DressURI : URI — URI to an image representing the dress of the team. FOR SENIORS ONLY
      - Formation : String — Character string (x-x-x) representing the formation the team started with.
      - AwayGoals : unsigned Integer — The current number of goals in the match for the away team.
      - TacticType : MatchTacticType — Integer value representing the the team tactic type specified for the match.
      - TacticSkill : SkillLevel — Integer value representing the tactic skill of the team.
      - RatingMidfield : MatchRating — Integer value representing the midfield rating of the team.
      - RatingRightDef : MatchRating — Integer value representing the right defence rating of the team.
      - RatingMidDef : MatchRating — Integer value representing the middle defence rating of the team.
      - RatingLeftDef : MatchRating — Integer value representing the left defence rating of the team.
      - RatingRightAtt : MatchRating — Integer value representing the right attack rating of the team.
      - RatingMidAtt : MatchRating — Integer value representing the middle attack rating of the team.
      - RatingLeftAtt : MatchRating — Integer value representing the left attack rating of the team.
      - TeamAttitude : MatchTeamAttitude — Integer value representing the the team attitude specified for the match. Only shown to the owner of the team - otherwise omitted.
      - RatingIndirectSetPiecesDef : MatchRating — Integer value representing the defensive indirect set pieces rating of the team.
      - RatingIndirectSetPiecesAtt : MatchRating — Integer value representing the attacking indirect set pieces rating of the team.
      - NrOfChancesLeft : unsigned Integer — Number of chances the team had on the left side.
      - NrOfChancesCenter : unsigned Integer — Number of chances the team had in the middle.
      - NrOfChancesRight : unsigned Integer — Number of chances the team had on the right side.
      - NrOfChancesSpecialEvents : unsigned Integer — Number of special event chances.
      - NrOfChancesOther : unsigned Integer — Number of chances of other types that doesn't fit into any of the above categories.
    - **Arena** — Container for the data about the arena where the match was played.
      - ArenaID : unsigned Integer — The globally unique arenaID.
      - ArenaName : String — The name of the arena.
      - WeatherID : WeatherID — Integer defining the weather during the match.
      - SoldTotal : unsigned Integer — The total number of sold tickets (number of spectators).
      - SoldTerraces : unsigned Integer — The number of sold tickets for the teraces section.
      - SoldBasic : unsigned Integer — The number of sold tickets for the basic seating section.
      - SoldRoof : unsigned Integer — The number of sold tickets for the seating under roof section.
      - SoldVIP : unsigned Integer — The number of sold tickets for the VIP section.
    - **MatchOfficials** — Container for data about the match officials, only shown for matches with SourceSystem=Hattrick.
      - **Referee** — Container for the referee.
        - RefereeId : unsigned Integer — The globally unique PlayerID for the referee.
        - RefereeName : String — The name of the referee.
        - RefereeCountryId : unsigned Integer — The countryID of the country the referee is from.
        - RefereeCountryName : String — The name of the country the referee is from.
        - RefereeTeamId : unsigned Integer — The teamId of the team that the referee is a Hall of Fame member of.
        - RefereeTeamname : String — The name of the team that the referee is a Hall of Fame member of.
      - **RefereeAssistant1** — Container for the first assistant referee.
        - RefereeId : unsigned Integer — The globally unique PlayerID for the referee.
        - RefereeName : String — The name of the referee.
        - RefereeCountryId : unsigned Integer — The countryID of the country the referee is from.
        - RefereeCountryName : String — The name of the country the referee is from.
        - RefereeTeamId : unsigned Integer — The teamId of the team that the referee is a Hall of Fame member of.
        - RefereeTeamname : String — The name of the team that the referee is a Hall of Fame member of.
      - **RefereeAssistant2** — Container for the second assistant referee.
        - RefereeId : unsigned Integer — The globally unique PlayerID for the referee.
        - RefereeName : String — The name of the referee.
        - RefereeCountryId : unsigned Integer — The countryID of the country the referee is from.
        - RefereeCountryName : String — The name of the country the referee is from.
        - RefereeTeamId : unsigned Integer — The teamId of the team that the referee is a Hall of Fame member of.
        - RefereeTeamname : String — The name of the team that the referee is a Hall of Fame member of.
    - **Scorers** — Container for the data about the scorers.
      - **Goal** — Attribute: 'Index' : unsigned Integer The relative number of the goal in the list.
        - ScorerPlayerID : unsigned Integer — The globally unique PlayerID for the scorer.
        - ScorerPlayerName : String — The name of the scorer.
        - ScorerTeamID : Integer — The teamID of the team that scored.
        - ScorerHomeGoals : unsigned Integer — The number of goals for the home team after the goal.
        - ScorerAwayGoals : unsigned Integer — The number of goals for the away team after the goal.
        - ScorerMinute : unsigned Integer — The minute that the goal was scored in.
        - MatchPart : matchPart — Which part of the match the goal was scored in.
    - **Bookings** — Container for the data about the warned or sent off players.
      - **Booking** — Attribute: 'Index' : unsigned Integer The relative number of the booking in the list.
        - BookingPlayerID : unsigned Integer — The globally unique PlayerID for the booked player.
        - BookingPlayerName : String — The name of the booked player.
        - BookingTeamID : Integer — The teamID of the team that suffered the booking.
        - BookingType : unsigned Integer — Specifies whether it was a warning (BookingType=1) or red card (BookingType=2).
        - BookingMinute : unsigned Integer — The minute that the booking happened.
        - MatchPart : matchPart — Which part of the match the booking happened.
    - **Injuries** — Container for the data about injured players.
      - **Injury** — Attribute: 'Index' : unsigned Integer The relative number of the booking in the list.
        - InjuryPlayerID : unsigned Integer — The globally unique PlayerID for the injured player.
        - InjuryPlayerName : String — The name of the injured player.
        - InjuryTeamID : Integer — The teamID of the team that suffered the injury.
        - InjuryType : unsigned Integer — Specifies whether it was a bruise (InjuryType=1) or an injury (InjuryType=2).
        - InjuryMinute : unsigned Integer — The minute that the injury happened.
        - MatchPart : matchPart — Which part of the match the injury happened.
    - PossessionFirstHalfHome : unsigned Integer — Home team's ball possession in % for first half.
    - PossessionFirstHalfAway : unsigned Integer — Away team's ball possession in % for first half.
    - PossessionSecondHalfHome : unsigned Integer — Home team's ball possession in % for second half.
    - PossessionSecondHalfAway : unsigned Integer — Away team's ball possession in % for second half.
    - **EventList** — Container for all the events.
      - **Event** — Attribute: 'Index' : unsigned Integer The order of the events
        - Minute : unsigned Integer — The match minute when the event happened.
        - MatchPart : matchPart — Which part of the match the event happened.
        - EventTypeID : unsigned Integer — A unique key defining what type of event it was. # See information MatchEventTypes
        - EventVariation : unsigned Integer — The variation defines which text is used to describe the event.
        - EventText : String — String describing the event as it would appear in the match report.
        - SubjectTeamID : Integer — Integer that, for goals and chances, is indicating the attacking team. For other event, this field usually indicates the team that is doing something. It may indicate other things for other events - exactly what is not documented here and the SubjectTeamID information, while free to use for CHPPs is 'unsupported' and used 'at your own risk'.
        - SubjectPlayerID : unsigned Integer — Integer that, for goals and chances, is indicating the scorer or the player failing to score. For other events, this field usually indicates the primarily active player. It may indicate other things for other events and like SubjectTeamID is 'unsupported' and used 'at your own risk'.
        - ObjectPlayerID : unsigned Integer — Integer that, for regular goals and chances, is indicating the defending team's goal keeper. For special event chances/goals, the ObjectPlayer is often the assisting player of the attacking team, but can be any other player that is involved in the event. For other events, this field is used very freely and can indicate any kind of relevant integer data. Like SubjectPlayerID and SubjectTeamID, it is 'unsupported' and used at 'your own risk'.

## References
#MatchEventTypes
Documentation of the match events in Hattrick here!
