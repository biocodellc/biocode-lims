package com.biomatters.plugins.biocode.server.utilities;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Gen Li
 *         Created on 5/06/14 12:02 PM
 */

class XorQuery extends CompoundQuery {

    XorQuery(Query LHS, Query RHS) {
        super(LHS, RHS);
    }

    <T> List<T> combineLists(List<T> one, List<T> two) {
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