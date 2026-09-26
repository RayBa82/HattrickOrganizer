# translations

Translations for the denominations in the game. This file is NOT meant to be fetched on each user session, but instead every certain period of time, like once a week, and store the values in the app.

## Changelog
### Version 1.2 (2022-03-04)
- Added form, indirect set pieces, attack and defence.

### Version 1.1 (2014-08-18)
- Added leaguenames container
- Added Output: HattrickData → Texts → LeagueNames (Container)
- Added Output: HattrickData → Texts → LeagueNames → League (Container)
- Added Output: HattrickData → Texts → LeagueNames → League → LeagueId (Element)
- Added Output: HattrickData → Texts → LeagueNames → League → LocalLeagueName (Element)
- Added Output: HattrickData → Texts → LeagueNames → League → LanguageLeagueName (Element)

### Version 1.0 (2013-11-27) {default}
- First version

## Input
Only available inputs in this file version are shown.
required file = translations
optional version = 
1.2
1.1
1.0
required languageId : unsigned Integer
The id of the language to get the translations for.

## Output
- **HattrickData** — Hattrick Data Container
  - Language : String — Attribute: 'Id' : unsigned Integer The id of the language.
  - **Texts** — Container for all the translations.
    - **SkillNames** — Container for the skill names. These are the labels for the skill levels.
      - Skill : String — Attribute: 'Type' : String Identifier for the skill.
    - **SkillLevels** — Container for the skill levels.
      - Level : String — Attribute: 'Value' : unsigned Integer The value that identifies the skill level.
    - **SkillSubLevels** — Container for the skill sub-levels.
      - SubLevel : String — Attribute: 'Value' : Float The value that identifies the skill sub-level, in steps of 0,25.
    - **PlayerSpecialties** — Container for the player specialties.
      - Item : String — Attribute: 'Value' : unsigned Integer The value that identifies the player specialty.
    - **PlayerAgreeability** — Container for the player agreeability levels.
      - Level : String — Attribute: 'Value' : unsigned Integer The value that identifies the player agreeability level.
    - **PlayerAgressiveness** — Container for the player agressiveness levels.
      - Level : String — Attribute: 'Value' : unsigned Integer The value that identifies the player agressiveness level.
    - **PlayerHonesty** — Container for the player honesty levels.
      - Level : String — Attribute: 'Value' : unsigned Integer The value that identifies the player honesty level.
    - **TacticTypes** — Container for the tactic types.
      - Item : String — Attribute: 'Value' : unsigned Integer The value that identifies the tactic type.
    - **MatchPositions** — Container for the match positions.
      - Item : String — Attribute: 'Value' : unsigned Integer The value that identifies the match position.
    - **RatingSectors** — Container for the rating sectors.
      - Item : String — Attribute: 'Value' : unsigned Integer The value that identifies the rating sector.
    - **TeamAttitude** — Container for the team attitude levels.
      - Level : String — Attribute: 'Value' : unsigned Integer The value that identifies the team attitude level.
    - **TeamSpirit** — Container for the team spirit levels.
      - Level : String — Attribute: 'Value' : unsigned Integer The value that identifies the team spirit level.
    - **Confidence** — Container for the confidence levels.
      - Level : String — Attribute: 'Value' : unsigned Integer The value that identifies the confidence level.
    - **TrainingTypes** — Container for the training types.
      - Item : String — Attribute: 'Value' : unsigned Integer The value that identifies the training type.
    - **Sponsors** — Container for the sponsors levels.
      - Level : String — Attribute: 'Value' : unsigned Integer The value that identifies the sponsors level.
    - **FanMood** — Container for the fan mood levels.
      - Level : String — Attribute: 'Value' : unsigned Integer The value that identifies the fan mood level.
    - **FanMatchExpectations** — Container for the fan match expectation levels.
      - Level : String — Attribute: 'Value' : unsigned Integer The value that identifies the fan match expectation level.
    - **FanSeasonExpectations** — Container for the fan season expectation levels.
      - Level : String — Attribute: 'Value' : unsigned Integer The value that identifies the fan season expectation level.
    - **LeagueNames** — Container for the leaguenames.
      - **League** — Container for the league.
        - LeagueId : unsigned Integer — The globally unique LeagueID.
        - LocalLeagueName : unsigned Integer — The name of the league in the leagues local language.
        - LanguageLeagueName : unsigned Integer — The name of the league in the chosen language.
