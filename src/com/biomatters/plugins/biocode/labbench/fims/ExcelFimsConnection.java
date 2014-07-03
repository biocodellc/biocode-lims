package com.biomatters.plugins.biocode.labbench.fims;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.databaseservice.*;
import com.biomatters.geneious.publicapi.documents.Condition;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.plugins.biocode.BiocodePlugin;
import com.biomatters.plugins.biocode.XmlUtilities;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.FimsSample;
import com.biomatters.plugins.biocode.labbench.reaction.CycleSequencingReaction;
import com.biomatters.plugins.biocode.labbench.reaction.ExtractionReaction;
import com.biomatters.plugins.biocode.labbench.reaction.PCRReaction;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;
import jxl.Cell;
import jxl.Sheet;
import jxl.Workbook;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.List;

/**
 * @author steve
 * @version $Id: 12/05/2009 8:00:41 AM steve $
 */
public class ExcelFimsConnection extends TableFimsConnection{

    private Workbook workbook;
    private File excelFile;
    private List<String> columnNames;

    public String getName() {
        return "excel";
    }

    public String getDescription() {
        return  "Read field information from an excel worksheet";
    }

    public String getLabel() {
        return "Excel";
    }

    public TableFimsConnectionOptions _getConnectionOptions() {
        return new ExcelFimsConnectionOptions();
    }


    static void handleCorruptedExcelFile(Component owner, Exception e) {
        StringWriter stacktrace = new StringWriter();
        e.printStackTrace(new PrintWriter(stacktrace));
        Dialogs.DialogOptions dialogOptions = new Dialogs.DialogOptions(Dialogs.OK_ONLY, "Could not read Excel file", owner, Dialogs.DialogIcon.WARNING);
        dialogOptions.setMoreOptionsButtonText("Show details...", "Hide details...");
        Dialogs.showMoreOptionsDialog(dialogOptions,
                        "Geneious could not read your EXCEL file.  It is possible that the file is corrupted, or the wrong format.  Geneious only supports EXCEL 97-2003 compatible workbooks.  \n\nIf you believe this is an error, please click the details button below, and email the information to "+ new BiocodePlugin().getEmailAddressForCrashes()+".",
                stacktrace.toString());
    }

    private static int parseInt(String number, String errorMessage) throws ConnectionException {
        try {
            return Integer.parseInt(number);
        } catch (NumberFormatException e) {
            throw new ConnectionException(errorMessage);
        }
    }

    public void _connect(TableFimsConnectionOptions optionsa) throws ConnectionException {
        ExcelFimsConnectionOptions options = (ExcelFimsConnectionOptions)optionsa;
        String excelFileLocation = options.getChildOptions().get(TableFimsConnectionOptions.CONNECTION_OPTIONS_KEY).getValueAsString("excelFile");
        if(excelFileLocation.length() == 0) {
            throw new ConnectionException("You must specify an Excel file");
        }
        excelFile = new File(excelFileLocation);
        if(!excelFile.exists()) {
            throw new ConnectionException("Cannot find the file "+ excelFile.getAbsolutePath());
        }

        Set<String> columnNamesSet = new LinkedHashSet<String>();
        columnNames = new ArrayList<String>();

        //noinspection CatchGenericClass
        try {
            workbook = Workbook.getWorkbook(excelFile);
            Sheet sheet = workbook.getSheet(0);
            Cell[] rows = sheet.getRow(0);
            int tissueIdColumnIndex = 0;
            String tissueColumnName = options.getTissueColumn();
            while (tissueIdColumnIndex < rows.length && !rows[tissueIdColumnIndex].getContents().equalsIgnoreCase(tissueColumnName)) {
                tissueIdColumnIndex++;
            }
            if (tissueIdColumnIndex == rows.length) {
                throw new ConnectionException(null, "Invalid spreadsheet: Tissue id column (" + tissueColumnName + ") was not found");
            }
            Cell[] keys = sheet.getColumn(tissueIdColumnIndex);
            HashSet<String> keySet = new HashSet<String>();
            for (Cell c : keys) {
                if (keySet.contains(c.getContents())) {
                    throw new ConnectionException(null, "Invalid spreadsheet. Multiple rows with same tissue id: " + c.getContents());
                }
                keySet.add(c.getContents());
            }
            for(int i=0; i < sheet.getColumns(); i++) {
                Cell cell = sheet.getCell(i,0);
                String cellContents = cell.getContents();
                if(cellContents.length() > 0) {
                    columnNames.add(cellContents);
                    if(!columnNamesSet.add(XmlUtilities.encodeXMLChars(cellContents).toLowerCase())) {
                        throw new ConnectionException("You have more than one column with the name \""+cellContents+"\" in your spreadsheet.  Please make sure that all columns have unique names");
                    }
                }
            }
        } catch(IOException e) {
            Dialogs.showMessageDialog("Geneious could not read your excel file: "+e.getMessage(), "Could not read Excel file", null, Dialogs.DialogIcon.WARNING);
            return;
        } catch(Exception e) {
            if (e instanceof ConnectionException) {
                throw (ConnectionException)e;
            }
            handleCorruptedExcelFile(null, e);
            throw ConnectionException.NO_DIALOG;
        }
    }

