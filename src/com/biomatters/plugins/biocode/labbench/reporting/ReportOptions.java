package com.biomatters.plugins.biocode.labbench.reporting;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.labbench.lims.FimsToLims;

import java.util.Collections;
import java.util.List;

public class ReportOptions extends Options {

    private final String NAME_OPTION = "name";

    public ReportOptions(Class prefClass, List<Report> reportTypes, String initialName, String initialType, Options initialOptions, FimsToLims fimsToLims) {
        super(prefClass);

        addStringOption(NAME_OPTION, "Report Name", initialName);
        for(Report report : reportTypes) {
            Options options;
            if(fimsToLims.limsHasFimsValues() || !report.requiresFimsValues()) {
                options = report.getOptions();
            }
            else {
                options = new Options(this.getClass());
                options.addLabel("You must copy your FIMS data into the LIMS before using this report.");
            }
            String typeName = report.getTypeName();
            if(typeName.equals(initialType)) {
                options.valuesFromXML(initialOptions.valuesToXML("options"));
            }
            addChildOptions(typeName, report.getTypeName(), report.getTypeDescription(), options);
        }
        Options.Option chooser = addChildOptionsPageChooser("reportType", "ReportType", Collections.EMPTY_LIST, Options.PageChooserType.COMBO_BOX, false);
        if(initialType != null) {
            chooser.setValueFromString(initialType);
        }

    }

    @Override
    public String verifyOptionsAreValid() {
        if(getOption(NAME_OPTION).getValueAsString().isEmpty()) {
            return "Please specify a name for your report";
        }
        return null;
    }
}
