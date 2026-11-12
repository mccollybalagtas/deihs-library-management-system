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
 */
public final class Db {

    private static String url;
    private static String user;
    private static String password;
    private static boolean ready;

    private Db() {
    }

    public static synchronized void init() throws DaoException {
        Properties props = new Properties();
        try (InputStream in = Db.class.getResourceAsStream("/db.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (Exception ex) {
            throw new DaoException("Could not read db.properties", ex);
        }

        String host = value(props, "db.host", "127.0.0.1");
        String port = value(props, "db.port", "3306");
        String name = value(props, "db.name", "deihs_library");
        user = value(props, "db.user", "root");
        password = value(props, "db.password", "");

        url = "jdbc:mysql://" + host + ":" + port + "/" + name
                + "?useSSL=false"
                + "&allowPublicKeyRetrieval=true"
                + "&serverTimezone=Asia/Manila"
                + "&characterEncoding=UTF-8"
                + "&useUnicode=true";

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException ex) {
            throw new DaoException("MySQL Connector/J is missing from the classpath", ex);
        }

        try (Connection c = DriverManager.getConnection(url, user, password)) {
            if (!c.isValid(4)) {
                throw new DaoException("The database did not answer a validation request");
            }
        } catch (SQLException ex) {
            throw new DaoException("Cannot connect to the library database.\n\n"
                    + "Server: " + host + ":" + port + "\n"
                    + "Database: " + name + "\n"
                    + "Check that MySQL/MariaDB is running and that database/schema.sql was imported.\n\n"
                    + "Driver said: " + ex.getMessage(), ex);
        }

        ready = true;
    }

    public static Connection getConnection() throws SQLException {
        if (!ready) {
            throw new SQLException("Database has not been initialised");
        }
        return DriverManager.getConnection(url, user, password);
    }

    public static String databaseLabel() {
        return url == null ? "" : url.replaceAll(".*//([^/]+)/.*", "$1");
    }

    private static String value(Properties props, String key, String fallback) {
        String override = System.getProperty(key);
        if (override != null && !override.isBlank()) {
            return override.trim();
        }
        String stored = props.getProperty(key);
        return stored == null ? fallback : stored.trim();
    }
}
