package com.biomatters.plugins.biocode.labbench.reporting;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import org.jdom.Element;

import java.util.List;
import java.util.ArrayList;
import java.util.Date;
import java.sql.SQLException;

/**
 * @author Steve
 * @version $Id$
 */
public class AccumulationOptions extends Options {
    private static final String COUNT_OPTIONS = "countOptions";
    private static final String FIMS_OPTIONS = "fimsOptions";
    private static final String FIMS_FIELD = "fimsField";
    private static final String START_DATE = "startDate";
    private static final String END_DATE = "endDate";

    public AccumulationOptions(Class cl, FimsToLims fimsToLims) throws SQLException {
        super(cl);
        init(fimsToLims);
    }

    public AccumulationOptions(Class cl, String preferenceNameSuffix, FimsToLims fimsToLims) throws SQLException {
        super(cl, preferenceNameSuffix);
        init(fimsToLims);
    }

    public AccumulationOptions(Element element) throws XMLSerializationException {
        super(element);
    }

    private void init(FimsToLims fimsToLims) throws SQLException {
        beginAlignHorizontally("", false);
        addDateOption(START_DATE, "Start Date", new Date());
        addDateOption(END_DATE, "     End Date", new Date());
        endAlignHorizontally();
        List<DocumentField> documentFields = new ArrayList<DocumentField>();
        documentFields.addAll(fimsToLims.getFimsFields());
        documentFields.addAll(LIMSConnection.getSearchAttributes());
        addChildOptions(COUNT_OPTIONS, "", "", new ReactionFieldOptions(this.getClass(), fimsToLims, true));
        FimsMultiOptions fimsMultiOptions = new FimsMultiOptions(this.getClass(), fimsToLims);
        addChildOptions(FIMS_OPTIONS, "FIMS fields", "", fimsMultiOptions);
        BooleanOption fimsFieldOption = addBooleanOption(FIMS_FIELD, "Restrict by FIMS field", false);
        fimsFieldOption.addChildOptionsDependent(fimsMultiOptions, true, true);
    }

    public String getSql() {
        ReactionFieldOptions countOptions = (ReactionFieldOptions)getChildOptions().get(COUNT_OPTIONS);
        boolean hasFims = (Boolean)getValue(FIMS_FIELD);
        String sql = countOptions.getSql(hasFims ? "fims_values" : null, hasFims ? "fims_values.tissue_id=extraction.sampleId" : null);
        FimsMultiOptions fimsMultiOptions = (FimsMultiOptions)getChildOptions().get(FIMS_OPTIONS);
        if(hasFims) {
            sql += " AND (";
            List<SingleFieldOptions> fimsOptions = fimsMultiOptions.getFimsOptions();
            String join = fimsMultiOptions.isOr() ? " OR " : " AND ";
            for (int i = 0; i < fimsOptions.size(); i++) {
                SingleFieldOptions option = fimsMultiOptions.getFimsOptions().get(i);
                sql += "fims_values." + FimsToLims.getSqlColName(option.getFieldName()) + " " + option.getComparitor() + " " + "?";
                if(i < fimsOptions.size()-1) {
                    sql += join;
                }
            }
            sql += ")";
        }
        sql += " AND "+countOptions.getTable()+".date < ?";
        return sql;
    }

    public Date getStartDate() {
        return (Date)getValue(START_DATE);
    }

    public Date getEndDate() {
        return (Date)getValue(END_DATE);
    }

    public List<Object> getObjectsForPreparedStatement() {
        List<Object> object = new ArrayList<Object>();
        ReactionFieldOptions countOptions = (ReactionFieldOptions)getChildOptions().get(COUNT_OPTIONS);
        if(countOptions.getValue() != null) {
            object.add(countOptions.getValue());
        }
        FimsMultiOptions fimsMultiOptions = (FimsMultiOptions)getChildOptions().get(FIMS_OPTIONS);
        List<SingleFieldOptions> fimsOptions = fimsMultiOptions.getFimsOptions();
        if((Boolean)getValue(FIMS_FIELD)) {
            for (int i = 0; i < fimsOptions.size(); i++) {
                SingleFieldOptions option = fimsMultiOptions.getFimsOptions().get(i);
                object.add(option.getValue());
            }
        }
        return object;
    }
}
