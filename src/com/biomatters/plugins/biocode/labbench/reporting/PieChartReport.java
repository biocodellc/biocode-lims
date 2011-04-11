package com.biomatters.plugins.biocode.labbench.reporting;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.documents.DocumentField;

import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.text.AttributedString;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.labels.PieSectionLabelGenerator;
import org.jfree.chart.plot.PiePlot;
import org.jfree.data.general.PieDataset;
import org.jfree.data.general.DefaultKeyedValuesDataset;
import jebl.util.ProgressListener;

/**
 * @author Steve
 * @version $Id$
 */
public class PieChartReport implements Report{

    public String getName() {
        return "Pie Chart";
    }

    public Options getOptions(FimsToLims fimsToLims) throws SQLException {
        return new PieChartOptions(this.getClass(), fimsToLims);
    }

    public ChartPanel getChart(Options options, FimsToLims fimsToLims, ProgressListener progress) throws SQLException {
        final PiePlot piePlot = new PiePlot();
        piePlot.setLabelGenerator(new PieSectionLabelGenerator() {
            public String generateSectionLabel(PieDataset pieDataset, Comparable comparable) {
                Number value = pieDataset.getValue(comparable);
                if(value.intValue() == 0) {
                    return null;
                }
                return comparable.toString()+" ("+value+")";
            }

            public AttributedString generateAttributedSectionLabel(PieDataset pieDataset, Comparable comparable) {
                return new AttributedString("arse2");
            }
        });

        final ChartPanel chartPanel = new ChartPanel(new JFreeChart(piePlot));

        piePlot.setDataset(getDataset((PieChartOptions)options, fimsToLims, progress));
        chartPanel.getAnchor();
        return chartPanel;
    }

    public PieDataset getDataset(PieChartOptions options, FimsToLims fimsToLims, ProgressListener progress) throws SQLException{
        DefaultKeyedValuesDataset dataset = new DefaultKeyedValuesDataset();
        ReactionFieldOptions reactionFields = (ReactionFieldOptions)options.getChildOptions().get(PieChartOptions.REACTION_FIELDS);
        String fieldName = reactionFields.getValueAsString(ReactionFieldOptions.FIELDS);
        String reactionType = ((Options.OptionValue)reactionFields.getValue(ReactionFieldOptions.REACTION_TYPE)).getName();
        String fimsSql = options.getFimsSql();
        String limsSql = options.getLimsSql();
        DocumentField enumeratedField = ReportGenerator.getField(reactionType, fieldName);
        if(!enumeratedField.isEnumeratedField()) {
            throw new RuntimeException("The document field "+enumeratedField.getName()+" is not enumerated!");
        }

        String sql = limsSql;
        if(fimsSql != null) {
            sql += "AND extraction.sampleId=f."+fimsToLims.getTissueColumnId()+" AND "+fimsSql;
        }
        System.out.println(sql);
        PreparedStatement statement = fimsToLims.getLimsConnection().getConnection().prepareStatement(sql);
        Object[] enumerationObjects = getEnumerationObjects(enumeratedField);
        for (int i1 = 0; i1 < enumerationObjects.length; i1++) {
            progress.setProgress(((double)i1)/enumerationObjects.length);
            if(progress.isCanceled()) {
                return null;
            }
            Object value = enumerationObjects[i1];
            statement.setObject(1, value);
            if (fimsSql != null) {
                for (int i = 0; i < options.getFimsValues().size(); i++) {
                    Object o = options.getFimsValues().get(i);
                    statement.setObject(2 + i, o);
                }
            }

            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                dataset.setValue(value.toString(), resultSet.getInt(1));
            } else {
                throw new RuntimeException("No result for " + value);
            }

        }

        return dataset;
    }

    private static Object[] getEnumerationObjects(DocumentField field) {
        if(isBooleanField(field)) {
            return new Object[] {true, false};
        }
        else {
            return field.getEnumerationValues();
        }
    }

    private static boolean isBooleanField(DocumentField field) {
        return field.getEnumerationValues().length == 2 && ((field.getEnumerationValues()[0].toLowerCase().equals("yes") && field.getEnumerationValues()[1].toLowerCase().equals("no")) ||
        (field.getEnumerationValues()[0].toLowerCase().equals("true") && field.getEnumerationValues()[1].toLowerCase().equals("false")));
    }


}
