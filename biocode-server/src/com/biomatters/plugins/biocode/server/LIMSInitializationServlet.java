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
import com.biomatters.geneious.publicapi.utilities.FileUtilities;
import com.biomatters.geneious.publicapi.utilities.FileUtilitiesImplementation;
import com.biomatters.geneious.publicapi.utilities.ImportUtilitiesImplementation;
import com.biomatters.geneious.publicapi.utilities.SequenceUtilitiesImplementation;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.PasswordOptions;
import com.biomatters.plugins.biocode.labbench.connection.Connection;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import com.biomatters.plugins.biocode.labbench.lims.LimsConnectionOptions;
import com.biomatters.plugins.biocode.labbench.lims.MySqlLIMSConnectionOptions;
import jebl.util.ProgressListener;

import javax.servlet.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

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

            connectionConfig.setFims("biocode");
            PasswordOptions fimsOptions = connectionConfig.getFimsOptions();
            fimsOptions.setValue("username", "limsuser");
            fimsOptions.setValue("password", "biomatters");

            LimsConnectionOptions parentLimsOptions = (LimsConnectionOptions)connectionConfig.getLimsOptions();
            parentLimsOptions.setValue(LimsConnectionOptions.CONNECTION_TYPE_CHOOSER, LIMSConnection.AvailableLimsTypes.remote.name());
            PasswordOptions _limsOptions = parentLimsOptions.getSelectedLIMSOptions();
            MySqlLIMSConnectionOptions limsOptions = (MySqlLIMSConnectionOptions) _limsOptions;

            limsOptions.setValue("server", config.getProperty("lims.host", "darwin.berkeley.edu"));
            //port
            limsOptions.setValue("database", config.getProperty("lims.name", "labbench"));
            limsOptions.setValue("username", config.getProperty("lims.username", "limsuser"));
            limsOptions.setValue("password", config.getProperty("lims.password", "biomatters"));

            // todo
        } catch (IOException e) {
            initializationErrors.append("Failed to load properties file from ").append(
                    connectionPropertiesFile.getAbsolutePath()).append(": ").append(e.getMessage());
        }


        try {
            biocodeeService.connect(connectionConfig, false);
        } catch (Exception e) {
            initializationErrors.append(e.getMessage());
        }
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
}
