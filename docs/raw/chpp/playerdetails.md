# playerdetails

Detailed information for a player

## Changelog
### Version 3.2 (2026-02-09)
- Include player gender.
- Added Output: HattrickData → Player → GenderID (Element)

### Version 3.1 (2025-02-25)
- Include player assists.
- Added Output: HattrickData → Player → AssistsCurrentTeam (Element)
- Added Output: HattrickData → Player → CareerAssists (Element)

### Version 3.0 (2023-09-07)
- Removed non-player trainers (now added it to the staff list) and replaced TrainerSkill with TrainerSkillLevel, for player trainers only.
- Removed Output: HattrickData → Player → TrainerData → TrainerSkill (Element)
- Added Output: HattrickData → Player → TrainerData → TrainerSkillLevel (Element)

### Version 2.9 (2022-03-04)
- Added mother team ID and name.
- Added Output: HattrickData → Player → MotherClub (Container)
- Added Output: HattrickData → Player → MotherClub → TeamID (Element)
- Added Output: HattrickData → Player → MotherClub → TeamName (Element)

### Version 2.8 (2019-11-04)
- Added MatchesCurrentTeam.
- Added Output: HattrickData → Player → MatchesCurrentTeam (Element)

### Version 2.7 (2016-05-17)
- Added GoalsCurrentTeam.
- Added Output: HattrickData → Player → GoalsCurrentTeam (Element)

### Version 2.6 (2014-02-05)
- Removed Input: bidAmount
- Added Input: bidAmount
- Added Input: maxBidAmount
- Added Output: HattrickData → Player → TransferDetails → MaxBid (Element)

### Version 2.5 (2013-12-12)
- Added Input: teamId
- Added Input: bidAmount
- Added Action for 'actionType': view (Shows information for the player with the corresponding playerId.)
- Added Action for 'actionType': placeBid (Places a bid on the player and then shows all the information, for Supporters only. (Requires "place_bid" scope.))
- Added Output: HattrickData → Player → TransferDetails (Container)
- Added Output: HattrickData → Player → TransferDetails → AskingPrice (Element)
- Added Output: HattrickData → Player → TransferDetails → Deadline (Element)
- Added Output: HattrickData → Player → TransferDetails → HighestBid (Element)
- Added Output: HattrickData → Player → TransferDetails → BidderTeam (Container)
- Added Output: HattrickData → Player → TransferDetails → BidderTeam → TeamID (Element)
- Added Output: HattrickData → Player → TransferDetails → BidderTeam → TeamName (Element)

### Version 2.4 (2013-03-04)
- Removed Output: HattrickData → UserIsSupporter (Element)
- Added Output: HattrickData → UserSupporterTier (Element)

### Version 2.3 (2012-04-12)
- Added FriendliesGoals.

### Version 2.2 (2011-12-05)
- Added ArrivalDate.
- Added Output: HattrickData → Player → ArrivalDate (Element)
- Added Output: HattrickData (Container)
- Added Output: HattrickData → View (Container)

### Version 2.1 (2011-10-21)
- BestMatch-container was removed because of performance-issues. Previous version will only return an empty container from now on!
- Removed Output: HattrickData → Player → BestMatch (Container)

### Version 2.0 (2011-10-13)
- Added Loyalty and MotherClubBonus. Please note that the value for Loyalty will be 0 and the value for MotherClubBonus will be false until the loyalty features are activated in the beginning of season 47.
- Added Output: HattrickData → Player → Loyalty (Element)
- Added Output: HattrickData → Player → MotherClubBonus (Element)

### Version 1.9 (2011-05-12)
- Fixed bug, so RatingEndOfGame contains correct value (was always 0 before).

### Version 1.8 (2011-02-25)
- Added Input: includeMatchInfo

### Version 1.7 (2011-02-03)
- Added Output: HattrickData → Player → BestMatch (Container)

### Version 1.6 (2010-12-16)
- Added Output: HattrickData → Player → NativeCountryID (Element)

### Version 1.5
- Added Output: HattrickData → Player → LastMatch (Container)
- Added Output: HattrickData → Player → LastMatch → Date (Element)
- Added Output: HattrickData → Player → LastMatch → MatchId (Element)
- Added Output: HattrickData → Player → LastMatch → PositionCode (Element)
- Added Output: HattrickData → Player → LastMatch → PlayedMinutes (Element)
- Added Output: HattrickData → Player → LastMatch → Rating (Element)
- Added Output: HattrickData → Player → LastMatch → RatingEndOfGame (Element)

