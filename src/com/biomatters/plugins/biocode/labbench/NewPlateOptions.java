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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 28/09/2009 10:49:11 PM
 */
public class NewPlateOptions extends Options{

    static final String FROM_EXISTING_OPTION_NAME = "fromExistingPlates";
    private AnnotatedPluginDocument[] documents;
    private final OptionValue INDIVIDUAL_REACTIONS = new OptionValue("individualReactions", "");
    private final OptionValue STRIPS = new OptionValue("strips", "");
    private final OptionValue PLATE_48 = new OptionValue("48Plate", "48 well plate");
    private final OptionValue PLATE_96 = new OptionValue("96Plate", "96 well plate");
    private final OptionValue PLATE_384 = new OptionValue("384Plate", "384 well plate");

    public NewPlateOptions(AnnotatedPluginDocument... documents) throws DocumentOperationException {
        if(!BiocodeService.getInstance().isLoggedIn()) {
            throw new DocumentOperationException(BiocodeUtilities.NOT_CONNECTED_ERROR_MESSAGE);
        }

        this.documents = documents;

        //analyse the documents
        final AtomicBoolean fourPlates = new AtomicBoolean(false);
        boolean allPcrOrSequencing = true;
        Plate.Size pSize = null;
        int numberOfReactions = 0;

        boolean fromExistingPlates = false;
        boolean fromExistingTissues = false;
        if (documents.length > 0) {
            if (PlateDocument.class.isAssignableFrom(documents[0].getDocumentClass())) {
                fromExistingPlates = true;
            } else if (TissueDocument.class.isAssignableFrom(documents[0].getDocumentClass())) {
                fromExistingTissues = true;
            } else {
                throw new DocumentOperationException("Invalid document type encountered: " + documents[0].getDocumentClass().getSimpleName() + ".");
            }
        }

        if (fromExistingPlates) {
            if (documents.length == 4) {
                fourPlates.set(true);
            }
            for (AnnotatedPluginDocument doc : documents) {
                PlateDocument plateDoc = (PlateDocument) doc.getDocument();
                Plate.Size size = plateDoc.getPlate().getPlateSize();
                if (plateDoc.getPlate().getReactionType() == Reaction.Type.Extraction) {
                    allPcrOrSequencing = false;
                }
                if (pSize != null && size != pSize) {
                    throw new DocumentOperationException("All selected plates must be of the same size");
                }
                numberOfReactions += plateDoc.getPlate().getReactions().length;
                pSize = size;
            }
        }
        final Plate.Size plateSize = pSize;
        if (fourPlates.get() && plateSize != Plate.Size.w96) {
            throw new DocumentOperationException("You may only combine 96 well plates.");
        }

        final Options.OptionValue[] plateValues = new Options.OptionValue[] {
                INDIVIDUAL_REACTIONS,
                STRIPS,
                PLATE_48,
                PLATE_96,
                PLATE_384
        };

        final Options.OptionValue[] typeValues = new Options.OptionValue[] {
                new Options.OptionValue("extraction", "Extraction"),
                new Options.OptionValue("pcr", "PCR"),
                new Options.OptionValue("cyclesequencing", "Cycle Sequencing")
        };
        final Options.OptionValue[] passedValues = new Options.OptionValue[] {
                new Options.OptionValue("passed", "Passed"),
                new Options.OptionValue("failed", "Failed")
        };

        Options.BooleanOption fromExistingOption = null;
        Options.BooleanOption onlyFailed;
        ComboBoxOption passedOrFailed;

        if (fromExistingPlates) {
            fromExistingOption = addBooleanOption(FROM_EXISTING_OPTION_NAME, "Create plate from existing plate documents", false);
            fromExistingOption.setSpanningComponent(true);
            beginAlignHorizontally(null, false);
            onlyFailed = addBooleanOption("onlyFailed", "Copy only ", false);
            passedOrFailed = addComboBoxOption("passedOrFailed", "", passedValues, passedValues[0]);
            addLabel(" reactions").setFillHorizontalSpace(true);
            onlyFailed.setDisabledValue(false);
            onlyFailed.addDependent(passedOrFailed, true);
            endAlignHorizontally();
            if(allPcrOrSequencing) {
                fromExistingOption.addDependent(onlyFailed, true);
            }
            else {
                onlyFailed.setEnabled(false);
            }
        } else if (fromExistingTissues) {
            fromExistingOption = addBooleanOption("fromExistingTissues", "Create plate from existing tissue documents", false);
            fromExistingOption.setSpanningComponent(true);
        }

        addComboBoxOption("reactionType", "Type of reaction", typeValues, typeValues[0]);
        final Options.RadioOption<Options.OptionValue> plateOption = addRadioOption("plateType", "", plateValues, PLATE_96, Options.Alignment.VERTICAL_ALIGN);


        final Options.IntegerOption reactionNumber = addIntegerOption("reactionNumber", "", 1, 1, 26);
        final Options.IntegerOption stripNumber = addIntegerOption("stripNumber", "", 1, 1, 6);
        plateOption.addDependent(INDIVIDUAL_REACTIONS, reactionNumber, true);
        plateOption.addDependent(INDIVIDUAL_REACTIONS, addLabel(" individual reactions"), true);
        plateOption.addDependent(STRIPS, stripNumber, true);
        plateOption.addDependent(STRIPS, addLabel(" 8-reaction strips"), true);
        plateOption.setDependentPosition(RadioOption.DependentPosition.RIGHT);
        if(plateSize == null) {
            reactionNumber.setValue(numberOfReactions);
        }

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

        if (fromExistingOption != null) {
            final Options.BooleanOption fromExistingOption1 = fromExistingOption;
            SimpleListener fromExistingListener = new SimpleListener() {
                public void objectChanged() {
                    quadrantOptions.setVisible(fromExistingOption1.getValue() && !fourPlates.get() && plateSize == Plate.Size.w384 && plateOption.getValue().equals(PLATE_96));
                    docChooserOptions.setVisible(fromExistingOption1.getValue() && plateSize == Plate.Size.w96 && plateOption.getValue().equals(PLATE_384));
                    reactionNumber.setEnabled(!fromExistingOption1.getValue() || plateSize == null);
                }
            };
            fromExistingOption.addChangeListener(fromExistingListener);
            plateOption.addChangeListener(fromExistingListener);
            fromExistingListener.objectChanged();
        }
    }

