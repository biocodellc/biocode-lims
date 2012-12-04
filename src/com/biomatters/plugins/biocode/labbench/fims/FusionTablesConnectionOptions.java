package com.biomatters.plugins.biocode.labbench.fims;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.utilities.IconUtilities;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.labbench.AnimatedIcon;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.PasswordOptions;
import com.google.api.services.fusiontables.model.Table;
import org.jdom.Element;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Steve
 * @version $Id$
 */
public class FusionTablesConnectionOptions extends PasswordOptions {
    static final OptionValue NO_TABLE = new OptionValue("%NONE%", "<html><i>None</i></html>");

    private JButton dialogOkButton;

    public FusionTablesConnectionOptions() {
        init();
    }

    public FusionTablesConnectionOptions(Class cl) {
        super(cl);
        init();
    }

    public FusionTablesConnectionOptions(Class cl, String preferenceNameSuffix) {
        super(cl, preferenceNameSuffix);
        init();
    }

    public FusionTablesConnectionOptions(Element element) throws XMLSerializationException {
        super(element);
    }

    @Override
    protected JPanel createPanel() {
        JPanel wrapperPanel = new JPanel(new BorderLayout()){
            boolean firstPaint = true;
            @Override
            protected void paintComponent(Graphics g) {
                if(firstPaint) {
                    dialogOkButton = BiocodeUtilities.getDialogOkButton(this);
                    firstPaint=false;
                }
                super.paintComponent(g);
            }
        };
        wrapperPanel.add(super.createPanel());
        return wrapperPanel;
    }

    private void init() {
        addLabel("<html>Google requires you to authenticate via a browser.  If no browser has been opened, click the <b>Authorize</b> button below.</html>", false, true);

        beginAlignHorizontally("Current User:", false);
        LabelOption currentUserLabel = new LabelOption("currentUserLabel", "");
        addCustomOption(currentUserLabel);
        endAlignHorizontally();

        beginAlignHorizontally("", false);
        final ButtonOption authorizeButton = addButtonOption("authorize", "", "Authorize");
        final ButtonOption changeAccountButton = addButtonOption("changeAccount", "", "Change Account");
        final LabelOption waitLabel = new LabelOption("waitLabel", "");
        waitLabel.setIcon(AnimatedIcon.getActivityIcon());
        waitLabel.setVisible(false);
        addCustomOption(waitLabel);
        changeAccountButton.setEnabled(false);
        endAlignHorizontally();

        authorizeButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                authorizeButton.setEnabled(false);
                waitLabel.setVisible(true);
                changeAccountButton.setEnabled(false);
                if(dialogOkButton != null) {
                    dialogOkButton.setEnabled(false);
                }
                Runnable r = new Runnable() {
                    public void run() {

                        //authencate...
                        try {
                            FusionTableUtils.authorize();
                        } catch (IOException e1) {
                            Dialogs.showMessageDialog("There was an error authenticating with Google: "+e1.getMessage(), "Error Authenticating");
                        } finally {

                            //read data from the server...
                            try {
                                update();
                            } catch (ConnectionException ignore) {}

                            //update the UI
                            Runnable runnable = new Runnable() {
                                public void run() {
                                    authorizeButton.setEnabled(true);
                                    waitLabel.setVisible(false);
                                    if(dialogOkButton != null) {
                                        dialogOkButton.setEnabled(true);
                                    }
                                }
                            };
                            ThreadUtilities.invokeNowOrLater(runnable);
                        }
                    }
                };
                new Thread(r).start();
            }
        });

        changeAccountButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                getOption("currentUserLabel").setValue("");
                FusionTableUtils.clearCachedAccessTokens();
                authorizeButton.fireActionListeners();
            }
        });

        List<OptionValue> tables = null;
        tables = getTables();

        if(tables.size() == 0) {
            tables = Collections.singletonList(
                    FusionTablesConnectionOptions.NO_TABLE
            );
        }
        addDivider(" ");
        beginAlignHorizontally("Your Tables:", false);
        final ComboBoxOption<OptionValue> tablesOption = addComboBoxOption(TableFimsConnectionOptions.TABLE_ID, "", tables, tables.get(0));
        ButtonOption helpButton = addButtonOption("help", "", "", IconUtilities.getIcons("help16.png").getIcon16(), JButton.LEFT);
        helpButton.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e) {
                Dialogs.showMessageDialog("As Google no longer supports numeric table id's, we can only access tables stored in your google drive.  To move a table someone shared with you into your drive, just drag it into the red My Drive label on the right hand side of your google drive screen." +
                        "\n" +
                        "\n" +
                        "For more information about creating and using Fusion Tables, please see: " +
                        "\n" +
                        "<a href=\"http://software.mooreabiocode.org/index.php?title=Google_Fusion_Tables_FIMS\">The Moorea Biocode Plugin Documentation</a>" +
                        "", "Getting an ID", getPanel(), Dialogs.DialogIcon.INFORMATION);
            }
        });
        endAlignHorizontally();

        new Thread() {
            @Override
            public void run() {
                try {
                    update();
                } catch (ConnectionException ignore) {}
            }
        }.start();

    }

    @Override
    public Options getEnterPasswordOptions() {
        //the access token is stored in an on-disk cache - nothing to do here
        return null;
    }

    @Override
    public void setPasswordsFromOptions(Options enterPasswordOptions) {
        //the access token is stored in an on-disk cache - nothing to do here
    }

    @Override
    public void update() throws ConnectionException {
        super.update();
        final AtomicReference<List<OptionValue>> tableValues = new AtomicReference<List<OptionValue>>();
        final AtomicReference<String> accountName = new AtomicReference<String>();
        try {
            tableValues.set(getTables());
            accountName.set(FusionTableUtils.getAccountName());
        } catch (IOException e) {
            e.printStackTrace();
        }

        Runnable runnable = new Runnable() {
            public void run() {
                ComboBoxOption tables = (ComboBoxOption)getOption(TableFimsConnectionOptions.TABLE_ID);
                tables.setPossibleValues(tableValues.get());
                LabelOption label = (LabelOption)getOption("currentUserLabel");
                Option changeAccountButton = getOption("changeAccount");
                if(accountName.get() != null) {
                    label.setValue("<html><b>"+accountName.get()+"</b></html>");
                    changeAccountButton.setEnabled(true);
                }
                else {
                    label.setValue("<html><i>Not logged in...</i></html>");
                    changeAccountButton.setEnabled(false);
                }
            }
        };
        ThreadUtilities.invokeNowOrLater(runnable);

    }

    private List<OptionValue> getTables() {
        try {
            List<Table> tableJson = FusionTableUtils.listTables();

            List<OptionValue> tables = new ArrayList<OptionValue>();

            for(Table table : tableJson) {
                tables.add(new OptionValue(table.getTableId(), table.getName()));
            }

            if(tables.size() == 0) {
                return Collections.singletonList(FusionTablesConnectionOptions.NO_TABLE);
            }
            return tables;
        } catch (IOException e) {
            return Collections.singletonList(FusionTablesConnectionOptions.NO_TABLE);
        }
    }

}