### Version 1.4
- Removed Output: HattrickData → UserHasClubhouse (Element)
- Removed Output: HattrickData → IsYouth (Element)
- Added Output: HattrickData → Player → NextBirthDay (Element)
- Added Output: HattrickData → Player → PlayerLanguageID (Element)

### Version 1.3
- Removed Output: HattrickData → IsPlayingMatch (Element)
- Removed Output: HattrickData → Player → Playername (Element)
- Added Output: HattrickData → Player → FirstName (Element)
- Added Output: HattrickData → Player → NickName (Element)
- Added Output: HattrickData → Player → LastName (Element)

### Version 1.2 {default}
- First version

## Input
Only available inputs in this file version are shown.
required file = playerdetails
optional version = 
3.2
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
1.4
1.3
1.2
optional actionType = 
view {default}
placeBid
Indicates what type of action the page should perform.
view {default}
Shows information for the player with the corresponding playerId.
placeBid
Places a bid on the player and then shows all the information, for Supporters only. (Requires "place_bid" scope.)
required playerID : unsigned Integer
The player to get information for.
optional includeMatchInfo : Boolean{Default:false}
Set it to 'true' to get LastMatch Container.
required_1 teamId : unsigned Integer
Team id of the team placing the bid. Must refer to a senior team managed by requesting user.
optional bidAmount : Money
Bid amount in SEK.
optional maxBidAmount : Money
Maximum bid amount in SEK (autobid).
required_1: Input teamId is required for actionType=placeBid. Only for version 2.5 bidAmount is also required.

