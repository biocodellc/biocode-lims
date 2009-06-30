package com.biomatters.plugins.moorea.reaction;

import com.biomatters.geneious.publicapi.documents.*;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.plugin.DocumentType;
import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import com.biomatters.geneious.publicapi.implementations.sequence.OligoSequenceDocument;
import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.plugins.moorea.*;
import com.biomatters.plugins.moorea.fims.FIMSConnection;
import org.virion.jam.util.SimpleListener;
import org.jdom.Element;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
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

    private Options options;

    public PCRReaction() {
        options = new PCROptions(this.getClass());
    }

    public PCRReaction(ResultSet r, Workflow workflow) throws SQLException{
        this();
        setWorkflow(workflow);
        Options options = init(r);
        options.setValue("workflowId", workflow.getName());
    }

    public PCRReaction(ResultSet r) throws SQLException{
        this();
        init(r);
    }

    private Options init(ResultSet r) throws SQLException {
        setId(r.getInt("pcr.id"));
        setPlate(r.getInt("pcr.plate"));
        Options options = getOptions();
        options.setValue("extractionId", r.getString("pcr.extractionId"));

        options.getOption("runStatus").setValueFromString(r.getString("pcr.progress"));

        Options.ComboBoxOption primerOption = (Options.ComboBoxOption)options.getOption(PCROptions.PRIMER_OPTION_ID);
        String primerName = r.getString("pcr.prName");
        primerOption.setValueFromString(primerName);//todo: what if the user doesn't have the primer?
        options.setValue("prAmount", r.getInt("pcr.prAmount"));
        setCreated(r.getDate("pcr.date"));
        setPosition(r.getInt("pcr.location"));
        options.setValue("cocktail", r.getString("pcr.cocktail"));
        options.setValue("cleanupPerformed", r.getBoolean("pcr.cleanupPerformed"));
        options.setValue("cleanupMethod", r.getString("pcr.cleanupMethod"));

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

    public Options getOptions() {
        return options;
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
                new DocumentField("Primer", "", "primer", String.class, true, false),
                new DocumentField("Reaction Cocktail", "", "cocktail", String.class, true, false)
        });
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
            tissueMapping = MooreaLabBenchService.getInstance().getReactionToTissueIdMapping("extraction", reactions);
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
