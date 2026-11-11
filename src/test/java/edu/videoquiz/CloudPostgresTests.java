package edu.videoquiz;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.*;

/** Same persistence contracts against a disposable PostgreSQL CI service. Never point at real user data. */
@EnabledIfEnvironmentVariable(named="VIDEOQUIZ_TEST_POSTGRES_URL",matches="jdbc:postgresql://localhost:5432/videoquiz\\?user=videoquiz&password=ci-fixture-only")
class CloudPostgresTests extends CloudStoreTests {
    @BeforeEach void freshDatabase() throws Exception {
        url=System.getenv("VIDEOQUIZ_TEST_POSTGRES_URL");
        try(var connection=DriverManager.getConnection(url);var statement=connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS videoquiz_private CASCADE");
        }
    }
}
