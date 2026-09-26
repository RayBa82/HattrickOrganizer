# challenges

Challenges

## Changelog
### Version 1.6 (2016-10-27)
- Removed Output: HattrickData → Team → ChallengesByMe → Challenge → Opponent → League (Container)
- Removed Output: HattrickData → Team → ChallengesByMe → Challenge → Opponent → League → LeagueID (Element)
- Removed Output: HattrickData → Team → ChallengesByMe → Challenge → Opponent → League → LeagueName (Element)
- Added Output: HattrickData → Team → ChallengesByMe → Challenge → Opponent → Country (Container)
- Added Output: HattrickData → Team → ChallengesByMe → Challenge → Opponent → Country → CountryID (Element)
- Added Output: HattrickData → Team → ChallengesByMe → Challenge → Opponent → Country → CountryName (Element)

### Version 1.5 (2015-03-02)
- Added Input: isWeekendFriendly

### Version 1.4 (2013-05-02)
- Added Input: teamId

### Version 1.3 (2011-02-17)
- Added Action for 'actionType': withdraw (Withdraw a not yet accepted challenge. (Requires "manage_challenges" scope.))
- Added Output: HattrickData → Team → ChallengesByMe → Challenge → MatchID (Element)
- Added Output: HattrickData → Team → ChallengesByMe → Challenge → Opponent → LogoURL (Element)
- Added Output: HattrickData → Team → OffersByOthers → Offer → MatchID (Element)
- Added Output: HattrickData → Team → OffersByOthers → Offer → Opponent → LogoURL (Element)

### Version 1.2 (2010-11-08) {default}
- First version

## Input
Only available inputs in this file version are shown.
required file = challenges
optional version = 
1.6
1.5
1.4
1.3
1.2
optional actionType = 
view {default}
challengeable
challenge
accept
decline
withdraw
Indicates what type of action the page should perform.
view {default}
Returns data on the challenges you have made and the offers you have received.
challengeable
Check if it is possible to challenge the teams specified in parameter suggestedTeamIds. (Requires "manage_challenges" scope.)
optional suggestedTeamIds : String
A comma-separated list of teamid numbers for the teams that you want to check challengeable status for.
challenge
Challenge an opponent to a friendly match. See parameter list above for which parameters must be specified to create the challenge. This allows CHPP to book a friendly match regardless of the users settings on the site, as long as both users have the extended permission necessary. (Requires "manage_challenges" scope.)
required opponentTeamId : unsigned Integer
The teamid of the team you want to challenge.
optional matchType : unsigned Integer{Default:0}
Specifies whether to challenge for a normal friendly (0) or a friendly with cup rules (1).
optional_1 matchPlace : unsigned Integer{Default:0}
Defines if challenger wants to play home (0), away (1) or on a neutral arena (2).
optional_1 neutralArenaId : unsigned Integer{Default:0}
ArenaId for arena where friendly on neutral ground should be played.
accept
Accept a challenge from an opponent. (Requires "manage_challenges" scope.)
optional trainingMatchId : unsigned Integer
Specifies the training match id for the friendly offer to accept.
decline
Decline a challenge from an opponent. (Requires "manage_challenges" scope.)
optional trainingMatchId : unsigned Integer
Specifies the training match id for the friendly offer to decline.
withdraw
Withdraw a not yet accepted challenge. (Requires "manage_challenges" scope.)
optional trainingMatchId : unsigned Integer
Specifies the training match id for the friendly offer to withdraw.
optional teamId : unsigned Integer{Default:Your primary club senior teamId}
Team id to manage challenges for. Must refer to a senior team managed by requesting user.
optional isWeekendFriendly : unsigned Integer{Default:0}
Show weekend Friendly match och use it to set data for weekend friendly
optional_1: If matchPlace = 2, then you have to set the arena in neutralArenaId!

