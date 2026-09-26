package core.file.xml;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class XMLChallengesParserTest {

    /**
     * Shaped from the official challenges 1.6 doc: under {@code <Team>}, {@code <ChallengesByMe>} /
     * {@code <OffersByOthers>} hold {@code <Challenge>} / {@code <Offer>} items whose
     * {@code TrainingMatchID}, {@code MatchID}, {@code MatchTime}, {@code FriendlyType},
     * {@code <Arena>} and {@code IsAgreed} are siblings of {@code <Opponent>} (only TeamID/TeamName
     * are inside {@code <Opponent>}).
     */
    private static final String VIEW = """
            <?xml version="1.0" encoding="utf-8"?>
            <HattrickData>
              <FileName>challenges.xml</FileName>
              <Version>1.6</Version>
              <Team>
                <TeamID>1500</TeamID>
                <TeamName>HO FC</TeamName>
                <ChallengesByMe>
                  <Challenge>
                    <TrainingMatchID>733551221</TrainingMatchID>
                    <MatchTime>2026-09-06 15:00:00</MatchTime>
                    <MatchID>0</MatchID>
                    <FriendlyType>1</FriendlyType>
                    <Opponent>
                      <TeamID>1877</TeamID>
                      <TeamName>Rivals FC</TeamName>
                      <LogoURL>http://x/logo.png</LogoURL>
                    </Opponent>
                    <Arena>
                      <ArenaID>200</ArenaID>
                      <ArenaName>Rivals Arena</ArenaName>
                    </Arena>
                    <Country>
                      <CountryID>5</CountryID>
                      <CountryName>Sweden</CountryName>
                    </Country>
                    <IsAgreed>false</IsAgreed>
                  </Challenge>
                </ChallengesByMe>
                <OffersByOthers>
                  <Offer>
                    <TrainingMatchID>733559999</TrainingMatchID>
                    <MatchTime>2026-09-06 18:00:00</MatchTime>
                    <MatchID>733999888</MatchID>
                    <FriendlyType>0</FriendlyType>
                    <Opponent>
                      <TeamID>2999</TeamID>
                      <TeamName>Challengers United</TeamName>
                    </Opponent>
                    <Arena>
                      <ArenaID>300</ArenaID>
                      <ArenaName>CU Park</ArenaName>
                    </Arena>
                    <League>
                      <LeagueID>36</LeagueID>
                      <LeagueName>Sweden</LeagueName>
                    </League>
                    <IsAgreed>true</IsAgreed>
                  </Offer>
                </OffersByOthers>
              </Team>
            </HattrickData>
            """;

    @Test
    void parse_readsBothLists() {
        final var lists = XMLChallengesParser.parse(VIEW);

        assertThat(lists.outgoing()).hasSize(1);
        final var out = lists.outgoing().get(0);
        assertThat(out.trainingMatchId()).isEqualTo(733551221L);
        assertThat(out.matchId()).isZero();
        assertThat(out.matchTime()).isEqualTo("2026-09-06 15:00:00");
        assertThat(out.friendlyType()).isEqualTo(1);
        assertThat(out.opponentTeamId()).isEqualTo(1877);
        assertThat(out.opponentName()).isEqualTo("Rivals FC");
        assertThat(out.arenaName()).isEqualTo("Rivals Arena");
        assertThat(out.isAgreed()).isFalse();

        assertThat(lists.incoming()).hasSize(1);
        final var in = lists.incoming().get(0);
        assertThat(in.trainingMatchId()).isEqualTo(733559999L);
        assertThat(in.matchId()).isEqualTo(733999888L);
        assertThat(in.arenaName()).isEqualTo("CU Park");
        assertThat(in.isAgreed()).isTrue();
    }

    /**
     * The challengeable answer repeats the view's team block (with its own Opponent elements) and
     * adds ChallengeableResult/Opponent, per the challenges 1.6 doc. Only the latter may be read.
     */
    @Test
    void parseChallengeable_readsOnlyTheChallengeableResult() {
        final var xml = VIEW.replace("</Team>", """
                </Team>
                <ChallengeableResult>
                  <Opponent>
                    <IsChallengeable>True</IsChallengeable>
                    <UserId>42</UserId>
                    <TeamID>1877</TeamID>
                    <TeamName>Rivals FC</TeamName>
                  </Opponent>
                  <Opponent>
                    <IsChallengeable>False</IsChallengeable>
                    <UserId>43</UserId>
                    <TeamID>1878</TeamID>
                    <TeamName>Busy FC</TeamName>
                  </Opponent>
                </ChallengeableResult>""");
        final var result = XMLChallengesParser.parseChallengeable(xml);
        assertThat(result).containsExactly(
                new XMLChallengesParser.Challengeable(1877, "Rivals FC", 42, true),
                new XMLChallengesParser.Challengeable(1878, "Busy FC", 43, false));
        assertThat(XMLChallengesParser.parseChallengeable(VIEW)).isEmpty();
        assertThat(XMLChallengesParser.parseChallengeable("not xml")).isEmpty();
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
