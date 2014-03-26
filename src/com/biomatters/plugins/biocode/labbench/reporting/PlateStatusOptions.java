package com.biomatters.plugins.biocode.labbench.reporting;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.labbench.lims.FimsToLims;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Steve
 * Date: 29/09/11
 * Time: 6:38 AM
 * To change this template use File | Settings | File Templates.
 */
public class PlateStatusOptions extends Options {
    static final String PLATE_NAME = "plateName";
    private String LOCI = "loci";

    public PlateStatusOptions(FimsToLims fimsToLims) {
        if(!fimsToLims.getFimsConnection().storesPlateAndWellInformation()) {
            addLabel("You must specify plate and well in your FIMS connection before you can use this report.  Please see the connection dialog.");
            return;
        }

        addStringOption(PLATE_NAME, "Plate name contains", "", "Leave blank to list all plates");
        List<OptionValue> lociOptionValues = fimsToLims.getLociOptionValues(false);
        ListOption listOption = new ListOption(LOCI, "Loci", lociOptionValues, lociOptionValues);
        addCustomOption(listOption);
    }


}
