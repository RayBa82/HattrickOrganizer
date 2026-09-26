# youthteamdetails

Youth team information.

## Changelog
### Version 1.4 (2026-02-26)
- Added team LeagueSystemId.
- Added Output: HattrickData → YouthTeam → LeagueSystemID (Element)

### Version 1.3 (2026-02-10)
- Added genderID
- Added Output: HattrickData → YouthTeam → GenderID (Element)

### Version 1.2 (2022-03-03)
- Added UserID.
- Added Output: HattrickData → YouthTeam → UserID (Element)

### Version 1.1 (2015-04-15)
- Added HofPlayerId to scout list
- Added Output: HattrickData → YouthTeam → ScoutList → Scout → HofPlayerId (Element)

### Version 1.0 {default}
- First version

## Input
Only available inputs in this file version are shown.
required file = youthteamdetails
optional version = 
1.4
1.3
1.2
1.1
1.0
optional youthTeamId : unsigned Integer{Default:Your YouthTeamID}
What team/user to show the data for.
optional showScouts : Boolean{Default:false}
Set it to 'true' to get ScoutList container.

## Output
- **HattrickData** — Hattrick Data Container
  - **YouthTeam** — Container for the youthteam to which the data applies.
    - YouthTeamID : unsigned Integer — The globally unique YouthTeamID.
    - YouthTeamName : String — The full team name.
    - ShortTeamName : String — The short team name.
    - CreatedDate : DateTime — The date when the youth team was created.
    - UserID : unsigned Integer — The globally unique UserID for the owner of the team.
    - GenderID : GenderID — The gender of the youth team.
    - LeagueSystemID : LeagueSystemID — The system type of the league for this team.
    - **Country** — Container for the country of the team.
      - CountryID : unsigned Integer — The globally unique CountryID.
      - CountryName : String — The name of the country.
    - **Region** — Container for the region of the team
      - RegionID : unsigned Integer — The globally unique RegionID.
      - RegionName : String — The name of the region.
    - **YouthArena** — Container for the youth arena.
      - YouthArenaID : unsigned Integer — The globally unique YouthArenaID.
      - YouthArenaName : String — The arena name.
    - **YouthLeague** — Container for the youthleague the team is participating in. Empty if the team is currently not participating in any league.
      - YouthLeagueID : unsigned Integer — The globally unique YouthLeagueID.
      - YouthLeagueName : String — The name of the youth league
      - YouthLeagueStatus : YouthLeagueStatusID — The status of the youth league
    - **OwningTeam** — Container for the list of youthplayers. Empty if the team has no owner.
      - MotherTeamID : unsigned Integer — The globally unique TeamID of the motherteam.
      - MotherTeamName : String — The name of the motherteam
    - **YouthTrainer** — The player that is trainer ('coach') for the team.
      - YouthPlayerID : unsigned Integer — The globally unique YouthPlayerID of the trainer.
    - NextTrainingMatchDate : DateTime — The date that the next friendly match can be booked. (Will be less than today if it can be booked now. If a friendly is already booked it will be more than 3 weeks into the future.)
    - **ScoutList** — Container for the list of youthplayers. Only available with showScouts-flag as true.
      - **Scout** — Container for the data about the scout.
        - YouthScoutID : unsigned Integer — The globally unique YouthScoutID.
        - ScoutName : String — The name of the scout.
        - Age : unsigned Integer — The age of the scout.
        - **Country** — Container for the home country of the scout.
          - CountryID : unsigned Integer — The globally unique CountryID.
          - CountryName : String — The name of the country.
        - **Region** — Container for the home region of the scout
          - RegionID : unsigned Integer — The globally unique RegionID.
          - RegionName : String — The name of the region.
        - **InCountry** — Container for the country the scout is currently in.
          - CountryID : unsigned Integer — The globally unique CountryID.
          - CountryName : String — The name of the country.
        - **InRegion** — Container for the region the scout is currently in.
          - RegionID : unsigned Integer — The globally unique RegionID.
          - RegionName : String — The name of the region.
        - HiredDate : DateTime — The date the team hired the scout.
        - LastCalled : DateTime — The date of when the scout was last called by the team.
        - PlayerTypeSearch : ScoutSearchTypeID — Specifies which type of player the scout is currently searching for.
        - HofPlayerId : unsigned Integer — The id of the hall of fame player associated with this scout. 0 if none.
        - **Travel** — Container with information about the scouts travel. Empty if the scout is currently not traveling.
          - TravelStartDate : DateTime — The date the scout started his travel.
          - TravelLength : unsigned Integer — How far the travel is in kilometers.
          - TravelType : ScoutTravelTypeID — By which mean the scout is traveling.
