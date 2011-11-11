package com.biomatters.plugins.biocode.labbench.reporting;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.plugin.DocumentSelectionSignature;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.components.GPanel;
import com.biomatters.geneious.publicapi.implementations.Percentage;
import com.biomatters.plugins.biocode.labbench.TableDocumentViewerFactory;
import jebl.util.ProgressListener;
import jebl.util.CompositeProgressListener;

import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.LinkedHashMap;
import java.awt.*;

import org.jdom.Element;

import javax.swing.*;
import javax.swing.table.TableModel;
import javax.swing.table.AbstractTableModel;

/**
 * @author Steve
 * @version $Id$
 *          <p/>
 *          Created on 28/10/2011 4:30:35 PM
 */


public class PrimerPerformanceReport extends Report{

    public PrimerPerformanceReport(FimsToLims fimsToLims) {
        super(fimsToLims);
    }

    public PrimerPerformanceReport(Element e) throws XMLSerializationException {
        super(e);
    }

    public String getTypeName() {
        return "Primer Performance";
    }

    public String getTypeDescription() {
        return "Measure the performance of primers across various conditions";
    }

    public Options createOptions(FimsToLims fimsToLims) {
        return new PrimerPerformanceOptions(this.getClass(), fimsToLims);
    }

    public ReportChart getChart(Options optionsa, FimsToLims fimsToLims, ProgressListener progress) throws SQLException {
        final PrimerPerformanceOptions options = (PrimerPerformanceOptions)optionsa;
        String fimsColumn = FimsToLims.getSqlColName(options.getFimsField());

        final List<String> progressValues = ReportGenerator.getDistinctValues(fimsToLims, "progress", "pcr", null, true, progress);

        final List<String> fimsValues = ReportGenerator.getDistinctValues(fimsToLims, fimsColumn, FimsToLims.FIMS_VALUES_TABLE, null, false, progress);

        if(progressValues == null) {
            return null;
        }

        String sql = "SELECT "+FimsToLims.FIMS_VALUES_TABLE+"."+fimsColumn+", count(pcr.id) from pcr, workflow, extraction, fims_values WHERE pcr.workflow=workflow.id AND workflow.extractionId = extraction.id AND extraction.sampleId = fims_values."+fimsToLims.getTissueColumnId()+" AND (pcr.prSequence=? AND pcr.revPrSequence=? AND pcr.progress=?) group by "+FimsToLims.FIMS_VALUES_TABLE+"."+fimsColumn;

        System.out.println(sql);

        PreparedStatement statement = fimsToLims.getLimsConnection().getConnection().prepareStatement(sql);

        final List<Map<String, Integer>> counts = new ArrayList<Map<String, Integer>>();

        CompositeProgressListener composite = new CompositeProgressListener(progress, progressValues.size());


        for(String value : progressValues) {
            composite.beginSubtask("Counting "+(value != null ? value : "No Value")+" reactions");
            if(composite.isCanceled()) {
                return null;
            }
            statement.setString(1, options.getForwardPrimer().getPrimers().get(0).getSequence());
            statement.setString(2, options.getReversePrimer().getPrimers().get(0).getSequence());
            statement.setString(3, value);

            ResultSet resultSet = statement.executeQuery();

            Map<String, Integer> valuesMap = new LinkedHashMap<String, Integer>();
            while(resultSet.next()) {
                valuesMap.put(resultSet.getString(1), resultSet.getInt(2));
            }
            counts.add(valuesMap);
        }
        progress.setProgress(1.0);


        final TableModel model = new AbstractTableModel(){
            public int getRowCount() {
                return fimsValues.size();
            }

            public int getColumnCount() {
                return progressValues.size() > 0 ? progressValues.size()+2 : 0;
            }

            public Object getValueAt(int rowIndex, int columnIndex) {
                if(columnIndex == 0) {
                    return fimsValues.get(rowIndex);
                }
                if(columnIndex == 1) {
                    Integer passedObject = counts.get(0).get(fimsValues.get(rowIndex));
                    Integer failedObject = counts.get(1).get(fimsValues.get(rowIndex));
                    int passed = passedObject != null ? passedObject : 0;
                    int failed = failedObject != null ? failedObject : 0;
                    if(passed == 0 && failed == 0) {
                        return null;
                    }
                    return new Percentage(100.0*(passed)/(failed+passed));
                }
                Integer count = counts.get(columnIndex - 2).get(fimsValues.get(rowIndex));
                return count != null ? count : 0;
            }

            @Override
            public String getColumnName(int column) {
                if(column == 0) {
                    return options.getFimsFieldName();
                }
                if(column == 1) {
                    return "Performance";
                }
                return progressValues.get(column-2);
            }

            @Override
            public Class<?> getColumnClass(int column) {
                if(column == 0) {
                    return String.class;
                }
                if(column == 1) {
                    return Percentage.class;
                }
                return Integer.class;
            }
        };

        final TableDocumentViewerFactory factory = new TableDocumentViewerFactory(){
            protected TableModel getTableModel(AnnotatedPluginDocument[] docs, Options options) {
                return model;
            }

            public String getName() {
                return null;
            }

            public String getDescription() {
                return null;
            }

            public String getHelp() {
                return null;
            }

            public DocumentSelectionSignature[] getSelectionSignatures() {
                return new DocumentSelectionSignature[0];
            }
        };

        return new ReportChart(){
            public JPanel getPanel() {
                GPanel panel = new GPanel(new BorderLayout());
                panel.add(factory.createViewer(new AnnotatedPluginDocument[0]).getComponent(), BorderLayout.CENTER);
                return panel;
            }

            @Override
            public ChartExporter[] getExporters() {
                return new ChartExporter[] {
                        new ExcelChartExporter(getName(), model)
                };
            }
        };
    }


    
}
