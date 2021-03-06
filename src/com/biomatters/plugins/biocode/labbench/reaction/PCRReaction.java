package com.biomatters.plugins.biocode.labbench.reaction;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.plugin.DocumentSelectionOption;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.FimsSample;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.Workflow;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import com.biomatters.plugins.biocode.labbench.fims.FIMSConnection;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import com.biomatters.plugins.biocode.labbench.plates.GelImage;
import com.biomatters.plugins.biocode.BiocodeUtilities;

import javax.swing.*;
import java.awt.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.List;

/**
 * @author steve
 */
@SuppressWarnings({"ConstantConditions"})
public class PCRReaction extends Reaction<PCRReaction> {

    private ReactionOptions options;

    public PCRReaction() {
    }

    public PCRReaction(ResultSet r) throws SQLException{
        this();
        init(r);
//        System.out.println(getWorkflow());
    }

    private void init(ResultSet r) throws SQLException {
        setId(r.getInt("pcr.id"));
        setPlateId(r.getInt("pcr.plate"));
        extractionBarcode = r.getString("extractionBarcode");
        databaseIdOfExtraction = r.getInt("workflow.extractionId");
        ReactionOptions options = getOptions();
        String extractionId = r.getString("extractionId");
        if(extractionId != null) {
            options.setValue("extractionId", extractionId);
        }

        String s = r.getString("workflow.name");
        if(s != null) {
            options.setValue("workflowId", s);
            setWorkflow(new Workflow(r.getInt("workflow.id"), r.getString("workflow.name"), extractionId, r.getString("workflow.locus"), r.getDate("workflow.date")));
            options.setValue("workflowId", getWorkflow().getName());
        }

        options.getOption(ReactionOptions.RUN_STATUS).setValueFromString(r.getString("pcr.progress"));

        DocumentSelectionOption primerOption = (DocumentSelectionOption)options.getOption(PCROptions.PRIMER_OPTION_ID);
        String primerName = r.getString("pcr.prName");
        String primerSequence = r.getString("pcr.prSequence");
        if(primerSequence != null && primerSequence.length() > 0) {
            primerOption.setValue(new DocumentSelectionOption.FolderOrDocuments(BiocodeUtilities.createPrimerDocument(primerName, primerSequence)));
        }
        //options.setValue("prAmount", r.getInt("pcr.prAmount"));

        DocumentSelectionOption reversePrimerOption = (DocumentSelectionOption)options.getOption(PCROptions.PRIMER_REVERSE_OPTION_ID);
        String reversePrimerName = r.getString("pcr.revPrName");
        String reversePrimerSequence = r.getString("pcr.revPrSequence");
        if(reversePrimerSequence != null && reversePrimerSequence.length() > 0) {
            reversePrimerOption.setValue(new DocumentSelectionOption.FolderOrDocuments(BiocodeUtilities.createPrimerDocument(reversePrimerName, reversePrimerSequence)));
        }
        //options.setValue("revPrAmount", r.getInt("pcr.revPrAmount"));

        setCreated(r.getTimestamp("pcr.date"));
        setPosition(r.getInt("pcr.location"));
        String workflowString = r.getString("workflow.locus");
        if(workflowString != null) {
            options.setValue(LIMSConnection.WORKFLOW_LOCUS_FIELD.getCode(), workflowString);
        }
        options.setValue("cocktail", r.getString("pcr.cocktail"));
        options.setValue("cleanupPerformed", r.getBoolean("pcr.cleanupPerformed"));
        options.setValue("cleanupMethod", r.getString("pcr.cleanupMethod"));
        options.setValue("notes", r.getString("pcr.notes"));
        options.getOption("date").setValue(r.getDate("pcr.date")); //we use getOption() here because the toString() method of java.sql.Date is different to the toString() method of java.util.Date, so setValueFromString() fails in DateOption
        options.setValue("technician", r.getString("pcr.technician"));
        setPlateName(r.getString("plate.name"));
        setLocationString(Plate.getWell(getPosition(), Plate.getSizeEnum(r.getInt("plate.size"))).toString());

        byte[] imageBytes = r.getBytes("pcr.gelimage");
        if(imageBytes != null) {
            setGelImage(new GelImage(imageBytes, getLocationString()));
        }

        int thermocycleId = r.getInt("plate.thermocycle");
        if(thermocycleId >= 0) {
            for(Thermocycle tc : BiocodeService.getInstance().getPCRThermocycles()) {
                if(tc.getId() == thermocycleId) {
                    setThermocycle(tc);
                    break;
                }
            }
        }
    }

