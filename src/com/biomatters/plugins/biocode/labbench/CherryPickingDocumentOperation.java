package com.biomatters.plugins.biocode.labbench;

import com.biomatters.geneious.publicapi.documents.DocumentUtilities;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.utilities.GuiUtilities;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import com.biomatters.plugins.biocode.labbench.reaction.*;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import com.biomatters.plugins.biocode.labbench.plates.PlateViewer;
import com.biomatters.plugins.biocode.BiocodePlugin;

import java.util.*;
import java.sql.SQLException;

import jebl.util.ProgressListener;
import org.virion.jam.util.SimpleListener;
import org.jdom.Element;

import javax.swing.*;

/**
 * @author Steve
 * @version $Id: 22/12/2009 7:04:21 PM steve $
 */
public class CherryPickingDocumentOperation extends DocumentOperation {

    public GeneiousActionOptions getActionOptions() {
        return GeneiousActionOptions.createSubmenuActionOptions(BiocodePlugin.getSuperBiocodeAction(),new GeneiousActionOptions("Cherry picking", "Create new Reactions from Failed Reactions", BiocodePlugin.getIcons("cherry_24.png"))
                .setProOnly(true).setInPopupMenu(true, 0.01));
    }

    public String getHelp() {
        return null;
    }

    @Override
    public boolean isDocumentGenerator() {
        return false;
    }

    public DocumentSelectionSignature[] getSelectionSignatures() {
        return new DocumentSelectionSignature[]{new DocumentSelectionSignature(PlateDocument.class, 1, Integer.MAX_VALUE)};
    }

    @Override
    public Options getOptions(AnnotatedPluginDocument... documents) throws DocumentOperationException {
        //Reaction.Type reactionType = null;
        for(AnnotatedPluginDocument doc : documents) {
            PlateDocument plateDoc = (PlateDocument)doc.getDocument();
            if(plateDoc.getPlate().getReactionType() == Reaction.Type.Extraction) {
                throw new DocumentOperationException("You must select either PCR or Cycle Sequencing plates");
            }
//            if(reactionType != null && plateDoc.getPlate().getReactionType() != reactionType) {
//                throw new DocumentOperationException("You must select plates of the same type");
//            }
//            reactionType = plateDoc.getPlate().getReactionType();
        }
//        if(reactionType == Reaction.Type.Extraction) {
//            throw new DocumentOperationException("You must select either PCR or Cycle Sequencing plates");
//        }

        Options options = new Options(this.getClass());

        final List<Options.OptionValue> sizeValues = Arrays.asList(
                new Options.OptionValue("null", "Just give me a list of reactions"),
                new Options.OptionValue(Plate.Size.w96.name(), Plate.Size.w96.toString()),
                new Options.OptionValue(Plate.Size.w384.name(), Plate.Size.w384.toString())
        );

        CherryPickingOptions cherryPickingConditionsOptions = new CherryPickingOptions(this.getClass());

        final Options.RadioOption<Options.OptionValue> plateSizeOption = options.addRadioOption("plateSize", "Plate Size", sizeValues, sizeValues.get(0), Options.Alignment.HORIZONTAL_ALIGN);

        Options.OptionValue[] plateTypeValues = new Options.OptionValue[] {
                new Options.OptionValue(Reaction.Type.Extraction.name(), "Extraction"),
                new Options.OptionValue(Reaction.Type.PCR.name(), "PCR"),
                new Options.OptionValue(Reaction.Type.CycleSequencing.name(), "Cycle Sequencing")
        };
        options.beginAlignHorizontally("", false);
        final Options.ComboBoxOption<Options.OptionValue> plateTypeOption = options.addComboBoxOption("plateType", "Plate Type", plateTypeValues, plateTypeValues[0]);

        final List<Options.OptionValue> directionValues = Arrays.asList(
                new Options.OptionValue("across", "Fill across"),
                new Options.OptionValue("down", "Fill down")
        );

        options.addRadioOption("direction", "", directionValues, directionValues.get(0), Options.Alignment.HORIZONTAL_ALIGN);

        options.endAlignHorizontally();

        SimpleListener plateTypeListener = new SimpleListener() {
            public void objectChanged() {
                plateTypeOption.setEnabled(plateSizeOption.getValue() != sizeValues.get(0));
            }
        };
        plateSizeOption.addChangeListener(plateTypeListener);
        plateTypeListener.objectChanged();

        Options.Option<String, ? extends JComponent> label = options.addLabel("Geneious will select reactions that conform to the following:");
        label.setSpanningComponent(true);

        options.addMultipleOptions("conditions", cherryPickingConditionsOptions, false);

        return options;
    }

