package com.biomatters.plugins.biocode.labbench.rest.client;

import com.biomatters.plugins.biocode.BiocodePlugin;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.MultivaluedMap;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A filter to add biocode plugin version header to request sent to biocode server.
 * The request will be rejected if its value does not fall in the range between minVersion and maxVersion, which are configured in web.xml of server.
 * @author Frank Lee
 *         Created on 4/02/15 11:54 AM
 */
public class VersionHeaderAddingFilter implements ClientRequestFilter {

    public static final String BIOCODE_VERSION = "biocode_version";

    @Override
    public void filter(ClientRequestContext requestContext) throws IOException {
        MultivaluedMap<String, Object> headers = requestContext.getHeaders();
        List<Object> objectList = new ArrayList<Object>();
        objectList.add(BiocodePlugin.PLUGIN_VERSION);
        headers.put(BIOCODE_VERSION, objectList);
    }
}