## Output (actionType=view)
- **HattrickData** — Hattrick Data Container
  - UserSupporterTier : supporterTier — Indicates which Supporter package the fetching user has, or empty if not a Supporter.
  - **Player** — Container for the data about the player.
    - PlayerID : unsigned Integer — The globally unique playerID.
    - FirstName : String — Player's FirstName name.
    - NickName : String — Player's NickName name.
    - LastName : String — Player's LastName name.
    - PlayerNumber : unsigned Integer — The number (1 to 99) that the owner of the team has assigned this player.
    - PlayerCategoryID : PlayerCategoryID — The CategoryID assigned by the owner. Only visible to owner.
    - OwnerNotes : String (HTML encoded) — The notes owner has set. Only visible to owner.
    - Age : unsigned Integer — The age of the player in years.
    - AgeDays : unsigned Integer — The amount of days past since last birthday.
    - NextBirthDay : DateTime — The aproximate Date/time of next birthday.
    - GenderID : GenderID — The gender of the player
    - ArrivalDate : DateTime — The date of arrival to the team.
    - PlayerForm : SkillLevel — The current form, given as an integer with 1 indicating disastrous, 2 for wretched and so on.
    - Cards : unsigned Integer — The number of currently accumulated cards. If the player is suspended, this will return 3 (whether the player has actually accumulated 3 bookings, or if he has become suspended by being sent off after 2 bookings in the same game). If a match is running this value is not available
    - InjuryLevel : Integer — The number of weeks the player is predicted to be injured. If the player is bruised, a value of 0 is returned. If the player is healthy, a value of -1 is returned. If a match is running this value is not available
    - Statement : String (HTML encoded) — If the owner is a HT Supporter, he may have provided a Statement from his players.
    - PlayerLanguage : String — This container and its elements is only shown if the user has supporter
    - PlayerLanguageID : unsigned Integer — This container and its elements is only shown if the user has supporter
    - **TrainerData** — Container for the skills that relate to coach/trainer ability. Only provided if the player has been made into a trainer, 'has gained trainer license'.
      - TrainerType : unsigned Integer — 0 = defensive, 1 = offensive, 2 = balanced
      - TrainerSkillLevel : unsigned Integer — The trainer skill level, from 1 (lowest) to 5 (highest).
    - Agreeability : PlayerAgreeability — The agreeability of the player.
    - Aggressiveness : PlayerAggressiveness — The aggressiveness of the player.
    - Honesty : PlayerHonesty — The honesty of the player.
    - Experience : SkillLevel — The experience of the player.
    - Loyalty : SkillLevel — The loyalty of the player.
    - MotherClubBonus : Boolean — Whether the player gains the mother club bonus.
    - **MotherClub** — Container for the mother club of the player.
      - TeamID : unsigned Integer — The globally unique TeamID of the mother club of the player.
      - TeamName : String — The full team name of the mother club of the player.
    - Leadership : SkillLevel — The leadership of the player.
    - Specialty : SpecialtyID — Integer indicating the specialty (if any) of the player.
    - NativeCountryID : unsigned Integer — CountryID of the country where the player was born.
    - NativeLeagueID : unsigned Integer — LeagueID of the league where the player was born.
    - NativeLeagueName : String — LeagueName of the league where the player was born.
    - TSI : unsigned Integer — The Total Skill Index (TSI) of the player.
    - **OwningTeam** — Container for the team that owns the player.
      - TeamID : unsigned Integer — The globally unique TeamID of the team owning the player.
      - TeamName : String — The full team name of the team owning the player.
      - LeagueID : unsigned Integer — LeagueID for the league of the team owning the player.
    - Salary : Money — The salary of the player. The container is empty If the player has no owner.
    - IsAbroad : Boolean* — The abroad status of the player, given as an integer. true for beeing in home country, false for beeing in a team abroad. The container is empty If the player has no owner.
    - **PlayerSkills** — Container for the skills of a player. Please not that only the owner can see the skill, except Stamina with v1.7 and greater.
      - StaminaSkill : SkillLevel — The stamina skill of the player.
      - KeeperSkill : SkillLevel — The keeper skill of the player.
      - PlaymakerSkill : SkillLevel — The playmaker skill of the player.
      - ScorerSkill : SkillLevel — The scorer skill of the player.
      - PassingSkill : SkillLevel — The passing skill of the player.
      - WingerSkill : SkillLevel — The winger skill of the player.
      - DefenderSkill : SkillLevel — The defender skill of the player.
      - SetPiecesSkill : SkillLevel — The set pieces skill of the player.
    - Caps : unsigned Integer — The number of matches played for the national team.
    - CapsU20 : unsigned Integer — The number of matches played for the U-20 national team.
    - CareerGoals : unsigned Integer — The total number of goals throughout this player's carreer. If a match is running this value is not available
    - CareerHattricks : unsigned Integer — The total number of hattricks throughout this player's carreer. If a match is running this value is not available
    - LeagueGoals : unsigned Integer — The number of goals in the league this season. If a match is running this value is not available
    - CupGoals : unsigned Integer — Attribute: 'Available' : Boolean If a match is running this value is not available
    - FriendliesGoals : unsigned Integer — The number of goals in friendlies this season. If a match is running this value is not available
    - MatchesCurrentTeam : unsigned Integer — The total number of matches for current team. If a match is running this value is not available
    - GoalsCurrentTeam : unsigned Integer — The number of goals in friendlies this season. If a match is running this value is not available
    - AssistsCurrentTeam : unsigned Integer — The number of assists for the current team.
    - CareerAssists : unsigned Integer — The number of assists through out the whole player's career
    - NationalTeamID : unsigned Integer* — If the player is enrolled on a national team, this is that national team's ID.
    - NationalTeamName : String* — If the player is enrolled on a national team, this is that national team's name.
    - TransferListed : Boolean — Is the player on transfer list?
    - **TransferDetails** — Container for the transfer details for the player. Empty if not transfer listed.
      - AskingPrice : Money — The original asking price.
      - Deadline : DateTime — The transfer deadline. Can be even a few hours in the past.
      - HighestBid : Money — The highest bid made for the player so far. Returns 0 if no bids have been made.
      - MaxBid : Money — The maximum bid (autobid) set by the user. Not shown if not set.
      - **BidderTeam** — Container with the information about the team holding the highest bid. Empty if no bids have been made.
        - TeamID : unsigned Integer — The globally unique TeamID.
        - TeamName : String — The full team name.
    - **LastMatch** — Last played match. If MatchId = 0 it either means that the player has not played a match yet, or that he's in a bot team.
      - Date : DateTime — Date of last played match.
      - MatchId : unsigned Integer — Id of the last played match.
      - PositionCode : MatchRoleID — Played position in the last match.
      - PlayedMinutes : unsigned Integer — Minutes played in the last match.
      - Rating : Float — The players average rating in the game.
      - RatingEndOfGame : Float — The players rating at the end of the game.

## Output (actionType=placeBid)
- **HattrickData** — Container for the data about setting training.
  - **View** — The same data as in actionType=view is shown here.
