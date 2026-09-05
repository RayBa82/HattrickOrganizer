package core.file.xml;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class XMLCurrentBidsParserTest {

    private static final String XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <HattrickData>
              <TeamId>1234</TeamId>
              <BidItems TrackingTypeID="2">
                <BidItem>
                  <TransferId>500</TransferId>
                  <PlayerId>111</PlayerId>
                  <PlayerName>Leading Larry</PlayerName>
                  <HighestBid>
                    <Amount>1500000</Amount>
                    <TeamId>1234</TeamId>
                    <TeamName>My Team</TeamName>
                  </HighestBid>
                  <Deadline>2030-01-02 20:00:00</Deadline>
                </BidItem>
              </BidItems>
              <BidItems TrackingTypeID="8">
                <BidItem>
                  <TransferId>501</TransferId>
                  <PlayerId>222</PlayerId>
                  <PlayerName>Outbid Ollie</PlayerName>
                  <HighestBid>
                    <Amount>2000000</Amount>
                    <TeamId>9999</TeamId>
                    <TeamName>Rival FC</TeamName>
                  </HighestBid>
                  <Deadline>2030-01-03 20:00:00</Deadline>
                </BidItem>
              </BidItems>
              <BidItems TrackingTypeID="5">
                <BidItem>
                  <TransferId>502</TransferId>
                  <PlayerId>333</PlayerId>
                  <PlayerName>Hotlist Harry</PlayerName>
                  <HighestBid/>
                  <Deadline>2030-01-04 20:00:00</Deadline>
                </BidItem>
              </BidItems>
            </HattrickData>
            """;

    @Test
    void parse_readsTeamIdAndGroupsItemsUnderTheirTrackingType() {
        final var result = XMLCurrentBidsParser.parse(XML);
        assertThat(result.teamId()).isEqualTo(1234);
        assertThat(result.items()).extracting(XMLCurrentBidsParser.BidItem::playerId)
                .containsExactly(111, 222, 333);
        assertThat(result.items()).extracting(XMLCurrentBidsParser.BidItem::trackingTypeId)
                .containsExactly(2, 8, 5);
    }

    @Test
    void parse_readsHighestBidAndDeadline() {
        final var outbid = XMLCurrentBidsParser.parse(XML).items().get(1);
        assertThat(outbid.transferId()).isEqualTo(501);
        assertThat(outbid.playerName()).isEqualTo("Outbid Ollie");
        assertThat(outbid.highestBidAmount()).isEqualTo(2_000_000);
        assertThat(outbid.highestBidTeamId()).isEqualTo(9999);
        assertThat(outbid.highestBidTeamName()).isEqualTo("Rival FC");
        assertThat(outbid.deadline()).isEqualTo("2030-01-03 20:00:00");
    }

    @Test
    void parse_emptyHighestBidYieldsZeroes() {
        final var hot = XMLCurrentBidsParser.parse(XML).items().get(2);
        assertThat(hot.highestBidAmount()).isZero();
        assertThat(hot.highestBidTeamId()).isZero();
        assertThat(hot.highestBidTeamName()).isEmpty();
    }

    @Test
    void parse_toleratesLowercaseTrackingTypeIdAttribute() {
        final var xml = XML.replace("TrackingTypeID=\"2\"", "TrackingTypeId=\"2\"");
        assertThat(XMLCurrentBidsParser.parse(xml).items().get(0).trackingTypeId()).isEqualTo(2);
    }

    @Test
    void parse_returnsEmptyOnGarbage() {
        final var result = XMLCurrentBidsParser.parse("not xml");
        assertThat(result.teamId()).isZero();
        assertThat(result.items()).isEmpty();
    }
}
