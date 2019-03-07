package com.biomatters.plugins.biocode.labbench.fims.geome;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.plugins.biocode.BiocodePlugin;
import com.biomatters.plugins.biocode.labbench.LoginOptions;
import com.biomatters.plugins.biocode.labbench.PasswordOptions;
import com.biomatters.plugins.biocode.utilities.PasswordOption;
import com.biomatters.plugins.biocode.utilities.SharedCookieHandler;

import javax.ws.rs.ProcessingException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * @author Matthew Cheung
 *       <p />
 *       Created on 1/02/14 10:50 AM
 */public class geomeFIMSConnectionOptions extends PasswordOptions {

    private StringOption hostOption;
    private StringOption usernameOption;
    private PasswordOption passwordOption;
    private BooleanOption includePublicProjectsOption;

    public geomeFIMSConnectionOptions() {
        super(BiocodePlugin.class);
        hostOption = addStringOption("host", "Host:", geomeFIMSConnection.GEOME_URL);
        usernameOption = addStringOption("username", "Username:", "");
        passwordOption = addCustomOption(new PasswordOption("password", "Password:", true));
        includePublicProjectsOption = addBooleanOption("includePublic", "Include data from public projects", false);
    }

    public void login(String host, String username, String password) throws MalformedURLException, ProcessingException, DatabaseServiceException {
        try {
            URL url = new URL(host);
            SharedCookieHandler.registerHost(url.getHost());
            geomeFIMSClient client = new geomeFIMSClient(host, LoginOptions.DEFAULT_TIMEOUT);
            client.login(username, password);
        } catch (IOException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    public String getHost() {
        return hostOption.getValue();
    }

    public String getUserName() {
        return usernameOption.getValue();
    }

    public void setUserName(String name) {
        usernameOption.setValue(name);
    }

    public String getPassword() {
        return passwordOption.getPassword();
    }

    public void setPassword(String password) {
        passwordOption.setPassword(password);
    }

    public boolean includePublicProjects() {
        return includePublicProjectsOption.getValue();
    }

    public void setIncludePublicProjectsOption(Boolean publicOption) { includePublicProjectsOption.setValue(publicOption); }

    public void setHostOption(String host) { this.hostOption.setValue(host); }
}