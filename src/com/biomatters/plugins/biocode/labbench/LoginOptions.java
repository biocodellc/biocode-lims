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
            fimsOptions.addChildOptions(connection.getName(), connection.getLabel(), connection.getDescription(), connection.getConnectionOptions() != null ? connection.getConnectionOptions() : new PasswordOptions(BiocodeService.class));
        }
        Option chooser = fimsOptions.addChildOptionsPageChooser("fims", "Field Database Connection", Collections.<String>emptyList(), PageChooserType.COMBO_BOX, false);

        PasswordOptions limsOptions = LIMSConnection.createConnectionOptions();

        addChildOptions("fims", null, null, fimsOptions);
        addChildOptions("lims", null, null, limsOptions);
    }

    public LoginOptions(Element element) throws XMLSerializationException {
        super(element);
    }

    public void updateOptions() throws ConnectionException{
        Options fimsOptions = getChildOptions().get("fims");
        PasswordOptions activeFimsOptions = (PasswordOptions)fimsOptions.getChildOptions().get(fimsOptions.getValueAsString("fims"));
        activeFimsOptions.update();
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
