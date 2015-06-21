package com.biomatters.plugins.biocode.labbench.reporting;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.plugin.GeneiousAction;
import com.biomatters.geneious.publicapi.components.*;
import com.biomatters.geneious.publicapi.utilities.GuiUtilities;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import com.biomatters.geneious.publicapi.utilities.IconUtilities;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.plugins.biocode.assembler.annotate.AnnotateUtilities;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.AbstractListComboBoxModel;
import com.biomatters.plugins.biocode.labbench.lims.FimsToLims;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import com.biomatters.plugins.biocode.labbench.reaction.*;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import org.jfree.data.general.DefaultKeyedValuesDataset;
import org.jfree.data.general.PieDataset;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.*;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.List;
import java.util.prefs.Preferences;
import java.util.concurrent.atomic.AtomicBoolean;
import java.text.DateFormat;
import java.io.File;
import java.io.IOException;

import jebl.util.ProgressListener;

/**
 * Created by IntelliJ IDEA.
 * User: Steve
 * Date: 29/01/11
 * Time: 4:12 AM
 * To change this template use File | Settings | File Templates.
 */
public class ReportGenerator {
    private ReportManager reportManager;
    Preferences PREFS = Preferences.userNodeForPackage(getClass());

    private static final Map<String, String> geneiousFieldToTableField = new HashMap<String, String>();
    private static final Set<String> workflowFields = new HashSet<String>();
    FimsToLims fimsToLims;
    private Chartable chartable;
    private Report.ReportChart currentReportChart;

    public ReportGenerator(Chartable chartable, File userDataDirectory) throws SQLException, DatabaseServiceException {
        this.chartable = chartable;
        fimsToLims = new FimsToLims(BiocodeService.getInstance());
        reportManager = new ReportManager(userDataDirectory);
    }

    static {
        workflowFields.add("workflowName");
        workflowFields.add("locus");

        geneiousFieldToTableField.put("runStatus", "progress");
        geneiousFieldToTableField.put(PCROptions.PRIMER_OPTION_ID, "prName");
        geneiousFieldToTableField.put(PCROptions.PRIMER_REVERSE_OPTION_ID, "revPrName");
    }

