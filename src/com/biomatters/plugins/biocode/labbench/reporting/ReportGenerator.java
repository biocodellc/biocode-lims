package com.biomatters.plugins.biocode.labbench.reporting;

import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.plugin.GeneiousAction;
import com.biomatters.geneious.publicapi.components.*;
import com.biomatters.geneious.publicapi.utilities.GuiUtilities;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import com.biomatters.geneious.publicapi.utilities.IconUtilities;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.AbstractListComboBoxModel;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import com.biomatters.plugins.biocode.labbench.reaction.*;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import org.jfree.data.general.DefaultKeyedValuesDataset;
import org.jfree.data.general.PieDataset;

import javax.swing.*;
import javax.swing.border.LineBorder;
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

    private static final Map<String, String> geneiousFieldToTableField = new HashMap<String, String>();
    private static final Set<String> workflowFields = new HashSet<String>();
    FimsToLims fimsToLims;
    private Chartable chartable;

    public ReportGenerator(Chartable chartable, File userDataDirectory) throws SQLException{
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

        ActionListener updateFimsCopy = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                final ProgressFrame frame = new ProgressFrame("Copying FIMS", "Copying FIMS records into your LIMS database", 1000, true, GuiUtilities.getMainFrame());
                Runnable runnable = new Runnable() {
                    public void run() {
                        try {
                            fimsToLims.createFimsTable(frame);
                            Runnable runnable = new Runnable() {
                                public void run() {
                                    panel.removeAll();
                                    panel.add(getFimsPanel());
                                }
                            };
                            ThreadUtilities.invokeNowOrLater(runnable);
                        } catch (ConnectionException e1) {
                            frame.cancel();
                            Dialogs.showMessageDialog(e1.getMessage());
                        }
                    }
                };
                new Thread(runnable).start();
            }
        };
        if(fimsToLims.limsHasFimsValues()) {
            Date date = fimsToLims.getDateLastCopied();
            GLabel label = new GLabel("You last updated the copy of FIMS in your LIMS on "+ DateFormat.getDateInstance().format(date));
            JButton button = new GButton("Copy Now");

            button.addActionListener(updateFimsCopy);
            panel.add(label);
            panel.add(button);
        }
        else {
            JLabel label = new GLabel("You need to copy your FIMS data to the LIMS database in order to make reports based on FIMS fields");
            JButton button = new GButton("Copy Now");

            button.addActionListener(updateFimsCopy);
            panel.add(label);
            panel.add(button);
        }
        return panel;
    }

    public List<Report> getNewReports() {
        return Arrays.asList(
                new PieChartReport(fimsToLims),
                new ComparisonReport(fimsToLims),
                new AccumulationReport(fimsToLims),
                new FimsAccumulationReport(fimsToLims)/*,
                new WorkflowReport(fimsToLims)*/);
    }

    public JPanel getReportingPanel() throws SQLException{
        final JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        final AbstractListComboBoxModel reportComboModel = new AbstractListComboBoxModel() {
            public int getSize() {
                return Math.max(1, reportManager.getReports().size());
            }

            public Object getElementAt(int index) {
                if (reportManager.getReports().size() == 0) {
                    return "<html><i>None...</i></html>";
                }
                return reportManager.getReports().get(index);
            }
        };
        final JComboBox reportCombo = new GComboBox(reportComboModel);
        reportCombo.setMaximumSize(reportCombo.getPreferredSize());
        reportCombo.setSelectedIndex(0);
        final GeneiousAction calculateAction = new GeneiousAction("Calculate", "Calculate and graph the selected report", IconUtilities.getIcons("graph16.png")) {
            public void actionPerformed(ActionEvent e) {
                if(reportManager.getReports().size() == 0) {
                    return;
                }
                final Report report = reportManager.getReports().get(reportCombo.getSelectedIndex());
                if(chartable != null) {
                    final ProgressFrame progress = new ProgressFrame("Generating Report", "Generating Report", 1000, false);
                    final Report report1 = report;
                    reportCombo.setEnabled(false);
                    setEnabled(false);
                    Runnable runnable = new Runnable() {
                        public void run() {
                            try {
                                try {
                                    final Report.ReportChart reportChart = report1.getChart(report1.getOptions(), fimsToLims, progress);
                                    Runnable runnable = new Runnable() {
                                        public void run() {
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
                                            progress.setComplete();

                                        }
                                    };
                                    ThreadUtilities.invokeNowOrLater(runnable);
                                } finally {
                                    Runnable runnable = new Runnable() {
                                        public void run() {
                                            reportCombo.setEnabled(true);
                                            setEnabled(true);
                                        }
                                    };
                                    ThreadUtilities.invokeNowOrLater(runnable);
                                }
                            } catch (SQLException e1) {
                                e1.printStackTrace();
                                BiocodeUtilities.displayExceptionDialog(e1);
                                setReportPanel(null);
                            } finally {
                                progress.setComplete();
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
                Options reportOptions = new Options(ReportGenerator.class);
                reportOptions.addStringOption("name", "Report Name", "");
                for(Report report : getNewReports()) {
                    reportOptions.addChildOptions(report.getTypeName(), report.getTypeName(), report.getTypeDescription(),report.getOptions());
                }
                reportOptions.addChildOptionsPageChooser("reportType", "ReportType", Collections.EMPTY_LIST, Options.PageChooserType.COMBO_BOX, false);

                Dialogs.DialogOptions dialogOptions = new Dialogs.DialogOptions(Dialogs.OK_CANCEL, "Create Report", toolbar);
                dialogOptions.setMaxWidth(Integer.MAX_VALUE);
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

        toolbar.add(Box.createHorizontalGlue());
        for(Component component : toolbar.getComponents()) {
            if(component instanceof JButton) {
                ((JButton)component).setHideActionText(false);
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
            Options options = report.getOptions();
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
        if(geneiousFieldName.equals("notes")) {
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

    public static List<String> getDistinctValues(FimsToLims fimsToLims, String field, String table, String locus, ProgressListener progress) throws SQLException {
        String sql;

        sql = "SELECT DISTINCT ("+field+") FROM "+table;

        boolean where = false;
        if(!table.equals("extraction") && !table.equals(FimsToLims.FIMS_VALUES_TABLE) && !table.equals("workflow")) {
            where = true;
            sql += ", workflow WHERE workflow.id = " + table + ".workflow";
        }

        if(locus != null) {
            sql += (where ? " AND " : " WHERE ")+"workflow.locus='"+locus+"'";
        }

        System.out.println(sql);
        ResultSet resultSet = fimsToLims.getLimsConnection().getConnection().createStatement().executeQuery(sql);
        List<String> values = new ArrayList<String>();
        while(resultSet.next()) {
            if(progress.isCanceled()) {
                return null;
            }
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
            PreparedStatement statement = BiocodeService.getInstance().getActiveLIMSConnection().getConnection().prepareStatement(builder.toString());
            System.out.print(builder.toString()+" ");
            System.out.print(value+" ");
            setSqlParam(statement, 1, value);
            ResultSet resultSet = statement.executeQuery();
            resultSet.next();
            int result = resultSet.getInt(1);
            System.out.println(result);
            return result;
        } catch (SQLException e) {
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

        for(DocumentField field : documentFields) {
            optionValues.add(new Options.OptionValue(field.getCode(), field.getName()));
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

    public static List<Options.OptionValue> getPossibleFields(String reactionType, boolean includeAll) {
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
            if(!includeField(f.getCode(), includeAll)) {
                continue;
            }
            fields.add(new Options.OptionValue(f.getCode(), f.getName()));
        }
        if(fields.size() == 0) {
            fields.add(new Options.OptionValue("none", "None..."));
        }
        else if(includeAll){
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
