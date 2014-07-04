package com.biomatters.plugins.biocode.server.security;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
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
        Set<FimsSample> samples = new HashSet<FimsSample>();
        for (Plate plate : plates) {
            for (Reaction reaction : plate.getReactions()) {
                samples.add(reaction.getFimsSample());  // Can this be null?
            }
        }

        checkUserHasRoleForSamples(samples, role);
    }

    public static void checkUserHasRoleForSamples(Collection<FimsSample> samples, Role role) throws DatabaseServiceException {
        List<String> projectIds = LIMSInitializationListener.getFimsConnection().getProjectsForSamples(samples);
        List<FimsProject> projectsUserCanWriteTo = Projects.getFimsProjectsUserHasAtLeastRole(
                LIMSInitializationListener.getDataSource(),
                LIMSInitializationListener.getFimsConnection(),
                Users.getLoggedInUser(), role);

        for (String projectId : projectIds) {
            boolean found = false;
            for (FimsProject fimsProject : projectsUserCanWriteTo) {
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
        List<FimsSample> samples = new ArrayList<FimsSample>();
        for (Reaction reaction : reactionList) {
            samples.add(reaction.getFimsSample());
        }
        checkUserHasRoleForSamples(samples, role);
    }
}
