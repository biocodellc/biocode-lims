package com.biomatters.plugins.biocode.labbench.lims;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.utilities.PasswordOption;
import com.biomatters.plugins.biocode.labbench.PasswordOptions;


/**
 * @author Steve
 * @version $Id$
 *          <p/>
 *          Created on 17/04/13 2:50 PM
 */


public class MySqlLIMSConnectionOptions extends PasswordOptions {

    private PasswordOption passwordOption;
    private StringOption usernameOption;
    private StringOption serverOption;

    @SuppressWarnings("UnusedDeclaration")
    public MySqlLIMSConnectionOptions() {
        super(MysqlLIMSConnection.class);
        init();
    }

    public MySqlLIMSConnectionOptions(Class cl) {
        super(cl);
        init();
    }

    @SuppressWarnings("UnusedDeclaration")
    public MySqlLIMSConnectionOptions(Class cl, String preferenceNameSuffix) {
        super(cl, preferenceNameSuffix);
        init();
    }

    private void init() {
        serverOption = addStringOption("server", "Server Address:", "");
        addIntegerOption("port", "Port:", 3306, 1, Integer.MAX_VALUE);
        addStringOption("database", "Database Name:", "labbench");
        usernameOption = addStringOption("username", "Username:", "");
        passwordOption = addCustomOption(new PasswordOption("password", "Password:", true));
    }

    @Override
    public Options getEnterPasswordOptions() {
        if (passwordOption.getPassword().equals("")) {
            PasswordOptions options = new PasswordOptions(this.getClass(), "mooreaFIMS");
            options.addLabel("Server: " + serverOption.getValue());
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
        PasswordOption password = (PasswordOption) enterPasswordOptions.getOption("password");
        passwordOption.setPassword(password.getPassword());
    }


}
