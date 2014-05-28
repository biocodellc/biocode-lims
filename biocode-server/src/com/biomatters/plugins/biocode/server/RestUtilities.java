package com.biomatters.plugins.biocode.server;

import javax.ws.rs.core.NoContentException;
import java.util.List;

/**
 * @author Gen Li
 *         Created on 28/05/14 11:59 AM
 */
public class RestUtilities {

    private RestUtilities() {
        // Can't instantiate
    }

    public static <T> T getOnlyItemFromList(List<T> list, String noItemErrorMessage) throws NoContentException {
        if (list.isEmpty()) {
            throw new NoContentException(noItemErrorMessage);
        }
        return list.get(0);
    }
}
