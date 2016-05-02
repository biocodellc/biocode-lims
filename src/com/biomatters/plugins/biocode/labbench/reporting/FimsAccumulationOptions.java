package com.biomatters.plugins.biocode.labbench.reporting;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.documents.DocumentField;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;

import com.biomatters.plugins.biocode.labbench.lims.FimsToLims;
import org.jdom.Element;

/**
 * @author Steve
 *          <p/>
 *          Created on 18/08/2011 4:33:47 PM
 */


public class FimsAccumulationOptions extends Options {
    private static final String COUNT_OPTIONS = "countOptions";
    private static final String FIMS_OPTIONS = "fimsOptions";
    private static final String FIMS_FIELD = "fimsField";
    private static final String START_DATE = "startDate";
    private static final String END_DATE = "endDate";
    private static final String TODAY = "today";
    private boolean isLocalLims;

    public FimsAccumulationOptions(Class cl, FimsToLims fimsToLims) {
        super(cl);
        init(fimsToLims);
    }

    public FimsAccumulationOptions(Class cl, String preferenceNameSuffix, FimsToLims fimsToLims) throws SQLException {
        super(cl, preferenceNameSuffix);
        init(fimsToLims);
    }

    public FimsAccumulationOptions(Element element) throws XMLSerializationException {
        super(element);
    }

    private void init(FimsToLims fimsToLims) {
        isLocalLims = fimsToLims.getLimsConnection().isLocal();
        beginAlignHorizontally("", false);
        addDateOption(START_DATE, "Start Date", new Date());
        DateOption endDateOption = addDateOption(END_DATE, "     End Date", new Date());
        BooleanOption todayOption = addBooleanOption(TODAY, "Today", false);
        todayOption.addDependent(endDateOption, false);
        endAlignHorizontally();
        List<DocumentField> documentFields = new ArrayList<DocumentField>();
        documentFields.addAll(fimsToLims.getFimsFields());
        Options countOptions = new ReactionFieldOptions(this.getClass(), fimsToLims, true, true, true, true);
        addChildOptions(COUNT_OPTIONS, "Series", "", countOptions);
        Options fieldOptions = new Options(this.getClass());
        fieldOptions.addLabel("Your chosen series will be counted across each value of this field in the database");
        List<OptionValue> optionValues = ReportGenerator.getOptionValues(documentFields);
        fieldOptions.addComboBoxOption(FIMS_FIELD, "Field", optionValues, optionValues.get(0));
        addChildOptions(FIMS_OPTIONS, "FIMS Field", "", fieldOptions);
    }

    public ReactionFieldOptions getFieldOptions() {
        return (ReactionFieldOptions)getChildOptions().get(COUNT_OPTIONS);
    }

    public String getSql(FimsToLims fimsToLims) {
        ReactionFieldOptions fieldOptions = (ReactionFieldOptions)getChildOptions().get(COUNT_OPTIONS);

        String sql = fieldOptions.getSql(null, Arrays.asList(FimsToLims.FIMS_VALUES_TABLE), true, FimsToLims.FIMS_VALUES_TABLE + "." + fimsToLims.getTissueColumnId() + "=extraction.sampleId");

        sql += " AND "+FimsToLims.FIMS_VALUES_TABLE+"."+getFimsField()+"=?";

        sql += " AND "+getFieldOptions().getTable()+".date <= ?";

        return sql;
    }

    public Date getStartDate() {
        return (Date)getValue(START_DATE);
    }

    public Date getEndDate() {
        if((Boolean)getValue(TODAY)) {
            return new Date();
        }
        return (Date)getValue(END_DATE);
    }

    public String getFimsField() {
        return FimsToLims.getSqlColName(((Options.OptionValue)getChildOptions().get(FIMS_OPTIONS).getValue(FIMS_FIELD)).getName(), isLocalLims);
    }
}
