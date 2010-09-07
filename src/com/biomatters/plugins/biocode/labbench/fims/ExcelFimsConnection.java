package com.biomatters.plugins.biocode.labbench.fims;

import com.biomatters.geneious.publicapi.databaseservice.AdvancedSearchQueryTerm;
import com.biomatters.geneious.publicapi.databaseservice.BasicSearchQuery;
import com.biomatters.geneious.publicapi.databaseservice.CompoundSearchQuery;
import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.Condition;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.XmlUtilities;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.FimsSample;
import jxl.Cell;
import jxl.Sheet;
import jxl.Workbook;
import jxl.read.biff.BiffException;
import org.virion.jam.util.SimpleListener;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.prefs.Preferences;

/**
 * Created by IntelliJ IDEA.
 * User: steve
 * Date: 12/05/2009
 * Time: 8:00:41 AM
 * To change this template use File | Settings | File Templates.
 */
public class ExcelFimsConnection extends FIMSConnection{
    Preferences prefs = Preferences.userNodeForPackage(this.getClass());

    private Workbook workbook;

    int tissueCol;

    int specimenCol;

    int plateCol, wellCol;

    boolean storePlates;

    public String getName() {
        return "excel";
    }

    public String getDescription() {
        return  "Read field information from an excel worksheet";
    }

    public String getLabel() {
        return "Excel";
    }

    public Options getConnectionOptions() {
        Options options = new Options(this.getClass());
        options.addLabel("<html>Choose the location of your excel file.<br>The first row should be column headers, and it should<br>have at least a tissue and specimen column.</html>");
        final Options.FileSelectionOption fileLocation = options.addFileSelectionOption("excelFile", "Excel file location:", "");
        options.restorePreferences(); //to make sure that the field chooser boxes start out with the right values
        fileLocation.setSelectionType(JFileChooser.FILES_ONLY);

        List<Options.OptionValue> cols = getTableColumns(fileLocation.getValue().length() > 0 ? new File(fileLocation.getValue()) : null);

        final Options.ComboBoxOption<Options.OptionValue> tissueId = options.addComboBoxOption("tissueId", "Tissue ID field:", cols, cols.get(0));

        final Options.ComboBoxOption<Options.OptionValue> specimenId = options.addComboBoxOption("specimenId", "Specimen ID field:", cols, cols.get(0));

        final Options.BooleanOption storePlates = options.addBooleanOption("storePlates", "The FIMS database contains plate information", false);

        final Options.ComboBoxOption<Options.OptionValue> plateName = options.addComboBoxOption("plateName", "Plate name field:", cols, cols.get(0));

        final Options.ComboBoxOption<Options.OptionValue> plateWell = options.addComboBoxOption("plateWell", "Well field:", cols, cols.get(0));

        storePlates.addDependent(plateName, true);
        storePlates.addDependent(plateWell, true);

        options.addLabel(" ");
        options.addLabel("Specify your taxonomy fields, in order of highest to lowest");
        Options taxonomyOptions = new Options(this.getClass());
        taxonomyOptions.beginAlignHorizontally("", false);
        final Options.ComboBoxOption<Options.OptionValue> taxCol = taxonomyOptions.addComboBoxOption("taxCol", "", cols, cols.get(0));
        taxonomyOptions.endAlignHorizontally();

        final Options.MultipleOptions taxOptions = options.addMultipleOptions("taxFields", taxonomyOptions, false);

        fileLocation.addChangeListener(new SimpleListener(){
            public void objectChanged() {
                List<Options.OptionValue> newCols = getTableColumns(fileLocation.getValue().length() > 0 ? new File(fileLocation.getValue()) : null);
                tissueId.setPossibleValues(newCols);
                specimenId.setPossibleValues(newCols);
                plateName.setPossibleValues(newCols);
                plateWell.setPossibleValues(newCols);
                taxCol.setPossibleValues(newCols);
                for(Options options : taxOptions.getValues()) {
                    for(Options.Option option : options.getOptions()) {
                        if(option instanceof Options.ComboBoxOption) {
                            ((Options.ComboBoxOption)option).setPossibleValues(newCols);
                        }
                    }
                }
            }
        });

        return options;
    }

