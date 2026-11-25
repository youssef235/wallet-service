package db.migration;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * Scopes transfer idempotency keys to the sending wallet. The old global unique
 * constraint on idempotency_key is dropped and replaced with a composite unique
 * on (from_wallet_id, idempotency_key). Written in Java because the generated
 * name of the old constraint differs between PostgreSQL and H2 (tests), so we
 * look it up through JDBC metadata instead of hardcoding it.
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

        List<String> candidates = new ArrayList<>();
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
                try {
                    st.execute("ALTER TABLE transfers DROP CONSTRAINT " + name);
                } catch (Exception ignored) {
                    // Wrong candidate name for this dialect; try the next one.
                }
            }
            st.execute("ALTER TABLE transfers ADD CONSTRAINT uq_transfer_idem"
                    + " UNIQUE (from_wallet_id, idempotency_key)");
        }
    }
}
