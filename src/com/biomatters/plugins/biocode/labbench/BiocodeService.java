package com.biomatters.plugins.biocode.labbench;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.databaseservice.*;
import com.biomatters.geneious.publicapi.documents.*;
import com.biomatters.geneious.publicapi.plugin.GeneiousAction;
import com.biomatters.geneious.publicapi.plugin.Icons;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;
import com.biomatters.geneious.publicapi.utilities.GuiUtilities;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import com.biomatters.plugins.biocode.BiocodePlugin;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.labbench.fims.*;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import com.biomatters.plugins.biocode.labbench.plates.GelImage;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import com.biomatters.plugins.biocode.labbench.reaction.*;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;
import java.util.List;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 23/02/2009 4:41:26 PM
 */
public class BiocodeService extends DatabaseService {
    private boolean isLoggedIn = false;
    private FIMSConnection activeFIMSConnection;
    private LIMSConnection limsConnection = new LIMSConnection();
    private String loggedOutMessage = "Right click on the " + getName() + " service in the service tree to log in.";
    static Driver driver;
    static Driver localDriver;
    private static BiocodeService instance = null;
    public static final Map<String, Image[]> imageCache = new HashMap<String, Image[]>();
    private File dataDirectory;

    public static final DateFormat dateFormat = SimpleDateFormat.getDateInstance(SimpleDateFormat.MEDIUM);//synchronize access on this (it's not threadsafe!)

    private BiocodeService() {
    }

    public void setDataDirectory(File dataDirectory) {
        this.dataDirectory = dataDirectory;
        try {
            buildCachesFromDisk();
        } catch (IOException e) {
            assert false : e.getMessage();
            loadEmptyCaches();
        } catch (JDOMException e) {
            assert false : e.getMessage();
            loadEmptyCaches();
        } catch (XMLSerializationException e) {
            assert false : e.getMessage();
            loadEmptyCaches();
        }
    }

    public File getDataDirectory() {
        return dataDirectory;
    }

    private void loadEmptyCaches() {
        cyclesequencingCocktails = Collections.emptyList();
        PCRCocktails = Collections.emptyList();
        PCRThermocycles = Collections.emptyList();
        cyclesequencingThermocycles = Collections.emptyList();
    }

    public static BiocodeService getInstance() {
        if(instance == null) {
            instance = new BiocodeService();
        }
        return instance;
    }

    public FIMSConnection getActiveFIMSConnection() {
        return activeFIMSConnection;
    }

    public LIMSConnection getActiveLIMSConnection() {
        return limsConnection;
    }

    public boolean isLoggedIn() {
        return isLoggedIn;
    }

    @Override
    public ExtendedSearchOption[] getExtendedSearchOptions(boolean isAdvancedSearch) {
        return new ExtendedSearchOption[] {
                new CheckboxSearchOption("tissueDocuments", "Tissue-samples", true),
                new CheckboxSearchOption("workflowDocuments", "Workflows", true),
                new CheckboxSearchOption("plateDocuments", "Plates", true),
        };
    }

