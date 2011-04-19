package com.biomatters.plugins.biocode.labbench.reporting;

import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import org.jdom.Element;

import java.sql.SQLException;
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

    public ComparisonReportOptions(Class cl, FimsToLims fimsToLims) throws SQLException{
        super(cl);
        init(fimsToLims);
    }

    public ComparisonReportOptions(Element element) throws XMLSerializationException {
        super(element);
    }

    private void init(final FimsToLims fimsToLims) throws SQLException {
        Set<DocumentField> documentFields = new LinkedHashSet<DocumentField>();
        documentFields.addAll(fimsToLims.getFimsFields());
        documentFields.addAll(LIMSConnection.getSearchAttributes());
        addChildOptions(Y_CHILD_OPTIONS, "Y Axis (Count)", "", new ReactionFieldOptions(this.getClass(), fimsToLims, true));

        Options fieldOptions = new Options(this.getClass());
        fieldOptions.addLabel("Your Y Axis search will be counted across each value of this field in the database");
        List<OptionValue> optionValues = ReportGenerator.getOptionValues(documentFields);
        fieldOptions.addComboBoxOption(FIELD_OPTION, "Field", optionValues, optionValues.get(0));
        addChildOptions(X_CHILD_OPTIONS, "X Axis", "", fieldOptions);
    }

    public OptionValue getXField() {
        return (Options.OptionValue)getChildOptions().get(X_CHILD_OPTIONS).getValue(FIELD_OPTION);
    }

    public ReactionFieldOptions getYAxisOptions() {
        return (ReactionFieldOptions)getChildOptions().get(Y_CHILD_OPTIONS);
    }
}
