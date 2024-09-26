package nl.cofx.top10.postgresql;

import lombok.extern.slf4j.Slf4j;
import org.postgresql.util.PGobject;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

@Slf4j
public class PostgreSql {

    public static PGobject toUuids(List<String> ids) {
        try {
            var pgObject = new PGobject();
            pgObject.setType("uuid[]");
            pgObject.setValue("{" + String.join(",", ids) + "}");

            return pgObject;
        } catch (SQLException exception) {
            log.error("Unable to create PgObject for UUIDs {}", ids, exception);
            throw new IllegalStateException(exception);
        }
    }

    public static PGobject toUuid(String id) {
        try {
            var pgObject = new PGobject();
            pgObject.setType("uuid");
            pgObject.setValue(id);

            return pgObject;
        } catch (SQLException exception) {
            log.error("Unable to create PgObject for UUID {}", id, exception);
            throw new IllegalStateException(exception);
        }
    }

    public static PGobject toTimestamptz(Instant instant) {
        try {
            var pgObject = new PGobject();
            pgObject.setType("timestamptz");
            pgObject.setValue(instant.toString());

            return pgObject;
        } catch (SQLException exception) {
            log.error("Unable to create PgObject for instant {}", instant, exception);
            throw new IllegalStateException(exception);
        }
    }
}
