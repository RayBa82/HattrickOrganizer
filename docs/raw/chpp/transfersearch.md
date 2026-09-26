# transfersearch

Search the transfer market. This file is NOT meant to be fetched by bots to automatically scan the transfer market, but instead upon manual user request.

## Changelog
### Version 1.1 (2022-03-03)
- Added Salary.
- Added Output: HattrickData → TransferSearch → TransferResults → TransferResult → Details → Salary (Element)

### Version 1.0 (2013-12-12) {default}
- First version

## Input
Only available inputs in this file version are shown.
required file = transfersearch
optional version = 
1.1
1.0
required ageMin : unsigned Integer
Minimum age value (years) to search for.
optional ageDaysMin : unsigned Integer{Default:0}
Minimum age value (days) to search for.
required ageMax : unsigned Integer
Maximum age value (years) to search for.
optional ageDaysMax : unsigned Integer{Default:111}
Maximum age value (days) to search for.
required skillType1 : SkillID
Main skill to search for.
required minSkillValue1 : SkillLevel
Minimum value for the main skill (skillType1) to search for. The difference between min and max can be up to 4 levels. Set pieces, leadership and experience need a minimum level of 5 and keeper of 3.
required maxSkillValue1 : SkillLevel
Maximum value for the main skill (skillType1) to search for. The difference between min and max can be up to 4 levels.
optional skillType2 : SkillID{Default:Not specified}
Secondary skill to search for.
optional minSkillValue2 : SkillLevel{Default:Not specified}
Minimum value for the secondary skill (skillType2) to search for. The difference between min and max can be up to 4 levels. Set pieces, leadership and experience need a minimum level of 5 and keeper of 3.
optional maxSkillValue2 : SkillLevel{Default:Not specified}
Maximum value for the secondary skill (skillType2) to search for. The difference between min and max can be up to 4 levels.
optional skillType3 : SkillID{Default:Not specified}
Secondary skill to search for.
optional minSkillValue3 : SkillLevel{Default:Not specified}
Minimum value for the secondary skill (skillType3) to search for. The difference between min and max can be up to 4 levels. Set pieces, leadership and experience need a minimum level of 5 and keeper of 3.
optional maxSkillValue3 : SkillLevel{Default:Not specified}
Maximum value for the secondary skill (skillType3) to search for. The difference between min and max can be up to 4 levels.
optional skillType4 : SkillID{Default:Not specified}
Secondary skill to search for.
optional minSkillValue4 : SkillLevel{Default:Not specified}
Minimum value for the secondary skill (skillType4) to search for. The difference between min and max can be up to 4 levels. Set pieces, leadership and experience need a minimum level of 5 and keeper of 3.
optional maxSkillValue4 : SkillLevel{Default:Not specified}
Maximum value for the secondary skill (skillType4) to search for. The difference between min and max can be up to 4 levels.
optional specialty : SpecialtyID{Default:Not specified}
The specialty of the players to search for.
optional nativeCountryId : unsigned Integer{Default:Not specified}
CountryId where the players to search for where born.
optional tsiMin : unsigned Integer{Default:Not specified}
Minimum TSI value to search for.
optional tsiMax : unsigned Integer{Default:Not specified}
Maximum TSI value to search for.
optional priceMin : Money{Default:Not specified}
Minimum price to search for.
optional priceMax : Money{Default:Not specified}
Maximum price to search for.
optional pageSize : unsigned Integer{Default:25}
How many results to be shown in each page, the maximum value is 100.
optional pageIndex : unsigned Integer{Default:0}
What page to be shown, starts from 0.

## Output
- **HattrickData** — Hattrick Data Container
  - **TransferSearch** — Container for the transfer search values.
    - ItemCount : unsigned Integer — Total items matching the search criteria. If over 100, -1 is returned (as an analogy to "many").
    - PageSize : unsigned Integer — How many results are being shown.
    - PageIndex : unsigned Integer — What page is being shown.
    - **TransferResults** — Container for the results of the search.
      - **TransferResult** — Container for each result of the search.
        - PlayerId : unsigned Integer — The globally unique playerId.
        - FirstName : String — Player's FirstName name.
        - NickName : String — Player's NickName name.
        - LastName : String — Player's LastName name.
        - NativeCountryID : unsigned Integer — CountryID of the country where the player was born.
        - AskingPrice : Money — The original asking price.
        - Deadline : DateTime — The transfer deadline. Can be even a few hours in the past.
        - HighestBid : Money — The highest bid made for the player so far. Returns 0 if no bids have been made.
        - **BidderTeam** — Container with the information about the team holding the highest bid. Empty if no bids have been made.
          - TeamID : unsigned Integer — The globally unique TeamID.
          - TeamName : String — The full team name.
        - **Details** — Container with more details. Empty if the deadline has passed.
          - Age : unsigned Integer — The age of the player in years.
          - AgeDays : unsigned Integer — The amount of days past since last birthday.
          - Salary : Money — The Salary for the player.
          - TSI : unsigned Integer — The Total Skill Index (TSI) of the player.
          - PlayerForm : SkillLevel — The current form, given as an integer with 1 indicating disastrous, 2 for wretched and so on.
          - Experience : SkillLevel — The experience of the player.
          - Leadership : SkillLevel — The leadership of the player.
          - Specialty : SpecialtyID — Integer indicating the specialty (if any) of the player.
          - Cards : unsigned Integer — The number of currently accumulated cards. If the player is suspended, this will return 3 (whether the player has actually accumulated 3 bookings, or if he has become suspended by being sent off after 2 bookings in the same game). If a match is running this value is not available
          - InjuryLevel : Integer — The number of weeks the player is predicted to be injured. If the player is bruised, a value of 0 is returned. If the player is healthy, a value of -1 is returned. If a match is running this value is not available
          - StaminaSkill : SkillLevel — The stamina skill of the player.
          - KeeperSkill : SkillLevel — The keeper skill of the player.
          - PlaymakerSkill : SkillLevel — The playmaker skill of the player.
          - ScorerSkill : SkillLevel — The scorer skill of the player.
          - PassingSkill : SkillLevel — The passing skill of the player.
          - WingerSkill : SkillLevel — The winger skill of the player.
          - DefenderSkill : SkillLevel — The defender skill of the player.
          - SetPiecesSkill : SkillLevel — The set pieces skill of the player.
          - **SellerTeam** — Container with the information about the team holding the highest bid. Empty if no bids have been made.
            - TeamID : unsigned Integer — The globally unique TeamID.
            - TeamName : String — The full team name.
            - LeagueId : unsigned Integer — The globally unique LeagueId where the team plays.
