package com.biomatters.plugins.biocode.labbench.reporting;

import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.components.*;
import com.biomatters.geneious.publicapi.utilities.GuiUtilities;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import com.biomatters.plugins.biocode.labbench.reaction.*;
import org.jfree.data.general.DefaultKeyedValuesDataset;
import org.jfree.data.general.PieDataset;
import org.jfree.chart.ChartPanel;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.*;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.text.DateFormat;

/**
 * Created by IntelliJ IDEA.
 * User: Steve
 * Date: 29/01/11
 * Time: 4:12 AM
 * To change this template use File | Settings | File Templates.
 */
public class ReportGenerator {
    private Options reportingOptions;

    private static final Map<String, String> geneiousFieldToTableField = new HashMap<String, String>();
    private static final Set<String> workflowFields = new HashSet<String>();
    FimsToLims fimsToLims;
    private Chartable chartable;

    public ReportGenerator(Chartable chartable) throws SQLException{
        this.chartable = chartable;
        fimsToLims = new FimsToLims(BiocodeService.getInstance().getActiveFIMSConnection(), BiocodeService.getInstance().getActiveLIMSConnection());
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

        try {
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
        }
        catch(SQLException ex) {
            ex.printStackTrace();
            //todo: properly
            JLabel label = new GLabel(ex.getMessage());
            panel.add(label);
        }
        return panel;
    }

    public Report[] getReports() {
        return new Report[] {new PieChartReport(), new ComparisonReport(), new AccumulationReport()};
    }

    public JPanel getReportingPanel() throws SQLException{
        reportingOptions = new Options(this.getClass());

        final Report[] reports = getReports();
        for(Report report : reports) {
            reportingOptions.addChildOptions(report.getName(), report.getName(), "", report.getOptions(fimsToLims));
        }
        final Options.ComboBoxOption<Options.OptionValue> reportChooser = (Options.ComboBoxOption)reportingOptions.addChildOptionsPageChooser("report", "Report Type", Collections.EMPTY_LIST, Options.PageChooserType.COMBO_BOX, false);

        Options.ButtonOption buttonOption = reportingOptions.addButtonOption("button", "", "Calculate");
        buttonOption.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                final Options.OptionValue reportValue = reportChooser.getValue();
                Report report = null;
                for(Report r : reports) {
                    if(r.getName().equals(reportValue.getName())) {
                        report = r;
                    }
                }
                if(report == null) {
                    throw new RuntimeException("Could not find a report called "+reportValue.getName());
                }
                if(chartable != null) {
                    final ProgressFrame progress = new ProgressFrame("Generating Report", "Generating Report", 1000, false);
                    final Report report1 = report;
                    Runnable runnable = new Runnable() {
                        public void run() {
                            try {
                                final Report.ReportChart reportChart = report1.getChart(reportingOptions.getChildOptions().get(reportValue.getName()), fimsToLims, progress);
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
                                            holderPanel.setBorder(new CompoundBorder(new LineBorder(holderPanel.getBackground().darker()), new EmptyBorder(5,5,5,5)));
                                            splitPane.add(holderPanel, BorderLayout.EAST);
                                            setReportPanel(splitPane);
                                        }
                                        progress.setComplete();

                                    }
                                };
                                ThreadUtilities.invokeNowOrLater(runnable);
                            } catch (SQLException e1) {
                                e1.printStackTrace();
                                Dialogs.showMessageDialog(e1.getMessage()); //todo: add stacktrace
                                setReportPanel(null);
                            } finally {
                                progress.setComplete();
                            }
                        }
                    };
                    new Thread(runnable, "Generating LIMS report").start();



                }
            }
        });

        GPanel panel = new GPanel(new BorderLayout());
        panel.add(reportingOptions.getPanel(), BorderLayout.CENTER);
        final JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(getFimsPanel(), BorderLayout.CENTER);
        panel.add(topPanel, BorderLayout.NORTH);

        return panel;
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

    public static String getTableFieldName(String tableName, String geneiousFieldName) {
        String overridename = geneiousFieldToTableField.get(geneiousFieldName);
        String fieldName = overridename != null ? overridename : geneiousFieldName;
        if(fieldName.contains(".")) {
            fieldName = fieldName.substring(fieldName.indexOf(".")+1);
        }
        if(tableName.equals("extraction")) {
            return fieldName;
        }
        if(workflowFields.contains(geneiousFieldName)) {
            return "workflow."+fieldName;
        }
        return tableName+"."+fieldName;
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
        sequenceFields.add(LIMSConnection.SEQUENCE_ID);
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

    public static List<Options.OptionValue> getPossibleFields(String reactionType, boolean onlyEnumerated) {
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
            if(onlyEnumerated && !f.isEnumeratedField()) {
                continue;
            }
            fields.add(new Options.OptionValue(f.getCode(), f.getName()));
        }
        if(fields.size() == 0) {
            fields.add(new Options.OptionValue("none", "None..."));
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


    public static DocumentField getFimsField(String name) throws SQLException{
        //todo: use the proper fimsToLims
        List<DocumentField> fimsFields = new FimsToLims(BiocodeService.getInstance().getActiveFIMSConnection(), BiocodeService.getInstance().getActiveLIMSConnection()).getFimsFields();
        for(DocumentField f : fimsFields) {
            if(f.getCode().equals(name)) {
                return f;
            }
        }
        return null;
    }

    public static DocumentField getLimsField(String name) {
        List<DocumentField> limsFields = LIMSConnection.getSearchAttributes();
        for(DocumentField field : limsFields) {
            if(field.getCode().equals(name)) {
                return field;
            }
        }
        return null;
    }

    public static DocumentField getFimsOrLimsField(String name) throws SQLException{
        DocumentField fimsField = getFimsField(name);
        if(fimsField != null) {
            return fimsField;
        }
        return getLimsField(name);
    }
}
