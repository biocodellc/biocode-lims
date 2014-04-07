package com.biomatters.plugins.biocode.labbench.fims;

import com.biomatters.geneious.publicapi.plugin.Geneious;
import com.biomatters.plugins.biocode.labbench.PasswordOptions;
import com.biomatters.plugins.biocode.XmlUtilities;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.components.Dialogs;

import javax.swing.*;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.io.IOException;
import java.io.File;
import java.awt.*;
import java.util.Set;

import jxl.Workbook;
import jxl.Sheet;
import jxl.Cell;

/**
 * @author Steve
 * @version $Id$
 */
public class ExcelFimsConnectionOptions extends TableFimsConnectionOptions{
    public static final String FILE_LOCATION = "excelFile";

    protected PasswordOptions getConnectionOptions() {
        PasswordOptions options = new PasswordOptions(this.getClass(), "excel");
        options.addLabel("<html>Choose the location of your excel file.<br>The first row should be column headers, and it should<br>have at least a tissue and specimen column.</html>");
        final Options.FileSelectionOption fileLocation = options.addFileSelectionOption(FILE_LOCATION, "Excel file location:", "");
        options.restorePreferences(); //to make sure that the field chooser boxes start out with the right values
        fileLocation.setSelectionType(JFileChooser.FILES_ONLY);
        return options;
    }

    protected List<OptionValue> _getTableColumns() throws IOException {
        FileSelectionOption location = (FileSelectionOption)getChildOptions().get(CONNECTION_OPTIONS_KEY).getOption(FILE_LOCATION);
        return getTableColumns(location.getValue().length() > 0 ? new File(location.getValue()) : null,
                Geneious.isHeadless() ? null : getPanel());
    }

    protected boolean updateAutomatically() {
        return true;
    }

    private List<Options.OptionValue> getTableColumns(File excelFile, final Component owner) {
        List<Options.OptionValue> values = new ArrayList<OptionValue>();

        if(excelFile != null && excelFile.exists()) {
            //noinspection CatchGenericClass
            try {
                Workbook workbook = Workbook.getWorkbook(excelFile);
                Set<String> columnIds = new HashSet<String>();

                if(workbook.getNumberOfSheets() > 0) {
                    Sheet sheet = workbook.getSheet(0);

                    for(int i=0; i < sheet.getColumns(); i++) {
                        Cell cell = sheet.getCell(i,0);
                        String cellContents = cell.getContents();
                        if(cellContents.length() > 0) {
                            String columnName = XmlUtilities.encodeXMLChars(cellContents);
                            if(columnIds.add(columnName)) {
                                values.add(new Options.OptionValue(columnName.toLowerCase(), columnName));
                            }
                        }
                    }
                }
            } catch(IOException e) {
                Dialogs.showMessageDialog("Geneious could not read your excel file: "+e.getMessage(), "Could not read Excel file", owner, Dialogs.DialogIcon.WARNING);
            }
            catch(Exception e) {
                ExcelFimsConnection.handleCorruptedExcelFile(owner, e);
            }
        }


        if(values.size() == 0) {
            values.add(new Options.OptionValue("none", "No Columns"));
        }

        return values;
    }

}
