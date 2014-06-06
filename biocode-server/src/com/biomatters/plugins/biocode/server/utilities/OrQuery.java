package com.biomatters.plugins.biocode.server.utilities;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Gen Li
 *         Created on 5/06/14 12:02 PM
 */
class OrQuery extends CompoundQuery {

    OrQuery(Query LHS, Query RHS) {
        super(LHS, RHS);
    }

    <T> List<T> combineLists(List<T> one, List<T> two) {
        Set<T> filterSet = new HashSet<T>();

        filterSet.addAll(one);
        filterSet.addAll(two);

        List<T> result = new ArrayList<T>();

        result.addAll(filterSet);

        return result;
    }
}