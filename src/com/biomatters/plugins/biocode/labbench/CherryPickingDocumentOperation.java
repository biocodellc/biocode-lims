package com.biomatters.plugins.biocode.labbench;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.documents.DocumentUtilities;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceDocument;
import com.biomatters.geneious.publicapi.utilities.GuiUtilities;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import com.biomatters.plugins.biocode.labbench.reaction.*;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import com.biomatters.plugins.biocode.labbench.plates.PlateViewer;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import com.biomatters.plugins.biocode.BiocodePlugin;
import com.biomatters.plugins.biocode.BiocodeUtilities;

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
    protected DocumentSelectionSignature PLATE_DOCUMENT_SELECTION_SIGNATURE = new DocumentSelectionSignature(PlateDocument.class, 1, Integer.MAX_VALUE);
    protected DocumentSelectionSignature SEQUENCE_DOCUMENT_SELECTION_SIGNATURE = new DocumentSelectionSignature(SequenceDocument.class, 1, Integer.MAX_VALUE);

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
        return new DocumentSelectionSignature[]{PLATE_DOCUMENT_SELECTION_SIGNATURE, SEQUENCE_DOCUMENT_SELECTION_SIGNATURE};
    }

    @Override
    public Options getOptions(AnnotatedPluginDocument... documents) throws DocumentOperationException {

        boolean sequences = SEQUENCE_DOCUMENT_SELECTION_SIGNATURE.matches(Arrays.asList(documents));
        if(sequences) {
            validateSequenceDocuments(documents);
        }

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

        if(!sequences) {
            Options.Option<String, ? extends JComponent> label = options.addLabel("Geneious will select reactions that conform to the following:");
            label.setSpanningComponent(true);
            options.addMultipleOptions("conditions", cherryPickingConditionsOptions, false);
        }

        return options;
    }

    public Options getGeneralOptions() throws DocumentOperationException {
        return getOptions();
    }

    public static class CherryPickingOptions extends Options {
        private static final String STATE = "reactionState";
        private static final String TAXON = "taxonomy";
        private static final String PRIMER = "primer";

        static final Options.OptionValue[] cherryPickingConditions = new Options.OptionValue[] {
                new Options.OptionValue(STATE, "Reaction State"),
                new Options.OptionValue(TAXON, "Taxonomy"),
                new Options.OptionValue(PRIMER, "Primer"),
                new OptionValue(FailureReason.getOptionName(), "Failure Reason")
        };

        public CherryPickingOptions(Class sourceClass) throws DocumentOperationException {
            super(sourceClass);
            beginAlignHorizontally("", false);

            addComboBoxOption("condition", "", cherryPickingConditions, cherryPickingConditions[0]);

            addComboBoxOption(STATE, "is", ReactionOptions.STATUS_VALUES, ReactionOptions.STATUS_VALUES[0]);
            addStringOption(TAXON, "contains", "");
            addPrimerSelectionOption(PRIMER, "is", DocumentSelectionOption.FolderOrDocuments.EMPTY, false, Collections.<AnnotatedPluginDocument>emptyList());
            FailureReason.addToOptions(this);

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
                    String chosenCondition = BiocodeUtilities.getValueAsString(conditionOption);
                    for(String optionName : new String[] {STATE, TAXON, PRIMER, FailureReason.getOptionName()}) {
                        Option option = getOption(optionName);
                        option.setVisible(chosenCondition.equals(optionName));
                    }
                }
            };
            listener.objectChanged();
            conditionOption.addChangeListener(listener);
        }


        public boolean reactionMatches(Reaction r) throws DocumentOperationException{
            Option conditionOption = getOption("condition");
            String value1 = getValueAsString(STATE);
            String value2 = getValueAsString(TAXON);
            DocumentSelectionOption option3 = (DocumentSelectionOption)getOption(PRIMER);

            if(conditionOption.getValue().equals(cherryPickingConditions[3])) {
                if(r instanceof CycleSequencingReaction) {
                    FailureReason chosenReason = FailureReason.getReasonFromOptions(this);
                    CycleSequencingReaction cycleSequencingReaction = (CycleSequencingReaction) r;
                    List<SequencingResult> results = cycleSequencingReaction.getSequencingResults();
                    if(!results.isEmpty() && chosenReason == results.get(0).getReason()) {
                        return true;
                    }
                }
                return false;
            } else if(conditionOption.getValue().equals(cherryPickingConditions[0])) { //reaction state
                return value1.equals(r.getFieldValue(ReactionOptions.RUN_STATUS));
            } else if(conditionOption.getValue().equals(cherryPickingConditions[1])) { //taxonomy
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
            } else if(conditionOption.getValue().equals(cherryPickingConditions[2])) { //primer
                if(r.getType() == Reaction.Type.PCR) { //two primers
                    List<AnnotatedPluginDocument> primer1 = ((DocumentSelectionOption)r.getOptions().getOption(PCROptions.PRIMER_OPTION_ID)).getDocuments();
                    List<AnnotatedPluginDocument> primer2 = ((DocumentSelectionOption)r.getOptions().getOption(PCROptions.PRIMER_REVERSE_OPTION_ID)).getDocuments();


                    for(AnnotatedPluginDocument doc2 : option3.getDocuments()){
                        for(AnnotatedPluginDocument doc : primer1) {
                            if(primersAreEqual(doc, doc2)) {
                                return true;
                            }
                        }
                        for(AnnotatedPluginDocument doc : primer2) {
                            if(primersAreEqual(doc, doc2)) {
                                return true;
                            }
                        }
                    }
                }
                else if(r.getType() == Reaction.Type.CycleSequencing) {
                    List<AnnotatedPluginDocument> primer = ((DocumentSelectionOption)r.getOptions().getOption(CycleSequencingOptions.PRIMER_OPTION_ID)).getDocuments();

                    for(AnnotatedPluginDocument doc2 : option3.getDocuments()){
                        for(AnnotatedPluginDocument doc : primer) {
                            if(primersAreEqual(doc, doc2)) {
                                return true;
                            }
                        }
                    }
                }
                return false;
            }
            return false;
        }

        private boolean primersAreEqual(AnnotatedPluginDocument doc1, AnnotatedPluginDocument doc2) throws DocumentOperationException {
            SequenceDocument seq1 = (SequenceDocument)doc1.getDocument();
            SequenceDocument seq2 = (SequenceDocument)doc2.getDocument();
            return seq1.getSequenceString().equalsIgnoreCase(seq2.getSequenceString());
        }


    }

    @Override
    public List<AnnotatedPluginDocument> performOperation(AnnotatedPluginDocument[] annotatedDocuments, ProgressListener progressListener, Options options) throws DocumentOperationException {
        if(!BiocodeService.getInstance().isLoggedIn()) {
            throw new DocumentOperationException(BiocodeUtilities.NOT_CONNECTED_ERROR_MESSAGE);
        }


        return cherryPick(annotatedDocuments, options);
    }

    private void validateSequenceDocuments(AnnotatedPluginDocument... documents) throws DocumentOperationException{
        for(AnnotatedPluginDocument doc : documents) {
            if(doc.getFieldValue(LIMSConnection.EXTRACTION_ID_FIELD) == null) {
                throw new DocumentOperationException("At least one of your documents ("+doc.getName()+") appears not to be an sequence document from LIMS.  You can only cherry pick assembly documents returned from a LIMS search");
            }
        }
    }


    private List<AnnotatedPluginDocument> cherryPick(AnnotatedPluginDocument[] annotatedDocuments, Options options) throws DocumentOperationException {
        final List<Reaction> failedReactions;
        if(SEQUENCE_DOCUMENT_SELECTION_SIGNATURE.matches(Arrays.asList(annotatedDocuments))) {
            validateSequenceDocuments(annotatedDocuments);
            failedReactions = getReactionsFromSequences(annotatedDocuments);
        }
        else {
            List<PlateDocument> plateDocuments = new ArrayList<PlateDocument>();
            for(AnnotatedPluginDocument doc : annotatedDocuments) {
                plateDocuments.add((PlateDocument)doc.getDocument());
            }

            failedReactions = getMatchingReactionsFromPlates(plateDocuments, options);
        }

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
                List<String> extractionIds = new ArrayList<String>();
                for (Reaction failedReaction : failedReactions) {
                    extractionIds.add(failedReaction.getExtractionId());
                }
                List<ExtractionReaction> temp = BiocodeService.getInstance().getActiveLIMSConnection().getExtractionsForIds(extractionIds);
                Map<String, Reaction> extractionReactions = new HashMap<String, Reaction>();
                for (ExtractionReaction reaction : temp) {
                    extractionReactions.put(reaction.getExtractionId(), reaction);
                }
                newReactions = new ArrayList<Reaction>();
                for(Reaction r : failedReactions) {
                    Reaction newReaction = extractionReactions.get(r.getExtractionId());
                    if(newReaction == null) {
                        newReaction = new ExtractionReaction();
                        newReaction.setExtractionId(r.getExtractionId());
                    }
                    newReactions.add(r);
                }
            } catch (DatabaseServiceException e) {
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
        return Collections.emptyList();
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

    private List<Reaction> getReactionsFromSequences(AnnotatedPluginDocument[] annotatedDocuments) throws DocumentOperationException {
        List<Reaction> reactions = new ArrayList<Reaction>();
        for(AnnotatedPluginDocument doc : annotatedDocuments) {
            ExtractionReaction reaction = new ExtractionReaction();
            reaction.getOptions().setValue("extractionId", doc.getFieldValue(LIMSConnection.EXTRACTION_ID_FIELD));
            Object extractionBarcode = doc.getFieldValue(LIMSConnection.EXTRACTION_BARCODE_FIELD);
            if(extractionBarcode != null) {
                reaction.getOptions().setValue("extractionBarcode", extractionBarcode);
            }
            reactions.add(reaction);
        }
        try {
            List<String> extractionIds = new ArrayList<String>();
            for (Reaction reaction : reactions) {
                extractionIds.add(reaction.getExtractionId());
            }
            return new ArrayList<Reaction>(BiocodeService.getInstance().getActiveLIMSConnection().getExtractionsForIds(extractionIds));
        } catch (DatabaseServiceException e) {
            e.printStackTrace();
            throw new DocumentOperationException("Error getting extraction reactions from database: "+e.getMessage(), e);
        }
    }

    List<Reaction> getMatchingReactionsFromPlates(List<PlateDocument> plates, Options options) throws DocumentOperationException {
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
