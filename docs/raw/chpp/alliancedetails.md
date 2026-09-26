# alliancedetails

Alliance / Federation information

## Changelog
### Version 1.5 (2013-03-04)
- Removed Output: HattrickData → UserIsSupporter (Element)
- Added Output: HattrickData → UserSupporterTier (Element)

### Version 1.4
- Added Output: HattrickData → Alliance → Roles → Role → RoleMaxMembers (Element)
- Added Output: HattrickData → Alliance → Roles → Role → RoleRequestType (Element)
- Added Output: HattrickData → Alliance → Roles → Role → RoleDescription (Element)

### Version 1.3
- Removed Output: HattrickData → UserHasClubhouse (Element)

### Version 1.2 {default}
- First version

## Input
Only available inputs in this file version are shown.
required file = alliancedetails
optional version = 
1.5
1.4
1.3
1.2
optional actionType = 
view {default}
roles
members
membersSubset
Indicates what type of action the page should perform.
view {default}
Federation info.
roles
View the roles of a federation.
members
List the members of a federation.
membersSubset
List a subset of the members of a federation.
optional Subset : unsigned Integer
Starting character names to show A = 65 .... Z = 90. Use 0 for other than english starting characters. Only relevant for actionType membersSubset.
required allianceID : unsigned Integer
ID of federation you search information for.

## Output (actionType=view)
- **HattrickData** — Hattrick Data Container
  - UserSupporterTier : supporterTier — Indicates which Supporter package the fetching user has, or empty if not a Supporter.
  - ActionType : String — The requested ActionType.
  - **Alliance** — Container for the data of the federation.
    - AllianceID : unsigned Integer — The globally unique AllianceID.
    - AllianceName : String (HTML encoded) — The name of the federation.
    - Abbreviation : String (HTML encoded) — The abbreviation of the federation. Left out if abbreviation is missing for federation.
    - Description : String (HTML encoded) — Description of the federation.
    - LogoURL : URI — The URL of the logo of the federation. Left out if logo is missing for federation.
    - TopRole : String — Name of role within federation for the top user of the federation.
    - TopUserID : unsigned Integer — Globally unique UserID for the top user of the federation.
    - TopLoginname : String — Alias for the top user of the federation.
    - CreationDate : DateTime — Creation date of the federation.
    - HomePageURL : URI — URL for home page of the federation.
    - NumberOfMembers : unsigned Integer — Number of members in the federation.
    - **Languages** — Container for the list of languages the federation has declared As used languages. An Attribute named Count specifies how many federations it contains.
      - **Language** — Container for a language. An Attribute named Index works as a counter.
        - LanguageID : unsigned Integer — Globally unique id for the language. If the choice of language is 'All' the id is 0.
        - LanguageName : String — Name of the language or 'All' for id 0.
    - Message : String — Internal message to members of federation.
    - Rules : String — Internal message to members of federation. Only present if indata ShowRules is 'true'.
    - **UserRole** — Container for role of fetching user in federation.
      - RoleId : unsigned Integer — Id of role within federation.
      - RoleName : String — Name of role within federation.

## Output (actionType=roles)
- **HattrickData** — Hattrick Data Container
  - UserIsSupporter : Boolean — Indicated if the fetching user has a supporter subscription or not.
  - ActionType : String — The requested ActionType.
  - **Alliance** — Container for the data of the federation.
    - AllianceID : unsigned Integer — The globally unique AllianceID.
    - AllianceName : String (HTML encoded) — The name of the federation.
    - **Roles** — Container for list of roles in federation. An Attribute named Count specifies how many federations it contains.
      - **Role** — Container for a role. An Attribute named Index works as a counter.
        - RoleId : unsigned Integer — Id of role within federation.
        - RoleName : String — Name of role within federation.
        - RoleRank : unsigned Integer — Rank of role within federation.
        - RoleMemberCount : unsigned Integer — Number of members in federation.
        - RoleMaxMembers : unsigned Integer — Max number of members in role.
        - RoleRequestType : unsigned Integer — Way to become member of a role in federation with 1 = 'apply' and 2 = 'free for all'
        - RoleDescription : String — Description of role within federation.

## Output (actionType=members)
- **HattrickData** — Hattrick Data Container
  - UserIsSupporter : Boolean — Indicated if the fetching user has a supporter subscription or not.
  - ActionType : String — The requested ActionType.
  - **Alliance** — Container for the data of the federation.
    - AllianceID : unsigned Integer — The globally unique AllianceID.
    - AllianceName : String (HTML encoded) — The name of the federation.
    - **Members** — Container for the list of members, or a subset thereof, of the federation.
      - **Member** — Container for a member.
        - UserID : unsigned Integer — Globally unique user id of member.
        - Loginname : String — Alias of member.
        - RoleId : unsigned Integer — Role id within federation of member.
        - RoleName : String — Role name within federation of user.
        - IsOnline : Boolean — Is user currently online?
        - MembershipDate : DateTime — Date when member last changed role within federation.

## Output (actionType=membersSubset)
- **HattrickData** — Hattrick Data Container
  - UserIsSupporter : Boolean — Indicated if the fetching user has a supporter subscription or not.
  - ActionType : String — The requested ActionType.
  - **Alliance** — Container for the data of the federation.
    - AllianceID : unsigned Integer — The globally unique AllianceID.
    - AllianceName : String (HTML encoded) — The name of the federation.
    - ListSubset : String — Subset of members in federation to list for actionType membersSubset.
    - **Members** — Container for the list of members, or a subset thereof, of the federation.
      - **Member** — Container for a member.
        - UserID : unsigned Integer — Globally unique user id of member.
        - Loginname : String — Alias of member.
        - RoleId : unsigned Integer — Role id within federation of member.
        - RoleName : String — Role name within federation of user.
        - IsOnline : Boolean — Is user currently online?
        - MembershipDate : DateTime — Date when member last changed role within federation.
