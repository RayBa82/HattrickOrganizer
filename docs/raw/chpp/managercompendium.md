# managercompendium

The manager compendium of the logged in user.

## Changelog
### Version 1.7 (2026-02-26)
- Add team LeagueSystemID
- Added Output: HattrickData → Manager → Teams → Team → LeagueSystemID (Element)

### Version 1.6 (2026-02-11)
- Add team gender
- Added Output: HattrickData → Manager → Teams → Team → GenderID (Element)

### Version 1.5 (2023-01-18)
- Add Currency
- Added Output: HattrickData → Manager → Currency (Container)
- Added Output: HattrickData → Manager → Currency → CurrencyName (Element)
- Added Output: HattrickData → Manager → Currency → CurrencyRate (Element)

### Version 1.4 (2022-03-04)
- Added Season to League
- Added Output: HattrickData → Manager → Teams → Team → League → Season (Element)

### Version 1.3 (2019-12-03)
- Added National Team Assistants
- Added Output: HattrickData → Manager → NationalTeamAssistant (Container)
- Added Output: HattrickData → Manager → NationalTeamAssistant → NationalTeam (Container)
- Added Output: HattrickData → Manager → NationalTeamAssistant → NationalTeam → NationalTeamId (Element)
- Added Output: HattrickData → Manager → NationalTeamAssistant → NationalTeam → NationalTeamName (Element)

### Version 1.2 (2018-03-01)
- Added Country element for each team
- Added Output: HattrickData → Manager → Teams → Team → Country (Container)
- Added Output: HattrickData → Manager → Teams → Team → Country → CountryId (Element)
- Added Output: HattrickData → Manager → Teams → Team → Country → CountryName (Element)

### Version 1.1 (2016-05-17)
- Added LastLogins element with the last LoginTime and IP like ex. 2016-05-17 15:08:13 : ***.***.***.***
- Added Output: HattrickData → Manager → LastLogins (Container)
- Added Output: HattrickData → Manager → LastLogins → LoginTime (Element)
- Added Output: HattrickData → Manager → Avatar (Container)
- Added Output: HattrickData → Manager → Avatar → BackgroundImage (Element)
- Added Output: HattrickData → Manager → Avatar → Layer (Container)
- Added Output: HattrickData → Manager → Avatar → Layer → Image (Element)

### Version 1.0 (2013-12-10) {default}
- First version

## Input
Only available inputs in this file version are shown.
required file = managercompendium
optional version = 
1.7
1.6
1.5
1.4
1.3
1.2
1.1
1.0
optional userId : unsigned Integer{Default:Your userId}
What user to show the data for.

## Output
- **HattrickData** — Hattrick Data Container
  - **Manager** — Container wrapping the info about the manager
    - UserId : unsigned Integer — The globally unique UserID.
    - Loginname : String — The 'username' or 'nickname' used in Forums and around the site.
    - SupporterTier : String — The current level of Hattrick Supporter that the user has. Empty if not a Supporter.
    - **LastLogins** — Container for the last logins.
      - LoginTime : String — Repeter with Login Time information
    - **Language** — Container for the language.
      - LanguageId : unsigned Integer — The globally unique LanguageID.
      - LanguageName : String — The language name.
    - **Country** — Container for the country.
      - CountryId : unsigned Integer — The globally unique CountryID.
      - CountryName : String — The country name
    - **Currency** — Container for the currency selected by the user.
      - CurrencyName : String — The name of the currency.
      - CurrencyRate : Decimal — Decimal value specifying the relative currency rate to SEK (swedish krona).
    - **Teams** — Container for the list of Teams.
      - **Team** — Container for the data about a particular Team.
        - TeamId : unsigned Integer — The globally unique TeamID.
        - TeamName : String — The full team name
        - GenderID : GenderID — The gender of the team.
        - LeagueSystemID : LeagueSystemID — The system type of the league for the team.
        - **Arena** — Container for the arena.
          - ArenaId : unsigned Integer — The globally unique ArenaID.
          - ArenaName : String — The arena name.
        - **League** — Container for the league.
          - LeagueId : unsigned Integer — The globally unique LeagueID
          - LeagueName : String — The League name.
          - Season : unsigned Integer — The Current Season.
        - **Country** — Container for the country.
          - CountryId : unsigned Integer — The globally unique CountryId
          - CountryName : String — The Country name.
        - **LeagueLevelUnit** — Container for the LeagueLevelUnit ('series').
          - LeagueLevelUnitId : unsigned Integer — The globally unique LeagueLevelUnitID.
          - LeagueLevelUnitName : String — The name of the LeagueLevelUnit
        - **Region** — Container for the region.
          - RegionId : unsigned Integer — The globally unique RegionID.
          - RegionName : String — The Region name
        - **YouthTeam** — Container for the youthTeam. Will be empty if the team has no Youth academy.
          - YouthTeamId : unsigned Integer — If the Team has a Youth academy the ID is represented here.
          - YouthTeamName : String — If the Team has a Youth academy the team name is represented here.
          - **YouthLeague** — Container for the league of the youthTeam. Will be empty if the youthTeam is currently not playing in a league.
            - YouthLeagueId : unsigned Integer — The globally unique YouthLeagueID.
            - YouthLeagueName : String — The name of the youth league.
    - **NationalTeamCoach** — Container for national teams the manager is a coach of. Empty if user is not a national team coach.
      - **NationalTeam** — Container for national team.
        - NationalTeamId : unsigned Integer — The globally unique NationalTeamID.
        - NationalTeamName : String — The name of the national team.
    - **NationalTeamAssistant** — Container for national teams the manager is an assistant coach of. Empty if user is not a national team assistant coach.
      - **NationalTeam** — Container for national team.
        - NationalTeamId : unsigned Integer — The globally unique NationalTeamID.
        - NationalTeamName : String — The name of the national team.
    - **Avatar** — Container for the elements to build the avatar.
      - BackgroundImage : URI — The URL to the card background-image. This will show a silouette for non-supporter teams.
      - **Layer** — Attribute: 'x' : unsigned Integer x-coordinate of image layer
        - Image : URI — The URL to the bodypart item.
