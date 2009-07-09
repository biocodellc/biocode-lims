package com.biomatters.plugins.moorea;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.databaseservice.*;
import com.biomatters.geneious.publicapi.documents.*;
import com.biomatters.geneious.publicapi.plugin.GeneiousAction;
import com.biomatters.geneious.publicapi.plugin.Icons;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.utilities.IconUtilities;
import com.biomatters.geneious.publicapi.utilities.GuiUtilities;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import com.biomatters.plugins.moorea.fims.FIMSConnection;
import com.biomatters.plugins.moorea.fims.GeneiousFimsConnection;
import com.biomatters.plugins.moorea.fims.MooreaFimsConnection;
import com.biomatters.plugins.moorea.fims.TAPIRFimsConnection;
import com.biomatters.plugins.moorea.lims.LIMSConnection;
import com.biomatters.plugins.moorea.reaction.*;
import com.biomatters.plugins.moorea.plates.Plate;
import com.biomatters.plugins.moorea.plates.GelImage;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.event.ActionEvent;
import java.awt.*;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.FileOutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.*;
import java.util.*;
import java.util.List;
import java.util.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

import org.jdom.input.SAXBuilder;
import org.jdom.JDOMException;
import org.jdom.Element;
import org.jdom.output.XMLOutputter;
import org.jdom.output.Format;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 23/02/2009 4:41:26 PM
 */
public class MooreaLabBenchService extends DatabaseService {
    private boolean isLoggedIn = false;
    private FIMSConnection activeFIMSConnection;
    private LIMSConnection limsConnection = new LIMSConnection();
    private String loggedOutMessage = "Right click on the " + getName() + " service in the service tree to log in.";
    static Driver driver;
    private static MooreaLabBenchService instance = null;
    public static final Map<String, Image[]> imageCache = new HashMap<String, Image[]>();
    private File dataDirectory;

    public static final DateFormat dateFormat = SimpleDateFormat.getDateInstance(SimpleDateFormat.MEDIUM);

