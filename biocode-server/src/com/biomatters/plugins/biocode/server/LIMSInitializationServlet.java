package com.biomatters.plugins.biocode.server;

import com.biomatters.geneious.privateApi.PrivateApiUtilities;
import com.biomatters.geneious.privateApi.PrivateApiUtilitiesImplementation;
import com.biomatters.geneious.publicapi.components.ComponentUtilitiesImplementation;
import com.biomatters.geneious.publicapi.databaseservice.QueryFactoryImplementation;
import com.biomatters.geneious.publicapi.databaseservice.WritableDatabaseServiceActions;
import com.biomatters.geneious.publicapi.documents.DocumentUtilitiesImplementation;
import com.biomatters.geneious.publicapi.documents.NoteTypeStorage;
import com.biomatters.geneious.publicapi.documents.XMLSerializerImplementation;
import com.biomatters.geneious.publicapi.implementations.ImportedFileOriginalTextImplementation;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.geneious.publicapi.utilities.*;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.PasswordOptions;
import com.biomatters.plugins.biocode.labbench.connection.Connection;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import com.biomatters.plugins.biocode.labbench.lims.LimsConnectionOptions;
import com.biomatters.plugins.biocode.labbench.lims.LocalLIMSConnectionOptions;
import com.biomatters.plugins.biocode.labbench.lims.MySqlLIMSConnectionOptions;
import com.sun.xml.rpc.processor.config.Configuration;
import jebl.util.ProgressListener;

import javax.servlet.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

/**
 * Responsible for making the various LIMS connections on start up
 *
 * @author Matthew Cheung
 * @version $Id$
 *          <p/>
 *          Created on 20/03/14 3:18 PM
 */
public class LIMSInitializationServlet extends GenericServlet {

    private String settingsFolderName = ".biocode-lims";
    private String defaultPropertiesFile = "default_connection.properties";
    private String propertiesFile = "connection.properties";

    @Override
    public void service(ServletRequest servletRequest, ServletResponse servletResponse) throws ServletException, IOException {
        // Ignore any requests.  There shouldn't be any connections made to this servlet
    }

    @Override
    public void init() throws ServletException {
        initializeGeneiousUtilities();

        BiocodeService biocodeeService = null;
        Connection connectionConfig = null;
        File settingsFolder = new File(System.getProperty("user.home"), settingsFolderName);
        File connectionPropertiesFile = new File(settingsFolder, propertiesFile);
        if(!connectionPropertiesFile.exists()) {
            File defaultFile = new File(getWarDirectory(), defaultPropertiesFile);
            if(defaultFile.exists()) {
                try {
                    FileUtilities.copyFile(defaultFile, connectionPropertiesFile, FileUtilities.TargetExistsAction.Skip, ProgressListener.EMPTY);
                } catch (IOException e) {
                    initializationErrors.append("Failed to copy default properties to: ").append(
                            connectionPropertiesFile.getAbsolutePath()).append(": ").append(e.getMessage());
                }
            }
        }

        try {
            Properties config = new Properties();
            config.load(new FileInputStream(connectionPropertiesFile));

            biocodeeService = BiocodeService.getInstance();
            File dataDir = new File(settingsFolderName, "data");
            if(!dataDir.exists()) {
                dataDir.mkdirs();
            }
            biocodeeService.setDataDirectory(dataDir);

            connectionConfig = new Connection("forServer");

            setFimsOptionsFromConfigFile(connectionConfig, config);

            setLimsOptionsFromConfigFile(connectionConfig, config);

            biocodeeService.connect(connectionConfig, false);
        } catch (IOException e) {
            initializationErrors.append("Failed to load properties file from ").append(
                    connectionPropertiesFile.getAbsolutePath()).append(": ").append(e.getMessage());
        } catch(MissingPropertyException e) {
            initializationErrors.append(e.getMessage()).append(" in configuration file (").append(
                    connectionPropertiesFile.getAbsolutePath()).append(")");
        } catch (Exception e) {
            initializationErrors.append(e.getMessage());
        }
    }

