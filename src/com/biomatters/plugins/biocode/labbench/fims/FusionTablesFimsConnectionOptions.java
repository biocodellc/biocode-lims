package com.biomatters.plugins.biocode.labbench.fims;

import com.biomatters.plugins.biocode.labbench.PasswordOptions;
import com.biomatters.plugins.biocode.XmlUtilities;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.options.PasswordOption;
import com.google.gdata.client.ClientLoginAccountType;
import com.google.gdata.client.GoogleService;
import com.google.gdata.client.Service;
import com.google.gdata.util.ServiceException;
import com.google.gdata.util.ContentType;

import javax.swing.*;
import java.util.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLEncoder;

/**
 * @author Steve
 * @version $Id$
 */
public class FusionTablesFimsConnectionOptions extends PasswordOptions{

    private GoogleService service = new GoogleService("fusiontables", "fusiontables.Biocode");
    static final List<Options.OptionValue> NO_FIELDS = Arrays.asList(new Options.OptionValue("None", "None"));

    private String TABLE_ID = "tableId";
    private String USERNAME = "username";
    private String PASSWORD = "password";
    private String TISSUE_ID = "tissueId";
    private String SPECIMEN_ID = "specimenId";
    private String STORE_PLATES = "storePlates";
    private String PLATE_NAME = "plateName";
    private String PLATE_WELL = "plateWell";
    private String TAX_FIELDS = "taxFields";
    private String TAX_COL = "taxCol";


