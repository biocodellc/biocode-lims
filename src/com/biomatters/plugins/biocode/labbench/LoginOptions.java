package com.biomatters.plugins.biocode.labbench;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.plugins.biocode.labbench.fims.FIMSConnection;
import org.jdom.Element;

import java.util.Collections;

/**
 * @author Steve
 * @version $Id$
 */
public class LoginOptions extends Options {

    public LoginOptions() {
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
        fimsOptions.addChildOptionsPageChooser("fims", "Field Database Connection", Collections.<String>emptyList(), Options.PageChooserType.COMBO_BOX, false);

        PasswordOptions limsOptions = BiocodeService.getInstance().getActiveLIMSConnection().getConnectionOptions();

        addChildOptions("fims", null, null, fimsOptions);
        addChildOptions("lims", null, null, limsOptions);
    }

    public LoginOptions(Element element) throws XMLSerializationException {
        super(element);
    }

    public void updateOptions() {
        Options fimsOptions = getChildOptions().get("fims");
        PasswordOptions activeFimsOptions = (PasswordOptions)fimsOptions.getChildOptions().get(fimsOptions.getValueAsString("fims"));
        activeFimsOptions.update();
    }
}
