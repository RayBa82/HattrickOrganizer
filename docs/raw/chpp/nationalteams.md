# nationalteams

National teams

## Changelog
### Version 1.6 (2021-01-18)
- Removed Output: HattrickData → NationalTeams → Cup (Container)
- Removed Output: HattrickData → NationalTeams → Cup → CupTeams (Container)
- Removed Output: HattrickData → NationalTeams → Cup → CupTeams → CupTeam (Container)
- Removed Output: HattrickData → NationalTeams → Cup → CupTeams → CupTeam → CupNationalTeamID (Element)
- Removed Output: HattrickData → NationalTeams → Cup → CupTeams → CupTeam → CupNationalTeamName (Element)
- Added Output: HattrickData → NationalTeams → Cups (Container)
- Added Output: HattrickData → NationalTeams → Cups → Cup (Container)
- Added Output: HattrickData → NationalTeams → Cups → Cup → CupTeams (Container)
- Added Output: HattrickData → NationalTeams → Cups → Cup → CupTeams → CupTeam (Container)
- Added Output: HattrickData → NationalTeams → Cups → Cup → CupTeams → CupTeam → CupNationalTeamID (Element)
- Added Output: HattrickData → NationalTeams → Cups → Cup → CupTeams → CupTeam → CupNationalTeamName (Element)

### Version 1.5 (2013-03-04)
- Removed Output: HattrickData → UserIsSupporter (Element)
- Removed Output: HattrickData → UserHasSupporter (Element)
- Added Output: HattrickData → UserSupporterTier (Element)

### Version 1.4 (2011-02-12)
- Added Output: HattrickData → NationalTeams → NationalTeam → LeagueID (Element)

### Version 1.3 {default}
- First version

### Version 1.2

## Input
Only available inputs in this file version are shown.
required file = nationalteams
optional version = 
1.6
1.5
1.4
1.3
1.2
optional LeagueOfficeTypeID : unsigned Integer{Default:2}
What national team type to list (2 = National teams, 4 = U-20 Teams).

## Output
- **HattrickData** — Hattrick Data Container
  - UserSupporterTier : supporterTier — Indicates which Supporter package the fetching user has, or empty if not a Supporter.
  - LeagueOfficeTypeID : unsigned Integer — The requested LeagueOfficeTypeID.
  - **NationalTeams** — Container for the national teams.
    - **NationalTeam** — A national team.
      - NationalTeamID : unsigned Integer — The globally unique identifier of the national team.
      - NationalTeamName : String — The team name of the national team.
      - Dress : String — 7 character string representing the dress of the team: First characters is a letter indicating type of dress. Next two numbers indicate the shirt colour, next two indicate pants colour and final two numbers indicate socks colour.
      - RatingScore : unsigned Integer — A integer that indicates the current rating score of the national team.
      - LeagueID : unsigned Integer — The id for the league the team is representing.
    - **Cups** — Container for the different national teams cups
      - **Cup** — Container for the national teams still in the Cup.
        - **CupTeams** — Container for the teams in the Cup. If a match is running this value is not available
          - **CupTeam** — A national team still in the World Cup.
            - CupNationalTeamID : unsigned Integer — The globally unique identifier of the national team.
            - CupNationalTeamName : String — The team name of the national team.
