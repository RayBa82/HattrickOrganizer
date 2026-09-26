# club

Information about specialists and youth

## Changelog
### Version 1.5 (2015-09-22)
- Added tactical assistant staff member.
- Added Output: HattrickData → Team → Staff → TacticalAssistantLevels (Element)

### Version 1.4 (2014-04-09)
- Added new Staffsystem to the file to show the levels of each type. For more detailed staff info. Use StaffList.xml or StaffAvatars.xml. After the 2014/04/14 the old staff system will return 0 to the old versions of this file.
- Removed Output: HattrickData → Team → Specialists (Container)
- Removed Output: HattrickData → Team → Specialists → AssistantTrainers (Element)
- Removed Output: HattrickData → Team → Specialists → Psychologists (Element)
- Removed Output: HattrickData → Team → Specialists → PressSpokesmen (Element)
- Removed Output: HattrickData → Team → Specialists → Physiotherapists (Element)
- Removed Output: HattrickData → Team → Specialists → Doctors (Element)
- Added Output: HattrickData → Team → Staff (Container)
- Added Output: HattrickData → Team → Staff → AssistantTrainerLevels (Element)
- Added Output: HattrickData → Team → Staff → FinancialDirectorLevels (Element)
- Added Output: HattrickData → Team → Staff → FormCoachLevels (Element)
- Added Output: HattrickData → Team → Staff → MedicLevels (Element)
- Added Output: HattrickData → Team → Staff → SpokespersonLevels (Element)
- Added Output: HattrickData → Team → Staff → SportPsychologistLevels (Element)

### Version 1.3 (2013-05-02)
- Added Input: teamId

### Version 1.2 (2010-11-08)
- Removed Output: HattrickData → Team → Specialists → KeeperTrainers (Element)
- Removed Output: HattrickData → Team → Specialists → Economists (Element)

### Version 1.1 {default}
- First version

## Input
Only available inputs in this file version are shown.
required file = club
optional version = 
1.5
1.4
1.3
1.2
1.1
optional teamId : unsigned Integer{Default:Your primary club senior teamId}
Team id to get data for. Must refer to a senior team managed by requesting user.

## Output
- **HattrickData** — Hattrick Data Container
  - **Team** — Container for the data about the team.
    - TeamID : unsigned Integer — The globally unique TeamID.
    - TeamName : String — The name of the team.
    - **Staff** — Container for the staff.
      - AssistantTrainerLevels : unsigned Integer — The level of the Assistant Trainers.
      - FinancialDirectorLevels : unsigned Integer — The level of the Financial Director.
      - FormCoachLevels : unsigned Integer — The level of the Form Coache.
      - MedicLevels : unsigned Integer — The level of the Medic.
      - SpokespersonLevels : unsigned Integer — The level of the Spokesperson.
      - SportPsychologistLevels : unsigned Integer — The level of the Sport Psychologist.
      - TacticalAssistantLevels : unsigned Integer — The level of the Tactical Assistant.
    - **YouthSquad** — Container for the data about the youth squad.
      - Investment : Money — The weekly youth squad invstment.
      - HasPromoted : Boolean — Boolean value indicating if the team has promoted a youth player this week.
      - YouthLevel : unsigned Integer — The current level of the youth squad.
