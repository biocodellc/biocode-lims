package com.biomatters.plugins.biocode.labbench.reporting;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import org.jdom.Element;

import java.util.List;
import java.util.ArrayList;

/**
 * @author Steve
 * @version $Id$
 *          <p/>
 *          Created on 28/10/2011 4:44:11 PM
 */


public class PrimerPerformanceOptions extends Options {

    private static final OptionValue NO_PRIMERS = new OptionValue("noPrimer", "No Primers");
    private String FORWARD_PRIMER = "primers";
    private String REVERSE_PRIMER = "revPrimers";
    private String FIMS_FIELDS = "fimsFields";

    public PrimerPerformanceOptions(Class cl, FimsToLims fimsToLims) {
        super(cl);
        init(fimsToLims);
    }

    public PrimerPerformanceOptions(Element element) throws XMLSerializationException {
        super(element);
    }

    private void init(FimsToLims fimsToLims) {
        List<OptionValue> forwardPrimers = getPrimerOptionValues(fimsToLims.getForwardPrimerNames());
        addComboBoxOption(FORWARD_PRIMER, "Forward Primer Name", forwardPrimers, forwardPrimers.get(0));

        List<OptionValue> reversePrimers = getPrimerOptionValues(fimsToLims.getReversePrimerNames());
        addComboBoxOption(REVERSE_PRIMER, "Reverse Primer Name", reversePrimers, reversePrimers.get(0));

        List<OptionValue> optionValues = ReportGenerator.getOptionValues(fimsToLims.getFimsFields());
        addComboBoxOption(FIMS_FIELDS, "Compare primer performance across this field in the FIMS database", optionValues, optionValues.get(0));
    }


    private List<OptionValue> getPrimerOptionValues(List<String> primers) {
        List<OptionValue> optionValues = new ArrayList<OptionValue>();
        for(String s : primers) {
            optionValues.add(new OptionValue(s,s));
        }
        if(optionValues.size() == 0) {
            optionValues.add(NO_PRIMERS);
        }
        return optionValues;
    }

    public String getForwardPrimerName() {
        return getValueAsString(FORWARD_PRIMER);
    }

    public String getReversePrimerName() {
        return getValueAsString(REVERSE_PRIMER);
    }

    public String getFimsField() {
        return getValueAsString(FIMS_FIELDS);
    }

    public String getFimsFieldName() {
        return ((OptionValue)getValue(FIMS_FIELDS)).getLabel();
    }
}
