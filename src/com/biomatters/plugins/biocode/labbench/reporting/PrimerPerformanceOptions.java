package com.biomatters.plugins.biocode.labbench.reporting;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.plugins.biocode.labbench.lims.FimsToLims;
import org.jdom.Element;
import org.virion.jam.util.SimpleListener;

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
    private static final String REACTION_TYPE = "reactionType";
    private static final String FORWARD_PRIMER = "primers";
    private static final String REVERSE_PRIMER = "revPrimers";
    private static final String FIMS_FIELDS = "fimsFields";
    private List<PrimerSet> reversePrimers;
    private List<PrimerSet> forwardPrimers;
    private final String PCR = "pcr";
    private final String SEQUENCING = "cyclesequencing";

    public PrimerPerformanceOptions(Class cl, FimsToLims fimsToLims) {
        super(cl);
        init(fimsToLims);
    }

    public PrimerPerformanceOptions(Element element) throws XMLSerializationException {
        super(element);
    }

    private void init(final FimsToLims fimsToLims) {
        final OptionValue[] reactionTypes = new OptionValue[] {new OptionValue(PCR, "PCR"),
                new OptionValue(SEQUENCING, "Sequencing")};

        final RadioOption<OptionValue> reactionTypeOption = addRadioOption(REACTION_TYPE, "Reaction Type", reactionTypes, reactionTypes[0], Alignment.HORIZONTAL_ALIGN);

        this.forwardPrimers = fimsToLims.getForwardPcrPrimers();
        List<OptionValue> forwardPrimers = getPrimerOptionValues(this.forwardPrimers);
        final ComboBoxOption<OptionValue> forwardPrimersOption = addComboBoxOption(FORWARD_PRIMER, "Forward Primer Name", forwardPrimers, forwardPrimers.get(0));

        this.reversePrimers = fimsToLims.getReversePcrPrimers();
        List<OptionValue> reversePrimers = getPrimerOptionValues(this.reversePrimers);
        final ComboBoxOption<OptionValue> reversePrimersOption = addComboBoxOption(REVERSE_PRIMER, "Reverse Primer Name", reversePrimers, reversePrimers.get(0));

        reactionTypeOption.addChangeListener(new SimpleListener(){
            public void objectChanged() {
                boolean pcr = reactionTypeOption.getValue().equals(reactionTypes[0]);
                if(pcr) {
                    PrimerPerformanceOptions.this.forwardPrimers = fimsToLims.getForwardPcrPrimers();
                    PrimerPerformanceOptions.this.reversePrimers = fimsToLims.getReversePcrPrimers();
                }
                else {
                    PrimerPerformanceOptions.this.forwardPrimers = fimsToLims.getForwardSequencingPrimers();
                    PrimerPerformanceOptions.this.reversePrimers = fimsToLims.getReverseSequencingPrimers();    
                }
                forwardPrimersOption.setPossibleValues(getPrimerOptionValues(PrimerPerformanceOptions.this.forwardPrimers));
                reversePrimersOption.setPossibleValues(getPrimerOptionValues(PrimerPerformanceOptions.this.reversePrimers));
            }
        });

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
        OptionValue value = (OptionValue) getValue(FIMS_FIELDS);
        return value != null ? value.getLabel() : null;
    }

    public boolean isPcr() {
        return PCR.equals(getValueAsString(REACTION_TYPE));
    }
}
