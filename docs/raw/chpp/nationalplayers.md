# nationalplayers

National team players

## Changelog
### Version 1.5 (2022-03-04)
- Added Specialty and Avatars
- Added Output: HattrickData → Players → Player → Specialty (Element)
- Added Output: HattrickData → Players → Player → Avatar (Container)
- Added Output: HattrickData → Players → Player → Avatar → BackgroundImage (Element)
- Added Output: HattrickData → Players → Player → Avatar → Layer (Container)
- Added Output: HattrickData → Players → Player → Avatar → Layer → Image (Element)

### Version 1.4 (2021-04-09)
- Added player cards as a new XML element
- Added Output: HattrickData → Players → Player → Cards (Element)

### Version 1.3 (2013-03-04)
- Removed Output: HattrickData → UserIsSupporter (Element)
- Removed Output: HattrickData → UserHasClubhouse (Element)
- Added Output: HattrickData → UserSupporterTier (Element)

### Version 1.2 {default}
- First version

## Input
Only available inputs in this file version are shown.
required file = nationalplayers
optional version = 
1.5
1.4
1.3
1.2
optional actionType = 
view {default}
SupporterStats
Indicates what type of action the page should perform.
view {default}
View the national team players of a specific type ('regular' or 'U-20').
SupporterStats
View the number of matches that players have played in the national team. Only available for Supporters and may only be shown to Supporters.
optional MatchTypeCategory : String{Default:NT}
What national team type to list ('NT' = View all matches played in this National Team, 'WC' = View all matches in the World Cup played in this National Team
optional ShowAll : Boolean{Default:false}
Signifies the number of players shown in statistics (true = View all players that have performed for this National Team, false = View a maximum of 20 players that have performed for this National Team, ordered by number of matches played).
required teamID : unsigned Integer
What national team type to list.

## Output (actionType=view)
- **HattrickData** — Hattrick Data Container for actionType=view
  - UserSupporterTier : supporterTier — Indicates which Supporter package the fetching user has, or empty if not a Supporter.
  - ActionType : String — The requested ActionType.
  - TeamID : unsigned Integer — The globally unique nationalTeamID.
  - TeamName : String — The team name.
  - **Players** — Container for the players. Will be hidden (empty) 4 hours before every match the team plays and until the match starts.
    - **Player** — A player enlisted in this national team.
      - PlayerID : unsigned Integer — The globally unique identifier of the player.
      - PlayerName : unsigned Integer — The name of the player.
      - Cards : unsigned Integer — The number of currently accumulated cards. If the player is suspended, this will return 3 (whether the player has actually accumulated 3 bookings, or if he has become suspended by being sent off after 2 bookings in the same game).
      - Specialty : SpecialtyID — Integer indicating the specialty (if any) of the player.
      - **Avatar** — Container for the elements to build the avatar.
        - BackgroundImage : URI — The URL to the card background-image. This will show a silouette for non-supporter teams.
        - **Layer** — Attribute: 'x' : unsigned Integer x-coordinate of image layer
          - Image : URI — The URL to the bodypart item.

## Output (actionType=SupporterStats)
- **HattrickData** — Hattrick Data Container for actionType=supporterStats
  - UserIsSupporter : Boolean — Indicated if the fetching user has a supporter subscription or not.
  - UserHasClubhouse : Boolean — Indicated if the fetching user has a ClubHouse or not.
  - ActionType : String — The requested ActionType.
  - TeamID : unsigned Integer — The globally unique nationalTeamID.
  - TeamName : String — The team name.
  - **Stats** — Container for the supporter statstics.
    - MatchTypeCategory : String — Shows what kind of matches the statistics is selected for. Values are 'NT' and 'WC'.
    - ShowAll : Boolean — Echoes the in-parameter ShowAll.
    - MoreRecordsAvailable : Boolean — If ShowAll = 'false' and there are more records available than in the output, the value is 'true'. Otherwise 'false'. To get the rest of the records, re-post with ShowAll = 'true'.
    - **Players** — Container for the players.
      - **Player** — A player enlisted in this national team.
        - PlayerID : unsigned Integer — The globally unique identifier of the player.
        - PlayerName : unsigned Integer — The name of the player.
        - NrOfMatches : unsigned Integer — The number of matches that this player have played in this National Team.
