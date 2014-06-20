package com.biomatters.plugins.biocode.server.utilities.query;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Gen Li
 *         Created on 5/06/14 12:02 PM
 */
public class XorQuery extends CompoundQuery {
    public XorQuery(Query LHS, Query RHS) {
        super(LHS, RHS);
    }

    public <T> List<T> combineLists(List<T> one, List<T> two) {
        List<T> result = new ArrayList<T>();

        for (T item : one) {
            if (!two.contains(item)) {
                result.add(item);
            }
        }
        for (T item : two) {
            if (!one.contains(item)) {
                result.add(item);
            }
        }

        return result;
    }
}