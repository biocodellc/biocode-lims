package com.biomatters.plugins.moorea;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.databaseservice.*;
import com.biomatters.geneious.publicapi.documents.*;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideSequenceDocument;
import com.biomatters.geneious.publicapi.plugin.GeneiousAction;
import com.biomatters.geneious.publicapi.plugin.Icons;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.utilities.IconUtilities;
import com.biomatters.plugins.moorea.fims.FIMSConnection;
import com.biomatters.plugins.moorea.fims.GeneiousFimsConnection;
import com.biomatters.plugins.moorea.fims.MooreaFimsConnection;
import com.biomatters.plugins.moorea.fims.TAPIRFimsConnection;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;

import java.awt.event.ActionEvent;
import java.awt.*;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.*;
import java.util.List;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 23/02/2009 4:41:26 PM
 */
public class MooreaLabBenchService extends DatabaseService {
    public boolean isLoggedIn = false;
    private String username = null;
    private String password = null;
    private FIMSConnection activeFIMSConnection;
    private String loggedOutMessage = "Right click on the " + getName() + " service in the service tree to log in.";
    static Driver driver;
    public static final Map<String, Image> imageCache = new HashMap<String, Image>();

    @Override
    public ExtendedSearchOption[] getExtendedSearchOptions(boolean isAdvancedSearch) {
        return new ExtendedSearchOption[] {
                new CheckboxSearchOption("tissueDocuments", "Return tissue sample records", true),
                new CheckboxSearchOption("workflowDocuments", "Return workflow records", true),
                new CheckboxSearchOption("plateDocuments", "Return plate records", true),
        };
    }

    private static FIMSConnection[] getFimsConnections() {
        return new FIMSConnection[] {
                new GeneiousFimsConnection(),
                new MooreaFimsConnection(),
                new TAPIRFimsConnection()
        };
    }

    public static Class getDriverClass() {
        return driver.getClass();
    }

    public static Driver getDriver() {
        return driver;
    }


    public QueryField[] getSearchFields() {
        List<QueryField> fieldList = new ArrayList<QueryField>();

        QueryField[] limsFields = {
                new QueryField(new DocumentField("Experiment ID", "", "ExperimentId", String.class, true, false), new Condition[]{Condition.EQUAL, Condition.APPROXIMATELY_EQUAL})
        };
        fieldList.addAll(Arrays.asList(limsFields));

        if(activeFIMSConnection != null) {
            List<DocumentField> fimsAttributes = activeFIMSConnection.getFimsAttributes();
            if(fimsAttributes != null) {
                for(DocumentField field : fimsAttributes) {
                    Condition[] conditions = getFieldConditions(field.getValueType());
                    fieldList.add(new QueryField(field, conditions));
                }
            }
        }


        return fieldList.toArray(new QueryField[fieldList.size()]);
    }

    private Condition[] getFieldConditions(Class fieldClass) {
        if(Integer.class.equals(fieldClass) || Double.class.equals(fieldClass)) {
            return new Condition[] {
                    Condition.EQUAL,
                    Condition.NOT_EQUAL,
                    Condition.GREATER_THAN,
                    Condition.GREATER_THAN_OR_EQUAL_TO,
                    Condition.LESS_THAN,
                    Condition.LESS_THAN_OR_EQUAL_TO
            };
        }
        else if(String.class.equals(fieldClass)) {
            return new Condition[] {
                    Condition.EQUAL,
                    Condition.APPROXIMATELY_EQUAL,
                    Condition.CONTAINS,
                    Condition.NOT_EQUAL,
                    Condition.NOT_CONTAINS,
                    Condition.STRING_LENGTH_GREATER_THAN,
                    Condition.STRING_LENGTH_GREATER_THAN,
                    Condition.BEGINS_WITH,
                    Condition.ENDS_WITH
            };
        }
        else {
            return new Condition[] {
                    Condition.EQUAL,
                    Condition.NOT_EQUAL
            };
        }
    }

