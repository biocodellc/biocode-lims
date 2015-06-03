package com.biomatters.plugins.biocode.labbench;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.plugins.biocode.labbench.connection.ConnectionManager;
import com.biomatters.plugins.biocode.labbench.fims.FIMSConnection;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import org.jdom.Element;

import java.util.Collections;

/**
 * @author Steve
 * @version $Id$
 */
public class LoginOptions extends Options {
    public static final String FIMS_REQUEST_TIMEOUT_OPTION_NAME = "fimsRequestTimeout";
    public static final String LIMS_REQUEST_TIMEOUT_OPTION_NAME = "limsRequestTimeout";
    public static final int DEFAULT_TIMEOUT = 300;

    public LoginOptions() throws DatabaseServiceException {
        init();
    }

    public LoginOptions(Class cl) {
        super(cl);
        init();
    }

    public LoginOptions(Class cl, String preferenceNameSuffix) {
        super(cl, preferenceNameSuffix);
        init();
    }

    private void init() {
        Options fimsOptions = new Options(ConnectionManager.class);
        for (FIMSConnection connection : BiocodeService.getFimsConnections()) {
            PasswordOptions connectionOptions = connection.getConnectionOptions();
            fimsOptions.addChildOptions(connection.getName(), connection.getLabel(), connection.getDescription(), connectionOptions != null ? connectionOptions : new PasswordOptions(BiocodeService.class));
        }
        fimsOptions.addChildOptionsPageChooser("fims", "Field Database Connection", Collections.<String>emptyList(), PageChooserType.COMBO_BOX, false);
        fimsOptions.addIntegerOption(FIMS_REQUEST_TIMEOUT_OPTION_NAME, "FIMS Timeout (seconds):", DEFAULT_TIMEOUT, 0, Integer.MAX_VALUE);

        PasswordOptions limsOptions = LIMSConnection.createConnectionOptions();
        limsOptions.addIntegerOption(LIMS_REQUEST_TIMEOUT_OPTION_NAME, "LIMS Timeout (seconds):", DEFAULT_TIMEOUT, 0, Integer.MAX_VALUE);

        addChildOptions("fims", null, null, fimsOptions);
        addChildOptions("lims", null, null, limsOptions);
    }

    public LoginOptions(Element element) throws XMLSerializationException {
        super(element);
    }

    /**
     * @see PasswordOptions#update()
     */
    public void updateOptions() throws ConnectionException{
        Options fimsOptions = getChildOptions().get("fims");
        PasswordOptions activeFimsOptions = (PasswordOptions)fimsOptions.getChildOptions().get(fimsOptions.getValueAsString("fims"));
        activeFimsOptions.update();
    }

    /**
     * @see PasswordOptions#preUpdate()
     */
    public void preUpdateOptions() throws ConnectionException{
        Options fimsOptions = getChildOptions().get("fims");
        PasswordOptions activeFimsOptions = (PasswordOptions)fimsOptions.getChildOptions().get(fimsOptions.getValueAsString("fims"));
        activeFimsOptions.preUpdate();
    }

    /**
     * @see PasswordOptions#prepare()
     */
    public void prepare() throws ConnectionException{
        Options fimsOptions = getChildOptions().get("fims");
        PasswordOptions activeFimsOptions = (PasswordOptions)fimsOptions.getChildOptions().get(fimsOptions.getValueAsString("fims"));
        activeFimsOptions.prepare();
    }
//
//    @Override
//    public String verifyOptionsAreValid() {
//        Options fimsOptions = getChildOptions().get("fims");
//        String activeConnection = fimsOptions.getValueAsString("fims");
//        if(!"excel".equals(activeConnection)) {
//            Options excelOptions = fimsOptions.getChildOptions().get("excel");
//            Options excelConnectionOptions = excelOptions.getChildOptions().get(TableFimsConnectionOptions.CONNECTION_OPTIONS_KEY);
//            excelConnectionOptions.getOption(ExcelFimsConnectionOptions.FILE_LOCATION).setValueFromString("");
//        }
//        return super.verifyOptionsAreValid();
//    }
}
