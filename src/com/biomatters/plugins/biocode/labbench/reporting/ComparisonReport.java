package com.biomatters.plugins.biocode.labbench.reporting;

import com.biomatters.geneious.publicapi.plugin.Options;
import org.jfree.chart.ChartPanel;
import org.virion.jam.util.SimpleListener;

import java.sql.SQLException;

/**
 * @author Steve
 * @version $Id$
 */
public class ComparisonReport implements Report{

    public String getName() {
        return "Field Comparison";
    }

    public Options getOptions(FimsToLims fimsToLims) throws SQLException {

        Options options = new Options(this.getClass(), "Comparison");

        Options.MultipleOptions fimsMultiOptions = null;
        final Options fimsOptions1;
        final Options fimsOptions2;
        if(fimsToLims.limsHasFimsValues()) {
            fimsOptions1 = new Options(this.getClass());
            SingleFieldOptions singleFieldOptions = new SingleFieldOptions(fimsToLims.getFimsFields());
            fimsMultiOptions = fimsOptions1.addMultipleOptions("fields", singleFieldOptions, false);
            options.addChildOptions("fims", "FIMS fields1", "", fimsOptions1);

            fimsOptions2 = new Options(this.getClass());
            fimsOptions2.addChildOptions("limsField", "", "", singleFieldOptions);
            options.addChildOptions("fims2", "FIMS fields2", "", fimsOptions2);
        }
        else {
            fimsOptions1 = null;
            fimsOptions2 = null;
        }

        final Options limsOptions = new Options(this.getClass());
        SingleFieldOptions singleFieldOptions = new SingleFieldOptions(this.getClass());
        final Options.MultipleOptions multiOptions = limsOptions.addMultipleOptions("fields", singleFieldOptions, false);

        options.addChildOptions("lims", "LIMS fields", "", limsOptions);

        final Options limsOptions2 = new Options(this.getClass());
        limsOptions2.addChildOptions("limsField", "", "", singleFieldOptions);
        options.addChildOptions("lims2", "LIMS fields", "", limsOptions2);
        limsOptions2.setVisible(false);

        if(fimsMultiOptions != null) {
            final Options.MultipleOptions fimsMultiOptions1 = fimsMultiOptions;
            SimpleListener listener = new SimpleListener() {
                public void objectChanged() {
                    limsOptions.setVisible(fimsMultiOptions1.getValues().size() < 2);
                    limsOptions2.setVisible(fimsMultiOptions1.getValues().size() > 1);
                }
            };
            fimsMultiOptions.addChangeListener(listener);
            listener.objectChanged();

            SimpleListener listener2 = new SimpleListener() {
                public void objectChanged() {
                    fimsOptions1.setVisible(multiOptions.getValues().size() < 2);
                    fimsOptions2.setVisible(multiOptions.getValues().size() > 1);
                }
            };
            multiOptions.addChangeListener(listener2);
            listener.objectChanged();
        }



        return options;
    }

    public ChartPanel getChart(Options options)  throws SQLException{
        return null;
    }
}
