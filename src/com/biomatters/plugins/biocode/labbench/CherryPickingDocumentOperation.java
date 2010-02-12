package com.biomatters.plugins.biocode.labbench;

import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.utilities.GuiUtilities;
import com.biomatters.plugins.biocode.labbench.reaction.*;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import com.biomatters.plugins.biocode.labbench.plates.PlateViewer;
import com.biomatters.plugins.biocode.BiocodePlugin;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.sql.SQLException;

import jebl.util.ProgressListener;
import org.virion.jam.util.SimpleListener;
import org.jdom.Element;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 * User: Steve
 * Date: 22/12/2009
 * Time: 7:04:21 PM
 * To change this template use File | Settings | File Templates.
 */
public class CherryPickingDocumentOperation extends DocumentOperation {

    public GeneiousActionOptions getActionOptions() {
        return GeneiousActionOptions.createSubmenuActionOptions(BiocodePlugin.getSuperBiocodeAction(),new GeneiousActionOptions("Cherry picking", "Create new Reactions from Failed Reactions", BiocodePlugin.getIcons("cherry_24.png")).setInMainToolbar(true));
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
        Reaction.Type reactionType = null;
        for(AnnotatedPluginDocument doc : documents) {
            PlateDocument plateDoc = (PlateDocument)doc.getDocument();
            if(reactionType != null && plateDoc.getPlate().getReactionType() != reactionType) {
                throw new DocumentOperationException("You must select plates of the same type");
            }
            reactionType = plateDoc.getPlate().getReactionType();
        }
        if(reactionType == Reaction.Type.Extraction) {
            throw new DocumentOperationException("You must select either PCR or Cycle Sequencing plates");
        }

        Options options = new Options(this.getClass());

        List<Options.OptionValue> sizeValues = Arrays.asList(
                new Options.OptionValue(Plate.Size.w96.name(), Plate.Size.w96.name()),
                new Options.OptionValue(Plate.Size.w384.name(), Plate.Size.w384.name())
        );

        CherryPickingOptions cherryPickingConditionsOptions = new CherryPickingOptions(this.getClass());

        options.addRadioOption("plateSize", "Plate Size", sizeValues, sizeValues.get(0), Options.Alignment.HORIZONTAL_ALIGN);

        Options.Option<String, ? extends JComponent> label = options.addLabel("Geneious will select reactions that conform to the following:");
        label.setSpanningComponent(true);

        Options.MultipleOptions multipleOptions = options.addMultipleOptions("conditions", cherryPickingConditionsOptions, false);

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

            final Options.ComboBoxOption<Options.OptionValue> conditionOption = addComboBoxOption("condition", "", cherryPickingConditions, cherryPickingConditions[0]);


            final ComboBoxOption valueOption = addComboBoxOption("value", "is", valueValues, valueValues[0]);

            final Options.StringOption valueOption2 = addStringOption("value2", "contains", "");

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

        public Element toXML() {
            return super.toXML();
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
                for(DocumentField field : fimsSample.getTaxonomyAttributes()) {
                    Object value = fimsSample.getFimsAttributeValue(field.getCode());
                    if(value != null && value.toString().toLowerCase().contains(value2.toLowerCase())) {
                        return true;
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

        List<Reaction> failedReactions = getFailedReactions(plateDocuments, options);

        if(failedReactions.size() == 0) {
            throw new DocumentOperationException("The selected plates do not contain any reactions that match your criteria");
        }

        Map<String, ExtractionReaction> newReactions = null;
        try {
            newReactions = BiocodeService.getInstance().getActiveLIMSConnection().getExtractionReactions(failedReactions);
        } catch (SQLException e) {
            throw new DocumentOperationException("Could not fetch existing extractions from database: "+e.getMessage());
        }

        Plate.Size plateSize = Plate.Size.valueOf(options.getValueAsString("plateSize"));

        int numberOfPlatesRequired = (int)Math.ceil(((double)failedReactions.size())/plateSize.numberOfReactions());


        List<PlateViewer> plates = new ArrayList<PlateViewer>();
        for(int i=0; i < numberOfPlatesRequired; i++) {
            PlateViewer plate = new PlateViewer(plateSize, Reaction.Type.Extraction);
            for (int j = 0; j < plate.getPlate().getReactions().length; j++) {
                if(j+i*plateSize.numberOfReactions() > failedReactions.size()-1) {
                    break;
                }
                Reaction plateReaction = plate.getPlate().getReactions()[j];
                Reaction oldReaction = failedReactions.get(i * plateSize.numberOfReactions() + j);
                Reaction newReaction = newReactions.get(oldReaction.getExtractionId());
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
        return null;
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