# trainingevents

Get training events for a player.

## Changelog
### Version 1.3 (2013-03-04)
- Removed Output: HattrickData → UserIsSupporter (Element)
- Added Output: HattrickData → UserSupporterTier (Element)

### Version 1.2
- Removed Output: HattrickData → UserHasClubhouse (Element)

### Version 1.1 {default}
- First version

## Input
Only available inputs in this file version are shown.
required file = trainingevents
optional version = 
1.3
1.2
1.1
required playerID : unsigned Integer
What player to show training events for.

## Output
- **HattrickData** — Hattrick Data Container
  - UserSupporterTier : supporterTier — Indicates which Supporter package the fetching user has, or empty if not a Supporter.
  - **Player** — Container for the data about the player.
    - PlayerID : unsigned Integer — The globally unique playerID.
    - **TrainingEvents** — Attribute: 'Available' : Boolean If the player is currently playing a match training events are not available.
      - **TrainingEvent** — Attribute: 'Index' : unsigned Integer Incremented by one for each event shown.
        - SkillID : SkillID — An identifier to show which type of skill that has been affected.
        - OldLevel : SkillLevel — An integer to show the old level of the skill.
        - NewLevel : SkillLevel — An integer to show the new level of the skill.
        - Season : unsigned Integer — An integer to show which season the event was recorded. The season is related to the league of the team of the player.
        - MatchRound : unsigned Integer — An integer to show which matchround the event was recorded. The season is related to the league of the team of the player.
        - DayNumber : unsigned Integer — An integer to show which season the event was recorded. The season is related to the league of the team of the player. Ranges from 1 to 7
