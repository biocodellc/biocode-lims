package com.biomatters.plugins.biocode.labbench.reporting;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.StandardBarPainter;
import org.jfree.data.category.DefaultCategoryDataset;
import org.virion.jam.util.SimpleListener;
import org.jdom.Element;

import java.awt.*;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import jebl.util.ProgressListener;
import jebl.util.CompositeProgressListener;

import javax.swing.*;

/**
 * @author Steve
 * @version $Id$
 */
public class ComparisonReport extends Report{

    public String getTypeName() {
        return "Bar Chart (Field Comparison)";
    }

    public String getTypeDescription() {
        return "A comparison of two or more fields in a bar chart";
    }

    public ComparisonReport(FimsToLims fimsToLims) {
        super(fimsToLims);
    }

    public ComparisonReport(Element e) throws XMLSerializationException {
        super(e);
    }

    public Options createOptions(FimsToLims fimsToLims) {
        return new ComparisonReportOptions(this.getClass(), fimsToLims);

    }

    public ReportChart getChart(Options optionsa, FimsToLims fimsToLims, ProgressListener progress)  throws SQLException{

        ComparisonReportOptions options = (ComparisonReportOptions)optionsa;
        Options.OptionValue optionValueToCompare = options.getXField();
        DocumentField fieldToCompare = null;
        boolean fims = false;
        for(DocumentField field : fimsToLims.getFimsFields()) {
            if(field.getCode().equals(optionValueToCompare.getName())) {
                fieldToCompare = field;
                fims = true;
                break;
            }
        }
        if(fieldToCompare == null) {
            for(DocumentField field : LIMSConnection.getSearchAttributes()) {
                if(field.getCode().equals(optionValueToCompare.getName())) {
                    fieldToCompare = field;
                    fims = false;
                    break;
                }
            }
        }
        if(fieldToCompare == null) {
            //not sure whether to crash here or to suggest a reconnect?
            throw new RuntimeException("The field "+optionValueToCompare.getName()+" was not found in either the FIMS or the LIMS!");
        }
        String field = fieldToCompare.getCode();
        String xTable = fims ? FimsToLims.FIMS_VALUES_TABLE : "";
        if(field.indexOf(".") >= 0) {
            xTable = field.substring(0, field.indexOf("."));
            field = field.substring(field.indexOf(".")+1);
        }
        if(fims) {
            field = FimsToLims.getSqlColName(field);
        }
        final List<ReactionFieldOptions> fieldOptionsList = options.getYAxisOptions();
        Set<String> loci = new LinkedHashSet<String>();
        for(ReactionFieldOptions fieldOption : fieldOptionsList) {
            String locusValue = fieldOption.getLocus();
            if(locusValue != null) {
                loci.add(locusValue);
            }
        }

        List<String> values = ReportGenerator.getDistinctValues(fimsToLims, field, xTable, fims ? null : loci, progress);

        if(values == null) {
            return null;
        }

        DefaultCategoryDataset dataset = new DefaultCategoryDataset();

        CompositeProgressListener composite = new CompositeProgressListener(progress, fieldOptionsList.size());
        final List<FieldResult> results = new ArrayList<FieldResult>();
        for(ReactionFieldOptions fieldOptions : fieldOptionsList) {
            composite.beginSubtask();

            String sql1;
            String yTable = fieldOptions.getTable();

            if(fims) {
                sql1 = fieldOptions.getSql(FimsToLims.FIMS_VALUES_TABLE, FimsToLims.FIMS_VALUES_TABLE+"." + fimsToLims.getTissueColumnId() + "=extraction.sampleId");
                //sql1 = fieldOptions.getSql(fims);
            }
            else {
                if(xTable.equals(yTable) || xTable.equals("workflow")) {
                    sql1 = fieldOptions.getSql(null, null);
                }
                else {
                    sql1 = fieldOptions.getSql(xTable, xTable+".workflow = workflow.id");
                }
            }
            sql1  = sql1 + " AND "+xTable+"."+field+" like ?";
            System.out.println(sql1);

            PreparedStatement statement = fimsToLims.getLimsConnection().getConnection().prepareStatement(sql1);

            for (int i1 = 0; i1 < values.size(); i1++) {
                String value = values.get(i1);
                composite.setProgress(((double)i1)/values.size());
                if(progress.isCanceled()) {
                    return null;
                }
                if(fieldOptions.getValue() != null) {
                    statement.setObject(1, fieldOptions.getValue());
                    statement.setString(2, value);
                }
                else {
                    statement.setString(1, value);
                }
                long time = System.currentTimeMillis();
                ResultSet set = statement.executeQuery();
                System.out.println(System.currentTimeMillis() - time + " millis for " + value);
                while (set.next()) {
                    int result = set.getInt(1);
                    System.out.println(result);
                    results.add(new FieldResult(fieldOptions.getNiceName(), value != null && value.length() > 0 ? value : "None", result));
                }
            }
        }
        for(FieldResult result : results) {
            dataset.addValue(result.getResult(), result.getSeries(), result.getField());
        }


        final String title = "Comparison";
        final String xLabel = fimsToLims.getFriendlyName(field);
        final String yLabel;
        if(fieldOptionsList.size() == 1) {
            ReactionFieldOptions fieldOptions = fieldOptionsList.get(0);
            if(fieldOptions.getValue() != null) {
                yLabel = fieldOptions.getNiceName();
            }
            else {
                yLabel = fieldOptions.getFriendlyTableName();
            }
        }
        else {
            yLabel = "Count";
        }
        final JFreeChart barChart = ChartFactory.createBarChart(title, xLabel, yLabel, dataset, PlotOrientation.VERTICAL, fieldOptionsList.size() > 1, true, false);
        //barChart.getLegendItems(); //call this to triger the chart to populate its colors...
        final CategoryPlot plot = barChart.getCategoryPlot();
        barChart.getTitle().setFont(new Font("sans serif", Font.BOLD, 24));
        final BarRenderer barRenderer = new BarRenderer();
        barRenderer.setShadowVisible(false);
        plot.setRenderer(barRenderer);
        final Color barColor = new Color(0x4e6d92);
        barRenderer.setSeriesPaint(0, barColor);
        barRenderer.setBarPainter(new StandardBarPainter());
        plot.setBackgroundPaint(Color.white);
        plot.setDomainGridlinePaint(Color.lightGray);
        plot.setDomainGridlinesVisible(true);
        plot.setRangeGridlinePaint(Color.lightGray);

        final CategoryAxis domainAxis = plot.getDomainAxis();
        domainAxis.setCategoryLabelPositions(CategoryLabelPositions.STANDARD);



        final ChartPanel panel = new ChartPanel(barChart, false);
        panel.setMaximumDrawWidth(Integer.MAX_VALUE);
        panel.setMaximumDrawHeight(Integer.MAX_VALUE);


        return new ReportChart(){
            public JPanel getPanel() {
                return panel;
            }

            @Override
            public Options getOptions() {
                final Options options = new Options(this.getClass());
                options.addDivider("Labels");
                final Options.StringOption titleOption = options.addStringOption("title", "Title: ", title);
                final Options.StringOption xLabelOption = options.addStringOption("xlabel", "X-label: ", xLabel);
                final Options.StringOption yLabelOption = options.addStringOption("ylabel", "Y-label: ", yLabel);

                options.addDivider("Display");

                for (int i = 0; i < fieldOptionsList.size(); i++) {
                    ReactionFieldOptions fieldOptions = fieldOptionsList.get(i);
                    barRenderer.lookupSeriesPaint(i); //call this to make sure the series paint is populated
                    final ColorOption barColorOption = new ColorOption("barColor"+i, fieldOptions.getNiceName()+" ", (Color)plot.getRenderer().getSeriesPaint(i));
                    options.addCustomOption(barColorOption);
                }



                final Options.OptionValue[] labelPositionValues = new Options.OptionValue[] {
                        new Options.OptionValue("standard", "Horizontal"),
                        new Options.OptionValue("up45", "Up, 45 deg"),
                        new Options.OptionValue("up90", "Up, 90 deg"),
                        new Options.OptionValue("down45", "Down, 45 deg"),
                        new Options.OptionValue("down90", "Down, 90 deg")
                };
                final Options.ComboBoxOption<Options.OptionValue> labelPosition = options.addComboBoxOption("labelPosition", "Label position: ", labelPositionValues, labelPositionValues[0]);

                final Options.OptionValue[] orderingValues = new Options.OptionValue[] {
                        new Options.OptionValue("natural", "Natural Ordering"),
                        new Options.OptionValue("field", xLabel),
                        new Options.OptionValue("countasc", "Count (Ascending)"),
                        new Options.OptionValue("countdesc", "Count (Descending)"),
                };
                final Options.ComboBoxOption<Options.OptionValue> ordering = options.addComboBoxOption("ordering", "Order by: ", orderingValues, orderingValues[0]);



                final Options.BooleanOption shadows = options.addBooleanOption("shadows", "Draw shadows", barRenderer.getShadowsVisible());

                options.setHorizontallyCompact(true);
                //options.setVerticallyCompact(true);

                options.addChangeListener(new SimpleListener(){
                    public void objectChanged() {
                        barChart.setTitle(titleOption.getValue());
                        barChart.getCategoryPlot().getDomainAxis().setLabel(xLabelOption.getValue());
                        barChart.getCategoryPlot().getRangeAxis().setLabel(yLabelOption.getValue());

                        for (int i = 0; i < fieldOptionsList.size(); i++) {
                            ColorOption barColorOption = (ColorOption)options.getOption("barColor"+i);
                            plot.getRenderer().setSeriesPaint(i, barColorOption.getValue());
                        }
                        if(labelPosition.getValue().equals(labelPositionValues[0])) {
                            plot.getDomainAxis().setCategoryLabelPositions(CategoryLabelPositions.STANDARD);
                        }
                        else if(labelPosition.getValue().equals(labelPositionValues[1])) {
                            plot.getDomainAxis().setCategoryLabelPositions(CategoryLabelPositions.UP_45);
                        }
                        else if(labelPosition.getValue().equals(labelPositionValues[2])) {
                            plot.getDomainAxis().setCategoryLabelPositions(CategoryLabelPositions.UP_90);
                        }
                        else if(labelPosition.getValue().equals(labelPositionValues[3])) {
                            plot.getDomainAxis().setCategoryLabelPositions(CategoryLabelPositions.DOWN_45);
                        }
                        else if(labelPosition.getValue().equals(labelPositionValues[4])) {
                            plot.getDomainAxis().setCategoryLabelPositions(CategoryLabelPositions.DOWN_90);         
                        }

                        barRenderer.setShadowVisible(shadows.getValue());
                    }
                });

                SimpleListener orderingListener = new SimpleListener() {
                    public void objectChanged() {
                        List<FieldResult> newResults = new ArrayList<FieldResult>(results);
                        Options.OptionValue sorting = ordering.getValue();

                        //don't need to do anything for the first sorting order...
                        if(sorting.equals(orderingValues[1])) {
                            Collections.sort(newResults, new Comparator<FieldResult>() {
                                public int compare(FieldResult o1, FieldResult o2) {
                                    return o1.getField().compareTo(o2.getField());
                                }
                            });
                        }
                        else if(sorting.equals(orderingValues[2])) {
                            Collections.sort(newResults, new Comparator<FieldResult>() {
                                public int compare(FieldResult o1, FieldResult o2) {
                                    int dif = o1.result - o2.result;
                                    if(dif == 0) {
                                        return o1.getField().compareTo(o2.getField());
                                    }
                                    return dif;
                                }
                            });
                        }
                        else if(sorting.equals(orderingValues[3])) {
                            Collections.sort(newResults, new Comparator<FieldResult>() {
                                public int compare(FieldResult o1, FieldResult o2) {
                                    int dif = o2.result - o1.result;
                                    if(dif == 0) {
                                        return o1.getField().compareTo(o2.getField());
                                    }
                                    return dif;
                                }
                            });
                        }
                        DefaultCategoryDataset plotDataset = (DefaultCategoryDataset) plot.getDataset();
                        plotDataset.clear();

                        for (FieldResult newResult : newResults) {
                            plotDataset.addValue(newResult.getResult(), newResult.getSeries(), newResult.getField());
                        }
                    }
                };

                ordering.addChangeListener(orderingListener);
                orderingListener.objectChanged();



                return options;
            }
        };
    }

    private static class FieldResult implements Comparable{
        private String series;
        String field;
        int result;

        public String getField() {
            return field;
        }

        public int getResult() {
            return result;
        }

        private FieldResult(String series, String field, int result) {
            this.series = series;
            this.field = field;
            this.result = result;
        }

        public int compareTo(Object o) {
            FieldResult result = (FieldResult)o;
            if(result.getSeries().equals(series)) {
                return result.getSeries().compareTo(series);
            }
            int dif = result.result - this.result;
            if(dif == 0) {
                return field.compareTo(result.field);
            }
            return dif;
        }

        public String getSeries() {
            return series;
        }
    }
}
