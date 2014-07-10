package com.biomatters.plugins.biocode.labbench.rest.client;

import javax.ws.rs.ForbiddenException;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.client.ClientResponseFilter;
import javax.ws.rs.core.Response;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Converts any response with a 403 status into the appropriate exception.  This filter is required because for some
 * reason Jersey does not do this automatically like it does for InternalServerErrorException.
 *
 * @author Matthew Cheung
 *         Created on 10/07/14 5:17 PM
 */
public class ForbiddenExceptionClientFilter implements ClientResponseFilter {

    @Override
    public void filter(ClientRequestContext clientRequestContext, ClientResponseContext clientResponseContext) throws IOException {
        if (clientResponseContext.getStatus() == Response.Status.FORBIDDEN.getStatusCode()) {
            StringBuilder entity = new StringBuilder();

            BufferedReader entityReader = new BufferedReader(new InputStreamReader(clientResponseContext.getEntityStream()));
            String line;
            while((line = entityReader.readLine()) != null) {
                entity.append(line);
                entity.append("\n");
            }
            entityReader.close();
            if(entity.length() > 0) {
                throw new ForbiddenException(entity.toString());
            } else {
                throw new ForbiddenException();
            }
        }
    }
}
