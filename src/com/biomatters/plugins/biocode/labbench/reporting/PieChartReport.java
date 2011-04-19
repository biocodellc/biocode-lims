package com.biomatters.plugins.biocode.labbench.reporting;

import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.plugin.Options;
import jebl.util.ProgressListener;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.labels.PieSectionLabelGenerator;
import org.jfree.chart.plot.PiePlot;
import org.jfree.chart.title.TextTitle;
import org.jfree.data.general.DefaultKeyedValuesDataset;
import org.jfree.data.general.PieDataset;
import org.virion.jam.util.SimpleListener;

import javax.swing.*;
import java.awt.*;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.AttributedString;

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

    public ReportChart getChart(Options options, FimsToLims fimsToLims, ProgressListener progress) throws SQLException {
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

        final JFreeChart chart = new JFreeChart(piePlot);
        final ChartPanel chartPanel = new ChartPanel(chart);

        final PieDataset dataset = getDataset((PieChartOptions) options, fimsToLims, progress);
        piePlot.setDataset(dataset);
        piePlot.getLegendItems(); //call this to triger the chart to populate its colors...
        chartPanel.getAnchor();
        return new ReportChart(){
            public JPanel getPanel() {
                return chartPanel;
            }

            @Override
            public Options getOptions() {
                final Options options = new Options(this.getClass());
                options.addDivider("Labels");
                if(chart.getTitle() == null) {
                    chart.setTitle(new TextTitle(""));
                }
                final Options.StringOption titleOption = options.addStringOption("title", "Title: ", chart.getTitle().getText());


                for (int i = 0; i < dataset.getItemCount(); i++) {
                    Comparable key = dataset.getKey(i);
                    options.addDivider("Series "+(i+1));
                    Color existingColor = (Color)piePlot.getSectionPaint(key);
                    if(existingColor == null) {
                        existingColor = Color.blue;
                    }
                    ColorOption colorOption = new ColorOption("seriescolor"+i, "Color: ", existingColor);
                    options.addCustomOption(colorOption);
                }

                options.setHorizontallyCompact(true);

                options.addChangeListener(new SimpleListener(){
                    public void objectChanged() {
                        chart.setTitle(titleOption.getValue());

                        for (int i = 0; i < dataset.getItemCount(); i++) {
                            Comparable key = dataset.getKey(i);
                            ColorOption colorOption = (ColorOption)options.getOption("seriescolor"+i);
                            piePlot.setSectionPaint(key, colorOption.getValue());
                        }

                    }
                });

                return options;
            }
        };
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
