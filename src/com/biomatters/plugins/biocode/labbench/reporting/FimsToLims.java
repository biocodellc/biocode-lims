package com.biomatters.plugins.biocode.labbench.reporting;

import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.FimsSample;
import com.biomatters.plugins.biocode.labbench.TissueDocument;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.fims.FIMSConnection;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.PluginDocument;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.geneious.publicapi.databaseservice.RetrieveCallback;
import jebl.util.ProgressListener;

import java.sql.Statement;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.Connection;
import java.util.*;

/**
 * @author Steve
 * @version $Id$
 */
public class FimsToLims {

    public static void createFimsTable(ProgressListener listener) throws ConnectionException {
        BiocodeService service = BiocodeService.getInstance();
        if(!service.isLoggedIn() || listener.isCanceled()) {
            return;
        }

//        String dropDefinitionTable = "DROP TABLE IF EXISTS fims_definition";
//        Statement statement = service.getActiveLIMSConnection().getConnection().createStatement();
//        statement.executeUpdate(dropDefinitionTable);
//
//        String createDefinitionTable = "CREATE TABLE fims_definition(key  VARCHAR(999) PRIMARY KEY IDENTITY, value  VARCHAR(999) NOT NULL, type VARCHAR(99))";
//        statement.executeUpdate(createDefinitionTable);
//
//        for(DocumentField field : service.getActiveFIMSConnection().getSearchAttributes()){
//            String insertIntoDefinitionTable = "INSERT INTO fims_definition (key, value, type)"
//        }
        try {
            listener.setIndeterminateProgress();

            final FIMSConnection fimsConnection = service.getActiveFIMSConnection();
    
            String dropFimsTable = "DROP TABLE IF EXISTS fims_values";
            final Connection limsConnection = service.getActiveLIMSConnection().getConnection();
            Statement statement = limsConnection.createStatement();
            statement.executeUpdate(dropFimsTable);

            final List<String> fieldsAndTypes = new ArrayList<String>();
            final List<String> fields = new ArrayList<String>();
            for(DocumentField f : fimsConnection.getSearchAttributes()) {
                String colName = getSqlColName(f.getCode());
                fieldsAndTypes.add(colName +" VARCHAR(255)");
                fields.add(colName);
            }

            String createDefinitionTable = "CREATE TABLE fims_values("+ StringUtilities.join(", ", fieldsAndTypes)+")";
            System.out.println(createDefinitionTable);
            statement.executeUpdate(createDefinitionTable);

            RetrieveCallback callback = new RetrieveCallback(listener){
                protected void _add(PluginDocument document, Map<String, Object> searchResultProperties) {
                    handle((FimsSample)document);
                }

                protected void _add(AnnotatedPluginDocument document, Map<String, Object> searchResultProperties) {
                    handle((FimsSample)document.getDocumentOrCrash());
                }

                private void handle(FimsSample fimsSample) {
                    String sql = "INSERT INTO fims_values("+StringUtilities.join(", ", fields)+") VALUES ("+StringUtilities.join(", ", Collections.nCopies(fieldsAndTypes.size(), "?"))+")";
                    try {
                        PreparedStatement statement = limsConnection.prepareStatement(sql);
                        int count = 1;
                        for(DocumentField f : fimsConnection.getSearchAttributes()) {
                            Object attributeValue = fimsSample.getFimsAttributeValue(f.getCode());
                            String value = attributeValue == null ? null : attributeValue.toString();
                            statement.setString(count, value);
                            count++;
                        }
                        statement.executeUpdate();
                    } catch (SQLException e) {
                        e.printStackTrace();
                        //todo: exception handling
                    }


                }
            };

            fimsConnection.getAllSamples(callback);
        }
        catch(SQLException ex ){
            ex.printStackTrace();
            //todo: exception handling
        }



    }

    private static String getSqlColName(String code) {
        String value = code.replace('.', '_');
        try {
            Integer.parseInt(""+value.charAt(0));
            value = "a"+value;
        } catch (NumberFormatException e) {} //do nothing
        return value;
    }


}
