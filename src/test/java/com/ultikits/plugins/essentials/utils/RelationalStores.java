package com.ultikits.plugins.essentials.utils;

import com.ultikits.ultitools.abstracts.data.BaseDataEntity;
import com.ultikits.ultitools.interfaces.DataOperator;
import com.ultikits.ultitools.interfaces.impl.data.sqlite.SQLiteDataOperator;

import javax.sql.DataSource;

/**
 * Builds the framework's own relational data operator over an in-memory H2 database, so a test can
 * run a real insert, update and delete through the same SQL path a server uses
 * (UltiKits/UltiEssentials#49). H2 runs in MySQL compatibility mode for the framework's backtick
 * quoting, as the framework's own tests do.
 * <p>
 * The driver is loaded by name so that a build without it on the test classpath fails the test
 * that uses it, rather than failing compilation of every test.
 */
public final class RelationalStores {

    private RelationalStores() {
    }

    /**
     * A relational data operator for {@code type} over a fresh in-memory database.
     *
     * @param type     the entity class
     * @param database a database name unique to the calling test
     * @param <T>      the entity type
     * @return the operator; its table is created by the operator itself
     * @throws Exception if the driver or the operator cannot be built
     */
    public static <T extends BaseDataEntity<String>> DataOperator<T> inMemory(Class<T> type, String database)
            throws Exception {
        Class<?> dataSourceType = Class.forName("org.h2.jdbcx.JdbcDataSource");
        DataSource dataSource = (DataSource) dataSourceType.getDeclaredConstructor().newInstance();
        dataSourceType.getMethod("setURL", String.class)
                .invoke(dataSource, "jdbc:h2:mem:" + database + ";DB_CLOSE_DELAY=-1;MODE=MySQL");
        dataSourceType.getMethod("setUser", String.class).invoke(dataSource, "sa");
        return new SQLiteDataOperator<>(dataSource, type);
    }
}
