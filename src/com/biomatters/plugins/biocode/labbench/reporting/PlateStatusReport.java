package com.biomatters.plugins.biocode.labbench.reporting;

import com.biomatters.geneious.publicapi.components.GPanel;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.plugin.DocumentSelectionSignature;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import com.biomatters.plugins.biocode.labbench.TableDocumentViewerFactory;
import com.biomatters.plugins.biocode.labbench.lims.FimsToLims;
import jebl.util.CompositeProgressListener;
import jebl.util.ProgressListener;
import org.jdom.Element;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by IntelliJ IDEA.
 * User: Steve
 * Date: 29/09/11
 * Time: 6:33 AM
 * To change this template use File | Settings | File Templates.
 */
public class PlateStatusReport extends Report {


    public PlateStatusReport(FimsToLims fimsToLims) {
        super(fimsToLims);
    }

    public PlateStatusReport(Element e) throws XMLSerializationException {
        super(e);
    }

    @Override
    public String getTypeName() {
        return "Plate Status Report";
    }

    @Override
    public String getTypeDescription() {
        return "A status report of FIMS plates, by Locus";
    }

    @Override
    public Options createOptions(FimsToLims fimsToLims) {
        return new PlateStatusOptions(fimsToLims);
    }

    @Override
    public boolean requiresFimsValues() {
        return true;
    }

    @Override
    public boolean returnsResultsAsynchronously() {
        return true;
    }

