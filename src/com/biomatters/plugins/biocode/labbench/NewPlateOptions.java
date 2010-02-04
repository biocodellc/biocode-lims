package com.biomatters.plugins.biocode.labbench;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;
import org.virion.jam.util.SimpleListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 28/09/2009 10:49:11 PM
 */
public class NewPlateOptions extends Options{

    private AnnotatedPluginDocument[] documents;

    public NewPlateOptions(AnnotatedPluginDocument... documents) throws DocumentOperationException {
        if(!BiocodeService.getInstance().isLoggedIn()) {
            throw new DocumentOperationException(BiocodeUtilities.NOT_CONNECTED_ERROR_MESSAGE);
        }

        this.documents = documents;

        //analyse the documents
        final boolean fromExistingPossible = documents.length > 0;
        final boolean fourPlates = documents.length > 1;
        Plate.Size pSize = null;
        for(AnnotatedPluginDocument doc : documents) {
            PlateDocument plateDoc = (PlateDocument)doc.getDocument();
            Plate.Size size = plateDoc.getPlate().getPlateSize();
            if(pSize != null && size != pSize) {
                throw new DocumentOperationException("All selected plates must be of the same size");
            }
            pSize = size;
        }
        final Plate.Size plateSize = pSize;
        if(fourPlates && plateSize != Plate.Size.w96) {
            throw new DocumentOperationException("You may only combine 96 well plates.");
        }

        final Options.OptionValue[] plateValues = new Options.OptionValue[] {
                new Options.OptionValue("individualReactions", "Individual Reactions"),
                new Options.OptionValue("48Plate", "48 well plate"),
                new Options.OptionValue("96Plate", "96 well plate"),
                new Options.OptionValue("384Plate", "384 well plate")
        };

        Options.OptionValue[] typeValues = new Options.OptionValue[] {
                new Options.OptionValue("extraction", "Extraction"),
                new Options.OptionValue("pcr", "PCR"),
                new Options.OptionValue("cyclesequencing", "Cycle Sequencing")
        };

        Options.BooleanOption fromExistingOption = null;
        if(fromExistingPossible) {
            fromExistingOption = addBooleanOption("fromExisting", "Create plate from existing document", false);
            fromExistingOption.setSpanningComponent(true);
        }

        addComboBoxOption("reactionType", "Type of reaction", typeValues, typeValues[0]);
        final Options.RadioOption<Options.OptionValue> plateOption = addRadioOption("plateType", "", plateValues, plateValues[2], Options.Alignment.VERTICAL_ALIGN);


        final Options.IntegerOption reactionNumber = addIntegerOption("reactionNumber", "Number of reactions", 1, 1, 26);
        plateOption.addDependent(plateValues[0], reactionNumber, true);

        final Options quadrantOptions = new Options(this.getClass());
        final QuadrantOption quadrantOption = new QuadrantOption("value", "", 1);
        quadrantOption.setSpanningComponent(true);
        quadrantOptions.addCustomOption(quadrantOption);
        quadrantOptions.setVisible(false);
        addChildOptions("quadrant", "Quadrant", "The quadrant of the 384 well plate to take the reactions from", quadrantOptions);

        final Options docChooserOptions = new Options(this.getClass());
        docChooserOptions.setVisible(false);
        List<OptionValue> docValues = getDocumentOptionValues(documents);
        docChooserOptions.beginAlignHorizontally("", true);
        Options.ComboBoxOption<Options.OptionValue> q1Option = docChooserOptions.addComboBoxOption("q1", "q1", docValues, docValues.get(0));
        Options.ComboBoxOption<Options.OptionValue> q2Option = docChooserOptions.addComboBoxOption("q2", "q2", docValues, docValues.get(0));
        docChooserOptions.endAlignHorizontally();
        docChooserOptions.beginAlignHorizontally("", true);
        Options.ComboBoxOption<Options.OptionValue> q3Option = docChooserOptions.addComboBoxOption("q3", "q3", docValues, docValues.get(0));
        Options.ComboBoxOption<Options.OptionValue> q4Option = docChooserOptions.addComboBoxOption("q4", "q4", docValues, docValues.get(0));
        docChooserOptions.endAlignHorizontally();
        List<Options.ComboBoxOption<Options.OptionValue>> comboList = Arrays.asList(q1Option, q2Option, q3Option, q4Option);
        q1Option.addChangeListener(new ComboBoxListener(comboList, q1Option, docValues));
        q2Option.addChangeListener(new ComboBoxListener(comboList, q2Option, docValues));
        q3Option.addChangeListener(new ComboBoxListener(comboList, q3Option, docValues));
        q4Option.addChangeListener(new ComboBoxListener(comboList, q4Option, docValues));

        addChildOptions("fromQuadrant", "Quadrant", "", docChooserOptions);


        if(fromExistingOption != null) {
            final Options.BooleanOption fromExistingOption1 = fromExistingOption;
            SimpleListener fromExistingListener = new SimpleListener() {
                public void objectChanged() {
                    quadrantOptions.setVisible(fromExistingOption1.getValue() && !fourPlates && plateSize == Plate.Size.w384 && plateOption.getValue().equals(plateValues[2]));
                    docChooserOptions.setVisible(fromExistingOption1.getValue() && plateSize == Plate.Size.w96 && plateOption.getValue().equals(plateValues[3]));
                    reactionNumber.setEnabled(!fromExistingOption1.getValue());
                }
            };
            fromExistingOption.addChangeListener(fromExistingListener);
            plateOption.addChangeListener(fromExistingListener);
            fromExistingListener.objectChanged();
        }
    }

