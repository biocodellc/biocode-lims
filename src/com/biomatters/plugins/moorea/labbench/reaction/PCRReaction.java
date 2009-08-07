package com.biomatters.plugins.moorea.labbench.reaction;

import com.biomatters.geneious.publicapi.documents.*;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.plugins.moorea.labbench.plates.Plate;
import com.biomatters.plugins.moorea.labbench.fims.FIMSConnection;
import com.biomatters.plugins.moorea.labbench.MooreaLabBenchService;
import com.biomatters.plugins.moorea.labbench.Workflow;
import com.biomatters.plugins.moorea.labbench.FimsSample;
import com.biomatters.plugins.moorea.labbench.ConnectionException;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Created by IntelliJ IDEA.
 * User: steve
 * Date: 16/05/2009
 * Time: 10:56:30 AM
 * To change this template use File | Settings | File Templates.
 */
public class PCRReaction extends Reaction {

    private ReactionOptions options;

    public PCRReaction() {
        options = new PCROptions(this.getClass());
    }

    public PCRReaction(ResultSet r) throws SQLException{
        this();
        init(r);
        System.out.println(getWorkflow());
    }

    private Options init(ResultSet r) throws SQLException {
        setId(r.getInt("pcr.id"));
        setPlateId(r.getInt("pcr.plate"));
        ReactionOptions options = getOptions();
        options.setValue("extractionId", r.getString("pcr.extractionId"));

        String s = r.getString("workflow.name");
        if(s != null) {
            options.setValue("workflowId", s);
            setWorkflow(new Workflow(r.getInt("workflow.id"), r.getString("workflow.name"), r.getString("pcr.extractionId")));
            options.setValue("workflowId", getWorkflow().getName());
        }

        options.getOption("runStatus").setValueFromString(r.getString("pcr.progress"));

        PrimerOption primerOption = (PrimerOption)options.getOption(PCROptions.PRIMER_OPTION_ID);
        String primerName = r.getString("pcr.prName");
        String primerSequence = r.getString("pcr.prSequence");
        if(primerSequence.length() > 0) {
            primerOption.setAndAddValue(primerName, primerSequence);
        }
        options.setValue("prAmount", r.getInt("pcr.prAmount"));

        PrimerOption reversePrimerOption = (PrimerOption)options.getOption(PCROptions.PRIMER_REVERSE_OPTION_ID);
        String reversePrimerName = r.getString("pcr.revPrName");
        String reversePrimerSequence = r.getString("pcr.revPrSequence");
        if(reversePrimerSequence.length() > 0) {
            reversePrimerOption.setAndAddValue(reversePrimerName, reversePrimerSequence);
        }
        options.setValue("revPrAmount", r.getInt("pcr.revPrAmount"));

        setCreated(r.getDate("pcr.date"));
        setPosition(r.getInt("pcr.location"));
        options.setValue("cocktail", r.getString("pcr.cocktail"));
        options.setValue("cleanupPerformed", r.getBoolean("pcr.cleanupPerformed"));
        options.setValue("cleanupMethod", r.getString("pcr.cleanupMethod"));
        options.setValue("notes", r.getString("pcr.notes"));

        setPlateName(r.getString("plate.name"));
        setLocationString(Plate.getWellName(getPosition(), Plate.getSizeEnum(r.getInt("plate.size"))));

        int thermocycleId = r.getInt("plate.thermocycle");
        if(thermocycleId >= 0) {
            for(Thermocycle tc : MooreaLabBenchService.getInstance().getPCRThermocycles()) {
                if(tc.getId() == thermocycleId) {
                    setThermocycle(tc);
                    break;
                }
            }
        }
        return options;
    }

    public ReactionOptions getOptions() {
        return options;
    }

    public void setOptions(ReactionOptions op) {
        if(!(op instanceof PCROptions)) {
            throw new IllegalArgumentException("Options must be instances of PCR options");
        }
        this.options = op;
    }

    public Type getType() {
        return Type.PCR;
    }

    public Cocktail getCocktail() {
        String cocktailId = ((Options.OptionValue)getOptions().getOption("cocktail").getValue()).getName();
        for(Cocktail c : new PCRCocktail().getAllCocktailsOfType()) {
            if((""+c.getId()).equals(cocktailId)) {
                return c;
            }
        }
        return null;
    }

    public List<DocumentField> getDefaultDisplayedFields() {
        return Arrays.asList(new DocumentField[] {
                new DocumentField("Tissue ID", "", "tissueId", String.class, true, false),
                new DocumentField("Forward Primer", "", PCROptions.PRIMER_OPTION_ID, String.class, true, false),
                new DocumentField("Reverse Primer", "", PCROptions.PRIMER_REVERSE_OPTION_ID, String.class, true, false),
                new DocumentField("Reaction Cocktail", "", "cocktail", String.class, true, false)
        });
    }

    public String getExtractionId() {
        return getOptions().getValueAsString("extractionId");
    }

