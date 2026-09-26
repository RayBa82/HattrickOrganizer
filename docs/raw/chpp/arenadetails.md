# arenadetails

Information about specific arenas, supporter statistics and the largest arenas in Hattrick.

## Changelog
### Version 1.7 (2023-02-08)
- Added ArenaFallbackImage for arenas with no custom image.
- Removed Output: HattrickData → MyArena → ArenaImage (Element)
- Added Output: HattrickData → MyArena → ArenaFallbackImage (Element)
- Added Output: HattrickData → Arena → ArenaFallbackImage (Element)
- Added Output: HattrickData → LeagueArenaStats → OtherArenasStatList → ArenaStat → ArenaFallbackImage (Element)

### Version 1.6 (2013-06-11)
- Added ArenaImage.
- Added Output: HattrickData → MyArena → ArenaImage (Element)
- Added Output: HattrickData → Arena → ArenaImage (Element)
- Added Output: HattrickData → LeagueArenaStats → OtherArenasStatList → ArenaStat → ArenaImage (Element)

### Version 1.5 (2013-06-11)
- Removed Input: arenaID
- Added Input: arenaID
- Added Input: teamId

### Version 1.4
- Fixed bug so now LeagueId=0 returns global stats for biggest arenas (also in previous version).

### Version 1.3 {default}
- First version

