package com.biomatters.plugins.moorea.reaction;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.Condition;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.plugins.moorea.*;
import com.biomatters.plugins.moorea.plates.Plate;
import com.biomatters.plugins.moorea.fims.FIMSConnection;

import java.util.*;
import java.util.List;
import java.awt.*;
import java.sql.SQLException;
import java.sql.ResultSet;

import org.jdom.Element;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 12/06/2009 5:27:29 PM
 */
public class ExtractionReaction extends Reaction{

    public ExtractionReaction(){}

    public ExtractionReaction(ResultSet r, Workflow workflow) throws SQLException{
        Options options = getOptions();
        setWorkflow(workflow);
        init(r, options);
        options.setValue("workflowId", workflow.getName());
    }

    public ExtractionReaction(ResultSet r) throws SQLException{
        Options options = getOptions();
        init(r, options);
    }

    private void init(ResultSet r, Options options) throws SQLException {
        setId(r.getInt("extraction.id"));
        setCreated(r.getDate("extraction.date"));
        options.setValue("sampleId", r.getString("extraction.sampleId"));
        options.setValue("extractionId", r.getString("extraction.extractionId"));
        options.setValue("extractionMethod", r.getString("extraction.method"));
        options.setValue("parentExtraction", r.getString("extraction.parent"));
        options.setValue("volume", r.getInt("extraction.volume"));
        options.setValue("dilution", r.getInt("extraction.dilution"));
        options.setValue("notes", r.getString("extraction.notes"));
        setPlate(r.getInt("extraction.plate"));
        setPosition(r.getInt("extraction.location"));
    }

    public String getExtractionId() {
        return getOptions().getValueAsString("extractionId");
    }

    private Options options;

    public Options getOptions() {
        if(options == null) {
            options = new Options(this.getClass());
            options.addStringOption("sampleId", "Tissue Sample Id", "");
            options.addStringOption("extractionId", "Extraction Id", "");
            options.addStringOption("extractionMethod", "Extraction Method", "");
            options.addStringOption("parentExtraction", "Parent Extraction Id", "", "You may leave this field blank");
            options.addIntegerOption("dilution", "Dilution 1/", 5, 0, Integer.MAX_VALUE);
            Options.IntegerOption volume = options.addIntegerOption("volume", "Extraction Volume", 5, 0, Integer.MAX_VALUE);
            volume.setUnits("ul");
            TextAreaOption notesOption = new TextAreaOption("notes", "Notes", "");
            options.addCustomOption(notesOption);
        }
        return options;
    }

    public void setOptions(Options op) {
        this.options = op;
    }

    public void setThermocycle(Thermocycle tc){}
    public Thermocycle getThermocycle() {
        return null;  //Extractions don't have thermocycles
    }

    public Cocktail getCocktail() {
        return null; //extractions don't have cocktails
    }
    
    public Type getType() {
        return Type.Extraction;
    }

    public List<DocumentField> getDefaultDisplayedFields() {
        return Arrays.asList(
                new DocumentField("Sample Id", "", "sampleId", String.class, false, false),
                new DocumentField("Extraction Id", "", "extractionId", String.class, false, false)
        );
    }


    public Color _getBackgroundColor() {
        return Color.white;
    }


    public String areReactionsValid(List<? extends Reaction> reactions) {
        if(!MooreaLabBenchService.getInstance().isLoggedIn()) {
            return "You are not logged in to the database";
        }
        FIMSConnection fimsConnection = MooreaLabBenchService.getInstance().getActiveFIMSConnection();
        DocumentField tissueField = fimsConnection.getTissueSampleDocumentField();

        List<Query> queries = new ArrayList<Query>();

        for(Reaction reaction : reactions) {
            Options option = reaction.getOptions();
            if(option.getOption("sampleId").isEnabled()){
                Query fieldQuery = Query.Factory.createFieldQuery(tissueField, Condition.EQUAL, option.getValueAsString("sampleId"));
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
                reaction.isError = false;
                Options op = reaction.getOptions();
                String tissueId = op.getValueAsString("sampleId");
                FimsSample currentFimsSample = docMap.get(tissueId);
                if(currentFimsSample == null) {
                    error += "The tissue sample "+tissueId+" does not exist in the database.\n";
                    reaction.isError = true;
                }
                else {
                    reaction.setFimsSample(currentFimsSample);
                }
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
