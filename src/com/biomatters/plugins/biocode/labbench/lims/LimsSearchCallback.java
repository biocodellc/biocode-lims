package com.biomatters.plugins.biocode.labbench.lims;

import com.biomatters.geneious.publicapi.databaseservice.RetrieveCallback;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.PluginDocument;
import com.biomatters.geneious.publicapi.documents.XMLSerializable;
import com.google.common.base.Function;
import jebl.util.Cancelable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Matthew Cheung
 *         Created on 13/10/14 3:44 PM
 */
public abstract class LimsSearchCallback<Type> implements Cancelable {

    public abstract void addResult(Type result);

    /**
     *
     * @param retrieveCallback to add results to
     * @param function to convert between the LIMS search type and a Geneious {@link com.biomatters.geneious.publicapi.documents.PluginDocument}
     * @param <T> The LIMS search type
     * @param <ReturnType> The {@link com.biomatters.geneious.publicapi.documents.PluginDocument} type of results
     * @return A {@link com.biomatters.plugins.biocode.labbench.lims.LimsSearchCallback} that uses a {@link com.google.common.base.Function}
     * to convert between LIMS search type and a {@link com.biomatters.geneious.publicapi.documents.PluginDocument}
     */
    public static <T, ReturnType extends PluginDocument> LimsSearchCallback<T> forRetrievePluginDocumentCallback(
            RetrieveCallback retrieveCallback, Function<T, ReturnType> function) {

        return new LimsSearchRetreiveCallback<T, ReturnType>(retrieveCallback, function);
    }

    /**
     * The same as {@link #forRetrievePluginDocumentCallback(com.biomatters.geneious.publicapi.databaseservice.RetrieveCallback, com.google.common.base.Function)}
     * but for {@link com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument}
     *
     * @see #forRetrievePluginDocumentCallback(com.biomatters.geneious.publicapi.databaseservice.RetrieveCallback, com.google.common.base.Function)
     */
    public static <T> LimsSearchCallback<T> forRetrieveAnnotatedPluginDocumentCallback(
            RetrieveCallback retrieveCallback, Function<T, AnnotatedPluginDocument> function) {
        return new LimsSearchRetreiveCallback<T, AnnotatedPluginDocument>(retrieveCallback, function);
    }



    /**
     * A {@link com.biomatters.plugins.biocode.labbench.lims.LimsSearchCallback} that bridges the gap between LIMS
     * searching and {@link com.biomatters.geneious.publicapi.databaseservice.RetrieveCallback}s used by Geneious core.
     * <p/>
     * The constructor is private so we can restrict it to be only be instantiated with one of the two allowable return
     * types.
     *
     * @see #forRetrievePluginDocumentCallback(com.biomatters.geneious.publicapi.databaseservice.RetrieveCallback, com.google.common.base.Function)
     * @see #forRetrieveAnnotatedPluginDocumentCallback(com.biomatters.geneious.publicapi.databaseservice.RetrieveCallback, com.google.common.base.Function)
     *
     * @param <T> The return type of the LIMS search
     * @param <ReturnType> Should be one of {@link com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument}
     *                    or {@link com.biomatters.geneious.publicapi.documents.PluginDocument}
     */
    private static class LimsSearchRetreiveCallback<T, ReturnType extends XMLSerializable> extends LimsSearchCallback<T> {
        private RetrieveCallback internalCallback;
        private Function<T, ReturnType> function;

        private LimsSearchRetreiveCallback(RetrieveCallback internalCallback, Function<T, ReturnType> function) {
            this.internalCallback = internalCallback;
            this.function = function;
        }

        @Override
        public void addResult(T result) {
            ReturnType toAdd = function.apply(result);
            if(toAdd == null) {
                return;  // Ignore null results
            }
            if(AnnotatedPluginDocument.class.isAssignableFrom(toAdd.getClass())) {
                internalCallback.add((AnnotatedPluginDocument)toAdd, Collections.<String, Object>emptyMap());
            } else if(PluginDocument.class.isAssignableFrom(toAdd.getClass())) {
                internalCallback.add((PluginDocument)toAdd, Collections.<String, Object>emptyMap());
            } else {
                throw new IllegalStateException("This callback should only be used with AnnotatedPluginDocuments or " +
                        "Plugin Documents.");
            }
        }

        @Override
        public boolean isCanceled() {
            return internalCallback.isCanceled();
        }
    }

    public static class LimsSearchRetrieveListCallback<T> extends LimsSearchCallback<T> {

        private Cancelable cancelable;
        private List<T> list = new ArrayList<T>();

        public LimsSearchRetrieveListCallback(Cancelable cancelable) {
            this.cancelable = cancelable;
        }

        @Override
        public void addResult(T result) {
            list.add(result);
        }

        @Override
        public boolean isCanceled() {
            return cancelable != null && cancelable.isCanceled();
        }

        public List<T> getResults() {
            return Collections.unmodifiableList(list);
        }
    }
}
