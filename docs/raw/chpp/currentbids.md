# currentbids

Shows the current transfer activity for a team.

## Changelog
### Version 1.0 (2014-02-10) {default}
- First version

## Input
Only available inputs in this file version are shown.
required file = currentbids
optional version = 
1.0
optional actionType = 
view {default}
ignoreTransfer
deleteAllFinished
Indicates what type of action the page should perform.
view {default}
Shows all active, recently finished or hotlisted transfers for the specified team.
ignoreTransfer
Removes the tracking of a specific transfer, for Supporters only. Only possible for losing bids, hotlisted and finished. (Requires "place_bid" scope.)
deleteAllFinished
Removes the tracking of all finished transfers in one go, for Supporters only. (Requires "place_bid" scope.)
optional teamId : unsigned Integer{Default:Your primary club senior teamId}
Team id to show the transfers for. Must refer to a senior team managed by requesting user.
required_1 transferId : unsigned Integer
Id of the transfer to be ignored.
required_1 trackingTypeId : trackingTypeId
Tracking type (category) of the transfer to be ignored. Only values 5, 8 and 9 are allowed.
required_1: Inputs transferId and trackingTypeId are required for actionType=ignoreTransfer.

## Output (actionType=view)
- **HattrickData** — Hattrick Data Container.
  - TeamId : unsigned Integer — Globally unique identifier of the team whose bids are shown.
  - **BidItems** — Attribute: 'TrackingTypeId' : trackingTypeId Type of tracking, the categories the transfers are grouped in.
    - **BidItem** — Container for each transfer.
      - TransferId : unsigned Integer — Unique id for the transfer. Only needed for the ignore functionality.
      - PlayerId : unsigned Integer — The globally unique playerID for the transferlisted player.
      - PlayerName : String — The name for the transferlisted player.
      - **HighestBid** — Container for the current transfers. Empty if no bids.
        - Amount : Money — Amount of the highest bid.
        - TeamId : unsigned Integer — Id of the team holding the highest bid.
        - TeamName : String — Name of the team holding the highest bid.
      - Deadline : DateTime — The transfer deadline.

## Output (actionType=ignoreTransfer)
- **HattrickData** — Hattrick Data Container.
  - **View** — The same data as in actionType=view is shown here.

## Output (actionType=deleteAllFinished)
Same as actionType=ignoreTransfer.
