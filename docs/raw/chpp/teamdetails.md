# teamdetails

Team information

## Changelog
### Version 3.9 (2026-02-26)
- Added team LeagueSystemId.
- Added Output: HattrickData → Teams → Team → LeagueSystemID (Element)

### Version 3.8 (2026-02-10)
- Added team gender.
- Added Output: HattrickData → Teams → Team → GenderID (Element)

### Version 3.7 (2024-09-16)
- Added IsDeactivated for teams.
- Added Output: HattrickData → Teams → Team → IsDeactivated (Element)

### Version 3.6 (2022-03-03)
- Added PossibleToChallenge.
- Show StillInCup all the time, even during matches.
- Added cup information to trophies.
- Added LeagueLevelUnitId for trophies.
- Added Output: HattrickData → Teams → Team → TrophyList → Trophy → LeagueLevelUnitId (Element)
- Added Output: HattrickData → Teams → Team → TrophyList → Trophy → CupLeagueLevel (Element)
- Added Output: HattrickData → Teams → Team → TrophyList → Trophy → CupLevel (Element)
- Added Output: HattrickData → Teams → Team → TrophyList → Trophy → CupLevelIndex (Element)
- Added Output: HattrickData → Teams → Team → PossibleToChallengeMidweek (Element)
- Added Output: HattrickData → Teams → Team → PossibleToChallengeWeekend (Element)

### Version 3.5 (2020-07-02)
- Change the element name of NationalTeamCoach to NationalTeams. Add a new child element NationalTeamStaffType to indicate the staff position (coach, assistant coach, scouter). Fix typo on HasManagerLicense element.
- Removed Output: HattrickData → User → HasManagerLicese (Element)
- Removed Output: HattrickData → User → NationalTeamCoach (Container)
- Removed Output: HattrickData → User → NationalTeamCoach → NationalTeam (Container)
- Removed Output: HattrickData → User → NationalTeamCoach → NationalTeam → NationalTeamID (Element)
- Removed Output: HattrickData → User → NationalTeamCoach → NationalTeam → NationalTeamName (Element)
- Added Output: HattrickData → User → HasManagerLicense (Element)
- Added Output: HattrickData → User → NationalTeams (Container)
- Added Output: HattrickData → User → NationalTeams → NationalTeam (Container)
- Added Output: HattrickData → User → NationalTeams → NationalTeam → NationalTeamStaffType (Element)
- Added Output: HattrickData → User → NationalTeams → NationalTeam → NationalTeamID (Element)
- Added Output: HattrickData → User → NationalTeams → NationalTeam → NationalTeamName (Element)

### Version 3.4 (2016-12-19)
- Added Country container
- Added Output: HattrickData → Teams → Team → Country (Container)
- Added Output: HattrickData → Teams → Team → Country → CountryID (Element)
- Added Output: HattrickData → Teams → Team → Country → CountryName (Element)

### Version 3.3 (2015-08-04)
- Added Output: HattrickData → User → HasManagerLicese (Element)

### Version 3.2 (2014-07-21)
- Added more cup info. Added 'diamond' to output of HattrickData → User → SupporterTier.
- Removed Output: HattrickData → User → SupporterTier (Element)
- Added Output: HattrickData → User → SupporterTier (Element)
- Added Output: HattrickData → Teams → Team → Cup → CupLeagueLevel (Element)
- Added Output: HattrickData → Teams → Team → Cup → CupLevel (Element)
- Added Output: HattrickData → Teams → Team → Cup → CupLevelIndex (Element)
- Added Output: HattrickData → Teams → Team → Cup → MatchRound (Element)
- Added Output: HattrickData → Teams → Team → Cup → MatchRoundsLeft (Element)

### Version 3.1 (2013-12-16)
- Added new attribute TotalItems and MaxItems to SupportedTeams and MySupporters. All versions has been limited these lists ouput to 50 items (this is subject to change without prior notice, hence why MaxItems is also provided)! Use new file supporters.xml for more.

### Version 3.0 (2013-09-03)
- Show LogoUrl for all users as it's not longer a supporter feature.
- Removed Output: HattrickData → Teams → Team → LogoURL (Element)
- Added Output: HattrickData → Teams → Team → LogoURL (Element)

### Version 2.9 (2013-05-20)
- Added Output: HattrickData → Teams → Team → FoundedDate (Element)
- Added Output: HattrickData → Teams → Team → TeamColors (Container)
- Added Output: HattrickData → Teams → Team → TeamColors → BackgroundColor (Element)
- Added Output: HattrickData → Teams → Team → TeamColors → Color (Element)

