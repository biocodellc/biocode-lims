package com.biomatters.plugins.biocode.labbench.reporting;

import com.biomatters.geneious.publicapi.plugin.Options;

/**
 * Created by IntelliJ IDEA.
 * User: Steve
 * Date: 29/09/11
 * Time: 6:38 AM
 * To change this template use File | Settings | File Templates.
 */
public class PlateStatusOptions extends Options {
    static final String PLATE_NAME = "plateName";

    public PlateStatusOptions(FimsToLims fimsToLims) {
        if(!fimsToLims.limsHasFimsValues()) {
            addLabel("You must copy your FIMS values into the LIMS before using this report.");
            return;
        }
        if(!fimsToLims.getFimsConnection().canGetTissueIdsFromFimsTissuePlate()) {
            addLabel("You must specify plate and well in your FIMS connection before you can use this report.  Please see the connection dialog.");
            return;
        }

        addStringOption(PLATE_NAME, "Plate name contains", "", "Leave blank to list all plates");

    }


}
