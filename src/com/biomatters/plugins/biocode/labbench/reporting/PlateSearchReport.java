package com.biomatters.plugins.biocode.labbench.reporting;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.plugin.DocumentSelectionSignature;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.geneious.publicapi.components.GPanel;
import com.biomatters.plugins.biocode.labbench.TableDocumentViewerFactory;
import com.biomatters.plugins.biocode.labbench.lims.FimsToLims;
import jebl.util.ProgressListener;

import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.List;
import java.awt.*;

import org.jdom.Element;

import javax.swing.table.TableModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.*;

/**
 * @author Steve
 * @version $Id$
 *          <p/>
 *          Created on 1/09/2011 9:18:14 PM
 */


public class PlateSearchReport extends Report{

    public PlateSearchReport(FimsToLims fimsToLims) {
        super(fimsToLims);
    }

    public PlateSearchReport(Element e) throws XMLSerializationException {
        super(e);
    }

    public String getTypeName() {
        return "Plate Search";
    }

    public String getTypeDescription() {
        return "Get a list of all plates matching your criteria";
    }

    public Options createOptions(FimsToLims fimsToLims) {
        return new PlateSearchReportOptions(getClass(), fimsToLims);
    }

    public ReportChart getChart(Options optionsa, FimsToLims fimsToLims, ProgressListener progress) throws SQLException {
        PlateSearchReportOptions options = (PlateSearchReportOptions)optionsa;

        String sql = "SELECT DISTINCT (plate.name) FROM plate, ";
        Set<String> reactionTypes = new LinkedHashSet<String>();
        List<ReactionFieldOptions> fieldOptions = options.getFieldOptions();

        for(ReactionFieldOptions fieldOption : fieldOptions) {
            reactionTypes.add(fieldOption.getTable());
        }

        sql += StringUtilities.join(", ", reactionTypes);

        sql += " WHERE ";

        for (Iterator<String> it = reactionTypes.iterator(); it.hasNext();) {
            String tableName = it.next();
            sql += tableName + ".plate=plate.id";
            if(it.hasNext()) {
                sql += " AND ";
            }
        }
        sql += " AND (";

        for (Iterator<ReactionFieldOptions> it = fieldOptions.iterator(); it.hasNext();) {
            ReactionFieldOptions fieldOption = it.next();
            sql += ReportGenerator.getTableFieldName(fieldOption.getTable(), fieldOption.getField()) + " " + fieldOption.getComparator() + " " + "?";
            if(it.hasNext()) {
                sql += options.getComparator();
            }
        }
        sql += ")";

        System.out.println(sql);

        PreparedStatement statement = fimsToLims.getLimsConnection().createStatement(sql);

        for (int i = 0; i < fieldOptions.size(); i++) {
            statement.setObject(i+1, fieldOptions.get(i).getValue());
        }

        ResultSet resultSet = statement.executeQuery();

        final List<String> values = new ArrayList<String>();
        while(resultSet.next()) {
            values.add(resultSet.getString(1));
        }

        final TableModel model = new AbstractTableModel(){
            public int getRowCount() {
                return values.size();
            }

            public int getColumnCount() {
                return 1;
            }

            public Object getValueAt(int rowIndex, int columnIndex) {
                return values.get(rowIndex);
            }

            @Override
            public String getColumnName(int column) {
                return "Plate Name";
            }
        };

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
                        new ExcelChartExporter(getName(), model),
                        new HTMLChartExporter(getName(), model)
                };
            }
        };
    }
}
