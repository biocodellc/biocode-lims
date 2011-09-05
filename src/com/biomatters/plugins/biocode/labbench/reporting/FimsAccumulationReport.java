package com.biomatters.plugins.biocode.labbench.reporting;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import jebl.util.ProgressListener;
import jebl.util.CompositeProgressListener;

import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.DateFormat;
import java.util.List;
import java.util.Date;
import java.util.ArrayList;

import org.jdom.Element;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.data.xy.XYSeries;

/**
 * @author Steve
 * @version $Id$
 *          <p/>
 *          Created on 18/08/2011 5:25:21 PM
 */


public class FimsAccumulationReport extends AccumulationReport{


    public FimsAccumulationReport(FimsToLims fimsToLims) {
        super(fimsToLims);
    }

    public FimsAccumulationReport(Element e) throws XMLSerializationException {
        super(e);
    }

    public String getTypeName() {
        return "Accumulation Curve (Across field data)";
    }

    public String getTypeDescription() {
        return "An accumulation curve of one LIMS field across all values of a field selected from the FIMS database";
    }

    public Options createOptions(FimsToLims fimsToLims) {
        return new FimsAccumulationOptions(this.getClass(), fimsToLims);
    }

    public ReportChart getChart(Options optionsa, FimsToLims fimsToLims, ProgressListener progress) throws SQLException {

        DateFormat dateFormat = DateFormat.getDateInstance();
        FimsAccumulationOptions options = (FimsAccumulationOptions)optionsa;
        final XYSeriesCollection dataset = new XYSeriesCollection();


        progress.setMessage("Getting all possible values for "+options.getFimsField());
        progress.setIndeterminateProgress();
        List<String> fimsValues = ReportGenerator.getDistinctValues(fimsToLims, options.getFimsField(), FimsToLims.FIMS_VALUES_TABLE, null, progress);
        ReactionFieldOptions fieldOptions = options.getFieldOptions();

        String sql = options.getSql(fimsToLims);
        System.out.println(sql);
        PreparedStatement statement = fimsToLims.getLimsConnection().getConnection().prepareStatement(sql);
        CompositeProgressListener composite = new CompositeProgressListener(progress, fimsValues.size());
        for (int i1 = 0; i1 < fimsValues.size(); i1++) {
            String fimsValue = fimsValues.get(i1);
            final String seriesName = fimsValue;
            composite.beginSubtask("Calculating series "+(i1+1)+" of "+fimsValues.size()+" ("+fimsValue+")");
            Object fieldValue = fieldOptions.getValue();
            int fieldValueParam = 0;
            if(fieldValue != null) {
                fieldValueParam = 1;
                statement.setObject(fieldValueParam, fieldValue);
            }
            statement.setObject(fieldValueParam+1, fimsValue);
            Date startDate = options.getStartDate();
            Date endDate = options.getEndDate();
            if(startDate.equals(endDate)) {
                throw new SQLException("You cannot compute a report where the start date and end date are the same");
            }
            List<Integer> counts = new ArrayList<Integer>();
            XYSeries series = new XYSeries(""+seriesName);
            for (long time = startDate.getTime(); time <= endDate.getTime(); time += (endDate.getTime() - startDate.getTime()) / 40) {
                composite.setProgress(((double) time - startDate.getTime()) / (endDate.getTime() - startDate.getTime()));
                if (progress.isCanceled()) {
                    return null;
                }
                java.sql.Date date = new java.sql.Date(time);
                composite.setMessage(dateFormat.format(date));
                statement.setDate(fieldValueParam+2, date);
                ResultSet resultSet = statement.executeQuery();
                resultSet.next();
                int count = resultSet.getInt(1);
                System.out.println(date + ": " + count);
                counts.add(count);
                series.add(new DateDataItem(date, count));
            }
            dataset.addSeries(series);
        }


        return createAccumulationChart(dataset);
    }
}
