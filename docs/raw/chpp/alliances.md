# alliances

Alliance / Federation search

## Changelog
### Version 1.4 (2013-03-04)
- Removed Output: HattrickData → UserIsSupporter (Element)
- Added Output: HattrickData → UserSupporterTier (Element)

### Version 1.3 (2011-05-30)
- Added Action for 'SearchType': 5 (Get user's federations)

### Version 1.2
- Removed Output: HattrickData → UserHasClubHouse (Element)

### Version 1.1 {default}
- First version

## Input
Only available inputs in this file version are shown.
required file = alliances
optional version = 
1.4
1.3
1.2
1.1
optional SearchType = 
1 {default}
2
3
4
5
What field to search in
1 {default}
Let the list include only federations with names beginning with search string.
optional SearchLanguageID : unsigned Integer{Default:0}
Specifies a language to restrict the federations on the list to. (0 = All languages)
required SearchFor : String
What to search for (length ≥ 3 characters)
optional PageIndex : unsigned Integer{Default:0}
Zero based pageindex to retrieve. If searchresult results in more than one page you will know the total number of pages. Each result may not return more than 25 rows.
2
Let the list include only federations with abbreviation including search string.
optional SearchLanguageID : unsigned Integer{Default:0}
Specifies a language to restrict the federations on the list to. (0 = All languages)
required SearchFor : String
What to search for (length ≥ 3 characters)
optional PageIndex : unsigned Integer{Default:0}
Zero based pageindex to retrieve. If searchresult results in more than one page you will know the total number of pages. Each result may not return more than 25 rows.
3
Let the list include only federations with description including search string.
optional SearchLanguageID : unsigned Integer{Default:0}
Specifies a language to restrict the federations on the list to. (0 = All languages)
required SearchFor : String
What to search for (length ≥ 3 characters)
optional PageIndex : unsigned Integer{Default:0}
Zero based pageindex to retrieve. If searchresult results in more than one page you will know the total number of pages. Each result may not return more than 25 rows.
4
Search for a specific AllianceID
optional SearchLanguageID : unsigned Integer{Default:0}
Specifies a language to restrict the federations on the list to. (0 = All languages)
required SearchFor : unsigned Integer
What to search for
optional PageIndex : unsigned Integer{Default:0}
Zero based pageindex to retrieve. If searchresult results in more than one page you will know the total number of pages. Each result may not return more than 25 rows.
5
Get user's federations

## Output
- **HattrickData** — Hattrick Data Container
  - Pages : unsigned Integer — Number of result pages.
  - PageIndex : unsigned Integer — Zero based pageindex. If searchresult results in more than one page you will know the total number of pages. Each result may not return more than 25 rows.
  - UserSupporterTier : supporterTier — Indicates which Supporter package the fetching user has, or empty if not a Supporter.
  - **Alliances** — Container for the list of federation. An Attribute named Count specifies how many federations it contains.
    - **Alliance** — Container for a federation.
      - AllianceID : unsigned Integer — The globally unique AllianceID.
      - AllianceName : String (HTML encoded) — The name of the federation.
      - AllianceDescription : String (HTML encoded) — Description of the federation.
