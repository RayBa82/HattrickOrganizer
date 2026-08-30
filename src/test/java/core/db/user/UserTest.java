package core.db.user;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    /**
     * A relative database url would put the database next to the working directory of the process
     * instead of into the HO folder the rest of the application uses.
     */
    @Test
    void dbUrl_pointsAtTheDbFolder() {
        final var user = new User("user", "db", 3, false);

        assertThat(user.getDbFolder()).startsWith(UserManager.instance().getDbParentFolder());
        assertThat(user.getDbURL()).isEqualTo("jdbc:hsqldb:file:" + user.getDbFolder() + "/database");
    }
}
