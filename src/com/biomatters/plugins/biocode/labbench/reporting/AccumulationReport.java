package com.biomatters.plugins.biocode.labbench.reporting;

import com.biomatters.geneious.publicapi.plugin.Options;

import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Date;
import java.util.ArrayList;
import java.awt.*;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeriesCollection;
import jebl.util.ProgressListener;

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

    public ChartPanel getChart(Options optionsa, FimsToLims fimsToLims, ProgressListener progress) throws SQLException {
        AccumulationOptions options = (AccumulationOptions)optionsa;
        System.out.println(options.getSql());
        String sql = options.getSql();
        PreparedStatement statement = fimsToLims.getLimsConnection().getConnection().prepareStatement(sql);
        List<Object> objects = options.getObjectsForPreparedStatement();
        for (int i = 0; i < objects.size(); i++) {
            statement.setObject(i+1, objects.get(i));
        }
        Date startDate = options.getStartDate();
        Date endDate = options.getEndDate();
        List<Integer> counts = new ArrayList<Integer>();
        XYSeries series = new XYSeries("Dates");
        for(long time = startDate.getTime(); time <= endDate.getTime(); time += (endDate.getTime()-startDate.getTime())/40) {
            progress.setProgress(((double)time-startDate.getTime())/(endDate.getTime()-startDate.getTime()));
            if(progress.isCanceled()) {
                return null;
            }
            java.sql.Date date = new java.sql.Date(time);
            statement.setDate(objects.size()+1, date);
            ResultSet resultSet = statement.executeQuery();
            resultSet.next();
            int count = resultSet.getInt(1);
            System.out.println(date+": "+count);
            counts.add(count);
            series.add(new DateDataItem(date, count));
        }

        XYSeriesCollection dataset = new XYSeriesCollection();
        dataset.addSeries(series);
        final JFreeChart chart = ChartFactory.createXYLineChart(
                    "Accumulation",      // chart title
                    "X",                      // x axis label
                    "Count",                      // y axis label
                    dataset,                  // data
                    PlotOrientation.VERTICAL,
                    true,                     // include legend
                    true,                     // tooltips
                    false                     // urls
                );
        final XYPlot plot = chart.getXYPlot();
        plot.getRenderer().setSeriesStroke(0, new BasicStroke(3.0f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_ROUND));
        plot.setBackgroundPaint(Color.white);
        plot.setDomainGridlinePaint(Color.lightGray);
        plot.setRangeGridlinePaint(Color.lightGray);

        plot.setDomainAxis(new DateAxis());

        final ChartPanel chartPanel = new ChartPanel(chart);

        return chartPanel;
    }
}
