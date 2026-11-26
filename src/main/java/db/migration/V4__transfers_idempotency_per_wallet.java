package db.migration;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * Scopes transfer idempotency keys to the sending wallet. The old global unique
 * constraint on idempotency_key is dropped and replaced with a composite unique
 * on (from_wallet_id, idempotency_key). Written in Java because the generated
 * name of the old constraint differs between PostgreSQL and H2 (tests), so we
 * look it up through JDBC metadata instead of hardcoding it.
 *
 * Two PostgreSQL-specific traps, both verified against a real Postgres:
 * - the same constraint name arrives from both lookup sources, so candidates
 *   must be deduplicated (a Set), and
 * - a failed statement aborts the whole transaction, so try/catch around a
 *   wrong candidate cannot work; every drop uses DROP CONSTRAINT IF EXISTS.
 */
public class V4__transfers_idempotency_per_wallet extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        DatabaseMetaData meta = connection.getMetaData();

        // Unique indexes on transfers, grouped by index name.
        Map<String, List<String>> uniqueIndexes = new LinkedHashMap<>();
        try (ResultSet rs = meta.getIndexInfo(null, null, "transfers", true, false)) {
            while (rs.next()) {
                String indexName = rs.getString("INDEX_NAME");
                String column = rs.getString("COLUMN_NAME");
                if (indexName != null && column != null) {
                    uniqueIndexes.computeIfAbsent(indexName, k -> new ArrayList<>())
                            .add(column.toLowerCase());
                }
            }
        }

        Set<String> candidates = new LinkedHashSet<>();
        for (var entry : uniqueIndexes.entrySet()) {
            if (entry.getValue().equals(List.of("idempotency_key"))) {
                candidates.add(entry.getKey()); // PostgreSQL: constraint name == index name
            }
        }
        // H2 names the constraint differently from its backing index.
        try (Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS"
                                + " WHERE TABLE_NAME = 'transfers' AND CONSTRAINT_TYPE = 'UNIQUE'")) {
            while (rs.next()) {
                candidates.add(rs.getString(1));
            }
        }

        try (Statement st = connection.createStatement()) {
            for (String name : candidates) {
                // IF EXISTS, never try/catch: on PostgreSQL a failed statement
                // aborts the transaction and every later statement is rejected.
                st.execute("ALTER TABLE transfers DROP CONSTRAINT IF EXISTS " + name);
            }
            st.execute("ALTER TABLE transfers ADD CONSTRAINT uq_transfer_idem"
                    + " UNIQUE (from_wallet_id, idempotency_key)");
        }
    }
}
