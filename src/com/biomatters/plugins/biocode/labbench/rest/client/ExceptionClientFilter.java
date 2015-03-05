package com.biomatters.plugins.biocode.labbench.rest.client;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.client.ClientResponseFilter;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.List;

/**
 * @author Frank Lee
 *         Created on 5/03/15 3:35 PM
 */
public class ExceptionClientFilter implements ClientResponseFilter {
    public static final String ERROR_MSG_HEADER = "error_msg";
    @Override
    public void filter(ClientRequestContext requestContext, ClientResponseContext responseContext) throws IOException {
        int status = responseContext.getStatus();
        if (status != Response.Status.OK.getStatusCode() && status != Response.Status.FORBIDDEN.getStatusCode()) {
            List<String> errorMsg = responseContext.getHeaders().get(ERROR_MSG_HEADER);
            if (errorMsg != null && errorMsg.size() > 0) {
                throw new WebApplicationException(errorMsg.get(0));
            }
        }
    }
}
