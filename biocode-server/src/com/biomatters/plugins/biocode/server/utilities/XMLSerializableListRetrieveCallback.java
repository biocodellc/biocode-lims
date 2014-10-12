package com.biomatters.plugins.biocode.server.utilities;

import com.biomatters.geneious.publicapi.databaseservice.RetrieveCallback;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.PluginDocument;
import com.biomatters.plugins.biocode.server.XMLSerializableList;

import java.util.ArrayList;
import java.util.Map;

/**
 * A {@link com.biomatters.geneious.publicapi.databaseservice.RetrieveCallback} that stores any results added to it
 * using a {@link com.biomatters.plugins.biocode.server.XMLSerializableList}.  Use {@link #getResults()} to retrieve
 * the list after the retrieval has finished.
 *
 * @author Matthew Cheung
 *         Created on 12/10/14 10:27 PM
 */
public class XMLSerializableListRetrieveCallback<T extends PluginDocument> extends RetrieveCallback {

    private Class<T> type;
    private XMLSerializableList<T> list;

    public XMLSerializableListRetrieveCallback(Class<T> type) {
        this.type = type;
        list = new XMLSerializableList<T>(type, new ArrayList<T>());
    }

    @Override
    protected void _add(PluginDocument pluginDocument, Map<String, Object> stringObjectMap) {
        if(type.isAssignableFrom(pluginDocument.getClass())) {
            //noinspection unchecked
            list.add((T)pluginDocument);
        }
    }

    @Override
    protected void _add(AnnotatedPluginDocument annotatedPluginDocument, Map<String, Object> stringObjectMap) {
        throw new UnsupportedOperationException("XMLSerializableListRetrieveCallback does not support AnnotatedPluginDocuments");
    }

    public XMLSerializableList<T> getResults() {
        return list;
    }
}