### Version 2.8 (2013-05-02)
- Added container Teams around the team(s) managed by the requested user.
- Removed Output: HattrickData → Team (Container)
- Removed Output: HattrickData → Team → TeamID (Element)
- Removed Output: HattrickData → Team → TeamName (Element)
- Removed Output: HattrickData → Team → ShortTeamName (Element)
- Removed Output: HattrickData → Team → Arena (Container)
- Removed Output: HattrickData → Team → Arena → ArenaID (Element)
- Removed Output: HattrickData → Team → Arena → ArenaName (Element)
- Removed Output: HattrickData → Team → League (Container)
- Removed Output: HattrickData → Team → League → LeagueID (Element)
- Removed Output: HattrickData → Team → League → LeagueName (Element)
- Removed Output: HattrickData → Team → Region (Container)
- Removed Output: HattrickData → Team → Region → RegionID (Element)
- Removed Output: HattrickData → Team → Region → RegionName (Element)
- Removed Output: HattrickData → Team → Trainer (Container)
- Removed Output: HattrickData → Team → Trainer → PlayerID (Element)
- Removed Output: HattrickData → Team → HomePage (Element)
- Removed Output: HattrickData → Team → Cup (Container)
- Removed Output: HattrickData → Team → Cup → StillInCup (Element)
- Removed Output: HattrickData → Team → Cup → CupID (Element)
- Removed Output: HattrickData → Team → Cup → CupName (Element)
- Removed Output: HattrickData → Team → FriendlyTeamID (Element)
- Removed Output: HattrickData → Team → LeagueLevelUnit (Container)
- Removed Output: HattrickData → Team → LeagueLevelUnit → LeagueLevelUnitID (Element)
- Removed Output: HattrickData → Team → LeagueLevelUnit → LeagueLevelUnitName (Element)
- Removed Output: HattrickData → Team → LeagueLevelUnit → LeagueLevel (Element)
- Removed Output: HattrickData → Team → NumberOfVictories (Element)
- Removed Output: HattrickData → Team → NumberOfUndefeated (Element)
- Removed Output: HattrickData → Team → Fanclub (Container)
- Removed Output: HattrickData → Team → Fanclub → FanclubID (Element)
- Removed Output: HattrickData → Team → Fanclub → FanclubName (Element)
- Removed Output: HattrickData → Team → Fanclub → FanclubSize (Element)
- Removed Output: HattrickData → Team → LogoURL (Element)
- Removed Output: HattrickData → Team → Guestbook (Container)
- Removed Output: HattrickData → Team → Guestbook → NumberOfGuestbookItems (Element)
- Removed Output: HattrickData → Team → PressAnnouncement (Container)
- Removed Output: HattrickData → Team → PressAnnouncement → Subject (Element)
- Removed Output: HattrickData → Team → PressAnnouncement → Body (Element)
- Removed Output: HattrickData → Team → PressAnnouncement → SendDate (Element)
- Removed Output: HattrickData → Team → DressURI (Element)
- Removed Output: HattrickData → Team → DressAlternateURI (Element)
- Removed Output: HattrickData → Team → BotStatus (Container)
- Removed Output: HattrickData → Team → BotStatus → IsBot (Element)
- Removed Output: HattrickData → Team → BotStatus → BotSince (Element)
- Removed Output: HattrickData → Team → TeamRank (Element)
- Removed Output: HattrickData → Team → YouthTeamID (Element)
- Removed Output: HattrickData → Team → YouthTeamName (Element)
- Removed Output: HattrickData → Team → Flags (Container)
- Removed Output: HattrickData → Team → Flags → HomeFlags (Container)
- Removed Output: HattrickData → Team → Flags → HomeFlags → Flag (Container)
- Removed Output: HattrickData → Team → Flags → HomeFlags → Flag → LeagueID (Element)
- Removed Output: HattrickData → Team → Flags → HomeFlags → Flag → LeagueName (Element)
- Removed Output: HattrickData → Team → Flags → HomeFlags → Flag → CountryCode (Element)
- Removed Output: HattrickData → Team → Flags → AwayFlags (Container)
- Removed Output: HattrickData → Team → Flags → AwayFlags → Flag (Container)
- Removed Output: HattrickData → Team → Flags → AwayFlags → Flag → LeagueID (Element)
- Removed Output: HattrickData → Team → Flags → AwayFlags → Flag → LeagueName (Element)
- Removed Output: HattrickData → Team → Flags → AwayFlags → Flag → CountryCode (Element)
- Removed Output: HattrickData → Team → TrophyList (Container)
- Removed Output: HattrickData → Team → TrophyList → Trophy (Container)
- Removed Output: HattrickData → Team → TrophyList → Trophy → TrophyTypeId (Element)
- Removed Output: HattrickData → Team → TrophyList → Trophy → TrophySeason (Element)
- Removed Output: HattrickData → Team → TrophyList → Trophy → LeagueLevel (Element)
- Removed Output: HattrickData → Team → TrophyList → Trophy → LeagueLevelUnitName (Element)
- Removed Output: HattrickData → Team → TrophyList → Trophy → GainedDate (Element)
- Removed Output: HattrickData → Team → TrophyList → Trophy → ImageUrl (Element)
- Removed Output: HattrickData → Team → SupportedTeams (Container)
- Removed Output: HattrickData → Team → SupportedTeams → SupportedTeam (Container)
- Removed Output: HattrickData → Team → SupportedTeams → SupportedTeam → UserId (Element)
- Removed Output: HattrickData → Team → SupportedTeams → SupportedTeam → LoginName (Element)
- Removed Output: HattrickData → Team → SupportedTeams → SupportedTeam → TeamId (Element)
- Removed Output: HattrickData → Team → SupportedTeams → SupportedTeam → TeamName (Element)
- Removed Output: HattrickData → Team → SupportedTeams → SupportedTeam → LeagueID (Element)
- Removed Output: HattrickData → Team → SupportedTeams → SupportedTeam → LeagueName (Element)
- Removed Output: HattrickData → Team → SupportedTeams → SupportedTeam → LeagueLevelUnitID (Element)
- Removed Output: HattrickData → Team → SupportedTeams → SupportedTeam → LeagueLevelUnitName (Element)
- Removed Output: HattrickData → Team → SupportedTeams → SupportedTeam → LastMatch (Container)
- Removed Output: HattrickData → Team → SupportedTeams → SupportedTeam → LastMatch → LastMatchId (Element)
- Removed Output: HattrickData → Team → SupportedTeams → SupportedTeam → LastMatch → LastMatchDate (Element)
- Removed Output: HattrickData → Team → SupportedTeams → SupportedTeam → LastMatch → LastMatchHomeTeamId (Element)
- Removed Output: HattrickData → Team → SupportedTeams → SupportedTeam → LastMatch → LastMatchHomeTeamName (Element)
- Removed Output: HattrickData → Team → SupportedTeams → SupportedTeam → LastMatch → LastMatchHomeGoals (Element)
- Removed Output: HattrickData → Team → SupportedTeams → SupportedTeam → LastMatch → LastMatchAwayTeamId (Element)
- Removed Output: HattrickData → Team → SupportedTeams → SupportedTeam → LastMatch → LastMatchAwayTeamName (Element)
- Removed Output: HattrickData → Team → SupportedTeams → SupportedTeam → LastMatch → LastMatchAwayGoals (Element)
- Removed Output: HattrickData → Team → SupportedTeams → SupportedTeam → NextMatch (Container)
- Removed Output: HattrickData → Team → SupportedTeams → SupportedTeam → NextMatch → NextMatchId (Element)
- Removed Output: HattrickData → Team → SupportedTeams → SupportedTeam → NextMatch → NextMatchDate (Element)
- Removed Output: HattrickData → Team → SupportedTeams → SupportedTeam → NextMatch → NextMatchHomeTeamId (Element)
- Removed Output: HattrickData → Team → SupportedTeams → SupportedTeam → NextMatch → NextMatchHomeTeamName (Element)
- Removed Output: HattrickData → Team → SupportedTeams → SupportedTeam → NextMatch → NextMatchAwayTeamId (Element)
- Removed Output: HattrickData → Team → SupportedTeams → SupportedTeam → NextMatch → NextMatchAwayTeamName (Element)
- Removed Output: HattrickData → Team → SupportedTeams → SupportedTeam → PressAnnouncement (Container)
- Removed Output: HattrickData → Team → SupportedTeams → SupportedTeam → PressAnnouncement → PressAnnouncementSendDate (Element)
- Removed Output: HattrickData → Team → SupportedTeams → SupportedTeam → PressAnnouncement → PressAnnouncementSubject (Element)
- Removed Output: HattrickData → Team → SupportedTeams → SupportedTeam → PressAnnouncement → PressAnnouncementBody (Element)
- Removed Output: HattrickData → Team → MySupporters (Container)
- Removed Output: HattrickData → Team → MySupporters → SupporterTeam (Container)
- Removed Output: HattrickData → Team → MySupporters → SupporterTeam → UserId (Element)
- Removed Output: HattrickData → Team → MySupporters → SupporterTeam → LoginName (Element)
- Removed Output: HattrickData → Team → MySupporters → SupporterTeam → TeamId (Element)
- Removed Output: HattrickData → Team → MySupporters → SupporterTeam → TeamName (Element)
- Removed Output: HattrickData → Team → MySupporters → SupporterTeam → LeagueID (Element)
- Removed Output: HattrickData → Team → MySupporters → SupporterTeam → LeagueName (Element)
- Removed Output: HattrickData → Team → MySupporters → SupporterTeam → LeagueLevelUnitID (Element)
- Removed Output: HattrickData → Team → MySupporters → SupporterTeam → LeagueLevelUnitName (Element)
- Added Output: HattrickData → Teams (Container)
- Added Output: HattrickData → Teams → Team (Container)
- Added Output: HattrickData → Teams → Team → TeamID (Element)
- Added Output: HattrickData → Teams → Team → TeamName (Element)
- Added Output: HattrickData → Teams → Team → ShortTeamName (Element)
- Added Output: HattrickData → Teams → Team → IsPrimaryClub (Element)
- Added Output: HattrickData → Teams → Team → Arena (Container)
- Added Output: HattrickData → Teams → Team → Arena → ArenaID (Element)
- Added Output: HattrickData → Teams → Team → Arena → ArenaName (Element)
- Added Output: HattrickData → Teams → Team → League (Container)
- Added Output: HattrickData → Teams → Team → League → LeagueID (Element)
- Added Output: HattrickData → Teams → Team → League → LeagueName (Element)
- Added Output: HattrickData → Teams → Team → Region (Container)
- Added Output: HattrickData → Teams → Team → Region → RegionID (Element)
- Added Output: HattrickData → Teams → Team → Region → RegionName (Element)
- Added Output: HattrickData → Teams → Team → Trainer (Container)
- Added Output: HattrickData → Teams → Team → Trainer → PlayerID (Element)
- Added Output: HattrickData → Teams → Team → HomePage (Element)
- Added Output: HattrickData → Teams → Team → Cup (Container)
- Added Output: HattrickData → Teams → Team → Cup → StillInCup (Element)
- Added Output: HattrickData → Teams → Team → Cup → CupID (Element)
- Added Output: HattrickData → Teams → Team → Cup → CupName (Element)
- Added Output: HattrickData → Teams → Team → PowerRating (Container)
- Added Output: HattrickData → Teams → Team → PowerRating → GlobalRanking (Element)
- Added Output: HattrickData → Teams → Team → PowerRating → LeagueRanking (Element)
- Added Output: HattrickData → Teams → Team → PowerRating → RegionRanking (Element)
- Added Output: HattrickData → Teams → Team → PowerRating → PowerRating (Element)
- Added Output: HattrickData → Teams → Team → FriendlyTeamID (Element)
- Added Output: HattrickData → Teams → Team → LeagueLevelUnit (Container)
- Added Output: HattrickData → Teams → Team → LeagueLevelUnit → LeagueLevelUnitID (Element)
- Added Output: HattrickData → Teams → Team → LeagueLevelUnit → LeagueLevelUnitName (Element)
- Added Output: HattrickData → Teams → Team → LeagueLevelUnit → LeagueLevel (Element)
- Added Output: HattrickData → Teams → Team → NumberOfVictories (Element)
- Added Output: HattrickData → Teams → Team → NumberOfUndefeated (Element)
- Added Output: HattrickData → Teams → Team → Fanclub (Container)
- Added Output: HattrickData → Teams → Team → Fanclub → FanclubID (Element)
- Added Output: HattrickData → Teams → Team → Fanclub → FanclubName (Element)
- Added Output: HattrickData → Teams → Team → Fanclub → FanclubSize (Element)
- Added Output: HattrickData → Teams → Team → LogoURL (Element)
- Added Output: HattrickData → Teams → Team → Guestbook (Container)
- Added Output: HattrickData → Teams → Team → Guestbook → NumberOfGuestbookItems (Element)
- Added Output: HattrickData → Teams → Team → PressAnnouncement (Container)
- Added Output: HattrickData → Teams → Team → PressAnnouncement → Subject (Element)
- Added Output: HattrickData → Teams → Team → PressAnnouncement → Body (Element)
- Added Output: HattrickData → Teams → Team → PressAnnouncement → SendDate (Element)
- Added Output: HattrickData → Teams → Team → DressURI (Element)
- Added Output: HattrickData → Teams → Team → DressAlternateURI (Element)
- Added Output: HattrickData → Teams → Team → BotStatus (Container)
- Added Output: HattrickData → Teams → Team → BotStatus → IsBot (Element)
- Added Output: HattrickData → Teams → Team → BotStatus → BotSince (Element)
- Added Output: HattrickData → Teams → Team → TeamRank (Element)
- Added Output: HattrickData → Teams → Team → YouthTeamID (Element)
- Added Output: HattrickData → Teams → Team → YouthTeamName (Element)
- Added Output: HattrickData → Teams → Team → NumberOfVisits (Element)
- Added Output: HattrickData → Teams → Team → Flags (Container)
- Added Output: HattrickData → Teams → Team → Flags → HomeFlags (Container)
- Added Output: HattrickData → Teams → Team → Flags → HomeFlags → Flag (Container)
- Added Output: HattrickData → Teams → Team → Flags → HomeFlags → Flag → LeagueID (Element)
- Added Output: HattrickData → Teams → Team → Flags → HomeFlags → Flag → LeagueName (Element)
- Added Output: HattrickData → Teams → Team → Flags → HomeFlags → Flag → CountryCode (Element)
- Added Output: HattrickData → Teams → Team → Flags → AwayFlags (Container)
- Added Output: HattrickData → Teams → Team → Flags → AwayFlags → Flag (Container)
- Added Output: HattrickData → Teams → Team → Flags → AwayFlags → Flag → LeagueID (Element)
- Added Output: HattrickData → Teams → Team → Flags → AwayFlags → Flag → LeagueName (Element)
- Added Output: HattrickData → Teams → Team → Flags → AwayFlags → Flag → CountryCode (Element)
- Added Output: HattrickData → Teams → Team → TrophyList (Container)
- Added Output: HattrickData → Teams → Team → TrophyList → Trophy (Container)
- Added Output: HattrickData → Teams → Team → TrophyList → Trophy → TrophyTypeId (Element)
- Added Output: HattrickData → Teams → Team → TrophyList → Trophy → TrophySeason (Element)
- Added Output: HattrickData → Teams → Team → TrophyList → Trophy → LeagueLevel (Element)
- Added Output: HattrickData → Teams → Team → TrophyList → Trophy → LeagueLevelUnitName (Element)
- Added Output: HattrickData → Teams → Team → TrophyList → Trophy → GainedDate (Element)
- Added Output: HattrickData → Teams → Team → TrophyList → Trophy → ImageUrl (Element)
- Added Output: HattrickData → Teams → Team → SupportedTeams (Container)
- Added Output: HattrickData → Teams → Team → SupportedTeams → SupportedTeam (Container)
- Added Output: HattrickData → Teams → Team → SupportedTeams → SupportedTeam → UserId (Element)
- Added Output: HattrickData → Teams → Team → SupportedTeams → SupportedTeam → LoginName (Element)
- Added Output: HattrickData → Teams → Team → SupportedTeams → SupportedTeam → TeamId (Element)
- Added Output: HattrickData → Teams → Team → SupportedTeams → SupportedTeam → TeamName (Element)
- Added Output: HattrickData → Teams → Team → SupportedTeams → SupportedTeam → LeagueID (Element)
- Added Output: HattrickData → Teams → Team → SupportedTeams → SupportedTeam → LeagueName (Element)
- Added Output: HattrickData → Teams → Team → SupportedTeams → SupportedTeam → LeagueLevelUnitID (Element)
- Added Output: HattrickData → Teams → Team → SupportedTeams → SupportedTeam → LeagueLevelUnitName (Element)
- Added Output: HattrickData → Teams → Team → SupportedTeams → SupportedTeam → LastMatch (Container)
- Added Output: HattrickData → Teams → Team → SupportedTeams → SupportedTeam → LastMatch → LastMatchId (Element)
- Added Output: HattrickData → Teams → Team → SupportedTeams → SupportedTeam → LastMatch → LastMatchDate (Element)
- Added Output: HattrickData → Teams → Team → SupportedTeams → SupportedTeam → LastMatch → LastMatchHomeTeamId (Element)
- Added Output: HattrickData → Teams → Team → SupportedTeams → SupportedTeam → LastMatch → LastMatchHomeTeamName (Element)
- Added Output: HattrickData → Teams → Team → SupportedTeams → SupportedTeam → LastMatch → LastMatchHomeGoals (Element)
- Added Output: HattrickData → Teams → Team → SupportedTeams → SupportedTeam → LastMatch → LastMatchAwayTeamId (Element)
- Added Output: HattrickData → Teams → Team → SupportedTeams → SupportedTeam → LastMatch → LastMatchAwayTeamName (Element)
- Added Output: HattrickData → Teams → Team → SupportedTeams → SupportedTeam → LastMatch → LastMatchAwayGoals (Element)
- Added Output: HattrickData → Teams → Team → SupportedTeams → SupportedTeam → NextMatch (Container)
- Added Output: HattrickData → Teams → Team → SupportedTeams → SupportedTeam → NextMatch → NextMatchId (Element)
- Added Output: HattrickData → Teams → Team → SupportedTeams → SupportedTeam → NextMatch → NextMatchDate (Element)
- Added Output: HattrickData → Teams → Team → SupportedTeams → SupportedTeam → NextMatch → NextMatchHomeTeamId (Element)
- Added Output: HattrickData → Teams → Team → SupportedTeams → SupportedTeam → NextMatch → NextMatchHomeTeamName (Element)
- Added Output: HattrickData → Teams → Team → SupportedTeams → SupportedTeam → NextMatch → NextMatchAwayTeamId (Element)
- Added Output: HattrickData → Teams → Team → SupportedTeams → SupportedTeam → NextMatch → NextMatchAwayTeamName (Element)
- Added Output: HattrickData → Teams → Team → SupportedTeams → SupportedTeam → PressAnnouncement (Container)
- Added Output: HattrickData → Teams → Team → SupportedTeams → SupportedTeam → PressAnnouncement → PressAnnouncementSendDate (Element)
- Added Output: HattrickData → Teams → Team → SupportedTeams → SupportedTeam → PressAnnouncement → PressAnnouncementSubject (Element)
- Added Output: HattrickData → Teams → Team → SupportedTeams → SupportedTeam → PressAnnouncement → PressAnnouncementBody (Element)
- Added Output: HattrickData → Teams → Team → MySupporters (Container)
- Added Output: HattrickData → Teams → Team → MySupporters → SupporterTeam (Container)
- Added Output: HattrickData → Teams → Team → MySupporters → SupporterTeam → UserId (Element)
- Added Output: HattrickData → Teams → Team → MySupporters → SupporterTeam → LoginName (Element)
- Added Output: HattrickData → Teams → Team → MySupporters → SupporterTeam → TeamId (Element)
- Added Output: HattrickData → Teams → Team → MySupporters → SupporterTeam → TeamName (Element)
- Added Output: HattrickData → Teams → Team → MySupporters → SupporterTeam → LeagueID (Element)
- Added Output: HattrickData → Teams → Team → MySupporters → SupporterTeam → LeagueName (Element)
- Added Output: HattrickData → Teams → Team → MySupporters → SupporterTeam → LeagueLevelUnitID (Element)
- Added Output: HattrickData → Teams → Team → MySupporters → SupporterTeam → LeagueLevelUnitName (Element)

