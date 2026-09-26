# players

Players

## Changelog
### Version 2.8 (2026-02-11)
- Include player gender.
- Added Output: HattrickData → Team → PlayerList → Player → GenderID (Element)

### Version 2.7 (2025-02-25)
- Include player assists.
- Added Output: HattrickData → Team → PlayerList → Player → AssistsCurrentTeam (Element)
- Added Output: HattrickData → Team → PlayerList → Player → CareerAssists (Element)

### Version 2.6 (2023-09-07)
- Removed non-player trainers (now added it to the staff list) and replaced TrainerSkill with TrainerSkillLevel, for player trainers only.
- Removed Output: HattrickData → Team → PlayerList → Player → TrainerData → TrainerSkill (Element)
- Added Output: HattrickData → Team → PlayerList → Player → TrainerData → TrainerSkillLevel (Element)

### Version 2.5 (2021-01-19)
- Change TransferListed field to boolean as described in the docs
- Removed Output: HattrickData → Team → PlayerList → Player → TransferListed (Element)
- Added Output: HattrickData → Team → PlayerList → Player → TransferListed (Element)

### Version 2.4 (2019-11-04)
- Added ArrivalDate, Owner Notes, Number of matches and goals for current team
- Added Output: HattrickData → Team → PlayerList → Player → ArrivalDate (Element)
- Added Output: HattrickData → Team → PlayerList → Player → OwnerNotes (Element)
- Added Output: HattrickData → Team → PlayerList → Player → MatchesCurrentTeam (Element)
- Added Output: HattrickData → Team → PlayerList → Player → GoalsCurrentTeam (Element)

### Version 2.3 (2013-03-04)
- Removed Output: HattrickData → UserIsSupporter (Element)
- Added Output: HattrickData → UserSupporterTier (Element)

### Version 2.2 (2011-10-21)
- BestMatch-container was removed because of performance-issues. Previous version will only return an empty container from now on!

### Version 2.1 (2011-10-13)
- Added Loyalty and MotherClubBonus. Please note that the value for Loyalty will be 0 and the value for MotherClubBonus will be false until the loyalty features are activated in the beginning of season 47.
- Removed Output: HattrickData → Team → PlayerList → Player → BestMatch (Container)
- Added Output: HattrickData → Team → PlayerList → Player → Loyalty (Element)
- Added Output: HattrickData → Team → PlayerList → Player → MotherClubBonus (Element)

### Version 2.0 (2011-05-12)
- Added Output: HattrickData → Team → PlayerList → Player → BestMatch (Container)

### Version 1.9 (2011-02-25)
- Added Input: includeMatchInfo

### Version 1.8 (2011-02-03)
- Added Output: HattrickData → Team → PlayerList → Player → LastMatch (Container)
- Added Output: HattrickData → Team → PlayerList → Player → LastMatch → Date (Element)
- Added Output: HattrickData → Team → PlayerList → Player → LastMatch → MatchId (Element)
- Added Output: HattrickData → Team → PlayerList → Player → LastMatch → PositionCode (Element)
- Added Output: HattrickData → Team → PlayerList → Player → LastMatch → PlayedMinutes (Element)
- Added Output: HattrickData → Team → PlayerList → Player → LastMatch → Rating (Element)
- Added Output: HattrickData → Team → PlayerList → Player → LastMatch → RatingEndOfGame (Element)

### Version 1.7

### Version 1.6
- Added Output: HattrickData → Team → PlayerList → Player → CountryID (Element)

### Version 1.5
- Removed Output: HattrickData → Team → PlayerList → Player → Playername (Element)
- Added Output: HattrickData → Team → PlayerList → Player → FirstName (Element)
- Added Output: HattrickData → Team → PlayerList → Player → NickName (Element)
- Added Output: HattrickData → Team → PlayerList → Player → LastName (Element)

### Version 1.4 {default}
- First version

