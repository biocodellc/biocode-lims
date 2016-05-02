package com.biomatters.plugins.biocode.labbench.reporting;

import com.biomatters.geneious.publicapi.utilities.StringUtilities;

import java.util.List;
import java.util.ArrayList;

/**
 * @author Steve
 */
public class SearchOptionsToLabel implements ButtonMultipleOptions.OptionsToLabel<SearchOptions>{

    public SearchOptionsToLabel() {
    }

    public String getLabel(SearchOptions options) {
        String join = options.getJoin() == SearchOptions.Join.AND ? ", and " : ", or ";
        List<String> searchTerms = new ArrayList<String>();
        for(SingleFieldOptions option : options.getSingleFieldOptions()) {
            searchTerms.add(option.getFriendlyDescription());
        }
        return StringUtilities.join(join, searchTerms);
    }
}