    public FusionTablesFimsConnectionOptions() {
        super(FusionTablesFimsConnectionOptions.class);

        addLabel("<html>Choose the location of your excel file.<br>The first row should be column headers, and it should<br>have at least a tissue and specimen column.</html>");
        final StringOption username = addStringOption(USERNAME, "Username", "");
        final PasswordOption password = new PasswordOption(PASSWORD, "Password", true);
        addCustomOption(password);

        beginAlignHorizontally("Fusion Table ID:", false);
        final StringOption fileLocation = addStringOption(TABLE_ID, "", "");
        ButtonOption updateButton = addButtonOption("update", "", "Update");
        endAlignHorizontally();
        restorePreferences(); //to make sure that the field chooser boxes start out with the right values
        List<OptionValue> cols = NO_FIELDS;

        final ComboBoxOption<OptionValue> tissueId = addComboBoxOption(TISSUE_ID, "Tissue ID field:", cols, cols.get(0));

        final ComboBoxOption<OptionValue> specimenId = addComboBoxOption(SPECIMEN_ID, "Specimen ID field:", cols, cols.get(0));

        final BooleanOption storePlates = addBooleanOption(STORE_PLATES, "The FIMS database contains plate information", false);

        final ComboBoxOption<OptionValue> plateName = addComboBoxOption(PLATE_NAME, "Plate name field:", cols, cols.get(0));

        final ComboBoxOption<OptionValue> plateWell = addComboBoxOption(PLATE_WELL, "Well field:", cols, cols.get(0));

        storePlates.addDependent(plateName, true);
        storePlates.addDependent(plateWell, true);

        addLabel(" ");
        beginAlignHorizontally("", false);
        addLabel("Specify your taxonomy fields, in order of highest to lowest");
        ButtonOption autodetectTaxonomyButton = addButtonOption("autodetect", "", "Autodetect");
        endAlignHorizontally();
        Options taxonomyOptions = new Options(this.getClass());
        taxonomyOptions.beginAlignHorizontally("", false);
        final ComboBoxOption<OptionValue> taxCol = taxonomyOptions.addComboBoxOption(TAX_COL, "", cols, cols.get(0));
        taxonomyOptions.endAlignHorizontally();

        final MultipleOptions taxOptions = addMultipleOptions(TAX_FIELDS, taxonomyOptions, false);

        autodetectTaxonomyButton.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e) {
                taxOptions.restoreDefaults();
                int foundCount = 0;
                for(String s : BiocodeUtilities.taxonomyNames) {
                    for(OptionValue ov : taxCol.getPossibleOptionValues()) {
                        if(ov.getName().equalsIgnoreCase(s)) {
                            Options newOptions;
                            if(foundCount == 0) {
                                newOptions = taxOptions.getValues().get(0);
                            }
                            else {
                                newOptions = taxOptions.addValue(false);
                            }
                            ComboBoxOption<OptionValue> list = (ComboBoxOption<OptionValue>)newOptions.getOption(TAX_COL);
                            list.setValueFromString(ov.getName());
                            foundCount ++;
                        }
                    }
                }
            }
        });

        updateButton.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent ev) {
                update();
            }
        });   
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public void update() {
        final StringOption username = (StringOption)getOption(USERNAME);
        final PasswordOption password = (PasswordOption)getOption(PASSWORD);
        final StringOption tableId = (StringOption)getOption(TABLE_ID);
        final ComboBoxOption<OptionValue> tissueId = (ComboBoxOption<OptionValue>)getOption(TISSUE_ID);
        final ComboBoxOption<OptionValue> specimenId = (ComboBoxOption<OptionValue>)getOption(SPECIMEN_ID);
        final ComboBoxOption<OptionValue> plateName = (ComboBoxOption<OptionValue>)getOption(PLATE_NAME);
        final ComboBoxOption<OptionValue> plateWell = (ComboBoxOption<OptionValue>)getOption(PLATE_WELL);
        final MultipleOptions taxOptions = getMultipleOptions(TAX_FIELDS);
        
        
        List<OptionValue> newCols = null;
        try {
            service.setUserCredentials(username.getValue(), password.getPassword(), ClientLoginAccountType.GOOGLE);
            newCols = getTableColumns(tableId.getValue().length() > 0 ? tableId.getValue() : null, service, getPanel());
        } catch (IOException e) {
            e.printStackTrace();
            newCols = NO_FIELDS;
        } catch (ServiceException e) {
            e.printStackTrace();
            newCols = NO_FIELDS;
        }
        tissueId.setPossibleValues(newCols);
        specimenId.setPossibleValues(newCols);
        plateName.setPossibleValues(newCols);
        plateWell.setPossibleValues(newCols);
        for(Option option : taxOptions.getMasterOptions().getOptions()) {
            if(option instanceof ComboBoxOption) {
                ((ComboBoxOption)option).setPossibleValues(newCols);
            }
        }
        for(Options options : taxOptions.getValues()) {
            for(Option option : options.getOptions()) {
                if(option instanceof ComboBoxOption) {
                    ((ComboBoxOption)option).setPossibleValues(newCols);
                }
            }
        }
    }

    private static List<Options.OptionValue> getTableColumns(String tableId, GoogleService service, JComponent owner) throws IOException, ServiceException {
        if(tableId == null) {
            return NO_FIELDS;
        }
        List<DocumentField> decodedValues = getTableColumns(tableId, service);
        if(decodedValues.size() == 0) {
            return NO_FIELDS;
        }
        List<OptionValue> fields = new ArrayList<OptionValue>();
        for(DocumentField f : decodedValues) {
            fields.add(new OptionValue(f.getCode(), f.getName(), f.getDescription()));
        }
        return fields;
    }

    static List<DocumentField> getTableColumns(String tableId, GoogleService service) throws IOException, ServiceException {
        String query = "DESCRIBE "+tableId+"";
        URL url = new URL(FusionTablesFimsConnection.SERVICE_URL + "?sql=" + URLEncoder.encode(query, "UTF-8"));
        Service.GDataRequest request = service.getRequestFactory().getRequest(Service.GDataRequest.RequestType.QUERY, url, ContentType.TEXT_PLAIN);
        request.execute();

        BufferedReader reader = new BufferedReader(new InputStreamReader(request.getResponseStream()));
        String line;
        boolean firstTime = true;
        List<DocumentField> fields = new ArrayList<DocumentField>();

        while((line = reader.readLine()) != null) {
            String[] tokens = FusionTablesFimsConnection.tokenizeLine(line);
            if(firstTime) {//they send the col headers for some reason...
                firstTime = false;
                continue;
            }
            fields.add(new DocumentField(tokens[1], "", XmlUtilities.encodeXMLChars(tokens[1]), getClass(tokens[2]), false, false));
        }

        return fields;
    }

    private static Class getClass(String valueType) {
        if(valueType.equals("string")) {
            return String.class;
        }
        if(valueType.equals("number")) {
            return Double.class;
        }
        if(valueType.equals("datetime")) {
            return Date.class;
        }
        if(valueType.equals("location")) {
            return String.class;
        }
        throw new RuntimeException("Unrecognised value type: "+valueType);
    }

}
