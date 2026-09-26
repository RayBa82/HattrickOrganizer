# regiondetails

Detailed information about a region

## Changelog
### Version 1.2 {default}
- First version

## Input
Only available inputs in this file version are shown.
required file = regiondetails
optional version = 
1.2
optional regionID : unsigned Integer
The region to get information for.

## Output
- **HattrickData** — Hattrick Data Container
  - **League** — Container for the data about the league that the region is located in.
    - LeagueID : unsigned Integer — The globally unique LeagueID for the league.
    - LeagueName : String — The name of the league.
    - **Region** — Container for the data about the region.
      - RegionID : unsigned Integer — The globally unique RegionID.
      - RegionName : String — The region name.
      - NumberOfUsers : unsigned Integer — The number of active users in the region.
      - NumberOfOnline : unsigned Integer — The number of users currently online from the region.
      - WeatherID : WeatherID — Integer defining the current weather.
      - TomorrowWeatherID : WeatherID — Integer defining the forecast for tomorrow.
