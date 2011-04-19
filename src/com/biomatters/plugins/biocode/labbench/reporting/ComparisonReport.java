package com.biomatters.plugins.biocode.labbench.reporting;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.documents.DocumentField;
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
import org.jfree.chart.plot.IntervalMarker;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.StandardBarPainter;
import org.jfree.chart.renderer.category.BarPainter;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.ui.RectangleAnchor;
import org.jfree.ui.TextAnchor;
import org.jfree.ui.Layer;
import org.jfree.ui.RectangleEdge;
import org.virion.jam.util.SimpleListener;

import java.awt.*;
import java.awt.geom.RectangularShape;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import jebl.util.ProgressListener;

import javax.swing.*;

/**
 * @author Steve
 * @version $Id$
 */
public class ComparisonReport implements Report{

    public String getName() {
        return "Bar Chart (Field Comparison)";
    }

    public Options getOptions(FimsToLims fimsToLims) throws SQLException {
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
        List<String> values = new ArrayList<String>();
        String field = fieldToCompare.getCode();
        String xTable = fims ? "fims_values" : "assembly";
        if(field.indexOf(".") >= 0) {
            xTable = field.substring(0, field.indexOf("."));
            field = field.substring(field.indexOf(".")+1);
        }
        if(fims) {
            field = FimsToLims.getSqlColName(field);
        }

        String sql;
        if(fims) {
            sql = "SELECT DISTINCT("+field+") FROM fims_values";
        }
        else {

            sql = "SELECT DISTINCT ("+field+") FROM "+xTable;
        }

        ResultSet resultSet = fimsToLims.getLimsConnection().getConnection().createStatement().executeQuery(sql);
        while(resultSet.next()) {
            values.add(resultSet.getString(1));
        }
        if(values.size() > 20) {
            final AtomicBoolean cont = new AtomicBoolean();
            final int valueSize = values.size();
            Runnable r = new Runnable() {
                public void run() {
                    cont.set(Dialogs.showYesNoDialog("The field you have chosen has "+valueSize+" distinct values in the database.  This will result in a very large chart.  Are you sure you want to contunie?", "Large Values", null, Dialogs.DialogIcon.QUESTION));
                }
            };
            ThreadUtilities.invokeNowOrWait(r);
            if(!cont.get()) {
                return null;
            }
        }

        ReactionFieldOptions fieldOptions = options.getYAxisOptions();
        String sql1;
        String yTable = fieldOptions.getTable();

        if(fims) {
            sql1 = fieldOptions.getSql("fims_values", "fims_values." + fimsToLims.getTissueColumnId() + "=extraction.sampleId");
            //sql1 = fieldOptions.getSql(fims);
        }
        else {
            if(xTable.equals(yTable)) {
                sql1 = fieldOptions.getSql(null, null);
            }
            else {
                sql1 = fieldOptions.getSql(xTable, xTable+".workflow = workflow.id");
            }
        }
        sql1  = sql1 + " AND "+xTable+"."+field+" like ?";
        System.out.println(sql1);

        PreparedStatement statement = fimsToLims.getLimsConnection().getConnection().prepareStatement(sql1);
        List<Integer> results = new ArrayList<Integer>();

        for (int i1 = 0; i1 < values.size(); i1++) {
            String value = values.get(i1);
            progress.setProgress(((double)i1)/values.size());
            if(progress.isCanceled()) {
                return null;
            }
            statement.setString(1, fieldOptions.getValue());
            statement.setString(2, value);
            long time = System.currentTimeMillis();
            ResultSet set = statement.executeQuery();
            System.out.println(System.currentTimeMillis() - time + " millis for " + value);
            while (set.next()) {
                int result = set.getInt(1);
                System.out.println(result);
                results.add(result);
            }
        }

        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        for(int i=0; i < values.size(); i++) {
            String value = values.get(i);
            dataset.addValue(results.get(i), field, value != null && value.length() > 0 ? value : "None");
        }


        final String title = "Comparison";
        final String xLabel = fimsToLims.getFriendlyName(field);
        final String yLabel = fieldOptions.getField() + " " + fieldOptions.getValue();
        final JFreeChart barChart = ChartFactory.createBarChart(title, xLabel, yLabel, dataset, PlotOrientation.VERTICAL, false, true, false);
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



        final ChartPanel panel = new ChartPanel(barChart);


        return new ReportChart(){
            public JPanel getPanel() {
                return panel;
            }

            @Override
            public Options getOptions() {
                Options options = new Options(this.getClass());
                options.addDivider("Labels");
                final Options.StringOption titleOption = options.addStringOption("title", "Title: ", title);
                final Options.StringOption xLabelOption = options.addStringOption("xlabel", "X-label: ", xLabel);
                final Options.StringOption yLabelOption = options.addStringOption("ylabel", "Y-label: ", yLabel);

                options.addDivider("Display");
                final ColorOption barColorOption = new ColorOption("barColor", "Bar Color: ", barColor);
                options.addCustomOption(barColorOption);

                final Options.OptionValue[] labelPositionValues = new Options.OptionValue[] {
                        new Options.OptionValue("standard", "Horizontal"),
                        new Options.OptionValue("up45", "Up, 45 deg"),
                        new Options.OptionValue("up90", "Up, 90 deg"),
                        new Options.OptionValue("down45", "Down, 45 deg"),
                        new Options.OptionValue("down90", "Down, 90 deg")
                };
                final Options.ComboBoxOption<Options.OptionValue> labelPosition = options.addComboBoxOption("labelPosition", "Label position: ", labelPositionValues, labelPositionValues[0]);
                final Options.BooleanOption shadows = options.addBooleanOption("shadows", "Draw shadows", barRenderer.getShadowsVisible());

                options.setHorizontallyCompact(true);
                //options.setVerticallyCompact(true);

                options.addChangeListener(new SimpleListener(){
                    public void objectChanged() {
                        barChart.setTitle(titleOption.getValue());
                        barChart.getCategoryPlot().getDomainAxis().setLabel(xLabelOption.getValue());
                        barChart.getCategoryPlot().getRangeAxis().setLabel(yLabelOption.getValue());

                        plot.getRenderer().setSeriesPaint(0, barColorOption.getValue());
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

                return options;
            }
        };
    }
}
