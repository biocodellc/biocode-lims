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
    public void getProjectFromLineageWorks() {
        Multimap<String, FimsProject> projects = ArrayListMultimap.create();
        FimsProject parent = new FimsProject("1", "1", null);
        FimsProject child = new FimsProject("1.1", "1.1", parent);
        FimsProject parent2 = new FimsProject("2", "2", null);

        projects.put("1", parent);
        projects.put("1.1", child);
        projects.put("2", parent2);

        assertEquals(parent, FIMSConnection.getProjectFromLineage(projects, Arrays.asList("1"), parent.getName()));
        assertEquals(parent2, FIMSConnection.getProjectFromLineage(projects, Arrays.asList("2"), parent2.getName()));
        assertEquals(child, FIMSConnection.getProjectFromLineage(projects, Arrays.asList("1", "1.1"), child.getName()));

        Multimap<String, FimsProject> noProjects = ArrayListMultimap.create();
        assertNull(FIMSConnection.getProjectFromLineage(noProjects, Arrays.asList("1", "1.1"), "1.1"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void getProjectFromLineageThrowsExceptionWhenNameNotInLine() {
        Multimap<String, FimsProject> projects = ArrayListMultimap.create();
        FimsProject parent = new FimsProject("1", "1", null);
        FimsProject child = new FimsProject("1.1", "1.1", parent);
        FimsProject parent2 = new FimsProject("2", "2", null);

        projects.put("1", parent);
        projects.put("1.1", child);
        projects.put("2", parent2);

        FIMSConnection.getProjectFromLineage(projects, Arrays.asList("1", "1.1"), "3");
    }

    @Test
    public void getProjectFromLineageWorksWithDuplicateNamesDifferentParents() {
        Multimap<String, FimsProject> projects = ArrayListMultimap.create();
        FimsProject parent = new FimsProject("1", "1", null);
        FimsProject parent2 = new FimsProject("2", "2", null);
        FimsProject child = new FimsProject("1.3", "3", parent);
        FimsProject child2 = new FimsProject("2.3", "3", parent2);

        projects.put("1", parent);
        projects.put("2", parent2);
        projects.put("3", child);
        projects.put("3", child2);

        assertEquals(child, FIMSConnection.getProjectFromLineage(projects, Arrays.asList("1", "3"), child.getName()));
        assertEquals(child2, FIMSConnection.getProjectFromLineage(projects, Arrays.asList("2", "3"), child2.getName()));
    }

    @Test
    public void getProjectFromLineageWorksWithChildSameNameAsParent() {
        Multimap<String, FimsProject> projects = ArrayListMultimap.create();
        FimsProject parent = new FimsProject("1", "1", null);
        FimsProject parentOfChild = new FimsProject("2", "2", null);
        FimsProject child = new FimsProject("2.1", "1", parentOfChild);

        projects.put("1", parent);
        projects.put("2", parentOfChild);
        projects.put("1", child);

        assertEquals(child, FIMSConnection.getProjectFromLineage(projects, Arrays.asList("2", "1"), child.getName()));
        assertEquals(parent, FIMSConnection.getProjectFromLineage(projects, Arrays.asList("1"), parent.getName()));
    }

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

    @Test
    public void getProjectsFromListsCanHandleSameNameDifferentParent() throws DatabaseServiceException {
        List<String> line1 = Arrays.asList("1", "2");
        List<String> line2 = Arrays.asList("3,", "2");
        testProjectsFromLines(Arrays.asList(line1, line2), true);
    }

    void testProjectsFromLines(List<List<String>> combos, boolean allowDuplicateNames) throws DatabaseServiceException {
        List<FimsProject> projects;
        projects = FIMSConnection.getProjectsFromListOfCombinations(combos, allowDuplicateNames);

        Set<String> uniqueNames = new HashSet<String>();
        for (List<String> combo : combos) {
            uniqueNames.addAll(combo);
        }
        if(!allowDuplicateNames) {
            assertEquals(uniqueNames.size(), projects.size());
        }

        Multimap<String, FimsProject> found = ArrayListMultimap.create();
        for (FimsProject p : projects) {
            found.put(p.getName(), p);
        }

        for (List<String> line : combos) {
            for(int i=line.size()-1; i>=0; i--) {
                String name = line.get(i);
                FimsProject fimsProject = FIMSConnection.getProjectFromLineage(found, line, name);
                assertNotNull(fimsProject);

                FimsProject expectedParent;
                if(i > 0) {
                    expectedParent = FIMSConnection.getProjectFromLineage(found, line, line.get(i - 1));
                } else {
                    expectedParent = null;
                }
                assertEquals("Wrong parent for " + fimsProject.getName() + ". Expected " + nameForProject(expectedParent) + "(" + idForProject(expectedParent) + ")" +
                                " but got " + nameForProject(fimsProject.getParent()) + "(" + idForProject(fimsProject.getParent()) + ")",
                        expectedParent, fimsProject.getParent());
            }
        }
    }

    private String idForProject(FimsProject expectedParent) {
        return (expectedParent == null ? "no parent" : expectedParent.getId());
    }

    private String nameForProject(FimsProject expectedParent) {
        return (expectedParent == null ? "no parent" : expectedParent.getName());
    }
}