    public Plate.Size getPlateSize() {
        return Plate.getSizeEnum(getNumberOfReactions());
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

    public boolean isFromExistingPlates() {
        return "true".equals(getValueAsString(FROM_EXISTING_OPTION_NAME));
    }

    public boolean isFromExistingTissues() { return "true".equals(getValueAsString("fromExistingTissues"));}

    public boolean copyOnlyFailedReactions() {
        return "true".equals(getValueAsString("onlyFailed")) && "failed".equals(getValueAsString("passedOrFailed"));
    }

    public boolean copyOnlyPassedReactions() {
        return "true".equals(getValueAsString("onlyFailed"))&& "passed".equals(getValueAsString("passedOrFailed"));
    }

    @Override
    public String verifyOptionsAreValid() {
        //analyse the documents
        boolean fourPlates = false;
        Plate.Size pSize = null;

        if (isFromExistingPlates()) {
            fourPlates = documents.length == 4;
            for (AnnotatedPluginDocument doc : documents) {
                PlateDocument plateDoc;
                try {
                    plateDoc = (PlateDocument) doc.getDocument();
                } catch (DocumentOperationException e) {
                    return e.getMessage();
                }
                Plate.Size size = plateDoc.getPlate().getPlateSize();
                if (pSize != null && size != pSize) {
                    return "All plates must be of the same size";
                }
                pSize = size;
            }
        }

        final Plate.Size plateSize = pSize;
        if(fourPlates && plateSize != Plate.Size.w96) {
            return "You may only combine 96 well plates.  Select up to four 96 well plate documents.";
        }

        if(fourPlates && getPlateSize() != Plate.Size.w384) {
            return "Several plate documents can only be combined into a 384 well plate.  Select \"384 well plate\".";
        }

        if(isFromExistingPlates()) {
            if(getPlateSize() == Plate.Size.w96 && plateSize != Plate.Size.w384 && documents.length > 1){
                return "You can only create 96 well plates from a single 96 or 384 well plate document";
            }
            if(getPlateSize() == Plate.Size.w384 && plateSize != Plate.Size.w96 && plateSize != Plate.Size.w384) {
                return "You can only create 384 well plates from a single 384 well or up to 4 96 well plate documents";
            }
            if(plateSize == null && getPlateSize() != null) {
                return "You cannot create a "+getPlateSize()+" plate from a set of reactions.";
            }
            if(!(plateSize == Plate.Size.w96 && getPlateSize() == Plate.Size.w384) && !(plateSize == Plate.Size.w384 && getPlateSize() == Plate.Size.w96) && plateSize != getPlateSize()) {
                return "You cannot create a "+(getPlateSize() == null ? "set of reactions" : getPlateSize()+" well")+" plate from a "+plateSize+" well plate.";
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

    public int getNumberOfReactions() {
        Options.OptionValue plateType = (Options.OptionValue)getValue("plateType");
        if (plateType.equals(PLATE_48)) {
            return 48;
        } else if (plateType.equals(PLATE_96)) {
            return 96;
        } else if (plateType.equals(PLATE_384)) {
            return 384;
        } else if (plateType.equals(STRIPS)) {
            return (Integer)getOption("stripNumber").getValue()*8;   
        } else if (plateType.equals(INDIVIDUAL_REACTIONS)) {
            return (Integer)getOption("reactionNumber").getValue();
        } else {
            throw new IllegalStateException("Unknown plate type: " + plateType.getName() + ".");
        }
    }

    static class ComboBoxListener implements SimpleListener{
        private List<Options.ComboBoxOption<Options.OptionValue>> options;
        private Options.ComboBoxOption<OptionValue> sourceOption;
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
                    for(Options.ComboBoxOption<OptionValue> optionb : options) {
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