    public List<DocumentField> getTableColumns() throws IOException {
        Set<String> protectedCodes = getSystemCodes();
        List<DocumentField> results = new ArrayList<DocumentField>();
        for (int i = 0, cellValuesSize = columnNames.size(); i < cellValuesSize; i++) {
            String cellContents = columnNames.get(i);
            String fieldName = XmlUtilities.encodeXMLChars(cellContents);
            String name = fieldName;
            String code = fieldName.toLowerCase();
            if(protectedCodes.contains(code)) {
                code = CODE_PREFIX + code;
                name = name + " (FIMS)";
            }
            DocumentField field = new DocumentField(name, fieldName, code, String.class, true, false);
            results.add(field);
        }
        return results;
    }

    private static Set<String> getSystemCodes() {
        Set<String> codes = new HashSet<String>();
        List<Reaction<? extends Reaction>> reactions = new ArrayList<Reaction<? extends Reaction>>();
        reactions.add(new ExtractionReaction());
        reactions.add(new PCRReaction());
        reactions.add(new CycleSequencingReaction());
        for (Reaction<? extends Reaction> reaction : reactions) {
            for (DocumentField documentField : reaction.getDisplayableFields()) {
                codes.add(documentField.getCode());
            }
        }
        return codes;
    }

    public void _disconnect() {
        excelFile = null;
        if(workbook != null) {
            workbook.close();
            workbook = null;
        }
    }

    public int getTotalNumberOfSamples() throws ConnectionException {
        Sheet sheet = workbook.getSheet(0);
        return sheet.getRows();
    }
    
