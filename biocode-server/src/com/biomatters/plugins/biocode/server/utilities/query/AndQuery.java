package com.biomatters.plugins.biocode.server.utilities.query;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Gen Li
 *         Created on 5/06/14 12:01 PM
 */
public class AndQuery extends CompoundQuery {
    public AndQuery(Query LHS, Query RHS) { super(LHS, RHS); }

    public <T> List<T> combineLists(List<T> one, List<T> two) {
        List<T> shorter;
        List<T> longer;

        if (one.size() <= two.size()) {
            shorter = one;
            longer = two;
        } else {
            shorter = two;
            longer = one;
        }

        List<T> result = new ArrayList<T>();

        for (T item : shorter) {
            if (longer.contains(item)) {
                result.add(item);
            }
        }

        return result;
    }
}