package com.biomatters.plugins.biocode.labbench.lims;

import com.biomatters.geneious.publicapi.databaseservice.RetrieveCallback;
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
public abstract class LimsSearchCallback<Type extends XMLSerializable> implements Cancelable {

    public abstract void addResult(Type result);


    public static <T extends XMLSerializable, ReturnType extends PluginDocument> LimsSearchCallback<T>
        forRetrieveCallback(final RetrieveCallback retrieveCallback, Function<T, ReturnType> function) {

        return new LimsSearchRetreiveCallback<T, ReturnType>(retrieveCallback, function);

    }

    public static class LimsSearchRetreiveCallback<T extends XMLSerializable, ReturnType extends PluginDocument> extends LimsSearchCallback<T> {
        private RetrieveCallback internalCallback;
        private Function<T, ReturnType> function;

        private LimsSearchRetreiveCallback(RetrieveCallback internalCallback, Function<T, ReturnType> function) {
            this.internalCallback = internalCallback;
            this.function = function;
        }

        @Override
        public void addResult(T result) {
            internalCallback.add(function.apply(result), Collections.<String, Object>emptyMap());
        }

        @Override
        public boolean isCanceled() {
            return internalCallback.isCanceled();
        }
    }

    public static class LimsSearchRetrieveListCallback<T extends XMLSerializable> extends LimsSearchCallback<T> {

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
