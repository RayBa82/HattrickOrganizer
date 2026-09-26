# transfersteam

Get the transfer history of a team

## Changelog
### Version 1.2 (2011-04-13) {default}
- First version

## Input
Only available inputs in this file version are shown.
required file = transfersteam
optional version = 
1.2
optional teamID : unsigned Integer{Default:Your own teamID}
What team to show the transfers for.
optional pageIndex : unsigned Integer{Default:1}
What page in the list to retrieve. Each page consists of 25 records unless last page which may contain 25 or less. If you want the last page specify 0.

## Output
- **HattrickData** — Hattrick Data Container
  - **Team** — Container for the Team to show the transfers for.
    - TeamID : unsigned Integer — Globally unique identifier of the team.
    - TeamName : String — The full name of the team.
    - ActivatedDate : DateTime — The date when the user got this team.
  - **Stats** — Container for the transfer stats.
    - TotalSumOfBuys : Money — The total sum in SEK of all the bought players for this team.
    - TotalSumOfSales : Money — The total sum in SEK of all the sold players for this team.
    - NumberOfBuys : unsigned Integer — The total number of bought players.
    - NumberOfSales : unsigned Integer — The total number of sold players.
  - **Transfers** — Container for a set of transfers.
    - PageIndex : unsigned Integer — The pageIndex retrieved.
    - Pages : unsigned Integer — The total number of pages available.
    - StartDate : DateTime — The oldest date of the selected transfers list.
    - EndDate : DateTime — The latest date of the selected transfers list.
    - **Transfer** — Container for a particular transfer.
      - TransferID : unsigned Integer — A globally unique identifier of a particular transfer.
      - Deadline : DateTime — The date when bidding closed for the transfer.
      - **Player** — Container for the player.
        - PlayerID : unsigned Integer — Globally unique identifier of the player.
        - PlayerName : String — The full name of the player.
        - TSI : unsigned Integer — The Total Skill Index (TSI) of the player at the time he was sent to the transfer list.
        - TransferType : Char — Char of either 'S' or 'B', representing if the transfer was a Sale or a Buy, seen from the sought for team's point of view.
        - **Buyer** — Container for the buyer team.
          - BuyerTeamID : unsigned Integer — Globally unique identifier of the buyer team.
          - BuyerTeamName : String — The full name of the buyer team.
        - **Seller** — Container for the Seller team.
          - SellerTeamID : unsigned Integer — Globally unique identifier of the Seller team.
          - SellerTeamName : String — The full name of the Seller team.
        - Price : Money — The final price that the buyer paid.
