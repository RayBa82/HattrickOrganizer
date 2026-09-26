# nationalteamdetails

National Team information

## Changelog
### Version 1.9 (2016-06-28)
- Removed Output: HattrickData → Team → Trainer (Container)
- Removed Output: HattrickData → Team → Trainer → PlayerID (Element)
- Removed Output: HattrickData → Team → Trainer → PlayerName (Element)

### Version 1.8 (2013-03-04)
- Removed Output: HattrickData → UserIsSupporter (Element)
- Removed Output: HattrickData → UserHasClubhouse (Element)
- Added Output: HattrickData → UserSupporterTier (Element)

### Version 1.7 (2011-06-16)
- Added Output: HattrickData → Team → Logo (Element)

### Version 1.6 (2011-04-13)
- Added Output: HattrickData → Team → Experience442 (Element)

### Version 1.5 (2011-01-13)
- Added Output: HattrickData → Team → Experience523 (Element)
- Added Output: HattrickData → Team → Experience550 (Element)
- Added Output: HattrickData → Team → Experience253 (Element)

### Version 1.4 (2010-11-08)
- Removed Output: HattrickData → Team → Dress (Element)
- Removed Output: HattrickData → Team → DressAlternate (Element)
- Added Output: HattrickData → Team → DressURI (Element)
- Added Output: HattrickData → Team → DressAlternateURI (Element)

### Version 1.3 {default}
- Removed Output: HattrickData → Team → Morale (Element)
- Removed Output: HattrickData → Team → SelfConfidence (Element)
- Removed Output: HattrickData → Team → RatingScore (Element)
- Added Output: HattrickData → Team → Morale (Element)
- Added Output: HattrickData → Team → SelfConfidence (Element)
- Added Output: HattrickData → Team → RatingScore (Element)
- First version

### Version 1.2

## Input
Only available inputs in this file version are shown.
required file = nationalteamdetails
optional version = 
1.9
1.8
1.7
1.6
1.5
1.4
1.3
1.2
required teamID : unsigned Integer
What national team type to list.

## Output
- **HattrickData** — Hattrick Data Container
  - UserSupporterTier : supporterTier — Indicates which Supporter package the fetching user has, or empty if not a Supporter.
  - IsPlayingMatch : Boolean — Indicates if player is currently playing match. During matches some parameter values are not shown.
  - **Team** — Container for the data about the nation team.
    - TeamID : unsigned Integer — The globally unique TeamID.
    - TeamName : String — The full team name.
    - **NationalCoach** — Container for the data about the national coach.
      - NationalCoachUserID : unsigned Integer — The globally unique UserID for the national coach of the team.
      - NationalCoachLoginname : String — The 'username' or 'nickname' used in Forums.
    - **League** — Container for the data about the league ('Sverige', 'England' and so on) the team is part of.
      - LeagueID : unsigned Integer — The globally unique LeagueID.
      - LeagueName : String — The League name.
    - HomePage : URI — The home page URL that the team has specified.
    - Logo : URI — The URL to the team's logo.
    - DressURI : URI — URI to an image representing the dress of the team.
    - DressAlternateURI : URI — Same as DressURI, except that it shows the alternate dress.
    - Experience442 : SkillLevel — The team's experience of playing 4-4-2.
    - Experience433 : SkillLevel — The team's experience of playing 4-3-3.
    - Experience451 : SkillLevel — The team's experience of playing 4-5-1.
    - Experience352 : SkillLevel — The team's experience of playing 3-5-2.
    - Experience532 : SkillLevel — The team's experience of playing 5-3-2.
    - Experience343 : SkillLevel — The team's experience of playing 3-4-3.
    - Experience541 : SkillLevel — The team's experience of playing 5-4-1.
    - Experience523 : SkillLevel — The team's experience of playing 5-2-3.
    - Experience550 : SkillLevel — The team's experience of playing 5-5-0.
    - Experience253 : SkillLevel — The team's experience of playing 2-5-3.
    - Morale : TeamSpiritID — The morale/team spirit of the team. Will be hidden (empty) 4 hours before every match the team plays and until the match ends.
    - SupportersPopularity : SupportersPopularityID — Attribute: 'Available' : Boolean If the team is playing a match, the attribute is set to false and the container is empty, otherwise to true.
    - SelfConfidence : SelfConfidenceID — The self confidence of the team. Will be hidden (empty) 4 hours before every match the team plays and until the match ends.
    - RatingScore : unsigned Integer — A integer that indicates the current rating score of the national team.
    - FanClubSize : unsigned Integer — A integer that indicates the current fanclub size of the national team.
    - Rank : unsigned Integer — A integer that indicates the current rank of the national team.
