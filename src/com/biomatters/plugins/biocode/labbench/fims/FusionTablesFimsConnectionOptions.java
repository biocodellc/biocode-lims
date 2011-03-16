package com.biomatters.plugins.biocode.labbench.fims;

import com.biomatters.plugins.biocode.labbench.PasswordOptions;
import com.biomatters.plugins.biocode.XmlUtilities;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.options.PasswordOption;
import com.google.gdata.client.ClientLoginAccountType;
import com.google.gdata.client.GoogleService;
import com.google.gdata.client.Service;
import com.google.gdata.util.ServiceException;
import com.google.gdata.util.ContentType;
import com.google.gdata.util.AuthenticationException;

import javax.swing.*;
import java.util.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLEncoder;

/**
 * @author Steve
 * @version $Id$
 */
public class FusionTablesFimsConnectionOptions extends TableFimsConnectionOptions{

    private GoogleService service = new GoogleService("fusiontables", "fusiontables.Biocode");
    static final List<Options.OptionValue> NO_FIELDS = Arrays.asList(new Options.OptionValue("None", "None"));

    protected PasswordOptions getConnectionOptions() {
        PasswordOptions connectionOptions = new PasswordOptions(this.getClass(), "fusionTables");
        connectionOptions.addLabel("<html>Enter your google username and password.<br>(for example craig.venter@gmail.com)</html>");
        connectionOptions.addStringOption(USERNAME, "Username", "");
        final PasswordOption password = new PasswordOption(PASSWORD, "Password", true);
        connectionOptions.addCustomOption(password);
        connectionOptions.addStringOption(TABLE_ID, "Fusion Table ID", "");
        return connectionOptions;
    }


    protected List<OptionValue> getTableColumns() throws IOException {
        Options connectionOptions = getChildOptions().get(CONNECTION_OPTIONS_KEY);
        final StringOption username = (StringOption) connectionOptions.getOption(USERNAME);
        final PasswordOption password = (PasswordOption) connectionOptions.getOption(PASSWORD);
        final StringOption tableId = (StringOption) connectionOptions.getOption(TABLE_ID);
        try {
            service.setUserCredentials(username.getValue(), password.getPassword(), ClientLoginAccountType.GOOGLE);
        } catch (AuthenticationException e) {
            IOException ioException = new IOException(e.toString());
            ioException.setStackTrace(e.getStackTrace());
            throw ioException;
        }
        return getTableColumns(tableId.getValue(), service);
    }

    protected boolean updateAutomatically() {
        return false;
    }

    private static List<Options.OptionValue> getTableColumns(String tableId, GoogleService service) throws IOException {
        if(tableId == null) {
            return NO_FIELDS;
        }
        List<DocumentField> decodedValues = getTableColumnFields(tableId, service);
        if(decodedValues.size() == 0) {
            return NO_FIELDS;
        }
        List<OptionValue> fields = new ArrayList<OptionValue>();
        for(DocumentField f : decodedValues) {
            fields.add(new OptionValue(f.getCode(), f.getName(), f.getDescription()));
        }
        return fields;
    }

    static List<DocumentField> getTableColumnFields(String tableId, GoogleService service) throws IOException {
        String query = "DESCRIBE "+tableId+"";
        URL url = new URL(FusionTablesFimsConnection.SERVICE_URL + "?sql=" + URLEncoder.encode(query, "UTF-8"));
        Service.GDataRequest request = null;
        try {
            request = service.getRequestFactory().getRequest(Service.GDataRequest.RequestType.QUERY, url, ContentType.TEXT_PLAIN);
            request.execute();
        } catch (ServiceException e) {
            IOException ioException = new IOException(e.toString());
            ioException.setStackTrace(e.getStackTrace());
            throw ioException;
        }


        BufferedReader reader = new BufferedReader(new InputStreamReader(request.getResponseStream()));
        String line;
        boolean firstTime = true;
        List<DocumentField> fields = new ArrayList<DocumentField>();

        while((line = reader.readLine()) != null) {
            String[] tokens = FusionTablesFimsConnection.tokenizeLine(line);
            if(firstTime) {//they send the col headers for some reason...
                firstTime = false;
                continue;
            }
            fields.add(new DocumentField(tokens[1], "", XmlUtilities.encodeXMLChars(tokens[1]), getClass(tokens[2]), false, false));
        }

        return fields;
    }

    private static Class getClass(String valueType) {
        if(valueType.equals("string")) {
            return String.class;
        }
        if(valueType.equals("number")) {
            return Double.class;
        }
        if(valueType.equals("datetime")) {
            return Date.class;
        }
        if(valueType.equals("location")) {
            return String.class;
        }
        throw new RuntimeException("Unrecognised value type: "+valueType);
    }
}
