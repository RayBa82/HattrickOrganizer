# search

Search

## Changelog
### Version 1.2 {default}
- First version

## Input
Only available inputs in this file version are shown.
required file = search
optional version = 
1.2
required searchType = 
0 {default}
1
2
3
4
5
6
What type of search to perform.
0 {default}
Search for players
required_1 searchString2 : String
The second string to search for. Must be >= 3 characters long.
optional searchLeagueID : unsigned Integer{Default:Logged in user's leagueID}
The specific LeagueID to search in. -1 searches in ALL leagues.
1
Search for arenas.
2
Search for managers.
3
Search for series.
optional searchLeagueID : unsigned Integer{Default:Logged in user's leagueID}
The specific LeagueID to search in. -1 searches in ALL leagues.
4
Search for teams.
5
Search for regions.
6
Search for match.
required_1 searchString : String
The string to search for. Must be >= 3 characters long.
required_1 searchID : unsigned Integer
The specific ID to search for.
optional pageIndex : unsigned Integer
The zerobased PageIndex to retrieve. Will only have effect when resultset has more than 25 rows.
required_1: Set searchString (and searchString2) or searchID, which overrides the searchStrings

## Output
- **HattrickData** — Hattrick Data Container
  - **SearchParams** — Container for a search parameters passed.
    - SearchType : unsigned Integer — The SearchType specified as input parameter.
    - searchString : String — The string to search for. Must be >= 3 characters long.
    - searchString2 : String — The second string to search for. Must be >= 3 characters long.
    - searchID : unsigned Integer — The specific ID to search for.
    - searchLeagueID : unsigned Integer — The specific LeagueID to search in. -1 searches in ALL leagues.
  - **Result** — Container for a search resultset.
    - ResultID : unsigned Integer — The ID of the result.
    - ResultName : String — The name of the result.
