package com.biomatters.plugins.biocode.labbench.reporting;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.labbench.lims.FimsToLims;

import java.util.List;
import java.util.ArrayList;

/**
 * @author Steve
 * @version $Id$
 *          <p/>
 *          Created on 1/09/2011 9:19:40 PM
 */


public class PlateSearchReportOptions extends Options {

    public PlateSearchReportOptions(Class prefClass, FimsToLims fimsToLims) {
        super(prefClass);

        beginAlignHorizontally("", false);
        addLabel("Find plates with reactions that match ");
        Options.OptionValue[] allOrAny = new OptionValue[] {
                new OptionValue("all", "All"),
                new OptionValue("any", "Any")
        };
        addComboBoxOption("allOrAny", "", allOrAny, allOrAny[0]);
        addLabel(" of the following:");
        endAlignHorizontally();

        ReactionFieldOptions fieldOptions = new ReactionFieldOptions(prefClass, fimsToLims, true, false, false, false);
        addMultipleOptions("reactionOptions", fieldOptions, false);
    }

    public String getComparator() {
        return getValueAsString("allOrAny").equals("all") ? " AND " : " OR ";
    }

    public List<ReactionFieldOptions> getFieldOptions() {
        MultipleOptions multipleOptions = getMultipleOptions("reactionOptions");
        List<ReactionFieldOptions> fieldOptions = new ArrayList<ReactionFieldOptions>();
        for(Options o : multipleOptions.getValues()) {
            fieldOptions.add((ReactionFieldOptions)o);
        }
        return fieldOptions;
    }
}
