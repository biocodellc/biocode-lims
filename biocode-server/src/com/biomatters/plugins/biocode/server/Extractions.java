package com.biomatters.plugins.biocode.server;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.plugins.biocode.labbench.reaction.ExtractionReaction;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;
import com.biomatters.plugins.biocode.server.utilities.RestUtilities;

import javax.ws.rs.*;
import java.util.Arrays;

/**
 * @author Matthew Cheung
 *          <p/>
 *          Created on 7/04/14 1:16 AM
 */
@Path("extractions")
public class Extractions {

    @GET
    @Produces("application/xml")
    public XMLSerializableList<ExtractionReaction> getForBarcodes(@QueryParam("barcodes")String barcodes) { // todo: Migrate logic to QueryService?
        if(barcodes == null || barcodes.trim().isEmpty()) {
            throw new BadRequestException("Must specify barcodes");
        }

        try {
            return new XMLSerializableList<ExtractionReaction>(ExtractionReaction.class,
                    LIMSInitializationListener.getLimsConnection().getExtractionsFromBarcodes(
                            RestUtilities.getListFromString(barcodes)));
        } catch (DatabaseServiceException e) {
            throw new InternalServerErrorException(e.getMessage(), e);
        }
    }

    @GET
    @Produces("application/xml")
    @Path("workflows")
    public StringMap getWorkflowsForExtractionIds(@QueryParam("extractionIds")String extractionIds,
                                                  @QueryParam("loci")String loci, @QueryParam("type")String type) { // todo: Migrate logic to QueryService?
        if(extractionIds == null || extractionIds.trim().isEmpty()) {
            throw new BadRequestException("Must specify extractionIds");
        }

        if(loci == null || loci.trim().isEmpty()) {
            throw new BadRequestException("Must specify loci");
        }

        if(type == null || type.trim().isEmpty()) {
            throw new BadRequestException("Must specify type");
        }

        try {
            return new StringMap(
                    LIMSInitializationListener.getLimsConnection().getWorkflowIds(
                            RestUtilities.getListFromString(extractionIds),
                            RestUtilities.getListFromString(loci),
                            Reaction.Type.valueOf(type)
                    ));
        } catch (DatabaseServiceException e) {
            throw new InternalServerErrorException(e.getMessage(), e);
        } catch(IllegalArgumentException e) {
            throw new BadRequestException(type + " is not valid type.");
        }
    }
}
