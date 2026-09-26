# youthavatars

Avatars for all players of user's youthteam

## Changelog
### Version 1.2 (2021-01-19)
- Make it possible to fetch avatars from other youth teams as well

### Version 1.1 (2013-05-02)
- Added Input: youthTeamId

### Version 1.0 {default}
- First version

## Input
Only available inputs in this file version are shown.
required file = youthavatars
optional version = 
1.2
1.1
1.0
optional youthTeamId : unsigned Integer{Default:Your primary club youth team id}
Youth team id to view training information for. Must refer to a youth team managed by requesting user.

## Output
- **HattrickData** — Hattrick Data Container
  - **YouthTeam** — Container for the youthteam to which the data applies.
    - YouthTeamId : unsigned Integer — The globally unique YouthTeamID.
    - **YouthPlayers** — Container for the list of youthplayers.
      - **YouthPlayer** — Container for a youthplayer.
        - YouthPlayerID : unsigned Integer — The globally unique YouthPlayerID.
        - **Avatar** — Container for the elements to build the avatar.
          - BackgroundImage : URI — The URL to the card background-image. This will show a silouette for non-supporter teams.
          - **Layer** — Attribute: 'x' : unsigned Integer x-coordinate of image layer
            - Image : URI — The URL to the bodypart item.