## Output (actionType=view)
- **HattrickData** — Hattrick Data Container for actionType=view
  - **Team** — Container for a team in the group.
    - TeamID : unsigned Integer — The globally unique nationalTeamID.
    - TeamName : String — The team name.
    - **ChallengesByMe** — Container for the challenges that the user has done.
      - **Challenge** — Container for a particular challenge.
        - TrainingMatchID : unsigned Integer — The globally unique identifier for the challenge/offer.
        - MatchTime : unsigned Integer — The match start time.
        - MatchID : unsigned Integer — The matchid for the match that was created for an accepted challenge.
        - FriendlyType : unsigned Integer — Integer defining the type of friendly. # See table FriendlyType
        - **Opponent** — Container for the data about the opponent.
          - TeamID : unsigned Integer — The globally unique nationalTeamID.
          - TeamName : String — The team name.
          - LogoURL : URI — The url for the opponent's logo, if it has one.
          - **Arena** — Container for the data about the arena for the friendly.
            - ArenaID : unsigned Integer — The globally unique arenaID.
            - ArenaName : String — The name of the arena.
          - **Country** — Container for the data about the country that the friendly would be played in.
            - CountryID : unsigned Integer — The globally unique CountryID.
            - CountryName : String — The country name.
          - IsAgreed : Boolean — Indicating whether the challenge/offer has been accepted and the match arranged.
    - **OffersByOthers** — Container for the offers of friendlies that other teams have given to the logged on user's team.
      - **Offer** — Container for a particular offer.
        - TrainingMatchID : unsigned Integer — The globally unique identifier for the challenge/offer.
        - MatchTime : unsigned Integer — The match start time.
        - MatchID : unsigned Integer — The matchid for the match that was created for an accepted challenge.
        - FriendlyType : unsigned Integer — Integer defining the type of friendly. # See table FriendlyType
        - **Opponent** — Container for the data about the opponent.
          - TeamID : unsigned Integer — The globally unique nationalTeamID.
          - TeamName : String — The team name.
          - LogoURL : URI — The url for the opponent's logo, if it has one.
          - **Arena** — Container for the data about the arena for the friendly.
            - ArenaID : unsigned Integer — The globally unique arenaID.
            - ArenaName : String — The name of the arena.
          - **League** — Container for the data about the league that the friendly would be played in.
            - LeagueID : unsigned Integer — The globally unique LeagueID.
            - LeagueName : String — The League name.
          - IsAgreed : Boolean — Indicating whether the challenge/offer has been accepted and the match arranged.

## Output (actionType=challengeable)
- **HattrickData** — Hattrick Data Container for actionType=challengeable
  - **Team** — Container for a team in the group.
    - TeamID : unsigned Integer — The globally unique nationalTeamID.
    - TeamName : String — The team name.
    - **ChallengesByMe** — Container for the challenges that the user has done.
      - **Challenge** — Container for a particular challenge.
        - TrainingMatchID : unsigned Integer — The globally unique identifier for the challenge/offer.
        - MatchTime : unsigned Integer — The match start time.
        - MatchID : unsigned Integer — The matchid for the match that was created for an accepted challenge.
        - FriendlyType : unsigned Integer — Integer defining the type of friendly. # See table FriendlyType
        - **Opponent** — Container for the data about the opponent.
          - TeamID : unsigned Integer — The globally unique teamID.
          - TeamName : String — The team name.
          - LogoURL : URI — The url for the opponent's logo, if it has one.
          - **Arena** — Container for the data about the arena for the friendly.
            - ArenaID : unsigned Integer — The globally unique arenaID.
            - ArenaName : String — The name of the arena.
          - **League** — Container for the data about the league that the friendly would be played in.
            - LeagueID : unsigned Integer — The globally unique LeagueID.
            - LeagueName : String — The League name.
          - IsAgreed : Boolean — Indicating whether the challenge/offer has been accepted and the match arranged.
    - **OffersByOthers** — Container for the offers of friendlies that other teams have given to the logged on user's team.
      - **Offer** — Container for a particular offer.
        - TrainingMatchID : unsigned Integer — The globally unique identifier for the challenge/offer.
        - MatchTime : unsigned Integer — The match start time.
        - MatchID : unsigned Integer — The matchid for the match that was created for an accepted challenge.
        - FriendlyType : unsigned Integer — Integer defining the type of friendly. # See table FriendlyType
        - **Opponent** — Container for the data about the opponent.
          - TeamID : unsigned Integer — The globally unique teamID.
          - TeamName : String — The team name.
          - LogoURL : URI — The url for the opponent's logo, if it has one.
          - **Arena** — Container for the data about the arena for the friendly.
            - ArenaID : unsigned Integer — The globally unique arenaID.
            - ArenaName : String — The name of the arena.
          - **League** — Container for the data about the league that the friendly would be played in.
            - LeagueID : unsigned Integer — The globally unique LeagueID.
            - LeagueName : String — The League name.
          - IsAgreed : Boolean — Indicating whether the challenge/offer has been accepted and the match arranged.
    - **ChallengeableResult** — Container for data about the challengeable status of the teams specified in query.
      - **Opponent** — Container for data about a possible opponent.
        - IsChallengeable : Boolean — Specifying whether or not an opponent is challengeable.
        - UserId : unsigned Integer — UserId of the opponent.
        - TeamID : unsigned Integer — The globally unique teamID.
        - TeamName : unsigned Integer — Teamname of the opponent.
        - LogoUrl : URI — Url for the logo of the opponent.

