package com.biomatters.plugins.moorea.reaction;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.Condition;
import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.plugins.moorea.MooreaLabBenchService;
import com.biomatters.plugins.moorea.FimsSample;
import com.biomatters.plugins.moorea.ConnectionException;
import com.biomatters.plugins.moorea.fims.FIMSConnection;

import java.util.*;
import java.util.List;
import java.awt.*;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 12/06/2009 5:27:29 PM
 */
public class ExtractionReaction extends Reaction{

    private Options options;

    public Options getOptions() {
        if(options == null) {
            options = new Options(this.getClass());
            options.addStringOption("sampleId", "Tissue Sample Id", "");
            options.addStringOption("extractionId", "Extraction Id", "");
            options.addStringOption("workflowId", "Workflow ID", "");
            options.addStringOption("extractionMethod", "Extraction Method", "");
            options.addStringOption("parentExtraction", "Parent Extraction Id", "", "You may leave this field blank");
            options.addIntegerOption("dilution", "Dilution 1/", 5, 0, Integer.MAX_VALUE);
            Options.IntegerOption volume = options.addIntegerOption("volume", "Extraction Volume", 5, 0, Integer.MAX_VALUE);
            volume.setUnits("ul");
        }
        return options;
    }


    public List<DocumentField> getDefaultDisplayedFields() {
        return Collections.EMPTY_LIST;
    }


    public Color _getBackgroundColor() {
        return Color.white;
    }

    public String areReactionsValid(List<Reaction> reactions) {
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
                Options op = reaction.getOptions();
                String tissueId = op.getValueAsString("sampleId");
                FimsSample currentFimsSample = docMap.get(tissueId);
                if(currentFimsSample == null) {
                    error += "The tissue sample "+tissueId+" does not exist in the database.\n";
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