### Version 2.7 (2013-03-04)
- Removed Output: HattrickData → User → HasSupporter (Element)
- Added Output: HattrickData → User → SupporterTier (Element)

### Version 2.6 (2011-04-13)
- Added Input: includeFlags
- Added Input: includeSupporters
- Added Output: HattrickData → Team → TrophyList (Container)
- Added Output: HattrickData → Team → TrophyList → Trophy (Container)
- Added Output: HattrickData → Team → TrophyList → Trophy → TrophyTypeId (Element)
- Added Output: HattrickData → Team → TrophyList → Trophy → TrophySeason (Element)
- Added Output: HattrickData → Team → TrophyList → Trophy → LeagueLevel (Element)
- Added Output: HattrickData → Team → TrophyList → Trophy → LeagueLevelUnitName (Element)
- Added Output: HattrickData → Team → TrophyList → Trophy → GainedDate (Element)
- Added Output: HattrickData → Team → TrophyList → Trophy → ImageUrl (Element)
- Added Output: HattrickData → Team → SupportedTeams (Container)
- Added Output: HattrickData → Team → SupportedTeams → SupportedTeam (Container)
- Added Output: HattrickData → Team → SupportedTeams → SupportedTeam → UserId (Element)
- Added Output: HattrickData → Team → SupportedTeams → SupportedTeam → LoginName (Element)
- Added Output: HattrickData → Team → SupportedTeams → SupportedTeam → TeamId (Element)
- Added Output: HattrickData → Team → SupportedTeams → SupportedTeam → TeamName (Element)
- Added Output: HattrickData → Team → SupportedTeams → SupportedTeam → LeagueID (Element)
- Added Output: HattrickData → Team → SupportedTeams → SupportedTeam → LeagueName (Element)
- Added Output: HattrickData → Team → SupportedTeams → SupportedTeam → LeagueLevelUnitID (Element)
- Added Output: HattrickData → Team → SupportedTeams → SupportedTeam → LeagueLevelUnitName (Element)
- Added Output: HattrickData → Team → SupportedTeams → SupportedTeam → LastMatch (Container)
- Added Output: HattrickData → Team → SupportedTeams → SupportedTeam → LastMatch → LastMatchId (Element)
- Added Output: HattrickData → Team → SupportedTeams → SupportedTeam → LastMatch → LastMatchDate (Element)
- Added Output: HattrickData → Team → SupportedTeams → SupportedTeam → LastMatch → LastMatchHomeTeamId (Element)
- Added Output: HattrickData → Team → SupportedTeams → SupportedTeam → LastMatch → LastMatchHomeTeamName (Element)
- Added Output: HattrickData → Team → SupportedTeams → SupportedTeam → LastMatch → LastMatchHomeGoals (Element)
- Added Output: HattrickData → Team → SupportedTeams → SupportedTeam → LastMatch → LastMatchAwayTeamId (Element)
- Added Output: HattrickData → Team → SupportedTeams → SupportedTeam → LastMatch → LastMatchAwayTeamName (Element)
- Added Output: HattrickData → Team → SupportedTeams → SupportedTeam → LastMatch → LastMatchAwayGoals (Element)
- Added Output: HattrickData → Team → SupportedTeams → SupportedTeam → NextMatch (Container)
- Added Output: HattrickData → Team → SupportedTeams → SupportedTeam → NextMatch → NextMatchId (Element)
- Added Output: HattrickData → Team → SupportedTeams → SupportedTeam → NextMatch → NextMatchDate (Element)
- Added Output: HattrickData → Team → SupportedTeams → SupportedTeam → NextMatch → NextMatchHomeTeamId (Element)
- Added Output: HattrickData → Team → SupportedTeams → SupportedTeam → NextMatch → NextMatchHomeTeamName (Element)
- Added Output: HattrickData → Team → SupportedTeams → SupportedTeam → NextMatch → NextMatchAwayTeamId (Element)
- Added Output: HattrickData → Team → SupportedTeams → SupportedTeam → NextMatch → NextMatchAwayTeamName (Element)
- Added Output: HattrickData → Team → SupportedTeams → SupportedTeam → PressAnnouncement (Container)
- Added Output: HattrickData → Team → SupportedTeams → SupportedTeam → PressAnnouncement → PressAnnouncementSendDate (Element)
- Added Output: HattrickData → Team → SupportedTeams → SupportedTeam → PressAnnouncement → PressAnnouncementSubject (Element)
- Added Output: HattrickData → Team → SupportedTeams → SupportedTeam → PressAnnouncement → PressAnnouncementBody (Element)
- Added Output: HattrickData → Team → MySupporters (Container)
- Added Output: HattrickData → Team → MySupporters → SupporterTeam (Container)
- Added Output: HattrickData → Team → MySupporters → SupporterTeam → UserId (Element)
- Added Output: HattrickData → Team → MySupporters → SupporterTeam → LoginName (Element)
- Added Output: HattrickData → Team → MySupporters → SupporterTeam → TeamId (Element)
- Added Output: HattrickData → Team → MySupporters → SupporterTeam → TeamName (Element)
- Added Output: HattrickData → Team → MySupporters → SupporterTeam → LeagueID (Element)
- Added Output: HattrickData → Team → MySupporters → SupporterTeam → LeagueName (Element)
- Added Output: HattrickData → Team → MySupporters → SupporterTeam → LeagueLevelUnitID (Element)
- Added Output: HattrickData → Team → MySupporters → SupporterTeam → LeagueLevelUnitName (Element)

