# bookmarks

User bookmarks

## Changelog
### Version 1.0 {default}
- First version

## Input
Only available inputs in this file version are shown.
required file = bookmarks
optional version = 
1.0
optional BookmarkTypeID : BookmarkTypeID
Bookmark Type

## Output
- **HattrickData** — Hattrick Data Container
  - **BookmarkList** — Container for the list of Bookmarks.
    - **Bookmark** — Container for the data about a particular Bookmark.
      - BookmarkID : String — The unique ID of the bookmark. The ID is prefixed with a 'y' for youth data with BookmarkTypeID of 6, 7, 8 and 9, and prefixed with a 'c' for forum data with BookmarkTypeID of 4, 10 and 11.
      - BookmarkTypeID : BookmarkTypeID — The type of bookmark.
      - Text : String — # See table BookmarkTypeReturnValues
      - Text2 : String — # See table BookmarkTypeReturnValues
      - ObjectID : String — # See table BookmarkTypeReturnValues
      - ObjectID2 : String — # See table BookmarkTypeReturnValues
      - ObjectID3 : String — # See table BookmarkTypeReturnValues
      - Comment : String — The user supplied comment about a bookmark item.

## References
#BookmarkTypeReturnValues
BookmarkTypeID	Text	Text2	ObjectID	ObjectID2	ObjectID3
1	TeamName	UserAlias	TeamID	n/a	n/a
2	PlayerName	Deadline	PlayerID	n/a	n/a
3	Teams	MatchDate	MatchID	n/a	n/a
4	Alias	n/a	UserID	n/a	n/a
5	LeagueUnitName	n/a	LeagueLevelUnitID	LeagueID	n/a
6	YouthTeamName	n/a	YouthTeamID	n/a	n/a
7	YouthPlayerName	n/a	YouthPlayerID	n/a	n/a
8	YouthTeams	MatchDate	YouthMatchID	n/a	n/a
9	YouthLeague	n/a	YouthLeagueID	n/a	n/a
10	ThreadName	ByUser	ThreadID	MessageID	UserID
11	ThreadSubject	ByUser	ThreadID	UserID	n/a
