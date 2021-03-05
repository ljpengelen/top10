package nl.cofx.top10.postgresql;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import org.postgresql.util.PGobject;

import lombok.extern.log4j.Log4j2;

@Log4j2
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
