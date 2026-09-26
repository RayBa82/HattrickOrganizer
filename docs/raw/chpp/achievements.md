# achievements

The achievements of a specific user.

## Changelog
### Version 1.2 (2021-08-03)
- Added Output: HattrickData → AchievementList → Achievement → AchievementTitle (Element)

### Version 1.1 (2010-11-24)
- Added Output: HattrickData → AchievementList → Achievement → Rank (Element)

### Version 1.0 {default}
- First version

## Input
Only available inputs in this file version are shown.
required file = achievements
optional version = 
1.2
1.1
1.0
optional userID : unsigned Integer{Default:Your own userID}
What User to show achievements for

## Output
- **HattrickData** — Hattrick Data Container
  - MaxPoints : unsigned Integer — The maximum number of points a user currently can achieve
  - **AchievementList** — Container for the list of Achievements.
    - **Achievement** — Container for the data about a particular Achievement.
      - AchievementTypeID : unsigned Integer — The AchievementTypeID (integer). NOTE. We do not provide a list of available achievements.
      - AchievementTitle : String — The title for the achievement
      - AchievementText : String — The text describing the achievement
      - CategoryID : AchievementCategoryID — The CategoryID (integer) the achievement belongs to
      - EventDate : DateTime — The date when achievement was awarded
      - Points : unsigned Integer — Points awarded for achievement
      - Multilevel : Boolean — True if AchievementTypeID has multiple levels.
      - Rank : unsigned Integer — The user's current rank in this achievement, where 1 = best rank possible.
      - NumberOfEvents : unsigned Integer — Global number of users been awarded this achievement
