package com.biomatters.plugins.moorea.submission.genbank.barstool;

import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.moorea.assembler.verify.Pair;
import org.jdom.Element;
import org.virion.jam.util.SimpleListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Richard
 * @version $Id$
 */
public class ExtraSubmissionFieldsOptions extends Options {

    private static final String[] OTHER_SUBMISSION_FIELDS = new String[] {
            "Authority", "Biotype", "Biovar", "Breed", "Cell_line", "Cell_type", "Chemovar", "Clone", "Cultivar",
            "Dev_stage", "Ecotype", "Forma", "Forma_specialis", "Genotype", "Haplotype", "Isolate", "Isolation source",
            "Lab_host", "Natural_host", "Note", "Pathovar", "Pop_variant", "Serogroup", "Serotype", "Serovar", "Sex",
            "Strain", "Sub_species", "Subclone", "Subtype", "Substrain", "Tissue_lib", "Tissue_type", "Type", "Variety"
    };
    private MultipleOptions fieldsOptions;
    private BooleanOption enableExtraFieldsOption;

    public ExtraSubmissionFieldsOptions(List<OptionValue> stringFields) {
        super(ExtraSubmissionFieldOptions.class);
        enableExtraFieldsOption = addBooleanOption("enableExtraFields", "Include extra fields", false);
        fieldsOptions = addMultipleOptions("extraSubmissionFields", new ExtraSubmissionFieldOptions(stringFields), false);
        initListeners();
    }

    private void initListeners() {
        final SimpleListener enabledListener = new SimpleListener() {
            public void objectChanged() {
                fieldsOptions.setEnabled(enableExtraFieldsOption.getValue());
            }
        };
        enableExtraFieldsOption.addChangeListener(enabledListener);
        enabledListener.objectChanged();
    }

    protected ExtraSubmissionFieldsOptions(Element e) throws XMLSerializationException {
        super(e);
        fieldsOptions = getMultipleOptions("extraSubmissionFields");
        enableExtraFieldsOption = (BooleanOption) getOption("enableExtraFields");
        initListeners();
    }

    /**
     * pairs of submission field name to document field code
     */
    List<Pair<String, String>> getExtraFields() {
        if (!enableExtraFieldsOption.getValue()) {
            return Collections.emptyList();
        }
        List<Pair<String, String>> fields = new ArrayList<Pair<String, String>>();
        for (Options fieldOptions : fieldsOptions.getValues()) {
            fields.add(new Pair<String, String>(fieldOptions.getValueAsString("fieldName"), fieldOptions.getValueAsString("fieldValue")));
        }
        return fields;
    }

    private static final class ExtraSubmissionFieldOptions extends Options {

        protected ExtraSubmissionFieldOptions(Element e) throws XMLSerializationException {
            super(e);
        }

        public ExtraSubmissionFieldOptions(List<OptionValue> stringFields) {
            super(ExtraSubmissionFieldsOptions.class);
            beginAlignHorizontally(null, false);
            addEditableComboBoxOption("fieldName", "Field Name:", "", OTHER_SUBMISSION_FIELDS);
            addComboBoxOption("fieldValue", "Field Value:", stringFields, stringFields.get(0));
            endAlignHorizontally();
        }
    }

}
