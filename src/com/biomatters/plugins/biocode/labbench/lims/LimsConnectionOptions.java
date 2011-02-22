package com.biomatters.plugins.biocode.labbench.lims;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.options.PasswordOption;
import com.biomatters.plugins.biocode.labbench.PasswordOptions;

import java.util.Collections;

/**
 * @author Steve
 * @version $Id$
 */
public class LimsConnectionOptions extends PasswordOptions{
    private PasswordOption passwordOption;
    private StringOption usernameOption;
    private Option childOptionsChooser;
    private StringOption serverOption;

    public LimsConnectionOptions() {
        init();
    }

    public LimsConnectionOptions(Class cl) {
        super(cl);
        init();
    }

    public LimsConnectionOptions(Class cl, String preferenceNameSuffix) {
        super(cl, preferenceNameSuffix);
        init();
    }

    private void init() {

        Options remoteOptions = new Options(this.getClass());
        serverOption = remoteOptions.addStringOption("server", "Server Address:", "");
        remoteOptions.addIntegerOption("port", "Port:", 3306, 1, Integer.MAX_VALUE);
        remoteOptions.addStringOption("database", "Database Name:", "labbench");
        usernameOption = remoteOptions.addStringOption("username", "Username:", "");
        passwordOption = remoteOptions.addCustomOption(new PasswordOption("password", "Password:", true));

        addChildOptions("remote", "Remote Server", "Connect to a LIMS database on a remote MySQL server", remoteOptions);

        Options localOptions = LIMSConnection.getLocalOptions();


        addChildOptions("local", "Local Database", "Create and connect to LIMS databases on your local computer", localOptions);


        childOptionsChooser = addChildOptionsPageChooser("connectionType", "LIMS location", Collections.<String>emptyList(), PageChooserType.COMBO_BOX, false);
    }

    @Override
    public Options getEnterPasswordOptions() {
        if(((OptionValue)childOptionsChooser.getValue()).getName().equals("remote") && passwordOption.getPassword().equals("")) {
            PasswordOptions options = new PasswordOptions(this.getClass(), "mooreaFIMS");
            options.addLabel("Server: "+serverOption.getValue());
            options.addStringOption("username", "Username:", usernameOption.getValue());
            PasswordOption password = new PasswordOption("password", "Password:", false);
            options.addCustomOption(password);
            return options;
        }
        return null;
    }

    @Override
    public void setPasswordsFromOptions(Options enterPasswordOptions) {
        usernameOption.setValue(enterPasswordOptions.getValueAsString("username"));
        PasswordOption password = (PasswordOption)enterPasswordOptions.getOption("password");
        passwordOption.setPassword(password.getPassword());
    }
}
