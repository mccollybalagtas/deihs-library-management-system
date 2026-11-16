package com.deihs.library.db;

import com.deihs.library.dao.DaoException;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Reads db.properties from the classpath and hands out JDBC connections.
 *
 * <p>Every value can be overridden at launch time, which is how a client PC
 * points at a database hosted on another machine:</p>
 *
 * <pre>mvn javafx:run -Ddb.host=192.168.1.10 -Ddb.port=3306</pre>
 *
 * <p>Part of: Infrastructure. Owns the JDBC URL and the credentials.<br>
 * Used by: App.start() calls {@link #init()}; every DAO calls
 * {@link #getConnection()}; Schema.migrate() also calls {@link #getConnection()}.<br>
 * Key rules: {@link #init()} must run once before any connection is requested,
 * otherwise {@link #getConnection()} throws; each call returns a brand-new
 * connection that the caller must close.</p>
 */
public final class Db {

    // ============================================================
    // PART: Connection settings
    // ============================================================

    /** JDBC URL built once in init() from host, port and database name. */
    private static String url;
    private static String user;
    private static String password;
    /** False until init() has connected successfully at least once. */
    private static boolean ready;

    /** WHAT: Blocks instantiation. WHY: the class is a static holder of one configuration. */
    private Db() {
    }

    // ============================================================
    // PART: Startup
    // ============================================================

    /**
     * WHAT: Loads db.properties, builds the JDBC URL, tests the connection and
     * runs the schema migration.
     * WHY: Failing here, before any screen exists, turns a confusing blank window
     * into a clear "database unavailable" dialog.
     * CALLED BY: App.start().
     */
    public static synchronized void init() throws DaoException {
        // Step 1: read the properties file from the classpath. A missing file is
        // allowed; the built-in defaults below are then used.
        Properties props = new Properties();
        try (InputStream in = Db.class.getResourceAsStream("/db.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (Exception ex) {
            throw new DaoException("Could not read db.properties", ex);
        }

        // Step 2: resolve each setting. value() checks the -D system property
        // first, then the file, then the hard-coded default.
        String host = value(props, "db.host", "127.0.0.1");
        String port = value(props, "db.port", "3306");
        String name = value(props, "db.name", "deihs_library");
        user = value(props, "db.user", "root");
        password = value(props, "db.password", "");

        // Step 3: assemble the URL. Each parameter matters:
        // useSSL=false            - the school server has no TLS certificate;
        // allowPublicKeyRetrieval - needed for MySQL 8 password auth over plain TCP;
        // serverTimezone          - keeps NOW() and the Java clock in the same zone;
        // characterEncoding/useUnicode - so Filipino names and titles survive.
        url = "jdbc:mysql://" + host + ":" + port + "/" + name
                + "?useSSL=false"
                + "&allowPublicKeyRetrieval=true"
                + "&serverTimezone=Asia/Manila"
                + "&characterEncoding=UTF-8"
                + "&useUnicode=true";

        // Step 4: load the driver class explicitly; some launch setups do not
        // auto-register it.
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException ex) {
            throw new DaoException("MySQL Connector/J is missing from the classpath", ex);
        }

        // Step 5: prove the server answers before the UI is built.
        try (Connection c = DriverManager.getConnection(url, user, password)) {
            // isValid(4) waits up to 4 seconds for a real round-trip.
            if (!c.isValid(4)) {
                throw new DaoException("The database did not answer a validation request");
            }
        } catch (SQLException ex) {
            // The message repeats host and database name so a teacher can fix
            // the configuration without reading the stack trace.
            throw new DaoException("Cannot connect to the library database.\n\n"
                    + "Server: " + host + ":" + port + "\n"
                    + "Database: " + name + "\n"
                    + "Check that MySQL/MariaDB is running and that database/schema.sql was imported.\n\n"
                    + "Driver said: " + ex.getMessage(), ex);
        }

        ready = true;

        // Add the role/lockout/loan columns to databases created by an older
        // release. The migration is idempotent and keeps every existing row.
        Schema.migrate();
    }

    // ============================================================
    // PART: Connections
    // ============================================================

    /**
     * WHAT: Opens a new JDBC connection with the stored settings.
     * WHY: A fresh connection per call keeps the DAO code simple and avoids a
     * stale connection after the MySQL server drops an idle session.
     * CALLED BY: every method in BookDao, UserDao and Schema.
     */
    public static Connection getConnection() throws SQLException {
        // Guard against a DAO being used before App.start() ran init().
        if (!ready) {
            throw new SQLException("Database has not been initialised");
        }
        return DriverManager.getConnection(url, user, password);
    }

    // ============================================================
    // PART: Diagnostics
    // ============================================================

    /**
     * WHAT: Extracts the database name from the JDBC URL.
     * WHY: intended for a status label in the window.
     * The regex keeps the text between "//" and the next "/", which is the
     * database part of jdbc:mysql://host:port/database?params.
     */
    public static String databaseLabel() {
        return url == null ? "" : url.replaceAll(".*//([^/]+)/.*", "$1");
    }

    /**
     * WHAT: Returns the override, else the file value, else the default.
     * WHY: One lookup order means a -D flag always wins over db.properties.
     * CALLED BY: {@link #init}.
     */
    private static String value(Properties props, String key, String fallback) {
        // System properties come from the command line, e.g. -Ddb.host=...
        String override = System.getProperty(key);
        if (override != null && !override.isBlank()) {
            return override.trim();
        }
        String stored = props.getProperty(key);
        return stored == null ? fallback : stored.trim();
    }
}
