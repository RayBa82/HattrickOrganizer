# hofplayers

Hall of Fame Players

## Changelog
### Version 1.3 (2026-11-10)
- Added MatchesInClub, BestPerformanceValue, NoOfTimesTrainingMatchHero, FaithfulToTheClub and NoOfCleanSheet. Only shown when the team currently has Gold supporter.
- Added Output: HattrickData → PlayerList → Player → MatchesInClub (Element)
- Added Output: HattrickData → PlayerList → Player → BestPerformanceValue (Element)
- Added Output: HattrickData → PlayerList → Player → NoOfTimesTrainingMatchHero (Element)
- Added Output: HattrickData → PlayerList → Player → FaithfulToTheClub (Element)
- Added Output: HattrickData → PlayerList → Player → NoOfCleanSheet (Element)

### Version 1.2 (2020-06-02)
- Removed Output: HattrickData → PlayerList → Player → CountryID (Element)
- Added Output: HattrickData → PlayerList → Player → CountryID (Element)

### Version 1.1 (2014-07-23)
- Added Output: HattrickData → PlayerList → Player → CountryID (Element)

### Version 1.0 (2011-04-13) {default}
- First version

## Input
Only available inputs in this file version are shown.
required file = hofplayers
optional version = 
1.3
1.2
1.1
1.0
optional teamID : unsigned Integer{Default:Logged in User}
team ID for HoF.

## Output
- **HattrickData** — Hattrick Data Container
  - **PlayerList** — Container for the data about the player.
    - **Player** — Container for a player.
      - PlayerID : unsigned Integer — The globally unique PlayerID.
      - FirstName : String — Player FirstName name.
      - NickName : String — Player NickName name.
      - LastName : String — Player LastName name.
      - Age : unsigned Integer — The age of the player in years.
      - NextBirthday : DateTime — The aproximate Date/time of next birthday.
      - CountryID : unsigned Integer — CountryID of the country where the player was born.
      - ArrivalDate : DateTime — The date of arrival to the team.
      - ExpertType : unsigned Integer — An identifier to show which type of job the player have now. # See table HoFExpertType
      - HofDate : DateTime — The date the player was made hall of fame.
      - HofAge : unsigned Integer — The age of the player in years when he was made hall of fame.
      - MatchesInClub : unsigned Integer — Total number of matches played for the club. Only shown when the team has Gold supporter.
      - BestPerformanceValue : Float — The best rating, in stars, the player got in a match for the club. Only shown when the team has Gold supporter.
      - NoOfTimesTrainingMatchHero : unsigned Integer — Number of times the player was named training match hero. Only shown when the team has Gold supporter.
      - FaithfulToTheClub : unsigned Integer — Number of days the player has stayed with the club without being sold. Divide by 112 to get seasons. Only shown when the team has Gold supporter.
      - NoOfCleanSheet : unsigned Integer — Number of clean sheets kept by the player for the club. Only shown when the team has Gold supporter.

## References
#HoFExpertType
ExpertType	Description
1	Referee
2	Agent
3	Player Union Representative
4	Television Show Host
5	Actor
6	Sports Journalist
7	Marketing Director
8	Restaurant Owner
9	Head Equipment Manager
10	Entrepeneur
11	Hotdog vender
12	Public Relations Manager
13	Sales Director
14	Informercial Host
15	Secretary
16	Unemployed
17	Medic
18	FormCoach
19	Assistant Trainer
20	Spokesperson
21	Psychologist
22	Financial Director
23	Youth Trainer
24	Youth Scout
25	Tactical Assistant
26	Sports Director
27	Goalkeepers Coach
28	Supporter Liaison Officer
29	Chief Financial Officer
30	Press Manager
31	Head Coach
