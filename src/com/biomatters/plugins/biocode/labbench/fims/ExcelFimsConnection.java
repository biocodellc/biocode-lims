package com.biomatters.plugins.biocode.labbench.fims;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.databaseservice.*;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.Condition;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.BiocodePlugin;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.XmlUtilities;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.FimsSample;
import com.biomatters.plugins.biocode.labbench.PasswordOptions;
import com.biomatters.plugins.biocode.labbench.TissueDocument;
import jxl.Cell;
import jxl.Sheet;
import jxl.Workbook;
import org.virion.jam.util.SimpleListener;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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

    public boolean requiresMySql() {
        return true;
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
        fields = new ArrayList<DocumentField>();
        taxonomyFields = new ArrayList<DocumentField>();

        columnNames = new ArrayList<String>();
        //noinspection CatchGenericClass
        try {
            workbook = Workbook.getWorkbook(excelFile);
            Sheet sheet = workbook.getSheet(0);
            for(int i=0; i < sheet.getColumns(); i++) {
                Cell cell = sheet.getCell(i,0);
                String cellContents = cell.getContents();
                if(cellContents.length() > 0) {
                    columnNames.add(cellContents);
                }
            }

        } catch(IOException e) {
            Dialogs.showMessageDialog("Geneious could not read your excel file: "+e.getMessage(), "Could not read Excel file", null, Dialogs.DialogIcon.WARNING);
            return;
        } catch(Exception e) {
            handleCorruptedExcelFile(null, e);
            throw ConnectionException.NO_DIALOG;
        }
    }

    public List<DocumentField> getTableColumns() throws IOException {
        List<DocumentField> results = new ArrayList<DocumentField>();
        for (int i = 0, cellValuesSize = columnNames.size(); i < cellValuesSize; i++) {
            String cellContents = columnNames.get(i);
            DocumentField field = new DocumentField(XmlUtilities.encodeXMLChars(cellContents), XmlUtilities.encodeXMLChars(cellContents), "" + i, String.class, true, false);
            results.add(field);
        }
        return results;
    }

    private static DocumentField getTableCol(List<DocumentField> fields, int colIndex) {
        for(DocumentField field : fields) {
            if(field.getCode().equals(""+colIndex)) {
                return field;
            }
        }
        return null;
    }

    public void _disconnect() {
        excelFile = null;
        if(workbook != null) {
            workbook.close();
            workbook = null;
        }
    }

    public List<DocumentField> getSearchAttributes() {
        List<DocumentField> searchAttributes = new ArrayList<DocumentField>();
        searchAttributes.addAll(fields);
        searchAttributes.addAll(taxonomyFields);
        return searchAttributes;
    }

    public DocumentField getTissueSampleDocumentField() {
        return getTableCol(fields, tissueCol);
    }

    public List<DocumentField> getCollectionAttributes() {
        return fields;
    }

    public List<DocumentField> getTaxonomyAttributes() {
        return taxonomyFields;
    }

    public void getAllSamples(RetrieveCallback callback) throws ConnectionException {
        Sheet sheet = workbook.getSheet(0);
        for(int i=1; i < sheet.getRows(); i++) {
            callback.add(new TissueDocument(new TableFimsSample(sheet, i, this)), Collections.<String,Object>emptyMap());
        }
    }

    public List<FimsSample> _getMatchingSamples(Query query) {
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
        List<FimsSample> result = new ArrayList<FimsSample>();
        for(int i=1; i < sheet.getRows(); i++) {
            for (int i1 = 0, queriesSize = queries.size(); i1 < queriesSize; i1++) {
                AdvancedSearchQueryTerm term = queries.get(i1);
                DocumentField field = term.getField();
                Condition condition = term.getCondition();
                String termValue = term.getValues()[0].toString();
                int col = Integer.parseInt(field.getCode());
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
                    result.add(new TableFimsSample(sheet, i, this));
                    break;
                } else if (!colMatch && operator == CompoundSearchQuery.Operator.AND) {
                    break;
                }

            }
        }


        return result;
    }

    public Map<String, String> getTissueIdsFromExtractionBarcodes(List<String> extractionIds) throws ConnectionException{
        return Collections.emptyMap();
    }

    public Map<String, String> getTissueIdsFromFimsExtractionPlate(String plateId) throws ConnectionException{
        return Collections.emptyMap();
    }

    public boolean canGetTissueIdsFromFimsTissuePlate() {
        return storePlates;
    }

    @Override
    public DocumentField getPlateDocumentField() {
        if(!storePlates) {
            return null;
        }
        return getTableCol(fields, plateCol);
    }

    @Override
    public DocumentField getWellDocumentField() {
        if(!storePlates) {
            return null;
        }
        return getTableCol(fields, wellCol);
    }

    public BiocodeUtilities.LatLong getLatLong(AnnotatedPluginDocument annotatedDocument) {
        return null;
    }
}
