package com.biomatters.plugins.biocode.labbench.fims;

import com.biomatters.plugins.biocode.labbench.PasswordOptions;
import com.biomatters.options.PasswordOption;
import com.biomatters.geneious.publicapi.utilities.IconUtilities;
import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

import org.jdom.Element;

/**
 * @author Steve
 * @version $Id$
 */
public class FusionTablesConnectionOptions extends PasswordOptions {
    static final String USERNAME = "username";
    static final String PASSWORD = "password";

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

    private void init() {
        addLabel("<html>Enter your google username and password.<br>(for example craig.venter@gmail.com)</html>");
        addStringOption(USERNAME, "Username", "");
        final PasswordOption password = new PasswordOption(PASSWORD, "Password", true);
        addCustomOption(password);
        beginAlignHorizontally("Fusion Table ID", false);
        addStringOption(TableFimsConnectionOptions.TABLE_ID, "", "");
        ButtonOption helpButton = addButtonOption("help", "", "", IconUtilities.getIcons("help16.png").getIcon16(), JButton.LEFT);
        helpButton.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e) {
                Dialogs.showMessageDialog("To get the ID of a Fusion Table, visit the table in the Google Fusion Tables website and note the number at the end of the URL. For example, in the following URL for the Country Flags table, the table ID is 86424: <code>http://www.google.com/fusiontables/DataSource?dsrcid=86424</code>", "Getting an ID", getPanel(), Dialogs.DialogIcon.INFORMATION);
            }
        });
        endAlignHorizontally();
    }

    @Override
    public Options getEnterPasswordOptions() {
        PasswordOption passwordOption = (PasswordOption)getOption(PASSWORD);
        StringOption usernameOption = (StringOption)getOption(USERNAME);
        StringOption tableOption = (StringOption)getOption(TableFimsConnectionOptions.TABLE_ID);
        if(passwordOption.getPassword().equals("")) {
            PasswordOptions options = new PasswordOptions(this.getClass(), "mooreaFIMS");
            options.addLabel("Table ID: "+tableOption.getValue());
            options.addStringOption(USERNAME, "Username:", usernameOption.getValue());
            PasswordOption password = new PasswordOption(PASSWORD, "Password:", false);
            options.addCustomOption(password);
            return options;
        }
        return null;
    }

    @Override
    public void setPasswordsFromOptions(Options enterPasswordOptions) {
        StringOption usernameOption = (StringOption)getOption(USERNAME);
        PasswordOption passwordOption = (PasswordOption)getOption(PASSWORD);
        usernameOption.setValue(enterPasswordOptions.getValueAsString(USERNAME));
        PasswordOption password = (PasswordOption)enterPasswordOptions.getOption(PASSWORD);
        passwordOption.setPassword(password.getPassword());
    }

}
