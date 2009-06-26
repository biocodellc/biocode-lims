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
        setPlate(r.getInt("cycleSequencing.plate"));
        Options options = getOptions();
        options.setValue("extractionId", r.getString("extraction.extractionId"));
        options.setValue("workflowId", workflow.getName());

        options.getOption("runStatus").setValueFromString(r.getString("cycleSequencing.progress"));

        Options.ComboBoxOption primerOption = (Options.ComboBoxOption)options.getOption(CycleSequencingOptions.PRIMER_OPTION_ID);
        String primerName = r.getString("cycleSequencing.primerName");
        primerOption.setValueFromString(primerName);//todo: what if the user doesn't have the primer?
        options.setValue("cocktail", r.getString("cycleSequencing.cocktail"));
        options.setValue("cleanupPerformed", r.getBoolean("cycleSequencing.cleanupPerformed"));
        options.setValue("cleanupMethod", r.getBoolean("cycleSequencing.cleanupMethod"));

        int thermocycleId = r.getInt("cycleSequencing.thermocycle");
        if(thermocycleId >= 0) {
            for(Thermocycle tc : MooreaLabBenchService.getInstance().getCycleSequencingThermocycles()) {
                if(tc.getId() == thermocycleId) {
                    setThermocycle(tc);
                    break;
                }
            }
        }
    }

    public Type getType() {
        return Type.CycleSequencing;
    }

    public Options getOptions() {
        return options;
    }

    public List<DocumentField> getDefaultDisplayedFields() {
        return Collections.EMPTY_LIST;
    }

    public Color _getBackgroundColor() {
        return Color.white;
    }

    public String getExtractionId() {
        return getOptions().getValueAsString("extractionId");
    }

    public String areReactionsValid(List<Reaction> reactions) {
        FIMSConnection fimsConnection = MooreaLabBenchService.getInstance().getActiveFIMSConnection();
        DocumentField tissueField = fimsConnection.getTissueSampleDocumentField();

        List<Query> queries = new ArrayList<Query>();

        Map<String, String> tissueMapping = null;
        try {
            tissueMapping = MooreaLabBenchService.getInstance().getReactionToTissueIdMapping("pcr", reactions);
        } catch (SQLException e) {
            return "Could not connect to the LIMS database";
        }

        for(Reaction reaction : reactions) {
            Options option = reaction.getOptions();
            if(option.getOption("extractionId").isEnabled()){
                String tissue = tissueMapping.get(option.getValueAsString("extractionId"));
                if(tissue == null) {
                    return "The extraction '"+option.getOption("extractionId")+"' does not exist in the database!";
                }
                Query fieldQuery = Query.Factory.createFieldQuery(tissueField, Condition.EQUAL, tissue);
                if(!queries.contains(fieldQuery)) {
                     queries.add(fieldQuery);
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
            String error = "";
            for(Reaction reaction : reactions) {
                Options op = reaction.getOptions();
                String extractionId = op.getValueAsString("extractionId");
                FimsSample currentFimsSample = docMap.get(tissueMapping.get(extractionId));
                if(currentFimsSample == null) {
                    error += "The tissue sample '"+tissueMapping.get(extractionId)+"' does not exist in the database.\n";
                    reaction.isError = true;
                }
                reaction.isError = false;
                reaction.fimsSample = currentFimsSample;
            }
            if(error.length() > 0) {
                return "<html><b>There were some errors in your data:</b><br>"+error+"<br>The affected reactions have been highlighted in yellow.";
            }
        } catch (ConnectionException e) {
            return "Could not query the FIMS database.  "+e.getMessage();
        }
        return null;
    }
}
