# transfersplayer

Get all transfers of a player

## Changelog
### Version 1.1 {default}
- First version

## Input
Only available inputs in this file version are shown.
required file = transfersplayer
optional version = 
1.1
required playerID : unsigned Integer
The player to get the transfers for.

## Output
- **HattrickData** — Hattrick Data Container
  - **Transfers** — Container for a set of transfers.
    - StartDate : DateTime — The oldest date of the selected transfers list.
    - EndDate : DateTime — The latest date of the selected transfers list.
    - **Player** — Container for the player.
      - PlayerID : unsigned Integer — Globally unique identifier of the player.
      - PlayerName : String — The full name of the player.
    - **Transfer** — Container for a particular transfer. There can be several of this element.
      - TransferID : unsigned Integer — A globally unique identifier of a particular transfer.
      - Deadline : DateTime — The date when bidding closed for the transfer.
      - **Buyer** — Container for the buyer team.
        - BuyerTeamID : unsigned Integer — Globally unique identifier of the buyer team.
        - BuyerTeamName : String — The full name of the buyer team.
      - **Seller** — Container for the Seller team.
        - SellerTeamID : unsigned Integer — Globally unique identifier of the Seller team.
        - SellerTeamName : String — The full name of the Seller team.
      - Price : Money — The final price that the buyer paid.
      - TSI : unsigned Integer — The Total Skill Index (TSI) of the player at the time he was sent to the transfer list.
