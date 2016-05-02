package com.biomatters.plugins.biocode.labbench.reporting;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.plugins.biocode.labbench.lims.FimsToLims;
import org.jdom.Element;

import java.sql.SQLException;
import java.util.List;
import java.util.ArrayList;

/**
 * @author Steve
 */
public class FimsMultiOptions extends Options {
    private static final String ALL_OR_ANY = "allOrAny";
    private static final String FIMS = "fims";

    public FimsMultiOptions(Class cl, FimsToLims fimsToLims) {
        super(cl);
        init(fimsToLims);
    }

    public FimsMultiOptions(Class cl, String preferenceNameSuffix, FimsToLims fimsToLims) throws SQLException {
        super(cl, preferenceNameSuffix);
        init(fimsToLims);
    }

    public FimsMultiOptions(Element element) throws XMLSerializationException {
        super(element);
    }

    private void init(FimsToLims fimsToLims) {
        SingleFieldOptions fimsOptions = new SingleFieldOptions(fimsToLims.getFimsFields());
        beginAlignHorizontally("", false);
        addLabel("Match ");
        Options.OptionValue[] allOrAny = new OptionValue[] {
                new OptionValue("all", "All"),
                new OptionValue("any", "Any")
        };
        addComboBoxOption(ALL_OR_ANY, "", allOrAny, allOrAny[0]);
        addLabel(" of the following:");
        endAlignHorizontally();
        addMultipleOptions(FIMS, fimsOptions, false);
    }

    public boolean isOr() {
        return getValueAsString(ALL_OR_ANY).equals("any") ? true : false;
    }

    public List<SingleFieldOptions> getFimsOptions() {
        List<SingleFieldOptions> fieldOptions = new ArrayList<SingleFieldOptions>();
        for(Options options : getMultipleOptions(FIMS).getValues()) {
            fieldOptions.add((SingleFieldOptions)options);
        }
        return fieldOptions;
    }
}
