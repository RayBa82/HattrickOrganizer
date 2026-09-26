# economy

Economy

## Changelog
### Version 1.4 (2022-03-09)
- Added IncomeSponsorBonuses
- Added Output: HattrickData → Team → IncomeSponsorBonuses (Element)

### Version 1.3 (2013-05-02)
- Added Input: teamId

### Version 1.2 (2011-04-13)
- Removed Output: HattrickData → Team → IncomeTemporary (Element)
- Removed Output: HattrickData → Team → CostsTemporary (Element)
- Removed Output: HattrickData → Team → LastIncomeTemporary (Element)
- Removed Output: HattrickData → Team → LastCostsTemporary (Element)
- Added Output: HattrickData → Team → IncomeSoldPlayers (Element)
- Added Output: HattrickData → Team → IncomeSoldPlayersCommission (Element)
- Added Output: HattrickData → Team → CostsBoughtPlayers (Element)
- Added Output: HattrickData → Team → CostsArenaBuilding (Element)
- Added Output: HattrickData → Team → LastIncomeSoldPlayers (Element)
- Added Output: HattrickData → Team → LastIncomeSoldPlayersCommission (Element)
- Added Output: HattrickData → Team → LastCostsBoughtPlayers (Element)
- Added Output: HattrickData → Team → LastCostsArenaBuilding (Element)

### Version 1.1 {default}
- First version

## Input
Only available inputs in this file version are shown.
required file = economy
optional version = 
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
    - Cash : Money — The team's cash.
    - ExpectedCash : Money — The budgeted cash for next week for the team.
    - SponsorsPopularity : unsigned Integer — Attribute: 'Available' : Boolean If a match is running this value is not available.
    - SupportersPopularity : unsigned Integer — Attribute: 'Available' : Boolean If a match is running this value is not available.
    - FanClubSize : unsigned Integer — The size of the team's fanclub.
    - IncomeSpectators : Money — The budgeted income from spectators for the team this week.
    - IncomeSponsors : Money — The budgeted income from sponsors for the team this week.
    - IncomeSponsorBonuses : Money — The budgeted income for completing sponsor tasks.
    - IncomeFinancial : Money — The budgeted financial income for the team this week.
    - IncomeSoldPlayers : Money — The income from sold players of the team this week.
    - IncomeSoldPlayersCommission : Money — The income from sold players commission team this week.
    - IncomeSum : Money — The budgeted total income for the team this week.
    - CostsArena : Money — The budgeted costs of the arena for the team this week.
    - CostsPlayer : Money — The budgeted costs of the players for the team this week.
    - CostsFinancial : Money — The budgeted financial costs for the team this week.
    - CostsBoughtPlayers : Money — The costs from bought players to the team this week.
    - CostsArenaBuilding : Money — The costs from expanding your arena this week.
    - CostsStaff : Money — The budgeted costs of the staff for the team this week.
    - CostsYouth : Money — The budgeted costs of the youth squad for the team this week.
    - CostsSum : Money — The budgeted total cost for the team this week.
    - ExpectedWeeksTotal : Money — The budgeted economic result for the team this week.
    - LastIncomeSpectators : Money — The budgeted income from spectators for the team last week.
    - LastIncomeSponsors : Money — The budgeted income from sponsors for the team last week.
    - LastIncomeFinancial : Money — The budgeted financial income for the team last week.
    - LastIncomeSoldPlayers : Money — The income from sold players of the team last week.
    - LastIncomeSoldPlayersCommission : Money — The income from sold players commission team last week.
    - LastIncomeSum : Money — The budgeted total income for the team last week.
    - LastCostsArena : Money — The budgeted costs of the arena for the team last week.
    - LastCostsPlayer : Money — The budgeted costs of the players for the team last week.
    - LastCostsFinancial : Money — The budgeted financial costs for the team last week.
    - LastCostsBoughtPlayers : Money — The costs from bought players to the team last week.
    - LastCostsArenaBuilding : Money — The costs from expanding your arena last week.
    - LastCostsStaff : Money — The budgeted costs of the staff for the team last week.
    - LastCostsYouth : Money — The budgeted costs of the youth squad for the team last week.
    - LastCostsSum : Money — The budgeted total cost for the team last week.
    - LastWeeksTotal : Money — The budgeted economic result for the team last week.