    private JPanel getFimsPanel() {
        final JPanel panel = new GPanel();
        if(!BiocodeService.getInstance().isLoggedIn()) {
            return panel;
        }

        final GLabel label = new GLabel(getFimsCopyLabel());
        final JButton button = new GButton("Copy Now");


        panel.add(label);
        panel.add(button);

        ActionListener updateFimsCopy = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                @SuppressWarnings("deprecation") //using deprecated method so that api version doesn't have to be upped
                final ProgressFrame frame = new ProgressFrame("Copying FIMS", "Copying FIMS records into your LIMS database", 800, true, GuiUtilities.getMainFrame());
                Runnable runnable = new Runnable() {
                    public void run() {
                        try {
                            fimsToLims.createFimsTable(frame);
                        } catch (ConnectionException e1) {
                            frame.cancel();
                            Dialogs.showMessageDialog(e1.getMessage());
                        } finally {
                            Runnable runnable = new Runnable() {
                                public void run() {
                                    label.setText(getFimsCopyLabel());
                                    panel.revalidate();
                                }
                            };
                            ThreadUtilities.invokeNowOrLater(runnable);
                        }
                    }
                };
                new Thread(runnable, "Copying FIMS data into the LIMS").start();
            }
        };
        button.addActionListener(updateFimsCopy);
        return panel;
    }

    private String getFimsCopyLabel() {
        if(fimsToLims.limsHasFimsValues()) {
            Date date = fimsToLims.getDateLastCopied();
            return "You last updated the copy of FIMS in your LIMS on "+ DateFormat.getDateInstance().format(date);
        }
        else {
            return "You need to copy your FIMS data to the LIMS database in order to make reports based on FIMS fields";
        }
    }

    public static List<DocumentField> getFieldValues(FimsToLims fimsToLims) {
        List<DocumentField> fields = new ArrayList<DocumentField>();
        List<DocumentField> limsSearchFields = new ArrayList<DocumentField>(LIMSConnection.getSearchAttributes());
        limsSearchFields.remove(LIMSConnection.PLATE_TYPE_FIELD);
        limsSearchFields.remove(LIMSConnection.PLATE_DATE_FIELD);
        limsSearchFields.remove(LIMSConnection.PLATE_NAME_FIELD);
        fields.addAll(limsSearchFields);
        if(fimsToLims.limsHasFimsValues()) {
            fields.addAll(fimsToLims.getFimsFields());
        }
        return fields;
    }

    public List<Report> getNewReports() {
        return Arrays.asList(
                new ComparisonReport(fimsToLims, true),
                new ComparisonReport(fimsToLims, false),
                new AccumulationReport(fimsToLims),
                new FimsAccumulationReport(fimsToLims),
                new PieChartReport(fimsToLims),
                new PlateSearchReport(fimsToLims),
                new PlateStatusReport(fimsToLims),
                new PrimerPerformanceReport(fimsToLims)
                /*,new WorkflowReport(fimsToLims)*/);
    }

    public JPanel getReportingPanel() throws SQLException{
        final JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        final AbstractListComboBoxModel reportComboModel = new AbstractListComboBoxModel() {
            public int getSize() {
                return Math.max(1, reportManager.getReports().size());
            }

            public Object getElementAt(int index) {
                if (index < 0 || reportManager.getReports().size() == 0) {
                    return "<html><i>None...</i></html>";
                }
                return reportManager.getReports().get(index);
            }
        };
        final JComboBox reportCombo = new GComboBox(reportComboModel);
        reportCombo.setMaximumSize(reportCombo.getPreferredSize());
        reportCombo.setSelectedIndex(0);


        final Action exportAction = new GeneiousAction("Export", "Export the current view", IconUtilities.getIcons("export16.png")) {
            public void actionPerformed(ActionEvent e) {
                if(currentReportChart == null || currentReportChart.getExporters().length == 0) {
                    return;
                }
                JFileChooser chooser = new JFileChooser(PREFS.get("reportExportPath", System.getProperty("user.home")));
                chooser.setMultiSelectionEnabled(false);
                chooser.setDialogTitle("Export Report");
                chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                for(final ChartExporter exporter : currentReportChart.getExporters()) {
                    chooser.addChoosableFileFilter(new FileFilter() {
                        public String getDescription() {
                            return exporter.getFileTypeDescription()+" ("+exporter.getDefaultExtension()+")";
                        }

                        public boolean accept(File f) {
                            return f.getName().toLowerCase().endsWith("."+exporter.getDefaultExtension().toLowerCase());
                        }
                    });
                }
                chooser.showDialog(toolbar, "Export");
                if(chooser.getSelectedFile() != null) {
                    if(chooser.getSelectedFile().exists() && !Dialogs.showYesNoDialog("The file "+chooser.getSelectedFile().getName()+" exists.  Do you want to overwrite it?", "File Exists", toolbar, Dialogs.DialogIcon.QUESTION)) {
                        return;
                    }
                    PREFS.put("reportExportPath", chooser.getSelectedFile().getParent());
                    File selectedFile = chooser.getSelectedFile();
                    FileFilter fileFilter = chooser.getFileFilter();
                    ChartExporter selectedExporter = null;
                    //if the user has selected a file format explicitly
                    for(ChartExporter exporter : currentReportChart.getExporters()) {
                        if(fileFilter.getDescription().startsWith(exporter.getFileTypeDescription())) {
                            selectedExporter = exporter;
                        }
                    }
                    //if the user has manually entered the file extension
                    if(selectedExporter == null) {
                        for(ChartExporter exporter : currentReportChart.getExporters()) {
                            if(chooser.getSelectedFile().getName().toLowerCase().endsWith("."+exporter.getDefaultExtension().toLowerCase())) {
                                selectedExporter = exporter;
                            }
                        }
                    }
                    //otherwise pick the first one...
                    if(selectedExporter == null) {
                        selectedExporter = currentReportChart.getExporters()[0];
                    }
                    //append the extension if the user hasn't
                    if(!selectedFile.getName().contains(".")) {
                        selectedFile = new File(selectedFile.getParentFile(), selectedFile.getName()+"."+selectedExporter.getDefaultExtension());
                    }
                    try {
                        selectedExporter.export(selectedFile, ProgressListener.EMPTY); //todo
                    } catch (IOException e1) {
                        e1.printStackTrace(); //todo
                    }
                }
            }
        };
        exportAction.setEnabled(false);

        final GeneiousAction calculateAction = new GeneiousAction("Calculate", "Calculate and graph the selected report", IconUtilities.getIcons("graph16.png")) {
            public void actionPerformed(ActionEvent e) {
                if(reportManager.getReports().size() == 0) {
                    return;
                }
                final Report report = reportManager.getReports().get(reportCombo.getSelectedIndex());
                if(chartable != null) {
                    @SuppressWarnings("deprecation") //using deprecated method so that api version doesn't have to be upped
                    final ProgressFrame progress = new ProgressFrame("Generating Report", "Generating Report", 800, true);
                    final Report report1 = report;
                    if(!fimsToLims.limsHasFimsValues() && report.requiresFimsValues()) {
                        setReportChart(new NoFimsInLimsReportChart("You must copy your FIMS data into the LIMS before using this report.  <br><br>Click the <i>Copy Now</i> Button above."));
                        return;
                    }
                    reportCombo.setEnabled(false);
                    setEnabled(false);
                    Runnable runnable = new Runnable() {
                        public void run() {
                            try {
                                try {
                                    currentReportChart = null;
                                    final Report.ReportChart reportChart = report1.getChart(report1.getOptions(), fimsToLims, progress);
                                    if(!report.returnsResultsAsynchronously()) {
                                        progress.setProgress(1.0);
                                    }
                                    Runnable runnable = new Runnable() {
                                        public void run() {
                                            setReportChart(reportChart);
                                            //progress.setComplete();
                                        }
                                    };
                                    ThreadUtilities.invokeNowOrLater(runnable);
                                } finally {
                                    Runnable runnable = new Runnable() {
                                        public void run() {
                                            reportCombo.setEnabled(true);
                                            setEnabled(true);
                                            exportAction.setEnabled(currentReportChart != null && currentReportChart.getExporters().length > 0);
                                        }
                                    };
                                    ThreadUtilities.invokeNowOrLater(runnable);
                                }
                            } catch (SQLException e1) {
                                e1.printStackTrace();
                                BiocodeUtilities.displayExceptionDialog("Error creating Chart", "<html>There has been an " +
                                        "error creating your report:<br><br>"+e1.getMessage()+"<br><br><b>If you believe " +
                                        "that this is a bug, please click <i>Show Details</i> below, and send the text along " +
                                        "with a screenshot of your report options to biocode.lims@gmail.com.</b></html>", e1, reportCombo);
                                setReportPanel(null);
                                progress.setComplete();
                            } catch(Throwable e1) {
                                progress.setComplete();
                                throw new RuntimeException(e1);
                            }
                            finally {
                                //progress.setComplete();
                            }
                        }
                    };
                    new Thread(runnable, "Generating LIMS report").start();



                }
            }
        };


        final Action editAction = new GeneiousAction("View/Edit", "View or edit the selected report", IconUtilities.getIcons("edit16.png", "edit24.png")){
            public void actionPerformed(ActionEvent e) {
                int selectedIndex = reportCombo.getSelectedIndex();
                if(reportManager.getReports().size() == 0 || selectedIndex < 0 || selectedIndex >= reportManager.getReports().size()) {
                    return;
                }
                Report selectedReport = reportManager.getReports().get(reportCombo.getSelectedIndex());
                Report report = getReport(selectedReport.getName(), selectedReport.getTypeName(), selectedReport.getOptions(), toolbar);
                if(report != null) {
                    try {
                        reportManager.setReport(selectedIndex, report);
                    } catch (IOException e1) {
                        BiocodeUtilities.displayExceptionDialog("Error saving report", "Geneious could not save your reports to disk: "+e1.getMessage(), e1, toolbar);
                    }
                }
                reportComboModel.fireContentsChanged();
                reportCombo.setSelectedIndex(selectedIndex);
            }
        };

        final Action removeAction = new GeneiousAction("Remove", "Remove a report", IconUtilities.getIcons("remove24.png")){
            public void actionPerformed(ActionEvent e) {
                int selectedIndex = reportCombo.getSelectedIndex();
                try {
                    reportManager.removeReport(selectedIndex);
                    if(reportManager.getReports().size() == 0) {
                        editAction.setEnabled(false);
                        calculateAction.setEnabled(false);
                        setEnabled(false);
                    }
                } catch (IOException e1) {
                    BiocodeUtilities.displayExceptionDialog("Error saving report", "Geneious could not save your reports to disk: "+e1.getMessage(), e1, toolbar);
                }
                reportComboModel.fireContentsChanged();
                reportCombo.setSelectedIndex(Math.max(selectedIndex-1, 0));
            }
        };

        final Action addAction = new GeneiousAction("Add", "Add a new report", IconUtilities.getIcons("add24.png")) {
            public void actionPerformed(ActionEvent e) {
                Report newReport = getReport("", null, null, toolbar);
                if(newReport != null) {
                    editAction.setEnabled(true);
                    removeAction.setEnabled(true);
                    calculateAction.setEnabled(true);
                    try {
                        reportManager.addReport(newReport);
                    } catch (IOException e1) {
                        BiocodeUtilities.displayExceptionDialog("Error saving report", "Geneious could not save your reports to disk: "+e1.getMessage(), e1, toolbar);
                    }
                    reportComboModel.fireContentsChanged();
                    reportCombo.setSelectedIndex(reportComboModel.getSize()-1);
                }
            }
        };

        boolean removeEditEnabled = reportManager.getReports().size() > 0;
        editAction.setEnabled(removeEditEnabled);
        removeAction.setEnabled(removeEditEnabled);
        calculateAction.setEnabled(removeEditEnabled);

        toolbar.add(Box.createHorizontalGlue());

        toolbar.add(addAction);
        toolbar.add(removeAction);
        toolbar.add(editAction);
        toolbar.addSeparator();
        toolbar.add(reportCombo);
        toolbar.add(calculateAction);
        toolbar.add(exportAction);

        toolbar.add(Box.createHorizontalGlue());
        for(Component component : toolbar.getComponents()) {
            if(component instanceof JButton) {
                ((JButton)component).putClientProperty("hideActionText", Boolean.FALSE);
                ((JButton)component).setHorizontalTextPosition(JButton.RIGHT);
            }
        }
        final JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(getFimsPanel(), BorderLayout.NORTH);
        topPanel.add(toolbar, BorderLayout.SOUTH);

        return topPanel;
    }

    private Report getReport(String initialName, String initialType, Options initialOptions, Component dialogOwner) {
        Options reportOptions = new Options(ReportGenerator.class);
        reportOptions.addStringOption("name", "Report Name", initialName);
        for(Report report : getNewReports()) {
            Options options;
            if(fimsToLims.limsHasFimsValues() || !report.requiresFimsValues()) {
                options = report.getOptions();
            }
            else {
                options = new Options(this.getClass());
                options.addLabel("You must copy your FIMS data into the LIMS before using this report.");
            }
            String typeName = report.getTypeName();
            if(typeName.equals(initialType)) {
                options.valuesFromXML(initialOptions.valuesToXML("options"));
            }
            reportOptions.addChildOptions(typeName, report.getTypeName(), report.getTypeDescription(), options);
        }
        Options.Option chooser = reportOptions.addChildOptionsPageChooser("reportType", "ReportType", Collections.EMPTY_LIST, Options.PageChooserType.COMBO_BOX, false);
        if(initialType != null) {
            chooser.setValueFromString(initialType);
        }

        Dialogs.DialogOptions dialogOptions = new Dialogs.DialogOptions(Dialogs.OK_CANCEL, "Create Report", dialogOwner);
        dialogOptions.setMaxWidth(Integer.MAX_VALUE);

        if(Dialogs.OK.equals(Dialogs.showDialog(dialogOptions, reportOptions.getPanel()))) {
            String reportType = reportOptions.getValueAsString("reportType");
            for(Report report : getNewReports()) {
                if(report.getTypeName().equals(reportType)) {
                    report.setName(reportOptions.getValueAsString("name"));
                    report.setOptions(reportOptions.getChildOptions().get(reportType));
                    return report;
                }
            }
        }
        return null;
    }

    private void setReportChart(Report.ReportChart reportChart) {
        this.currentReportChart = reportChart;
        if(reportChart == null) {
            setReportPanel(null);
        }
        else if(reportChart.getOptions() == null) {
            setReportPanel(reportChart.getPanel());
        }
        else {
            JPanel splitPane = new GPanel(new BorderLayout());
            splitPane.add(reportChart.getPanel(), BorderLayout.CENTER);
            JPanel optionsPanel = reportChart.getOptions().getPanel();
            optionsPanel.setMaximumSize(optionsPanel.getPreferredSize());
            GPanel holderPanel = new GPanel();
            holderPanel.setLayout(new BoxLayout(holderPanel, BoxLayout.Y_AXIS));
            holderPanel.add(optionsPanel);
            holderPanel.setBorder(new CompoundBorder(new MatteBorder(0,1,0,0,holderPanel.getBackground().darker()), new EmptyBorder(5,5,5,5)));
            splitPane.add(holderPanel, BorderLayout.EAST);
            setReportPanel(splitPane);
        }
    }

    private void setReportPanel(final JComponent chart) {
        Runnable runnable = new Runnable() {
            public void run() {
                chartable.setChartPanel(chart);
            }
        };
        ThreadUtilities.invokeNowOrLater(runnable);
    }

    private Map<String, Integer> counts = new HashMap<String, Integer>();

    public PieDataset getDataset() {
        DefaultKeyedValuesDataset dataset = new DefaultKeyedValuesDataset();
        for(Map.Entry entry : counts.entrySet()) {
            Comparable key = (Comparable) entry.getKey();
            Integer value = (Integer) entry.getValue();
            dataset.setValue(key, value);
        }
        return dataset;
    }

    public static List<Options.OptionValue> getEnumeratedFieldValues(DocumentField field) {
        List<Options.OptionValue> values = new ArrayList<Options.OptionValue>();
        if(field == null || !field.isEnumeratedField()) {
            values.add(new Options.OptionValue("none", "none"));
        }
        else {
            for(String s : field.getEnumerationValues()) {
                values.add(new Options.OptionValue(s, s));
            }
        }
        return values;
    }

    public static boolean includeField(String geneiousFieldName, boolean excludeLocus) {
        if(geneiousFieldName.equals("sampleId")) {
            return false;
        }
        if(geneiousFieldName.equals("extractionId")) {
            return false;
        }
        if(geneiousFieldName.equals("workflowId")) {
            return false;
        }
        if(geneiousFieldName.equals("notes") || geneiousFieldName.equals(AnnotateUtilities.NOTES_FIELD.getCode())) {
            return false;
        }
        if(geneiousFieldName.equals("locus") && excludeLocus) {
            return false;
        }
        if(geneiousFieldName.equals("LimsSequenceId")) {
            return false;
        }
        return true;
    }

    public static String getTableFieldName(String tableName, String geneiousFieldName) {
        String overridename = geneiousFieldToTableField.get(geneiousFieldName);
        String fieldName = overridename != null ? overridename : geneiousFieldName;
        if(fieldName.contains(".")) {
            fieldName = fieldName.substring(fieldName.indexOf(".")+1);
        }
        if(tableName.equals("extraction")) {
            if(fieldName.equals("extractionMethod")) {
                return "method";
            }
            if(fieldName.equals("parentExtraction")) {
                return "parent";
            }
            
            return fieldName;
        }
        else if(tableName.equals("cyclesequencing")) {
            if(fieldName.equals("prName")) {
                return "primerName";
            }
        }
        if(workflowFields.contains(geneiousFieldName)) {
            return "workflow."+fieldName;
        }
        if(fieldName.equals("workflowId")) {
            return "workflow";
        }

        return tableName+"."+fieldName;
    }

    public static boolean isBooleanField(DocumentField field) {
        return field.getEnumerationValues().length == 2 && ((field.getEnumerationValues()[0].toLowerCase().equals("yes") && field.getEnumerationValues()[1].toLowerCase().equals("no")) ||
        (field.getEnumerationValues()[0].toLowerCase().equals("true") && field.getEnumerationValues()[1].toLowerCase().equals("false")));
    }

    public static List<String> getDistinctValues(FimsToLims fimsToLims, String field, String table, Collection<String> loci, boolean warnIfManyValues, ProgressListener progress) throws SQLException {
        String sql;

        sql = "SELECT DISTINCT ("+field+") FROM "+table;

        boolean where = false;
        if(!table.equals("extraction") && !table.equals(FimsToLims.FIMS_VALUES_TABLE) && !table.equals("workflow")) {
            where = true;
            sql += ", workflow WHERE workflow.id = " + table + ".workflow";
        }

        if(loci != null && loci.size() > 0) {
            List<String> locusList = new ArrayList<String>();
            for(String s : loci) {
                locusList.add("workflow.locus='"+s+"'");
            }
            sql += (where ? " AND " : " WHERE ")+ "("+StringUtilities.join(" OR ", locusList)+")";
        }

        System.out.println(sql);
        ResultSet resultSet = fimsToLims.getLimsConnection().createStatement().executeQuery(sql);
        List<String> values = new ArrayList<String>();
        while(resultSet.next()) {
            if(progress.isCanceled()) {
                return null;
            }
            values.add(resultSet.getString(1));
        }
        if(values.size() > 20 && warnIfManyValues) {
            final AtomicBoolean cont = new AtomicBoolean();
            final int valueSize = values.size();
            Runnable r = new Runnable() {
                public void run() {
                    cont.set(Dialogs.showYesNoDialog("The field you have chosen has "+valueSize+" distinct values in the database.  This will result in a very large chart.  Are you sure you want to contunie?", "Large Values", null, Dialogs.DialogIcon.QUESTION));
                }
            };
            ThreadUtilities.invokeNowOrWait(r);
            if(!cont.get()) {
                values = null;
            }
        }
        return values;
    }


    public int getFieldCount(SingleFieldOptions options) {
        StringBuilder builder = new StringBuilder();
        Object value = options.getValue();
        builder.append("SELECT count(*) FROM ");
        String tableName = options.getTableName().toLowerCase();
        builder.append(tableName);
        boolean notExtraction = !tableName.equals("extraction");
        if(notExtraction) {
            builder.append(", workflow");
        }
        builder.append(" WHERE ");
        if(notExtraction) {
            builder.append(tableName+".workflow = workflow.id AND ");
        }
        builder.append(getTableFieldName(tableName, options.getFieldName()));
        if(options.isExactMatch()) {
            builder.append("=?");
        }
        else {
            builder.append(" LIKE ?");
        }
        try {
            PreparedStatement statement = BiocodeService.getInstance().getActiveLIMSConnection().createStatement(builder.toString());
            System.out.print(builder.toString()+" ");
            System.out.print(value+" ");
            setSqlParam(statement, 1, value);
            ResultSet resultSet = statement.executeQuery();
            resultSet.next();
            int result = resultSet.getInt(1);
            return result;
        } catch (SQLException e) {
            e.printStackTrace();
            assert false : e.getMessage();
        } catch (DatabaseServiceException e) {
            e.printStackTrace();
            assert false : e.getMessage();
        }
        return 0;
    }

    private static void setSqlParam(PreparedStatement statement, int paramIndex, Object param) throws SQLException{
        if(param instanceof Integer) {
            statement.setInt(paramIndex, (Integer)param);
        }
        else if(param instanceof Double) {
            statement.setDouble(paramIndex, (Double)param);
        }
        else if(param instanceof Date) {
            statement.setDate(paramIndex, new java.sql.Date(((Date)param).getTime()));
        }
        else if(param instanceof Boolean) {
            statement.setObject(paramIndex, param);
        }
        else {
            statement.setString(paramIndex, param.toString());
        }
    }

    static DocumentField getField(String reactionType, String fieldCode) {
        if(reactionType == null) {

        }

        if(reactionType.equals("assembly")) {
            for(DocumentField field : Arrays.asList(
                    LIMSConnection.ASSEMBLY_TECHNICIAN,
                    LIMSConnection.SEQUENCE_ID,
                    LIMSConnection.EDIT_RECORD,
                    LIMSConnection.SEQUENCE_PROGRESS,
                    LIMSConnection.SEQUENCE_SUBMISSION_PROGRESS
            )) {
                if(field.getCode().equals(fieldCode)) {
                    return field;
                }
            }
            return null;
        }

        Reaction.Type type = null;
        try {
            type = Reaction.Type.valueOf(reactionType);
            Reaction reaction = getNewReaction(type);
            return reaction.getDisplayableField(fieldCode);
        } catch (IllegalArgumentException e) {
            for(DocumentField field : sequenceFields) {
                if(field.getCode().equals(fieldCode)) {
                    return field;
                }
            }
        }
        assert false;
        return null;
    }

    private static List<DocumentField> sequenceFields = new ArrayList<DocumentField>();
    static {
        sequenceFields.add(LIMSConnection.SEQUENCE_SUBMISSION_PROGRESS);
        sequenceFields.add(LIMSConnection.SEQUENCE_PROGRESS);
        sequenceFields.add(LIMSConnection.EDIT_RECORD);
    }

    public static List<Options.OptionValue> getOptionValues(Iterable<DocumentField> documentFields) {
        List<Options.OptionValue> optionValues = new ArrayList<Options.OptionValue> ();
        Set<String> codes = new LinkedHashSet<String>();

        for(DocumentField field : documentFields) {
            if(codes.add(field.getCode())) {
                optionValues.add(new Options.OptionValue(field.getCode(), field.getName()));
            }
        }
        return optionValues;
    }

    /**
     *
     * @param reactionType the reaction type containing the field in question
     * @param fieldCode the fieldcode of the field in question
     * @return null if the field is not enumerated
     */
    public static List<Options.OptionValue> getEnumeratedFieldValues(String reactionType, String fieldCode) {
        List<DocumentField> displayableFields;
        try {
            Reaction.Type type = Reaction.Type.valueOf(reactionType);
            Reaction reaction = getNewReaction(type);
            displayableFields = reaction.getDisplayableFields();

        } catch (IllegalArgumentException e) {
            displayableFields = sequenceFields;
        }
        for(DocumentField f : displayableFields){
            if(f.equals(Reaction.GEL_IMAGE_DOCUMENT_FIELD)) {
                continue;
            }
            if(f.getCode().equals(fieldCode) && f.isEnumeratedField()) {
                List<Options.OptionValue> values = new ArrayList<Options.OptionValue>();
                for(String s : f.getEnumerationValues()) {
                    values.add(new Options.OptionValue(s,s));
                }
                return values;
            }
        }
        return null;
    }

    public static List<Options.OptionValue> getPossibleFields(String reactionType, boolean includeAllFields, boolean includeAllTerm) {
        List<Options.OptionValue> fields = new ArrayList<Options.OptionValue>();
        List<DocumentField> displayableFields;
        try {
            Reaction.Type type = Reaction.Type.valueOf(reactionType);
            Reaction reaction = getNewReaction(type);
            displayableFields = reaction.getDisplayableFields();

        } catch (IllegalArgumentException e) {
            displayableFields = sequenceFields;
        }
        for(DocumentField f : displayableFields){
            if(f.equals(Reaction.GEL_IMAGE_DOCUMENT_FIELD)) {
                continue;
            }
            if(!includeField(f.getCode(), includeAllFields)) {
                continue;
            }
            fields.add(new Options.OptionValue(f.getCode(), f.getName()));
        }
        if(fields.size() == 0) {
            fields.add(new Options.OptionValue("none", "None..."));
        }
        else if(includeAllTerm){
            fields.add(0, new Options.OptionValue("nofield", "All "+reactionType+" reactions"));
        }
        return fields;
    }

    private static Reaction getNewReaction(Reaction.Type type) {
        Reaction reaction;
        switch(type) {
            case Extraction:
                reaction = new ExtractionReaction();
                break;
            case PCR:
                reaction = new PCRReaction();
                break;
            default:
                reaction = new CycleSequencingReaction();
                break;
        }
        return reaction;
    }

    public JPanel getChart(ResultSet resultSet){
        return new JPanel();
    }

    public void update() {
        reportManager.loadReportsFromDisk(fimsToLims);
    }
}