    public static class CherryPickingOptions extends Options {
        static final Options.OptionValue[] cherryPickingConditions = new Options.OptionValue[] {
                new Options.OptionValue("reactionState", "Reaction State"),
                new Options.OptionValue("taxonomy", "Taxonomy"),
                new Options.OptionValue("primer", "Primer")
        };

        static final OptionValue[] valueValues = new OptionValue[] {
                ReactionOptions.NOT_RUN_VALUE,
                ReactionOptions.PASSED_VALUE,
                ReactionOptions.FAILED_VALUE
        };

        public CherryPickingOptions(Class sourceClass) {
            super(sourceClass);
            beginAlignHorizontally("", false);

            addComboBoxOption("condition", "", cherryPickingConditions, cherryPickingConditions[0]);


            addComboBoxOption("value", "is", valueValues, valueValues[0]);

            addStringOption("value2", "contains", "");

            final PrimerOption valueOption3 = new PrimerOption("value3", "is");
            addCustomOption(valueOption3);


            endAlignHorizontally();
            initListener();

        }

        public CherryPickingOptions(Element e) throws XMLSerializationException{
            super(e);
            initListener();
        }

        private void initListener() {
            final Option conditionOption = getOption("condition");
            SimpleListener listener = new SimpleListener() {
                public void objectChanged() {
                    boolean value1Visible = conditionOption.getValue().equals(cherryPickingConditions[0]);
                    boolean value2Visible = conditionOption.getValue().equals(cherryPickingConditions[1]);

                    Option valueOption = getOption("value");
                    Option valueOption2 = getOption("value2");
                    Option valueOption3 = getOption("value3");

                    valueOption.setVisible(value1Visible);
                    valueOption2.setVisible(value2Visible);
                    valueOption3.setVisible(!value1Visible && !value2Visible);
                }
            };
            listener.objectChanged();
            conditionOption.addChangeListener(listener);
        }


        public boolean reactionMatches(Reaction r) {
            Option conditionOption = getOption("condition");
            String value1 = getValueAsString("value");
            String value2 = getValueAsString("value2");
            OptionValue value3 = (OptionValue)getValue("value3");
            if(conditionOption.getValue().equals(cherryPickingConditions[0])) { //reaction state
                return value1.equals(r.getFieldValue(ReactionOptions.RUN_STATUS));
            }
            else if(conditionOption.getValue().equals(cherryPickingConditions[1])) { //taxonomy
                FimsSample fimsSample = r.getFimsSample();
                if(fimsSample != null) {
                    for(DocumentField field : fimsSample.getTaxonomyAttributes()) {
                        Object value = fimsSample.getFimsAttributeValue(field.getCode());
                        if(value != null && value.toString().toLowerCase().contains(value2.toLowerCase())) {
                            return true;
                        }
                    }
                }
                return false;
            }
            else if(conditionOption.getValue().equals(cherryPickingConditions[2])) { //primer
                if(r.getType() == Reaction.Type.PCR) { //two primers
                    OptionValue primer1 = (OptionValue)r.getOptions().getValue(PCROptions.PRIMER_OPTION_ID);
                    OptionValue primer2 = (OptionValue)r.getOptions().getValue(PCROptions.PRIMER_REVERSE_OPTION_ID);

                    return primer1.equals(value3) || primer2.equals(value3);
                }
                else if(r.getType() == Reaction.Type.CycleSequencing) {
                    OptionValue primer = (OptionValue)r.getOptions().getValue(CycleSequencingOptions.PRIMER_OPTION_ID);

                    return primer.equals(value3);
                }
                return false;
            }
            return false;
        }


    }

