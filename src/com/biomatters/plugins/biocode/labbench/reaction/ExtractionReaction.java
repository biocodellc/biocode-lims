package com.biomatters.plugins.biocode.labbench.reaction;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import com.biomatters.plugins.biocode.labbench.fims.FIMSConnection;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.Workflow;
import com.biomatters.plugins.biocode.labbench.FimsSample;
import com.biomatters.plugins.biocode.labbench.ConnectionException;

import javax.swing.*;
import java.util.*;
import java.util.List;
import java.awt.*;
import java.sql.*;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 12/06/2009 5:27:29 PM
 */
public class ExtractionReaction extends Reaction<ExtractionReaction>{

    public ExtractionReaction(){}

    public ExtractionReaction(ResultSet r) throws SQLException{
        ReactionOptions options = getOptions();
        init(r, options);
    }

    private void init(ResultSet r, Options options) throws SQLException {
        setId(r.getInt("extraction.id"));
        setCreated(r.getTimestamp("extraction.date"));
        options.setValue("sampleId", r.getString("extraction.sampleId"));
        options.setValue("extractionId", r.getString("extraction.extractionId"));
        options.setValue("extractionBarcode", r.getString("extraction.extractionBarcode"));
        options.setValue("extractionMethod", r.getString("extraction.method"));
        options.setValue("previousPlate", r.getString("extraction.previousPlate"));
        options.setValue("previousWell", r.getString("extraction.previousWell"));
        options.setValue("parentExtraction", r.getString("extraction.parent"));
        options.setValue("volume", r.getInt("extraction.volume"));
        options.setValue("dilution", r.getInt("extraction.dilution"));
        options.setValue("notes", r.getString("extraction.notes"));
        setPlateId(r.getInt("extraction.plate"));
        setPosition(r.getInt("extraction.location"));
        String workflowName = null;
        try {
            workflowName = r.getString("workflow.name");
        } catch (SQLException e) {
            e.printStackTrace();
            //ignore...
        }

        String plateName = null;
        try {
            plateName = r.getString("plate.name");
        } catch (SQLException e) {
            e.printStackTrace();
            //ignore...
        }
        if(plateName != null) {
            setPlateName(plateName);
            setLocationString(Plate.getWell(getPosition(), Plate.getSizeEnum(r.getInt("plate.size"))).toString());
        }
        
        
        if(workflowName != null) {
            options.setValue("workflowId", workflowName);
            setWorkflow(new Workflow(r.getInt("workflow.id"), r.getString("workflow.name"), r.getString("extraction.extractionId")));
            options.setValue("workflowId", getWorkflow().getName());
        }

        setFimsSample(BiocodeService.getInstance().getActiveFIMSConnection().getFimsSampleFromCache(options.getValueAsString("sampleId"))); //todo: hack
    }

    public String getExtractionId() {
        return getOptions().getValueAsString("extractionId");
    }

    public void setExtractionId(String s) {
        getOptions().setValue("extractionId", s);
    }

    public String getTissueId() {
        return getOptions().getValueAsString("sampleId");
    }

    public void setTissueId(String s) {
        getOptions().setValue("sampleId", s);
    }
    

    private ReactionOptions options;

    public ReactionOptions _getOptions() {
        if(options == null) {
            options = new ExtractionOptions();
        }
        return options;
    }

