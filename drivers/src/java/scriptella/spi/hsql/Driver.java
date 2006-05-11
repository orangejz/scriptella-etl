/*
 * Copyright 2006 The Scriptella Project Team.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package scriptella.spi.hsql;

import scriptella.jdbc.JDBCConnection;
import scriptella.jdbc.ScriptellaJDBCDriver;
import scriptella.spi.ConnectionParameters;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Scriptella Adapter for HSLQDB database.
 * <p>The primary feature of this driver is {@link #SHUTDOWN_ON_EXIT}.
 *
 * @author Fyodor Kupolov
 * @version 1.0
 */
public class Driver extends ScriptellaJDBCDriver {
    private static final Logger LOG = Logger.getLogger(Driver.class.getName());
    public static final String HSQLDB_DRIVER_NAME = "org.hsqldb.jdbcDriver";
    /**
     * True if SHUTDOWN command should be executed before last connection closed. Default value is true.
     * In 1.7.2, in-process databases are no longer closed when the last connection to the database
     * is explicitly closed via JDBC, a SHUTDOWN is required
     */
    public static final String SHUTDOWN_ON_EXIT = "hsql.shutdown_on_exit";

    private static HsqlConnection lastConnection; //Send SHUTDOWN on JVM exit to fix
    private static boolean hookAdded = false;


    static final Thread HOOK = new Thread("Scriptella HSLQDB Shutdown Fix") {
        public void run() {
            if (lastConnection != null) {
                try {
                    lastConnection.shutdown();
                    lastConnection = null;
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Problem occured while trying to shutdown in-process HSQLDB database", e);
                }
            }

        }
    };

    static {
        try {
            Class.forName(HSQLDB_DRIVER_NAME);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(HSQLDB_DRIVER_NAME + " driver not found. Please check class path settings");
        }
    }

    @Override
    protected JDBCConnection connect(ConnectionParameters parameters, Properties props) throws SQLException {
        boolean shutdownOnExit = Boolean.parseBoolean(props.getProperty(SHUTDOWN_ON_EXIT))
                && isInprocess(parameters.getUrl());
        return new HsqlConnection(DriverManager.getConnection(parameters.getUrl(), props), shutdownOnExit);
    }

    private boolean isInprocess(String url) {
        //Returning false for server modes
        if (url.startsWith("jdbc:hsqldb:http:")) {
            return false;
        }
        if (url.startsWith("jdbc:hsqldb:https:")) {
            return false;
        }
        if (url.startsWith("jdbc:hsqldb:hsql")) {
            return false;
        }
        return !url.startsWith("jdbc:hsqldb:hsqls");
    }

    /**
     * Sets last connection and returns previous value of lastConnection field.
     *
     * @param connection last connection
     * @return previous value of lastConnection field.Null if no connections have been registered.
     */
    static synchronized HsqlConnection setLastConnection(HsqlConnection connection) {
        HsqlConnection old = lastConnection;
        lastConnection = connection;
        if (!hookAdded) {
            Runtime.getRuntime().addShutdownHook(HOOK);
            hookAdded = true;
        }
        return old;
    }


}
