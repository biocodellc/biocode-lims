package com.biomatters.plugins.biocode.labbench.rest.client;

import com.biomatters.plugins.biocode.BiocodePlugin;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.MultivaluedMap;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Frank Lee
 *         Created on 4/02/15 11:54 AM
 */
public class VersionHeaderAddingFilter implements ClientRequestFilter {

    public static final String GENEIOUS_VERSION_STRING = "geneious_version";

    @Override
    public void filter(ClientRequestContext requestContext) throws IOException {
        MultivaluedMap<String, Object> headers = requestContext.getHeaders();
        List<Object> objectList = new ArrayList<Object>();
        objectList.add(BiocodePlugin.PLUGIN_VERSION);
        headers.put(GENEIOUS_VERSION_STRING, objectList);
    }
}
