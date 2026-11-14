# youthplayerdetails

Detailed information for a youth player

## Changelog
### Version 1.4 (2026-11-10)
- Add the coach's training comments (TrainingReports).
- Add UserId to OwningYouthTeam.
- Added Output: HattrickData → YouthPlayer → OwningYouthTeam → UserId (Element)
- Added Output: HattrickData → YouthPlayer → TrainingReports (Container)
- Added Output: HattrickData → YouthPlayer → TrainingReports → TrainingReport (Container)
- Added Output: HattrickData → YouthPlayer → TrainingReports → TrainingReport → ReportText (Element)
- Added Output: HattrickData → YouthPlayer → TrainingReports → TrainingReport → ReportDate (Element)
- Added Output: HattrickData → YouthPlayer → TrainingReports → TrainingReport → ReportType (Element)
- Added Output: HattrickData → YouthPlayer → TrainingReports → TrainingReport → ReportSkillType (Element)
- Added Output: HattrickData → YouthPlayer → TrainingReports → TrainingReport → ReportSkillLevel (Element)

### Version 1.3 (2026-02-10)
- Add player gender.
- Added Output: HattrickData → YouthPlayer → GenderID (Element)

### Version 1.2 (2022-03-03)
- Add native country.
- Add new error code (62) for a previous youth player that is now a senior player.
- Added Output: HattrickData → YouthPlayer → NativeCountryID (Element)
- Added Output: HattrickData → YouthPlayer → NativeCountryName (Element)

### Version 1.1 (2014-01-23)
- Fixed incorrect value of Specialty when the player have no or unknown specialty (old: empty, new: 0).
- Removed Output: HattrickData → YouthPlayer → Specialty (Element)
- Added Output: HattrickData → YouthPlayer → Specialty (Element)

### Version 1.0 (2011-11-15) {default}
- First version

## Input
Only available inputs in this file version are shown.
required file = youthplayerdetails
optional version = 
1.4
1.3
1.2
1.1
1.0
optional actionType = 
details {default}
unlockskills
Indicates what type of action the page should perform.
details {default}
Show the player information.
unlockskills
Unlock the skills of the player before showing the player information (as details). (Requires "manage_youthplayers" scope.)
optional youthPlayerId : unsigned Integer
The player to get information for.
optional showScoutCall : Boolean{Default:false}
Set it to 'true' to get the information the scout provided when you pulled the player.
optional showLastMatch : Boolean{Default:false}
Set it to 'true' to get LastMatch container

