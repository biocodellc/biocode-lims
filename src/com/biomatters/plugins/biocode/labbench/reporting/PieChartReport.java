package com.biomatters.plugins.biocode.labbench.reporting;

import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
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
import org.jdom.Element;

import javax.swing.*;
import java.awt.*;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.AttributedString;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.*;
import java.util.List;

/**
 * @author Steve
 * @version $Id$
 */
public class PieChartReport extends Report{

    public String getTypeName() {
        return "Pie Chart";
    }

    public PieChartReport(FimsToLims fimsToLims) {
        super(fimsToLims);
    }

    public PieChartReport(Element e) throws XMLSerializationException {
        super(e);
    }

    public String getTypeDescription() {
        return "The relative amounts of all values of a single field";
    }

    public Options createOptions(FimsToLims fimsToLims) {
        return new PieChartOptions(this.getClass(), fimsToLims);
    }

    public ReportChart getChart(Options options, FimsToLims fimsToLims, ProgressListener progress) throws SQLException {
        final PiePlot piePlot = new PiePlot();
        final AtomicBoolean showNums = new AtomicBoolean(true);
        final AtomicBoolean showLabels = new AtomicBoolean(true);
        final AtomicBoolean showNames = new AtomicBoolean(true);
        piePlot.setLabelGenerator(new PieSectionLabelGenerator() {
            public String generateSectionLabel(PieDataset pieDataset, Comparable comparable) {
                int value = pieDataset.getValue(comparable).intValue();
                if(value == 0) {
                    return null;
                }
                String label = null;
                if(showNames.get()) {
                    label = comparable.toString() + (showNums.get() ? " (" + value + ")" : "");
                }
                else if(showNums.get()) {
                    label = ""+value;
                }
                return label;
            }

            public AttributedString generateAttributedSectionLabel(PieDataset pieDataset, Comparable comparable) {
                return new AttributedString("arse2");
            }
        });

        final JFreeChart chart = new JFreeChart(piePlot);
        chart.setBackgroundPaint(Color.white);
        final ChartPanel chartPanel = new ChartPanel(chart, false);
        chartPanel.setMaximumDrawWidth(Integer.MAX_VALUE);
        chartPanel.setMaximumDrawHeight(Integer.MAX_VALUE);

        final PieDataset dataset = getDataset((PieChartOptions) options, fimsToLims, progress);
        if(dataset == null) {
            return null;
        }
        piePlot.setDataset(dataset);
        piePlot.getLegendItems(); //call this to triger the chart to populate its colors...
        chartPanel.getAnchor();

        final double origShadowXOffset = piePlot.getShadowXOffset();
        final double origShadowYOffset = piePlot.getShadowYOffset();

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
                final Options.BooleanOption showLabelsOption = options.addBooleanOption("showLabels", "Show Labels", true);



                Options labelOptions = new Options(this.getClass());
                labelOptions.beginAlignHorizontally("", false);
                final Options.BooleanOption showNamesOption = labelOptions.addBooleanOption("showNames", "Names", true);
                final Options.BooleanOption showNumbersOption = labelOptions.addBooleanOption("showNumbers", "Values", true);
                showNamesOption.setDisabledValue(false);
                showNumbersOption.setDisabledValue(false);
                labelOptions.endAlignHorizontally();
                options.addChildOptions("Label", "", "", labelOptions);

                showLabelsOption.addChildOptionsDependent(labelOptions, true, true);

                final Options.BooleanOption shadowOption = options.addBooleanOption("shadow", "Shadow", true);


                for (int i = 0; i < dataset.getItemCount(); i++) {
                    Comparable key = dataset.getKey(i);
                    options.addDivider("Series "+(i+1));
                    options.addStringOption("seriestitle"+i, "Title: ", piePlot.getLegendItems().get(i).getLabel());
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

                        DefaultKeyedValuesDataset newDataset = new DefaultKeyedValuesDataset();
                        PieDataset oldDataset = piePlot.getDataset();
                        showLabels.set(showLabelsOption.getValue());
                        showNums.set(showNumbersOption.getValue());
                        showNames.set(showNamesOption.getValue());

                        piePlot.setShadowXOffset(shadowOption.getValue() ? origShadowXOffset : 0);
                        piePlot.setShadowYOffset(shadowOption.getValue() ? origShadowYOffset : 0);

                        piePlot.clearSectionPaints(false);
                        for (int i = 0; i < oldDataset.getItemCount(); i++) {
                            Comparable oldkey = oldDataset.getKey(i);
                            Comparable key = options.getValueAsString("seriestitle" + i);
                            ColorOption colorOption = (ColorOption)options.getOption("seriescolor"+i);
                            newDataset.setValue(key, piePlot.getDataset().getValue(oldkey));
                            final Color seriesColor = colorOption.getValue();
                            piePlot.setSectionPaint(key, seriesColor);
                        }
                        piePlot.setDataset(newDataset);
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
        String fimsSql = options.getExtraSql();
        String limsSql = options.getLimsSql();
        DocumentField enumeratedField = ReportGenerator.getField(reactionType, fieldName);

        String sql = limsSql;
        if(fimsSql != null) {
            sql += "AND extraction.sampleId=f."+fimsToLims.getTissueColumnId()+" AND "+fimsSql;
        }
        System.out.println(sql);
        PreparedStatement statement = fimsToLims.getLimsConnection().getConnection().prepareStatement(sql);
        progress.setMessage("Getting all possible values for your selected field");
        progress.setIndeterminateProgress();
        List<String> loci;
        if(reactionFields.getLocus() != null) {
            loci = Arrays.asList(reactionFields.getLocus());
        }
        else {
            loci = null;
        }
        java.util.List<String> enumerationObjects = ReportGenerator.getDistinctValues(fimsToLims, ReportGenerator.getTableFieldName(reactionType.toLowerCase(), enumeratedField.getCode()), reactionType.toLowerCase(), loci, progress);
        if(enumerationObjects == null) {
            return null;
        }
        progress.setMessage("Calculating...");

        for (int i1 = 0; i1 < enumerationObjects.size(); i1++) {
            progress.setProgress(((double)i1)/enumerationObjects.size());
            if(progress.isCanceled()) {
                return null;
            }
            Object value = enumerationObjects.get(i1);
            statement.setObject(1, value);
            if (fimsSql != null) {
                for (int i = 0; i < options.getExtraValues().size(); i++) {
                    Object o = options.getExtraValues().get(i);
                    statement.setObject(2 + i, o);
                }
            }
            String valueString = value.toString();

            if(enumeratedField.getName().toLowerCase().contains("cocktail")) {
                try {
                    int valueInt = Integer.parseInt(valueString);
                    valueString = fimsToLims.getCocktailName(reactionType.toLowerCase(), valueInt);
                }
                catch(NumberFormatException ex) {} //do nothing - keep the old value
            }

            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                dataset.setValue(valueString, resultSet.getInt(1));
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
