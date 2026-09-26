# worlddetails

General Information about all countries in HT World

## Changelog
### Version 2.0 (2026-02-09)
- Added LeagueSystemId to leagues.
- Added Output: HattrickData → LeagueList → League → LeagueSystemID (Element)

### Version 1.9 (2022-03-03)
- Added LanguageId and LanguageName to leagues.
- Added Output: HattrickData → LeagueList → League → LanguageId (Element)
- Added Output: HattrickData → LeagueList → League → LanguageName (Element)
- Added Output: HattrickData → LeagueList → League → Sequence1 (Element)
- Added Output: HattrickData → LeagueList → League → Sequence2 (Element)
- Added Output: HattrickData → LeagueList → League → Sequence3 (Element)
- Added Output: HattrickData → LeagueList → League → Sequence5 (Element)
- Added Output: HattrickData → LeagueList → League → Sequence7 (Element)

### Version 1.8 (2016-12-13)
- Added Hattrick International league.
- Added flag "Available" for Country container.

### Version 1.7 (2015-08-03)
- Added country code.
- Added Output: HattrickData → LeagueList → League → Country → CountryCode (Element)

### Version 1.6 (2014-07-21)
- Restructured to supporter more cups.
- Removed Output: HattrickData → LeagueList → League → Cup (Container)
- Removed Output: HattrickData → LeagueList → League → Cup → CupID (Element)
- Removed Output: HattrickData → LeagueList → League → Cup → CupName (Element)
- Added Output: HattrickData → LeagueList → League → Cups (Container)
- Added Output: HattrickData → LeagueList → League → Cups → Cup (Container)
- Added Output: HattrickData → LeagueList → League → Cups → Cup → CupID (Element)
- Added Output: HattrickData → LeagueList → League → Cups → Cup → CupName (Element)
- Added Output: HattrickData → LeagueList → League → Cups → Cup → CupLeagueLevel (Element)
- Added Output: HattrickData → LeagueList → League → Cups → Cup → CupLevel (Element)
- Added Output: HattrickData → LeagueList → League → Cups → Cup → CupLevelIndex (Element)
- Added Output: HattrickData → LeagueList → League → Cups → Cup → MatchRound (Element)
- Added Output: HattrickData → LeagueList → League → Cups → Cup → MatchRoundsLeft (Element)

### Version 1.5 (2013-09-03)
- Added ActiveTeams in accordance to additional club changes.
- Added Output: HattrickData → LeagueList → League → ActiveTeams (Element)

### Version 1.4 (2011-10-07)
- Please note, that with this version 'getRegions' was introduced. In earlier versions you could not fetch regions!
- Added Input: countryID
- Added Input: leagueID
- Added Output: HattrickData → LeagueList → League → SeasonOffset (Element)
- Added Output: HattrickData → LeagueList → League → Country → DateFormat (Element)
- Added Output: HattrickData → LeagueList → League → Country → TimeFormat (Element)
- Added Output: HattrickData → LeagueList → League → Country → RegionList (Container)
- Added Output: HattrickData → LeagueList → League → Country → RegionList → Region (Container)
- Added Output: HattrickData → LeagueList → League → Country → RegionList → Region → RegionID (Element)
- Added Output: HattrickData → LeagueList → League → Country → RegionList → Region → RegionName (Element)

### Version 1.3 (2011-02-12) {default}
- Added Output: HattrickData → LeagueList → League → NationalTeamId (Element)
- Added Output: HattrickData → LeagueList → League → U20teamId (Element)
- First version

### Version 1.2 {default}
- First version

## Input
Only available inputs in this file version are shown.
required file = worlddetails
optional version = 
2.0
1.9
1.8
1.7
1.6
1.5
1.4
1.3
1.2
optional includeRegions = 
false {default}
true
Indicates if regions should be shown or not.
false {default}
No regions for the countries are shown at all.
true
All regions for the countries are shown.
optional_1 countryID : unsigned Integer
The countryID to get information for.
optional_1 leagueID : unsigned Integer
The leagueID to get information for.
optional_1: You can set countryID or leagueID or none. If countryID and leagueID is set, leagueID is used! If none is set, all leagues/contries are shown!

