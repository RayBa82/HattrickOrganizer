# staffavatars

Avatars for all staff members.

## Changelog
### Version 1.1 (2023-09-07)
- Added trainer avatar
- Added Output: HattrickData → Trainer (Container)
- Added Output: HattrickData → Trainer → TrainerId (Element)
- Added Output: HattrickData → Trainer → Avatar (Container)
- Added Output: HattrickData → Trainer → Avatar → BackgroundImage (Element)
- Added Output: HattrickData → Trainer → Avatar → Layer (Container)
- Added Output: HattrickData → Trainer → Avatar → Layer → Image (Element)

### Version 1.0 (2014-04-03) {default}
- First version

## Input
Only available inputs in this file version are shown.
required file = staffavatars
optional version = 
1.1
1.0
optional teamId : unsigned Integer{Default:Your primary club senior teamId}
Team id to get data for.

## Output
- **HattrickData** — Hattrick Data Container
  - **Trainer** — Container with the trainer avatar
    - TrainerId : unsigned Integer — The id for the trainer.
    - **Avatar** — Container for the elements to build the avatar.
      - BackgroundImage : URI — The URL to the card background-image. This will show a silouette for non-supporter teams.
      - **Layer** — Attribute: 'x' : unsigned Integer x-coordinate of image layer
        - Image : URI — The URL to the bodypart item.
  - **StaffMembers** — Container with list of Staff Members.
    - **Staff** — Container with a Staff
      - StaffId : unsigned Integer — The id for the staff.
      - **Avatar** — Container for the elements to build the avatar.
        - BackgroundImage : URI — The URL to the card background-image. This will show a silouette for non-supporter teams.
        - **Layer** — Attribute: 'x' : unsigned Integer x-coordinate of image layer
          - Image : URI — The URL to the bodypart item.