    public ReactionOptions _getOptions() {
        if(options == null) {
            options = new PCROptions(this.getClass());
        }
        return options;
    }

    public void setOptions(ReactionOptions op) {
        if(!(op instanceof PCROptions)) {
            throw new IllegalArgumentException("Options must be instances of PCR options");
        }
        this.options = op;
    }

    public String getLocus() {
        return getOptions().getValueAsString(LIMSConnection.WORKFLOW_LOCUS_FIELD.getCode());
    }

    public Type getType() {
        return Type.PCR;
    }

    public Cocktail getCocktail() {
        String cocktailId = ((Options.OptionValue)getOptions().getOption("cocktail").getValue()).getName();
        for(Cocktail c : Cocktail.getAllCocktailsOfType(Reaction.Type.PCR)) {
            if((""+c.getId()).equals(cocktailId)) {
                return c;
            }
        }
        return null;
    }

    public static List<DocumentField> getDefaultDisplayedFields() {
        if(BiocodeService.getInstance().isLoggedIn()) {
            return Arrays.asList(BiocodeService.getInstance().getActiveFIMSConnection().getTissueSampleDocumentField(),
                    new DocumentField("Forward Primer", "", PCROptions.PRIMER_OPTION_ID, String.class, true, false),
                    new DocumentField("Reverse Primer", "", PCROptions.PRIMER_REVERSE_OPTION_ID, String.class, true, false),
                    new DocumentField("Reaction Cocktail", "", "cocktail", String.class, true, false));
        }
        return Arrays.asList(new DocumentField("Forward Primer", "", PCROptions.PRIMER_OPTION_ID, String.class, true, false),
                new DocumentField("Reverse Primer", "", PCROptions.PRIMER_REVERSE_OPTION_ID, String.class, true, false),
                new DocumentField("Reaction Cocktail", "", "cocktail", String.class, true, false));
    }

    public String getExtractionId() {
        return getOptions().getValueAsString("extractionId");
    }

    public void setExtractionId(String s) {
        getOptions().setValue("extractionId", s);
    }

