package core.file.xml;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class XMLChallengesParserTest {

    /**
     * Hand-built from the challenges 1.6 schema (spec section 1.6). VERIFY the element casing
     * (notably MatchTime and IsAgreed) against a real `challenges&actionType=view` response or the
     * reference structs at github.com/lucianoq/hattrick before merging.
     */
    private static final String VIEW = """
            <?xml version="1.0" encoding="utf-8"?>
            <HattrickData>
              <FileName>challenges.xml</FileName>
              <Version>1.6</Version>
              <IsPossibleToChallengeMidWeek>false</IsPossibleToChallengeMidWeek>
              <IsPossibleToChallengeWeekend>true</IsPossibleToChallengeWeekend>
              <ChallengesByMe>
                <Challenge>
                  <TrainingMatchID>733551221</TrainingMatchID>
                  <MatchTime>2026-09-06 15:00:00</MatchTime>
                  <FriendlyType>1</FriendlyType>
                  <Opponent>
                    <TeamID>1877</TeamID>
                    <TeamName>Rivals FC</TeamName>
                    <ArenaName>Rivals Arena</ArenaName>
                    <IsAgreed>false</IsAgreed>
                  </Opponent>
                </Challenge>
              </ChallengesByMe>
              <OffersByOthers>
                <Offer>
                  <TrainingMatchID>733559999</TrainingMatchID>
                  <MatchTime>2026-09-06 18:00:00</MatchTime>
                  <FriendlyType>0</FriendlyType>
                  <Opponent>
                    <TeamID>2999</TeamID>
                    <TeamName>Challengers United</TeamName>
                    <ArenaName>CU Park</ArenaName>
                    <IsAgreed>true</IsAgreed>
                  </Opponent>
                </Offer>
              </OffersByOthers>
            </HattrickData>
            """;

    @Test
    void parse_readsBothLists() {
        final var lists = XMLChallengesParser.parse(VIEW);

        assertThat(lists.outgoing()).hasSize(1);
        final var out = lists.outgoing().get(0);
        assertThat(out.trainingMatchId()).isEqualTo(733551221L);
        assertThat(out.matchTime()).isEqualTo("2026-09-06 15:00:00");
        assertThat(out.friendlyType()).isEqualTo(1);
        assertThat(out.opponentTeamId()).isEqualTo(1877);
        assertThat(out.opponentName()).isEqualTo("Rivals FC");
        assertThat(out.arenaName()).isEqualTo("Rivals Arena");
        assertThat(out.isAgreed()).isFalse();

        assertThat(lists.incoming()).hasSize(1);
        assertThat(lists.incoming().get(0).trainingMatchId()).isEqualTo(733559999L);
        assertThat(lists.incoming().get(0).isAgreed()).isTrue();
    }

    @Test
    void parse_garbageIsTwoEmptyLists() {
        final var lists = XMLChallengesParser.parse("not xml");
        assertThat(lists.outgoing()).isEmpty();
        assertThat(lists.incoming()).isEmpty();
    }

    @Test
    void parse_missingContainersAreEmptyLists() {
        final var lists = XMLChallengesParser.parse(
                "<HattrickData><Version>1.6</Version></HattrickData>");
        assertThat(lists.outgoing()).isEmpty();
        assertThat(lists.incoming()).isEmpty();
    }
}
