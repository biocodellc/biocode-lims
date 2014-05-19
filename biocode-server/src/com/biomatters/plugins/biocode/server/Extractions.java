package com.biomatters.plugins.biocode.server;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.plugins.biocode.labbench.reaction.ExtractionReaction;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;

import javax.ws.rs.*;
import java.util.Arrays;

/**
 * @author Matthew Cheung
 * @version $Id$
 *          <p/>
 *          Created on 7/04/14 1:16 AM
 */
@Path("extractions")
public class Extractions {

    @GET
    @Produces("application/xml")
    public XMLSerializableList<ExtractionReaction> getForBarcodes(@QueryParam("barcodes")String barcodes) {
        if(barcodes == null || barcodes.trim().isEmpty()) {
            throw new BadRequestException("Must specify barcodes");
        }

        try {
            return new XMLSerializableList<ExtractionReaction>(ExtractionReaction.class,
                    LIMSInitializationListener.getLimsConnection().getExtractionsFromBarcodes(Arrays.asList(barcodes.split(","))));
        } catch (DatabaseServiceException e) {
            throw new InternalServerErrorException(e.getMessage(), e);
        }
    }

    @GET
    @Produces("application/xml")
    @Path("workflows")
    public StringMap getWorkflowsForExtractionIds(@QueryParam("extractionIds")String extractionIds,
                                                  @QueryParam("loci")String loci, @QueryParam("type")String type) {
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
                            Arrays.asList(extractionIds.split(",")),
                            Arrays.asList(loci.split(",")),
                            Reaction.Type.valueOf(type)
                    ));
        } catch (DatabaseServiceException e) {
            throw new InternalServerErrorException(e.getMessage(), e);
        } catch(IllegalArgumentException e) {
            throw new BadRequestException(type + " is not valid type.");
        }
    }
}
