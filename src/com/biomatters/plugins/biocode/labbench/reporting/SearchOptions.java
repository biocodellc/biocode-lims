package com.biomatters.plugins.biocode.labbench.reporting;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import org.jdom.Element;

import javax.media.jai.operator.AndDescriptor;
import java.util.List;
import java.util.ArrayList;

/**
 * @author Steve
 * @version $Id$
 */
public class SearchOptions extends Options {

    public enum Join{
        AND,
        OR
    };

    public SearchOptions(Class cl, List<DocumentField> searchFields) {
        super(cl);
        init(searchFields);
    }

    public SearchOptions(Class cl, String preferenceNameSuffix, List<DocumentField> searchFields) {
        super(cl, preferenceNameSuffix);
        init(searchFields);
    }

    public SearchOptions(Element element) throws XMLSerializationException {
        super(element);
    }

    private void init(List<DocumentField> searchFields) {
        beginAlignHorizontally("", false);
        addLabel("Match ");
        Options.OptionValue[] allOrAny = new OptionValue[] {
                new OptionValue("all", "All"),
                new OptionValue("any", "Any")
        };
        addComboBoxOption("allOrAny", "", allOrAny, allOrAny[0]);
        addLabel(" of the following:");
        endAlignHorizontally();
        SingleFieldOptions searchOptions = new SingleFieldOptions(searchFields);
        addMultipleOptions("fims", searchOptions, false);
    }

    public Join getJoin() {
        return getValueAsString("allOrAny").equals("all") ? Join.AND : Join.OR;
    }

    public List<SingleFieldOptions> getSingleFieldOptions() {
        List<SingleFieldOptions> fieldOptions = new ArrayList<SingleFieldOptions>();
        for(Options options : getMultipleOptions("fims").getValues()) {
            fieldOptions.add((SingleFieldOptions)options);
        }
        return fieldOptions;
    }
}