## Output (actionType=false)
- **HattrickData** — Hattrick Data Container
  - **LeagueList** — Container for the list of leagues.
    - **League** — Container for the league.
      - LeagueID : unsigned Integer — The globally unique LeagueID.
      - LeagueName : String — The League name.
      - Season : unsigned Integer — The current season number of the league.
      - SeasonOffset : Integer — The season offset to swedish's season.
      - MatchRound : unsigned Integer — The current match round of the league.
      - ShortName : String — The short name of the league (used in some places with little room on the site).
      - Continent : String — The name of the continent it is defined to be part of in Hattrick.
      - ZoneName : String — The name of the zone the league is defined to be part of in Hattrick.
      - EnglishName : String — The English name of the league.
      - LeagueSystemID : LeagueSystemID — The system type of the league.
      - LanguageId : unsigned Integer — The ID of the default language of the league.
      - LanguageName : String — The name of the default language of the league.
      - **Country** — Attribute: 'Available' : Boolean If the league does not have a country this container is unavailable.
        - CountryID : unsigned Integer — The globally unique CountryID of country.
        - CountryName : String — The name of the country.
        - CurrencyName : String — The name of the currency in this country.
        - CurrencyRate : Decimal — Decimal value specifying the relative currency rate to SEK (swedish krona).
        - CountryCode : String — The country code for this country.
        - DateFormat : String — The date format for users of this country using ISO_8601
        - TimeFormat : String — The time format for users of this country using ISO_8601
      - **Cups** — Data about the league's cups.
        - **Cup** — Container for a cup.
          - CupID : unsigned Integer — Globally unique CupID.
          - CupName : String — Name of the cup.
          - CupLeagueLevel : unsigned Integer — LeagueLevel for the cup. 0 = National (LeagueLevel 1-6), 7-9 = Divisional.
          - CupLevel : unsigned Integer — Level of the cup. 1 = National/Divisional, 2 = Challenger, 3 = Consolation.
          - CupLevelIndex : unsigned Integer — Index of the cup. Always 1 for National and Consolation cups, for Challenger cup: 1 = Emerald, 2 = Ruby, 3 = Sapphire.
          - MatchRound : unsigned Integer — The last played matchround (or currently played if matches are ongoing). (Note that this is different from TeamDetails!).
          - MatchRoundsLeft : unsigned Integer — How many rounds are left of this cup.
      - NationalTeamId : unsigned Integer — The id of the national team of the league.
      - U20teamId : unsigned Integer — The id of the U-20 national team of the league.
      - ActiveTeams : unsigned Integer — Integer specifying the number of active teams in the league, as of last daily count.
      - ActiveUsers : unsigned Integer — Integer specifying the number of active users in the league, as of last daily count.
      - WaitingUsers : unsigned Integer — Integer specifying the number of users on the waiting list in the league. These users are not included in the ActiveUsers, so ActiveUsers + WaitingUsers would give a sort of total number of users.
      - TrainingDate : DateTime — The date and time when the next training update will start in this league.
      - EconomyDate : DateTime — The date and time when the next economy update will start in this league.
      - CupMatchDate : DateTime — The date and time when the next cup round is being checked for (that is, there are not necessarily any matches to play, if the cup has been finished).
      - SeriesMatchDate : DateTime — The date and time when the next series match round starts. Some leagues may have several series match dates - then this data gives the date when the first batch for the league starts.
      - Sequence1 : DateTime — Time and date for an upcoming daily update.
      - Sequence2 : DateTime — Time and date for an upcoming daily update.
      - Sequence3 : DateTime — Time and date for an upcoming daily update.
      - Sequence5 : DateTime — Time and date for an upcoming daily update.
      - Sequence7 : DateTime — Time and date for an upcoming daily update, including the confidence.
      - NumberOfLevels : unsigned Integer — Integer representing the number of LeagueLevels (divisions) active for this league.