    private void setLimsOptionsFromConfigFile(Connection connectionConfig, Properties config) throws ConfigurationException {
        LimsConnectionOptions parentLimsOptions = (LimsConnectionOptions)connectionConfig.getLimsOptions();
        String limsType = config.getProperty("lims.type");
        if(limsType == null) {
            throw new MissingPropertyException("lims.type");
        }
        parentLimsOptions.setValue(LimsConnectionOptions.CONNECTION_TYPE_CHOOSER, limsType);
        PasswordOptions _limsOptions = parentLimsOptions.getSelectedLIMSOptions();

        if(limsType.equals(LIMSConnection.AvailableLimsTypes.remote.name())) {
            MySqlLIMSConnectionOptions limsOptions = (MySqlLIMSConnectionOptions) _limsOptions;

            List<String> missing = new ArrayList<String>();
            for (String optionName : new String[]{"host", "port", "name", "username", "password"}) {
                String propertyKey = "lims." + optionName;
                String value = config.getProperty(propertyKey);
                if(value == null) {
                    missing.add(propertyKey);
                } else {
                    limsOptions.setValue(optionName, value);
                }
            }
            if(!missing.isEmpty()) {
                throw new MissingPropertyException(missing.toArray(new String[missing.size()]));
            }
        } else if(limsType.equals(LIMSConnection.AvailableLimsTypes.local.name())) {
            LocalLIMSConnectionOptions options = (LocalLIMSConnectionOptions) _limsOptions;
            String name = config.getProperty("lims.name", "BiocodeLIMS");  // Use a default
            options.setValue(LocalLIMSConnectionOptions.DATABASE, name);
        } else {
            throw new ConfigurationException("Invalid lims.type: " + limsType);
        }
    }

    private void setFimsOptionsFromConfigFile(Connection connectionConfig, Properties config) throws ConfigurationException {
        connectionConfig.setFims("biocode");
        PasswordOptions fimsOptions = connectionConfig.getFimsOptions();
        String username = config.getProperty("fims.username");
        String password = config.getProperty("fims.password");
        if(username == null || password == null) {
            throw new MissingPropertyException("fims.username", "fims.password");
        }

        fimsOptions.setValue("username", username);
        fimsOptions.setValue("password", password);
    }

    /**
     * Initializes parts of the Geneious core runtime.  As the Biocode LIMS plugin was originally written as a Geneious
     * plugin a lot of the code is tightly coupled with classes such as {@link com.biomatters.geneious.publicapi.databaseservice.Query} and
     * {@link com.biomatters.geneious.publicapi.plugin.Options}.  So we must initialize the runtime to make use of these
     * classes within the server.
     */
    private void initializeGeneiousUtilities() {
        PrivateApiUtilities.setRunningFromServlet(true);
        HeadlessOperationUtilities.setHeadless(true);
        TestGeneious.setNotRunningTest();
        TestGeneious.setRunningApplication();

        XMLSerializerImplementation.setImplementation();
        ImportedFileOriginalTextImplementation.setImplementation();

        DocumentUtilitiesImplementation.setImplementation();
        PrivateApiUtilitiesImplementation.setImplementation();

        if (NoteTypeStorage.getNoteTypeStorage()==null)
            NoteTypeStorage.setImplementation ();
        ExtendedPrintableFactoryImplementation.initialise();

        PluginUtilitiesImplementation.setImplementation();
        FileUtilitiesImplementation.setImplementation();
        SequenceUtilitiesImplementation.setImplementation();
        ImportUtilitiesImplementation.setImplementation();
        QueryFactoryImplementation.setImplementation();
        ComponentUtilitiesImplementation.setImplementation();
        PrivateUtilitiesImplementation.setImplementation();
        WritableDatabaseServiceActions.setImplemtation();

    }

    private static StringBuilder initializationErrors = new StringBuilder();
    public static String getErrors() {
        return initializationErrors.toString();
    }


    public File getWarDirectory() {
        ServletContext context = getServletContext();
        File warDir = new File(context.getRealPath("."));
        if(warDir.isDirectory()) {
            return warDir;
        } else {
            throw new IllegalStateException("Context directory does not exist!");
        }
    }

    private class ConfigurationException extends Exception {
        private ConfigurationException(String message) {
            super(message);
        }

        private ConfigurationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private class MissingPropertyException extends ConfigurationException {
        String[] missingValues;
        private MissingPropertyException(String... missing) {
            super("Must specify " + StringUtilities.humanJoin(Arrays.asList(missing)));
            missingValues = missing;
        }
    }
}
