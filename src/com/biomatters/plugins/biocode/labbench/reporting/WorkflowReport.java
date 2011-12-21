package com.biomatters.plugins.biocode.labbench.reporting;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.components.GPanel;

import javax.swing.*;
import javax.swing.table.TableModel;
import javax.swing.table.AbstractTableModel;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.awt.*;

import jebl.util.ProgressListener;
import jebl.util.CompositeProgressListener;
import org.jdom.Element;

/**
 * @author Steve
 * @version $Id$
 */
public class WorkflowReport extends Report{

    public String getTypeName() {
        return "Workflow Completion";
    }

    public String getTypeDescription() {
        return "Workflows";
    }

    public WorkflowReport(FimsToLims fimsToLims) {
        super(fimsToLims);
    }

    public WorkflowReport(Element e) throws XMLSerializationException {
        super(e);
    }

    private enum WorkflowProgress {
        PCR,
        CycleSequencing,
        SequencePassed
    }

    public Options createOptions(FimsToLims fimsToLims) {
        Options options = new Options(this.getClass());
        Set<DocumentField> documentFields = new LinkedHashSet<DocumentField>();
        documentFields.addAll(fimsToLims.getFimsFields());
        //documentFields.addAll(LIMSConnection.getSearchAttributes());
        List<Options.OptionValue> optionValues = ReportGenerator.getOptionValues(documentFields);
        options.addComboBoxOption("field", "View workflow progress across all values of this field", optionValues, optionValues.get(0));
        return options;
    }

    public ReportChart getChart(Options options, final FimsToLims fimsToLims, ProgressListener progress) throws SQLException {
        final Options.OptionValue optionValue = (Options.OptionValue)options.getValue("field");
        String field = FimsToLims.getSqlColName(optionValue.getName(), fimsToLims.getLimsConnection().isLocal());
        String sql  = "SELECT DISTINCT("+field+") FROM "+FimsToLims.FIMS_VALUES_TABLE;
        Statement statement = fimsToLims.getLimsConnection().getConnection().createStatement();
        ResultSet resultSet = statement.executeQuery(sql);
        final List<String> fieldValues = new ArrayList<String>();
        while(resultSet.next()) {
            fieldValues.add(resultSet.getString(1));
        }
        resultSet.close();
        statement.close();

        String s = getSql(WorkflowProgress.PCR, field, "=");
        System.out.println(s);
        PreparedStatement pcrStatement = fimsToLims.getLimsConnection().getConnection().prepareStatement(s);
        PreparedStatement sequencingStatement = fimsToLims.getLimsConnection().getConnection().prepareStatement(getSql(WorkflowProgress.CycleSequencing, field, "="));
        PreparedStatement assemblyStatement = fimsToLims.getLimsConnection().getConnection().prepareStatement(getSql(WorkflowProgress.SequencePassed, field, "="));

        final List<WorkflowEntry[]> table = new ArrayList<WorkflowEntry[]>();
        CompositeProgressListener composite = new CompositeProgressListener(progress, fieldValues.size());
        for (int i = 0; i < fieldValues.size(); i++) {
            String value = fieldValues.get(i);
            composite.beginSubtask("Processing "+value);
            //progress.setProgress(((double)i)/fieldValues.size());
            List<Options.OptionValue> lociOptionValues = fimsToLims.getLociOptionValues(true);
            WorkflowEntry[] entries = new WorkflowEntry[lociOptionValues.size()];
            for (int i1 = 0; i1 < lociOptionValues.size(); i1++) {
                composite.setProgress(((double)i1)/ lociOptionValues.size());
                Options.OptionValue locus = lociOptionValues.get(i1);
                pcrStatement.setObject(1, locus.getName());
                pcrStatement.setObject(2, value);
                composite.setMessage("PCR "+locus);
                ResultSet result = pcrStatement.executeQuery();
                int pcrCount = 0;
                while (result.next()) {
                    pcrCount = result.getInt(1);
                }

                composite.setMessage("Sequencing "+locus);
                sequencingStatement.setObject(1, locus.getName());
                sequencingStatement.setObject(2, value);
                result = pcrStatement.executeQuery();
                int sequencingCount = 0;
                while (result.next()) {
                    sequencingCount = result.getInt(1);
                }

                composite.setMessage("Assembly "+locus);
                assemblyStatement.setObject(1, locus.getName());
                assemblyStatement.setObject(2, value);
                result = pcrStatement.executeQuery();
                int assemblyCount = 0;
                while (result.next()) {
                    assemblyCount = result.getInt(1);
                }
                entries[i1] = new WorkflowEntry(pcrCount, sequencingCount, assemblyCount);
            }
            table.add(entries);
        }

        TableModel model = new AbstractTableModel(){

            @Override
            public String getColumnName(int column) {
                if(column == 0) {
                    return optionValue.getLabel();
                }
                return fimsToLims.getLociOptionValues(true).get(column-1).getLabel();
            }

            public int getRowCount() {
                return table.size();
            }

            public int getColumnCount() {
                return table.get(0).length+1;
            }

            public Object getValueAt(int rowIndex, int columnIndex) {
                if(columnIndex == 0) {
                    return fieldValues.get(rowIndex);
                }
                return table.get(rowIndex)[columnIndex-1];
            }
        };

        JTable jtable = new JTable(model);
        jtable.setRowHeight(jtable.getRowHeight()*3);
        final JPanel panel = new GPanel(new BorderLayout());
        panel.add(new JScrollPane(jtable), BorderLayout.CENTER);
        return new ReportChart(){
            public JPanel getPanel() {
                return panel;
            }
        };
    }

    private static class WorkflowEntry {
        private int pcrCount;
        private int cycleSequencingCount;
        private int assemblyCount;

        private WorkflowEntry(int pcrCount, int cycleSequencingCount, int assemblyCount) {
            this.pcrCount = pcrCount;
            this.cycleSequencingCount = cycleSequencingCount;
            this.assemblyCount = assemblyCount;
        }

        public int getPcrCount() {
            return pcrCount;
        }

        public int getCycleSequencingCount() {
            return cycleSequencingCount;
        }

        public int getAssemblyCount() {
            return assemblyCount;
        }

        public String toString() {
            return "<html> Passed PCR: "+pcrCount+"<br>" +
                    "Passed Sequencing: "+cycleSequencingCount+"<br>" +
                    "Passed Assemblies: "+assemblyCount+"</html>";
        }
    }

    private static String getSql(WorkflowProgress progress, String field, String comparator) {
        StringBuilder builder = new StringBuilder();
        String table;
        switch(progress) {
            case PCR:
                table = "pcr";
                break;
            case CycleSequencing:
                table = "cyclesequencing";
                break;
            case SequencePassed:
                table = "assembly";
                break;
            default :
                table = null;
                assert false;
        }

        builder.append("SELECT COUNT(workflow.id) FROM "+table+", workflow, extraction, "+FimsToLims.FIMS_VALUES_TABLE+" WHERE workflow.extractionId = extraction.id AND extraction.sampleId = "+FimsToLims.FIMS_VALUES_TABLE+".tissueId AND "+table+".progress = 'passed' AND workflow.locus = ? AND "+FimsToLims.FIMS_VALUES_TABLE+"."+field+" "+comparator+" ?");

        return builder.toString();
    }
}
