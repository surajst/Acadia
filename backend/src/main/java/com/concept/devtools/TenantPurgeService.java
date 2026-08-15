package com.concept.devtools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.DatabaseMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Deletes every row belonging to one tenant. **Dev and test only.**
 *
 * <p>Exists so end-to-end tests that sign up a real school can clean up after
 * themselves. Without it every run leaves a permanent tenant behind, which is
 * why the lifecycle spec carries a "never point this at production" warning.
 *
 * <p>This is deliberately NOT a product feature. Deleting a tenant is the most
 * destructive thing this system can do, and this codebase has repeatedly shipped
 * cross-tenant read bugs (report cards, fee dashboard, roster search, quest
 * approval). The same mistake on a delete path erases another school rather than
 * merely leaking it. Real school offboarding needs its own design — deactivation
 * that is actually enforced, an export first, and an authorisation story — and
 * must not be built by exposing this class.
 *
 * <h2>Why it discovers tables instead of listing them</h2>
 *
 * There are 26 entities carrying a tenant id and the schema keeps growing. A
 * hand-written list rots silently: someone adds a table, forgets this class, and
 * the purge starts leaving orphans that nobody notices because the tenant looks
 * gone. So the table list comes from database metadata — anything with a
 * {@code tenant_id} column is included automatically.
 *
 * <h2>Why it retries instead of ordering</h2>
 *
 * Deleting in the wrong order trips foreign keys. Rather than encode a
 * dependency order that would also rot, this deletes what it can and retries the
 * failures until a pass makes no progress. That converges on the correct order
 * by itself and stays correct as relationships change.
 */
@Service
public class TenantPurgeService {

    private static final Logger log = LoggerFactory.getLogger(TenantPurgeService.class);

    private final JdbcTemplate jdbc;
    private final boolean devMode;

    public TenantPurgeService(JdbcTemplate jdbc, @Value("${app.dev-mode:false}") boolean devMode) {
        this.jdbc = jdbc;
        this.devMode = devMode;
    }

    /**
     * @return per-table row counts actually deleted, for the caller to assert on
     * @throws IllegalStateException outside dev-mode, or if rows survive the purge
     */
    @Transactional
    public Map<String, Integer> purge(UUID tenantId) {
        if (!devMode) {
            throw new IllegalStateException("Tenant purge is disabled outside dev-mode");
        }
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }

        String tenantTable = tenantTableName();
        List<String> scoped = tablesWithTenantColumn();
        scoped.remove(tenantTable);

        // Statement per table. Join tables such as student_parents carry no
        // tenant_id of their own, so they are reached through their foreign keys
        // instead -- without them the rows they reference cannot be deleted and
        // the purge stalls on students, parents and the class tables.
        Map<String, String> statements = new LinkedHashMap<>();
        for (String table : scoped) {
            statements.put(table, "DELETE FROM " + table + " WHERE tenant_id = ?");
        }
        for (Map.Entry<String, String> link : joinTableStatements(scoped).entrySet()) {
            statements.putIfAbsent(link.getKey(), link.getValue());
        }

        List<String> pending = new ArrayList<>(statements.keySet());
        Map<String, Integer> deleted = new LinkedHashMap<>();

        // Retry until a whole pass deletes nothing new: that is the point at
        // which either everything is gone, or what remains is genuinely stuck.
        boolean progress = true;
        while (!pending.isEmpty() && progress) {
            progress = false;
            List<String> stillFailing = new ArrayList<>();
            for (String table : pending) {
                try {
                    int rows = jdbc.update(statements.get(table), tenantId);
                    deleted.merge(table, rows, Integer::sum);
                    progress = true;
                } catch (RuntimeException e) {
                    stillFailing.add(table);
                }
            }
            pending = stillFailing;
        }

        if (!pending.isEmpty()) {
            throw new IllegalStateException(
                    "Could not purge tenant " + tenantId + "; tables still holding rows: " + pending);
        }

        // The tenant row itself goes last, and is keyed by id rather than tenant_id.
        int rows = jdbc.update("DELETE FROM " + tenantTable + " WHERE id = ?", tenantId);
        deleted.merge(tenantTable, rows, Integer::sum);

        log.info("Purged tenant {}: {} rows across {} tables", tenantId,
                deleted.values().stream().mapToInt(Integer::intValue).sum(), deleted.size());
        return deleted;
    }

    /**
     * Every table carrying a tenant id, straight from JDBC metadata so new tables
     * are covered the day they are added rather than the day someone notices.
     */
    private List<String> tablesWithTenantColumn() {
        return jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<List<String>>) conn -> {
            DatabaseMetaData meta = conn.getMetaData();
            List<String> found = new ArrayList<>();
            try (var rs = meta.getColumns(null, null, "%", "%")) {
                while (rs.next()) {
                    String column = rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT);
                    String table = rs.getString("TABLE_NAME").toLowerCase(Locale.ROOT);
                    if (column.equals("tenant_id") && !found.contains(table)) {
                        found.add(table);
                    }
                }
            }
            return found;
        });
    }

    /**
     * Tables with no tenant_id that nonetheless hold tenant data, reached through
     * a foreign key into a tenant-scoped table. Discovered rather than listed, for
     * the same reason as everything else here: a hand-maintained list goes stale
     * the first time somebody adds a join table.
     */
    private Map<String, String> joinTableStatements(List<String> scoped) {
        return jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Map<String, String>>) conn -> {
            DatabaseMetaData meta = conn.getMetaData();
            Map<String, String> found = new LinkedHashMap<>();
            List<String> allTables = new ArrayList<>();
            try (var rs = meta.getTables(null, null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    allTables.add(rs.getString("TABLE_NAME"));
                }
            }
            for (String table : allTables) {
                String lower = table.toLowerCase(Locale.ROOT);
                if (scoped.contains(lower)) {
                    continue;
                }
                try (var fks = meta.getImportedKeys(null, null, table)) {
                    while (fks.next()) {
                        String target = fks.getString("PKTABLE_NAME").toLowerCase(Locale.ROOT);
                        if (!scoped.contains(target)) {
                            continue;
                        }
                        String fkColumn = fks.getString("FKCOLUMN_NAME");
                        String pkColumn = fks.getString("PKCOLUMN_NAME");
                        found.putIfAbsent(lower, "DELETE FROM " + lower + " WHERE " + fkColumn
                                + " IN (SELECT " + pkColumn + " FROM " + target + " WHERE tenant_id = ?)");
                        break;
                    }
                }
            }
            return found;
        });
    }

    /**
     * The tenants table, found by the column that only it has. Table names are
     * pluralised by the schema ("tenants", "users", "academic_years"), and this
     * class should not carry a copy of that convention -- the first version
     * hardcoded the singular names and failed on the very first purge.
     */
    private String tenantTableName() {
        return jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<String>) conn -> {
            DatabaseMetaData meta = conn.getMetaData();
            try (var rs = meta.getColumns(null, null, "%", "%")) {
                while (rs.next()) {
                    if (rs.getString("COLUMN_NAME").equalsIgnoreCase("subdomain")) {
                        return rs.getString("TABLE_NAME").toLowerCase(Locale.ROOT);
                    }
                }
            }
            throw new IllegalStateException("Could not locate the tenants table (no subdomain column found)");
        });
    }
}
