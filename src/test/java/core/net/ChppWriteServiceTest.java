package core.net;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class ChppWriteServiceTest {

    /**
     * A setTraining echo, shaped like a real training 2.2 response: the <Team> block carries the
     * current settings (no <View> wrapper, no invented <TrainingSet> element).
     */
    private static final String TRAINING_OK = """
            <?xml version="1.0" encoding="utf-8"?>
            <HattrickData>
              <FileName>training.xml</FileName>
              <Version>2.2</Version>
              <FetchedDate>2026-09-03 12:00:00</FetchedDate>
              <Team>
                <TeamID>1500</TeamID>
                <TeamName>HO FC</TeamName>
                <TrainingLevel>40</TrainingLevel>
                <NewTrainingLevel Available="false"></NewTrainingLevel>
                <StaminaTrainingPart>10</StaminaTrainingPart>
                <TrainingType>3</TrainingType>
                <LastTrainingTrainingType>2</LastTrainingTrainingType>
                <LastTrainingTrainingLevel>50</LastTrainingTrainingLevel>
                <LastTrainingStaminaTrainingPart>15</LastTrainingStaminaTrainingPart>
                <Trainer><TrainerID>1</TrainerID><TrainerName>T</TrainerName><ArrivalDate>2026-01-01 00:00:00</ArrivalDate></Trainer>
              </Team>
            </HattrickData>
            """;

    /** Same, but the intensity change is pending (takes effect at the next training update). */
    private static final String TRAINING_LEVEL_PENDING = TRAINING_OK.replace(
            "<NewTrainingLevel Available=\"false\"></NewTrainingLevel>",
            "<NewTrainingLevel Available=\"true\">40</NewTrainingLevel>")
            .replace("<TrainingLevel>40</TrainingLevel>", "<TrainingLevel>55</TrainingLevel>");

    /** A CHPP error response (bad scope, bad value, ...). */
    private static final String TRAINING_ERROR = """
            <HattrickData><FileName>training.xml</FileName>
              <ErrorCode>50</ErrorCode>
              <Error>The scope set_training is required.</Error>
            </HattrickData>
            """;

    /** A service whose fetch* methods answer from canned strings, never the network. */
    static class StubService extends ChppWriteService {
        String trainingResponse = TRAINING_OK;
        int trainingFetches;
        String challengesResponse = """
                <HattrickData><Version>1.6</Version>
                  <ChallengesByMe/><OffersByOthers/>
                </HattrickData>
                """;
        int challengeFetches;
        String lastChallengeAction;
        java.util.Map<String, String> lastChallengeParams;

        @Override
        protected String fetchSetTraining(int teamId, int trainingType, int trainingLevel, int staminaLevel) {
            trainingFetches++;
            return trainingResponse;
        }

        @Override
        protected String fetchChallenges(String actionType, java.util.Map<String, String> params) {
            challengeFetches++;
            lastChallengeAction = actionType;
            lastChallengeParams = params;
            return challengesResponse;
        }

        String matchOrderResponse = "<HattrickData><MatchData>ok</MatchData></HattrickData>";
        RuntimeException matchOrderFailure;
        int matchOrderFetches;

        @Override
        protected String fetchMatchOrder(int matchId, core.model.enums.MatchType matchType, String orderString) {
            matchOrderFetches++;
            if (matchOrderFailure != null) throw matchOrderFailure;
            return matchOrderResponse;
        }

        /** Shaped like a real playerdetails 3.2 placeBid response (bid state under TransferDetails). */
        String bidResponse = """
                <HattrickData><Player>
                  <PlayerID>111</PlayerID>
                  <TransferListed>true</TransferListed>
                  <TransferDetails>
                    <AskingPrice>1000000</AskingPrice>
                    <Deadline>2026-09-05 20:00:00</Deadline>
                    <HighestBid>1750000</HighestBid>
                    <MaxBid>2000000</MaxBid>
                  </TransferDetails>
                  <BidderTeam>
                    <TeamID>4711</TeamID>
                    <TeamName>HO FC</TeamName>
                  </BidderTeam>
                </Player></HattrickData>
                """;
        int bidFetches;

        @Override
        protected String fetchPlaceBid(int playerId, int teamId, long bidAmount, Long maxBidAmount) {
            bidFetches++;
            return bidResponse;
        }
    }

    private StubService service;

    @BeforeEach
    void setUp() {
        service = new StubService();
        core.model.UserParameter.instance().mcpChppWritesPerSession = 20;
    }

    @Test
    void setTraining_returnsTheCommittedValuesFromTheEcho() throws Exception {
        final var committed = service.setTrainingForAgent(3, 40, 10);
        assertThat(committed.trainingType()).isEqualTo(3);
        assertThat(committed.intensity()).isEqualTo(40);
        assertThat(committed.staminaShare()).isEqualTo(10);
        assertThat(committed.intensityChangePending()).isFalse();
        assertThat(service.writesThisSession).isEqualTo(1);
    }

    @Test
    void setTraining_reportsAPendingIntensityFromNewTrainingLevel() throws Exception {
        service.trainingResponse = TRAINING_LEVEL_PENDING;
        final var committed = service.setTrainingForAgent(3, 40, 10);
        assertThat(committed.intensity()).isEqualTo(40);              // the pending NewTrainingLevel
        assertThat(committed.intensityChangePending()).isTrue();
    }

    @Test
    void setTraining_chppErrorIsAFailure() {
        service.trainingResponse = TRAINING_ERROR;
        assertThatExceptionOfType(ChppWriteService.ChppWriteException.class)
                .isThrownBy(() -> service.setTrainingForAgent(3, 40, 10));
    }

    @Test
    void setTraining_blankResponseIsAFailureCarryingLastError() {
        service.trainingResponse = "";
        assertThatExceptionOfType(ChppWriteService.ChppWriteException.class)
                .isThrownBy(() -> service.setTrainingForAgent(3, 40, 10));
    }

    @Test
    void setTraining_rejectsOutOfRangeBeforeAnyFetch() {
        assertThatExceptionOfType(ChppWriteService.ChppWriteException.class)
                .isThrownBy(() -> service.setTrainingForAgent(3, 200, 10));
        assertThatExceptionOfType(ChppWriteService.ChppWriteException.class)
                .isThrownBy(() -> service.setTrainingForAgent(3, 40, 1));
        assertThat(service.trainingFetches).isZero();
    }

    @Test
    void cap_refusesThe21stWriteAndNamesTheOptionsPanel() throws Exception {
        core.model.UserParameter.instance().mcpChppWritesPerSession = 20;
        for (int i = 0; i < 20; i++) {
            service.setTrainingForAgent(3, 40, 10);
        }
        assertThatExceptionOfType(ChppWriteService.ChppWriteException.class)
                .isThrownBy(() -> service.setTrainingForAgent(3, 40, 10))
                .withMessageContaining("20")
                .withMessageContaining("MCP server");
    }

    @Test
    void listChallenges_doesNotCountAgainstTheWriteCap() throws Exception {
        service.listChallengesForAgent(false);
        service.listChallengesForAgent(true);
        assertThat(service.writesThisSession).isZero();
        assertThat(service.lastChallengeAction).isEqualTo("view");
    }

    @Test
    void listChallenges_blankResponseIsAFailure() {
        service.challengesResponse = "";
        assertThatExceptionOfType(ChppWriteService.ChppWriteException.class)
                .isThrownBy(() -> service.listChallengesForAgent(false));
    }

    @Test
    void checkChallengeable_isAReadThatSendsTheIdsCommaSeparated() throws Exception {
        service.checkChallengeableForAgent(java.util.List.of(10, 11, 12), true);
        assertThat(service.writesThisSession).isZero();
        assertThat(service.lastChallengeAction).isEqualTo("challengeable");
        assertThat(service.lastChallengeParams)
                .containsEntry("suggestedTeamIds", "10,11,12")
                .containsEntry("isWeekendFriendly", "1");
    }

    /** Error 70's two meanings reach the agent as reason tokens it can branch on. */
    @Test
    void arrangeChallenge_errorIsReportedWithItsReason() {
        service.challengesResponse = """
                <HattrickData><FileName>chpperror.xml</FileName>
                  <ErrorCode>70</ErrorCode>
                  <Error>Challenge failed (70) Additional Info: Your team already has booked a friendly this week</Error>
                </HattrickData>
                """;
        assertThatExceptionOfType(ChppWriteService.ChppWriteException.class)
                .isThrownBy(() -> service.arrangeChallengeForAgent(999, 0, 0, null, false))
                .withMessageContaining("OWN_FRIENDLY_BOOKED (CHPP 70)");
    }

    @Test
    void arrangeChallenge_neutralWithoutArenaIsRejectedBeforeAnyFetch() {
        assertThatExceptionOfType(ChppWriteService.ChppWriteException.class)
                .isThrownBy(() -> service.arrangeChallengeForAgent(999, 0, 2, null, false));
        assertThat(service.challengeFetches).isZero();
    }

    @Test
    void arrangeChallenge_countsOneWrite() throws Exception {
        service.arrangeChallengeForAgent(999, 1, 2, 42, true);
        assertThat(service.writesThisSession).isEqualTo(1);
        assertThat(service.lastChallengeAction).isEqualTo("challenge");
    }

    @Test
    void respondToChallenge_rejectsAnUnknownVerbBeforeAnyFetch() {
        assertThatExceptionOfType(ChppWriteService.ChppWriteException.class)
                .isThrownBy(() -> service.respondToChallengeForAgent("cancel", 733551221L));
        assertThat(service.challengeFetches).isZero();
    }

    @Test
    void respondToChallenge_acceptCountsOneWrite() throws Exception {
        service.respondToChallengeForAgent("accept", 733551221L);
        assertThat(service.writesThisSession).isEqualTo(1);
        assertThat(service.lastChallengeAction).isEqualTo("accept");
    }

    @Test
    void sendMatchOrder_countsOneWriteAndReturnsTheResponse() throws Exception {
        final var response = service.sendMatchOrderForAgent(
                12345, core.model.enums.MatchType.LEAGUE, "{\"positions\":[]}");
        assertThat(response).contains("ok");
        assertThat(service.writesThisSession).isEqualTo(1);
    }

    @Test
    void sendMatchOrder_wrapsAConnectorFailure() {
        service.matchOrderFailure = new RuntimeException("CHPP Fehler - 50 setmatchorder");
        assertThatExceptionOfType(ChppWriteService.ChppWriteException.class)
                .isThrownBy(() -> service.sendMatchOrderForAgent(
                        1, core.model.enums.MatchType.LEAGUE, "{}"))
                .withMessageContaining("50");
    }

    @Test
    void sendMatchOrder_withoutSupporter_isItsOwnFailure() {
        service.matchOrderFailure = new RuntimeException("CHPP Fehler - 6 - setmatchorder is only "
                + "available for Hattrick Supporters");
        assertThatExceptionOfType(ChppWriteService.SupporterRequiredException.class)
                .isThrownBy(() -> service.sendMatchOrderForAgent(
                        1, core.model.enums.MatchType.LEAGUE, "{}"))
                .withMessageContaining("SUPPORTER_REQUIRED");
    }

    /** Hattrick refuses an order with OrdersSet="False", not an error code - that is no "ok". */
    @Test
    void sendMatchOrder_ordersNotSetIsAFailure() {
        service.matchOrderResponse = """
                <HattrickData><MatchData OrdersSet="False">
                  <Reason>The match has already started.</Reason>
                </MatchData></HattrickData>
                """;
        assertThatExceptionOfType(ChppWriteService.ChppWriteException.class)
                .isThrownBy(() -> service.sendMatchOrderForAgent(
                        1, core.model.enums.MatchType.LEAGUE, "{}"))
                .withMessageContaining("The match has already started.")
                .isNotInstanceOf(ChppWriteService.SupporterRequiredException.class);
    }

    @Test
    void placeBid_parsesTransferDetailsAndTheBidderTeam() throws Exception {
        final var result = service.placeBidForAgent(111, 1_500_000L, null);
        assertThat(result.highestBid()).isEqualTo(1_750_000L);   // raw swedish krona, from <TransferDetails>
        assertThat(result.deadline()).isEqualTo("2026-09-05 20:00:00");
        assertThat(result.bidderTeamName()).isEqualTo("HO FC");  // from <BidderTeam><TeamName>
        assertThat(service.writesThisSession).isEqualTo(1);
    }

    @Test
    void placeBid_blankResponseFailsWithBidFailedKey() {
        service.bidResponse = "";
        assertThatExceptionOfType(ChppWriteService.ChppWriteException.class)
                .isThrownBy(() -> service.placeBidForAgent(111, 1_500_000L, null))
                .satisfies(e -> assertThat(e.errorKey()).isEqualTo("BidFailed"));
    }

    @Test
    void placeBid_chppErrorResponseFailsWithBidFailedKey() {
        service.bidResponse = """
                <HattrickData><FileName>playerdetails.xml</FileName>
                  <ErrorCode>50</ErrorCode>
                  <Error>Only Supporters may place bids.</Error>
                </HattrickData>
                """;
        assertThatExceptionOfType(ChppWriteService.ChppWriteException.class)
                .isThrownBy(() -> service.placeBidForAgent(111, 1_500_000L, null))
                .satisfies(e -> assertThat(e.errorKey()).isEqualTo("BidFailed"));
    }
}