    public Plate.Size getPlateSize() {
        Plate.Size sizeFromOptions = null;
        Options.OptionValue plateSizeValue = (Options.OptionValue)getValue("plateType");

        if(plateSizeValue.getName().equals("48Plate")) {
            return Plate.Size.w48;
        }
        else if(plateSizeValue.getName().equals("96Plate")) {
            return Plate.Size.w96;
        }
        else if(plateSizeValue.getName().equals("384Plate")) {
            return Plate.Size.w384;
        }

        return null;
    }

    public Reaction.Type getReactionType() {
        Options.OptionValue reactionType = (Options.OptionValue)getValue("reactionType");

        if(reactionType.getName().equals("extraction")) {
            return Reaction.Type.Extraction;
        }
        else if(reactionType.getName().equals("pcr")) {
            return Reaction.Type.PCR;
        }
        else {
            return Reaction.Type.CycleSequencing;
        }
    }

    public boolean isFromExisting() {
        return "true".equals(getValueAsString("fromExisting"));
    }

    @Override
    public String verifyOptionsAreValid() {
        //analyse the documents
        final boolean fromExistingPossible = documents.length > 0;
        final boolean fourPlates = documents.length == 4;
        Plate.Size pSize = null;
        for(AnnotatedPluginDocument doc : documents) {
            PlateDocument plateDoc = null;
            try {
                plateDoc = (PlateDocument)doc.getDocument();
            } catch (DocumentOperationException e) {
                return e.getMessage();
            }
            Plate.Size size = plateDoc.getPlate().getPlateSize();
            if(pSize != null && size != pSize) {
                return "All plates must be of the same size";
            }
            pSize = size;
        }
        final Plate.Size plateSize = pSize;
        if(fourPlates && plateSize != Plate.Size.w96) {
            return "You may only combine 96 well plates.  Select up to four 96 well plate documents.";
        }

        if(fourPlates && getPlateSize() != Plate.Size.w384) {
            return "Several plate documents can only be combined into a 384 well plate.  Select \"384 well plate\".";
        }

        if(isFromExisting()) {
            if(getPlateSize() == Plate.Size.w96 && plateSize != Plate.Size.w384 && documents.length > 1){
                return "You can only create 96 well plates from a single 96 or 384 well plate document";
            }
            if(getPlateSize() == Plate.Size.w384 && plateSize != Plate.Size.w96 && plateSize != Plate.Size.w384) {
                return "You can only create 384 well plates from a single 384 well or up to 4 96 well plate documents";
            }
            if(plateSize == null && getPlateSize() != null) {
                return "You cannot create a "+getPlateSize()+" plate from a set of reactions.";
            }
            if((plateSize != null && getPlateSize() != null) && (getPlateSize() != Plate.Size.w96 && getPlateSize() != Plate.Size.w384)) {
                return "You cannot create a "+getPlateSize()+" well plate from a "+plateSize+" well plate.";
            }
            if(getPlateSize() == Plate.Size.w384 && plateSize != Plate.Size.w384) {
                int docCount = 0;
                for(int i=0; i < 4; i++) {
                    AnnotatedPluginDocument doc = getPlateForQuadrant(documents, i);
                    if(doc != null) {
                        docCount++;
                    }
                }
                if(docCount == 0) {
                    return "Please select at least one quadrant";
                }
            }
        }

        return super.verifyOptionsAreValid();
    }

    AnnotatedPluginDocument getPlateForQuadrant(AnnotatedPluginDocument[] documents, int i) {
        AnnotatedPluginDocument doc = null;
        Options.OptionValue docName = (Options.OptionValue)getValue("fromQuadrant.q" + (i + 1));
        for(AnnotatedPluginDocument adoc : documents) {
            if(adoc.getURN().toString().equals(docName.getName())){
                doc = adoc;
            }
        }
        return doc;
    }

    private List<Options.OptionValue> getDocumentOptionValues(AnnotatedPluginDocument[] docs) {
        List<Options.OptionValue> result = new ArrayList<OptionValue>();

        result.add(new Options.OptionValue("null", "None"));
        for(AnnotatedPluginDocument doc : docs) {
            result.add(new Options.OptionValue(doc.getURN().toString(), doc.getName()));
        }

        return result;
    }

    static class ComboBoxListener implements SimpleListener{
        private List<Options.ComboBoxOption<Options.OptionValue>> options;
        private Options.ComboBoxOption sourceOption;
        List<Options.OptionValue> values;

        ComboBoxListener(List<Options.ComboBoxOption<Options.OptionValue>> options, Options.ComboBoxOption sourceOption, List<Options.OptionValue> values) {
            this.options = options;
            this.sourceOption = sourceOption;
            this.values = values;
        }

        public void objectChanged() {


            for(Options.ComboBoxOption option : options) {
                if(option == sourceOption) {
                    continue;
                }

                if(option.getValue().equals(sourceOption.getValue()) && !option.getValue().toString().equals("null")) {
                    //change it.
                    List<Options.OptionValue> values = new ArrayList(this.values);
                    values.remove(sourceOption.getValue());
                    for(Options.ComboBoxOption optionb : options) {
                        if(!optionb.getValue().toString().equals("null")) {
                            values.remove(optionb.getValue());
                        }
                    }
                    if(values.size() > 0) {
                        option.setValue(values.get(0));
                    }
                }
            }
        }
    }



}
