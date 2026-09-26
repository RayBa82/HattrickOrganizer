# leaguelevels

Shows league level units (series) information for a specific league.

## Changelog
### Version 1.0 (2024-08-26) {default}
- First version

## Input
Only available inputs in this file version are shown.
required file = leaguelevels
optional version = 
1.0
optional LeagueID : unsigned Integer{Default:LeagueID of the team of the currently logged on user's primary club}
The ID number of the League (for instance Sverige, USA, England...).

## Output
- **HattrickData** — Hattrick Data Container
  - LeagueID : unsigned Integer — The ID number of the League (for instance Sverige, USA, England...).
  - Season : unsigned Integer — Current season of this league.
  - NrOfLeagueLevels : unsigned Integer — Number of league levels in this league.
  - **LeagueLevelList** — List of league level units (series) for this league.
    - **LeagueLevelItem** — League level unti information (series).
      - LeagueLevel : unsigned Integer — Indicates the division/level. 1 is the top division, the second best is 2 and so on.
      - NrOfLeagueLevelUnits : String — Number of league level units (series) in this league.
      - NrOfTeams : unsigned Integer — Number of teams in this league level units (series).
      - LeagueLevelUnitIdList : String — Comma-separated list of LeagueLevelUnitIDs in this league level.
      - NrOfSharedPromotionSlotsPerSeries : unsigned Integer — Number of promotional slots shared per series.
      - NrOfDirectPromotionSlotsPerSeries : unsigned Integer — Number of direct promotion slots per series.
      - NrOfQualificationPromotionSlotsPerSeries : unsigned Integer — Number of qualification promotion slots per series.
      - NrOfDirectDemotionSlotsPerSeries : unsigned Integer — Number of direct demotion slots per series.
      - NrOfQualificationDemotionSlotsPerSeries : unsigned Integer — Number of qualification demotion slots per series.