## Output (actionType=details)
- **HattrickData** — Hattrick Data Container
  - **YouthPlayer** — Container for the data about the youth player.
    - YouthPlayerID : unsigned Integer — The globally unique youthPlayerID.
    - FirstName : String — Player's FirstName name.
    - NickName : String — Player's NickName name.
    - LastName : String — Player's LastName name.
    - Age : unsigned Integer — The age of the player in years.
    - AgeDays : unsigned Integer — The amount of days past since last birthday.
    - GenderID : GenderID — The player gender
    - ArrivalDate : DateTime — The datetime, when the player was pulled to the youth team.
    - CanBePromotedIn : unsigned Integer — Number in days, when the player can be promoted to senior squad
    - PlayerNumber : unsigned Integer — The number (1 to 99) that the owner of the team has assigned this player.
    - Statement : String (HTML encoded) — If the owner is a HT Supporter, he may have provided a Statement from his players.
    - NativeCountryID : unsigned Integer — CountryID of the country where the player was born.
    - NativeCountryName : String — CountryName of the country where the player was born
    - OwnerNotes : String (HTML encoded) — The notes owner has set. Only visible to owner.
    - PlayerCategoryID : PlayerCategoryID — The CategoryID assigned by the owner. Only visible to owner.
    - Cards : unsigned Integer — The number of currently accumulated cards. If the player is suspended, this will return 3 (whether the player has actually accumulated 3 bookings, or if he has become suspended by being sent off after 2 bookings in the same game). If a match is running this value is not available
    - InjuryLevel : Integer — The number of weeks the player is predicted to be injured. If the player is bruised, a value of 0 is returned. If the player is healthy, a value of -1 is returned. If a match is running this value is not available
    - Specialty : SpecialtyID — Integer indicating the specialty (if he has any and if it is already revealed). Will be 0 on no or unknown specialty.
    - CareerGoals : unsigned Integer — The total number of goals throughout this player's carreer. Empty if the team is playing a match.
    - CareerHattricks : unsigned Integer — The total number of hattricks throughout this player's carreer. Empty if the team is playing a match.
    - LeagueGoals : unsigned Integer — The number of goals in the league this season. Empty if the team is playing a match.
    - FriendlyGoals : unsigned Integer — The number of goals made in friendlies this season. Empty if the team is playing a match.
    - **OwningYouthTeam** — Container for the youth team that owns the player.
      - YouthTeamID : unsigned Integer — The globally unique YouthTeamID of the team owning the youth player.
      - YouthTeamName : String — The full youth team name of the team owning the youth player.
      - YouthTeamLeagueID : unsigned Integer — YouthLeagueID for the league of the youth team owning the youth player.
      - UserId : unsigned Integer — The globally unique UserId of the manager who owns the youth team.
      - **SeniorTeam** — Container for the senior team the youth team belongs to.
        - SeniorTeamID : unsigned Integer — The globally unique SeniorTeamID.
        - SeniorTeamName : String — The full senior team name.
    - **PlayerSkills** — Container for the skills of a player. Only visible to owner.
      - KeeperSkill : unsigned Integer — Attribute: 'IsAvailable' : Boolean Indicates if this skill has been unlocked yet. Attribute: 'IsMaxReached' : Boolean Indicates if the skill cant be trained anymore (false if current or max is not available). Attribute: 'MayUnlock' : Boolean Indicates if this skill may be unlocked using the unlockSkills actionType. The current keeper skill of the player. Empty if IsAvailable=false.
      - KeeperSkillMax : unsigned Integer — Attribute: 'IsAvailable' : Boolean Indicates if this skill cap has been unlocked yet. Attribute: 'MayUnlock' : Boolean Indicates if this skill may be unlocked using the unlockSkills actionType. The keeper skill cap of the player. Empty if IsAvailable=false.
      - DefenderSkill : unsigned Integer — Attribute: 'IsAvailable' : Boolean Indicates if this skill has been unlocked yet. Attribute: 'IsMaxReached' : Boolean Indicates if the skill cant be trained anymore (false if current or max is not available). Attribute: 'MayUnlock' : Boolean Indicates if this skill may be unlocked using the unlockSkills actionType. The current defender skill of the player. Empty if IsAvailable=false.
      - DefenderSkillMax : unsigned Integer — Attribute: 'IsAvailable' : Boolean Indicates if this skill cap has been unlocked yet. Attribute: 'MayUnlock' : Boolean Indicates if this skill may be unlocked using the unlockSkills actionType. The defender skill cap of the player. Empty if IsAvailable=false.
      - PlaymakerSkill : unsigned Integer — Attribute: 'IsAvailable' : Boolean Indicates if this skill has been unlocked yet. Attribute: 'IsMaxReached' : Boolean Indicates if the skill cant be trained anymore (false if current or max is not available). Attribute: 'MayUnlock' : Boolean Indicates if this skill may be unlocked using the unlockSkills actionType. The current playmaker skill of the player. Empty if IsAvailable=false.
      - PlaymakerSkillMax : unsigned Integer — Attribute: 'IsAvailable' : Boolean Indicates if this skill cap has been unlocked yet. Attribute: 'MayUnlock' : Boolean Indicates if this skill may be unlocked using the unlockSkills actionType. The playmaker skill cap of the player. Empty if IsAvailable=false.
      - WingerSkill : unsigned Integer — Attribute: 'IsAvailable' : Boolean Indicates if this skill has been unlocked yet. Attribute: 'IsMaxReached' : Boolean Indicates if the skill cant be trained anymore (false if current or max is not available). Attribute: 'MayUnlock' : Boolean Indicates if this skill may be unlocked using the unlockSkills actionType. The current winger skill of the player. Empty if IsAvailable=false.
      - WingerSkillMax : unsigned Integer — Attribute: 'IsAvailable' : Boolean Indicates if this skill cap has been unlocked yet. Attribute: 'MayUnlock' : Boolean Indicates if this skill may be unlocked using the unlockSkills actionType. The winger skill cap of the player. Empty if IsAvailable=false.
      - ScorerSkill : unsigned Integer — Attribute: 'IsAvailable' : Boolean Indicates if this skill has been unlocked yet. Attribute: 'IsMaxReached' : Boolean Indicates if the skill cant be trained anymore (false if current or max is not available). Attribute: 'MayUnlock' : Boolean Indicates if this skill may be unlocked using the unlockSkills actionType. The current scorer skill of the player. Empty if IsAvailable=false.
      - ScorerSkillMax : unsigned Integer — Attribute: 'IsAvailable' : Boolean Indicates if this skill cap has been unlocked yet. Attribute: 'MayUnlock' : Boolean Indicates if this skill may be unlocked using the unlockSkills actionType. The scorer skill cap of the player. Empty if IsAvailable=false.
      - PassingSkill : unsigned Integer — Attribute: 'IsAvailable' : Boolean Indicates if this skill has been unlocked yet. Attribute: 'IsMaxReached' : Boolean Indicates if the skill cant be trained anymore (false if current or max is not available). Attribute: 'MayUnlock' : Boolean Indicates if this skill may be unlocked using the unlockSkills actionType. The current passing skill of the player. Empty if IsAvailable=false.
      - PassingSkillMax : unsigned Integer — Attribute: 'IsAvailable' : Boolean Indicates if this skill cap has been unlocked yet. Attribute: 'MayUnlock' : Boolean Indicates if this skill may be unlocked using the unlockSkills actionType. The passing skill cap of the player. Empty if IsAvailable=false.
      - SetPiecesSkill : unsigned Integer — Attribute: 'IsAvailable' : Boolean Indicates if this skill has been unlocked yet. Attribute: 'IsMaxReached' : Boolean Indicates if the skill cant be trained anymore (false if current or max is not available). Attribute: 'MayUnlock' : Boolean Indicates if this skill may be unlocked using the unlockSkills actionType. The current set pieces skill of the player. Empty if IsAvailable=false.
      - SetPiecesSkillMax : unsigned Integer — Attribute: 'IsAvailable' : Boolean Indicates if this skill cap has been unlocked yet. Attribute: 'MayUnlock' : Boolean Indicates if this skill may be unlocked using the unlockSkills actionType. The set pieces skill cap of the player. Empty if IsAvailable=false.
    - **ScoutCall** — A container for information about the scout call. Only available with showScoutCall-flag as true.
      - **Scout** — The scout who suggested this youth player.
        - ScoutId : unsigned Integer — The unique scoutID.
        - ScoutName : String — The name of the scout.
      - ScoutingRegionID : unsigned Integer — The unique regionID the scout was looking for players while he scouted this youth player.
      - **ScoutComments** — Container for scout comments.
        - **ScoutComment** — A scout comment.
          - CommentText : String — The comment text.
          - CommentType : ScoutCommentTypeID — The type the comment belongs too.
          - CommentVariation : unsigned Integer — The text variant of the comment for given CommentType.
          - CommentSkillType : ScoutCommentSkillTypeID — The skill named in the comment (0 if there is no skill). Will be youthPlayerId if CommentType=1.
          - CommentSkillLevel : SkillLevel — The skill level named in the comment (0 if there is no skill level named). Will be the player age if CommentType=1.
    - **TrainingReports** — The coach's training comments about the player, newest first. Only shown for your own youth players, and only when your club has Gold supporter. Empty if the coach has not made any comments.
      - **TrainingReport** — A training comment.
        - ReportText : String — The comment text, in the language of the user.
        - ReportDate : DateTime — The date and time of the comment.
        - ReportType : unsigned Integer — The type of the comment.
        - ReportSkillType : ScoutCommentSkillTypeID — The skill named in the comment. 0 if the comment does not name a skill.
        - ReportSkillLevel : SkillLevel — The skill level named in the comment. Only set when ReportType is 18, 19, 28, 29 or 30, otherwise 0.
    - **LastMatch** — Last played match. Only available with showLastMatch-flag as true.
      - Date : DateTime — Date of the last played match.
      - YouthMatchID : unsigned Integer — The unique YouthMatchID of the last played match.
      - PositionCode : MatchRoleID — Played position in the last match.
      - PlayedMinutes : unsigned Integer — Minutes played in the last match.
      - Rating : Float — The players average rating in the last played match.

