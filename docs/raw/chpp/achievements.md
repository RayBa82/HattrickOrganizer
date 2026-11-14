# achievements

The achievements of a specific user.

## Changelog
### Version 1.3 (2026-11-10)
- Added Completed, MaxPoints, ProgressValue, ProgressValueDate, CurrentVariation and NextVariation.
- Added Output: HattrickData → AchievementList → Achievement → Completed (Element)
- Added Output: HattrickData → AchievementList → Achievement → MaxPoints (Element)
- Added Output: HattrickData → AchievementList → Achievement → ProgressValue (Element)
- Added Output: HattrickData → AchievementList → Achievement → ProgressValueDate (Element)
- Added Output: HattrickData → AchievementList → Achievement → CurrentVariation (Container)
- Added Output: HattrickData → AchievementList → Achievement → CurrentVariation → Rank (Element)
- Added Output: HattrickData → AchievementList → Achievement → CurrentVariation → ValueToAchieve (Element)
- Added Output: HattrickData → AchievementList → Achievement → CurrentVariation → Points (Element)
- Added Output: HattrickData → AchievementList → Achievement → NextVariation (Container)
- Added Output: HattrickData → AchievementList → Achievement → NextVariation → Rank (Element)
- Added Output: HattrickData → AchievementList → Achievement → NextVariation → ValueToAchieve (Element)
- Added Output: HattrickData → AchievementList → Achievement → NextVariation → Points (Element)

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
1.3
1.2
1.1
1.0
optional userID : unsigned Integer{Default:Your own userID}
What User to show achievements for

## Output
- **HattrickData** — Hattrick Data Container
  - MaxPoints : unsigned Integer — The total number of points the user has earned across all listed achievements.
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
      - Completed : Boolean — Whether the achievement has been fully completed.
      - MaxPoints : unsigned Integer — The maximum number of points achievable for this achievement.
      - ProgressValue : unsigned Integer — The best value the user has reached towards the next variation. This is the best value so far, not the current one. Only present for multilevel achievements that are not completed and where the user has progress beyond the current variation. Not written for AchievementTypeID 36 (years playing Hattrick). Omitted for a hidden achievement unless you are requesting your own data.
      - ProgressValueDate : DateTime — The date when ProgressValue was reached. Only present together with ProgressValue.
      - **CurrentVariation** — The currently achieved variation tier. Only present for multilevel achievements the user has started, and omitted for a hidden achievement unless you are requesting your own data.
        - Rank : unsigned Integer — The rank of this variation, where 1 = best rank possible.
        - ValueToAchieve : unsigned Integer — The value required to reach this variation.
        - Points : unsigned Integer — Points awarded for this variation.
      - **NextVariation** — The next variation tier still to be reached. Only present for multilevel achievements with a further tier available, and omitted for a hidden achievement unless you are requesting your own data.
        - Rank : unsigned Integer — The rank of this variation, where 1 = best rank possible.
        - ValueToAchieve : unsigned Integer — The value required to reach this variation.
        - Points : unsigned Integer — Points awarded for this variation.