    private List<Options.OptionValue> getTableColumns(File excelFile) {
        List<Options.OptionValue> values = new ArrayList<Options.OptionValue>();

        if(excelFile != null && excelFile.exists()) {
            try {
                Workbook workbook = Workbook.getWorkbook(excelFile);


                Sheet sheet = workbook.getSheet(0);

                for(int i=0; i < sheet.getColumns(); i++) {
                    Cell cell = sheet.getCell(i,0);
                    String cellContents = cell.getContents();
                    if(cellContents.length() > 0) {
                        values.add(new Options.OptionValue(""+i, XmlUtilities.encodeXMLChars(cellContents)));
                    }
                }
            } catch (BiffException e) {
                e.printStackTrace();  //todo: anything?
            } catch (IOException e) {
                e.printStackTrace();  //todo: anything?
            }
        }


        if(values.size() == 0) {
            values.add(new Options.OptionValue("none", "No Columns"));
        }

        return values;
    }

    private List<DocumentField> fields;
     List<DocumentField> taxonomyFields;

    public void connect(Options options) throws ConnectionException {
        String excelFileLocation = options.getValueAsString("excelFile");
        if(excelFileLocation.length() == 0) {
            throw new ConnectionException("You must specify an Excel file");
        }
        File excelFile = new File(excelFileLocation);
        if(!excelFile.exists()) {
            throw new ConnectionException("Cannot find the file "+excelFile.getAbsolutePath());
        }
        tissueCol = Integer.parseInt(((Options.OptionValue)options.getValue("tissueId")).getName());
        specimenCol = Integer.parseInt(((Options.OptionValue)options.getValue("specimenId")).getName());
        storePlates = (Boolean)options.getValue("storePlates");
        if(storePlates) {
            plateCol = Integer.parseInt(((Options.OptionValue)options.getValue("plateName")).getName());
            wellCol = Integer.parseInt(((Options.OptionValue)options.getValue("plateWell")).getName());
        }
        fields = new ArrayList<DocumentField>();
        taxonomyFields = new ArrayList<DocumentField>();

        try {
            workbook = Workbook.getWorkbook(excelFile);


            Sheet sheet = workbook.getSheet(0);

            List<Options> taxOptions = options.getMultipleOptions("taxFields").getValues();
            for(Options taxOptionsValue : taxOptions){
                Options.OptionValue colValue = (Options.OptionValue)((Options.ComboBoxOption)taxOptionsValue.getOption("taxCol")).getValue();
                taxonomyFields.add(new DocumentField(XmlUtilities.encodeXMLChars(colValue.getLabel()), XmlUtilities.encodeXMLChars(colValue.getLabel()), colValue.getName(), String.class, true, false));
            }

            for(int i=0; i < sheet.getColumns(); i++) {
                Cell cell = sheet.getCell(i,0);
                String cellContents = cell.getContents();
                if(cellContents.length() > 0) {
                    DocumentField field = new DocumentField(XmlUtilities.encodeXMLChars(cellContents), XmlUtilities.encodeXMLChars(cellContents), "" + i, String.class, true, false);
                    if(!taxonomyFields.contains(field)) {
                        fields.add(field);
                    }
                }
            }

            //if the tissue or specimen id is also a taxonomy field, it won't be in the fields list, and will cause problems later on
            if(getTableCol(fields, tissueCol) == null) {
                throw new ConnectionException(null, "You have listed your tissue sample field as also being a taxonomy field.  This is not allowed.");
            }
            if(getTableCol(fields, specimenCol) == null) {
                throw new ConnectionException(null, "You have listed your specimen field as also being a taxonomy field.  This is not allowed.");
            }

        } catch (BiffException e) {
            e.printStackTrace();  //todo: anything?
        } catch (IOException e) {
            e.printStackTrace();  //todo: anything?
        }


    }

    private static DocumentField getTableCol(List<DocumentField> fields, int colIndex) {
        for(DocumentField field : fields) {
            if(field.getCode().equals(""+colIndex)) {
                return field;
            }
        }
        return null;
    }

    public void disconnect() throws ConnectionException {
        tissueCol = specimenCol = -1;
        fields = null;
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
            for(AdvancedSearchQueryTerm term : queries) {
                DocumentField field = term.getField();
                Condition condition = term.getCondition();
                String termValue = term.getValues()[0].toString();
                int col = Integer.parseInt(field.getCode());
                String value = XmlUtilities.encodeXMLChars(sheet.getCell(col, i).getContents());
                boolean colMatch;
                switch(condition) {
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
                    default :
                        colMatch = false;
                }
                if(colMatch && operator == CompoundSearchQuery.Operator.OR) {
                    result.add(new ExcelFimsSample(sheet, i, this));
                }
                else if(!colMatch && operator == CompoundSearchQuery.Operator.AND) {
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
        return getTableCol(fields, plateCol);
    }

    @Override
    public DocumentField getWellDocumentField() {
        return getTableCol(fields, wellCol);
    }

    public BiocodeUtilities.LatLong getLatLong(AnnotatedPluginDocument annotatedDocument) {
        return null;
    }
}