## Input
Only available inputs in this file version are shown.
required file = arenadetails
optional version = 
1.7
1.6
1.5
1.4
1.3
optional StatsType = 
(empty) {default}
MyArena
OtherArenas
Statistics type to show
(empty) {default}
Default view
optional arenaID : unsigned Integer{Default:Your own arenaID, if no teamId supplied}
Which arena to show details for
optional teamId : unsigned Integer{Default:Your own primary team's team id}
Which of your own teams arena to show details for, only used when no arena id supplied
MyArena
Show supporter statistics for the users own arena.
optional arenaID : unsigned Integer{Default:Your own arenaID, if no teamId supplied}
Which arena to show details for
optional teamId : unsigned Integer{Default:Your own primary team's team id}
Which of your own teams arena to show details for, only used when no arena id supplied
optional MatchType : ArenaMatchType{Default:All}
Which type of matches that will be included in the statistics.
optional FirstDate : DateTime{Default:Date of first match in arena}
The first date the statistics should cover.
optional LastDate : DateTime{Default:DateTime.Now}
The last date the statistics should cover.
OtherArenas
Shows supporter statistics over biggest arenas.
required StatsLeagueID : unsigned Integer{Default:0}
Which league to show statistics for. 0 = global statistic

## Output (actionType=)
- **HattrickData** — Hattrick Data Container
  - **Arena** — Container for the data about the arena.
    - ArenaID : unsigned Integer — The globally unique arenaID.
    - ArenaName : String — The name of the arena.
    - ArenaImage : String — URL to an image of the arena
    - ArenaFallbackImage : String — The default image for an arena of this size, use this only when the ArenaImage fails to load.
    - **Team** — Container for the data about the team owning this arena.
      - TeamID : unsigned Integer — The globally unique teamID.
      - TeamName : String — The name of the team.
    - **League** — Container for the data about the league of the arena's team.
      - LeagueID : unsigned Integer — The globally unique leagueID.
      - LeagueName : String — The name of the league.
    - **Region** — Container for the data about the region of the arena.
      - RegionID : unsigned Integer — The globally unique regionID.
      - RegionName : String — The name of the region.
    - **CurrentCapacity** — Container for the data about the current capacity of the arena.
      - RebuiltDate : DateTime — The date when current capacity was reached. If the arena is under construction an attribute named Available is set to false and the container is then empty, otherwise Available is set to true.
      - Terraces : unsigned Integer — The current capacity on the terraces.
      - Basic : unsigned Integer — The current capacity on the basic seats.
      - Roof : unsigned Integer — The current capacity on the seats under roof.
      - VIP : unsigned Integer — The current capacity in the VIP lounge.
      - Total : unsigned Integer — The arenas current total capacity.
    - **ExpandedCapacity** — Container for the data about the expanded capacity of the arena during construction. If the arena is under construction an attribute named Available is set to true, otherwise to false and the container is then empty.
      - ExpansionDate : DateTime — The preliminary date when the expansion is finished.
      - Terraces : unsigned Integer — The expanded capacity on the terraces.
      - Basic : unsigned Integer — The expanded capacity on the basic seats.
      - Roof : unsigned Integer — The expanded capacity on the seats under roof.
      - VIP : unsigned Integer — The expanded capacity in the VIP lounge.
      - Total : unsigned Integer — The expanded current total capacity.

## Output (actionType=MyArena)
- **HattrickData** — Hattrick Data Container
  - **MyArena** — Container for the statistics for the user's own arena.
    - ArenaID : unsigned Integer — The globally unique arenaID.
    - ArenaName : String — The name of the arena.
    - ArenaImage : String — URL to an image of the arena. If this fails to load it usually means the team doesn't have a custom arena look. Fallback to ArenaFallbackImage.
    - ArenaFallbackImage : String — The default image for an arena of this size, use this only when the ArenaImage fails to load.
    - MatchTypes : ArenaMatchType — Matches that are be included in the statistics.
    - FirstDate : DateTime — The first date the statistics cover.
    - LastDate : DateTime — The last date the statistics cover.
    - NumberOfMatches : unsigned Integer — Number of matches of specified type within given period.
    - **VisitorsAverage** — Container for average visitor statistics.
      - Terraces : unsigned Integer — Visitors on terraces.
      - Basic : unsigned Integer — Visitors on basic seats.
      - Roof : unsigned Integer — Visitors on seats under roof.
      - VIP : unsigned Integer — Visitors in VIP lounge.
      - Total : unsigned Integer — Total amount of visitors.
    - **VisitorsMost** — Container for most visitor statistics.
      - Terraces : unsigned Integer — Visitors on terraces.
      - Basic : unsigned Integer — Visitors on basic seats.
      - Roof : unsigned Integer — Visitors on seats under roof.
      - VIP : unsigned Integer — Visitors in VIP lounge.
      - Total : unsigned Integer — Total amount of visitors.
    - **VisitorsLeast** — Container for least visitor statistics.
      - Terraces : unsigned Integer — Visitors on terraces.
      - Basic : unsigned Integer — Visitors on basic seats.
      - Roof : unsigned Integer — Visitors on seats under roof.
      - VIP : unsigned Integer — Visitors in VIP lounge.
      - Total : unsigned Integer — Total amount of visitors.

## Output (actionType=OtherArenas)
- **HattrickData** — Hattrick Data Container
  - **LeagueArenaStats** — Container for the biggest arena statistics. Only provided for StatsType = OtherArenas.
    - LeagueID : unsigned Integer — The globally unique LeagueID. LeaguID=0 for global statistics.
    - LeagueName : String — The name of the league. Not provided for global statistics.
    - CreatedDate : DateTime — The date when the statistics were created/updated.
    - **OtherArenasStatList** — Container for biggest arena statistics for the league.
      - **ArenaStat** — Container for a particular arena's statistics.
        - ArenaID : unsigned Integer — The globally unique arenaID.
        - ArenaName : String — The name of the arena.
        - ArenaImage : String — URL to an image of the arena
        - ArenaFallbackImage : String — The default image for an arena of this size, use this only when the ArenaImage fails to load.
        - ArenaSize : unsigned Integer — The size of the arena.
        - ArenaLeagueID : unsigned Integer — The leagueID for where the arena is used.
        - ArenaLeagueName : unsigned Integer — The name of the league for where the arena is used.
        - ArenaRegionID : unsigned Integer — The regionID for where the arena is placed.
        - ArenaRegionName : String — The name of the region for where the arena is placed.
