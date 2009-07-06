package com.biomatters.plugins.moorea.reaction;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.Condition;
import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.plugins.moorea.fims.FIMSConnection;
import com.biomatters.plugins.moorea.MooreaLabBenchService;
import com.biomatters.plugins.moorea.FimsSample;
import com.biomatters.plugins.moorea.ConnectionException;
import com.biomatters.plugins.moorea.Workflow;

import java.util.*;
import java.util.List;
import java.awt.*;
import java.sql.SQLException;
import java.sql.ResultSet;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 24/06/2009 6:02:38 PM
 */
public class CycleSequencingReaction extends Reaction{
    private Options options;

    public CycleSequencingReaction() {
        options = new CycleSequencingOptions(this.getClass());
    }

    public CycleSequencingReaction(ResultSet r, Workflow workflow) throws SQLException{
        this();
        setWorkflow(workflow);
        Options options = init(r);
        options.setValue("workflowId", workflow.getName());
    }

    public CycleSequencingReaction(ResultSet r) throws SQLException {
        this();
        init(r);
//        if(r.getObject("workflow.id") != null) {
//            setWorkflow(new Workflow(r.getInt("workflow.id"), r.getString("workflow.name"), r.getString("extraction.extractionId")));
//        }
    }

    private Options init(ResultSet r) throws SQLException {
        setPlate(r.getInt("cycleSequencing.plate"));
        setPosition(r.getInt("cycleSequencing.location"));
        setCreated(r.getDate("cycleSequencing.date"));
        setId(r.getInt("cycleSequencing.id"));
        Options options = getOptions();
        options.setValue("extractionId", r.getString("cycleSequencing.extractionId"));
        String s = r.getString("workflow.name");
        if(s != null) {
            options.setValue("workflowId", s);
            setWorkflow(new Workflow(r.getInt("workflow.id"), r.getString("workflow.name"), r.getString("cycleSequencing.extractionId")));
        }
        else {
            assert false : "We should be getting a resultset of at least the CycleSequencing table joined to the Workflow table";
        }


        options.getOption("runStatus").setValueFromString(r.getString("cycleSequencing.progress"));

        Options.ComboBoxOption primerOption = (Options.ComboBoxOption)options.getOption(CycleSequencingOptions.PRIMER_OPTION_ID);
        String primerName = r.getString("cycleSequencing.primerName");
        primerOption.setValueFromString(primerName);//todo: what if the user doesn't have the primer?
        options.setValue("prAmount", r.getInt("cycleSequencing.primerAmount"));
        options.setValue("notes", r.getString("cycleSequencing.notes"));
        options.setValue("runStatus", r.getString("cycleSequencing.progress"));
        options.setValue("cocktail", r.getString("cycleSequencing.cocktail"));
        options.setValue("cleanupPerformed", r.getBoolean("cycleSequencing.cleanupPerformed"));
        options.setValue("cleanupMethod", r.getBoolean("cycleSequencing.cleanupMethod"));

        int thermocycleId = r.getInt("plate.thermocycle");
        if(thermocycleId >= 0) {
            for(Thermocycle tc : MooreaLabBenchService.getInstance().getCycleSequencingThermocycles()) {
                if(tc.getId() == thermocycleId) {
                    setThermocycle(tc);
                    break;
                }
            }
        }
        return options;
    }

    public Type getType() {
        return Type.CycleSequencing;
    }

    public Options getOptions() {
        return options;
    }

    public Cocktail getCocktail() {
        String cocktailId = ((Options.OptionValue)getOptions().getOption("cocktail").getValue()).getName();
        for(Cocktail c : new CycleSequencingCocktail().getAllCocktailsOfType()) {
            if((""+c.getId()).equals(cocktailId)) {
                return c;
            }
        }
        return null;
    }

    public List<DocumentField> getDefaultDisplayedFields() {
        return Arrays.asList(new DocumentField[] {
                new DocumentField("Tissue ID", "", "tissueId", String.class, true, false),
                new DocumentField("Primer", "", CycleSequencingOptions.PRIMER_OPTION_ID, String.class, true, false),
                new DocumentField("Reaction Cocktail", "", "cocktail", String.class, true, false)
        });
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

        Map<String, String> tissueMapping = null;
        try {
            tissueMapping = MooreaLabBenchService.getInstance().getReactionToTissueIdMapping("pcr", reactions);
        } catch (SQLException e) {
            return "Could not connect to the LIMS database";
        }

        for(Reaction reaction : reactions) {
            reaction.isError = false;
            Options option = reaction.getOptions();
            if(option.getOption("extractionId").isEnabled()){
                String tissue = tissueMapping.get(option.getValueAsString("extractionId"));
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
        }

        Query orQuery = Query.Factory.createOrQuery(queries.toArray(new Query[queries.size()]), Collections.EMPTY_MAP);

        try {
            List<FimsSample> docList = fimsConnection.getMatchingSamples(orQuery);
            Map<String, FimsSample> docMap = new HashMap<String, FimsSample>();
            for(FimsSample sample : docList) {
                docMap.put(sample.getFimsAttributeValue(tissueField.getCode()).toString(), sample);
            }

            for(Reaction reaction : reactions) {
                Options op = reaction.getOptions();
                String extractionId = op.getValueAsString("extractionId");
                FimsSample currentFimsSample = docMap.get(tissueMapping.get(extractionId));
                if(currentFimsSample == null) {
                    error += "The tissue sample '"+tissueMapping.get(extractionId)+"' does not exist in the database.\n";
                    reaction.isError = true;
                }
                else {
                    reaction.setFimsSample(currentFimsSample);
                }
            }

        } catch (ConnectionException e) {
            return "Could not query the FIMS database.  "+e.getMessage();
        }



        //check the workflows exist in the database
        Set<String> workflowIdStrings = new HashSet<String>();
        for(Reaction reaction : reactions) {
            Object workflowId = reaction.getFieldValue("workflowId");
            if(!reaction.isEmpty() && workflowId != null && workflowId.toString().length() > 0 && reaction.getType() != Reaction.Type.Extraction) {
                if(reaction.getWorkflow() != null && reaction.getWorkflow().getName().equals(workflowId)){
                    continue;
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
                    if(workflowId != null && workflowId.toString().length() > 0) {
                        Workflow workflow = map.get(workflowId);
                        if(workflow == null) {
                            error += "The workflow "+workflowId+" does not exist in the database.\n";
                        }
                        reaction.setWorkflow(workflow);
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
}
