package com.biomatters.plugins.biocode.labbench.lims;

import com.biomatters.geneious.publicapi.plugin.Geneious;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.utilities.PasswordOption;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.PasswordOptions;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Steve
 * @version $Id$
 *          <p/>
 *          Created on 17/04/13 2:25 PM
 */


public class MysqlLIMSConnection extends SqlLimsConnection {

    private String username;
    private String schema;

    @Override
    public PasswordOptions getConnectionOptions() {
        return new MySqlLIMSConnectionOptions(LIMSConnection.class);
    }

    @Override
    public DataSource connectToDb(Options LIMSOptions) throws ConnectionException {
        username = LIMSOptions.getValueAsString("username");
        DriverManager.setLoginTimeout(20);
        serverUrn = LIMSOptions.getValueAsString("server") + ":" + LIMSOptions.getValueAsString("port");
        schema = LIMSOptions.getValueAsString("database");
        String connectionString = "jdbc:mysql://" + serverUrn + "/" + schema;
        serverUrn += "/"+ schema;
        return createBasicDataSource(connectionString, username, ((PasswordOption)LIMSOptions.getOption("password")).getPassword());
    }

    /**
     * Creates a {@link org.apache.commons.dbcp.BasicDataSource} using a custom ClassLoader.
     * <p/>
     * <b>Note</b>:This method is required because there is an older version of commons-dbcp in Geneious core class loader.  This
     * means the class uses that class loader when looking for the JDBC driver class and cannot access the MySQL driver
     * bundled in the Biocode plugin.
     * <p/>
     * We are unable to make use of {@link org.apache.commons.dbcp.BasicDataSource#setDriverClassLoader(ClassLoader)}
     * because the version that is part of Geneious core is version 1.1 and does not have that method.
     * </p>
     * So we are forced to create a custom class loader that only has access to the plugin classes and libraries.
     *
     * @param connectionUrl The URL to connect to
     * @param username The username to connect with
     * @param password The password to connect with
     * @return A {@link javax.sql.DataSource} for the specified parameters.
     * @throws ConnectionException
     */
    DataSource createBasicDataSource(String connectionUrl, String username, String password) throws ConnectionException {
        ClassLoader pluginClassLoader = getClass().getClassLoader();
        if(pluginClassLoader instanceof URLClassLoader) {
            List<URL> urlsOfJar = new ArrayList<URL>();
            for (URL url : ((URLClassLoader) pluginClassLoader).getURLs()) {
                if(url.toString().contains("biocode")) {
                    urlsOfJar.add(url);
                    System.out.println(url.toString());
                }
            }
            URLClassLoader classLoaderForPluginLibsOnly = new URLClassLoader(urlsOfJar.toArray(new URL[1]), ClassLoader.getSystemClassLoader().getParent());
            try {
                Class<?> dataSourceClass = classLoaderForPluginLibsOnly.loadClass("org.apache.commons.dbcp.BasicDataSource");
                DataSource dataSource = (DataSource)dataSourceClass.newInstance();

                Driver driver = BiocodeService.getInstance().getDriver();
//                dataSource.setDriverClassName(driver.getClass().getName());
                dataSourceClass.getDeclaredMethod("setDriverClassName", String.class).invoke(dataSource, driver.getClass().getName());
//                dataSource.setDriverClassLoader(pluginClassLoader);
//                dataSourceClass.getDeclaredMethod("setDriverClassLoader", ClassLoader.class).invoke(dataSource, pluginClassLoader);

//                dataSource.setUsername(username);
                dataSourceClass.getDeclaredMethod("setUsername", String.class).invoke(dataSource, username);
//                dataSource.setPassword();
                dataSourceClass.getDeclaredMethod("setPassword", String.class).invoke(dataSource, password);
//                dataSource.setUrl();
                dataSourceClass.getDeclaredMethod("setUrl", String.class).invoke(dataSource, connectionUrl);


                if(Geneious.isHeadless()) {
//                    dataSource.setMaxActive(25);
                    dataSourceClass.getDeclaredMethod("setMaxActive", Integer.class).invoke(dataSource, 25);
                }


                return dataSource;
            } catch (ClassNotFoundException e) {
                throw new ConnectionException("Failed to load data source. Missing library.", e);
            } catch (InstantiationException e) {
                throw new ConnectionException("Cannot construct BasicDataSource", e);
            } catch (IllegalAccessException e) {
                throw new ConnectionException("Failed to load data source.", e);
            } catch (NoSuchMethodException e) {
                throw new ConnectionException("Failed to load data source.", e);
            } catch (InvocationTargetException e) {
                throw new ConnectionException("Failed to load data source.", e); // todo all exceptions
            }
        } else {
            throw new IllegalStateException("Expected plugin class loader to be a URLClassLoader, was " + pluginClassLoader.getClass().getSimpleName());
        }
    }


    @Override
    protected boolean canUpgradeDatabase() {
        return false;
    }

    @Override
    public boolean isLocal() {
        return false;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getSchema() {
        return schema;
    }
}