### Version 2.5 (2011-02-03)
- Removed Output: HattrickData → Team → Fanclub (Container)
- Removed Output: HattrickData → Team → Fanclub → FanclubID (Element)
- Removed Output: HattrickData → Team → Fanclub → FanclubName (Element)
- Added Output: HattrickData → Team → Fanclub (Container)
- Added Output: HattrickData → Team → Fanclub → FanclubID (Element)
- Added Output: HattrickData → Team → Fanclub → FanclubName (Element)
- Added Output: HattrickData → Team → Fanclub → FanclubSize (Element)

### Version 2.4 (2010-11-08)
- Added Input: includeDomesticFlags
- Removed Output: HattrickData → Team → Dress (Element)
- Removed Output: HattrickData → Team → DressAlternate (Element)
- Added Output: HattrickData → Team → DressURI (Element)
- Added Output: HattrickData → Team → DressAlternateURI (Element)
- Added Output: HattrickData → Team → YouthTeamName (Element)

### Version 2.3
- Removed Output: HattrickData → Team → Flags → HomeFlags → CountryCode (Element)
- Removed Output: HattrickData → Team → Flags → AwayFlags → CountryCode (Element)
- Added Output: HattrickData → Team → Flags → HomeFlags → Flag (Container)
- Added Output: HattrickData → Team → Flags → HomeFlags → Flag → LeagueID (Element)
- Added Output: HattrickData → Team → Flags → HomeFlags → Flag → LeagueName (Element)
- Added Output: HattrickData → Team → Flags → HomeFlags → Flag → CountryCode (Element)
- Added Output: HattrickData → Team → Flags → AwayFlags → Flag (Container)
- Added Output: HattrickData → Team → Flags → AwayFlags → Flag → LeagueID (Element)
- Added Output: HattrickData → Team → Flags → AwayFlags → Flag → LeagueName (Element)
- Added Output: HattrickData → Team → Flags → AwayFlags → Flag → CountryCode (Element)

