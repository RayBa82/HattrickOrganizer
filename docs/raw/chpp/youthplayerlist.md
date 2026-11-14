# youthplayerlist

Youth Players.

## Changelog
### Version 1.4 (2026-11-10)
- Added UserId to OwningYouthTeam (actionType=details).
- Added Output: HattrickData → PlayerList → YouthPlayer → OwningYouthTeam → UserId (Element)

### Version 1.3 (2026-02-11)
- Add player gender.
- Added Output: HattrickData → PlayerList → YouthPlayer → GenderID (Element)

### Version 1.2 (2022-03-03)
- Add native country.
- Added Output: HattrickData → PlayerList → YouthPlayer → NativeCountryID (Element)
- Added Output: HattrickData → PlayerList → YouthPlayer → NativeCountryName (Element)

### Version 1.1 (2014-01-23)
- Fixed incorrect value of Specialty when the player have no or unknown specialty (old: empty, new: 0).
- Removed Output: HattrickData → PlayerList → YouthPlayer → Specialty (Element)
- Added Output: HattrickData → PlayerList → YouthPlayer → Specialty (Element)

### Version 1.0 (2011-11-15) {default}
- First version

## Input
Only available inputs in this file version are shown.
required file = youthplayerlist
optional version = 
1.4
1.3
1.2
1.1
1.0
optional actionType = 
list {default}
details
unlockskills
Indicates what type of action the page should perform.
list {default}
List the players, containing only PlayerId and Name-containers.
details
Show detailed players information.
unlockskills
Unlock the skills of the entire team before showing the players information (as details). (Requires "manage_youthplayers" scope.)
optional orderBy : String{Default:PlayerNumber}
The field that the outdata PlayerList should be sorted by.
optional youthTeamID : unsigned Integer{Default:Logged on user's youth team ID}
The youth team for which the data should apply to.
optional showScoutCall : Boolean{Default:false}
Set it to 'true' to get the information the scout provided when you pulled the player. Only available if actionType=details or actionType=unlockskills.
optional showLastMatch : Boolean{Default:false}
Set it to 'true' to get LastMatch container. Only available if actionType=details or actionType=unlockskills.

## Output (actionType=list)
- **HattrickData** — Hattrick Data Container
  - **PlayerList** — The container for all players.
    - **YouthPlayer** — Container for the data about the youth player.
      - YouthPlayerID : unsigned Integer — The globally unique youthPlayerID.
      - FirstName : String — Player's FirstName name.
      - NickName : String — Player's NickName name.
      - LastName : String — Player's LastName name.
      - GenderID : GenderID — The player gender

## Output (actionType=details)
- **HattrickData** — Hattrick Data Container
  - **PlayerList** — The container for all players.
    - **YouthPlayer** — Container for the data about the youth player.
      - YouthPlayerID : unsigned Integer — The globally unique youthPlayerID.
      - FirstName : String — Player's FirstName name.
      - NickName : String — Player's NickName name.
      - LastName : String — Player's LastName name.
      - Age : unsigned Integer — The age of the player in years.
      - GenderID : GenderID — The player gender
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
      - **ScoutCall** — A container for information about the scout call. Only visible to owner, and only with showScoutCall-flag as true.
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
      - **LastMatch** — Last played match. Only available with showLastMatch-flag as true.
        - Date : DateTime — Date of the last played match.
        - YouthMatchID : unsigned Integer — The unique YouthMatchID of the last played match.
        - PositionCode : MatchRoleID — Played position in the last match.
        - PlayedMinutes : unsigned Integer — Minutes played in the last match.
        - Rating : Float — The players average rating in the last played match.

## Output (actionType=unlockskills)
- **HattrickData** — Hattrick Data Container
  - **PlayerList** — The container for all players.
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
      - **ScoutCall** — A container for information about the scout call. Only visible to owner, and only with showScoutCall-flag as true.
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
      - **LastMatch** — Last played match. Only available with showLastMatch-flag as true.
        - Date : DateTime — Date of the last played match.
        - YouthMatchID : unsigned Integer — The unique YouthMatchID of the last played match.
        - PositionCode : MatchRoleID — Played position in the last match.
        - PlayedMinutes : unsigned Integer — Minutes played in the last match.
        - Rating : Float — The players average rating in the last played match.
