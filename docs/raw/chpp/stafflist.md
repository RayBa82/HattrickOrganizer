# stafflist

A list of all staff members

## Changelog
### Version 1.2 (2023-09-07)
- Added all trainer info
- Added Output: HattrickData → StaffList → Trainer (Container)
- Added Output: HattrickData → StaffList → Trainer → TrainerId (Element)
- Added Output: HattrickData → StaffList → Trainer → Name (Element)
- Added Output: HattrickData → StaffList → Trainer → Age (Element)
- Added Output: HattrickData → StaffList → Trainer → AgeDays (Element)
- Added Output: HattrickData → StaffList → Trainer → ContractDate (Element)
- Added Output: HattrickData → StaffList → Trainer → Cost (Element)
- Added Output: HattrickData → StaffList → Trainer → CountryID (Element)
- Added Output: HattrickData → StaffList → Trainer → TrainerType (Element)
- Added Output: HattrickData → StaffList → Trainer → Leadership (Element)
- Added Output: HattrickData → StaffList → Trainer → TrainerSkillLevel (Element)
- Added Output: HattrickData → StaffList → Trainer → TrainerStatus (Element)

### Version 1.1 (2015-04-07)
- Added HofPlayerId
- Added Output: HattrickData → StaffList → StaffMembers → Staff → HofPlayerId (Element)

### Version 1.0 (2014-04-03) {default}
- First version

## Input
Only available inputs in this file version are shown.
required file = stafflist
optional version = 
1.2
1.1
1.0
optional teamId : unsigned Integer{Default:Your primary club senior teamId}
Team id to get data for.

## Output
- **HattrickData** — Hattrick Data Container
  - **StaffList** — StaffList Container
    - **Trainer** — Container with the trainer's information.
      - TrainerId : unsigned Integer — The id for the trainer.
      - Name : String — The name of the trainer.
      - Age : unsigned Integer — The age of the trainer in years.
      - AgeDays : unsigned Integer — The amount of days past since last birthday.
      - ContractDate : DateTime — The start date of the contract.
      - Cost : unsigned Integer — The cost per week for the trainer.
      - CountryID : unsigned Integer — CountryID of the country where the trainer was born.
      - TrainerType : unsigned Integer — 0 = Defensive, 1 = Offensive, 2 = Balanced
      - Leadership : SkillLevel — The leadership of the trainer.
      - TrainerSkillLevel : unsigned Integer — The trainer skill level, from 1 (lowest) to 5 (highest).
      - TrainerStatus : unsigned Integer — 1 = PlayingTrainer, 2 = OnlyTrainer, 3 = HoFTrainer
    - **StaffMembers** — Container with list of Staff Members
      - **Staff** — Container with a Staff
        - Name : String — The name of the staff.
        - StaffId : unsigned Integer — The id for the staff.
        - StaffType : StaffType — The type.
        - StaffLevel : unsigned Integer — The level of the staff member.
        - HiredDate : DateTime — The date and time that the staff was hired.
        - Cost : unsigned Integer — The cost per week for the staff.
        - HofPlayerId : unsigned Integer — The id of the hall of fame player associated with this staff member. 0 if none.
    - TotalStaffMembers : unsigned Integer — Total of Staff Members in the staff
    - TotalCost : unsigned Integer — Total cost per week for all the staff members
