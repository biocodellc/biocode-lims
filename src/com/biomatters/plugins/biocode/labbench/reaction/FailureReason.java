package com.biomatters.plugins.biocode.labbench.reaction;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Matthew Cheung
 * @version $Id$
 *          <p/>
 *          Created on 6/09/13 5:12 PM
 */
@XmlRootElement
public class FailureReason {
    @XmlElement private int id;
    @XmlElement private String name;
    @XmlElement private String description;

    private FailureReason(){
        // For JAXB
    }

    private FailureReason(int id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    private static FailureReason fromResultSetRow(ResultSet resultSet) throws SQLException {
        return new FailureReason(resultSet.getInt("id"), resultSet.getString("name"), resultSet.getString("description"));
    }

    public static List<FailureReason> getPossibleListFromResultSet(ResultSet resultSet) throws SQLException {
        List<FailureReason> results = new ArrayList<FailureReason>();
        while(resultSet.next()) {
            results.add(fromResultSetRow(resultSet));
        }
        return results;
    }

    private static final String OPTION_NAME = "reason";

    public static String getOptionName() {
        return OPTION_NAME;
    }

    private static final String NO_REASON = "Unspecified";
    public static Options.ComboBoxOption<Options.OptionValue> addToOptions(Options toAddTo) throws DocumentOperationException, DatabaseServiceException {
        List<Options.OptionValue> possibleValues = new ArrayList<Options.OptionValue>();
        Options.OptionValue defaultValue = new Options.OptionValue(NO_REASON, NO_REASON);
        possibleValues.add(defaultValue);
        LIMSConnection limsConnection = BiocodeService.getInstance().getActiveLIMSConnection();
        if(limsConnection != null) {
            List<FailureReason> possibleFailureReasons = limsConnection.getPossibleFailureReasons();
            for (FailureReason reason : possibleFailureReasons) {
                possibleValues.add(new Options.OptionValue("" + reason.getId(), reason.getName(), reason.getDescription()));
            }
        }

        Options.ComboBoxOption<Options.OptionValue> option = toAddTo.addComboBoxOption(OPTION_NAME, "Reason", possibleValues, defaultValue);
        if(possibleValues.size() == 1) {
            option.setEnabled(false);
            if(limsConnection == null) {
                option.setDescription("You must log into the Biocode service to view potential failure reasons.");
            } else {
                option.setDescription("Possible values for this option need to be set up in the database. " +
                        "An admin can add values directly into the failure_reason table.");
            }
        }
        return option;
    }

    public static FailureReason getReasonFromOptions(Options options) {
        Options.Option option = options.getOption(OPTION_NAME);
        if(option == null) {
            return null;
        }
        return getReasonFromOption(option);
    }

    public static FailureReason getReasonFromOption(Options.Option option) {
        if(!option.getName().equals(OPTION_NAME)) {
            throw new IllegalArgumentException("This method should only be called using an Option returned from calling FailureReason.addToOptions()");
        }
        String reasonId = BiocodeUtilities.getValueAsString(option);
        if(NO_REASON.equals(reasonId)) {
            return null;
        }

        return getReasonFromIdString(reasonId);
    }

    public static FailureReason getReasonFromIdString(String reasonId) {
        if(reasonId == null) {
            return null;
        }
        try {
            for (FailureReason possible : BiocodeService.getInstance().getActiveLIMSConnection().getPossibleFailureReasons()) {
                if (reasonId.equals("" + possible.getId())) {
                    return possible;
                }
            }
        } catch (DatabaseServiceException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void setFailureReasonOnOptions(Options options, int reasonId) {
        Options.Option option = options.getOption(OPTION_NAME);
        if(option == null) {
            return;
        }
        option.setValueFromString(""+reasonId);
    }
}
