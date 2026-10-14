package com.mobilerepair.stock.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PostgresDocStoreTest {

    @Test
    void convertsPostgresSchemeToJdbc() {
        assertEquals(
                "jdbc:postgresql://host:5432/dbname?sslmode=require",
                PostgresDocStore.toJdbcUrl("postgres://user:secret@host:5432/dbname?sslmode=require"));
    }

    @Test
    void convertsPostgresqlSchemeToJdbc() {
        assertEquals(
                "jdbc:postgresql://host/dbname",
                PostgresDocStore.toJdbcUrl("postgresql://host/dbname"));
    }

    @Test
    void keepsExistingJdbcUrl() {
        assertEquals(
                "jdbc:postgresql://host:5432/dbname",
                PostgresDocStore.toJdbcUrl("jdbc:postgresql://host:5432/dbname"));
    }

    @Test
    void stripsCredentialsFromUrl() {
        assertEquals(
                "jdbc:postgresql://host/dbname",
                PostgresDocStore.toJdbcUrl("postgres://user:pa%40ss@host/dbname"));
    }

    @Test
    void rejectsBlankUrl() {
        assertThrows(DocStoreException.class, () -> PostgresDocStore.toJdbcUrl("  "));
        assertThrows(DocStoreException.class, () -> PostgresDocStore.toJdbcUrl(null));
    }
}
