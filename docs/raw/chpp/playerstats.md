# playerstats

Detailed player development history.

## Changelog
### Version 1.0 (2026-11-10) {default}
- First version

## Input
Only available inputs in this file version are shown.
required file = playerstats
optional version = 
1.0
optional playerID : unsigned Integer
The player to get statistics for.

## Output
- **HattrickData** — Hattrick Data Container
  - **Player** — Container for the player the statistics apply to.
    - PlayerID : unsigned Integer — The PlayerID.
    - FirstName : String — The player's first name.
    - NickName : String — The player's nickname.
    - LastName : String — The player's last name.
    - PlayerNumber : unsigned Integer — The number (1 to 99) that the owner of the team has assigned this player. Not shown if the player has no number.
    - **OwningTeam** — The team that currently owns the player.
      - TeamID : unsigned Integer — The owning team's TeamID.
      - TeamName : String — The owning team's name.
      - LeagueID : unsigned Integer — The globally unique LeagueID of the owning team's league.
    - **MatchStats** — The player's per-match performance history. Contains one Match node per match.
      - **Match** — A single match the player was involved in, with the player's state and performance in that match.
        - MatchID : unsigned Integer — The match's unique MatchID.
        - MatchType : MatchTypeID — The type of the match.
        - SourceSystem : sourceSystem — The system the match belongs to. Use it with MatchID to fetch the match in matchdetails.xml.
        - MatchDate : DateTime — The date and time the match was played.
        - **HomeTeam** — The home team of the match.
          - HomeTeamName : String — The home team's name.
        - **AwayTeam** — The away team of the match.
          - AwayTeamName : String — The away team's name.
        - IsWalkover : Boolean — True if the match was a walkover.
        - Age : unsigned Integer — The player's age in years at the time of the match.
        - AgeDays : unsigned Integer — The player's age days at the time of the match.
        - TSI : unsigned Integer — The player's TSI at the time of the match.
        - PlayerForm : SkillLevel — The player's form at the time of the match.
        - Stamina : SkillLevel — The player's stamina skill at the start of the match. Empty if there is no value, for example when the player was not in the lineup.
        - Loyalty : SkillLevel — The player's loyalty at the time of the match. Empty if there is no value.
        - Experience : SkillLevel — The player's experience at the time of the match.
        - GoalsInMatch : unsigned Integer — Number of goals the player scored in the match.
        - YellowCardsInMatch : unsigned Integer — Number of yellow cards the player received in the match.
        - RedCardsInMatch : unsigned Integer — Number of red cards the player received in the match.
        - InjuryLevel : Integer — The player's injury as a result of the match: -1 = healthy, 0 = bruised, >0 = number of weeks injured. 999 means the player will not recover before retiring.
        - PlayedMinutes : unsigned Integer — Total number of minutes the player was on the pitch in the match.
        - Rating : Float — The player's average star rating in the match. 0 if the player did not get a rating, for example if the player did not play or the match was a walkover.
        - RatingEndOfGame : Float — The player's star rating at the end of the match. 0 if the player did not get a rating.
        - **Positions** — The positions the player occupied during the match, with minutes played in each. Contains one Position node per occupied position group. Central defenders, inner midfielders and forwards are grouped, and the group is shown with the code of its right-side position (102, 107 or 111). Empty if the player did not play.
          - **Position** — A single position the player occupied during the match.
            - PositionCode : MatchPositionID — The position code the player occupied.
            - Minutes : unsigned Integer — Minutes the player spent in this position.