### Version 2.2
- Added Output: HattrickData → Team → Flags (Container)
- Added Output: HattrickData → Team → Flags → HomeFlags (Container)
- Added Output: HattrickData → Team → Flags → HomeFlags → CountryCode (Element)
- Added Output: HattrickData → Team → Flags → AwayFlags (Container)
- Added Output: HattrickData → Team → Flags → AwayFlags → CountryCode (Element)

### Version 2.1

### Version 2.0
- Removed Output: HattrickData → Team → Clubhouse (Element)
- Removed Output: HattrickData → Team → Cup (Container)
- Removed Output: HattrickData → Team → Cup → StillInCup (Element)
- Removed Output: HattrickData → Team → Cup → CupID (Element)
- Removed Output: HattrickData → Team → Cup → CupName (Element)
- Added Output: HattrickData → Team → Cup (Container)
- Added Output: HattrickData → Team → Cup → StillInCup (Element)
- Added Output: HattrickData → Team → Cup → CupID (Element)
- Added Output: HattrickData → Team → Cup → CupName (Element)

### Version 1.9 {default}
- First version

## Input
Only available inputs in this file version are shown.
required file = teamdetails
optional version = 
3.9
3.8
3.7
3.6
3.5
3.4
3.3
3.2
3.1
3.0
2.9
2.8
2.7
2.6
2.5
2.4
2.3
2.2
2.1
2.0
1.9
required_1 teamID : unsigned Integer{Default:Your senior teamID}
What team/user to show the data for. teamID and userID generates the same result, except that ownerless teams can only be accessed if submitting a teamID and that users without a team can only be accessed if userID is submitted.
required_1 userID : unsigned Integer{Default:Your userID, if teamID is also not set}
What team/user to show the data for. teamID and userID generates the same result, except that ownerless teams can only be accessed if submitting a teamID and that users without a team can only be accessed if userID is submitted. If neither userID or teamID is supplied, userID defaults to the logged on user's userID.
optional includeDomesticFlags : Boolean{Default:false}
Whether or not the teams own country should be included in the flag collections (provided games against domestic opponents have been played). If not specified then domestic flags won't be included.
optional includeFlags : Boolean{Default:false}
Whether or not the flag collection should be provided. If not specified then flags won't be included.
optional includeSupporters : Boolean{Default:false}
Whether or not the supported teams and the teams supporters should be included. If not specified they won't be included.
required_1: teamID or userID is required!

