package com.biomatters.plugins.biocode.labbench.reporting;

import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import org.jdom.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;

/**
 * @author Steve
 * @version $Id$
 */
public class ComparisonReportOptions extends Options{

    private String X_CHILD_OPTIONS = "xAxis";
    private String FIELD_OPTION = "field";
    private String Y_CHILD_OPTIONS = "yAxis";
    private String Y_MULTIPLE_OPTIONS = "reactionFieldOptions";

    public ComparisonReportOptions(Class cl, FimsToLims fimsToLims) {
        super(cl);
        init(fimsToLims);
    }

    public ComparisonReportOptions(Element element) throws XMLSerializationException {
        super(element);
    }

    private void init(final FimsToLims fimsToLims) {
        Set<DocumentField> documentFields = new LinkedHashSet<DocumentField>();
        documentFields.addAll(fimsToLims.getFimsFields());
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
}
