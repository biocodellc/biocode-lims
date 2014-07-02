package com.biomatters.plugins.biocode.labbench.fims;

import com.biomatters.geneious.publicapi.components.ProgressFrame;
import com.biomatters.geneious.publicapi.plugin.Geneious;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import com.biomatters.plugins.biocode.labbench.PasswordOptions;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.utilities.IconUtilities;

import java.util.*;
import java.io.IOException;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.virion.jam.util.SimpleListener;

import javax.swing.*;

/**
 * @author Steve
 * @version $Id$
 */
public abstract class TableFimsConnectionOptions extends PasswordOptions {
    static final List<OptionValue> NO_FIELDS = Arrays.asList(new Options.OptionValue("None", "None"));

    static final String TABLE_ID = "tableId";
    public static final String TISSUE_ID = "tissueId";
    public static final String SPECIMEN_ID = "specimenId";
    public static final String STORE_PLATES = "storePlates";
    public static final String PLATE_NAME = "plateName";
    public static final String PLATE_WELL = "plateWell";
    public static final String TAX_FIELDS = "taxFields";
    public static final String TAX_COL = "taxCol";
    public static final String CONNECTION_OPTIONS_KEY = "connection";

    public static final String STORE_PROJECTS = "storeProjects";
    public static final String PROJECT_COLUMN = "projectColumn";
    public static final String PROJECT_FIELDS = "projectFields";

    protected abstract PasswordOptions getConnectionOptions();

    final List<OptionValue> getTableColumns() throws IOException {
        List<OptionValue> list = new ArrayList<OptionValue>(_getTableColumns());
        Collections.sort(list, new Comparator<OptionValue>() {
            @Override
            public int compare(OptionValue o1, OptionValue o2) {
                return o1.getLabel().toLowerCase().compareTo(o2.getLabel().toLowerCase());
            }
        });
        return list;
    }

    protected abstract List<OptionValue> _getTableColumns() throws IOException;

    protected abstract boolean updateAutomatically();