    public void setOptions(ReactionOptions op) {
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

    protected BackgroundColorer getDefaultBackgroundColorer() {
        return new BackgroundColorer(null, Collections.EMPTY_MAP);
    }

    public List<DocumentField> getDefaultDisplayedFields() {
        return Arrays.asList(
                BiocodeService.getInstance().getActiveFIMSConnection().getTissueSampleDocumentField(),
                new DocumentField("Extraction Id", "", "extractionId", String.class, false, false)
        );
    }


    public Color _getBackgroundColor() {
        return Color.white;
    }


    public String areReactionsValid(List<ExtractionReaction> reactions, JComponent dialogParent, boolean showDialogs) {
        if(!BiocodeService.getInstance().isLoggedIn()) {
            return "You are not logged in to the database";
        }
        FIMSConnection fimsConnection = BiocodeService.getInstance().getActiveFIMSConnection();
        DocumentField tissueField = fimsConnection.getTissueSampleDocumentField();

        Set<String> samplesToGet = new HashSet<String>();

        for(Reaction reaction : reactions) {
            ReactionOptions option = reaction.getOptions();
            String tissueId = option.getValueAsString("sampleId");

            if(reaction.isEmpty() || tissueId == null || tissueId.length() == 0) {
                continue;
            }
            samplesToGet.add(tissueId);
        }

        if(samplesToGet.size() == 0) {
            return null;
        }

        String error = "";

        try {
            List<FimsSample> docList = fimsConnection.getMatchingSamples(samplesToGet);
            Map<String, FimsSample> docMap = new HashMap<String, FimsSample>();
            for(FimsSample sample : docList) {
                docMap.put(sample.getFimsAttributeValue(tissueField.getCode()).toString(), sample);
            }
            for(Reaction reaction : reactions) {
                ReactionOptions op = reaction.getOptions();
                String tissueId = op.getValueAsString("sampleId");
                reaction.isError = false;

                if(reaction.isEmpty() || tissueId == null || tissueId.length() == 0) {
                    continue;
                }
                FimsSample currentFimsSample = docMap.get(tissueId);
                if(currentFimsSample == null) {
                    //we used to report an error here, but since we want to allow extractions to be created 'headless' and then linked to tissue samples as they are entered into the FIMS later, we're just setting the FIMS sample and not complaining
//                    error += "The tissue sample "+tissueId+" does not exist in the database.\n";
//                    reaction.isError = true;
                }
                else {
                    reaction.setFimsSample(currentFimsSample);
                }
            }

        } catch (ConnectionException e) {
            return "Could not query the FIMS database.  "+e.getMessage();
        }

        try {
            //check that the extraction id's don't already exist in the database...
            List<String> reactionOrs = new ArrayList<String>();
            for(Reaction r : reactions) {
                if(r.getId() < 0) {
                    reactionOrs.add("extraction.extractionId=?");
                }
            }
            if(reactionOrs.size() > 0) {
                String sql = "SELECT * FROM extraction, workflow, plate WHERE workflow.extractionId=extraction.id AND plate.id = extraction.plate AND ("+StringUtilities.join(" OR ", reactionOrs)+")";
                Connection connection = BiocodeService.getInstance().getActiveLIMSConnection().getConnection();

                PreparedStatement statement = connection.prepareStatement(sql);
                int count=1;
                for(Reaction r : reactions) {
                    if(r.getId() < 0) {
                        statement.setString(count, r.getExtractionId());
                        count++;
                    }
                }
                ResultSet results = statement.executeQuery();
                List<ExtractionReaction> extractionsThatExist = new ArrayList<ExtractionReaction>();
                while(results.next()) {
                    extractionsThatExist.add(new ExtractionReaction(results));
                    //String extractionId = results.getString("extraction.extractionId");
                }
                statement.close();
                if(extractionsThatExist.size() > 0) {
                    //ask the user if they want to move the extractions that are already attached to a plate.
                    StringBuilder moveMessage = new StringBuilder("The following extractions already exist in the database.\nDo you want to move them to this plate?\n\n");
                    for(ExtractionReaction reaction : extractionsThatExist) {
                        moveMessage.append(reaction.getExtractionId()+"\n");
                    }
                    if(!showDialogs || Dialogs.showYesNoDialog(moveMessage.toString(), "Move existing extractions", dialogParent, Dialogs.DialogIcon.QUESTION)) {
                        for (int i = 0; i < reactions.size(); i++) {
                            Reaction r = reactions.get(i);
                            for (ExtractionReaction r2 : extractionsThatExist) {
                                if(r.getExtractionId().equals(r2.getExtractionId())) {
                                    ReactionUtilities.copyReaction(r2, r);
                                    r.setPlateId(r.getPlateId());
                                    r.setPosition(r.getPosition());
                                    r.setId(r2.getId());
                                    r.setExtractionId(r2.getExtractionId());
                                    r.setThermocycle(r.getThermocycle());
                                    r.setLocationString(r.getLocationString());
                                }
                            }
                        }
                    }
                    else {
                        for(Reaction r : reactions) {
                            for(Reaction r2 : extractionsThatExist) {
                                if(r.getExtractionId().equals(r2.getExtractionId())) {
                                    r.isError = true;
                                    error += "The extraction "+r.getExtractionId()+" already exists in the database.\n";
                                }
                            }
                        }
                    }
                }

            }
        } catch (SQLException e) {
            return "Could not qurey the LIMS database: "+e.getMessage();
        }

        //give the user the option to not save reactions with no extraction id
        List<Reaction> reactionsWithNoIds = new ArrayList<Reaction>();
        for(Reaction r : reactions) {
            if(r.getExtractionId().length() == 0 && !r.isEmpty()) {
                reactionsWithNoIds.add(r);
            }
        }
        if(reactionsWithNoIds.size() > 0 && reactionsWithNoIds.size() < reactions.size() && Dialogs.showYesNoDialog("Some extractions in your plate have no id's, but the reactions are not empty.  Would you like to make the extractions empty?", "Extractions with no ids", dialogParent, Dialogs.DialogIcon.QUESTION)) {
            for(Reaction r : reactionsWithNoIds) {
                r.getOptions().restoreDefaults();
            }
        }


        Set<String> namesSet = new HashSet<String>();
        for(Reaction r : reactions) {
            if(!r.isEmpty()) {
                if(r.getExtractionId().length() == 0) {
                    error += "Extraction reactions cannot have empty id's.\n";
                    r.isError = true;
                }
                else if(!namesSet.add(r.getExtractionId())) {
                    error += "You cannot add an extraction with the name '"+r.getExtractionId()+"' more than once.\n";
                    r.isError = true;
                }
            }
        }


        if(error.length() > 0) {
            return "<html><b>There were some errors in your data:</b><br>"+error+"<br>The affected reactions have been highlighted in yellow.";
        }
        return null;
    }
}
