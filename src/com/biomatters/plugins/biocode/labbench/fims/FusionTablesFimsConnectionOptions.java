package com.biomatters.plugins.biocode.labbench.fims;

import com.biomatters.plugins.biocode.labbench.PasswordOptions;
import com.biomatters.plugins.biocode.XmlUtilities;
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
import java.util.regex.MatchResult;
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
    private String lastTableId;
    
    
    public FusionTablesFimsConnectionOptions() {
        super(FusionTablesFimsConnectionOptions.class);

        addLabel("<html>Choose the location of your excel file.<br>The first row should be column headers, and it should<br>have at least a tissue and specimen column.</html>");
        final StringOption username = addStringOption("username", "Username", "");
        final PasswordOption password = new PasswordOption("password", "Password", true);
        addCustomOption(password);

        beginAlignHorizontally("Fusion Table ID:", false);
        final StringOption fileLocation = addStringOption("tableId", "", "");
        ButtonOption updateButton = addButtonOption("update", "", "Update");
        endAlignHorizontally();
        restorePreferences(); //to make sure that the field chooser boxes start out with the right values
        List<OptionValue> cols = NO_FIELDS;

        final ComboBoxOption<OptionValue> tissueId = addComboBoxOption("tissueId", "Tissue ID field:", cols, cols.get(0));

        final ComboBoxOption<OptionValue> specimenId = addComboBoxOption("specimenId", "Specimen ID field:", cols, cols.get(0));

        final BooleanOption storePlates = addBooleanOption("storePlates", "The FIMS database contains plate information", false);

        final ComboBoxOption<OptionValue> plateName = addComboBoxOption("plateName", "Plate name field:", cols, cols.get(0));

        final ComboBoxOption<OptionValue> plateWell = addComboBoxOption("plateWell", "Well field:", cols, cols.get(0));

        storePlates.addDependent(plateName, true);
        storePlates.addDependent(plateWell, true);

        addLabel(" ");
        addLabel("Specify your taxonomy fields, in order of highest to lowest");
        Options taxonomyOptions = new Options(this.getClass());
        taxonomyOptions.beginAlignHorizontally("", false);
        final ComboBoxOption<OptionValue> taxCol = taxonomyOptions.addComboBoxOption("taxCol", "", cols, cols.get(0));
        taxonomyOptions.endAlignHorizontally();

        final MultipleOptions taxOptions = addMultipleOptions("taxFields", taxonomyOptions, false);

        updateButton.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent ev) {
                List<OptionValue> newCols = null;
                lastTableId = fileLocation.getValue();
                try {
                    service.setUserCredentials(username.getValue(), password.getPassword(), ClientLoginAccountType.GOOGLE);
                    newCols = getTableColumns(fileLocation.getValue().length() > 0 ? fileLocation.getValue() : null, service, getPanel());
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
                taxCol.setPossibleValues(newCols);
                for(Options options : taxOptions.getValues()) {
                    for(Option option : getOptions()) {
                        if(option instanceof ComboBoxOption) {
                            ((ComboBoxOption)option).setPossibleValues(newCols);
                        }
                    }
                }
            }
        });   
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
            System.out.println(line);
            String[] tokens = line.split(",");
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
        throw new RuntimeException("Unrecognised value type: "+valueType);
    }

}
