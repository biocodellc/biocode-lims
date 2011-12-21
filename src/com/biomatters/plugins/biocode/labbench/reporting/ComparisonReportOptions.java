package com.biomatters.plugins.biocode.labbench.reporting;

import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import org.jdom.Element;

import java.util.*;

/**
 * @author Steve
 * @version $Id$
 */
public class ComparisonReportOptions extends Options{

    private static final String FIMS_OPTIONS = "fimsOptions";
    private static final String FIMS_FIELD = "fimsField";
    private String X_CHILD_OPTIONS = "xAxis";
    private String FIELD_OPTION = "field";
    private String Y_CHILD_OPTIONS = "yAxis";
    private String Y_MULTIPLE_OPTIONS = "reactionFieldOptions";
    private boolean isLocalLims;

    public ComparisonReportOptions(Class cl, FimsToLims fimsToLims) {
        super(cl);
        init(fimsToLims);
    }

    public ComparisonReportOptions(Element element) throws XMLSerializationException {
        super(element);
    }

    private void init(final FimsToLims fimsToLims) {
        isLocalLims = fimsToLims.getLimsConnection().isLocal();
        Set<DocumentField> documentFields = new LinkedHashSet<DocumentField>();
        if(fimsToLims.limsHasFimsValues()) {
            documentFields.addAll(fimsToLims.getFimsFields());
        }
        List<DocumentField> limsSearchFields = new ArrayList<DocumentField>(LIMSConnection.getSearchAttributes());
        limsSearchFields.remove(LIMSConnection.PLATE_TYPE_FIELD);
        limsSearchFields.remove(LIMSConnection.PLATE_DATE_FIELD);
        limsSearchFields.remove(LIMSConnection.PLATE_NAME_FIELD);
        documentFields.addAll(limsSearchFields);

        Options yAxisOptions = new Options(this.getClass());

        yAxisOptions.addMultipleOptions(Y_MULTIPLE_OPTIONS, new ReactionFieldOptions(this.getClass(), fimsToLims, true, true, true), false);

        addChildOptions(Y_CHILD_OPTIONS, "Y Axis (Count)", "", yAxisOptions);

        Options fieldOptions = new Options(this.getClass());
        fieldOptions.addLabel("Your Y Axis search will be counted across each value of this field in the database");
        List<OptionValue> optionValues = ReportGenerator.getOptionValues(documentFields);
        fieldOptions.addComboBoxOption(FIELD_OPTION, "Field", optionValues, optionValues.get(0));
        addChildOptions(X_CHILD_OPTIONS, "X Axis", "", fieldOptions);

        FimsMultiOptions fimsMultiOptions = new FimsMultiOptions(this.getClass(), fimsToLims);
        addChildOptions(FIMS_OPTIONS, "FIMS fields", "", fimsMultiOptions);
        BooleanOption fimsFieldOption = addBooleanOption(FIMS_FIELD, "Restrict by FIMS field", false);
        fimsFieldOption.setDisabledValue(false);
        fimsFieldOption.addChildOptionsDependent(fimsMultiOptions, true, true);
        if(!fimsToLims.limsHasFimsValues()) {
            fimsFieldOption.setEnabled(false);
        }
    }

    public OptionValue getXField() {
        return (Options.OptionValue)getChildOptions().get(X_CHILD_OPTIONS).getValue(FIELD_OPTION);
    }

    public List<ReactionFieldOptions> getYAxisOptions() {
        List<Options> optionsList = getChildOptions().get(Y_CHILD_OPTIONS).getMultipleOptions(Y_MULTIPLE_OPTIONS).getValues();
        List<ReactionFieldOptions> fieldOptions = new ArrayList<ReactionFieldOptions>();
        for(Options o : optionsList) {
            fieldOptions.add((ReactionFieldOptions)o);
        }
        return fieldOptions;
    }

    public List<String> getFimsFieldsForSql() {
        if(!(Boolean)getValue(FIMS_FIELD)) {
            return Collections.EMPTY_LIST;
        }
        FimsMultiOptions multiOptions = (FimsMultiOptions)getChildOptions().get(FIMS_OPTIONS);
        List<String> results = new ArrayList<String>();
        for(SingleFieldOptions options : multiOptions.getFimsOptions()) {
            results.add(FimsToLims.getSqlColName(options.getFieldName(), isLocalLims)+" LIKE ?");
        }
        return results;
    }

    public List<String> getFimsValues() {
        if(!(Boolean)getValue(FIMS_FIELD)) {
            return Collections.EMPTY_LIST;
        }
        FimsMultiOptions multiOptions = (FimsMultiOptions)getChildOptions().get(FIMS_OPTIONS);
        List<String> results = new ArrayList<String>();
        for(SingleFieldOptions options : multiOptions.getFimsOptions()) {
            results.add(""+options.getValue());
        }
        return results;
    }

    public boolean isFimsRestricted() {
        return (Boolean)getValue(FIMS_FIELD);
    }

    public String getFimsComparator() {
        FimsMultiOptions multiOptions = (FimsMultiOptions)getChildOptions().get(FIMS_OPTIONS);
        return multiOptions.isOr() ? " OR " : " AND ";
    }
}
