# playerevents

Player events

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
required file = playerevents
optional version = 
1.3
1.2
1.1
optional playerID : unsigned Integer
The player to get information for.

## Output
- **HattrickData** — Hattrick Data Container
  - UserSupporterTier : supporterTier — Indicates which Supporter package the fetching user has, or empty if not a Supporter.
  - **Player** — Container for the data about the player.
    - PlayerID : unsigned Integer — The globally unique playerID.
    - **PlayerEvents** — Attribute: 'Available' : Boolean The events are not available, if owner is playing a match currently
      - **PlayerEvent** — Container for the data about a specific event.
        - EventDate : DateTime — Date and time when the event was recorded.
        - PlayerEventTypeID : unsigned Integer — An identifier to show which type of event it is. No list of events will be available. Each CHPP will have to collect an eventlist him-/herself.
        - EventText : String (HTML encoded) — The description of the event, as shown in the HTML-output. May contain HTML-tags.
