package com.biomatters.plugins.biocode.labbench.reporting;

import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.reaction.*;
import org.jfree.data.DefaultKeyedValues;
import org.jfree.data.general.DefaultKeyedValuesDataset;
import org.jfree.data.general.PieDataset;
import org.jfree.data.xml.PieDatasetHandler;
import org.virion.jam.util.SimpleListener;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: Steve
 * Date: 29/01/11
 * Time: 4:12 AM
 * To change this template use File | Settings | File Templates.
 */
public class ReportGenerator {
    private Options reportingOptions;
    private SimpleListener chartChangedListener;

    private static final Map<String, String> geneiousFieldToTableField = new HashMap<String, String>();
    private static final Set<String> workflowFields = new HashSet<String>();

    static {
        workflowFields.add("workflowName");
        workflowFields.add("locus");

        geneiousFieldToTableField.put("runStatus", "progress");
        geneiousFieldToTableField.put(PCROptions.PRIMER_OPTION_ID, "prName");
        geneiousFieldToTableField.put(PCROptions.PRIMER_REVERSE_OPTION_ID, "revPrName");
    }



    public JPanel getReportingPanel() {
        reportingOptions = new Options(this.getClass());

        SingleFieldOptions singleFieldOptions = new SingleFieldOptions(this.getClass());
        final Options.MultipleOptions multiOptions = reportingOptions.addMultipleOptions("fields", singleFieldOptions, false);
        Options.ButtonOption buttonOption = reportingOptions.addButtonOption("button", "", "Calculate");
        buttonOption.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                List<Options> values = multiOptions.getValues();
                counts.clear();
                for(Options option : values) {
                    SingleFieldOptions sfOption = (SingleFieldOptions)option;
                    counts.put(sfOption.getTableName()+" "+sfOption.getFieldLabel()+" "+sfOption.getValue(), getFieldCount(sfOption));
                    if(chartChangedListener != null)
                        chartChangedListener.objectChanged();
                }
            }
        });

        return reportingOptions.getPanel();
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

    public void setChartChangedListener(SimpleListener listener) {
        chartChangedListener = listener;
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
        Reaction.Type type = Reaction.Type.valueOf(reactionType);
        Reaction reaction = getNewReaction(type);
        return reaction.getDisplayableField(fieldCode);
    }

    public static List<Options.OptionValue> getPossibleFields(String reactionType) {
        List<Options.OptionValue> fields = new ArrayList<Options.OptionValue>();
        try {
            Reaction.Type type = Reaction.Type.valueOf(reactionType);
            Reaction reaction = getNewReaction(type);
            List<DocumentField> displayableFields = reaction.getDisplayableFields();
            for(DocumentField f : displayableFields){
                if(f.equals(Reaction.GEL_IMAGE_DOCUMENT_FIELD)) {
                    continue;
                }
                fields.add(new Options.OptionValue(f.getCode(), f.getName()));
            }
        } catch (IllegalArgumentException e) {
            //must be a sequence (which has no reaction type)
            //todo
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




}
