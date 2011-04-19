package com.biomatters.plugins.biocode.labbench.reporting;

import com.biomatters.geneious.publicapi.plugin.Options;

import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Date;
import java.util.ArrayList;
import java.util.Iterator;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.text.DateFormat;

import org.jfree.chart.*;
import org.jfree.chart.labels.XYItemLabelGenerator;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.general.Series;
import org.virion.jam.util.SimpleListener;
import jebl.util.ProgressListener;
import jebl.util.CompositeProgressListener;

import javax.swing.*;

/**
 * @author Steve
 * @version $Id$
 */
public class AccumulationReport implements Report{

    public String getName() {
        return "Accumulation Curve";
    }

    public Options getOptions(FimsToLims fimsToLims) throws SQLException {
        return new AccumulationOptions(this.getClass(), fimsToLims);
    }

    public ReportChart getChart(Options optionsa, FimsToLims fimsToLims, ProgressListener progress) throws SQLException {
        DateFormat dateFormat = DateFormat.getDateInstance();
        AccumulationOptions options = (AccumulationOptions)optionsa;
        final XYSeriesCollection dataset = new XYSeriesCollection();

        CompositeProgressListener composite = new CompositeProgressListener(progress, options.getSeriesOptions().size());
        for (int i1 = 0; i1 < options.getSeriesOptions().size(); i1++) {
            Options seriesOptions = options.getSeriesOptions().get(i1);
            ReactionFieldOptions fieldOptions = (ReactionFieldOptions) seriesOptions;
            final String seriesName = fieldOptions.getNiceName();
            composite.beginSubtask("Calculating series "+(i1+1)+" of "+options.getSeriesOptions().size()+" ("+seriesName+")");
                    System.out.println(options.getSql(fieldOptions));
            String sql = options.getSql(fieldOptions);
            PreparedStatement statement = fimsToLims.getLimsConnection().getConnection().prepareStatement(sql);
            List<Object> objects = options.getObjectsForPreparedStatement(fieldOptions);
            for (int i = 0; i < objects.size(); i++) {
                statement.setObject(i + 1, objects.get(i));
            }
            Date startDate = options.getStartDate();
            Date endDate = options.getEndDate();
            if(startDate.equals(endDate)) {
                throw new SQLException("You cannot compute a report where the start date and end date are the same");
            }
            List<Integer> counts = new ArrayList<Integer>();
            XYSeries series = new XYSeries(seriesName);
            for (long time = startDate.getTime(); time <= endDate.getTime(); time += (endDate.getTime() - startDate.getTime()) / 40) {
                composite.setProgress(((double) time - startDate.getTime()) / (endDate.getTime() - startDate.getTime()));
                if (progress.isCanceled()) {
                    return null;
                }
                java.sql.Date date = new java.sql.Date(time);
                composite.setMessage(dateFormat.format(date));                
                statement.setDate(objects.size() + 1, date);
                ResultSet resultSet = statement.executeQuery();
                resultSet.next();
                int count = resultSet.getInt(1);
                System.out.println(date + ": " + count);
                counts.add(count);
                series.add(new DateDataItem(date, count));
            }

            dataset.addSeries(series);
        }
        final String title = "Accumulation";
        final String yAxis = "Count";
        final JFreeChart chart = ChartFactory.createXYLineChart(
                title,      // chart title
                    "X",                      // x axis label
                yAxis,                      // y axis label
                    dataset,                  // data
                    PlotOrientation.VERTICAL,
                    true,                     // include legend
                    true,                     // tooltips
                    false                     // urls
                );
        final XYPlot plot = chart.getXYPlot();
        final XYItemRenderer renderer = plot.getRenderer();
        for(int i=0; i < dataset.getSeriesCount(); i++) {
            renderer.setSeriesStroke(i, new BasicStroke(3.0f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_ROUND));
        }

        plot.setBackgroundPaint(Color.white);
        plot.setDomainGridlinePaint(Color.lightGray);
        plot.setRangeGridlinePaint(Color.lightGray);

        plot.setDomainAxis(new DateAxis());

        final ChartPanel chartPanel = new ChartPanel(chart);

        return new ReportChart(){
            public JPanel getPanel() {
                return chartPanel;
            }

            @Override
            public Options getOptions() {
                final Options options = new Options(this.getClass());
                options.addDivider("Labels");
                final Options.StringOption titleOption = options.addStringOption("title", "Title: ", title);
                final Options.StringOption yLabelOption = options.addStringOption("ylabel", "Y-label: ", yAxis);

                for (int i = 0; i < dataset.getSeries().size(); i++) {
                    Object o = dataset.getSeries().get(i);
                    XYSeries s = (XYSeries) o;
                    options.addDivider("Series "+(i+1));
                    options.addStringOption("seriestitle"+i, "Title: ", renderer.getLegendItem(0, i).getLabel());
                    ColorOption colorOption = new ColorOption("seriescolor"+i, "Color: ", (Color)renderer.getSeriesPaint(i));
                    options.addCustomOption(colorOption);
                    options.addIntegerOption("serieswidth"+i, "Line width: ", (int)((BasicStroke)renderer.getSeriesStroke(i)).getLineWidth(), 1, Integer.MAX_VALUE);
                }

                options.setHorizontallyCompact(true);
                //options.setVerticallyCompact(true);

                options.addChangeListener(new SimpleListener(){
                    public void objectChanged() {
                        chart.setTitle(titleOption.getValue());
                        chart.getXYPlot().getRangeAxis().setLabel(yLabelOption.getValue());
                        LegendItemCollection legends = new LegendItemCollection();

                        for (int i = 0; i < dataset.getSeries().size(); i++) {
                            final Color seriesColor = (Color) options.getValue("seriescolor" + i);
                            renderer.setSeriesPaint(i, seriesColor);
                            renderer.setSeriesStroke(i, new BasicStroke((Integer)options.getValue("serieswidth"+i), BasicStroke.CAP_SQUARE, BasicStroke.JOIN_ROUND));
                            legends.add(new LegendItem(options.getValueAsString("seriestitle"+i), seriesColor));
                        }

                        chart.getXYPlot().setFixedLegendItems(legends);

                    }
                });

                return options;
            }
        };
    }
}
