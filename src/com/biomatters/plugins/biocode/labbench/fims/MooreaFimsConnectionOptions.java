package com.biomatters.plugins.biocode.labbench.fims;

import com.biomatters.plugins.biocode.labbench.PasswordOptions;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.options.PasswordOption;
import org.jdom.Element;

/**
 * @author Steve
 * @version $Id$
 */
public class MooreaFimsConnectionOptions extends PasswordOptions{
    private PasswordOption passwordOption;
    private StringOption usernameOption;
    private StringOption serverOption;

    public MooreaFimsConnectionOptions() {
        init();
    }

    public MooreaFimsConnectionOptions(Class cl) {
        super(cl);
        init();
    }

    private void init() {
        serverOption = addStringOption("serverUrl", "Server:", "darwin.berkeley.edu");
        addIntegerOption("serverPort", "Port:", 3306, 0, Integer.MAX_VALUE);
        usernameOption = addStringOption("username", "Username:", "");
        passwordOption = new PasswordOption("password", "Password:", true);
        addCustomOption(passwordOption);
    }

    @Override
    public Options getEnterPasswordOptions() {
        if(passwordOption.getPassword().equals("")) {
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

    public MooreaFimsConnectionOptions(Class cl, String preferenceNameSuffix) {
        super(cl, preferenceNameSuffix);
        init();
    }

    public MooreaFimsConnectionOptions(Element element) throws XMLSerializationException {
        super(element);
    }
}
