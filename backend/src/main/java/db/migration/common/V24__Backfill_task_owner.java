package db.migration.common;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fills in {@code teacher_tasks.created_by_user_id} from the hashed owner column
 * beside it.
 *
 * <p>Java rather than SQL because the old column holds
 * {@code UUID.nameUUIDFromBytes(email)} -- an MD5-based version 3 UUID. Computing
 * one in SQL means hashing the address and then patching the version and variant
 * nibbles by hand, in a dialect where getting them wrong produces a valid-looking
 * UUID that matches nothing. Here it is one library call that cannot drift from
 * the one the application makes.
 *
 * <h2>It matches; it never guesses</h2>
 *
 * <p>Two rules, both exact:
 *
 * <ol>
 *   <li>the old value equals {@code nameUUIDFromBytes} of a user's current email
 *       -- what {@code TeacherTaskService} has always written;</li>
 *   <li>the old value <em>is</em> a user's id -- what {@code ScreenContentSeeder}
 *       writes, and the reason seeded demo tasks never appeared in the teacher's
 *       own task list: one writer stored the hash, the other the real id, and the
 *       reader only ever looked for the hash.</li>
 * </ol>
 *
 * <p>Anything matching neither keeps a null and is logged, row by row, at WARN.
 * Those are the rows a teacher has already lost -- V15 lowercased stored
 * addresses, so a task set before it by someone recorded in mixed case hashes to
 * an address that no longer exists. There is no honest way to recover the owner
 * of such a row from the data: the hash is one-way, and the address it was made
 * from is gone. Assigning them to a plausible teacher would be inventing a fact
 * about who set a piece of work, so they stay ownerless and visible instead.
 *
 * <p>Idempotent: only rows with a null {@code created_by_user_id} are touched, so
 * a repeat run is a no-op. Flyway will not repeat it anyway, but a migration that
 * depends on that is one bad {@code repair} away from trouble.
 */
public class V24__Backfill_task_owner extends BaseJavaMigration {

    private static final Logger log = LoggerFactory.getLogger(V24__Backfill_task_owner.class);

    @Override
    public void migrate(Context context) throws Exception {
        Map<UUID, UUID> ownerByOldId = new HashMap<>();

        try (Statement read = context.getConnection().createStatement();
             ResultSet users = read.executeQuery("select id, email from users")) {
            while (users.next()) {
                String rawId = users.getString("id");
                String email = users.getString("email");
                if (rawId == null) {
                    continue;
                }
                UUID userId = UUID.fromString(rawId);
                // Rule 2: the column already holds a real user id.
                ownerByOldId.put(userId, userId);
                if (email != null && !email.isBlank()) {
                    // Rule 1: the hash the application writes. Put second so that a
                    // real id always wins a collision, which cannot happen in
                    // practice but should not be left to chance if it did.
                    ownerByOldId.putIfAbsent(UUID.nameUUIDFromBytes(email.getBytes()), userId);
                }
            }
        }

        List<String> unmatched = new ArrayList<>();
        int matched = 0;

        try (Statement read = context.getConnection().createStatement();
             ResultSet tasks = read.executeQuery(
                     "select id, created_by_teacher_id, title, tenant_id, created_at "
                             + "from teacher_tasks where created_by_user_id is null");
             PreparedStatement write = context.getConnection().prepareStatement(
                     "update teacher_tasks set created_by_user_id = ? where id = ?")) {

            while (tasks.next()) {
                String oldId = tasks.getString("created_by_teacher_id");
                UUID owner = oldId == null ? null : ownerByOldId.get(UUID.fromString(oldId));
                if (owner == null) {
                    unmatched.add(String.format("  task %s  %-40.40s  tenant=%s  set=%s  owner_hash=%s",
                            tasks.getString("id"),
                            String.valueOf(tasks.getString("title")),
                            tasks.getString("tenant_id"),
                            tasks.getString("created_at"),
                            oldId));
                    continue;
                }
                write.setObject(1, owner);
                write.setObject(2, UUID.fromString(tasks.getString("id")));
                write.addBatch();
                matched++;
            }
            write.executeBatch();
        }

        log.info("V24 task owner backfill: {} matched to a user, {} left ownerless",
                matched, unmatched.size());
        if (!unmatched.isEmpty()) {
            // Row by row, not a count. A count tells somebody there is a problem;
            // the list tells them which pieces of work a teacher can no longer
            // close, edit or delete, so they can be reassigned by hand or closed.
            log.warn("V24 task owner backfill: {} task(s) match no current user's email "
                    + "and have been left ownerless. Their owner cannot be recovered -- the "
                    + "stored value is a one-way hash of an address that no longer exists "
                    + "(V15 lowercased stored addresses). An admin can still manage them.\n{}",
                    unmatched.size(), String.join("\n", unmatched));
        }
    }
}
