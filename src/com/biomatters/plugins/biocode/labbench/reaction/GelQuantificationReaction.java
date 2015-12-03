package com.biomatters.plugins.biocode.labbench.reaction;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.FimsSample;
import com.biomatters.plugins.biocode.labbench.fims.FIMSConnection;
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
public class GelQuantificationReaction extends Reaction<GelQuantificationReaction> {

    public static final String DB_TABLE_NAME = "gel_quantification";

    public GelQuantificationReaction() {
    }

    public GelQuantificationReaction(ResultSet resultSet) throws SQLException {
        ReactionOptions options = getOptions();
        init(resultSet, options);
    }

    private static final DocumentField ABOVE_THRESHOLD = DocumentField.createDoubleField("Above Threshold", "", "aboveThreshold", true, false);
    public static List<DocumentField> getBulkEditorFields() {
        return Arrays.asList(EXTRACTION_FIELD, ABOVE_THRESHOLD);
    }

    private void init(ResultSet resultSet, ReactionOptions options) throws SQLException {
        setId(resultSet.getInt(DB_TABLE_NAME + ".id"));
        setPlateName(resultSet.getString("plate.name"));
        setPlateId(resultSet.getInt(DB_TABLE_NAME + ".plate"));
        options.setValue(GelQuantificationOptions.TISSUE_ID, resultSet.getString("sampleId"));
        options.setValue(EXTRACTION_FIELD.getCode(), resultSet.getString("extractionId"));
        extractionBarcode = resultSet.getString("extractionBarcode");
        String parent = resultSet.getString("parent");
        options.setValue("parentExtractionId", parent == null ? "" : parent);

        String originalPlate = resultSet.getString(GelQuantificationOptions.ORIGINAL_PLATE);
        if(originalPlate != null) {
            options.setValue(GelQuantificationOptions.ORIGINAL_PLATE, originalPlate);
            int originalPlateSize = resultSet.getInt(GelQuantificationOptions.ORIGINAL_PLATE_SIZE);
            if (originalPlateSize != -1) {
                Plate.Size size = Plate.getSizeEnum(originalPlateSize);
                int originalLocation = resultSet.getInt(GelQuantificationOptions.ORIGINAL_WELL);
                if (originalLocation != -1) {
                    options.setValue(GelQuantificationOptions.ORIGINAL_WELL, Plate.getWell(originalLocation, size).toPaddedString());
                }
            }
        }

        setCreated(resultSet.getTimestamp(DB_TABLE_NAME + ".date"));
        options.getOption("date").setValue(resultSet.getDate(DB_TABLE_NAME + ".date")); //we use getOption() here because the toString() method of java.sql.Date is different to the toString() method of java.util.Date, so setValueFromString() fails in DateOption
        setPosition(resultSet.getInt(DB_TABLE_NAME + ".location"));
        setLocationString(Plate.getWell(getPosition(), Plate.getSizeEnum(resultSet.getInt("plate.size"))).toString());

        options.setValue("technician", resultSet.getString(DB_TABLE_NAME + ".technician"));
        options.setValue("notes", resultSet.getString(DB_TABLE_NAME + ".notes"));

        byte[] imageBytes = resultSet.getBytes(DB_TABLE_NAME + ".gelimage");
        if(imageBytes != null) {
            setGelImage(new GelImage(imageBytes, getLocationString()));
        }

        for (String optionName : Arrays.asList("volume", "gelBuffer", "gelConc", "stain", "stainConc", "stainMethod", "gelLadder", "threshold", "aboveThreshold")) {
            Options.Option option = options.getOption(optionName);
            String columnName = DB_TABLE_NAME + "." + optionName;
            if(option instanceof Options.DoubleOption) {
                option.setValue(resultSet.getDouble(columnName));
            } else if(option instanceof Options.IntegerOption) {
                option.setValue(resultSet.getInt(columnName));
            } else if(option instanceof Options.BooleanOption) {
                option.setValue(resultSet.getBoolean(columnName));
            } else {
                option.setValue(resultSet.getString(columnName));
            }
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
            options = new GelQuantificationOptions();
        }
        return options;
    }

    @Override
    public void setOptions(ReactionOptions op) {
        if(op instanceof GelQuantificationOptions) {
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
        return options.getValueAsString(EXTRACTION_FIELD.getCode());
    }

    @Override
    public void setExtractionId(String s) {
        options.setValue(EXTRACTION_FIELD.getCode(), s);
    }

    @Override
    public Color _getBackgroundColor() {
        return Color.WHITE;
    }

    @Override
    protected String _areReactionsValid(List<GelQuantificationReaction> reactions, JComponent dialogParent, boolean checkingFromPlate) {
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
            String extractionid = option.getValueAsString(EXTRACTION_FIELD.getCode());
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
                    String extractionId = op.getValueAsString(EXTRACTION_FIELD.getCode());
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
        if (errorBuilder.length() > 0) {
            return "<html><b>There were some errors in your data:</b><br>" + errorBuilder.toString() + ".</html>";
        }
        return "";
    }

    public static List<DocumentField> getDefaultDisplayedFields() {
        return Arrays.asList(EXTRACTION_FIELD, ABOVE_THRESHOLD);
    }
}
