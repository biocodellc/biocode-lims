package com.biomatters.plugins.biocode.assembler.annotate;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.options.NamePartOption;
import com.biomatters.plugins.biocode.options.NameSeparatorOption;
import org.jdom.Element;
import org.virion.jam.util.SimpleListener;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;


public class AnnotateFimsDataOptions extends Options {
    private StringOption plateNameOption;
    private Option<String,? extends JComponent> afterLabel;
    private final NamePartOption namePartOption = new NamePartOption("namePart", "");
    private final NameSeparatorOption nameSeparatorOption = new NameSeparatorOption("nameSeparator", "");
    private RadioOption<OptionValue> useExistingPlate;
    private OptionValue[] useExistingValues;

    public AnnotateFimsDataOptions(AnnotatedPluginDocument[] documents) throws DocumentOperationException {
        super(AnnotateLimsDataOptions.class);
        Options useExistingOptions = new Options(this.getClass());
        plateNameOption = useExistingOptions.addStringOption("forwardPlateName", "FIMS Plate Name:", "");
        plateNameOption.setDescription("Name of the plate your selected sequences correspond to in the FIMS. must not be empty.");
        useExistingOptions.beginAlignHorizontally(null, false);
        useExistingOptions.addLabel("well is");
        useExistingOptions.addCustomOption(namePartOption);
        useExistingOptions.addLabel("part of name");
        afterLabel = useExistingOptions.addLabel("separated by");
        useExistingOptions.addCustomOption(nameSeparatorOption);
        SimpleListener namePartListener = new SimpleListener() {
            public void objectChanged() {
                updateOptions();
            }
        };
        namePartOption.addChangeListener(namePartListener);
        namePartListener.objectChanged();
        useExistingOptions.endAlignHorizontally();

        useExistingValues = new OptionValue[] {
                new OptionValue("plateFromOptions", "Use the following plate/well"),
                new OptionValue("existingPlate", "Use annotated plate/well", "Your sequences will have annotated plate and well values if you have run annotate with \nFIMS data on them before, or if you downloaded them directly from the database.")
        };

        addChildOptions("useExistingOptions", "", "", useExistingOptions);

        useExistingPlate = addRadioOption("useExistingPlate", "", useExistingValues, useExistingValues[0], Alignment.VERTICAL_ALIGN);
        useExistingPlate.addDependent(useExistingValues[0], useExistingOptions, true);
    }

    private void updateOptions() {
        boolean requiresSeparator = namePartOption.getPart() != 0;
        afterLabel.setEnabled(requiresSeparator);
        nameSeparatorOption.setEnabled(requiresSeparator);
    }

    public boolean useExistingPlate() {
        return useExistingPlate.getValue().equals(useExistingValues[1]);
    }

    public String getExistingPlateName() {
        return plateNameOption.getValue();
    }

    private static List<OptionValue> convertDocumentFieldsToOptionValues(List<DocumentField> fields) {
        List<OptionValue> values = new ArrayList<OptionValue>();
        for(DocumentField field : fields) {
            values.add(new OptionValue(field.getCode(), field.getName(), field.getDescription()));
        }
        return values;
    }

    public AnnotateFimsDataOptions(Element e) throws XMLSerializationException{
        super(e);
    }


    public String getNameSeaparator() {
        return nameSeparatorOption.getSeparatorString();
    }

    public int getNamePart() {
        return namePartOption.getPart();
    }
}
