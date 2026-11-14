package core.file.xml;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** stafflist 1.3: ContractExpires per staff member, none on the head coach. */
class XMLStaffParserTest {

    private static final String STAFFLIST = """
            <?xml version="1.0" encoding="utf-8"?>
            <HattrickData>
              <FileName>stafflist.xml</FileName>
              <Version>1.3</Version>
              <StaffList>
                <Trainer>
                  <TrainerId>486069728</TrainerId>
                  <Name>Coach</Name>
                  <ContractDate>2026-04-06 01:29:00</ContractDate>
                  <Cost>103800</Cost>
                  <TrainerType>2</TrainerType>
                  <TrainerSkillLevel>4</TrainerSkillLevel>
                </Trainer>
                <StaffMembers>
                  <Staff>
                    <Name>Assistant</Name>
                    <StaffId>2703849</StaffId>
                    <StaffType>1</StaffType>
                    <StaffLevel>3</StaffLevel>
                    <Cost>40800</Cost>
                    <ContractExpires>2026-12-01 03:43:00</ContractExpires>
                  </Staff>
                </StaffMembers>
              </StaffList>
            </HattrickData>
            """;

    @Test
    void readsContractExpiresOfAStaffMember() {
        final var list = XMLStaffParser.parseStaffFromString(STAFFLIST);

        assertThat(list).hasSize(2);
        assertThat(list.get(0).get("ContractExpires")).isNullOrEmpty();
        assertThat(list.get(1)).containsEntry("Name", "Assistant")
                .containsEntry("ContractExpires", "2026-12-01 03:43:00");
    }
}
