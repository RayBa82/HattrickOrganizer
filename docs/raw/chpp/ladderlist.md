# ladderlist

Information about ladder that the user is currently playing in.

## Changelog
### Version 1.0 (2013-11-08) {default}
- First version

## Input
Only available inputs in this file version are shown.
required file = ladderlist
optional version = 
1.0
optional teamId : unsigned Integer{Default:Your primary club senior teamId}
Team id to get data for.

## Output
- **HattrickData** — Hattrick Data Container
  - **Ladders** — Container for the list of ladders.
    - **Ladder** — Container for the data about the ladder.
      - LadderId : unsigned Integer — The globally unique id for the ladder.
      - Name : String — The name of the ladder.
      - Posistion : unsigned Integer — The Position.
      - NextMatchDate : DateTime — The date of the next match. During matches this will be the Next match round.
      - Wins : unsigned Integer — Total wins.
      - Lost : unsigned Integer — Total lost.