    @Override
    public List<AnnotatedPluginDocument> performOperation(AnnotatedPluginDocument[] annotatedDocuments, ProgressListener progressListener, Options options) throws DocumentOperationException {
        List<PlateDocument> plateDocuments = new ArrayList<PlateDocument>();
        for(AnnotatedPluginDocument doc : annotatedDocuments) {
            plateDocuments.add((PlateDocument)doc.getDocument());
        }

        final List<Reaction> failedReactions = getFailedReactions(plateDocuments, options);

        if(failedReactions.size() == 0) {
            throw new DocumentOperationException("The selected plates do not contain any reactions that match your criteria");
        }

        final boolean across = options.getValueAsString("direction").equals("across");

        final List<Reaction> newReactions;
        String reactionTypeString = options.getValueAsString("plateType");
        final Reaction.Type plateType = Reaction.Type.valueOf(reactionTypeString);
        if(plateType == null) {
            throw new DocumentOperationException("There is no reaction type '"+reactionTypeString+"'");
        }

        if(plateType == Reaction.Type.Extraction) { //if it's an extraction we want to move the existing extractions to this plate, not make new ones...
            try {
                Map<String, Reaction> extractionReactions = BiocodeService.getInstance().getActiveLIMSConnection().getExtractionReactions(failedReactions);
                newReactions = new ArrayList<Reaction>();
                for(Reaction r : failedReactions) {
                    Reaction newReaction = extractionReactions.get(r.getExtractionId());
                    if(newReaction == null) {
                        newReaction = new ExtractionReaction();
                        newReaction.setExtractionId(r.getExtractionId());
                    }
                    newReactions.add(r);
                }
            } catch (SQLException e) {
                throw new DocumentOperationException("Could not fetch existing extractions from database: "+e.getMessage());
            }
        }
        else {
            newReactions = getNewReactions(failedReactions, plateType);
        }

        String plateSizeString = options.getValueAsString("plateSize");
        if(plateSizeString.equals("null")) {
            DocumentUtilities.addGeneratedDocument(DocumentUtilities.createAnnotatedPluginDocument(new CherryPickingDocument(getDocumentTitle(annotatedDocuments), failedReactions)), true);
            return null;
        }
        final Plate.Size plateSize = Plate.Size.valueOf(plateSizeString);

        final int numberOfPlatesRequired = (int)Math.ceil(((double)failedReactions.size())/plateSize.numberOfReactions());


        final List<PlateViewer> plates = new ArrayList<PlateViewer>();
        Runnable runnable = new Runnable() {
            public void run() {
                for(int i=0; i < numberOfPlatesRequired; i++) {
                    PlateViewer plate = new PlateViewer(plateSize, plateType);
                    for (int n = 0; n < plate.getPlate().getReactions().length; n++) {
                        int plateIndex;
                        if(across) {
                            plateIndex = n;
                        }
                        else {
                            int cols = plate.getPlate().getCols();
                            int rows = plate.getPlate().getRows();
                            plateIndex = ((n*cols)%(cols*rows)) + n/rows;

                        }
                        System.out.println(n+": "+plateIndex);
                        int index = i * plateSize.numberOfReactions() + n;
                        if(index > failedReactions.size()-1) {
                            break;
                        }
                        Reaction plateReaction = plate.getPlate().getReactions()[plateIndex];
                        Reaction oldReaction = failedReactions.get(index);
                        Reaction newReaction = newReactions.get(index);
                        if(newReaction == null) {
                            continue;
                        }
                        plateReaction.getOptions().valuesFromXML(newReaction.getOptions().valuesToXML("values"));
                        plateReaction.setId(newReaction.getId());
                        plateReaction.setWorkflow(newReaction.getWorkflow());
                        FimsSample fimsSample = oldReaction.getFimsSample();
                        if(fimsSample != null) {
                            plateReaction.setFimsSample(newReaction.getFimsSample());
                        }
                        //todo: copy across the actual fields of the extractions
                    }
                    plates.add(plate);
                }
                for(PlateViewer viewer : plates) {
                    viewer.displayInFrame(true, GuiUtilities.getMainFrame());
                }
            }
        };
        ThreadUtilities.invokeNowOrWait(runnable);
        return null;
    }

    public String getDocumentTitle(AnnotatedPluginDocument[] selectedPlates) {
        String title = "Cherry picking of ";
        if(selectedPlates.length > 1) {
            title += selectedPlates.length+" plates";
        }
        else {
            title += selectedPlates[0].getName();
        }
        return title;
    }

    List<Reaction> getNewReactions(List<Reaction> failedReactions, Reaction.Type reactionType) {
        List<Reaction> newReactions = new ArrayList<Reaction>();

        for(Reaction oldReaction : failedReactions) {
            Reaction newReaction = Reaction.getNewReaction(reactionType);
            ReactionUtilities.copyReaction(oldReaction, newReaction);
            newReaction.getOptions().setValue(ReactionOptions.RUN_STATUS, ReactionOptions.NOT_RUN_VALUE);
            newReactions.add(newReaction);
        }

        return newReactions;

    }

    List<Reaction> getFailedReactions(List<PlateDocument> plates, Options options) {
        List<Reaction> reactions = new ArrayList<Reaction>();
        for(PlateDocument doc : plates) {
            Plate plate = doc.getPlate();
            for(Reaction r : plate.getReactions()) {
                if(r.isEmpty()) {
                    continue;
                }
                boolean matches = true;
                Options.MultipleOptions conditions = options.getMultipleOptions("conditions");
                for(Options o : conditions.getValues()) {
                    CherryPickingOptions cOptions = (CherryPickingOptions)o;
                    matches = matches && cOptions.reactionMatches(r);
                }
                if(matches) {
                    reactions.add(r);
                }
            }
        }
        return reactions;
    }
}