## Output (actionType=challenge)
- **HattrickData** — Hattrick Data Container for actionType=challenge
  - **Team** — Container for a team in the group.
    - TeamID : unsigned Integer — The globally unique nationalTeamID.
    - TeamName : String — The team name.
    - **ChallengesByMe** — Container for the challenges that the user has done.
      - **Challenge** — Container for a particular challenge.
        - TrainingMatchID : unsigned Integer — The globally unique identifier for the challenge/offer.
        - MatchTime : unsigned Integer — The match start time.
        - MatchID : unsigned Integer — The matchid for the match that was created for an accepted challenge.
        - FriendlyType : unsigned Integer — Integer defining the type of friendly. # See table FriendlyType
        - **Opponent** — Container for the data about the opponent.
          - TeamID : unsigned Integer — The globally unique nationalTeamID.
          - TeamName : String — The team name.
          - LogoURL : URI — The url for the opponent's logo, if it has one.
          - **Arena** — Container for the data about the arena for the friendly.
            - ArenaID : unsigned Integer — The globally unique arenaID.
            - ArenaName : String — The name of the arena.
          - **League** — Container for the data about the league that the friendly would be played in.
            - LeagueID : unsigned Integer — The globally unique LeagueID.
            - LeagueName : String — The League name.
          - IsAgreed : Boolean — Indicating whether the challenge/offer has been accepted and the match arranged.
    - **OffersByOthers** — Container for the offers of friendlies that other teams have given to the logged on user's team.
      - **Offer** — Container for a particular offer.
        - TrainingMatchID : unsigned Integer — The globally unique identifier for the challenge/offer.
        - MatchTime : unsigned Integer — The match start time.
        - MatchID : unsigned Integer — The matchid for the match that was created for an accepted challenge.
        - FriendlyType : unsigned Integer — Integer defining the type of friendly. # See table FriendlyType
        - **Opponent** — Container for the data about the opponent.
          - TeamID : unsigned Integer — The globally unique nationalTeamID.
          - TeamName : String — The team name.
          - LogoURL : URI — The url for the opponent's logo, if it has one.
          - **Arena** — Container for the data about the arena for the friendly.
            - ArenaID : unsigned Integer — The globally unique arenaID.
            - ArenaName : String — The name of the arena.
          - **League** — Container for the data about the league that the friendly would be played in.
            - LeagueID : unsigned Integer — The globally unique LeagueID.
            - LeagueName : String — The League name.
          - IsAgreed : Boolean — Indicating whether the challenge/offer has been accepted and the match arranged.

## Output (actionType=accept)
Same as actionType=challenge.

## Output (actionType=decline)
Same as actionType=challenge.

## Output (actionType=withdraw)
Same as actionType=challenge.

## References
#FriendlyType
FriendlyType	Description
0	Normal Rules
1	Cup Rules
12	National team friendly
