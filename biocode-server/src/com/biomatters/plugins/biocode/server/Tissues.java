package com.biomatters.plugins.biocode.server;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;

import javax.ws.rs.*;
import java.util.Arrays;

/**
 * A tissue entry
 *
 * @author Matthew Cheung
 * @version $Id$
 *          <p/>
 *          Created on 23/03/14 4:57 PM
 */
@Path("tissues")
public class Tissues {

    @GET
    @Produces("text/plain")
    @Path("extractions")
    public String getForBarcodes(@QueryParam("tissues")String tissueIds) {
        if(tissueIds == null || tissueIds.trim().isEmpty()) {
            throw new BadRequestException("Must specify tissues");
        }

        try {
            return StringUtilities.join("\n", LIMSInitializationListener.getLimsConnection().
                    getAllExtractionIdsForTissueIds(Arrays.asList(tissueIds.split(","))));

        } catch (DatabaseServiceException e) {
            throw new InternalServerErrorException(e.getMessage(), e);
        }
    }
}
