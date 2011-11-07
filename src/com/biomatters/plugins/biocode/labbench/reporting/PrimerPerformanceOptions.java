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
    private List<PrimerSet> reversePrimers;
    private List<PrimerSet> forwardPrimers;

    public PrimerPerformanceOptions(Class cl, FimsToLims fimsToLims) {
        super(cl);
        init(fimsToLims);
    }

    public PrimerPerformanceOptions(Element element) throws XMLSerializationException {
        super(element);
    }

    private void init(FimsToLims fimsToLims) {
        this.forwardPrimers = fimsToLims.getForwardPrimers();
        List<OptionValue> forwardPrimers = getPrimerOptionValues(this.forwardPrimers);
        addComboBoxOption(FORWARD_PRIMER, "Forward Primer Name", forwardPrimers, forwardPrimers.get(0));

        this.reversePrimers = fimsToLims.getReversePrimers();
        List<OptionValue> reversePrimers = getPrimerOptionValues(this.reversePrimers);
        addComboBoxOption(REVERSE_PRIMER, "Reverse Primer Name", reversePrimers, reversePrimers.get(0));

        List<OptionValue> optionValues = ReportGenerator.getOptionValues(fimsToLims.getFimsFields());
        addComboBoxOption(FIMS_FIELDS, "Compare primer performance across this field in the FIMS database", optionValues, optionValues.get(0));
    }


    private List<OptionValue> getPrimerOptionValues(List<PrimerSet> primers) {
        List<OptionValue> optionValues = new ArrayList<OptionValue>();
        for (int i = 0; i < primers.size(); i++) {
            PrimerSet primer = primers.get(i);
            optionValues.add(new OptionValue(primer.getPrimers().get(0).getName(), primer.toString()));
        }
        if(optionValues.size() == 0) {
            optionValues.add(NO_PRIMERS);
        }
        return optionValues;
    }

    private PrimerSet getPrimerSet(List<PrimerSet> primers, String id) {
        for(PrimerSet primerSet : primers) {
            for(PrimerSet.Primer primer : primerSet.getPrimers()) {
                if(primer.getName().equals(id)) {
                    return primerSet;
                }
            }
        }
        assert false;
        return null;
    }

    public PrimerSet getForwardPrimer() {
        return getPrimerSet(forwardPrimers, getValueAsString(FORWARD_PRIMER));
    }

    public PrimerSet getReversePrimer() {
        return getPrimerSet(reversePrimers, getValueAsString(REVERSE_PRIMER));
    }

    public String getFimsField() {
        return getValueAsString(FIMS_FIELDS);
    }

    public String getFimsFieldName() {
        return ((OptionValue)getValue(FIMS_FIELDS)).getLabel();
    }
}
