package com.biomatters.plugins.biocode.labbench.fims;

import com.biomatters.plugins.biocode.labbench.LoginOptions;
import com.biomatters.plugins.biocode.labbench.PasswordOptions;
import com.biomatters.plugins.biocode.XmlUtilities;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.documents.DocumentField;

import java.util.*;
import java.io.IOException;

/**
 * @author Steve
 * @version $Id$
 */
public class FusionTablesFimsConnectionOptions extends TableFimsConnectionOptions{

    static final List<Options.OptionValue> NO_FIELDS = Collections.singletonList(new Options.OptionValue("None", "None"));

    protected PasswordOptions getConnectionOptions() {
        return new FusionTablesConnectionOptions();
    }


    protected List<OptionValue> _getTableColumns() throws IOException {
        Options connectionOptions = getChildOptions().get(CONNECTION_OPTIONS_KEY);
        return getTableColumns(connectionOptions.getValueAsString(TABLE_ID));
    }

    protected boolean updateAutomatically() {
        return true;
    }

    private static List<Options.OptionValue> getTableColumns(String tableId) throws IOException {
        if(tableId == null || tableId.length() == 0 || tableId.equals(FusionTablesConnectionOptions.NO_TABLE.getName())) {
            return NO_FIELDS;
        }
        List<DocumentField> decodedValues = FusionTableUtils.getTableColumns(tableId, LoginOptions.DEFAULT_TIMEOUT);
        if(decodedValues.size() == 0) {
            return NO_FIELDS;
        }
        List<OptionValue> fields = new ArrayList<OptionValue>();
        for(DocumentField f : decodedValues) {
            fields.add(new OptionValue(XmlUtilities.encodeXMLChars(f.getCode()), XmlUtilities.encodeXMLChars(f.getName()), XmlUtilities.encodeXMLChars(f.getDescription())));
        }
        return fields;
    }
}
