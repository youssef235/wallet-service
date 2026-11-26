package com.wallet;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wallet.support.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class LedgerAppendOnlyIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    /** Inserts a balanced pair (+1/-1) so the ledger invariants stay intact. */
    private long insertBalancedPair() {
        String journal = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO ledger_entries (journal_id, account, amount_minor, description, created_at)"
                + " VALUES (?, 'TEST:A', 1, 'test', now())", journal);
        jdbc.update("INSERT INTO ledger_entries (journal_id, account, amount_minor, description, created_at)"
                + " VALUES (?, 'TEST:B', -1, 'test', now())", journal);
        return jdbc.queryForObject(
                "SELECT id FROM ledger_entries WHERE journal_id = ? AND account = 'TEST:A'", Long.class, journal);
    }

    @Test
    void updateIsRejected() {
        long id = insertBalancedPair();
        assertThatThrownBy(() -> jdbc.update("UPDATE ledger_entries SET amount_minor = 999 WHERE id = ?", id))
                .hasMessageContaining("append-only");
    }

    @Test
    void deleteIsRejected() {
        long id = insertBalancedPair();
        assertThatThrownBy(() -> jdbc.update("DELETE FROM ledger_entries WHERE id = ?", id))
                .hasMessageContaining("append-only");
    }

    @Test
    void truncateIsRejected() {
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE ledger_entries"))
                .hasMessageContaining("append-only");
    }
}