## Output (actionType=unlockskills)
- **HattrickData** — Hattrick Data Container
  - **YouthPlayer** — Container for the data about the youth player.
    - YouthPlayerID : unsigned Integer — The globally unique youthPlayerID.
    - FirstName : String — Player's FirstName name.
    - NickName : String — Player's NickName name.
    - LastName : String — Player's LastName name.
    - Age : unsigned Integer — The age of the player in years.
    - AgeDays : unsigned Integer — The amount of days past since last birthday.
    - ArrivalDate : DateTime — The datetime, when the player was pulled to the youth team.
    - CanBePromotedIn : unsigned Integer — Number in days, when the player can be promoted to senior squad
    - PlayerNumber : unsigned Integer — The number (1 to 99) that the owner of the team has assigned this player.
    - Statement : String (HTML encoded) — If the owner is a HT Supporter, he may have provided a Statement from his players.
    - NativeCountryID : unsigned Integer — CountryID of the country where the player was born.
    - NativeCountryName : String — CountryName of the country where the player was born
    - OwnerNotes : String (HTML encoded) — The notes owner has set. Only visible to owner.
    - PlayerCategoryID : PlayerCategoryID — The CategoryID assigned by the owner. Only visible to owner.
    - Cards : unsigned Integer — The number of currently accumulated cards. If the player is suspended, this will return 3 (whether the player has actually accumulated 3 bookings, or if he has become suspended by being sent off after 2 bookings in the same game).
    - InjuryLevel : Integer — The number of weeks the player is predicted to be injured. If the player is bruised, a value of 0 is returned. If the player is healthy, a value of -1 is returned.
    - Specialty : SpecialtyID — Integer indicating the specialty (if he has any and if it is already revealed). Will be 0 on no or unknown specialty.
    - CareerGoals : unsigned Integer — The total number of goals throughout this player's carreer. Empty if the team is playing a match.
    - CareerHattricks : unsigned Integer — The total number of hattricks throughout this player's carreer. Empty if the team is playing a match.
    - LeagueGoals : unsigned Integer — The number of goals in the league this season. Empty if the team is playing a match.
    - FriendlyGoals : unsigned Integer — The number of goals made in friendlies this season. Empty if the team is playing a match.
    - **OwningYouthTeam** — Container for the youth team that owns the player.
      - YouthTeamID : unsigned Integer — The globally unique YouthTeamID of the team owning the youth player.
      - YouthTeamName : String — The full youth team name of the team owning the youth player.
      - YouthTeamLeagueID : unsigned Integer — YouthLeagueID for the league of the youth team owning the youth player.
      - UserId : unsigned Integer — The globally unique UserId of the manager who owns the youth team.
      - **SeniorTeam** — Container for the senior team the youth team belongs to.
        - SeniorTeamID : unsigned Integer — The globally unique SeniorTeamID.
        - SeniorTeamName : String — The full senior team name.
    - **PlayerSkills** — Container for the skills of a player. Only visible to owner.
      - KeeperSkill : unsigned Integer — Attribute: 'IsAvailable' : Boolean Indicates if this skill has been unlocked yet. Attribute: 'IsMaxReached' : Boolean Indicates if the skill cant be trained anymore (false if current or max is not available). Attribute: 'MayUnlock' : Boolean Indicates if this skill may be unlocked using the unlockSkills actionType. The current keeper skill of the player. Empty if IsAvailable=false.
      - KeeperSkillMax : unsigned Integer — Attribute: 'IsAvailable' : Boolean Indicates if this skill cap has been unlocked yet. Attribute: 'MayUnlock' : Boolean Indicates if this skill may be unlocked using the unlockSkills actionType. The keeper skill cap of the player. Empty if IsAvailable=false.
      - DefenderSkill : unsigned Integer — Attribute: 'IsAvailable' : Boolean Indicates if this skill has been unlocked yet. Attribute: 'IsMaxReached' : Boolean Indicates if the skill cant be trained anymore (false if current or max is not available). Attribute: 'MayUnlock' : Boolean Indicates if this skill may be unlocked using the unlockSkills actionType. The current defender skill of the player. Empty if IsAvailable=false.
      - DefenderSkillMax : unsigned Integer — Attribute: 'IsAvailable' : Boolean Indicates if this skill cap has been unlocked yet. Attribute: 'MayUnlock' : Boolean Indicates if this skill may be unlocked using the unlockSkills actionType. The defender skill cap of the player. Empty if IsAvailable=false.
      - PlaymakerSkill : unsigned Integer — Attribute: 'IsAvailable' : Boolean Indicates if this skill has been unlocked yet. Attribute: 'IsMaxReached' : Boolean Indicates if the skill cant be trained anymore (false if current or max is not available). Attribute: 'MayUnlock' : Boolean Indicates if this skill may be unlocked using the unlockSkills actionType. The current playmaker skill of the player. Empty if IsAvailable=false.
      - PlaymakerSkillMax : unsigned Integer — Attribute: 'IsAvailable' : Boolean Indicates if this skill cap has been unlocked yet. Attribute: 'MayUnlock' : Boolean Indicates if this skill may be unlocked using the unlockSkills actionType. The playmaker skill cap of the player. Empty if IsAvailable=false.
      - WingerSkill : unsigned Integer — Attribute: 'IsAvailable' : Boolean Indicates if this skill has been unlocked yet. Attribute: 'IsMaxReached' : Boolean Indicates if the skill cant be trained anymore (false if current or max is not available). Attribute: 'MayUnlock' : Boolean Indicates if this skill may be unlocked using the unlockSkills actionType. The current winger skill of the player. Empty if IsAvailable=false.
      - WingerSkillMax : unsigned Integer — Attribute: 'IsAvailable' : Boolean Indicates if this skill cap has been unlocked yet. Attribute: 'MayUnlock' : Boolean Indicates if this skill may be unlocked using the unlockSkills actionType. The winger skill cap of the player. Empty if IsAvailable=false.
      - ScorerSkill : unsigned Integer — Attribute: 'IsAvailable' : Boolean Indicates if this skill has been unlocked yet. Attribute: 'IsMaxReached' : Boolean Indicates if the skill cant be trained anymore (false if current or max is not available). Attribute: 'MayUnlock' : Boolean Indicates if this skill may be unlocked using the unlockSkills actionType. The current scorer skill of the player. Empty if IsAvailable=false.
      - ScorerSkillMax : unsigned Integer — Attribute: 'IsAvailable' : Boolean Indicates if this skill cap has been unlocked yet. Attribute: 'MayUnlock' : Boolean Indicates if this skill may be unlocked using the unlockSkills actionType. The scorer skill cap of the player. Empty if IsAvailable=false.
      - PassingSkill : unsigned Integer — Attribute: 'IsAvailable' : Boolean Indicates if this skill has been unlocked yet. Attribute: 'IsMaxReached' : Boolean Indicates if the skill cant be trained anymore (false if current or max is not available). Attribute: 'MayUnlock' : Boolean Indicates if this skill may be unlocked using the unlockSkills actionType. The current passing skill of the player. Empty if IsAvailable=false.
      - PassingSkillMax : unsigned Integer — Attribute: 'IsAvailable' : Boolean Indicates if this skill cap has been unlocked yet. Attribute: 'MayUnlock' : Boolean Indicates if this skill may be unlocked using the unlockSkills actionType. The passing skill cap of the player. Empty if IsAvailable=false.
      - SetPiecesSkill : unsigned Integer — Attribute: 'IsAvailable' : Boolean Indicates if this skill has been unlocked yet. Attribute: 'IsMaxReached' : Boolean Indicates if the skill cant be trained anymore (false if current or max is not available). Attribute: 'MayUnlock' : Boolean Indicates if this skill may be unlocked using the unlockSkills actionType. The current set pieces skill of the player. Empty if IsAvailable=false.
      - SetPiecesSkillMax : unsigned Integer — Attribute: 'IsAvailable' : Boolean Indicates if this skill cap has been unlocked yet. Attribute: 'MayUnlock' : Boolean Indicates if this skill may be unlocked using the unlockSkills actionType. The set pieces skill cap of the player. Empty if IsAvailable=false.
    - **ScoutCall** — A container for information about the scout call. Only available with showScoutCall-flag as true.
      - **Scout** — The scout who suggested this youth player.
        - ScoutID : unsigned Integer — The unique scoutID.
        - ScoutName : String — The name of the scout.
      - ScoutingRegionID : unsigned Integer — The unique regionID the scout was looking for players while he scouted this youth player.
      - **ScoutComments** — Container for scout comments.
        - **ScoutComment** — A scout comment.
          - CommentText : String — The comment text.
          - CommentType : ScoutCommentTypeID — The type the comment belongs too.
          - CommentVariation : unsigned Integer — The text variant of the comment for given CommentType.
          - CommentSkillType : ScoutCommentSkillTypeID — The skill named in the comment (0 if there is no skill). Will be youthPlayerId if CommentType=1.
          - CommentSkillLevel : SkillLevel — The skill level named in the comment (0 if there is no skill level named). Will be the player age if CommentType=1.
    - **TrainingReports** — The coach's training comments about the player, newest first. Only shown for your own youth players, and only when your club has Gold supporter. Empty if the coach has not made any comments.
      - **TrainingReport** — A training comment.
        - ReportText : String — The comment text, in the language of the user.
        - ReportDate : DateTime — The date and time of the comment.
        - ReportType : unsigned Integer — The type of the comment.
        - ReportSkillType : ScoutCommentSkillTypeID — The skill named in the comment. 0 if the comment does not name a skill.
        - ReportSkillLevel : SkillLevel — The skill level named in the comment. Only set when ReportType is 18, 19, 28, 29 or 30, otherwise 0.
    - **LastMatch** — Last played match. Only available with showLastMatch-flag as true.
      - Date : DateTime — Date of the last played match.
      - YouthMatchID : unsigned Integer — The unique YouthMatchID of the last played match.
      - PositionCode : MatchRoleID — Played position in the last match.
      - PlayedMinutes : unsigned Integer — Minutes played in the last match.
      - Rating : Float — The players average rating in the last played match.
