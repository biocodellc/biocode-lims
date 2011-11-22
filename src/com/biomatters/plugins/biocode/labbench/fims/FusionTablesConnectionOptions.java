package com.biomatters.plugins.biocode.labbench.fims;

import com.biomatters.plugins.biocode.labbench.PasswordOptions;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.options.PasswordOption;
import com.biomatters.geneious.publicapi.utilities.IconUtilities;
import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.google.gdata.client.Service;
import com.google.gdata.client.GoogleService;
import com.google.gdata.util.ContentType;
import com.google.gdata.util.ServiceException;
import com.google.gdata.util.AuthenticationException;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.net.URL;
import java.net.URLEncoder;
import java.net.MalformedURLException;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import org.jdom.Element;
import org.virion.jam.util.SimpleListener;

/**
 * @author Steve
 * @version $Id$
 */
public class FusionTablesConnectionOptions extends PasswordOptions {
    static final String USERNAME = "username";
    static final String PASSWORD = "password";
    private final OptionValue NO_TABLE = new OptionValue("%NONE%", "<html><i>None</i></html>");

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
        List<OptionValue> tables = null;
        try {
            tables = getTables(null);
        } catch (IOException e) {
            throw new RuntimeException("NOT POSSIBLE");
        }
        beginAlignHorizontally("Fusion Table ID", false);
        final StringOption fusionTableIdOption = addStringOption(TableFimsConnectionOptions.TABLE_ID, "", "");
        ButtonOption helpButton = addButtonOption("help", "", "", IconUtilities.getIcons("help16.png").getIcon16(), JButton.LEFT);
        helpButton.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e) {
                Dialogs.showMessageDialog("To get the ID of a Fusion Table, visit the table in the Google Fusion Tables website and note the number at the end of the URL. For example, in the following URL for the Country Flags table, the table ID is 86424: <code>http://www.google.com/fusiontables/DataSource?dsrcid=86424</code>", "Getting an ID", getPanel(), Dialogs.DialogIcon.INFORMATION);
            }
        });
        endAlignHorizontally();
        final ComboBoxOption<OptionValue> tablesOption = addComboBoxOption("tables", "Your Tables:", tables, tables.get(0));     
        final AtomicBoolean changing = new AtomicBoolean(false);
        tablesOption.addChangeListener(new SimpleListener(){
            public void objectChanged() {
                if(!changing.getAndSet(true)) {

                    if(!tablesOption.getValue().equals(NO_TABLE))
                        fusionTableIdOption.setValue(tablesOption.getValue().getName());

                    changing.set(false);
                }
            }
        });
        fusionTableIdOption.addChangeListener(new SimpleListener(){
            public void objectChanged() {
                if(!changing.getAndSet(true)) {

                    tablesOption.setValue(NO_TABLE);

                    changing.set(false);
                }
            }
        });

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

    @Override
    public void update() throws ConnectionException {
        super.update();
        GoogleService service = new GoogleService("fusiontables", "fusiontables.ApiExample");
        try {
            service.setUserCredentials(getValueAsString(USERNAME), ((PasswordOption)getOption(PASSWORD)).getPassword());
            ComboBoxOption tables = (ComboBoxOption)getOption("tables");
            tables.setPossibleValues(getTables(service));
        } catch (AuthenticationException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private List<OptionValue> getTables(GoogleService service) throws IOException {
        List<OptionValue> tables = new ArrayList<OptionValue>();

        tables.add(NO_TABLE);

        if(service != null) {
            URL url = new URL(FusionTablesFimsConnection.SERVICE_URL + "?sql=SHOW%20TABLES");
            Service.GDataRequest request = null;
            try {
                request = service.getRequestFactory().getRequest(Service.GDataRequest.RequestType.QUERY, url, ContentType.TEXT_PLAIN);
                request.execute();
            } catch (ServiceException e) {
                IOException ioException = new IOException(e.toString());
                ioException.setStackTrace(e.getStackTrace());
                throw ioException;
            }
            catch(NullPointerException e) { //why do they throw these?
                if(e.getMessage().contains("No authentication header information")) {
                    throw new IOException("Acess denied connecting to Fusion Tables");
                }
            }



            BufferedReader reader = new BufferedReader(new InputStreamReader(request.getResponseStream()));
            String line;
            boolean first = true;
            while((line = reader.readLine()) != null) {
                if(first) {
                    first = false;
                    continue;
                }
                String[] parts = FusionTablesFimsConnection.tokenizeLine(line);
                tables.add(new OptionValue(parts[0], parts[1]));
            }
        }

        return tables;
    }

}
