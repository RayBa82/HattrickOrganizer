package core.net;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class TransferSearchServiceTest {

    private static final String ONE_RESULT = """
            <?xml version="1.0" encoding="utf-8"?>
            <HattrickData>
              <FileName>transfersearch.xml</FileName>
              <Version>1.1</Version>
              <TransferSearch>
                <ItemCount>1</ItemCount>
                <PageSize>50</PageSize>
                <PageIndex>0</PageIndex>
                <TransferResults>
                  <TransferResult>
                    <PlayerId>111222333</PlayerId>
                    <FirstName>Sven</FirstName>
                    <NickName></NickName>
                    <LastName>Persson</LastName>
                    <NativeCountryID>3</NativeCountryID>
                    <AskingPrice>1500000</AskingPrice>
                    <Deadline>2026-09-02 20:15:00</Deadline>
                    <HighestBid>1650000</HighestBid>
                    <Details>
                      <Age>24</Age>
                      <AgeDays>77</AgeDays>
                      <Salary>44000</Salary>
                      <TSI>29000</TSI>
                      <PlayerForm>6</PlayerForm>
                      <Experience>4</Experience>
                      <Leadership>3</Leadership>
                      <Specialty>2</Specialty>
                      <Cards>1</Cards>
                      <InjuryLevel>-1</InjuryLevel>
                      <StaminaSkill>8</StaminaSkill>
                      <KeeperSkill>1</KeeperSkill>
                      <PlaymakerSkill>12</PlaymakerSkill>
                      <ScorerSkill>5</ScorerSkill>
                      <PassingSkill>9</PassingSkill>
                      <WingerSkill>7</WingerSkill>
                      <DefenderSkill>6</DefenderSkill>
                      <SetPiecesSkill>10</SetPiecesSkill>
                    </Details>
                  </TransferResult>
                </TransferResults>
              </TransferSearch>
            </HattrickData>
            """;

    /** A service that answers from a canned file instead of calling Hattrick. */
    static class StubService extends TransferSearchService {
        String lastQueryString;

        @Override
        protected String fetch(TransferSearchQuery query) {
            lastQueryString = query.toQueryString();
            return ONE_RESULT;
        }
    }

    private static TransferSearchQuery.TransferSearchQueryBuilder validQuery() {
        return TransferSearchQuery.builder()
                .ageMin(20).ageMax(22)
                .skillType1(TransferSearchQuery.Skill.PLAYMAKING)
                .minSkillValue1(7).maxSkillValue1(9);
    }

    @Test
    void search_parsesTheAnswer() throws Exception {
        final var service = new StubService();

        final var page = service.search(validQuery().build());

        assertThat(page.hits()).hasSize(1);
        assertThat(page.hits().get(0).playerId()).isEqualTo(111222333);
        assertThat(service.lastQueryString).contains("ageMin=20");
    }

    /** The regression guard for spec section 1.3: no Swing call, no dialog, on the failure path. */
    @Test
    void search_rejectsAnInvalidQueryWithoutTouchingSwing() {
        final var service = new StubService();
        final var invalid = validQuery().maxSkillValue1(20).build();   // span of 13 levels

        assertThatExceptionOfType(TransferSearchService.SearchFailed.class)
                .isThrownBy(() -> service.search(invalid))
                .satisfies(e -> assertThat(e.errorKey()).isEqualTo("ls.transfersearch.error.skillrange"));

        assertThat(service.lastQueryString).isNull();   // never went out
    }

    @Test
    void searchForAgent_refusedWhileMarketSearchIsLocked() {
        final var service = new StubService();

        assertThatExceptionOfType(TransferSearchService.SearchFailed.class)
                .isThrownBy(() -> service.searchForAgent(validQuery().build()))
                .withMessageContaining("locked");

        assertThat(service.lastQueryString).isNull();
    }

    @Test
    void searchForAgent_succeedsWhileUnlocked() throws Exception {
        final var service = new StubService();
        service.unlockMarketSearch();

        assertThat(service.searchForAgent(validQuery().build()).hits()).hasSize(1);
        assertThat(service.marketUnlockSecondsRemaining()).isPositive();
    }

    @Test
    void searchForAgent_refusesThe21stCallAndSaysSo() throws Exception {
        final var service = new StubService();
        service.unlockMarketSearch();
        core.model.UserParameter.instance().mcpSearchesPerSession = 20;

        for (int i = 0; i < 20; i++) {
            service.searchForAgent(validQuery().build());
        }

        assertThatExceptionOfType(TransferSearchService.SearchFailed.class)
                .isThrownBy(() -> service.searchForAgent(validQuery().build()))
                .withMessageContaining("20");
    }
}
