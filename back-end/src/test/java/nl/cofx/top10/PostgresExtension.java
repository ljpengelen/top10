package nl.cofx.top10;

import org.junit.jupiter.api.extension.Extension;
import org.testcontainers.containers.PostgreSQLContainer;

public class PostgresExtension implements Extension {

    private static final PostgreSQLContainer<?> POSTGRES_CONTAINER;

    public static final String JDBC_URL;
    public static final String USERNAME;
    public static final String PASSWORD;

    static {
        POSTGRES_CONTAINER = new PostgreSQLContainer<>("postgres:11.6");
        POSTGRES_CONTAINER.start();

        JDBC_URL = POSTGRES_CONTAINER.getJdbcUrl();
        USERNAME = POSTGRES_CONTAINER.getUsername();
        PASSWORD = POSTGRES_CONTAINER.getPassword();
    }
}