    public TableFimsConnectionOptions() {
        super(FusionTablesFimsConnectionOptions.class);

        final PasswordOptions connectionOptions = getConnectionOptions();
        addChildOptions(CONNECTION_OPTIONS_KEY, "", "", connectionOptions);
        restorePreferences(); //to make sure that the field chooser boxes start out with the right values
        List<OptionValue> cols = NO_FIELDS;

        final ComboBoxOption<OptionValue> tissueId = addComboBoxOption(TISSUE_ID, "Tissue ID field:", cols, cols.get(0));

        beginAlignHorizontally("Specimen ID field:", false);
        final ComboBoxOption<OptionValue> specimenId = addComboBoxOption(SPECIMEN_ID, "", cols, cols.get(0));
        addBooleanOption("flickrPhotos", "Specimen photos on Flickr", false);
        ButtonOption buttonOption = addButtonOption("flickrHelp", "", "", Geneious.isHeadless() ? null : IconUtilities.getIcons("help16.png").getIcon16(), JButton.LEFT);
        buttonOption.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e) {
                String message = "<html>If you validate your specimen/tissue data using <i>BioValidator</i> (available from <a href=\"http://biovalidator.sourceforge.net/\">http://biovalidator.sourceforge.net/</a>), any photos that it uploads to Flickr will be automatically tagged for search by the Biocode LIMS plugin.</html>";
                Dialogs.DialogOptions dialogOptions = new Dialogs.DialogOptions(Dialogs.OK_ONLY, "Information", (Component)e.getSource(), Dialogs.DialogIcon.INFORMATION);
                Dialogs.showDialog(dialogOptions, message);
            }
        });
        endAlignHorizontally();

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
        taxonomyOptions.addComboBoxOption(TAX_COL, "", cols, cols.get(0));
        taxonomyOptions.endAlignHorizontally();

        addMultipleOptions(TAX_FIELDS, taxonomyOptions, false);

        //make sure the listeners are set up after all the options are added - it seems that the listener can fire immediately in some cases
        if(updateAutomatically()) {
            connectionOptions.addChangeListener(new SimpleListener() {
                public void objectChanged() {
                    final ProgressFrame progress = new ProgressFrame("Updating Fields...", "", 1000, true, Dialogs.getCurrentModalDialog());
                    progress.setCancelable(false);
                    progress.setIndeterminateProgress();
                    new Thread() {
                        public void run() {
                            try {
                                update();
                            } catch (ConnectionException e) {
                                Dialogs.showMessageDialog(e.getMessage());
                            }
                            progress.setComplete();
                        }
                    }.start();
                }
            });
        }
        else {
            ButtonOption updateButton = addButtonOption("update", "", "Update Columns");
            updateButton.addActionListener(new ActionListener(){
                public void actionPerformed(ActionEvent ev) {
                    try {
                        update();
                        if(connectionOptions != null) {
                            connectionOptions.update();
                        }
                    } catch (ConnectionException e) {
                        Dialogs.showMessageDialog(e.getMessage());
                    }
                }
            });
        }

        autodetectTaxonomyButton.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e) {
                autodetectTaxonFields();
            }
        });


        addHiddenProjectOptions(cols);
    }

    private static final boolean SHOW_HIDDEN_PROJECT_OPTIONS = false;  // Set to true for debugging via plugin
    private void addHiddenProjectOptions(List<OptionValue> availableColumns) {
        BooleanOption storeProjects = addBooleanOption(STORE_PROJECTS, "The FIMS database contains project information", false);
        storeProjects.setVisible(SHOW_HIDDEN_PROJECT_OPTIONS);
        Options projectOptions = new Options(this.getClass());
        projectOptions.beginAlignHorizontally("", false);
        projectOptions.addComboBoxOption(PROJECT_COLUMN, "", availableColumns, availableColumns.get(0));
        projectOptions.endAlignHorizontally();
        addMultipleOptions(PROJECT_FIELDS, projectOptions, false).setVisible(SHOW_HIDDEN_PROJECT_OPTIONS);
    }

    @SuppressWarnings("unchecked")
    public void autodetectTaxonFields() {
        MultipleOptions taxOptions = getMultipleOptions(TAX_FIELDS);
        ComboBoxOption<OptionValue> taxCol = (ComboBoxOption<OptionValue>)taxOptions.getMasterOptions().getOption(TAX_COL);
        List<OptionValue> possibleOptionValues = taxCol.getPossibleOptionValues();

        taxOptions.restoreDefaults();
        int foundCount = 0;
        for(String s : BiocodeUtilities.taxonomyNames) {
            for(OptionValue ov : possibleOptionValues) {
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

    @Override
    public String verifyOptionsAreValid() {
        PasswordOptions pOptions = (PasswordOptions)getChildOptions().get(CONNECTION_OPTIONS_KEY);
        return pOptions.verifyOptionsAreValid();
    }

    public boolean linkPhotos() {
        return ((Boolean)getValue("flickrPhotos"));
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

    @Override
    public void update() throws ConnectionException {
        List<OptionValue> newCols;
        try {
            newCols = getTableColumns();
        } catch (IOException e) {
            e.printStackTrace();
            throw new ConnectionException(e.getMessage(), e);
        }
        if(newCols == null || newCols.isEmpty()) {
            newCols = NO_FIELDS;
        }

        ThreadUtilities.invokeNowOrLater(getUpdateFieldsRunnable(newCols));
    }

    private Runnable getUpdateFieldsRunnable(final List<OptionValue> newCols) {
        return new Runnable() {
            @SuppressWarnings("unchecked")
            public void run() {
                final ComboBoxOption<OptionValue> tissueId = (ComboBoxOption<OptionValue>)getOption(TISSUE_ID);
                final ComboBoxOption<OptionValue> specimenId = (ComboBoxOption<OptionValue>)getOption(SPECIMEN_ID);
                final ComboBoxOption<OptionValue> plateName = (ComboBoxOption<OptionValue>)getOption(PLATE_NAME);
                final ComboBoxOption<OptionValue> plateWell = (ComboBoxOption<OptionValue>)getOption(PLATE_WELL);
                final MultipleOptions taxOptions = getMultipleOptions(TAX_FIELDS);
                final MultipleOptions projOptions = getMultipleOptions(PROJECT_FIELDS);

                tissueId.setPossibleValues(newCols);
                specimenId.setPossibleValues(newCols);
                plateName.setPossibleValues(newCols);
                plateWell.setPossibleValues(newCols);
                setPossibleValuesForMultipleOptions(taxOptions, newCols);
                setPossibleValuesForMultipleOptions(projOptions, newCols);
            }
        };
    }

    @SuppressWarnings("unchecked")
    void setPossibleValuesForMultipleOptions(MultipleOptions multipleOptions, List<OptionValue> newCols) {
        for(Option option : multipleOptions.getMasterOptions().getOptions()) {
            if(option instanceof ComboBoxOption) {
                ((ComboBoxOption)option).setPossibleValues(newCols);
            }
        }
        for(Options options : multipleOptions.getValues()) {
            for(Option option : options.getOptions()) {
                if(option instanceof ComboBoxOption) {
                    ((ComboBoxOption)option).setPossibleValues(newCols);
                }
            }
        }
    }
}