## Output
- **HattrickData** — Hattrick Data Container
  - **User** — Container for the data about the user ('owner').
    - UserID : unsigned Integer — The globally unique UserID for the owner of the team. Teams without an owner have UserID = 0.
    - **Language** — Container for the data about the language the user has chosen as his/her default language.
      - LanguageID : unsigned Integer — The globally unique LanguageID.
      - LanguageName : String — The language name
    - SupporterTier : supporterTier — The current level of Hattrick Supporter that the user has (including Diamond). Empty if not a Supporter.
    - Loginname : String — The 'username' or 'nickname' used in Forums and around the site. Not supplied if the team is ownerless.
    - Name : String — The user's personal name or the value 'HIDDEN' to indicate that the user has chosen to hide it. Not supplied if the team is ownerless.
    - ICQ : unsigned Integer — The user's ICQ number (if any). Not supplied if the team is ownerless.
    - SignupDate : DateTime — The date and time when the user signed up for Hattrick. Not supplied if the team is ownerless.
    - ActivationDate : DateTime — The date and time when the user gained control of this team. Not supplied if the team is ownerless.
    - LastLoginDate : DateTime — The date and time when the user last logged on. Not supplied if the team is ownerless.
    - HasManagerLicense : Boolean — Whether or not the user has received the manager license
    - **NationalTeams** — Container for the national teams.
      - **NationalTeam** — Container for a national team.
        - NationalTeamStaffType : NationalTeamStaffType — The staff type id from the position the user has in the national team
        - NationalTeamID : unsigned Integer — The globally unique identifier of the national team.
        - NationalTeamName : String — The name of the national team.
  - **Teams** — Container for the team(s) that are owned by the specified user.
    - **Team** — Container for the data about the team (not supplied for user without team).
      - TeamID : unsigned Integer — The globally unique TeamID.
      - TeamName : String — The full team name.
      - ShortTeamName : String — The short team name.
      - IsPrimaryClub : Boolean — True/false indicating if this team is the user's primary team.
      - FoundedDate : DateTime — Date when club was founded.
      - IsDeactivated : Boolean — Whether or not the team is deactivated and not part of the league system
      - GenderID : GenderID — The gender of the team.
      - LeagueSystemID : LeagueSystemID — The system type of the league for this team.
      - **Arena** — Container for the data about the team's home ground.
        - ArenaID : unsigned Integer — The globally unique ArenaID.
        - ArenaName : String — The arena name.
      - **League** — Container for the data about the league ('Sverige', 'England' and so on) the team is part of.
        - LeagueID : unsigned Integer — The globally unique LeagueID.
        - LeagueName : String — The League name.
      - **Country** — Container for the data about the country ('Sverige', 'England' and so on) the team is part of.
        - CountryID : unsigned Integer — The globally unique CountryID.
        - CountryName : String — The Country name.
      - **Region** — Container for the data about the region theteam is located in.
        - RegionID : unsigned Integer — The globally unique RegionID.
        - RegionName : String — The Region name.
      - **Trainer** — The player that is trainer ('coach') for the team. (Not to be confused with the 'owner'/'user').
        - PlayerID : unsigned Integer — The globally unique PlayerID of the player that has the job as trainer of the team.
      - HomePage : URI — The home page URL that the team has specified.
      - **Cup** — Data about the team's cup status. If the team is playing a match the container is empty.
        - StillInCup : Boolean — Boolean value stating if the team is still in the cup.
        - CupID : unsigned Integer — The globally unique CupID of the cup that the team is still in. Only provided if team is still in cup.
        - CupName : String — The name of the cup that the team is still in. Only provided if team is still in cup.
        - CupLeagueLevel : unsigned Integer — LeagueLevel for the cup. 0 = National (LeagueLevel 1-6), 7-9 = Divisional. Only provided if team is still in cup.
        - CupLevel : unsigned Integer — Level of the cup. 1 = National/Divisional, 2 = Challenger, 3 = Consolation. Only provided if team is still in cup.
        - CupLevelIndex : unsigned Integer — Index of the cup. Always 1 for National and Consolation cups, for Challenger cup: 1 = Emerald, 2 = Ruby, 3 = Sapphire. Only provided if team is still in cup.
        - MatchRound : unsigned Integer — Next round (or current if matches are ongoing). (Note that this is different from WorldDetails!). Only provided if team is still in cup.
        - MatchRoundsLeft : unsigned Integer — How many rounds are left of this cup. Only provided if team is still in cup.
      - **PowerRating** — Data about the team's Power Rating
        - GlobalRanking : unsigned Integer — The Power Rating team ranking in Global.
        - LeagueRanking : unsigned Integer — The Power Rating team ranking in the same league.
        - RegionRanking : unsigned Integer — The Power Rating team ranking in the same region.
        - PowerRating : unsigned Integer — The Power Rating value of the team.
      - FriendlyTeamID : unsigned Integer — The team's status about if a friendly is booked. The value is the opponents globally unique teamID if the team has booked a friendly, 0 if not. If the team is playing a match the tag will be empty.
      - **LeagueLevelUnit** — Data about the team's LeagueLevelUnit ('series'). If the league is playing qualification matches the tag will be empty.
        - LeagueLevelUnitID : unsigned Integer — The globally unique LeagueLevelUnitID.
        - LeagueLevelUnitName : String — The name of the LeagueLevelUnit.
        - LeagueLevel : unsigned Integer — Integer value stating the relative level of the LeagueLevelUnit, with 1 indicating the league's top series.
      - NumberOfVictories : unsigned Integer — If the team is in a winning streak of at least 2 matches, this integer indicates how many victories in a row it currently has. If the team is playing a match the tag will be empty.
      - NumberOfUndefeated : unsigned Integer — If the team is in a streak of at least 2 matches as undefeated, this integer indicates how many matches in a row it is undefeated. If the team is playing a match the tag will be empty.
      - **Fanclub** — Container for the data about the team's fanclub.
        - FanclubID : unsigned Integer — The globally unique FanclubID.
        - FanclubName : String — The name of the fanclub.
        - FanclubSize : unsigned Integer — The amount of members in the fanclub.
      - LogoURL : URI — The logo URL that the team has specified.
      - **Guestbook** — This container and its elements is only shown if the user has supporter
        - NumberOfGuestbookItems : unsigned Integer — The number of postings in the team's guestbook.
      - **PressAnnouncement** — This container and its elements is only shown if the user has supporter
        - Subject : String — The subject text specified for the PressAnnouncement.
        - Body : String — The body of the PressAnnouncement.
        - SendDate : DateTime — The time and date when the PressAnnouncement was submitted.
      - **TeamColors** — This container and its elements is only shown if the user has supporter
        - BackgroundColor : String — The defined background color from the club theme.
        - Color : String — The matching text color to the defined background color.
      - DressURI : URI — URI to an image representing the dress of the team.
      - DressAlternateURI : URI — Same as DressURI, except that it shows the alternate dress.
      - **BotStatus** — Container with data indicating if a team is a 'bot'. If yes the team has default and random lineup and players.
        - IsBot : Boolean — Specifies whether a team is currently a bot or not.
        - BotSince : DateTime — The date when the team was made a bot, only availble if team is a bot.
      - TeamRank : unsigned Integer — If the team has an owner, last is the League Rank, a number based on LeagueLevel, postion etc, only counting teams with an owner. If the team is playing a match the tag will be empty.
      - YouthTeamID : unsigned Integer — If the Team has a Youth academy the ID is represented here. If not it will be 0.
      - YouthTeamName : String* — If the Team has a Youth academy the team name is represented here. If not it will be an empty string.
      - NumberOfVisits : unsigned Integer — The number of visits the team had in the latest day with at least one visit.
      - **Flags** — Acquired team flags.
        - **HomeFlags** — Acquired team home flags.
          - **Flag** — Container for flags.
            - LeagueID : unsigned Integer — The LeagueId for the league a flag belongs to.
            - LeagueName : String — The LeagueName for the league a flag belongs to.
            - CountryCode : String — The CountryCode for the league a flag belongs to.
        - **AwayFlags** — Acquired team away flags.
          - **Flag** — Container for flags.
            - LeagueID : unsigned Integer — The LeagueId for the league a flag belongs to.
            - LeagueName : String — The LeagueName for the league a flag belongs to.
            - CountryCode : String — The CountryCode for the league a flag belongs to.
      - **TrophyList** — Container for the team's trophies. It will be empty if no trophies are won.
        - **Trophy** — Container for the trophy. There may be serveral of this element.
          - TrophyTypeId : trophyID — TypeId for the trophy
          - TrophySeason : unsigned Integer — The season the trophy was won.
          - LeagueLevel : unsigned Integer — The level the league the user won. For tournament trophies, this act as a "type".
          - LeagueLevelUnitId : String — The LeagueLevelUnitId of the leaguelevelunit that the user won. For tournaments this is the id of the tournament.
          - LeagueLevelUnitName : String — The name of the leaguelevelunit that the user won. For tournaments this is the name of the tournament.
          - GainedDate : DateTime — The date when the user gained the trohpy.
          - ImageUrl : URI — The url to the trophy image.
          - CupLeagueLevel : unsigned Integer — The league level of the cup. 0 for the main cups, division 1-6. Empty when not a cup trophy (16).
          - CupLevel : CupLevel — The level of the cup. Empty when not a cup trophy (16).
          - CupLevelIndex : CupLevelIndex — Which type of challenger cup. Always 1 for national and consolation cups. Empty when not a cup trophy (16).
      - **SupportedTeams** — Attribute: 'TotalItems' : unsigned Integer Shows how many teams the user supports in total.
        - **SupportedTeam** — Container for a supported team. Not supplied if the user dont have no supporters.
          - UserId : unsigned Integer — The globally unique UserID for the owner of the supported team.
          - LoginName : String — The 'username' or 'nickname' used in Forums and around the site.
          - TeamId : unsigned Integer — The globally unique TeamID for the supported team.
          - TeamName : String — The teamname of the supported team.
          - LeagueID : unsigned Integer — The globally unique LeagueID for the supported team.
          - LeagueName : String — The League name.
          - LeagueLevelUnitID : unsigned Integer — The globally unique LeagueLevelUnitID for the supported team.
          - LeagueLevelUnitName : String — The name of the LeagueLevelUnit.
          - **LastMatch** — Container for the last match of the supported team.
            - LastMatchId : unsigned Integer — The globally unique matchID.
            - LastMatchDate : DateTime — The matchdate of the last match.
            - LastMatchHomeTeamId : unsigned Integer — The globally unique TeamID for the home team.
            - LastMatchHomeTeamName : String — The team name of the home team in the match.
            - LastMatchHomeGoals : unsigned Integer — The number of goals scored in the match by the hometeam.
            - LastMatchAwayTeamId : unsigned Integer — The globally unique TeamID for the away team.
            - LastMatchAwayTeamName : String — The team name of the away team in the match.
            - LastMatchAwayGoals : unsigned Integer — The number of goals scored in the match by the awayteam.
          - **NextMatch** — Container for the last match of the supported team.
            - NextMatchId : unsigned Integer — The globally unique matchID.
            - NextMatchDate : DateTime — The matchdate of the next match.
            - NextMatchHomeTeamId : unsigned Integer — The globally unique TeamID for the home team.
            - NextMatchHomeTeamName : String — The team name of the home team in the match.
            - NextMatchAwayTeamId : unsigned Integer — The globally unique TeamID for the away team.
            - NextMatchAwayTeamName : String — The team name of the away team in the match.
          - **PressAnnouncement** — Container for the data about the user's most recent PressAnnouncement.
            - PressAnnouncementSendDate : DateTime — The time and date when the PressAnnouncement was submitted.
            - PressAnnouncementSubject : String — The subject text specified for the PressAnnouncement.
            - PressAnnouncementBody : String — The body of the PressAnnouncement.
      - **MySupporters** — Attribute: 'TotalItems' : unsigned Integer Shows how many teams supports the team in total.
        - **SupporterTeam** — Container for a supported team. Not supplied if the user dont have no supporters.
          - UserId : unsigned Integer — The globally unique UserID for the owner of the supporter team.
          - LoginName : String — The 'username' or 'nickname' used in Forums and around the site.
          - TeamId : unsigned Integer — The globally unique TeamID for the supporter team.
          - TeamName : String — The teamname of the supporter team.
          - LeagueID : unsigned Integer — The globally unique LeagueID for the supporter team.
          - LeagueName : String — The League name.
          - LeagueLevelUnitID : unsigned Integer — The globally unique LeagueLevelUnitID for the supported team.
          - LeagueLevelUnitName : String — The name of the LeagueLevelUnit.
      - PossibleToChallengeMidweek : Boolean — Whether or not the team is possible to challenge for a mid-week friendly
      - PossibleToChallengeWeekend : Boolean — Whether or not the team is possible to challenge for a weekend friendly