    @Override
    public ReportChart getChart(Options options, final FimsToLims fimsToLims, final ProgressListener progress) throws SQLException {
        if(!fimsToLims.getFimsConnection().storesPlateAndWellInformation()) {
            throw new SQLException("You must specify plate and well in your FIMS connection before you can use this report.  Please see the connection dialog.");
        }
        if(!fimsToLims.getFimsConnection().storesPlateAndWellInformation()) {
            return new ReportChart(){
                @Override
                public JPanel getPanel() {
                    return new GPanel();
                }
            };
        }

        final String plateColName = FimsToLims.getSqlColName(fimsToLims.getFimsConnection().getPlateDocumentField().getCode(), fimsToLims.getLimsConnection().isLocal());
        String plateSql = "SELECT distinct("+ plateColName +") FROM "+FimsToLims.FIMS_VALUES_TABLE;

        String plateWhere = options.getValueAsString(PlateStatusOptions.PLATE_NAME);
        if(plateWhere.trim().length() > 0){
            plateSql += " WHERE LOWER("+plateColName+") LIKE ?";
        }
        System.out.println(plateSql);
        PreparedStatement plateNameStatement = fimsToLims.getLimsConnection().createStatement(plateSql);

        if(plateWhere.trim().length() > 0){
            plateNameStatement.setString(1, "%"+plateWhere.trim().toLowerCase()+"%");
        }

        progress.setIndeterminateProgress();
        progress.setMessage("Getting plate names matching your query");

        ResultSet plateNameResultSet = plateNameStatement.executeQuery();
        final List<String> plateNames = new ArrayList<String>();
        while(plateNameResultSet.next()) {
            plateNames.add(plateNameResultSet.getString(1));
        }

        final CompositeProgressListener composite = new CompositeProgressListener(progress, plateNames.size());


        final List<PlateStatus> plateStatus = new ArrayList<PlateStatus>();
        final List<String> loci = new ArrayList<String>();
        for(Options.OptionValue value : (List<Options.OptionValue>)options.getValue("loci")) {
            loci.add(value.getName());
        }


        final AbstractTableModel model = new AbstractTableModel(){

            public int getRowCount() {
                return plateStatus.size();
            }

            public int getColumnCount() {
                return loci.size()+1;
            }

            public Object getValueAt(int rowIndex, int columnIndex) {
                if(columnIndex == 0) {
                    return plateStatus.get(rowIndex).getPlateName();
                }
                return plateStatus.get(rowIndex).getLocusStatus(loci.get(columnIndex-1));
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                if(columnIndex == 0) {
                    return String.class;
                }
                return LocusStatus.class;
            }

            @Override
            public String getColumnName(int column) {
                if(column == 0) {
                    return "Plate Name";
                }
                return loci.get(column-1);
            }
        };


        final AtomicReference<SQLException> error = new AtomicReference<SQLException>();
        Runnable runnable = new Runnable() {
            public void run() {
                try {
                    for (int i = 0, plateNamesSize = plateNames.size(); i < plateNamesSize; i++) {
                        String plateName = plateNames.get(i);
                        PlateStatus status = new PlateStatus(plateName);

                        composite.beginSubtask("Plate " + (i + 1) + " of " + plateNames.size() + " (" + plateName + ")");
                        CompositeProgressListener innerComposite = new CompositeProgressListener(composite, loci.size());
                        for(String locus : loci) {
                            if(progress.isCanceled()) {
                                return;
                            }
                            innerComposite.beginSubtask(locus);


                            innerComposite.setMessage("Calculating PCR progress");
                            ReactionStatus pcrStatus = getReactionStatus(fimsToLims, plateColName, plateName, locus, "pcr");
                            innerComposite.setProgress(1/3.0);
                            System.out.println(plateName+" "+locus+" PCR "+pcrStatus);


                            innerComposite.setMessage("Calculating Cycle Sequencing progress");
                            ReactionStatus sequencingStatus = getReactionStatus(fimsToLims, plateColName, plateName, locus, "cyclesequencing");
                            innerComposite.setProgress(2/3.0);
                            System.out.println(plateName+" "+locus+" CycleSequencing "+sequencingStatus);

                            innerComposite.setMessage("Counting traces and sequences");
                            int numberOfTraces = getNumberOfTraces(fimsToLims, plateColName, plateName, locus);
                            int numberOfSequences = getNumberOfSequences(fimsToLims, plateColName, plateName, locus);
                            innerComposite.setProgress(1.0);
                            System.out.println(plateName+" "+locus+" traces and sequences "+numberOfTraces+", "+numberOfSequences);

                            status.addLocusStatus(locus, new LocusStatus(pcrStatus, sequencingStatus, numberOfTraces, numberOfSequences));


                        }
                        plateStatus.add(status);
                        Runnable runnable = new Runnable() {
                            public void run() {
                                model.fireTableDataChanged();
                            }
                        };
                        ThreadUtilities.invokeNowOrLater(runnable);
                    }
                    progress.setProgress(1.0);
                } catch (SQLException e) {
                    error.set(e);
                }
            }
        };

        Thread thread = new Thread(runnable);
        thread.start();

        while(plateStatus.size() == 0 && thread.isAlive()) { //wait for the first result before continuing...
            ThreadUtilities.sleep(100);
        }

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

            @Override
            protected void messWithTheTable(JTable table, TableModel model) {
                table.setRowHeight(table.getRowHeight()*4);
                table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
                DefaultTableColumnModel colModel = (DefaultTableColumnModel)table.getColumnModel();
                for(int vColIndex = 0; vColIndex < colModel.getColumnCount(); vColIndex++) {
                    TableColumn col = colModel.getColumn(vColIndex);
                    int width = 0;

                    // Get width of column header
                    TableCellRenderer renderer = col.getHeaderRenderer();
                    if (renderer == null) {
                        renderer = table.getTableHeader().getDefaultRenderer();
                    }
                    Component comp = renderer.getTableCellRendererComponent(
                        table, col.getHeaderValue(), false, false, 0, 0);
                    width = comp.getPreferredSize().width;

                    // Get maximum width of column data
                    for (int r=0; r<table.getRowCount(); r++) {
                        renderer = table.getCellRenderer(r, vColIndex);
                        comp = renderer.getTableCellRendererComponent(
                            table, table.getValueAt(r, vColIndex), false, false, r, vColIndex);
                        width = Math.max(width, comp.getPreferredSize().width);
                    }

                    // Add margin
                    width += 10;

                    // Set the width
                    col.setPreferredWidth(width);
                }
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
                        new HTMLChartExporter(getName(), model)
                };
            }
        };




    }

    private int getNumberOfTraces(FimsToLims fimsToLims, String plateColName, String plateName, String locus) throws SQLException{

        String tissueCol = fimsToLims.getTissueColumnId();
        String sql = "SELECT count(traces.id) from cyclesequencing, traces, workflow, extraction, " +FimsToLims.FIMS_VALUES_TABLE
                        +" WHERE traces.reaction = cyclesequencing.id AND cyclesequencing.workflow=workflow.id AND workflow.locus=? AND workflow.extractionId=extraction.id AND " +
                        "extraction.sampleId="+FimsToLims.FIMS_VALUES_TABLE+"."+tissueCol+" AND "+FimsToLims.FIMS_VALUES_TABLE+"."+plateColName+"=?";

        PreparedStatement statement = fimsToLims.getLimsConnection().createStatement(sql);

        statement.setString(1, locus);
        statement.setString(2, plateName);

        ResultSet resultSet = statement.executeQuery();

        while(resultSet.next()) {
            return resultSet.getInt(1);
        }
        throw new RuntimeException("The server returned 0 rows from a count request");
    }

    private int getNumberOfSequences(FimsToLims fimsToLims, String plateColName, String plateName, String locus) throws SQLException{

        String tissueCol = fimsToLims.getTissueColumnId();
        String sql = "SELECT  count(assembly.id) from assembly, workflow, extraction, " +FimsToLims.FIMS_VALUES_TABLE
                +" WHERE assembly.workflow=workflow.id AND workflow.locus=? AND workflow.extractionId=extraction.id AND " +
                "extraction.sampleId="+FimsToLims.FIMS_VALUES_TABLE+"."+tissueCol+" AND "+FimsToLims.FIMS_VALUES_TABLE+"."+plateColName+"=? AND assembly.progress='passed'";

        PreparedStatement statement = fimsToLims.getLimsConnection().createStatement(sql);

        statement.setString(1, locus);
        statement.setString(2, plateName);

        ResultSet resultSet = statement.executeQuery();

        while(resultSet.next()) {
            return resultSet.getInt(1);
        }
        throw new RuntimeException("The server returned 0 rows from a count request");
    }

    private ReactionStatus getReactionStatus(FimsToLims fimsToLims, String plateColName, String plateName, String locus, String tableName) throws SQLException {
        String tissueCol = fimsToLims.getTissueColumnId();
        String sql = "SELECT " + tableName + ".progress from " + tableName + ", workflow, extraction, " +FimsToLims.FIMS_VALUES_TABLE
                +" WHERE " + tableName + ".workflow=workflow.id AND workflow.locus=? AND workflow.extractionId=extraction.id AND " +
                "extraction.sampleId="+FimsToLims.FIMS_VALUES_TABLE+"."+tissueCol+" AND "+FimsToLims.FIMS_VALUES_TABLE+"."+plateColName+"=?";
        PreparedStatement statement = fimsToLims.getLimsConnection().createStatement(sql);
        statement.setString(1, locus);
        statement.setString(2, plateName);

        int performed = 0;
        int scored = 0;
        int passed = 0;

        ResultSet pcrResultSet = statement.executeQuery();
        while(pcrResultSet.next()) {
            performed++;
            String pcrProgress = pcrResultSet.getString(1);
            if(pcrProgress.equals("passed")) {
                scored++;
                passed++;
            }
            else if(pcrProgress.equals("failed") || pcrProgress.equals("suspect")) {
                scored++;
            }
        }

        return new ReactionStatus(performed, scored, passed);
    }

    public static String getColor(int count) {
        return count > 0 ? "#000000" : "#AA0000";
    }

    private static class PlateStatus {
        private Map<String, LocusStatus> loci = new LinkedHashMap<String, LocusStatus>();
        private String plateName;

        public String getPlateName() {
            return plateName;
        }

        private PlateStatus(String plateName) {

            this.plateName = plateName;
        }

        public void addLocusStatus(String locus, LocusStatus status) {
            loci.put(locus, status);
        }

        public Set<String> getLoci() {
            return loci.keySet();
        }

        public LocusStatus getLocusStatus(String locus) {
            return loci.get(locus);
        }
    }

    private static class LocusStatus implements Comparable{
        private ReactionStatus pcrStatus;
        private ReactionStatus cyclesequencingStatus;
        private int numberOfTraces;
        private int numberOfSequences;

        private LocusStatus(ReactionStatus pcrStatus, ReactionStatus cyclesequencingStatus, int numberOfTraces, int numberOfSequences) {
            this.pcrStatus = pcrStatus;
            this.cyclesequencingStatus = cyclesequencingStatus;
            this.numberOfTraces = numberOfTraces;
            this.numberOfSequences = numberOfSequences;
        }

        public ReactionStatus getPcrStatus() {
            return pcrStatus;
        }

        public ReactionStatus getCyclesequencingStatus() {
            return cyclesequencingStatus;
        }

        public int getNumberOfTraces() {
            return numberOfTraces;
        }

        public int getNumberOfSequences() {
            return numberOfSequences;
        }

        public String toString() {
            return "<html><b>PCR: </b> "+pcrStatus+"<br>" +
                    "<b>Cycle Sequencing: </b>"+cyclesequencingStatus+"<br>" +
                    "<font color=\""+getColor(numberOfTraces)+"\"><b>"+numberOfTraces+" Traces</b></font><br>"+
                    "<font color=\""+getColor(numberOfSequences)+"\"><b>"+numberOfSequences+" Sequences</b></font></html>";
        }

        public int compareTo(Object o) {
            if(!(o instanceof LocusStatus)) {
                return -1;
            }
            LocusStatus status = (LocusStatus)o;
            int pcrComparison = pcrStatus.compareTo(status.pcrStatus);
            if(pcrComparison != 0) {
                return pcrComparison;
            }
            int sequencingComparison = cyclesequencingStatus.compareTo(status.cyclesequencingStatus);
            if(sequencingComparison != 0) {
                return sequencingComparison;
            }
            int tracesComparison = numberOfTraces - status.numberOfTraces;
            if(tracesComparison != 0) {
                return tracesComparison;
            }
            return numberOfSequences - status.numberOfSequences;
        }
    }



    private static class ReactionStatus implements Comparable{
        private int performed = 0;
        private int scored = 0;
        private int passed = 0;

        private ReactionStatus(int performed, int scored, int passed) {
            this.performed = performed;
            this.scored = scored;
            this.passed = passed;
        }

        public int getPerformed() {
            return performed;
        }

        public String toString() {
            return "<font color=\""+getColor(performed)+"\">"+performed+" performed</font>, "+
                    "<font color=\""+getColor(1+scored-performed)+"\">"+scored+" scored</font>, "+
                    "<font color=\""+getColor(passed)+"\">"+passed+" passed</font>";
        }

        public int getScored() {
            return scored;
        }

        public int getPassed() {
            return passed;
        }

        public int compareTo(Object o) {
            if(!(o instanceof ReactionStatus)) {
                return -1;
            }
            ReactionStatus status = (ReactionStatus)o;
            int passedComparison = passed-status.passed;
            if(passedComparison != 0) {
                return passedComparison;
            }
            int scoredComparison = scored-status.scored;
            if(scoredComparison != 0) {
                return scoredComparison;
            }
            return performed-status.performed;
        }
    }
}