    private MooreaLabBenchService() {
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

    private void loadEmptyCaches() {
        cycleSequencingCocktails = Collections.EMPTY_LIST;
        PCRCocktails = Collections.EMPTY_LIST;
        PCRThermocycles = Collections.EMPTY_LIST;
        cycleSequencingThermocycles = Collections.EMPTY_LIST;
    }

    public static MooreaLabBenchService getInstance() {
        if(instance == null) {
            instance = new MooreaLabBenchService();
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
        try {
            activeFIMSConnection.disconnect();
        } catch (ConnectionException e1) {
            Dialogs.showMessageDialog("Could not disconnect from " + activeFIMSConnection.getLabel() + ": " + e1.getMessage());
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
        FIMSOptions.addChildOptionsPageChooser("fims", "Field Database Connection", Collections.EMPTY_LIST, Options.PageChooserType.COMBO_BOX, false);

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

        if (Dialogs.showOkCancelDialog(loginOptions.getPanel(), "Log in", null)) {
            loginOptions.savePreferences();

            //load the connection driver -------------------------------------------------------------------
            String driverFileName = (String) loginOptions.getValue("driver");

            ClassLoader loader;
            try {
                URL driverUrl = new File(driverFileName).toURL();
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
                error = "Could not find driver class";
            } catch (IllegalAccessException e1) {
                error = "Could not access driver class";
            } catch (InstantiationException e1) {
                error = "Could not instantiate driver class";
            } catch (ClassCastException e1) {
                error = "Driver class exists, but is not an SQL driver";
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
                        Dialogs.showMessageDialog("Could not connect to " + activeFIMSConnection.getLabel());
                        logOut();
                        unBlock();
                        return;
                    }

                    try {
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
                    compoundQuery = Query.Factory.createAndQuery(fimsQueries.toArray(new Query[fimsQueries.size()]), Collections.EMPTY_MAP);
                }
                else {
                    compoundQuery = Query.Factory.createOrQuery(fimsQueries.toArray(new Query[fimsQueries.size()]), Collections.EMPTY_MAP);
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
            }
            else {
                limsQueries.add(query);
            }
        }

        if(tissueSamples != null) {
            for(FimsSample sample : tissueSamples) {
                TissueDocument doc = new TissueDocument(sample);
                callback.add(doc, Collections.EMPTY_MAP);
            }
        }
        try {
            List<WorkflowDocument> workflowList = limsConnection.getMatchingWorkflowDocuments(Query.Factory.createAndQuery(limsQueries.toArray(new Query[limsQueries.size()]), Collections.EMPTY_MAP), tissueSamples);
            for(PluginDocument doc : workflowList) {
                callback.add(doc, Collections.EMPTY_MAP);
            }
            List<PlateDocument> plateList = limsConnection.getMatchingPlateDocuments(Query.Factory.createAndQuery(limsQueries.toArray(new Query[limsQueries.size()]), Collections.EMPTY_MAP), workflowList);
            for(PluginDocument doc : plateList) {
                callback.add(doc, Collections.EMPTY_MAP);
            }

        } catch (SQLException e) {
            throw new DatabaseServiceException(e, e.getMessage(), true);
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
    private List<Thermocycle> cycleSequencingThermocycles = null;
    private List<Cocktail> PCRCocktails = null;
    private List<Cocktail> cycleSequencingCocktails = null;

    private void buildCaches() throws TransactionException {
        PCRThermocycles = getThermocyclesFromDatabase("pcr_thermocycle");
        cycleSequencingThermocycles = getThermocyclesFromDatabase("cycleSequencing_thermocycle");
        PCRCocktails = getPCRCocktailsFromDatabase();
        cycleSequencingCocktails = getCycleSequencingCocktailsFromDatabase();
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
        cycleSequencingThermocycles = getThermocyclesFromDisk("cycleSequencing_thermocycle");
        PCRCocktails = getPCRCocktailsFromDisk();
        cycleSequencingCocktails = getCycleSequencingCocktailsFromDisk();
    }

    private void saveCachesToDisk() throws IOException, JDOMException, XMLSerializationException {
        saveThermocyclesToDisk("pcr_thermocycle", PCRThermocycles);
        saveThermocyclesToDisk("cycleSequencing_thermocycle", cycleSequencingThermocycles);
        savePCRCocktailsToDisk();
        saveCycleSequencingCocktailsToDisk();
    }

    private List<Cocktail> getCycleSequencingCocktailsFromDisk() throws JDOMException, IOException, XMLSerializationException{
        File file = new File(dataDirectory, "cycleSequencingCocktails.xml");
        return getCocktails(file);
    }

    private List<Cocktail> getPCRCocktailsFromDisk() throws IOException, JDOMException, XMLSerializationException {
        File file = new File(dataDirectory, "PCRCocktails.xml");
        return getCocktails(file);
    }

    private List<Cocktail> getCocktails(File file) throws JDOMException, IOException, XMLSerializationException {
        if(!file.exists()) {
            return Collections.EMPTY_LIST;
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
            return Collections.EMPTY_LIST;
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
        File file = new File(dataDirectory, "cycleSequencingCocktails.xml");
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
        saveCocktails(file, cycleSequencingCocktails);
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
        }
        catch(SQLException ex) {
            throw new TransactionException("Could not query PCR Cocktails from the database");
        }
        return cocktails;
    }

    private List<Cocktail> getCycleSequencingCocktailsFromDatabase() throws TransactionException{
        ResultSet resultSet = limsConnection.executeQuery("SELECT * FROM cycleSequencing_cocktail");
        List<Cocktail> cocktails = new ArrayList<Cocktail>();
        try {
            while(resultSet.next()) {
                cocktails.add(new CycleSequencingCocktail(resultSet));    
            }
        }
        catch(SQLException ex) {
            throw new TransactionException("Could not query CycleSequencing Cocktails from the database");
        }
        return cocktails;
    }

    private List<Thermocycle> getThermocyclesFromDatabase(String thermocycleIdentifierTable) throws TransactionException {
        String sql = "SELECT * FROM "+thermocycleIdentifierTable+" LEFT JOIN (thermocycle, cycle, state) ON (thermocycleid = "+thermocycleIdentifierTable+".cycle AND thermocycle.id = cycle.thermocycleId AND cycle.id = state.cycleId);";


        ResultSet resultSet = limsConnection.executeQuery(sql);

        List<Thermocycle> tCycles = new ArrayList<Thermocycle>();

        try {
            while(resultSet.next()) {
                tCycles.add(Thermocycle.fromSQL(resultSet));
                resultSet.previous();
            }
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
        cycles.addAll(cycleSequencingThermocycles);
        return cycles;
    }

    public void addPCRThermoCycles(List<Thermocycle> cycles) throws TransactionException{
        insertThermocycles(cycles, "pcr_thermocycle");
    }

    private void insertThermocycles(List<Thermocycle> cycles, String tableName) throws TransactionException {
        try {
            Connection connection = limsConnection.getConnection();
            connection.setAutoCommit(false);
            for(Thermocycle tCycle : cycles) {
                Savepoint savepoint = connection.setSavepoint();
                try {
                    int id = tCycle.toSQL(connection);
                    PreparedStatement statement = connection.prepareStatement("INSERT INTO "+tableName+" (cycle) VALUES ("+id+");\n");
                    statement.execute();
                    connection.commit();
                }
                catch(SQLException ex) {
                    connection.rollback(savepoint);
                    throw ex;
                }
                finally {
                    connection.setAutoCommit(true);
                }
            }
        } catch (SQLException e) {
            throw new TransactionException("Could not add thermocycle(s): "+e.getMessage(), e);
        }
        buildCaches();
    }

    public void addCycleSequencingThermoCycles(List<Thermocycle> cycles) throws TransactionException{
        insertThermocycles(cycles, "cycleSequencing_thermocycle");
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
                    unBlock();
                }
            }
        };
        new Thread(r).start();
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

    public List<Cocktail> getPCRCocktails() {
        return PCRCocktails;
    }

    public List<Cocktail> getCycleSequencingCocktails() {
        return cycleSequencingCocktails;
    }

    public Map<String, String> getReactionToTissueIdMapping(String tableName, List<? extends Reaction> reactions) throws SQLException{
        if(reactions.size() == 0) {
            return Collections.EMPTY_MAP;
        }
        String tableDefinition = tableName.equals("extraction") ? tableName : tableName+", extraction, workflow";
        String notExtractionBit = tableName.equals("extraction") ? "" : " workflow.extractionId = extraction.id AND " + tableName + ".workflow = workflow.id AND";
        StringBuilder sql = new StringBuilder("SELECT extraction.extractionId AS extractionId, extraction.sampleId AS tissue FROM " + tableDefinition + " WHERE" + notExtractionBit + " (");

        for(int i=0; i < reactions.size(); i++) {
            if(i > 0) {
                sql.append(" OR ");
            }
            sql.append("extraction.extractionId=?");
        }
        sql.append(")");
        PreparedStatement statement = limsConnection.getConnection().prepareStatement(sql.toString());
        for(int i=0; i < reactions.size(); i++) {
            statement.setString(i+1, reactions.get(i).getExtractionId());
        }

        ResultSet resultSet = statement.executeQuery();

        Map<String, String> results = new HashMap<String, String>();
        while(resultSet.next()) {
            results.put(resultSet.getString("extractionId"), resultSet.getString("tissue"));
        }

        return results;
    }

    public List<Workflow> createWorkflows(List<String> extractionIds, BlockingDialog progress) throws SQLException{
        List<Workflow> workflows = new ArrayList<Workflow>();
        Connection connection = limsConnection.getConnection();
        Savepoint savepoint = connection.setSavepoint();
        try {
            connection.setAutoCommit(false);
            PreparedStatement statement = connection.prepareStatement("INSERT INTO workflow(extractionId) VALUES ((SELECT extraction.id from extraction where extraction.extractionId = ?))");
            PreparedStatement statement2 = connection.prepareStatement("SELECT last_insert_id()");
            PreparedStatement statement3 = connection.prepareStatement("UPDATE workflow SET name = CONCAT('workflow', id) WHERE id=?");
            for(int i=0; i < extractionIds.size(); i++) {
                if(progress != null) {
                    progress.setMessage("Creating new workflow "+(i+1)+" of "+extractionIds.size());
                }
                statement.setString(1, extractionIds.get(i));
                statement.execute();
                ResultSet resultSet = statement2.executeQuery();
                resultSet.next();
                int workflowId = resultSet.getInt(1);
                workflows.add(new Workflow(workflowId, "workflow"+workflowId, extractionIds.get(i)));
                statement3.setInt(1, workflowId);
                statement3.execute();
            }
            connection.commit();
            connection.setAutoCommit(true);
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
            connection.setAutoCommit(true);
        }
    }

    public void saveExtractions(MooreaLabBenchService.BlockingDialog progress, Plate plate) throws SQLException, BadDataException{
        List<String> extractionIds = new ArrayList<String>();

        Connection connection = limsConnection.getConnection();
        Savepoint savepoint = connection.setSavepoint();
        try {
            connection.setAutoCommit(false);
            List<Reaction> reactionsToSave = new ArrayList<Reaction>();
            for(Reaction reaction : plate.getReactions()) {
                if(!reaction.isEmpty() && (reaction.getId() < 0)) {
                    extractionIds.add(reaction.getExtractionId());
                    reactionsToSave.add(reaction);
                }
            }

            if(reactionsToSave.size() == 0) {
                throw new BadDataException("You need to save at least one reaction with your plate");
            }

            String error = reactionsToSave.get(0).areReactionsValid(reactionsToSave);
            if(error != null && error.length() > 0) {
                throw new BadDataException(error);
            }

            createOrUpdatePlate(plate, progress);

            progress.setMessage("Creating new workflows");

            //create workflows if necessary

            if(extractionIds.size() > 0) {
                List<Workflow> workflowList = MooreaLabBenchService.getInstance().createWorkflows(extractionIds, progress);
                int workflowIndex = 0;
                for(Reaction reaction : plate.getReactions()) {
                    if(!reaction.isEmpty() && (reaction.getWorkflow() == null || reaction.getWorkflow().getId() < 0)) {
                        reaction.setWorkflow(workflowList.get(workflowIndex));
                        workflowIndex++;
                    }
                }
            }
            connection.commit();
            connection.releaseSavepoint(savepoint);
        } catch(BadDataException e) {
            try {
                connection.rollback(savepoint);
            } catch (SQLException e1) {} //ignore - if this fails, then we are already rolled back.
            finally {
                throw e;
            }
        } finally {
            connection.setAutoCommit(true);
        }


    }

    public void saveReactions(MooreaLabBenchService.BlockingDialog progress, Plate plate) throws SQLException, BadDataException {
        progress.setMessage("Retrieving existing workflows");
        Connection connection = limsConnection.getConnection();
        Savepoint savepoint = connection.setSavepoint();
        int originalPlateId = plate.getId();
        try {
            connection.setAutoCommit(false);

            //set workflows for reactions that have id's
            List<Reaction> reactionsToSave = new ArrayList<Reaction>();
            List<String> workflowIdStrings = new ArrayList<String>();
            for(Reaction reaction : plate.getReactions()) {
                Object workflowId = reaction.getFieldValue("workflowId");
                if(!reaction.isEmpty() && workflowId != null && workflowId.toString().length() > 0 && reaction.getType() != Reaction.Type.Extraction) {
                    reactionsToSave.add(reaction);
                    if(reaction.getWorkflow() != null && reaction.getWorkflow().getName().equals(workflowId)){
                        continue;
                    }
                    else {
                        reaction.setWorkflow(null);
                        workflowIdStrings.add(workflowId.toString());
                    }
                }
            }

            if(reactionsToSave.size() == 0) {
                throw new BadDataException("You need to save at least one reaction with your plate");
            }

            String error = reactionsToSave.get(0).areReactionsValid(reactionsToSave);
            if(error != null && error.length() > 0) {
                throw new BadDataException(error);
            }

            if(workflowIdStrings.size() > 0) {
                Map<String,Workflow> map = MooreaLabBenchService.getInstance().getWorkflows(workflowIdStrings);
                for(Reaction reaction : plate.getReactions()) {
                    Object workflowId = reaction.getFieldValue("workflowId");
                    if(reaction.getWorkflow() == null){
                        reaction.setWorkflow(map.get(workflowId));
                    }
                }
            }

            progress.setMessage("Creating new workflows");

            //create workflows if necessary
            //int workflowCount = 0;
            List<String> extractionIds = new ArrayList<String>();
            for(Reaction reaction : plate.getReactions()) {
                if(!reaction.isEmpty() && (reaction.getWorkflow() == null || reaction.getWorkflow().getId() < 0)) {
                    extractionIds.add(reaction.getExtractionId());
                }
            }
            if(extractionIds.size() > 0) {
                List<Workflow> workflowList = MooreaLabBenchService.getInstance().createWorkflows(extractionIds, progress);
                int workflowIndex = 0;
                for(Reaction reaction : plate.getReactions()) {
                    if(!reaction.isEmpty() && (reaction.getWorkflow() == null || reaction.getWorkflow().getId() < 0)) {
                        reaction.setWorkflow(workflowList.get(workflowIndex));
                        workflowIndex++;
                    }
                }
            }

            progress.setMessage("Creating the plate");
            //we need to create the plate
            createOrUpdatePlate(plate, progress);
            connection.commit();
            connection.releaseSavepoint(savepoint);
        } catch(BadDataException e) {
            plate.setId(originalPlateId);
            try {
                connection.rollback(savepoint);
            } catch (SQLException e1) {} //ignore - if this fails, then we are already rolled back.
            finally {
                throw e;
            }
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private void createOrUpdatePlate(Plate plate, BlockingDialog progress) throws SQLException, BadDataException{
        Connection connection = limsConnection.getConnection();

        //check the vaidity of the plate.
        if(plate.getName() == null || plate.getName().length() == 0) {
            throw new BadDataException("Pates cannot have empty names");
        }
        if(plate.getId() < 0) {
            PreparedStatement plateCheckStatement = connection.prepareStatement("SELECT name FROM plate WHERE name=?");
            plateCheckStatement.setString(1, plate.getName());
            if(plateCheckStatement.executeQuery().next()) {
                throw new BadDataException("A plate with the name '"+plate.getName()+"' already exists");
            }
        }
        if(plate.getThermocycle() == null && plate.getReactionType() != Reaction.Type.Extraction) {
            throw new BadDataException("The plate has no thermocycle set");
        }

        //update the plate
        PreparedStatement statement = plate.toSQL(connection);
        statement.execute();
        if(plate.getId() < 0) {
            PreparedStatement statement1 = connection.prepareStatement("SELECT last_insert_id()");
            ResultSet resultSet = statement1.executeQuery();
            resultSet.next();
            int plateId = resultSet.getInt(1);
            plate.setId(plateId);
        }

        //replace the images
        PreparedStatement deleteImagesStatement = connection.prepareStatement("DELETE FROM gelImages WHERE plate="+plate.getId());
        deleteImagesStatement.execute();
        for(GelImage image : plate.getImages()) {
            image.toSql(connection).execute();
        }

        Reaction.saveReactions(plate.getReactions(), plate.getReactionType(), connection, progress);
    }

    public Map<String, Workflow> getWorkflows(List<String> idsToCheck, Reaction.Type reactionType) throws SQLException{
        if(idsToCheck.size() == 0) {
            return Collections.EMPTY_MAP;
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
        Map<String, Workflow> result = new HashMap<String, Workflow>();

        while(results.next()) {
            result.put(results.getString("id"), new Workflow(results.getInt("workflowId"), results.getString("workflow"), results.getString("id")));
        }
        return result;
    }

    public Map<String, Workflow> getWorkflows(List<String> workflowIds) throws SQLException{
        if(workflowIds.size() == 0) {
            return Collections.EMPTY_MAP;
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
        for (int i = 0; i < workflowIds.size(); i++) {
            statement.setString(i+1, workflowIds.get(i));
        }
        ResultSet results = statement.executeQuery();
        Map<String, Workflow> result = new HashMap<String, Workflow>();

        while(results.next()) {
            result.put(results.getString("workflow"), new Workflow(results.getInt("workflowId"), results.getString("workflow"), results.getString("extractionId")));
        }
        return result;
    }

    public static class BlockingDialog extends JDialog {
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


    }
}
