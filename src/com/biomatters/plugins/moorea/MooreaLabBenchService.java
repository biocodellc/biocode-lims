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
            for(DocumentField field : fimsAttributes) {
                Condition[] conditions = getFieldConditions(field.getClass());
                fieldList.add(new SourceAwareQueryField(field, conditions, activeFIMSConnection.getName()));
            }
        }


        return fieldList.toArray(new QueryField[fieldList.size()]);
    }

    private Condition[] getFieldConditions(Class fieldClass) {
        if(Integer.class.isAssignableFrom(fieldClass) || Double.class.isAssignableFrom(fieldClass)) {
            return new Condition[] {
                    Condition.EQUAL,
                    Condition.NOT_EQUAL,
                    Condition.GREATER_THAN,
                    Condition.GREATER_THAN_OR_EQUAL_TO,
                    Condition.LESS_THAN,
                    Condition.LESS_THAN_OR_EQUAL_TO
            };
        }
        else if(String.class.isAssignableFrom(fieldClass)) {
            return new Condition[] {
                    Condition.EQUAL,
                    Condition.APPROXIMATELY_EQUAL,
                    Condition.CONTAINS,
                    Condition.NOT_EQUAL,
                    Condition.NOT_CONTAINS
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

                    if(Dialogs.showOkCancelDialog(loginOptions.getPanel(), "Log in", null)) {
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
                        properties.put("password", LIMSOptions.getValueAsString("password"));
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
        ExperimentDocument document = new ExperimentDocument("PL005");

        //add notes
        DocumentNoteType specimen = DocumentNoteUtilities.getNoteType("moorea_specimem");
        if(specimen == null) {
            specimen = DocumentNoteUtilities.createNewNoteType("Specimen", "moorea_specimem", "", Arrays.asList(DocumentNoteField.createTextNoteField("Organism", "", "organism", Collections.EMPTY_LIST, false)), true);
            DocumentNoteUtilities.setNoteType(specimen);
        }
        DocumentNoteType sample = DocumentNoteUtilities.getNoteType("moorea_sample");
        if(sample == null) {
            sample = DocumentNoteUtilities.createNewNoteType("Tissue Sample", "moorea_sample", "", Arrays.asList(DocumentNoteField.createTextNoteField("Sample ID", "", "id", Collections.EMPTY_LIST, false), DocumentNoteField.createIntegerNoteField("Freezer", "", "freezer", Collections.EMPTY_LIST, false), DocumentNoteField.createIntegerNoteField("Shelf", "", "shelf", Collections.EMPTY_LIST, false)), true);
            DocumentNoteUtilities.setNoteType(sample);
        }
        DocumentNoteType extraction = DocumentNoteUtilities.getNoteType("moorea_extraction");
        if(extraction == null) {
            extraction = DocumentNoteUtilities.createNewNoteType("DNA Extraction", "moorea_extraction", "", Arrays.asList(
                    DocumentNoteField.createTextNoteField("Plate ID", "", "plate", Collections.EMPTY_LIST, false),
                    DocumentNoteField.createDateNoteField("Extraction Date", "", "date", Collections.EMPTY_LIST, false),
                    DocumentNoteField.createTextNoteField("Extracted By", "", "extractor", Collections.EMPTY_LIST, false),
                    DocumentNoteField.createEnumeratedNoteField(new String[] {"Method 1", "Method 2", "Method 3"}, "Extraction Method", "", "plate", false),
                    DocumentNoteField.createIntegerNoteField("Well Location", "", "location", Collections.EMPTY_LIST, false),
                    DocumentNoteField.createTextNoteField("Suspension Solution", "", "solution", Collections.EMPTY_LIST, false),
                    DocumentNoteField.createTextNoteField("Plate ID", "", "plate", Collections.EMPTY_LIST, false),
                    DocumentNoteField.createIntegerNoteField("Freezer", "", "freezer", Collections.EMPTY_LIST, false),
                    DocumentNoteField.createIntegerNoteField("Shelf", "", "shelf", Collections.EMPTY_LIST, false),
                    DocumentNoteField.createIntegerNoteField("Extra data", "", "extra_data", Collections.EMPTY_LIST, false)
            ), false);
            DocumentNoteUtilities.setNoteType(extraction);
        }
        DocumentNoteType pcr = DocumentNoteUtilities.getNoteType("moorea_pcr");
        if(pcr == null) {
            pcr = DocumentNoteUtilities.createNewNoteType("PCR Amplification", "moorea_pcr", "", Arrays.asList(DocumentNoteField.createEnumeratedNoteField(new String[] {"Passed", "Failed", "None"}, "Outcome", "", "outcome", false), DocumentNoteField.createIntegerNoteField("Freezer", "", "freezer", Collections.EMPTY_LIST, false), DocumentNoteField.createIntegerNoteField("Shelf", "", "shelf", Collections.EMPTY_LIST, false)), true);
            DocumentNoteUtilities.setNoteType(pcr);
        }

        List<DocumentNote> notes = new ArrayList<DocumentNote>();



        try {
            InputStream resource = getClass().getResourceAsStream("TextAbiDocuments.xml");
            List<NucleotideSequenceDocument> docs = new ArrayList<NucleotideSequenceDocument>();
            SAXBuilder builder = new SAXBuilder();
            Element root = builder.build(resource).detachRootElement();
            for(Element e : root.getChildren()) {
                docs.add(XMLSerializer.classFromXML(e, NucleotideSequenceDocument.class));
            }
            document.setNucleotideSequences(docs);
        } catch (JDOMException e) {
            throw new DatabaseServiceException("arse2", false);
        } catch (IOException e) {
            throw new DatabaseServiceException("arse3", false);
        } catch (XMLSerializationException e) {
            throw new DatabaseServiceException("arse4", false);
        }


        AnnotatedPluginDocument apd = DocumentUtilities.createAnnotatedPluginDocument(document);

        AnnotatedPluginDocument.DocumentNotes documentNotes = apd.getDocumentNotes(true);

        DocumentNote specimenNote = specimen.createDocumentNote();
        documentNotes.setNote(specimenNote);

        DocumentNote sampleNote = sample.createDocumentNote();
        documentNotes.setNote(sampleNote);

        DocumentNote extractionNote = extraction.createDocumentNote();
        documentNotes.setNote(extractionNote);

        DocumentNote pcrNote = pcr.createDocumentNote();
        documentNotes.setNote(pcrNote);

        documentNotes.saveNotes();

        callback.add(apd, Collections.EMPTY_MAP);
    }

    public String getUniqueID() {
        return "MooreaLabBenchService";
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
