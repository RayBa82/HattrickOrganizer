# training

Training

## Changelog
### Version 2.2 (2013-11-21)
- Removed Input: teamId
- Added Input: teamId
- Added Input: trainingType
- Added Input: trainingLevel
- Added Input: trainingLevelStamina
- Added Action for 'actionType': setTraining (Set the training settings for the logged-in user's team. (Requires "set_training" scope.))
- Added Output: HattrickData (Container)
- Added Output: HattrickData → TrainingSet (Element)
- Added Output: HattrickData → View (Container)

### Version 2.1 (2013-05-02)
- Added Input: teamId

### Version 2.0 (2013-03-04)
- Removed Output: HattrickData → UserIsSupporter (Element)
- Added Output: HattrickData → UserSupporterTier (Element)

### Version 1.9 (2011-02-12)
- Added Output: HattrickData → Team → LastTrainingTrainingLevel (Element)
- Added Output: HattrickData → Team → LastTrainingStaminaTrainingPart (Element)

### Version 1.8 (2011-02-03)
- Added Output: HattrickData → Team → LastTrainingTrainingType (Element)

### Version 1.7 (2011-01-17)
- Added Output: HattrickData (Container)
- Added Output: HattrickData → League (Container)
- Added Output: HattrickData → League → LeagueID (Element)
- Added Output: HattrickData → League → LeagueName (Element)
- Added Output: HattrickData → League → TrainingStatList (Container)
- Added Output: HattrickData → League → TrainingStatList → TrainingStat (Element)
- Added Output: HattrickData → League → TrainingStatList → NumberOfTeams (Element)
- Added Output: HattrickData → League → TrainingStatList → FractionOfTeams (Element)

### Version 1.6 (2010-11-08)
- Added Output: HattrickData → Team → SpecialTraining (Container)
- Added Output: HattrickData → Team → SpecialTraining → Player (Container)
- Added Output: HattrickData → Team → SpecialTraining → Player → PlayerID (Element)
- Added Output: HattrickData → Team → SpecialTraining → Player → SpecialTrainingTypeID (Element)
- Added Output: HattrickData → Team → SpecialTraining → TrainerName (Element)
- Added Output: HattrickData → Team → SpecialTraining → ArrivalDate (Element)

### Version 1.5
- Added Output: HattrickData → Team → Experience442 (Element)
- Added Output: HattrickData → Team → Experience523 (Element)
- Added Output: HattrickData → Team → Experience550 (Element)
- Added Output: HattrickData → Team → Experience253 (Element)

### Version 1.4
- Removed Output: HattrickData → userHasClubhouse (Element)

### Version 1.3 {default}
- First version

## Input
Only available inputs in this file version are shown.
required file = training
optional version = 
2.2
2.1
2.0
1.9
1.8
1.7
1.6
1.5
1.4
1.3
optional actionType = 
view {default}
stats
setTraining
Indicates what type of action the page should perform.
view {default}
Training information for the logged-in user's team
stats
Shows the distribution among training types for all leagues or for a specific league. Requires that the logged on user is a Hattrick Supporter.
optional leagueID : unsigned Integer{Default:Your leagueID}
What LeagueID to retrieve statistics for (empty for global stats in version 1.7 or above).
setTraining
Set the training settings for the logged-in user's team. (Requires "set_training" scope.)
required_1 teamId : unsigned Integer{Default:Your primary club senior teamId, except in action setTraining}
Team id to view training information for. Must refer to a senior team managed by requesting user.
optional trainingType : trainingType
Type of training to be set for the specified team if action is setTraining.
optional trainingLevel : unsigned Integer
Training level to be set for the specified team (0-100) if action is setTraining.
optional trainingLevelStamina : unsigned Integer
Stamina level to be set for the specified team (5-100) if action is setTraining.
required_1: TeamId is required for actionType=setTraining, no default will be used!

## Output (actionType=view)
- **HattrickData** — Hattrick Data Container for actionType=view
  - UserSupporterTier : supporterTier — Indicates which Supporter package the fetching user has, or empty if not a Supporter.
  - **Team** — Container for teams in the group.
    - TeamID : unsigned Integer — The globally unique TeamID.
    - TeamName : String — The name of the team.
    - TrainingLevel : unsigned Integer — The current training level for the team in percent.
    - NewTrainingLevel : unsigned Integer — Attribute: 'Available' : Boolean If the team is not about to change training level, the attribute is set to false and the container is empty, otherwise to true.
    - TrainingType : trainingType — Integer defining the type of training.
    - StaminaTrainingPart : unsigned Integer — The current part of training level, in percent, used on stamina trainging.
    - LastTrainingTrainingType : trainingType — The trainingtype that took place last training.
    - LastTrainingTrainingLevel : unsigned Integer — The training intensity that was used the last training.
    - LastTrainingStaminaTrainingPart : unsigned Integer — The stamina share that was used the last training.
    - **Trainer** — Container for the data about the trainer of the team.
      - TrainerID : unsigned Integer — The globally unique PlayerID for the trainer.
      - TrainerName : String — The name of the trainer.
      - ArrivalDate : DateTime — The date the trainer became trainer of the team.
    - **SpecialTraining** — Container for data about special training instructions. Empty if no special training instructions are given. Contains at most 3 Player nodes.
      - **Player** — Container for data about a specific players special training.
        - PlayerID : unsigned Integer — The globally unique PlayerID for the player with special training instructions.
        - SpecialTrainingTypeID : unsigned Integer — The type of special training is always 1 = Stamina
      - TrainerName : String — The name of the trainer.
      - ArrivalDate : DateTime — The date the trainer became trainer of the team.
    - Morale : TeamSpiritID — Attribute: 'Available' : Boolean If the team is playing a match, the attribute is set to false and the container is empty, otherwise to true.
    - SelfConfidence : SelfConfidenceID — Attribute: 'Available' : Boolean If the team is playing a match, the attribute is set to false and the container is empty, otherwise to true.
    - Experience442 : SkillLevel — The team's experience of playing 4-4-2.
    - Experience433 : SkillLevel — The team's experience of playing 4-3-3.
    - Experience451 : SkillLevel — The team's experience of playing 4-5-1.
    - Experience352 : SkillLevel — The team's experience of playing 3-5-2.
    - Experience532 : SkillLevel — The team's experience of playing 5-3-2.
    - Experience343 : SkillLevel — The team's experience of playing 3-4-3.
    - Experience541 : SkillLevel — The team's experience of playing 5-4-1.
    - Experience523 : SkillLevel — The team's experience of playing 5-2-3.
    - Experience550 : SkillLevel — The team's experience of playing 5-5-0.
    - Experience253 : SkillLevel — The team's experience of playing 2-5-3.

## Output (actionType=stats)
- **HattrickData** — Container for the stats about training.
  - **League** — Container for the training supporter stats.
    - LeagueID : unsigned Integer — The globally unique LeagueID.
    - LeagueName : String* — The name of the league. Empty for global stats.
    - **TrainingStatList** — Container for training stats for the league.
      - TrainingStat : trainingType — Container for a particular training stat.
      - NumberOfTeams : unsigned Integer — Number of teams with this training type in this league.
      - FractionOfTeams : unsigned Integer — Percentage of teams with this training type in this league.

## Output (actionType=setTraining)
- **HattrickData** — Container for the data about setting training.
  - TrainingSet : Boolean — True if setting the training was successful. Only available for actionType=setTraining.
  - **View** — The same data as in actionType=view is shown here.
