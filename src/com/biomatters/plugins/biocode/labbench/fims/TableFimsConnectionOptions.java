package com.biomatters.plugins.biocode.labbench.fims;

import com.biomatters.plugins.biocode.labbench.PasswordOptions;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.XmlUtilities;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.options.PasswordOption;
import com.google.gdata.client.GoogleService;
import com.google.gdata.client.ClientLoginAccountType;
import com.google.gdata.client.Service;
import com.google.gdata.util.AuthenticationException;
import com.google.gdata.util.ContentType;
import com.google.gdata.util.ServiceException;

import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Date;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.net.URL;
import java.net.URLEncoder;

import org.virion.jam.util.SimpleListener;

/**
 * @author Steve
 * @version $Id$
 */
public abstract class TableFimsConnectionOptions extends PasswordOptions {
    static final List<OptionValue> NO_FIELDS = Arrays.asList(new Options.OptionValue("None", "None"));

    String TABLE_ID = "tableId";
    String USERNAME = "username";
    String PASSWORD = "password";
    private String TISSUE_ID = "tissueId";
    private String SPECIMEN_ID = "specimenId";
    private String STORE_PLATES = "storePlates";
    private String PLATE_NAME = "plateName";
    private String PLATE_WELL = "plateWell";
    private String TAX_FIELDS = "taxFields";
    private String TAX_COL = "taxCol";
    public static final String CONNECTION_OPTIONS_KEY = "connection";

    protected abstract PasswordOptions getConnectionOptions();


    protected abstract List<OptionValue> getTableColumns() throws IOException;

    protected abstract boolean updateAutomatically();


    public TableFimsConnectionOptions() {
        super(FusionTablesFimsConnectionOptions.class);

        Options connectionOptions = getConnectionOptions();
        addChildOptions(CONNECTION_OPTIONS_KEY, "", "", connectionOptions);
        if(updateAutomatically()) {
            connectionOptions.addChangeListener(new SimpleListener() {
                public void objectChanged() {
                    update();
                }
            });
        }
        else {
            ButtonOption updateButton = addButtonOption("update", "", "Update Columns");
            updateButton.addActionListener(new ActionListener(){
                public void actionPerformed(ActionEvent ev) {
                    update();
                }
            });
        }
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
                        if(ov.getLabel().equalsIgnoreCase(s)) {
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

    }

    @Override
    public String verifyOptionsAreValid() {
        PasswordOptions pOptions = (PasswordOptions)getChildOptions().get(CONNECTION_OPTIONS_KEY);
        return pOptions.verifyOptionsAreValid();
    }

    public String getTissueColumn() {
        return ((Options.OptionValue)getValue("tissueId")).getName();
    }

    public String getSpecimenColumn() {
        return ((Options.OptionValue)getValue("specimenId")).getName();
    }

    public boolean storePlates() {
        return (Boolean)getValue("storePlates");
    }

    public String getPlateColumn() {
        return ((Options.OptionValue)getValue("plateName")).getName();
    }

    public String getWellColumn() {
        return ((Options.OptionValue)getValue("plateWell")).getName();
    }

    @Override
    public Options getEnterPasswordOptions() {
        PasswordOptions pOptions = (PasswordOptions)getChildOptions().get(CONNECTION_OPTIONS_KEY);
        return pOptions.getEnterPasswordOptions();
    }

    @Override
    public void setPasswordsFromOptions(Options enterPasswordOptions) {
        PasswordOptions pOptions = (PasswordOptions)getChildOptions().get(CONNECTION_OPTIONS_KEY);
        pOptions.setPasswordsFromOptions(enterPasswordOptions);
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public void update() {
        final ComboBoxOption<OptionValue> tissueId = (ComboBoxOption<OptionValue>)getOption(TISSUE_ID);
        final ComboBoxOption<OptionValue> specimenId = (ComboBoxOption<OptionValue>)getOption(SPECIMEN_ID);
        final ComboBoxOption<OptionValue> plateName = (ComboBoxOption<OptionValue>)getOption(PLATE_NAME);
        final ComboBoxOption<OptionValue> plateWell = (ComboBoxOption<OptionValue>)getOption(PLATE_WELL);
        final MultipleOptions taxOptions = getMultipleOptions(TAX_FIELDS);


        List<OptionValue> newCols = null;
        try {
            newCols = getTableColumns();
        } catch (IOException e) {
            //todo: exception handling...
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
}