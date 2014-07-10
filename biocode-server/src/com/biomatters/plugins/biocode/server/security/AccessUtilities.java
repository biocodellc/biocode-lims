package com.biomatters.plugins.biocode.server.security;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.FimsSample;
import com.biomatters.plugins.biocode.labbench.fims.FimsProject;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;
import com.biomatters.plugins.biocode.server.LIMSInitializationListener;

import javax.ws.rs.ForbiddenException;
import java.util.*;

/**
 * @author Matthew Cheung
 *         Created on 4/07/14 1:21 PM
 */
public class AccessUtilities {
    /**
     * Throws a {@link javax.ws.rs.ForbiddenException} if the current logged in user cannot edit the plates specified
     * @param plates a list of {@link com.biomatters.plugins.biocode.labbench.plates.Plate}s to check
     * @param role
     */
    public static void checkUserHasRoleForPlate(List<Plate> plates, Role role) throws DatabaseServiceException {
        Set<String> extractionIdsFromPlates = getExtractionIdsFromPlates(plates);
        checkUserHasRoleForExtractionIds(extractionIdsFromPlates, role);
    }

    public static void checkUserHasRoleForExtractionIds(Collection<String> extractionIds, Role role) throws DatabaseServiceException {
        List<FimsSample> samples;
        Map<String, String> extractionIdToSampleId;
        try {
            extractionIdToSampleId = LIMSInitializationListener.getLimsConnection().getTissueIdsForExtractionIds(
                   "extraction", new ArrayList<String>(extractionIds));
            samples = LIMSInitializationListener.getFimsConnection().retrieveSamplesForTissueIds(extractionIdToSampleId.values());
        } catch (ConnectionException e) {
            throw new DatabaseServiceException(e, e.getMainMessage(), false);
        }
        checkUserHasRoleForSamples(samples, role);
    }

    public static void checkUserHasRoleForSamples(Collection<FimsSample> samples, Role role) throws DatabaseServiceException {
        Map<String, Collection<FimsSample>> projectMap = LIMSInitializationListener.getFimsConnection().getProjectsForSamples(samples);
        List<FimsProject> projectsUserHasRoleFor = Projects.getFimsProjectsUserHasAtLeastRole(
                LIMSInitializationListener.getDataSource(),
                LIMSInitializationListener.getFimsConnection(),
                Users.getLoggedInUser(), role);

        for (String projectId : projectMap.keySet()) {
            boolean found = false;
            for (FimsProject fimsProject : projectsUserHasRoleFor) {
                if(fimsProject.getId().equals(projectId)) {
                    found = true;
                }
            }
            if(!found) {
                throw new ForbiddenException("User cannot access project: " + projectId);
            }
        }
    }

    /**
     * Throws a {@link javax.ws.rs.ForbiddenException} if the current logged in user cannot edit the plates specified
     * @param reactionList a list of {@link com.biomatters.plugins.biocode.labbench.reaction.Reaction}s to check
     */
    public static void checkUserHasRoleForReactions(Collection<? extends Reaction> reactionList, Role role) throws DatabaseServiceException {
        Set<String> extractionIds = new HashSet<String>();
        for (Reaction reaction : reactionList) {
            extractionIds.add(reaction.getExtractionId());
        }
        checkUserHasRoleForExtractionIds(extractionIds, role);
    }

    /**
     * @param user The user to check for
     * @param allSamples A list of all {@link com.biomatters.plugins.biocode.labbench.FimsSample} to examine
     * @param role The role to check for
     * @return A list of IDs for samples of the supplied list that are readable
     * @throws com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException if there is a problem communicating with the FIMS or LIMS
     */
    public static Set<String> getSampleIdsUserHasRoleFor(User user, List<FimsSample> allSamples, Role role) throws DatabaseServiceException {
        List<FimsProject> readableProjects = Projects.getFimsProjectsUserHasAtLeastRole(
                LIMSInitializationListener.getDataSource(), LIMSInitializationListener.getFimsConnection(),
                user, role);
        Map<String, Collection<FimsSample>> mappedSamples = LIMSInitializationListener.getFimsConnection().getProjectsForSamples(allSamples);
        Set<String> validSampleIds = new HashSet<String>();
        for (Map.Entry<String, Collection<FimsSample>> entry : mappedSamples.entrySet()) {
            for (FimsProject readableProject : readableProjects) {
                if(readableProject.getId().equals(entry.getKey())) {
                    for (FimsSample fimsSample : entry.getValue()) {
                        validSampleIds.add(fimsSample.getId());
                    }
                }
            }
        }
        return validSampleIds;
    }

    public static Set<String> getExtractionIdsFromPlates(List<Plate> plates) {
        Set<String> ids = new HashSet<String>();
        for (Plate plate : plates) {
            for (Reaction reaction : plate.getReactions()) {
                if(!reaction.isEmpty()) {
                    ids.add(reaction.getExtractionId());
                }
            }
        }
        return ids;
    }
}
