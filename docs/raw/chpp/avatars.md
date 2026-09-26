# avatars

Avatars for all players of user's team

## Changelog
### Version 1.1 (2013-01-08)
- Added Input: teamId

### Version 1.0 {default}
- First version

## Input
Only available inputs in this file version are shown.
required file = avatars
optional version = 
1.1
1.0
optional actionType = 
players {default}
hof
Indicates what type of action the page should perform.
players {default}
View the avatars of players for the team of the currently logged in user.
hof
View the avatars of Hall of Fame players for the team of the currently logged in user.
optional teamId : unsigned Integer{Default:Your senior teamId}
What team/user to show the avatars for. Defaults to logged in teamId.

## Output (actionType=players)
- **HattrickData** — Hattrick Data Container
  - **Team** — Container for the team to which the data applies.
    - TeamId : unsigned Integer — The globally unique TeamID.
    - **Players** — Container for the list of players.
      - **Player** — Container for a player.
        - PlayerID : unsigned Integer — The globally unique PlayerID.
        - **Avatar** — Container for the elements to build the avatar.
          - BackgroundImage : URI — The URL to the card background-image. This will show a silouette for non-supporter teams.
          - **Layer** — Attribute: 'x' : unsigned Integer x-coordinate of image layer
            - Image : URI — The URL to the bodypart item.

## Output (actionType=hof)
Same as actionType=players.
