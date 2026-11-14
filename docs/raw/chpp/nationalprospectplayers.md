# nationalprospectplayers

The national team's prospect player list.

## Changelog
### Version 1.0 (2026-11-10) {default}
- First version

## Input
Only available inputs in this file version are shown.
required file = nationalprospectplayers
optional version = 
1.0
required teamID : unsigned Integer
The nationalTeamID to list prospect players for.
optional playerCategoryId : PlayerCategoryID{Default:All categories}
Only list the prospect players in this category. Use 0 for players with no category set.

## Output
- **HattrickData** — Hattrick Data Container
  - UserSupporterTier : supporterTier — Indicates which Supporter package the fetching user has, or empty if not a Supporter.
  - TeamID : unsigned Integer — The nationalTeamID.
  - TeamName : String — The team name.
  - **Players** — Container for the prospect players. Will be hidden (empty) from 8 hours before every match the team plays until the match starts. It is not hidden for the national team's coach, assistant coach and scouts.
    - **Player** — A prospect player of this national team.
      - PlayerID : unsigned Integer — The identifier of the player.
      - PlayerName : String — The name of the player.
      - PlayerCategoryId : PlayerCategoryID — The category the national team staff set for this prospect player. 0 if no category is set.
      - Specialty : SpecialtyID — Integer indicating the specialty (if any) of the player.
      - **Avatar** — Container for the elements to build the avatar.
        - BackgroundImage : URI — The URL to the card background image.
        - **Layer** — Attribute: 'x' : unsigned Integer x-coordinate of image layer Attribute: 'y' : unsigned Integer y-coordinate of image layer The container for each avatar bodypart item. The attributes x and y say where the item should be positioned. There are several of these containers for each player.
          - Image : URI — The URL to the bodypart item.
