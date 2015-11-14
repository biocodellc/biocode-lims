package com.biomatters.plugins.biocode.labbench.reaction;

import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import com.biomatters.plugins.biocode.labbench.plates.GelImage;
import com.biomatters.plugins.biocode.labbench.plates.Plate;

import javax.swing.*;
import java.awt.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.List;

/**
 * @author Matthew Cheung
 *         Created on 12/11/15 12:42 PM
 */
public class GelQualificationReaction extends Reaction {

    public static final String DB_TABLE_NAME = "gel_quantification";

    public GelQualificationReaction() {
    }

    public GelQualificationReaction(ResultSet resultSet) throws SQLException {
        ReactionOptions options = getOptions();
        init(resultSet, options);
    }

    private void init(ResultSet resultSet, ReactionOptions options) throws SQLException {
        setId(resultSet.getInt(DB_TABLE_NAME + ".id"));
        setPlateId(resultSet.getInt(DB_TABLE_NAME + ".plate"));
        options.setValue(GelQualificationOptions.EXTRCTION_ID, resultSet.getString("extractionId"));

        setCreated(resultSet.getTimestamp(DB_TABLE_NAME + ".date"));
        setPosition(resultSet.getInt(DB_TABLE_NAME + ".location"));
        String workflowString = resultSet.getString("workflow.locus");
        if(workflowString != null) {
            options.setValue(LIMSConnection.WORKFLOW_LOCUS_FIELD.getCode(), workflowString);
        }
        options.setValue("notes", resultSet.getString(DB_TABLE_NAME + ".notes"));
        options.getOption("date").setValue(resultSet.getDate(DB_TABLE_NAME + ".date")); //we use getOption() here because the toString() method of java.sql.Date is different to the toString() method of java.util.Date, so setValueFromString() fails in DateOption
        options.setValue("technician", resultSet.getString(DB_TABLE_NAME + ".technician"));
        setPlateName(resultSet.getString("plate.name"));
        setLocationString(Plate.getWell(getPosition(), Plate.getSizeEnum(resultSet.getInt("plate.size"))).toString());

        byte[] imageBytes = resultSet.getBytes(DB_TABLE_NAME + ".gelimage");
        if(imageBytes != null) {
            setGelImage(new GelImage(imageBytes, getLocationString()));
        }

    }

    @Override
    public String getLocus() {
        return null;  // No locus
    }

    @Override
    public Type getType() {
        return Type.GelQuantification;
    }

    private ReactionOptions options;
    @Override
    public ReactionOptions _getOptions() {
        if(options == null) {
            options = new GelQualificationOptions();
        }
        return options;
    }

    @Override
    public void setOptions(ReactionOptions op) {
        if(op instanceof GelQualificationOptions) {
            options = op;
        } else {
            throw new IllegalArgumentException("Options must be instances of GelQualificationOptions");
        }
    }

    @Override
    public Cocktail getCocktail() {
        return null;
    }

    @Override
    public String getExtractionId() {
        return options.getValueAsString(GelQualificationOptions.EXTRCTION_ID);
    }

    @Override
    public void setExtractionId(String s) {
        options.setValue(GelQualificationOptions.EXTRCTION_ID, s);
    }

    @Override
    public Color _getBackgroundColor() {
        return Color.WHITE;
    }

    @Override
    protected String _areReactionsValid(List reactions, JComponent dialogParent, boolean checkingFromPlate) {
        return "";//todo
    }

    public static List<DocumentField> getDefaultDisplayedFields() {
        // todo Add other relevant fields eg. % above threshold
        return Collections.singletonList(new DocumentField("Extraction Id", "", "extractionId", String.class, false, false));
    }
}