    public String _areReactionsValid(List<PCRReaction> reactions, JComponent dialogParent, boolean checkingFromPlate) {
        if (!BiocodeService.getInstance().isLoggedIn()) {
            return "You are not logged in to the database";
        }

        ReactionUtilities.setReactionErrorStates(reactions, false);

        FIMSConnection fimsConnection = BiocodeService.getInstance().getActiveFIMSConnection();
        DocumentField tissueField = fimsConnection.getTissueSampleDocumentField();

        StringBuilder errorBuilder = new StringBuilder();

        Set<String> samplesToGet = new HashSet<String>();

        //check the extractions exist in the database...
        Map<String, String> tissueMapping;
        try {
            tissueMapping = BiocodeService.getInstance().getReactionToTissueIdMapping("extraction", reactions);
        } catch (DatabaseServiceException e) {
            e.printStackTrace();
            return "Could not connect to the LIMS database: " + e.getMessage();
        }
        for (Reaction reaction : reactions) {
            ReactionOptions option = reaction.getOptions();
            String extractionid = option.getValueAsString("extractionId");
            if (reaction.isEmpty() || extractionid == null || extractionid.length() == 0) {
                continue;
            }

            String tissue = tissueMapping.get(extractionid);
            if (tissue == null) {
                errorBuilder.append("The extraction '").append(option.getOption("extractionId").getValue()).append("' does not exist in the database!<br>");
                reaction.setHasError(true);
            }
            else {
                samplesToGet.add(tissue);
            }
            if (reaction.getLocus() == null || reaction.getLocus().length() == 0 || reaction.getLocus().equalsIgnoreCase("none")) {
                errorBuilder.append("The reaction in well '").append(reaction.getLocationString()).append("' does not have a valid locus.<br>");
                reaction.setHasError(true);
            }
        }


        //add FIMS data to the reaction...
        if (samplesToGet.size() > 0) {
            try {
                List<FimsSample> docList = fimsConnection.retrieveSamplesForTissueIds(samplesToGet);
                Map<String, FimsSample> docMap = new HashMap<String, FimsSample>();
                for (FimsSample sample : docList) {
                    docMap.put(sample.getFimsAttributeValue(tissueField.getCode()).toString(), sample);
                }
                for (Reaction reaction : reactions) {
                    ReactionOptions op = reaction.getOptions();
                    String extractionId = op.getValueAsString("extractionId");
                    if (extractionId == null || extractionId.length() == 0) {
                        continue;
                    }
                    FimsSample currentFimsSample = docMap.get(tissueMapping.get(extractionId));
                    if (currentFimsSample != null) {
                        reaction.setFimsSample(currentFimsSample);
                    }
                }
            } catch (ConnectionException e) {
                return "Could not query the FIMS database.  "+e.getMessage();
            }
        }

        //check the workflows exist in the database
        Set<String> workflowIdStrings = new HashSet<String>();
        for (Reaction reaction : reactions) {
            Object workflowId = reaction.getFieldValue("workflowId");
            if (reaction.getWorkflow() != null && !reaction.getWorkflow().getName().equals(workflowId)) {
                reaction.setWorkflow(null);
            }
            if (!reaction.isEmpty() && reaction.getExtractionId().length() != 0 && (reaction.getLocus() == null || reaction.getLocus().length() == 0)) {
                reaction.setHasError(true);
                errorBuilder.append("The reaction ").append(reaction.getExtractionId()).append(" does not have a locus set.<br>");
            }
            if (!reaction.isEmpty() && workflowId != null && workflowId.toString().length() > 0 && reaction.getType() != Reaction.Type.Extraction) {
                if (reaction.getWorkflow() != null){
                    String extractionId = reaction.getExtractionId();
                    if (reaction.getWorkflow().getExtractionId() != null && !reaction.getWorkflow().getExtractionId().equals(extractionId)) {
                        reaction.setHasError(true);
                        errorBuilder.append("The workflow ").append(workflowId).append(" does not match the extraction ").append(extractionId).append("<br>");
                    }
                    if (!reaction.getWorkflow().getLocus().equals(reaction.getLocus())) {
                        reaction.setHasError(true);
                        errorBuilder.append("The locus of the workflow ").append(workflowId).append(" (").append(reaction.getWorkflow().getLocus()).append(") does not match the reaction's locus (").append(reaction.getLocus()).append(")<br>");
                    }
//                  ssh: commenting out because this appears to have no effect  
//                    if(reaction.getWorkflow().getName().equals(workflowId)) {
//                        continue;
//                    }
                }
                else {
                    reaction.setWorkflow(null);
                    workflowIdStrings.add(workflowId.toString());
                }
            }
        }

        //do the same check for reactions that have a workflow id, but not a workflow object set.
        if (workflowIdStrings.size() > 0) {
            try {
                Map<String,Workflow> map = BiocodeService.getInstance().getWorkflows(new ArrayList<String>(workflowIdStrings));

                for(Reaction reaction : reactions) {
                    Object workflowId = reaction.getFieldValue("workflowId");
                    if (workflowId != null && workflowId.toString().length() > 0 && reaction.getWorkflow() == null && reaction.getType() != Reaction.Type.Extraction) {
                        Workflow workflow = map.get(workflowId.toString());
                        String extractionId = reaction.getExtractionId();
                        if (workflow == null) {
                            errorBuilder.append("The workflow ").append(workflowId).append(" does not exist in the database.<br>");
                        }
                        else if (!workflow.getExtractionId().equals(extractionId)) {
                            errorBuilder.append("The workflow ").append(workflowId).append(" does not match the extraction ").append(extractionId).append("<br>");
                        }
                        else {
                            reaction.setWorkflow(workflow);
                        }
                    }
                }
            } catch (DatabaseServiceException e) {
                return "Could not query the LIMS database: " + e.getMessage();
            }
        }

        if (errorBuilder.length() > 0) {
            return "<html><b>There were some errors in your data:</b><br>" + errorBuilder.toString() + "<br>.</html>";
        }

        return "";
    }

    public Color _getBackgroundColor() {
        String runStatus = options.getValueAsString(ReactionOptions.RUN_STATUS);
        if(runStatus.equals("none"))
                return Color.white;
        else if(runStatus.equals("passed"))
                return Color.green.darker();
        else if(runStatus.equals("failed"))
            return Color.red.darker();
        return Color.white;
    }
}
