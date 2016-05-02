package com.biomatters.plugins.biocode.labbench.lims;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.labbench.PasswordOptions;

import java.util.Collections;

/**
 * @author Steve
 */
public class LimsConnectionOptions extends PasswordOptions{

    public static final String CONNECTION_TYPE_CHOOSER = "connectionType";

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
        for(LIMSConnection.AvailableLimsTypes type : LIMSConnection.AvailableLimsTypes.values()) {
            try {
                LIMSConnection connection = (LIMSConnection)type.getLimsClass().newInstance();
                PasswordOptions connectionOptions = connection.getConnectionOptions();
                addChildOptions(type.name(), type.getLabel(), type.getDescription(), connectionOptions);
            } catch (InstantiationException e) {
                throw new IllegalStateException(e);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        addChildOptionsPageChooser(CONNECTION_TYPE_CHOOSER, "LIMS location", Collections.<String>emptyList(), PageChooserType.COMBO_BOX, false);
    }

    @Override
    public Options getEnterPasswordOptions() {
        return ((PasswordOptions)getChildOptions().get(getValueAsString(CONNECTION_TYPE_CHOOSER))).getEnterPasswordOptions();
    }

    @Override
    public void setPasswordsFromOptions(Options enterPasswordOptions) {
        ((PasswordOptions)getChildOptions().get(getValueAsString(CONNECTION_TYPE_CHOOSER))).setPasswordsFromOptions(enterPasswordOptions);
    }

    public LIMSConnection.AvailableLimsTypes getSelectedLIMSType() {
        return LIMSConnection.AvailableLimsTypes.valueOf(getValueAsString(CONNECTION_TYPE_CHOOSER));
    }

    public PasswordOptions getSelectedLIMSOptions() {
        return ((PasswordOptions)getChildOptions().get(getValueAsString(CONNECTION_TYPE_CHOOSER)));
    }
}
