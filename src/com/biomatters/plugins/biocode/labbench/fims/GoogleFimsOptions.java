package com.biomatters.plugins.biocode.labbench.fims;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.options.PasswordOption;
import com.biomatters.plugins.biocode.labbench.AnimatedIcon;
import com.google.gdata.data.spreadsheet.*;
import com.google.gdata.util.ServiceException;
import com.google.gdata.client.spreadsheet.ListQuery;
import com.google.gdata.client.spreadsheet.SpreadsheetService;
import org.jdom.Element;
import org.virion.jam.util.SimpleListener;

import javax.swing.*;
import java.util.List;
import java.util.ArrayList;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.io.IOException;

/**
 * User: Steve
 * Date: 1/03/2010
 * Time: 2:11:28 PM
 */
public class GoogleFimsOptions extends Options {

    private ComboBoxOption spreadsheetOption;
    private List<SpreadsheetEntry> availableSpreadsheets = new ArrayList<SpreadsheetEntry>();
    private List<OptionValue> columnHeaders = new ArrayList<OptionValue>();
    Options.ComboBoxOption<Options.OptionValue> tissueId;
    Options.ComboBoxOption<Options.OptionValue> specimenId;
    Options.MultipleOptions taxOptions;

    public GoogleFimsOptions(Class cl) {
        super(cl);
        init();
    }

    public GoogleFimsOptions(Class cl, String preferenceNameSuffix) {
        super(cl, preferenceNameSuffix);
        init();
    }

    protected GoogleFimsOptions(Element element) throws XMLSerializationException {
        super(element);
    }

    public void init() {
        final StringOption emailOption = addStringOption("email", "Google email", "");
        final PasswordOption passwordOption = new PasswordOption("password", "Password");
        addCustomOption(passwordOption);
        beginAlignHorizontally(null, false);
        final List<OptionValue> valueList = getSpreadsheetValues();
        spreadsheetOption = addComboBoxOption("spreadsheet", "Spreadsheet", valueList, valueList.get(0));
        final ButtonOption buttonOption = addButtonOption("spreadsheetUpdater", "", "Update Spreadsheet List", null, ButtonOption.CENTER);
        final AnimatedIcon icon = AnimatedIcon.getActivityIcon();
        JLabel iconLabel = new JLabel(icon);
        final Option iconOption = addCustomComponent(iconLabel);
        iconOption.setVisible(false);
        buttonOption.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                iconOption.setVisible(true);
                icon.startAnimation();
                buttonOption.setEnabled(false);
                Runnable runnable = new Runnable() {
                    public void run() {
                        try {
                            availableSpreadsheets = GoogleFimsConnection.getSpreadsheetList(emailOption.getValue(), passwordOption.getPassword());
                            updateAvailableSpreadsheets();
                        }
                        catch(Exception ex) {
                            ex.printStackTrace();
                            availableSpreadsheets = null;
                            spreadsheetOption.setPossibleValues(getSpreadsheetValues());
                        }
                        Runnable runnable = new Runnable() {
                            public void run() {
                                iconOption.setVisible(false);
                                buttonOption.setEnabled(true);
                            }
                        };
                        SwingUtilities.invokeLater(runnable);
                    }
                };
                new Thread(runnable).start();
            }
        });

        spreadsheetOption.addChangeListener(new SimpleListener() {
            public void objectChanged() {
                iconOption.setVisible(true);
                icon.startAnimation();
                buttonOption.setEnabled(false);
                Runnable runnable = new Runnable() {
                    public void run() {
                        try {
                            updateAvailableColumns();
                        }
                        catch(Exception ex) {
                            ex.printStackTrace();
                        }
                        Runnable runnable = new Runnable() {
                            public void run() {
                                iconOption.setVisible(false);
                                buttonOption.setEnabled(true);
                            }
                        };
                        SwingUtilities.invokeLater(runnable);
                    }
                };
                new Thread(runnable).start();
            }
        });
        endAlignHorizontally();

        if(columnHeaders.size() == 0) {
            columnHeaders.add(new OptionValue("null", "No Columns"));
        }


        tissueId = addComboBoxOption("tissueId", "Tissue ID field", columnHeaders, columnHeaders.get(0));

        specimenId = addComboBoxOption("specimenId", "Specimen ID field", columnHeaders, columnHeaders.get(0));

        addLabel(" ");
        addLabel("Specify your taxonomy fields, in order of highest to lowest");
        Options taxonomyOptions = new Options(this.getClass());
        taxonomyOptions.beginAlignHorizontally("", false);
        final Options.ComboBoxOption<Options.OptionValue> taxCol = taxonomyOptions.addComboBoxOption("taxCol", "", columnHeaders, columnHeaders.get(0));
        taxonomyOptions.endAlignHorizontally();

        taxOptions = addMultipleOptions("taxFields", taxonomyOptions, false);
    }

    private List<OptionValue> getSpreadsheetValues() {
        List<OptionValue> value = new ArrayList<OptionValue>();
        if(availableSpreadsheets == null || availableSpreadsheets.size() == 0) {
            value.add(new OptionValue("null", "No spreadsheets"));
        }
        else {
            for(SpreadsheetEntry entry : availableSpreadsheets) {
                value.add(new OptionValue(entry.getTitle().getPlainText(), entry.getTitle().getPlainText()));
            }
        }
        return value;
    }

    public void updateAvailableSpreadsheets() throws IOException, ServiceException {
        spreadsheetOption.setPossibleValues(getSpreadsheetValues());
        updateAvailableColumns();

    }

    private void updateAvailableColumns() throws IOException, ServiceException {
        SpreadsheetService service = new SpreadsheetService("exampleCo-exampleApp-1");
        String email = getValueAsString("email");
        String password = ((PasswordOption)getOption("password")).getPassword();
        service.setUserCredentials(email, password);

        SpreadsheetEntry selectedEntry = null;
        for(SpreadsheetEntry entry : availableSpreadsheets) {
            if(entry.getTitle().getPlainText().equals(((OptionValue)spreadsheetOption.getValue()).getName())) {
                selectedEntry = entry;
            }
        }

        if(selectedEntry == null) {
            return; //todo: disable stuff...
        }

        WorksheetEntry worksheet = selectedEntry.getWorksheets().get(0);
        int numberOfCols = worksheet.getColCount();
        ListQuery query = new ListQuery(worksheet.getListFeedUrl());
        query.setMaxResults(1);
        ListFeed feed = service.query(query, ListFeed.class);
        final List<ListEntry> entries = feed.getEntries();
        if(entries.size() > 0) {
            final ListEntry headerRow = entries.get(0);
            final CustomElementCollection customElements = headerRow.getCustomElements();
            columnHeaders = new ArrayList<OptionValue>();
            for(String s : customElements.getTags()) {
                columnHeaders.add(new OptionValue(s,s));
            }
            if(columnHeaders.size() == 0) {
                columnHeaders.add(new OptionValue("null", "No Columns"));
            }

            tissueId.setPossibleValues(columnHeaders);
            specimenId.setPossibleValues(columnHeaders);
            for(Options options : taxOptions.getValues()) {
                for(Options.Option option : options.getOptions()) {
                    if(option instanceof Options.ComboBoxOption) {
                        ((Options.ComboBoxOption)option).setPossibleValues(columnHeaders);
                    }
                }
            }
        }
    }
}
