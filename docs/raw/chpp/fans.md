# fans

Fanclub information

## Changelog
### Version 1.3 (2018-06-01)
- Removed Output: HattrickData → Team → UpcomingMatches → Match → FanMatchExpectation (Element)
- Added Output: HattrickData → Team → UpcomingMatches → Match → FanMatchExpectation (Element)

### Version 1.2 (2013-05-02)
- Added Input: teamId

### Version 1.1 (2011-04-13)
- Added Output: HattrickData → Team → FanClubId (Element)
- Added Output: HattrickData → Team → FanClubName (Element)

### Version 1.0 {default}
- First version

## Input
Only available inputs in this file version are shown.
required file = fans
optional version = 
1.3
1.2
1.1
1.0
optional teamId : unsigned Integer{Default:Your primary club senior teamId}
Team id to get data for. Must refer to a senior team managed by requesting user.

## Output
- **HattrickData** — Hattrick Data Container
  - **Team** — Container for the data about the team.
    - TeamID : unsigned Integer — The globally unique TeamID for logged on user.
    - FanClubId : unsigned Integer — The globally unique FanclubID.
    - FanClubName : String* — The name of the fanclub. If the user have not set a fanclub name the container is empty.
    - FanMood : unsigned Integer — The popularity among the supporters of the team. If a match is running this value is not available # See table FanMood
    - Members : unsigned Integer — The size of the team's fanclub.
    - FanSeasonExpectation : unsigned Integer — The expectations for current season. # See table FanSeasonExpectation
    - **PlayedMatches** — Container for the data about the three last played matches. Only series, qualification, cup and hattrick master games are listed.
      - **Match** — The container for a match.
        - MatchID : unsigned Integer — The globally unique MatchID for the match.
        - **HomeTeam** — The Home (formal, not taking arena location into account) team of the match.
          - HomeTeamID : Integer — The teamID of the Home team in the match. (Negative for street teams)
          - HomeTeamName : String — The team name of the Home team in the match.
        - **AwayTeam** — The away (formal, not taking arena location into account) team of the match.
          - AwayTeamID : Integer — The teamID of the away team in the match. (Negative for street teams)
          - AwayTeamName : String — The team name of the away team in the match.
        - MatchDate : DateTime — The start date (kick-off) of the match.
        - MatchType : MatchTypeID — Integer defining the type of match
        - HomeGoals : unsigned Integer — The current number of goals in the match for the home team.
        - AwayGoals : unsigned Integer — The current number of goals in the match for the away team.
        - FanMatchExpectation : unsigned Integer — Expectation for the match. # See table FanMatchExpectation
        - FanMoodAfterMatch : unsigned Integer — Fan mood after the match. # See table FanMood
        - Weather : WeatherID — Wheather during the match.
        - SoldSeats : unsigned Integer — The total number of sold seats.
    - **UpcomingMatches** — Container for the data about the three upcoming matches. Only series, qualification, cup and hattrick master games are listed.
      - **Match** — The container for a match.
        - MatchID : unsigned Integer — The globally unique MatchID for the match.
        - **HomeTeam** — The Home (formal, not taking arena location into account) team of the match.
          - HomeTeamID : Integer — The teamID of the Home team in the match. (Negative for street teams)
          - HomeTeamName : String — The team name of the Home team in the match.
        - **AwayTeam** — The away (formal, not taking arena location into account) team of the match.
          - AwayTeamID : Integer — The teamID of the away team in the match. (Negative for street teams)
          - AwayTeamName : String — The team name of the away team in the match.
        - MatchDate : DateTime — The start date (kick-off) of the match.
        - MatchType : MatchTypeID — Integer defining the type of match.
        - FanMatchExpectation : unsigned Integer — Expectation for the match. # See table FanMatchExpectation

## References
#FanMatchExpectation
MatchExpectation	Description
8	We will win
7	We are favourites
6	We have the edge
5	It will be a close affair
4	They have the edge
3	They are favourites
2	We will lose
1	We are outclassed
0	Better not show up
#FanMood
Mood	Description
11	Sending love poems to you
10	dancing in the streets
9	high on life
8	delirious
7	satisfied
6	content
5	calm
4	disappointed
3	irritated
2	angry
1	furious
0	murderous
#FanSeasonExpectation
SeasonExpectation	Description
7	We are so much better than this devision!
6	We have to win this season
5	Aim for the title!
4	We belong in the top4
3	A mid table finish is nice
2	We will have to fight to stay up
1	Every day in this devision is a bonus
0	We are not worthy of this devision