    @Override
    public List<GeneiousAction> getActionsAlwaysEnabled() {
        List<GeneiousAction> actions = new ArrayList<GeneiousAction>();

        if(isLoggedIn) {
            actions.add(new GeneiousAction("logout", "Log out"){
            public void actionPerformed(ActionEvent e) {
                    username = password = null;
                    isLoggedIn = false;
                    updateStatus();
                }
            }.setInPopupMenu(true));
        }
        else {
            actions.add(new GeneiousAction("login", "Log in"){
                public void actionPerformed(ActionEvent e) {
                    Options LIMSOptions = new Options(this.getClass());
                    Options FIMSOptions = new Options(this.getClass());
                    for(FIMSConnection connection : getFimsConnections()) {
                        FIMSOptions.addChildOptions(connection.getName(), connection.getLabel(), connection.getDescription(), connection.getConnectionOptions() != null ? connection.getConnectionOptions() : new Options(this.getClass()));
                    }
                    FIMSOptions.addChildOptionsPageChooser("fims", "Field Database Connection", Collections.EMPTY_LIST, Options.PageChooserType.COMBO_BOX, false);

                    LIMSOptions.addStringOption("server", "Server Address", "");
                    LIMSOptions.addIntegerOption("port", "Port", 3306, 1, Integer.MAX_VALUE);
                    LIMSOptions.addStringOption("database", "Database Name", "labbench");
                    LIMSOptions.addStringOption("username", "Username", "");
                    LIMSOptions.addCustomOption(new PasswordOption("password", "Password", ""));
                    LIMSOptions.addFileSelectionOption("driver", "MySQL Driver", "", new String[0], "Browse...", new FilenameFilter(){
                        public boolean accept(File dir, String name) {
                            return name.toLowerCase().endsWith(".jar");
                        }
                    });

                    Options loginOptions = new Options(this.getClass());
                    loginOptions.addChildOptions("fims", "", "", FIMSOptions);
                    loginOptions.addChildOptions("lims", "Lab-bench login", "", LIMSOptions);
                    loginOptions.restorePreferences();

                    if(Dialogs.showOkCancelDialog(loginOptions.getPanel(), "Log in", null)) {
                        loginOptions.savePreferences();
                        //load the connection driver
                        String driverFileName = (String)LIMSOptions.getValue("driver");

                        ClassLoader loader;
                        try {
                            URL driverUrl = new File(driverFileName).toURL();
                            loader = new URLClassLoader(new URL[]{driverUrl}, getClass().getClassLoader());
                        } catch (MalformedURLException ex) {
                            Dialogs.showMessageDialog("Could not load the MySql Driver!");
                            return;
                        }

                        String error = null;

                        try {
                            Class driverClass = loader.loadClass("com.mysql.jdbc.Driver");
                            driver = (Driver)driverClass.newInstance();
                        } catch (ClassNotFoundException e1) {
                            error = "Could not find driver class";
                        } catch (IllegalAccessException e1) {
                            error = "Could not access driver class";
                        } catch (InstantiationException e1) {
                            error = "Could not instantiate driver class";
                        } catch (ClassCastException e1) {
                            error = "Driver class exists, but is not an SQL driver";
                        }

                        if(error != null) {
                            Dialogs.showMessageDialog(error);
                            return;
                        }

                        //connect to the LIMS
                        Properties properties = new Properties();
                        properties.put("user", LIMSOptions.getValueAsString("username"));
                        properties.put("password", ((PasswordOption)LIMSOptions.getOption("password")).getPassword());
                        try {
                            driver.connect("jdbc:mysql://"+LIMSOptions.getValueAsString("server")+":"+LIMSOptions.getValueAsString("port"), properties);
                        } catch (SQLException e1) {
                            Dialogs.showMessageDialog("Failed to connect to the LIMS database: "+e1.getMessage());
                            return;
                        }


                        //get the selected fims service.
                        String selectedFimsServiceName = FIMSOptions.getValueAsString("fims");
                        Options selectedFimsOptions = FIMSOptions.getChildOptions().get(selectedFimsServiceName);
                        for(FIMSConnection connection : getFimsConnections()) {
                            if(connection.getName().equals(selectedFimsServiceName)) {
                                activeFIMSConnection = connection;
                            }
                        }
                        if(activeFIMSConnection == null) {
                            throw new RuntimeException("Could not find a FIMS connection called "+selectedFimsServiceName);
                        }


                        //try to connect to the selected service
                        try {
                            activeFIMSConnection.connect(selectedFimsOptions);
                            isLoggedIn = true;
                        }
                        catch(ConnectionException ex) {
                            Dialogs.showMessageDialog("Could not connect to "+activeFIMSConnection.getLabel());
                            activeFIMSConnection = null;
                            isLoggedIn = false;
                        }
                        updateStatus();
                        
                    }


                }
            }.setInPopupMenu(true));
        }

        return actions;
    }

