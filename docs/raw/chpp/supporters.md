# supporters

Information about teams supported and teams supporting.

## Changelog
### Version 1.0 (2013-12-16) {default}
- First version

## Input
Only available inputs in this file version are shown.
required file = supporters
optional version = 
1.0
optional actionType = 
supportedteams {default}
mysupporters
Indicates what type of action the page should perform
supportedteams {default}
See the teams the user support.
optional userId : unsigned Integer{Default:The user id of the logged on user.}
User id to get data for.
mysupporters
See the teams supporting the team.
optional teamId : unsigned Integer{Default:Your primary club senior teamId}
Team id to get data for.
optional pageIndex : unsigned Integer{Default:0}
Zero based pageindex to retrieve.
optional pageSize : unsigned Integer{Default:50}
How many results to be shown in each page, the maximum value is 500. Note however, you should expect longer responsetimes the more items that will be returned!

## Output (actionType=supportedteams)
- **HattrickData** — Hattrick Data Container
  - **SupportedTeams** — Attribute: 'TotalItems' : unsigned Integer Shows how many teams the user supports in total.
    - **SupportedTeam** — Container for a supported team. Not supplied if the user dont have no supporters.
      - UserId : unsigned Integer — The globally unique UserID for the owner of the supported team.
      - LoginName : String — The 'username' or 'nickname' used in Forums and around the site.
      - TeamId : unsigned Integer — The globally unique TeamID for the supported team.
      - TeamName : String — The teamname of the supported team.
      - LeagueID : unsigned Integer — The globally unique LeagueID for the supported team.
      - LeagueName : String — The League name.
      - LeagueLevelUnitID : unsigned Integer — The globally unique LeagueLevelUnitID for the supported team.
      - LeagueLevelUnitName : String — The name of the LeagueLevelUnit.
      - **LastMatch** — Container for the last match of the supported team.
        - LastMatchId : unsigned Integer — The globally unique matchID.
        - LastMatchDate : DateTime — The matchdate of the last match.
        - LastMatchHomeTeamId : unsigned Integer — The globally unique TeamID for the home team.
        - LastMatchHomeTeamName : String — The team name of the home team in the match.
        - LastMatchHomeGoals : unsigned Integer — The number of goals scored in the match by the hometeam.
        - LastMatchAwayTeamId : unsigned Integer — The globally unique TeamID for the away team.
        - LastMatchAwayTeamName : String — The team name of the away team in the match.
        - LastMatchAwayGoals : unsigned Integer — The number of goals scored in the match by the awayteam.
      - **NextMatch** — Container for the last match of the supported team.
        - NextMatchId : unsigned Integer — The globally unique matchID.
        - NextMatchDate : DateTime — The matchdate of the next match.
        - NextMatchHomeTeamId : unsigned Integer — The globally unique TeamID for the home team.
        - NextMatchHomeTeamName : String — The team name of the home team in the match.
        - NextMatchAwayTeamId : unsigned Integer — The globally unique TeamID for the away team.
        - NextMatchAwayTeamName : String — The team name of the away team in the match.
      - **PressAnnouncement** — Container for the data about the user's most recent PressAnnouncement.
        - PressAnnouncementSendDate : DateTime — The time and date when the PressAnnouncement was submitted.
        - PressAnnouncementSubject : String — The subject text specified for the PressAnnouncement.
        - PressAnnouncementBody : String — The body of the PressAnnouncement.

## Output (actionType=mysupporters)
- **HattrickData** — Hattrick Data Container
  - **MySupporters** — Attribute: 'TotalItems' : unsigned Integer Shows how many teams supports the team in total.
    - **SupporterTeam** — Container for a supported team. Not supplied if the user dont have no supporters.
      - UserId : unsigned Integer — The globally unique UserID for the owner of the supporter team.
      - LoginName : String — The 'username' or 'nickname' used in Forums and around the site.
      - TeamId : unsigned Integer — The globally unique TeamID for the supporter team.
      - TeamName : String — The teamname of the supporter team.
      - LeagueID : unsigned Integer — The globally unique LeagueID for the supporter team.
      - LeagueName : String — The League name.
      - LeagueLevelUnitID : unsigned Integer — The globally unique LeagueLevelUnitID for the supported team.
      - LeagueLevelUnitName : String — The name of the LeagueLevelUnit.
