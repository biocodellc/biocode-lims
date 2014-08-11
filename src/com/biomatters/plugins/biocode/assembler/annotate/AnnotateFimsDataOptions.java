package com.biomatters.plugins.biocode.assembler.annotate;

import com.biomatters.geneious.publicapi.components.GLabel;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.options.NamePartOption;
import com.biomatters.plugins.biocode.options.NameSeparatorOption;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import org.jdom.Element;
import org.virion.jam.util.SimpleListener;

import javax.swing.*;
import java.util.List;


public class AnnotateFimsDataOptions extends Options {
    private StringOption plateNameOption;
    private Option<String,? extends JComponent> afterLabel;
    private final NamePartOption namePartOption = new NamePartOption("namePart", "");
    private final NameSeparatorOption nameSeparatorOption = new NameSeparatorOption("nameSeparator", "");
    private final NamePartOption namePartOptionForField = new NamePartOption("namePart2", "");
    private final NameSeparatorOption nameSeparatorOptionForField = new NameSeparatorOption("nameSeparator2", "");
    private RadioOption<OptionValue> useExistingPlate;
    private OptionValue[] useExistingValues;
    private ComboBoxOption<OptionValue> fimsField;

    public AnnotateFimsDataOptions(AnnotatedPluginDocument[] documents) throws DocumentOperationException {
        super(AnnotateLimsDataOptions.class);
        if(!BiocodeService.getInstance().isLoggedIn()) {
            throw new DocumentOperationException(BiocodeUtilities.NOT_CONNECTED_ERROR_MESSAGE);
        }
        GLabel warningLabel = new GLabel("You should use a field which is unique for each tissue, or Geneious will choose the first tissue that matches", true);
        Option warningLabelOption = addCustomComponent(warningLabel);
        warningLabelOption.setSpanningComponent(true);

        Options useExistingOptions = new Options(this.getClass());
        useExistingOptions.addLabel(" ");
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
                new OptionValue("matchField", ""),
                new OptionValue("plateFromOptions", "Use the following plate/well"),
                new OptionValue("existingPlate", "Use annotated plate/well", "Your sequences will have annotated plate and well values if you have run annotate with \nFIMS data on them before, or if you downloaded them directly from the database.")
        };

        addChildOptions("useExistingOptions", "", "", useExistingOptions);

        useExistingPlate = addRadioOption("useExistingPlate", "", useExistingValues, useExistingValues[1], Alignment.VERTICAL_ALIGN);
        useExistingPlate.addDependent(useExistingValues[1], useExistingOptions, true);
        useExistingPlate.addDependent(useExistingValues[0], warningLabelOption, false);

        List<OptionValue> fimsFields = AnnotateUtilities.getOptionValuesForFimsFields();
        if(fimsFields.size() > 0) {
            fimsField = addComboBoxOption("fimsField", "", fimsFields, fimsFields.get(0));
        }
        else {
            OptionValue[] noValues = {new OptionValue("none", "None")};
            fimsField = addComboBoxOption("fimsField", "", noValues, noValues[0]);
        }
        useExistingPlate.addDependent(useExistingValues[0], fimsField, true);
        Option<String, ? extends JComponent> label = addLabel(" matches ");
        useExistingPlate.addDependent(useExistingValues[0], label, true);
        addCustomOption(namePartOptionForField);
        useExistingPlate.addDependent(useExistingValues[0], namePartOptionForField, true);
        Option<String, ? extends JComponent> label2 = addLabel("part of name");
        useExistingPlate.addDependent(useExistingValues[0], label2, true);
        Option<String, ? extends JComponent> label3 = addLabel("separated by");
        useExistingPlate.addDependent(useExistingValues[0], label3, true);
        addCustomOption(nameSeparatorOptionForField);
        useExistingPlate.addDependent(useExistingValues[0], nameSeparatorOptionForField, true);
        useExistingPlate.setDependentPosition(RadioOption.DependentPosition.RIGHT);
        boolean noFields = false;
        if(fimsFields.size() == 0) {
            useExistingValues[0].setEnabled(false);
            noFields = true;
        }
        if(BiocodeService.getInstance().getActiveFIMSConnection().getPlateDocumentField() == null
                || BiocodeService.getInstance().getActiveFIMSConnection().getWellDocumentField() == null) {
            useExistingValues[1].setEnabled(false);
            useExistingValues[2].setEnabled(false);
            if(noFields) {
                throw new DocumentOperationException("Your FIMS does not have annotated plates and wells, or fields which we can match to");
            }
        }
    }

    private void updateOptions() {
        boolean requiresSeparator = namePartOption.getPart() != 0;
        afterLabel.setEnabled(requiresSeparator);
        nameSeparatorOption.setEnabled(requiresSeparator);
    }

    public boolean matchField() {
        return useExistingPlate.getValue().equals(useExistingValues[0]);
    }

    public DocumentField getFieldToMatch() {
        String fieldCode = fimsField.getValue().getName();
        for(DocumentField field : BiocodeService.getInstance().getActiveFIMSConnection().getSearchAttributes()) {
            if(field.getCode().equals(fieldCode)) {
                return field;
            }
        }
        return null;
    }

    public boolean useExistingPlate() {
        return useExistingPlate.getValue().equals(useExistingValues[2]);
    }

    public String getExistingPlateName() {
        return plateNameOption.getValue();
    }

    public AnnotateFimsDataOptions(Element e) throws XMLSerializationException{
        super(e);
    }


    public String getNameSeaparator() {
        if(matchField()) {
            return nameSeparatorOptionForField.getSeparatorString();
        }
        return nameSeparatorOption.getSeparatorString();
    }

    public int getNamePart() {
        if(matchField()) {
            return namePartOptionForField.getPart();
        }
        return namePartOption.getPart();
    }
}
