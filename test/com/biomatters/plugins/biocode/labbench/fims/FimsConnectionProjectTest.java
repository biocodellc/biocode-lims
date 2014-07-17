package com.biomatters.plugins.biocode.labbench.fims;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import org.junit.Assert;
import org.junit.Test;

import java.util.*;

/**
 * @author Matthew Cheung
 *         Created on 17/07/14 3:49 PM
 */
@SuppressWarnings("unchecked")
public class FimsConnectionProjectTest extends Assert {

    @Test
    public void getProjectsFromListsWorksAsExpected() throws DatabaseServiceException {
        List<String> line1 = Arrays.asList("1", "2", "3");
        List<String> line2 = Arrays.asList("4", "5");
        List<String> line3 = Arrays.asList("6");
        testProjectsFromLines(Arrays.asList(line1), false);
        testProjectsFromLines(Arrays.asList(line1, line2), false);
        testProjectsFromLines(Arrays.asList(line1, line2, line3), false);
        testProjectsFromLines(Arrays.asList(line1, line3), false);
    }

    @Test
    public void getProjectsFromListsHandlesDuplicateLinesWithoutCreatingDuplicateProjects() throws DatabaseServiceException {
        List<String> line1 = Arrays.asList("1", "2", "3");
        List<String> line2 = Arrays.asList("4", "5");
        List<String> line3 = Arrays.asList("6");
        testProjectsFromLines(Arrays.asList(line1, line1, line1), false);
        testProjectsFromLines(Arrays.asList(line1, line2, line1, line2), false);
        testProjectsFromLines(Arrays.asList(line1, line2, line3, line3), false);
        testProjectsFromLines(Arrays.asList(line1, line3, line2, line2), false);
    }

    @Test(expected = DatabaseServiceException.class)
    public void getProjectsFromListsThrowsExceptionWhenBadHierarchy() throws DatabaseServiceException {
        List<String> line1 = Arrays.asList("1", "2", "3");
        List<String> line2 = Arrays.asList("2", "3");
        testProjectsFromLines(Arrays.asList(line1, line2), false);

        List<String> line3 = Arrays.asList("1", "1");
        testProjectsFromLines(Arrays.asList(line3, line3), false);
    }

    @Test
    public void getProjectsFromListsCanHandleDuplicateNamesOnDifferentLevels() throws DatabaseServiceException {
        List<String> line1 = Arrays.asList("1", "2", "3");
        List<String> line2 = Arrays.asList("2");
        List<String> line3 = Arrays.asList("3");
        List<String> line4 = Arrays.asList("2", "3");
        testProjectsFromLines(Arrays.asList(line1, line2), true);
        testProjectsFromLines(Arrays.asList(line1, line2, line3), true);
        testProjectsFromLines(Arrays.asList(line1, line4), true);
        testProjectsFromLines(Arrays.asList(line1, line2, line3, line4), true);
    }

    void testProjectsFromLines(List<List<String>> combos, boolean allowDuplicateNamesOnDifferentLevels) throws DatabaseServiceException {
        List<FimsProject> projects;
        projects = FIMSConnection.getProjectsFromListOfCombinations(combos, allowDuplicateNamesOnDifferentLevels);

        Set<String> uniqueNames = new HashSet<String>();
        for (List<String> combo : combos) {
            uniqueNames.addAll(combo);
        }
        if(!allowDuplicateNamesOnDifferentLevels) {
            assertEquals(uniqueNames.size(), projects.size());
        }

        Multimap<String, FimsProject> found = ArrayListMultimap.create();
        for (FimsProject p : projects) {
            found.put(p.getName(), p);
        }

        for (List<String> line : combos) {
            for(int i=line.size()-1; i>=0; i--) {
                String name = line.get(i);
                FimsProject fimsProject = getFimsProject(found, name, i);
                assertNotNull(fimsProject);

                FimsProject expectedParent;
                if(i > 0) {
                    expectedParent = getFimsProject(found, line.get(i-1), i-1);
                } else {
                    expectedParent = null;
                }
                assertEquals(expectedParent, fimsProject.getParent());
            }
        }
    }

    private FimsProject getFimsProject(Multimap<String, FimsProject> found, String name, int level) {
        Collection<FimsProject> candidates = found.get(name);
        return getFimsProjectThatMatchesLevel(candidates, level);
    }

    private FimsProject getFimsProjectThatMatchesLevel(Collection<FimsProject> candidates, int level) {
        for (FimsProject candidate : candidates) {
            int candidateLevel = 0;
            FimsProject p = candidate;
            while(p.getParent() != null) {
                p = p.getParent();
                candidateLevel++;
            }
            if(candidateLevel == level) {
                return candidate;
            }
        }
        return null;
    }
}