    public String areReactionsValid(List<? extends Reaction> reactions) {
        if(!MooreaLabBenchService.getInstance().isLoggedIn()) {
            return "You are not logged in to the database";
        }
        FIMSConnection fimsConnection = MooreaLabBenchService.getInstance().getActiveFIMSConnection();
        DocumentField tissueField = fimsConnection.getTissueSampleDocumentField();

        String error = "";            

        List<Query> queries = new ArrayList<Query>();

        //check the extractions exist in the database...
        Map<String, String> tissueMapping = null;
        try {
            tissueMapping = MooreaLabBenchService.getInstance().getReactionToTissueIdMapping("extraction", reactions);
        } catch (SQLException e) {
            e.printStackTrace();
            return "Could not connect to the LIMS database: "+e.getMessage();
        }
        for(Reaction reaction : reactions) {
            ReactionOptions option = reaction.getOptions();
            String extractionid = option.getValueAsString("extractionId");
            if(reaction.isEmpty() || extractionid == null || extractionid.length() == 0) {
                continue;
            }
            reaction.isError = false;

            String tissue = tissueMapping.get(extractionid);
            if(tissue == null) {
                error += "The extraction '"+option.getOption("extractionId")+"' does not exist in the database!\n";
                reaction.isError = true;
            }
            else {
                Query fieldQuery = Query.Factory.createFieldQuery(tissueField, Condition.EQUAL, tissue);
                if(!queries.contains(fieldQuery)) {
                     queries.add(fieldQuery);
                }
            }
        }


        //add FIMS data to the reaction...
        if(queries.size() > 0) {
            Query orQuery = Query.Factory.createOrQuery(queries.toArray(new Query[queries.size()]), Collections.EMPTY_MAP);
            try {
                List<FimsSample> docList = fimsConnection.getMatchingSamples(orQuery);
                Map<String, FimsSample> docMap = new HashMap<String, FimsSample>();
                for(FimsSample sample : docList) {
                    docMap.put(sample.getFimsAttributeValue(tissueField.getCode()).toString(), sample);
                }
                for(Reaction reaction : reactions) {
                    ReactionOptions op = reaction.getOptions();
                    String extractionId = op.getValueAsString("extractionId");
                    if(extractionId == null || extractionId.length() == 0) {
                        continue;
                    }
                    FimsSample currentFimsSample = docMap.get(tissueMapping.get(extractionId));
                    if(currentFimsSample != null) {
                        reaction.isError = false;
                        reaction.setFimsSample(currentFimsSample);
                    }
                }
            } catch (ConnectionException e) {
                return "Could not query the FIMS database.  "+e.getMessage();
            }
        }

        //check the workflows exist in the database
        Set<String> workflowIdStrings = new HashSet<String>();
        for(Reaction reaction : reactions) {
            Object workflowId = reaction.getFieldValue("workflowId");
            if(!reaction.isEmpty() && workflowId != null && workflowId.toString().length() > 0 && reaction.getType() != Reaction.Type.Extraction) {
                if(reaction.getWorkflow() != null){
                    String extractionId = reaction.getExtractionId();
                    if(!reaction.getWorkflow().getExtractionId().equals(extractionId)) {
                        reaction.setHasError(true);
                        error += "The workflow "+workflowId+" does not match the extraction "+extractionId;
                    }
                    if(reaction.getWorkflow().getName().equals(workflowId)) {
                        continue;
                    }
                }
                else {
                    reaction.setWorkflow(null);
                    workflowIdStrings.add(workflowId.toString());
                }
            }
        }

        if(workflowIdStrings.size() > 0) {
            try {
                Map<String,Workflow> map = MooreaLabBenchService.getInstance().getWorkflows(new ArrayList<String>(workflowIdStrings));

                for(Reaction reaction : reactions) {
                    Object workflowId = reaction.getFieldValue("workflowId");
                    if(workflowId != null && workflowId.toString().length() > 0 && reaction.getWorkflow() == null && reaction.getType() != Reaction.Type.Extraction) {
                        Workflow workflow = map.get(workflowId);
                        String extractionId = reaction.getExtractionId();
                        if(workflow == null) {
                            error += "The workflow "+workflowId+" does not exist in the database.\n";    
                        }
                        else if(!workflow.getExtractionId().equals(extractionId)) {
                            error += "The workflow "+workflowId+" does not match the extraction "+extractionId;       
                        }
                        else {
                            reaction.setWorkflow(workflow);
                        }
                    }
                }
            } catch (SQLException e) {
                return "Could not query the LIMS database.  "+e.getMessage();
            }
        }

        if(error.length() > 0) {
            return "<html><b>There were some errors in your data:</b><br>"+error+"<br>The affected reactions have been highlighted in yellow.";
        }
        return null;
    }

    public Color _getBackgroundColor() {
        String runStatus = options.getValueAsString("runStatus");
        if(runStatus.equals("none"))
                return Color.white;
        else if(runStatus.equals("passed"))
                return Color.green.darker();
        else if(runStatus.equals("failed"))
            return Color.red.darker();
        return Color.white;
    }
}