    private static FIMSConnection[] getFimsConnections() {
        return new FIMSConnection[] {
                new ExcelFimsConnection(),
//                new GoogleFimsConnection(),
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

    public static Class getLocalDriverClass() {
        return localDriver.getClass();
    }

    public static Driver getLocalDriver() {
        return localDriver;
    }


    public QueryField[] getSearchFields() {
        List<QueryField> fieldList = new ArrayList<QueryField>();

        List<DocumentField> limsFields = limsConnection.getSearchAttributes();
        for(DocumentField field : limsFields) {
            Condition[] conditions;
            if(field.isEnumeratedField()) {
                conditions = new Condition[] {
                    Condition.EQUAL,
                    Condition.NOT_EQUAL
                };
            }
            else {
                conditions = getFieldConditions(field.getValueType());
            }
            fieldList.add(new QueryField(field, conditions));
        }

        if(activeFIMSConnection != null) {
            List<DocumentField> fimsAttributes = activeFIMSConnection.getSearchAttributes();
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
                    Condition.CONTAINS,
                    Condition.EQUAL,
                    Condition.NOT_EQUAL,
                    Condition.NOT_CONTAINS,
                    Condition.STRING_LENGTH_GREATER_THAN,
                    Condition.STRING_LENGTH_GREATER_THAN,
                    Condition.BEGINS_WITH,
                    Condition.ENDS_WITH
            };
        }
        else if(Date.class.equals(fieldClass)) {
            return new Condition[] {
                    Condition.EQUAL,
                    Condition.NOT_EQUAL,
                    Condition.GREATER_THAN,
                    Condition.GREATER_THAN_OR_EQUAL_TO,
                    Condition.LESS_THAN,
                    Condition.LESS_THAN_OR_EQUAL_TO
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

        GeneiousAction logoutAction = new GeneiousAction("logout", "Log out") {
            public void actionPerformed(ActionEvent e) {
                logOut();
            }
        }.setInPopupMenu(true);

        GeneiousAction loginAction = new GeneiousAction("login", "Log in") {
            public void actionPerformed(ActionEvent e) {
                logIn();
            }
        }.setInPopupMenu(true);
        if(isLoggedIn) {
            actions.add(logoutAction);
        }
        else {
            actions.add(loginAction);
        }

        return actions;
    }

    private void logOut() {
        isLoggedIn = false;
        if(activeFIMSConnection != null) {
            try {
                activeFIMSConnection.disconnect();
                activeFIMSConnection = null;
            } catch (ConnectionException e1) {
                Dialogs.showMessageDialog("Could not disconnect from " + activeFIMSConnection.getLabel() + ": " + e1.getMessage());
            }
        }
        try {
            limsConnection.disconnect();
        } catch (ConnectionException e1) {
            Dialogs.showMessageDialog("Could not disconnect from the FIMS service: " + e1.getMessage());
        }
        updateStatus();
    }

    private void logIn() {
        Options FIMSOptions = new Options(this.getClass());
        for (FIMSConnection connection : getFimsConnections()) {
            FIMSOptions.addChildOptions(connection.getName(), connection.getLabel(), connection.getDescription(), connection.getConnectionOptions() != null ? connection.getConnectionOptions() : new Options(this.getClass()));
        }
        FIMSOptions.addChildOptionsPageChooser("fims", "Field Database Connection", Collections.<String>emptyList(), Options.PageChooserType.COMBO_BOX, false);

        final Options LIMSOptions = limsConnection.getConnectionOptions();

        Options loginOptions = new Options(this.getClass());
        loginOptions.addChildOptions("fims", "", "", FIMSOptions);
        loginOptions.addChildOptions("lims", "Lab-bench login", "", LIMSOptions);

        loginOptions.addFileSelectionOption("driver", "MySQL Driver", "", new String[0], "Browse...", new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.toLowerCase().endsWith(".jar");
            }
        }).setSelectionType(JFileChooser.FILES_ONLY);

        loginOptions.restorePreferences();

        String logIn = "Log In";
        Dialogs.DialogOptions dialogOptions = new Dialogs.DialogOptions(new String[] {logIn, "Cancel"}, logIn, null, Dialogs.DialogIcon.NO_ICON);
        dialogOptions.setMaxWidth(dialogOptions.getMaxDimensions().width + 50);
        Object result = Dialogs.showDialog(dialogOptions, loginOptions.getPanel());
        if (logIn.equals(result)) {
            loginOptions.savePreferences();

            //load the connection driver -------------------------------------------------------------------
            String driverFileName = (String) loginOptions.getValue("driver");

            ClassLoader loader;
            try {
                File driverFile = new File(driverFileName);
                if(!driverFile.exists() || driverFile.isDirectory()) {
                    Dialogs.showMessageDialog("You need to specify a valid MySql Driver!");
                    logOut();
                    return;
                }
                URL driverUrl = driverFile.toURL();
                loader = new URLClassLoader(new URL[]{driverUrl}, getClass().getClassLoader());
            } catch (MalformedURLException ex) {
                Dialogs.showMessageDialog("Could not load the MySql Driver!");
                logOut();
                return;
            }

            String error = null;

            try {
                Class driverClass = loader.loadClass("com.mysql.jdbc.Driver");
                driver = (Driver) driverClass.newInstance();
            } catch (ClassNotFoundException e1) {
                error = "Could not find MySQL driver class";
            } catch (IllegalAccessException e1) {
                error = "Could not access MySQL driver class";
            } catch (InstantiationException e1) {
                error = "Could not instantiate MySQL driver class";
            } catch (ClassCastException e1) {
                error = "MySQL Driver class exists, but is not an SQL driver";
            }

            try {
                Class driverClass = loader.loadClass("org.hsqldb.jdbc.JDBCDriver");
                localDriver = (Driver) driverClass.newInstance();
            } catch (ClassNotFoundException e1) {
                error = "Could not find HSQL driver class";
            } catch (IllegalAccessException e1) {
                error = "Could not access HSQL driver class";
            } catch (InstantiationException e1) {
                error = "Could not instantiate HSQL driver class";
            } catch (ClassCastException e1) {
                error = "HSQL Driver class exists, but is not an SQL driver";
            }

            if (error != null) {
                Dialogs.showMessageDialog(error);
                logOut();
                return;
            }
            //----------------------------------------------------------------------------------------------


            //get the selected fims service.
            String selectedFimsServiceName = FIMSOptions.getValueAsString("fims");
            final Options selectedFimsOptions = FIMSOptions.getChildOptions().get(selectedFimsServiceName);
            for (FIMSConnection connection : getFimsConnections()) {
                if (connection.getName().equals(selectedFimsServiceName)) {
                    activeFIMSConnection = connection;
                }
            }
            if (activeFIMSConnection == null) {
                throw new RuntimeException("Could not find a FIMS connection called " + selectedFimsServiceName);
            }


            //try to connect to the selected service
            Runnable runnable = new Runnable() {
                public void run() {
                    block("Connecting to the FIMS", null);
                    try {
                        activeFIMSConnection.connect(selectedFimsOptions);
                        isLoggedIn = true;
                    }
                    catch (ConnectionException ex) {
                        unBlock();
                        Dialogs.showMoreOptionsDialog(new Dialogs.DialogOptions(new String[] {"OK"},"Error connecting to FIMS"), "There was an error connecting to "+activeFIMSConnection.getLabel(), ex.getMessage());
                        logOut();
                        return;
                    }

                    try {
                        if(!(activeFIMSConnection instanceof MooreaFimsConnection) && LIMSOptions.getValueAsString("server").equalsIgnoreCase("darwin.berkeley.edu")) {
                            Dialogs.showMessageDialog("You cannot connect to the Moorea Lab Bench database using a field database other than the Moorea FIMS");
                            logOut();
                            unBlock();
                            return;
                        }

                        block("Connecting to the LIMS", null);
                        limsConnection.connect(LIMSOptions);
                        block("Building Caches", null);
                        buildCaches();
                    } catch (ConnectionException e1) {
                        Dialogs.showMessageDialog("Failed to connect to the LIMS database: " + e1.getMessage());
                        logOut();
                        unBlock();
                        return;
                    } catch (TransactionException e2) {
                        Dialogs.showMessageDialog("Failed to connect to the LIMS database: " + e2.getMessage());
                        logOut();
                        unBlock();
                        return;
                    }
                    unBlock();
                    updateStatus();
                }

            };
            new Thread(runnable).start();
        }
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
        List<FimsSample> tissueSamples = null;
        List<Query> fimsQueries = new ArrayList<Query>();
        List<Query> limsQueries = new ArrayList<Query>();


        if(query instanceof CompoundSearchQuery) {
            CompoundSearchQuery masterQuery = (CompoundSearchQuery) query;
            for(Query childQuery : masterQuery.getChildren()) {
                if(childQuery instanceof AdvancedSearchQueryTerm && activeFIMSConnection.getSearchAttributes().contains(((AdvancedSearchQueryTerm)childQuery).getField())) {
                    fimsQueries.add(childQuery);//todo: distinguish between queries from multiple FIMS connections
                }
                else {
                    limsQueries.add(childQuery);
                }
            }
            if(fimsQueries.size() > 0) {
                Query compoundQuery;
                if(masterQuery.getOperator() == CompoundSearchQuery.Operator.AND) {
                    compoundQuery = Query.Factory.createAndQuery(fimsQueries.toArray(new Query[fimsQueries.size()]), Collections.<String, Object>emptyMap());
                }
                else {
                    compoundQuery = Query.Factory.createOrQuery(fimsQueries.toArray(new Query[fimsQueries.size()]), Collections.<String, Object>emptyMap());
                }
                try {
                    tissueSamples = activeFIMSConnection.getMatchingSamples(compoundQuery);
                } catch (ConnectionException e) {
                    throw new DatabaseServiceException(e.getMessage(), false);
                }
            }
        }
        else if(query instanceof BasicSearchQuery){
            try {
                tissueSamples = activeFIMSConnection.getMatchingSamples(query);
            } catch (ConnectionException e) {
                throw new DatabaseServiceException(e.getMessage(), false);
            }
            fimsQueries.add(query);
            limsQueries.add(query);
        } else if(query instanceof AdvancedSearchQueryTerm){
            if(activeFIMSConnection.getSearchAttributes().contains(((AdvancedSearchQueryTerm)query).getField())) {
                fimsQueries.add(query);
                try {
                    tissueSamples = activeFIMSConnection.getMatchingSamples(query);
                } catch (ConnectionException e) {
                    throw new DatabaseServiceException(e.getMessage(), false);
                }
            }
            else {
                limsQueries.add(query);
            }
        }
        if(callback.isCanceled()) {
            return;
        }

        if(tissueSamples != null && (Boolean)query.getExtendedOptionValue("tissueDocuments")) {
            for(FimsSample sample : tissueSamples) {
                TissueDocument doc = new TissueDocument(sample);
                callback.add(doc, Collections.<String, Object>emptyMap());
            }
        }
        if(callback.isCanceled()) {
            return;
        }
        try {
            List<WorkflowDocument> workflowList = Collections.emptyList();
            boolean isAnd = true;
            if(query instanceof CompoundSearchQuery) {
                isAnd = ((CompoundSearchQuery)query).getOperator() == CompoundSearchQuery.Operator.AND;
            }
            Query limsQuery = isAnd ? Query.Factory.createAndQuery(limsQueries.toArray(new Query[limsQueries.size()]), Collections.EMPTY_MAP) : Query.Factory.createOrQuery(limsQueries.toArray(new Query[limsQueries.size()]), Collections.EMPTY_MAP);

            if((Boolean)query.getExtendedOptionValue("workflowDocuments") || (Boolean)query.getExtendedOptionValue("plateDocuments")) {
                workflowList = limsConnection.getMatchingWorkflowDocuments(limsQuery, tissueSamples);
            }
            if(callback.isCanceled()) {
                return;
            }
            if((Boolean)query.getExtendedOptionValue("workflowDocuments")) {
                for(PluginDocument doc : workflowList) {
                    callback.add(doc, Collections.<String, Object>emptyMap());
                }
            }
            if(callback.isCanceled()) {
                return;
            }
            if((Boolean)query.getExtendedOptionValue("plateDocuments")) {
                List<PlateDocument> plateList = limsConnection.getMatchingPlateDocuments(limsQuery, workflowList);
                if(callback.isCanceled()) {
                    return;
                }
                for(PluginDocument doc : plateList) {
                    callback.add(doc, Collections.<String, Object>emptyMap());
                }
            }

        } catch (SQLException e) {
            throw new DatabaseServiceException(e, e.getMessage(), true);
        }


    }

    static final String UNIQUE_ID = "BiocodeService";

    public String getUniqueID() {
        return UNIQUE_ID;
    }

    public String getName() {
        return "Biocode";
    }

    public String getDescription() {
        return "Search records form a Biocode database";
    }

    public String getHelp() {
        return null;
    }

    public Icons getIcons() {
        return BiocodePlugin.getIcons("biocode24.png");
    }

    public void addNewPCRCocktails(List<? extends Cocktail> newCocktails) throws TransactionException{
        if(newCocktails.size() > 0) {
            for(Cocktail cocktail : newCocktails) {
                limsConnection.executeUpdate(cocktail.getSQLString());
            }
        }
        buildCaches();
    }

    public void addNewCycleSequencingCocktails(List<? extends Cocktail> newCocktails) throws TransactionException{
        if(newCocktails.size() > 0) {
            for(Cocktail cocktail : newCocktails) {
                limsConnection.executeUpdate(cocktail.getSQLString());
            }
        }
        buildCaches();
    }

    private List<Thermocycle> PCRThermocycles = null;
    private List<Thermocycle> cyclesequencingThermocycles = null;
    private List<Cocktail> PCRCocktails = null;
    private List<Cocktail> cyclesequencingCocktails = null;

    private void buildCaches() throws TransactionException {
        PCRThermocycles = getThermocyclesFromDatabase("pcr_thermocycle");
        cyclesequencingThermocycles = getThermocyclesFromDatabase("cyclesequencing_thermocycle");
        PCRCocktails = getPCRCocktailsFromDatabase();
        cyclesequencingCocktails = getCycleSequencingCocktailsFromDatabase();
        try {
            saveCachesToDisk();
        } catch (IOException e) {
            throw new TransactionException("Could not write the caches to disk", e);
        } catch (JDOMException e) {
            throw new TransactionException("Could not write the caches to disk", e);
        } catch (XMLSerializationException e) {
            throw new TransactionException("Could not write the caches to disk", e);
        }
    }

    private void buildCachesFromDisk() throws IOException, JDOMException, XMLSerializationException {
        PCRThermocycles = getThermocyclesFromDisk("pcr_thermocycle");
        cyclesequencingThermocycles = getThermocyclesFromDisk("cyclesequencing_thermocycle");
        PCRCocktails = getPCRCocktailsFromDisk();
        cyclesequencingCocktails = getCycleSequencingCocktailsFromDisk();
    }

    private void saveCachesToDisk() throws IOException, JDOMException, XMLSerializationException {
        saveThermocyclesToDisk("pcr_thermocycle", PCRThermocycles);
        saveThermocyclesToDisk("cyclesequencing_thermocycle", cyclesequencingThermocycles);
        savePCRCocktailsToDisk();
        saveCycleSequencingCocktailsToDisk();
    }

    private List<Cocktail> getCycleSequencingCocktailsFromDisk() throws JDOMException, IOException, XMLSerializationException{
        File file = new File(dataDirectory, "cyclesequencingCocktails.xml");
        return getCocktails(file);
    }

    private List<Cocktail> getPCRCocktailsFromDisk() throws IOException, JDOMException, XMLSerializationException {
        File file = new File(dataDirectory, "PCRCocktails.xml");
        return getCocktails(file);
    }

    private List<Cocktail> getCocktails(File file) throws JDOMException, IOException, XMLSerializationException {
        if(!file.exists()) {
            return Collections.emptyList();
        }
        List<Cocktail> cocktails = new ArrayList<Cocktail>();
        SAXBuilder builder = new SAXBuilder();
        Element element = builder.build(file).detachRootElement();
        for(Element e : element.getChildren("cocktail")) {
            cocktails.add(XMLSerializer.classFromXML(e, Cocktail.class));
        }
        return cocktails;
    }

    private List<Thermocycle> getThermocyclesFromDisk(String type)  throws JDOMException, IOException, XMLSerializationException{
        File file = new File(dataDirectory, type+".xml");
        if(!file.exists()) {
            return Collections.emptyList();
        }

        List<Thermocycle> thermocycles = new ArrayList<Thermocycle>();
        SAXBuilder builder = new SAXBuilder();
        Element element = builder.build(file).detachRootElement();
        for(Element e : element.getChildren("thermocycle")) {
            thermocycles.add(XMLSerializer.classFromXML(e, Thermocycle.class));
        }
        return thermocycles;
    }

    //----

    private void saveCycleSequencingCocktailsToDisk() throws IOException, XMLSerializationException{
        File file = new File(dataDirectory, "cyclesequencingCocktails.xml");
        if(!file.exists()) {
            createNewFile(file);
        }
        saveCocktails(file, PCRCocktails);
    }

    private void savePCRCocktailsToDisk() throws IOException, XMLSerializationException {
        File file = new File(dataDirectory, "PCRCocktails.xml");
        if(!file.exists()) {
            createNewFile(file);
        }
        saveCocktails(file, cyclesequencingCocktails);
    }

    private void saveCocktails(File file, List<Cocktail> cocktails) throws IOException, XMLSerializationException {
        if(cocktails == null || cocktails.size() == 0) {
            file.delete();
            return;
        }
        Element cocktailsElement = new Element("cocktails");
        for(Cocktail c : cocktails) {
            cocktailsElement.addContent(XMLSerializer.classToXML("cocktail", c));
        }
        XMLOutputter out = new XMLOutputter(Format.getPrettyFormat());
        FileOutputStream outf = new FileOutputStream(file);
        out.output(cocktailsElement, outf);
        outf.close();
    }


    private void saveThermocyclesToDisk(String type, List<Thermocycle> thermocycles)  throws IOException, XMLSerializationException{
        File file = new File(dataDirectory, type+".xml");
        if(!file.exists()) {
            createNewFile(file);
        }
        if(thermocycles == null || thermocycles.size() == 0) {
            file.delete();
            return;
        }

        Element thermocyclesElement = new Element("thermocycles");
        for(Thermocycle tc : thermocycles) {
            thermocyclesElement.addContent(XMLSerializer.classToXML("thermocycle", tc));
        }
        XMLOutputter out = new XMLOutputter(Format.getPrettyFormat());
        FileOutputStream outf = new FileOutputStream(file);
        out.output(thermocyclesElement, outf);
        outf.close();
    }

    private void createNewFile(File f) throws IOException {
        if(f.getParentFile() != null && !f.getParentFile().exists()) {
            f.getParentFile().mkdirs();
        }
        f.createNewFile();
    }

    private List<Cocktail> getPCRCocktailsFromDatabase() throws TransactionException{
        ResultSet resultSet = limsConnection.executeQuery("SELECT * FROM pcr_cocktail");
        List<Cocktail> cocktails = new ArrayList<Cocktail>();
        try {
            while(resultSet.next()) {
                cocktails.add(new PCRCocktail(resultSet));    
            }
            resultSet.getStatement().close();
        }
        catch(SQLException ex) {
            ex.printStackTrace();
            throw new TransactionException("Could not query PCR Cocktails from the database");
        }
        return cocktails;
    }

    private List<Cocktail> getCycleSequencingCocktailsFromDatabase() throws TransactionException{
        ResultSet resultSet = limsConnection.executeQuery("SELECT * FROM cyclesequencing_cocktail");
        List<Cocktail> cocktails = new ArrayList<Cocktail>();
        try {
            while(resultSet.next()) {
                cocktails.add(new CycleSequencingCocktail(resultSet));    
            }
            resultSet.getStatement().close();
        }
        catch(SQLException ex) {
            throw new TransactionException("Could not query CycleSequencing Cocktails from the database");
        }
        return cocktails;
    }

    private List<Thermocycle> getThermocyclesFromDatabase(String thermocycleIdentifierTable) throws TransactionException {
        //String sql = "SELECT * FROM "+thermocycleIdentifierTable+" LEFT JOIN (thermocycle, cycle, state) ON (thermocycleid = "+thermocycleIdentifierTable+".cycle AND thermocycle.id = cycle.thermocycleId AND cycle.id = state.cycleId);";
        String sql = "SELECT * FROM "+thermocycleIdentifierTable+" LEFT JOIN thermocycle ON (thermocycle.id = "+thermocycleIdentifierTable+".cycle) LEFT JOIN cycle ON (thermocycle.id = cycle.thermocycleId) LEFT JOIN state ON (cycle.id = state.cycleId);";
//        String sql = "SELECT * FROM "+thermocycleIdentifierTable+" LEFT JOIN thermocycle ON thermocycle.id = "+thermocycleIdentifierTable+".cycle LEFT JOIN cycle ON thermocycle.id = cycle.thermocycleId LEFT JOIN state ON cycle.id = state.cycleId;";
        System.out.println(sql);


        ResultSet resultSet = limsConnection.executeQuery(sql);

        List<Thermocycle> tCycles = new ArrayList<Thermocycle>();

        try {
            resultSet.next();
            while(true) {
                try {
                    resultSet.getInt("thermocycle.id");
                    Thermocycle thermocycle = Thermocycle.fromSQL(resultSet);
                    if(thermocycle != null) {
                    tCycles.add(thermocycle);
                    }
                    else {
                        break;
                    }
                }
                catch(SQLException e) {
                    break;
                }
            }
            resultSet.getStatement().close();
        }
        catch(SQLException ex) {
            throw new TransactionException("could not read thermocycles from the database", ex);
        }

        return tCycles;
    }

    public List<Thermocycle> getPCRThermocycles() {
//        Thermocycle tc = new Thermocycle("Test Cycle", 0);
//        Thermocycle.Cycle cycle1 = new Thermocycle.Cycle(1);
//        cycle1.addState(new Thermocycle.State(25, 90));
//        tc.addCycle(cycle1);
//        Thermocycle.Cycle cycle2 = new Thermocycle.Cycle(25);
//        cycle1.addState(new Thermocycle.State(90, 30));
//        cycle1.addState(new Thermocycle.State(55, 30));
//        cycle1.addState(new Thermocycle.State(75, 30));
//        tc.addCycle(cycle2);
//        Thermocycle.Cycle cycle3 = new Thermocycle.Cycle(1);
//        cycle1.addState(new Thermocycle.State(15, Integer.MAX_VALUE));
//        tc.addCycle(cycle3);
        ArrayList<Thermocycle> cycles = new ArrayList<Thermocycle>();
        cycles.addAll(PCRThermocycles);
//        cycles.add(tc);
        return cycles;
    }

    public List<Thermocycle> getCycleSequencingThermocycles() {
        ArrayList<Thermocycle> cycles = new ArrayList<Thermocycle>();
        cycles.addAll(cyclesequencingThermocycles);
        return cycles;
    }

    public void addPCRThermoCycles(List<Thermocycle> cycles) throws TransactionException{
        insertThermocycles(cycles, "pcr_thermocycle");
    }

    private void insertThermocycles(List<Thermocycle> cycles, String tableName) throws TransactionException {
        try {
            Connection connection = limsConnection.getConnection();
            connection.setAutoCommit(false);
            boolean autoCommit = connection.getAutoCommit();
            for(Thermocycle tCycle : cycles) {
                connection.setAutoCommit(false);
                Savepoint savepoint = connection.setSavepoint();
                try {
                    int id = tCycle.toSQL(connection);
                    PreparedStatement statement = connection.prepareStatement("INSERT INTO "+tableName+" (cycle) VALUES ("+id+");\n");
                    statement.execute();
                    if(!autoCommit)
                        connection.commit();
                    statement.close();
                }
                catch(SQLException ex) {
                    try {
                        connection.rollback(savepoint);
                    } catch (SQLException e) {}
                    throw ex;
                }
                finally {
                    connection.setAutoCommit(autoCommit);
                }
            }
        } catch (SQLException e) {
            throw new TransactionException("Could not add thermocycle(s): "+e.getMessage(), e);
        }
        buildCaches();
    }

    public void addCycleSequencingThermoCycles(List<Thermocycle> cycles) throws TransactionException{
        insertThermocycles(cycles, "cyclesequencing_thermocycle");
        System.out.println("done!");
    }

    public boolean hasWriteAccess() {
        return true; //todo: figure out how to do this...
    }

    private static BlockingDialog blockingDialog;

    public static synchronized void block(final String message, final Component parentComponent, final Runnable task){
        Runnable r = new Runnable(){
            public void run() {
                try {
                    task.run();
                }
                catch(Exception e) {
                    throw new RuntimeException("An exception was caught in another thread", e);
                }
                finally {
                    unSynchronizedUnBlock();
                }
            }
        };
        new Thread(r, "Biocode blocking thread").start();
        block(message, parentComponent);
    }

    public static synchronized void block(final String message, final Component parentComponent){
        Runnable runnable = new Runnable() {
            public void run() {
                if(blockingDialog == null) {
                    Component parent = parentComponent;
                    while(parent != null && !(parent instanceof Frame) && parent.getParent() != null) {
                        parent = parent.getParent();
                    }
                    if(parent instanceof Frame) {
                        blockingDialog = new BlockingDialog(message, (Frame)parent);
                        blockingDialog.setLocationRelativeTo(parent);
                    }
                    else {
                        blockingDialog = new BlockingDialog(message, GuiUtilities.getMainFrame());
                        blockingDialog.setLocationRelativeTo(GuiUtilities.getMainFrame());
                    }
                    blockingDialog.setVisible(true);
                }
                else {
                    blockingDialog.setMessage(message);
                }
            }
        };
        ThreadUtilities.invokeNowOrLater(runnable);
    }

    public static synchronized void unBlock() {
        Runnable runnable = new Runnable() {
            public void run() {
                if(blockingDialog != null) {
                    blockingDialog.setVisible(false);
                    blockingDialog = null;
                }
            }
        };
        ThreadUtilities.invokeNowOrLater(runnable);
    }


    public static void unSynchronizedUnBlock() {
        Runnable runnable = new Runnable() {
            public void run() {
                if(blockingDialog != null) {
                    blockingDialog.setVisible(false);
                    blockingDialog = null;
                }
            }
        };
        ThreadUtilities.invokeNowOrLater(runnable);
    }

    public List<Cocktail> getPCRCocktails() {
        List<Cocktail> cocktailList = new ArrayList<Cocktail>();
        //cocktailList.add(new PCRCocktail("No Cocktail"));
        if(PCRCocktails != null) {
            cocktailList.addAll(PCRCocktails);
        }
        return cocktailList;
    }

    public List<Cocktail> getCycleSequencingCocktails() {
        List<Cocktail> cocktailList = new ArrayList<Cocktail>();
        //cocktailList.add(new CycleSequencingCocktail("No Cocktail"));
        if(cyclesequencingCocktails != null) {
            cocktailList.addAll(cyclesequencingCocktails);
        }
        return cocktailList;
    }

    public Map<String, String> getReactionToTissueIdMapping(String tableName, List<? extends Reaction> reactions) throws SQLException{
        if(reactions.size() == 0) {
            return Collections.emptyMap();
        }
        String tableDefinition = tableName.equals("extraction") ? tableName : tableName+", extraction, workflow";
        String notExtractionBit = tableName.equals("extraction") ? "" : " workflow.extractionId = extraction.id AND " + tableName + ".workflow = workflow.id AND";
        StringBuilder sql = new StringBuilder("SELECT extraction.extractionId AS extractionId, extraction.sampleId AS tissue FROM " + tableDefinition + " WHERE" + notExtractionBit + " (");

        int count = 0;
        for(int i=0; i < reactions.size(); i++) {
            if(reactions.get(i).isEmpty()) {
                continue;
            }
            if(count > 0) {
                sql.append(" OR ");
            }
            sql.append("extraction.extractionId=?");
            count++;
        }
        sql.append(")");
        if(count == 0) {
            return Collections.emptyMap();
        }
        PreparedStatement statement = limsConnection.getConnection().prepareStatement(sql.toString());
        int reactionCount = 1;
        for(int i=0; i < reactions.size(); i++) {
            if(reactions.get(i).isEmpty()) {
                continue;
            }
            statement.setString(reactionCount, reactions.get(i).getExtractionId());
            reactionCount++;
        }

        ResultSet resultSet = statement.executeQuery();

        Map<String, String> results = new HashMap<String, String>();
        while(resultSet.next()) {
            results.put(resultSet.getString("extractionId"), resultSet.getString("tissue"));
        }

        statement.close();
        return results;
    }

    public List<Workflow> createWorkflows(List<Reaction> reactions, BlockingProgress progress) throws SQLException{
        List<Workflow> workflows = new ArrayList<Workflow>();
        Connection connection = limsConnection.getConnection();
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);     
        Savepoint savepoint = connection.setSavepoint();
        try {
            PreparedStatement statement = connection.prepareStatement("INSERT INTO workflow(extractionId) VALUES ((SELECT extraction.id from extraction where extraction.extractionId = ?))");
            PreparedStatement statement2 = limsConnection.isLocal() ? connection.prepareStatement("CALL IDENTITY();") : connection.prepareStatement("SELECT last_insert_id()");
            PreparedStatement statement3 = connection.prepareStatement("UPDATE workflow SET name = CONCAT('workflow', id) WHERE id=?");
            for(int i=0; i < reactions.size(); i++) {
                if(progress != null) {
                    progress.setMessage("Creating new workflow "+(i+1)+" of "+reactions.size());
                }
                statement.setString(1, reactions.get(i).getExtractionId());
                statement.execute();
                ResultSet resultSet = statement2.executeQuery();
                resultSet.next();
                int workflowId = resultSet.getInt(1);
                workflows.add(new Workflow(workflowId, "workflow"+workflowId, reactions.get(i).getExtractionId()));
                statement3.setInt(1, workflowId);
                statement3.execute();
            }
            if(!autoCommit)
                connection.commit();
            statement.close();
            statement2.close();
            statement3.close();
            return workflows;
        }
        catch(SQLException ex) {
            try {
                connection.rollback(savepoint);
            } catch (SQLException e) {}
            finally {
                throw ex;
            }
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    public void saveExtractions(BiocodeService.BlockingProgress progress, Plate plate) throws SQLException, BadDataException{
        List<String> extractionIds = new ArrayList<String>();
        List<Reaction> extractionWithoutWorkflowIds = new ArrayList<Reaction>();

        Connection connection = limsConnection.getConnection();
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);      
        Savepoint savepoint = connection.setSavepoint();
        try {
            isPlateValid(plate, connection);

            List<Reaction> reactionsToSave = new ArrayList<Reaction>();
            for(Reaction reaction : plate.getReactions()) {
                if(!reaction.isEmpty()) {
                    extractionIds.add(reaction.getExtractionId());
                    if(reaction.getId() < 0 && (reaction.getWorkflow() == null || reaction.getWorkflow().getId() < 0) && reaction.getFieldValue("sampleId").toString().length() > 0) {
                        extractionWithoutWorkflowIds.add(reaction);
                    }
                    reactionsToSave.add(reaction);
                }
            }

            if(reactionsToSave.size() == 0) {
                throw new BadDataException("You need to save at least one reaction with your plate");
            }

            String error = reactionsToSave.get(0).areReactionsValid(reactionsToSave, null, true);
            if(error != null && error.length() > 0) {
                throw new BadDataException(error);
            }

            createOrUpdatePlate(plate, progress);

            if(extractionWithoutWorkflowIds.size() > 0) {
                progress.setMessage("Creating new workflows");

                //create workflows if necessary

                if(extractionIds.size() > 0) {
                    List<Workflow> workflowList = createWorkflows(extractionWithoutWorkflowIds, progress);
                    for (int i = 0; i < extractionWithoutWorkflowIds.size(); i++) {
                        Reaction reaction = extractionWithoutWorkflowIds.get(i);
                        reaction.setWorkflow(workflowList.get(i));
                    }
                }
            }
            if(!autoCommit)
                connection.commit();
            if(!limsConnection.isLocal()) {
                connection.releaseSavepoint(savepoint);
            }
        } catch(BadDataException e) {
            try {
                connection.rollback(savepoint);
            } catch (SQLException e1) {} //ignore - if this fails, then we are already rolled back.
            finally {
                throw e;
            }
        } finally {
            connection.setAutoCommit(autoCommit);
        }


    }

    public void deletePlate(BiocodeService.BlockingProgress progress, Plate plate) throws SQLException {

        Set<Integer> plateIds = new HashSet<Integer>();

        //delete the reactions...
        if(plate.getReactionType() == Reaction.Type.Extraction) {
            plateIds.addAll(deleteWorkflows(progress, plate));
        }
        else {
            deleteReactions(progress, plate);
        }


        //delete the images...
        progress.setMessage("Deleting GEL images");
        limsConnection.deleteRecords("gelimages", "plate", Arrays.asList(plate.getId()));

        //delete the plate...
        progress.setMessage("deleting the plate");

        limsConnection.deleteRecords("plate", "id", Arrays.asList(plate.getId()));

        if(plate.getReactionType() == Reaction.Type.Extraction) {
            List<Plate> emptyPlates = getEmptyPlates(plateIds);
            if(emptyPlates.size() > 0) {
                StringBuilder message = new StringBuilder("Geneious has found the following empty plates in the database.\n  Do you want to delete them as well?\n");
                for(Plate emptyPlate : emptyPlates) {
                    message.append(emptyPlate.getName()+"\n");
                }
                if(Dialogs.showYesNoDialog(message.toString(), "Delete empty plates", progress.getComponentForOwner(), Dialogs.DialogIcon.QUESTION)){
                    for(Plate p : emptyPlates) {
                        deletePlate(progress, p);
                    }
                }
            }
        }



        plate.setDeleted(true);
        progress.dispose();

    }

    private Set<Integer> deleteWorkflows(BlockingProgress progress, Plate plate) throws SQLException {
        progress.setMessage("deleting workflows");
        if(plate.getReactionType() != Reaction.Type.Extraction) {
            throw new IllegalArgumentException("You may only delete workflows from an extraction plate!");
        }

        ArrayList<Integer> workflows = new ArrayList<Integer>();
        ArrayList<Integer> ids = new ArrayList<Integer>();
        ArrayList<String> extractionNames = new ArrayList<String>();


        boolean first = true;
        StringBuilder builder = new StringBuilder();
        int reactionCount = 0;
        for(Reaction r : plate.getReactions()) { //get the extraction id's and set up the query to get the workflow id's
            if(r.getId() >= 0) {
                ids.add(r.getId());
                extractionNames.add("'"+r.getExtractionId()+"'");
                if(!first) {
                    builder.append(" OR ");
                }
                builder.append("extractionId="+r.getId());
                first = false;
                reactionCount++;
            }
        }
        if(reactionCount == 0) { //the plate is empty
            return Collections.EMPTY_SET;    
        }

        String getWorkflowSQL = "SELECT id FROM workflow WHERE "+builder.toString();
        System.out.println(getWorkflowSQL);

        Statement statement = limsConnection.getConnection().createStatement();


        ResultSet resultSet = statement.executeQuery(getWorkflowSQL);
        while(resultSet.next()) {
            workflows.add(resultSet.getInt("workflow.id"));
        }

        Set<Integer> plates = new HashSet<Integer>();

        plates.addAll(limsConnection.deleteRecords("pcr", "workflow", workflows));
        //plates.addAll(limsConnection.deleteRecords("pcr", "extractionId", extractionNames));
        plates.addAll(limsConnection.deleteRecords("cyclesequencing", "workflow", workflows));
       // plates.addAll(limsConnection.deleteRecords("cyclesequencing", "extractionId", extractionNames));
        limsConnection.deleteRecords("workflow", "id", workflows);
        plates.addAll(limsConnection.deleteRecords("extraction", "id", ids));

        return plates;
    }

    private void deleteReactions(BlockingProgress progress, Plate plate) throws SQLException {
        progress.setMessage("deleting reactions");

        String tableName;
        switch(plate.getReactionType()) {
            case Extraction:
                tableName = "extraction";
                break;
            case PCR:
                tableName = "pcr";
                break;
            case CycleSequencing:
            default:
                tableName = "cyclesequencing";
                break;
        }

        ArrayList<Integer> terms = new ArrayList<Integer>();
        for(Reaction r : plate.getReactions()) {
            if(r.getId() >= 0) {
                terms.add(r.getId());
            }
        }

        limsConnection.deleteRecords(tableName, "id", terms);
    }

    /**
     * @param plateIds the ids of the plates to check
     * returns all the empty plates in the database...
     * @return all the empty plates in the database...
     * @throws SQLException
     */
    private List<Plate> getEmptyPlates(Collection<Integer> plateIds) throws SQLException{
        if(plateIds == null || plateIds.size() == 0) {
            return Collections.emptyList();
        }

        String sql = "SELECT * FROM plate WHERE (plate.id NOT IN (select plate from extraction)) AND (plate.id NOT IN (select plate from pcr)) AND (plate.id NOT IN (select plate from cyclesequencing))";


        List<String> idMatches = new ArrayList<String>();
        for(Integer num : plateIds) {
            idMatches.add("id="+num);
        }

        String termString = StringUtilities.join(" OR ", idMatches);
        if(termString.length() > 0) {
            sql += " AND ("+termString+")";
        }

        ResultSet resultSet = limsConnection.getConnection().createStatement().executeQuery(sql);
        List<Plate> result = new ArrayList<Plate>();
        while(resultSet.next()) {
            Plate plate = new Plate(resultSet);
            plate.initialiseReactions();
            result.add(plate);
        }
        return result;
    }

    public void saveReactions(BiocodeService.BlockingProgress progress, Plate plate) throws SQLException, BadDataException {
        progress.setMessage("Retrieving existing workflows");
        Connection connection = limsConnection.getConnection();
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        Savepoint savepoint = connection.setSavepoint();
        int originalPlateId = plate.getId();
        try {


            //set workflows for reactions that have id's
            List<Reaction> reactionsToSave = new ArrayList<Reaction>();
            List<String> workflowIdStrings = new ArrayList<String>();
            for(Reaction reaction : plate.getReactions()) {

                Object workflowId = reaction.getFieldValue("workflowId");
                Object tissueId = reaction.getFieldValue("sampleId");
                String extractionId = reaction.getExtractionId();

                if(!reaction.isEmpty() && reaction.getType() != Reaction.Type.Extraction) {
                    reactionsToSave.add(reaction);
                    if(extractionId != null && tissueId != null && tissueId.toString().length() > 0) {
                        if(reaction.getWorkflow() != null && workflowId.toString().length() > 0){
                            if(!reaction.getWorkflow().getExtractionId().equals(extractionId)) {
                                reaction.setHasError(true);
                                throw new BadDataException("The workflow "+workflowId+" does not match the extraction "+extractionId);
                            }
                            if(reaction.getWorkflow().getName().equals(workflowId)) {
                                continue;
                            }
                        }
                        else {
                            reaction.setWorkflow(null);
                            workflowIdStrings.add(workflowId.toString());
                        }
                    }
                }
            }

            if(reactionsToSave.size() == 0) {
                throw new BadDataException("You need to save at least one reaction with your plate");
            }

            String error = reactionsToSave.get(0).areReactionsValid(reactionsToSave, null, true);
            if(error != null && error.length() > 0) {
                throw new BadDataException(error);
            }

            if(workflowIdStrings.size() > 0) {
                Map<String,Workflow> map = BiocodeService.getInstance().getWorkflows(workflowIdStrings);
                for(Reaction reaction : plate.getReactions()) {

                    Object workflowId = reaction.getFieldValue("workflowId");
                    Object tissueId = reaction.getFieldValue("sampleId");
                    String extractionId = reaction.getExtractionId();

                    if(reaction.getWorkflow() == null && tissueId != null && tissueId.toString().length() > 0){
                        Workflow workflow = map.get(workflowId);
                        if(workflow != null) {
                            if(!reaction.getWorkflow().getExtractionId().equals(extractionId)) {
                                reaction.setHasError(true);
                                throw new BadDataException("The workflow "+workflowId+" does not match the extraction "+extractionId);
                            }
                        }
                        reaction.setWorkflow(workflow);
                    }
                }
            }

            progress.setMessage("Creating new workflows");

            //create workflows if necessary
            //int workflowCount = 0;
            List<Reaction> reactionsWithoutWorkflows = new ArrayList<Reaction>();
            for(Reaction reaction : plate.getReactions()) {
                Object extractionId = reaction.getFieldValue("extractionId");
                if(!reaction.isEmpty() && extractionId != null && extractionId.toString().length() > 0 && (reaction.getWorkflow() == null || reaction.getWorkflow().getId() < 0)) {
                    reactionsWithoutWorkflows.add(reaction);
                }
            }
            if(reactionsWithoutWorkflows.size() > 0) {
                List<Workflow> workflowList = createWorkflows(reactionsWithoutWorkflows, progress);
                for (int i = 0; i < reactionsWithoutWorkflows.size(); i++) {
                    Reaction reaction = reactionsWithoutWorkflows.get(i);
                    reaction.setWorkflow(workflowList.get(i));
                }
            }

            progress.setMessage("Creating the plate");
            //we need to create the plate
            createOrUpdatePlate(plate, progress);
            if(!autoCommit)
                connection.commit();
            if(!limsConnection.isLocal()) {
                connection.releaseSavepoint(savepoint);
            }
        } catch(BadDataException e) {
            plate.setId(originalPlateId);
            try {
                connection.rollback(savepoint);
            } catch (SQLException e1) {} //ignore - if this fails, then we are already rolled back.
            finally {
                throw e;
            }
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    private void createOrUpdatePlate(Plate plate, BlockingProgress progress) throws SQLException, BadDataException{
        Connection connection = limsConnection.getConnection();

        //check the vaidity of the plate.
        isPlateValid(plate, connection);

        //update the plate
        PreparedStatement statement = plate.toSQL(connection);
        statement.execute();
        statement.close();
        if(plate.getId() < 0) {
            PreparedStatement statement1 = limsConnection.isLocal() ? connection.prepareStatement("CALL IDENTITY();") : connection.prepareStatement("SELECT last_insert_id()");;
            ResultSet resultSet = statement1.executeQuery();
            resultSet.next();
            int plateId = resultSet.getInt(1);
            plate.setId(plateId);
            statement1.close();
        }

        //replace the images
        if(plate.gelImagesHaveBeenDownloaded()) { //don't modify the gel images if we haven't downloaded them from the server or looked at them...
            PreparedStatement deleteImagesStatement = connection.prepareStatement("DELETE FROM gelimages WHERE plate="+plate.getId());
            deleteImagesStatement.execute();
            for(GelImage image : plate.getImages()) {
                PreparedStatement statement1 = image.toSql(connection);
                statement1.execute();
                statement1.close();
            }
            deleteImagesStatement.close();
        }

        Reaction.saveReactions(plate.getReactions(), plate.getReactionType(), connection, progress);
    }

    private void isPlateValid(Plate plate, Connection connection) throws BadDataException, SQLException {
        if(plate.getName() == null || plate.getName().length() == 0) {
            throw new BadDataException("Pates cannot have empty names");
        }
        if(plate.getId() < 0) {
            PreparedStatement plateCheckStatement = connection.prepareStatement("SELECT name FROM plate WHERE name=?");
            plateCheckStatement.setString(1, plate.getName());
            if(plateCheckStatement.executeQuery().next()) {
                throw new BadDataException("A plate with the name '"+plate.getName()+"' already exists");
            }
            plateCheckStatement.close();
        }
        if(plate.getThermocycle() == null && plate.getReactionType() != Reaction.Type.Extraction) {
            throw new BadDataException("The plate has no thermocycle set");
        }
    }

    public Map<BiocodeUtilities.Well, WorkflowDocument> getWorkflowsForCycleSequencingPlate(String plateName) throws SQLException, DocumentOperationException {
        List<PlateDocument> plateList = limsConnection.getMatchingPlateDocuments(Query.Factory.createFieldQuery(LIMSConnection.PLATE_NAME_FIELD, Condition.EQUAL, plateName), null);
        if(plateList.size() == 0) {
            throw new DocumentOperationException("The plate '"+plateName+"' does not exist in the database.");
        }
        assert plateList.size() == 1;
        if(plateList.get(0).getPlate().getReactionType() != Reaction.Type.CycleSequencing) {
            throw new DocumentOperationException("The plate '"+plateName+"' is not a cycle sequencing plate.");
        }
        Plate plate = plateList.get(0).getPlate();

        List<Query> workflowNameQueries = new ArrayList<Query>();

        for(Reaction r : plate.getReactions()) {
            if(r.getWorkflow() != null) {
                workflowNameQueries.add(Query.Factory.createFieldQuery(LIMSConnection.WORKFLOW_NAME_FIELD, Condition.EQUAL, r.getWorkflow().getName()));
            }
        }
        List<WorkflowDocument> docs = limsConnection.getMatchingWorkflowDocuments(Query.Factory.createOrQuery(workflowNameQueries.toArray(new Query[workflowNameQueries.size()]), Collections.EMPTY_MAP), null);

        Map<BiocodeUtilities.Well, WorkflowDocument> workflows = new HashMap<BiocodeUtilities.Well, WorkflowDocument>();
        for(Reaction r : plate.getReactions()) {
            for(WorkflowDocument doc : docs) {
                Reaction mostRecentExtraction = doc.getMostRecentReaction(Reaction.Type.Extraction);
                assert mostRecentExtraction != null; //workflows should always have an extraction
                if(mostRecentExtraction != null && mostRecentExtraction.getFimsSample() != null && r.getFimsSample() != null && mostRecentExtraction.getFimsSample().getId().equals(r.getFimsSample().getId())) {
                    workflows.put(new BiocodeUtilities.Well(r.getLocationString()), doc);
                }
            }
        }
        return workflows;
    }

    public Map<BiocodeUtilities.Well, FimsSample> getFimsSamplesForCycleSequencingPlate(String plateName) throws SQLException{
        //step 1, query the plate record
        String query1 = "SELECT plate.size, plate.id FROM plate WHERE plate.name = ?";
        PreparedStatement statement1 = limsConnection.getConnection().prepareStatement(query1);
        statement1.setString(1, plateName);
        ResultSet resultSet1 = statement1.executeQuery();
        if(!resultSet1.next()) {
            return null;
        }
        int plateId = resultSet1.getInt("plate.id");


        int size = resultSet1.getInt("plate.size");
        Plate.Size sizeEnum = Plate.getSizeEnum(size);

        //step 2, get the relevant reaction record
        String query2 = "SELECT extraction.sampleId, cyclesequencing.location FROM cyclesequencing, plate, extraction WHERE cyclesequencing.extractionId = extraction.extractionId AND cyclesequencing.plate = ?";
        PreparedStatement statement2 = limsConnection.getConnection().prepareStatement(query2);
        statement2.setInt(1, plateId);
        ResultSet resultSet2 = statement2.executeQuery();
        Set<String> samplesToGet = new HashSet<String>();
        Map<String, Integer> tissueToLocationMap = new HashMap<String, Integer>();
        while(resultSet2.next()) {
            tissueToLocationMap.put(resultSet2.getString("extraction.sampleId"), resultSet2.getInt("cycleSequencing.location"));
            String sampleId = resultSet2.getString("extraction.sampleId");
            if(sampleId != null && sampleId.length() > 0) {
                samplesToGet.add(sampleId);
            }
        }
        statement1.close();
        statement2.close();

        //step 3 - get the fims samples from the fims database
        try {
            List<FimsSample> list = activeFIMSConnection.getMatchingSamples(samplesToGet);
            Map<BiocodeUtilities.Well, FimsSample> result = new HashMap<BiocodeUtilities.Well, FimsSample>();
            for(FimsSample sample : list) {
                Integer location = tissueToLocationMap.get(sample.getId());
                if(location != null) {
                    BiocodeUtilities.Well well = Plate.getWell(location, sizeEnum);
                    result.put(well, sample);
                }
            }
            return result;
        } catch (ConnectionException e) {
            if(e.getCause() instanceof SQLException){
                throw (SQLException)e.getCause();
            }
            e.printStackTrace();
            assert false;
        }
        return null;
    }

    public Map<String, String> getWorkflowIds(List<String> idsToCheck, Reaction.Type reactionType) throws SQLException{
        if(idsToCheck.size() == 0) {
            return Collections.emptyMap();
        }
        StringBuilder sqlBuilder = new StringBuilder();
        switch(reactionType) {
            case Extraction:
                throw new RuntimeException("You should not be adding extractions to existing workflows!");
            case PCR:
            case CycleSequencing:
                sqlBuilder.append("SELECT extraction.extractionId AS id, workflow.name AS workflow, workflow.id AS workflowId, extraction.date FROM extraction, workflow WHERE workflow.extractionId = extraction.id AND (");
                for (int i = 0; i < idsToCheck.size(); i++) {
                    sqlBuilder.append("extraction.extractionId = ? ");
                    if(i < idsToCheck.size()-1) {
                        sqlBuilder.append("OR ");
                    }
                }
                sqlBuilder.append(") ORDER BY extraction.date"); //make sure the most recent workflow is stored in the map
            default:
                break;
        }
        System.out.println(sqlBuilder.toString());
        PreparedStatement statement = limsConnection.getConnection().prepareStatement(sqlBuilder.toString());
        for (int i = 0; i < idsToCheck.size(); i++) {
            statement.setString(i+1, idsToCheck.get(i));
        }
        ResultSet results = statement.executeQuery();
        Map<String, String> result = new HashMap<String, String>();

        while(results.next()) {
            result.put(results.getString("id"), results.getString("workflow")/*new Workflow(results.getInt("workflowId"), results.getString("workflow"), results.getString("id"))*/);
        }
        statement.close();
        return result;
    }

    public Map<String, Workflow> getWorkflows(Collection<String> workflowIds) throws SQLException{
        if(workflowIds.size() == 0) {
            return Collections.emptyMap();
        }
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("SELECT workflow.name AS workflow, workflow.id AS workflowId, extraction.extractionId FROM workflow, extraction WHERE extraction.id = workflow.extractionId AND (");
        for(int i=0; i < workflowIds.size(); i++) {
            sqlBuilder.append("workflow.name = ? ");
            if(i < workflowIds.size()-1) {
                sqlBuilder.append("OR ");
            }
        }
        sqlBuilder.append(")");
        PreparedStatement statement = limsConnection.getConnection().prepareStatement(sqlBuilder.toString());
        int i=0;
        for (String s : workflowIds) {
            statement.setString(i+1, s);
            i++;
        }
        ResultSet results = statement.executeQuery();
        Map<String, Workflow> result = new HashMap<String, Workflow>();

        while(results.next()) {
            result.put(results.getString("workflow"), new Workflow(results.getInt("workflowId"), results.getString("workflow"), results.getString("extractionId")));
        }
        statement.close();
        return result;
    }

//    public Map<String, String> getTissueIdsFromBarcodes(List<String> barcodeIds) throws ConnectionException {
//        if(activeFIMSConnection == null) {
//            return Collections.emptyMap();
//        }
//
//        DocumentField barcodeField = activeFIMSConnection.getTissueBarcodeDocumentField();
//        DocumentField tissueField = activeFIMSConnection.getTissueSampleDocumentField();
//
//
//        Query[] queries = new Query[barcodeIds.size()];
//        for(int i=0; i < barcodeIds.size(); i++) {
//            queries[i] = Query.Factory.createFieldQuery(barcodeField, Condition.EQUAL, barcodeIds.get(i));
//        }
//
//        Query orQuery = Query.Factory.createOrQuery(queries, Collections.<String, Object>emptyMap());
//
//        List<FimsSample> samples = activeFIMSConnection.getMatchingSamples(orQuery);
//
//        Map<String, String> result = new HashMap<String, String>();
//        for(FimsSample sample : samples) {
//            result.put(""+sample.getFimsAttributeValue(barcodeField.getCode()), ""+sample.getFimsAttributeValue(tissueField.getCode()));
//        }
//
//        return result;
//    }

    public static interface BlockingProgress {
        public void setMessage(String s);
        public void dispose();
        public Component getComponentForOwner();

        public static final BlockingProgress EMPTY = new BlockingProgress() {
            public void setMessage(String s) {}

            public void dispose() {}

            public Component getComponentForOwner() {
                return null;
            }
        };
    }

    public static class BlockingDialog extends JDialog implements BlockingProgress {
        private String message;
        private JLabel label;

        public static BlockingDialog getDialog(String message, Component owner) {
            Window w = getParentFrame(owner);
            if(w instanceof JFrame) {
                return new BlockingDialog(message, (JFrame)w);
            }
            else if(w instanceof JDialog) {
                return new BlockingDialog(message, (JDialog)w);
            }
            return new BlockingDialog(message, GuiUtilities.getMainFrame());
        }

        private static Window getParentFrame(Component component) {
            if(component == null) {
                return null;
            }
            if(component instanceof Window) {
                return (Window)component;
            }
            if(component.getParent() != null) {
                return getParentFrame(component.getParent());
            }
            return null;
        }

        private BlockingDialog(String message, Frame owner){
            super(owner);
            if(owner != null) {
                setLocationRelativeTo(owner);
            }
            this.message = message;
            init();
        }

        private BlockingDialog(String message, Dialog owner){
            super(owner);
            if(owner != null) {
                setLocationRelativeTo(owner);
            }
            this.message = message;
            init();
        }

        private void init() {
            setUndecorated(true);
            setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
            setAlwaysOnTop(true);
            setModal(true);
            setTitle("Please Wait...");
            Container cp = getContentPane();
            cp.setLayout(new BoxLayout(cp, BoxLayout.Y_AXIS));
            AnimatedIcon activityIcon = AnimatedIcon.getActivityIcon();
            activityIcon.startAnimation();
            label = new JLabel(message, activityIcon, JLabel.LEFT);
            label.setBorder(new EmptyBorder(25,50,25,50));
            cp.add(label);
            if(cp instanceof JComponent) {
                ((JComponent)cp).setBorder(new LineBorder(Color.black));
            }
            pack();
        }

        public void setMessage(String s) {
            message = s;
            label.setText(message);
            pack();
        }

        public Component getComponentForOwner() {
            return this;
        }

    }
}
