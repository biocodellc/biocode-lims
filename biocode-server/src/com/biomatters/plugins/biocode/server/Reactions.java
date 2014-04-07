package com.biomatters.plugins.biocode.server;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.reaction.MemoryFile;
import jebl.util.ProgressListener;

import javax.ws.rs.GET;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Matthew Cheung
 * @version $Id$
 *          <p/>
 *          Created on 7/04/14 11:45 AM
 */
@Path("reactions")
public class Reactions {

    @GET
    @Path("{id}/traces")
    public List<MemoryFile> getTraces(@PathParam("id")int reactionId) {
        try {
            Map<Integer, List<MemoryFile>> traces = BiocodeService.getInstance().getActiveLIMSConnection().downloadTraces(
                    Collections.singletonList(reactionId), ProgressListener.EMPTY);
            if(traces == null) {
                return null;
            }
            return traces.get(reactionId);
        } catch (DatabaseServiceException e) {
            throw new InternalServerErrorException(e.getMessage(), e);
        }
    }
}