    public void updateStatus() {
        for(DatabaseServiceListener listener : getDatabaseServiceListeners()) {
            listener.searchableStatusChanged(isLoggedIn, isLoggedIn ? "Logged in" : loggedOutMessage);
            listener.extendedSearchOptionsChanged();
            listener.fieldsChanged();
            listener.actionsChanged();
        }
    }

    @Override
    public void addDatabaseServiceListener(DatabaseServiceListener listener) {
        super.addDatabaseServiceListener(listener);
        listener.searchableStatusChanged(isLoggedIn, loggedOutMessage);
    }

    public void retrieve(Query query, RetrieveCallback callback, URN[] urnsToNotRetrieve) throws DatabaseServiceException {
        boolean allLims = false;
        List<FimsSample> tissueSamples = null;
        List<Query> fimsQueries = new ArrayList<Query>();
        List<Query> limsQueries = new ArrayList<Query>();


        if(query instanceof CompoundSearchQuery) {
            CompoundSearchQuery masterQuery = (CompoundSearchQuery) query;
            for(Query childQuery : masterQuery.getChildren()) {
                if(childQuery instanceof AdvancedSearchQueryTerm && activeFIMSConnection.getFimsAttributes().contains(((AdvancedSearchQueryTerm)childQuery).getField())) {
                    fimsQueries.add(childQuery);//todo: distinguish between queries from multiple FIMS connections
                }
                else {
                    limsQueries.add(childQuery);
                }
            }
            if(fimsQueries.size() > 0) {
                Query compoundQuery;
                if(masterQuery.getOperator() == CompoundSearchQuery.Operator.AND) {
                    compoundQuery = Query.Factory.createAndQuery(fimsQueries.toArray(new Query[fimsQueries.size()]), Collections.EMPTY_MAP);
                }
                else {
                    compoundQuery = Query.Factory.createOrQuery(fimsQueries.toArray(new Query[fimsQueries.size()]), Collections.EMPTY_MAP);
                }
                tissueSamples = activeFIMSConnection.getMatchingSamples(compoundQuery);
            }
        }
        else {
            allLims = true;
            tissueSamples = activeFIMSConnection.getMatchingSamples(query);
            fimsQueries.add(query);
            limsQueries.add(query);
        }

        if(tissueSamples != null) {
            for(FimsSample sample : tissueSamples) {
                TissueDocument doc = new TissueDocument(sample);
                callback.add(doc, Collections.EMPTY_MAP);
            }
        }




    }

    static final String UNIQUE_ID = "MooreaLabBenchService";

    public String getUniqueID() {
        return UNIQUE_ID;
    }

    public String getName() {
        return "Moorea";
    }

    public String getDescription() {
        return "Search records form Moorea";
    }

    public String getHelp() {
        return null;
    }

    public Icons getIcons() {
        return IconUtilities.getIcons("databaseSearch16.png", "databaseSearch24.png");
    }

}