## Input
Only available inputs in this file version are shown.
required file = players
optional version = 
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
optional actionType = 
view {default}
viewOldies
viewOldCoaches
Indicates what type of action the page should perform.
view {default}
View the list of players belonging to a team.
viewOldies
View the list of players who no longer belong to the club but were promoted from the youth squad of this team, or who were part of the start-up squad of the team.
viewOldCoaches
View list of former players that now are coaches.
optional orderBy : String{Default:PlayerNumber}
The field that the outdata PlayerList should be sorted by.
optional teamID : unsigned Integer{Default:Logged on user's teamID}
The team for which the data should apply to.
optional includeMatchInfo : Boolean{Default:false}
Include LastMatch container for all players.

## Output (actionType=view)
- **HattrickData** — Hattrick Data Container for actionType=view
  - UserSupporterTier : supporterTier — Indicates which Supporter package the fetching user has, or empty if not a Supporter.
  - IsYouth : Boolean — Always false, for youth players, use dedicated youth XML files.
  - ActionType : String — The requested ActionType.
  - IsPlayingMatch : Boolean — Indicated if the player is currently playing in a match.
  - **Team** — Container for the team to which the data applies.
    - TeamID : unsigned Integer — The globally unique TeamID.
    - TeamName : String — The full team name.
    - **PlayerList** — Container for the list of players.
      - **Player** — Container for the data about the player.
        - PlayerID : unsigned Integer — The globally unique playerID.
        - FirstName : String — Player's FirstName name.
        - NickName : String — Player's NickName name.
        - LastName : String — Player's LastName name.
        - PlayerNumber : unsigned Integer — The number (1 to 99) that the owner of the team has assigned this player.
        - Age : unsigned Integer — The age of the player in years.
        - AgeDays : unsigned Integer — The amount of days past since last birthday.
        - GenderID : GenderID — The gender of the player
        - ArrivalDate : DateTime — The date of arrival to current team
        - OwnerNotes : String — The owner notes for the player. Only visible to owner.
        - TSI : unsigned Integer — The Total Skill Index (TSI) of the player.
        - PlayerForm : SkillLevel — The current form, given as an integer with 1 indicating disastrous, 2 for wretched and so on.
        - Statement : String (HTML encoded) — If the owner is a HT Supporter, he may have provided a Statement from his players.
        - Experience : SkillLevel — The experience of the player.
        - Loyalty : SkillLevel — The loyalty of the player.
        - MotherClubBonus : Boolean — Whether the player gains the mother club bonus.
        - Leadership : SkillLevel — The leadership of the player.
        - Salary : Money — The salary of the player. The container is empty If the player has no owner.
        - IsAbroad : Boolean* — The abroad status of the player, given as an integer. true for beeing in home country, false for beeing in a team abroad. The container is empty If the player has no owner.
        - Agreeability : PlayerAgreeability — The agreeability of the player.
        - Aggressiveness : PlayerAggressiveness — The aggressiveness of the player.
        - Honesty : PlayerHonesty — The honesty of the player.
        - LeagueGoals : unsigned Integer — The number of goals in the league this season. If a match is running this value is not available
        - CupGoals : unsigned Integer — The number of goals in the cup this season. If a match is running this value is not available
        - FriendliesGoals : unsigned Integer — The number of goals in friendlies this season. If a match is running this value is not available
        - CareerGoals : unsigned Integer — The total number of goals throughout this player's carreer. If a match is running this value is not available
        - CareerHattricks : unsigned Integer — The total number of hattricks throughout this player's carreer. If a match is running this value is not available
        - MatchesCurrentTeam : unsigned Integer — The total number of matches for current team. If a match is running this value is not available
        - GoalsCurrentTeam : unsigned Integer — The total number of goals for current team. If a match is running this value is not available
        - AssistsCurrentTeam : unsigned Integer — The number of assists for the current team.
        - CareerAssists : unsigned Integer — The number of assists through out the whole player's career
        - Specialty : SpecialtyID — Integer indicating the specialty (if any) of the player.
        - TransferListed : Boolean — Is the player on transfer list?
        - NationalTeamID : unsigned Integer* — If the player is enrolled on a national team, this is that national team's ID.
        - CountryID : unsigned Integer — CountryID of the country where the player was born.
        - Caps : unsigned Integer — The number of matches played for the national team.
        - CapsU20 : unsigned Integer — The number of matches played for the U-20 national team.
        - Cards : unsigned Integer — The number of currently accumulated cards. If the player is suspended, this will return 3 (whether the player has actually accumulated 3 bookings, or if he has become suspended by being sent off after 2 bookings in the same game).
        - InjuryLevel : Integer — The number of weeks the player is predicted to be injured. If the player is bruised, a value of 0 is returned. If the player is healthy, a value of -1 is returned.
        - StaminaSkill : SkillLevel — The stamina skill of the player (visible for everyone from v1.8 on)
        - KeeperSkill : SkillLevel — The keeper skill of the player.
        - PlaymakerSkill : SkillLevel — The playmaker skill of the player.
        - ScorerSkill : SkillLevel — The scorer skill of the player.
        - PassingSkill : SkillLevel — The passing skill of the player.
        - WingerSkill : SkillLevel — The winger skill of the player.
        - DefenderSkill : SkillLevel — The defender skill of the player.
        - SetPiecesSkill : SkillLevel — The set pieces skill of the player.
        - PlayerCategoryId : PlayerCategoryID — The category set for the player.
        - **TrainerData** — Container for the skills that relate to coach/trainer ability. Only provided if the player has been made into a trainer, 'has gained trainer license'.
          - TrainerType : unsigned Integer — 0 = defensive, 1 = offensive, 2 = balanced
          - TrainerSkillLevel : unsigned Integer — The trainer skill level, from 1 (lowest) to 5 (highest).
        - **LastMatch** — Last played match. If MatchId = 0 it either means that the player has not played a match yet, or that he's in a bot team.
          - Date : DateTime — Date of last played match.
          - MatchId : unsigned Integer — Id of the last played match.
          - PositionCode : MatchRoleID — Played position in the last match.
          - PlayedMinutes : unsigned Integer — Minutes played in the last match.
          - Rating : Float — The players average rating in the game.
          - RatingEndOfGame : Float — The players rating at the end of the game.

## Output (actionType=viewOldies)
- **HattrickData** — Hattrick Data Container for actionType=viewOldies
  - UserSupporterTier : supporterTier — Indicates which Supporter package the fetching user has, or empty if not a Supporter.
  - IsYouth : Boolean — Always false, for youth players, use dedicated youth XML files.
  - ActionType : String — The requested ActionType.
  - IsPlayingMatch : Boolean — Indicated if the player is currently playing in a match.
  - **Team** — Container for the team to which the data applies.
    - TeamID : unsigned Integer — The globally unique TeamID.
    - TeamName : String — The full team name.
    - **PlayerList** — Container for the list of players.
      - **Player** — Container for the data about the player.
        - PlayerID : unsigned Integer — The globally unique playerID.
        - FirstName : String — Player's FirstName name.
        - NickName : String — Player's NickName name.
        - LastName : String — Player's LastName name.
        - PlayerNumber : unsigned Integer — The number (1 to 99) that the owner of the team has assigned this player.
        - Age : unsigned Integer — The age of the player in years.
        - AgeDays : unsigned Integer — The amount of days past since last birthday.
        - TSI : unsigned Integer — The Total Skill Index (TSI) of the player.
        - PlayerForm : SkillLevel — The current form, given as an integer with 1 indicating disastrous, 2 for wretched and so on.
        - Statement : String (HTML encoded) — If the owner is a HT Supporter, he may have provided a Statement from his players.
        - Experience : SkillLevel — The experience of the player.
        - Leadership : SkillLevel — The leadership of the player.
        - Salary : Money — The salary of the player. The container is empty If the player has no owner.
        - IsAbroad : Boolean* — The abroad status of the player, given as an integer. true for beeing in home country, false for beeing in a team abroad. The container is empty If the player has no owner.
        - Agreeability : PlayerAgreeability — The agreeability of the player.
        - Aggressiveness : PlayerAggressiveness — The aggressiveness of the player.
        - Honesty : PlayerHonesty — The honesty of the player.
        - LeagueGoals : unsigned Integer — The number of goals in the league this season. If a match is running this value is not available
        - CupGoals : unsigned Integer — The number of goals in the cup this season. If a match is running this value is not available
        - FriendliesGoals : unsigned Integer — The number of goals in friendlies this season. If a match is running this value is not available
        - CareerGoals : unsigned Integer — The total number of goals throughout this player's carreer. If a match is running this value is not available
        - CareerHattricks : unsigned Integer — The total number of hattricks throughout this player's carreer. If a match is running this value is not available
        - AssistsCurrentTeam : unsigned Integer — The number of assists for the current team.
        - CareerAssists : unsigned Integer — The number of assists through out the whole player's career
        - Specialty : SpecialtyID — Integer indicating the specialty (if any) of the player.
        - TransferListed : Boolean — Is the player on transfer list?
        - NationalTeamID : unsigned Integer* — If the player is enrolled on a national team, this is that national team's ID.
        - CountryID : unsigned Integer — CountryID of the country where the player was born.
        - Caps : unsigned Integer — The number of matches played for the national team.
        - CapsU20 : unsigned Integer — The number of matches played for the U-20 national team.
        - Cards : unsigned Integer — The number of currently accumulated cards. If the player is suspended, this will return 3 (whether the player has actually accumulated 3 bookings, or if he has become suspended by being sent off after 2 bookings in the same game). If a match is running this value is not available
        - InjuryLevel : Integer — The number of weeks the player is predicted to be injured. If the player is bruised, a value of 0 is returned. If the player is healthy, a value of -1 is returned. If a match is running this value is not available
        - **OwningTeam** — Container for the team that owns the player.
          - TeamID : unsigned Integer — The globally unique TeamID of the team owning the player.
          - TeamName : String — The full team name of the team owning the player.
          - LeagueName : unsigned Integer — LeagueName for the league of the team owning the player.
        - **LastMatch** — Last played match. If MatchId = 0 it either means that the player has not played a match yet, or that he's in a bot team.
          - Date : DateTime — Date of last played match.
          - MatchId : unsigned Integer — Id of the last played match.
          - PositionCode : MatchRoleID — Played position in the last match.
          - PlayedMinutes : unsigned Integer — Minutes played in the last match.
          - Rating : Float — The players average rating in the game.
          - RatingEndOfGame : Float — The players rating at the end of the game.

## Output (actionType=viewOldCoaches)
- **HattrickData** — Hattrick Data Container for actionType=viewOldCoaches
  - UserSupporterTier : supporterTier — Indicates which Supporter package the fetching user has, or empty if not a Supporter.
  - IsYouth : Boolean — Always false, for youth players, use dedicated youth XML files.
  - ActionType : String — The requested ActionType.
  - IsPlayingMatch : Boolean — Indicated if the player is currently playing in a match.
  - **Team** — Container for the team to which the data applies.
    - TeamID : unsigned Integer — The globally unique TeamID.
    - TeamName : String — The full team name.
    - **PlayerList** — Container for the list of players.
      - **Player** — Container for the data about the player.
        - PlayerID : unsigned Integer — The globally unique playerID.
        - FirstName : String — Player's FirstName name.
        - NickName : String — Player's NickName name.
        - LastName : String — Player's LastName name.
        - PlayerNumber : unsigned Integer — The number (1 to 99) that the owner of the team has assigned this player.
        - Age : unsigned Integer — The age of the player in years.
        - AgeDays : unsigned Integer — The amount of days past since last birthday.
        - TSI : unsigned Integer — The Total Skill Index (TSI) of the player.
        - PlayerForm : SkillLevel — The current form, given as an integer with 1 indicating disastrous, 2 for wretched and so on.
        - Statement : String (HTML encoded) — If the owner is a HT Supporter, he may have provided a Statement from his players.
        - Experience : SkillLevel — The experience of the player.
        - Leadership : SkillLevel — The leadership of the player.
        - Salary : Money — The salary of the player. The container is empty If the player has no owner.
        - IsAbroad : Boolean* — The abroad status of the player, given as an integer. true for beeing in home country, false for beeing in a team abroad. The container is empty If the player has no owner.
        - Agreeability : PlayerAgreeability — The agreeability of the player.
        - Aggressiveness : PlayerAggressiveness — The aggressiveness of the player.
        - Honesty : PlayerHonesty — The honesty of the player.
        - LeagueGoals : unsigned Integer — The number of goals in the league this season.
        - CupGoals : unsigned Integer — The number of goals in the cup this season.
        - FriendliesGoals : unsigned Integer — The number of goals in friendlies this season.
        - CareerGoals : unsigned Integer — The total number of goals throughout this player's carreer.
        - CareerHattricks : unsigned Integer — The total number of hattricks throughout this player's carreer.
        - AssistsCurrentTeam : unsigned Integer — The number of assists for the current team.
        - CareerAssists : unsigned Integer — The number of assists through out the whole player's career
        - Specialty : SpecialtyID — Integer indicating the specialty (if any) of the player.
        - TransferListed : Boolean — Is the player on transfer list?
        - NationalTeamID : unsigned Integer* — If the player is enrolled on a national team, this is that national team's ID.
        - CountryID : unsigned Integer — CountryID of the country where the player was born.
        - Caps : unsigned Integer — The number of matches played for the national team.
        - CapsU20 : unsigned Integer — The number of matches played for the U-20 national team.
        - Cards : unsigned Integer — The number of currently accumulated cards. If the player is suspended, this will return 3 (whether the player has actually accumulated 3 bookings, or if he has become suspended by being sent off after 2 bookings in the same game).
        - InjuryLevel : Integer — The number of weeks the player is predicted to be injured. If the player is bruised, a value of 0 is returned. If the player is healthy, a value of -1 is returned.
        - **TrainerData** — Container for the skills that relate to coach/trainer ability. Only provided if the player has been made into a trainer, 'has gained trainer license'.
          - TrainerType : unsigned Integer — 0 = defensive, 1 = offensive, 2 = balanced
          - TrainerSkill : SkillLevel — The trainer skill.
        - **LastMatch** — Last played match. If MatchId = 0 it either means that the player has not played a match yet, or that he's in a bot team.
          - Date : DateTime — Date of last played match.
          - MatchId : unsigned Integer — Id of the last played match.
          - PositionCode : MatchRoleID — Played position in the last match.
          - PlayedMinutes : unsigned Integer — Minutes played in the last match.
          - Rating : Float — The players average rating in the game.
          - RatingEndOfGame : Float — The players rating at the end of the game.