## Output (actionType=true)
- **HattrickData** — Hattrick Data Container
  - **LeagueList** — Container for the list of leagues.
    - **League** — Container for the league.
      - LeagueID : unsigned Integer — The globally unique LeagueID.
      - LeagueName : String — The League name.
      - Season : unsigned Integer — The current season number of the league.
      - SeasonOffset : unsigned Integer — The season offset to swedish's season.
      - MatchRound : unsigned Integer — The current match round of the league.
      - ShortName : String — The short name of the league (used in some places with little room on the site).
      - Continent : String — The name of the continent it is defined to be part of in Hattrick.
      - ZoneName : String — The name of the zone the league is defined to be part of in Hattrick.
      - EnglishName : String — The English name of the league.
      - LanguageId : unsigned Integer — The ID of the default language of the league.
      - LanguageName : String — The name of the default language of the league.
      - **Country** — Container for the country that is related to the league. It may seem confusing what the difference is between a league and a country. Well, there isn't much, except that in some cases we refer to CountryID instead of LeagueID. The easiest way to deal with it is perhaps to just say: "It's the way it is. Just learn to live with it.". Some things are formally tied to the Country instead of the League. We have chosen to maintain that relationship in the XML output, hence the sub-containers of Country.
        - CountryID : unsigned Integer — The globally unique CountryID of country.
        - CountryName : String — The name of the country.
        - CurrencyName : String — The name of the currency in this country.
        - CurrencyRate : Decimal — Decimal value specifying the relative currency rate to SEK (swedish krona).
        - CountryCode : String — The country code for this country.
        - DateFormat : String — The date format for users of this country using ISO_8601
        - TimeFormat : String — The time format for users of this country using ISO_8601
        - **RegionList** — Container for all regions of this country
          - **Region** — Container for a region
            - RegionID : unsigned Integer — The globally unique RegionID
            - RegionName : String — The name of the region
      - **Cups** — Data about the league's cups.
        - **Cup** — Container for a cup.
          - CupID : unsigned Integer — Globally unique CupID.
          - CupName : String — Name of the cup.
          - CupLeagueLevel : unsigned Integer — LeagueLevel for the cup. 0 = National (LeagueLevel 1-6), 7-9 = Divisional.
          - CupLevel : unsigned Integer — Level of the cup. 1 = National/Divisional, 2 = Challenger, 3 = Consolation.
          - CupLevelIndex : unsigned Integer — Index of the cup. Always 1 for National and Consolation cups, for Challenger cup: 1 = Emerald, 2 = Ruby, 3 = Sapphire.
          - MatchRound : unsigned Integer — The last played matchround (or currently played if matches are ongoing). (Note that this is different from TeamDetails!).
          - MatchRoundsLeft : unsigned Integer — How many rounds are left of this cup.
      - NationalTeamId : unsigned Integer — The id of the national team of the league.
      - U20teamId : unsigned Integer — The id of the U-20 national team of the league.
      - ActiveTeams : unsigned Integer — Integer specifying the number of active teams in the league, as of last daily count.
      - ActiveUsers : unsigned Integer — Integer specifying the number of active users in the league, as of last daily count.
      - WaitingUsers : unsigned Integer — Integer specifying the number of users on the waiting list in the league. These users are not included in the ActiveUsers, so ActiveUsers + WaitingUsers would give a sort of total number of users.
      - TrainingDate : DateTime — The date and time when the next training update will start in this league.
      - EconomyDate : DateTime — The date and time when the next economy update will start in this league.
      - CupMatchDate : DateTime — The date and time when the next cup round is being checked for (that is, there are not necessarily any matches to play, if the cup has been finished).
      - SeriesMatchDate : DateTime — The date and time when the next series match round starts. Some leagues may have several series match dates - then this data gives the date when the first batch for the league starts.
      - Sequence1 : DateTime — Time and date for an upcoming daily update.
      - Sequence2 : DateTime — Time and date for an upcoming daily update.
      - Sequence3 : DateTime — Time and date for an upcoming daily update.
      - Sequence5 : DateTime — Time and date for an upcoming daily update.
      - Sequence7 : DateTime — Time and date for an upcoming daily update, including the confidence.
      - NumberOfLevels : unsigned Integer — Integer representing the number of LeagueLevels (divisions) active for this league.