    private int getTableIndex(DocumentField documentField) {
        String name = documentField.getCode();
        name = name.replace(TableFimsConnection.CODE_PREFIX, "");
        for (int i = 0, cellValuesSize = columnNames.size(); i < cellValuesSize; i++) {
            String cellContents = columnNames.get(i);
            if(XmlUtilities.encodeXMLChars(cellContents).equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    private List<Integer> getListOfRowsMatchingQuery(Query query) throws ConnectionException {
        CompoundSearchQuery.Operator operator;
        List<AdvancedSearchQueryTerm> queries;

        //prepare the fields
        if(query instanceof AdvancedSearchQueryTerm) {
            operator = CompoundSearchQuery.Operator.OR;
            queries = new ArrayList<AdvancedSearchQueryTerm>();
            queries.add((AdvancedSearchQueryTerm)query);
        }
        else if(query instanceof BasicSearchQuery) {
            operator = CompoundSearchQuery.Operator.OR;
            BasicSearchQuery basicQuery = (BasicSearchQuery)query;
            queries = new ArrayList<AdvancedSearchQueryTerm>();
            for(DocumentField field : getSearchAttributes()) {
                queries.add((AdvancedSearchQueryTerm)Query.Factory.createFieldQuery(field, Condition.CONTAINS, basicQuery.getSearchText()));
            }
        }
        else if(query instanceof CompoundSearchQuery) {
            CompoundSearchQuery compoundQuery = (CompoundSearchQuery) query;
            operator = compoundQuery.getOperator();
            queries = (List<AdvancedSearchQueryTerm>)compoundQuery.getChildren();
        }
        else {
            throw new IllegalArgumentException("the query was not an instance of BasicSearchQuery, AdvancedSearchQueryTerm, or CompoundSearchQuery");
        }

        //do the actual search...
        Sheet sheet = workbook.getSheet(0);
        List<Integer> result = new ArrayList<Integer>();
        for(int i=1; i < sheet.getRows(); i++) {
            for (int i1 = 0, queriesSize = queries.size(); i1 < queriesSize; i1++) {
                AdvancedSearchQueryTerm term = queries.get(i1);
                DocumentField field = term.getField();
                Condition condition = term.getCondition();
                String termValue = term.getValues()[0].toString();
                int col = getTableIndex(field);
                String value = XmlUtilities.encodeXMLChars(sheet.getCell(col, i).getContents());
                boolean colMatch;
                switch (condition) {
                    case EQUAL:
                        colMatch = termValue.equalsIgnoreCase(value);
                        break;
                    case NOT_EQUAL:
                       colMatch = !termValue.equalsIgnoreCase(value);
                        break;
                    case CONTAINS:
                        colMatch = value.toLowerCase().contains(termValue.toLowerCase());
                        break;
                    case NOT_CONTAINS:
                        colMatch = !value.toLowerCase().contains(termValue.toLowerCase());
                        break;
                    case STRING_LENGTH_GREATER_THAN:
                        colMatch = value.length() > Integer.parseInt(termValue);
                        break;
                    case STRING_LENGTH_LESS_THAN:
                        colMatch = value.length() > Integer.parseInt(termValue);
                        break;
                    case BEGINS_WITH:
                        colMatch = value.toLowerCase().startsWith(termValue.toLowerCase());
                        break;
                    case ENDS_WITH:
                        colMatch = value.toLowerCase().endsWith(termValue.toLowerCase());
                        break;
                    default:
                        colMatch = false;
                }
                if (colMatch && (operator == CompoundSearchQuery.Operator.OR || i == queriesSize-1)) {
                    result.add(i);
                    break;
                } else if (!colMatch && operator == CompoundSearchQuery.Operator.AND) {
                    break;
                }

            }
        }

        return result;
    }

    @Override
    public List<String> getTissueIdsMatchingQuery(Query query, List<FimsProject> projectsToMatch) throws ConnectionException {
        List<String> tissueIds = new ArrayList<String>();
        // todo add in project
        for (Integer row : getListOfRowsMatchingQuery(query)) {

            int index = getTableIndex(getTissueSampleDocumentField());
            if(index == -1) {
                throw new ConnectionException("Could not find tissue column (" + getTissueSampleDocumentField().getCode() + ") in Excel sheet.\n\n" +
                "Columns were:\n" + StringUtilities.join("\n", columnNames));
            }

            tissueIds.add(workbook.getSheet(0).getRow(row)[index].getContents());
        }
        return tissueIds;
    }

    @Override
    public List<FimsSample> _retrieveSamplesForTissueIds(List<String> tissueIds, RetrieveCallback callback) throws ConnectionException {
        List<FimsSample> results = new ArrayList<FimsSample>();

        Sheet sheet = workbook.getSheet(0);
        Query[] queries = new Query[tissueIds.size()];
        int i = 0;
        for (String tissueId : tissueIds) {
            queries[i++] = Query.Factory.createFieldQuery(getTissueSampleDocumentField(), Condition.EQUAL, tissueId);
        }
        for (Integer row : getListOfRowsMatchingQuery(Query.Factory.createOrQuery(queries, Collections.<String, Object>emptyMap()))) {
            results.add(new TableFimsSample(sheet, row, this));
        }
        return results;
    }

    protected List<List<String>> getProjectLists() {
        List<List<String>> lists = new ArrayList<List<String>>();
        for(int i=1; i<workbook.getSheet(0).getRows(); i++) {
            List<String> line = new ArrayList<String>();
            for (DocumentField projectField : getProjectFields()) {
                int projectIndex = getTableIndex(projectField);
                if (projectIndex > 0) {
                    Cell[] column = workbook.getSheet(0).getColumn(projectIndex);
                    if(i < column.length) {
                        String contents = column[i].getContents();
                        if(contents != null && contents.trim().length() > 0) {
                            line.add(contents.trim());
                        }
                    }
                }
            }
            lists.add(line);
        }
        return lists;
    }
}