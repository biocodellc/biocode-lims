package com.biomatters.plugins.biocode.labbench.fims;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.geneious.publicapi.databaseservice.RetrieveCallback;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.Condition;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.FimsSample;
import com.biomatters.plugins.biocode.labbench.PasswordOptions;

import java.util.*;
import java.lang.ref.SoftReference;
import java.io.IOException;

/**
 *
 * Represents a connection to a field management database.  The database structure is flattened, and the user is only
 * able to query tissue sample records, which have attributes taken from the rest of the field management database. <br>
 * User: steve
 * Date: 11/05/2009
 * Time: 6:16:57 PM
 */
public abstract class FIMSConnection {

    protected static String getProjectForSample(List<DocumentField> projectsLowestToHighest, FimsSample sample) {
        for (DocumentField projectField : projectsLowestToHighest) {
            Object projectNameCandidate = sample.getFimsAttributeValue(projectField.getCode());
            if(projectNameCandidate != null) {
                String name = projectNameCandidate.toString().trim();
                if(name.length() > 0) {
                    return name;
                }
            }
        }
        return null;
    }

    /**
     *
     * @return a user-friendly name for this connection
     */
    public abstract String getLabel();

    /**
     *
     * @return a unique identifier for this connection
     */
    public abstract String getName();

    /**
     *
     * @return a user friendly description of this connection
     */
    public abstract String getDescription();

    /**
     * @return some options allowing the user to enter all necessary information to connect to the database
     * (eg username/password, server address etc)
     */
    public abstract PasswordOptions getConnectionOptions();

    public void connect(Options options) throws ConnectionException {
        _connect(options);

        if(getTissueSampleDocumentField() == null) {
            throw new ConnectionException("You have an empty tissue sample field.  Please check your FIMS connection options");
        }

        if(getTaxonomyAttributes() == null || getTaxonomyAttributes().isEmpty()) {
            throw new ConnectionException("You must have at least one taxonomy field.  Please check your FIMS connection options");
        }

        if(storesPlateAndWellInformation()) {
            if(getPlateDocumentField() == null) {
                throw new ConnectionException("You have specified that your FIMS connection contains plate information, but you have not specified a plate field.  Please check your FIMS connection options");
            }
            if(getWellDocumentField() == null) {
                throw new ConnectionException("You have specified that your FIMS connection contains plate information, but you have not specified a well field.  Please check your FIMS connection options");    
            }
        }
    }

    /**
     *  connects to the field management database
     * @param options the options taken from {@link #getConnectionOptions()} and passed to the user.
     * @throws com.biomatters.plugins.biocode.labbench.ConnectionException if the client is unable to connect - either because of a connection error, or bad credentials
     */
    public abstract void _connect(Options options) throws ConnectionException;

    public abstract void disconnect();

    public abstract DocumentField getTissueSampleDocumentField();

    /**
     * <p>Helper method to generate a list of {@link com.biomatters.plugins.biocode.labbench.fims.FimsProject} from a list
     * of combinations.</p>
     *
     * <p><strong>Note</strong>: Lists must not contain null.</p>
     *
     * i.e.
     * <pre>
     * {
     *    {Project,SubProject, SubSubProject}
     *    {Project,SubProject, SubSubProject2}
     *    {Project,SubProject2, SubSubProject3}
     * }</pre>
     *
     * <pre>
     * Will produce a hierarchy of
     * Project
     *  |-SubProject
     *  |   |-SubProject
     *  |        |-SubSubProject
     *  |        |-SubSubProject2
     *  |-SubProject2
     *      |-SubSubProject3
     * </pre>
     *
     * @param projectCombinations List of project combinations.
     * @param allowDuplicateNamesBetweenLevels True if projects with the same name are allowed in different parts of the hierarchy
     * @return a list of {@link com.biomatters.plugins.biocode.labbench.fims.FimsProject}
     * @throws DatabaseServiceException if there is a problem determining the overall project hierarchy
     */
    protected static List<FimsProject> getProjectsFromListOfCombinations(List<List<String>> projectCombinations, boolean allowDuplicateNamesBetweenLevels) throws DatabaseServiceException {
        Map<Integer, Map<String, FimsProject>> allProjects = new HashMap<Integer, Map<String, FimsProject>>();

        for (List<String> projectCombination : projectCombinations) {
            String parentName = null;
            int level = 1;
            for (String projectName : projectCombination) {

                Map<String, FimsProject> projects = allProjects.get(level);
                if(projects == null) {
                    projects = new HashMap<String, FimsProject>();
                    allProjects.put(level, projects);
                }

                projectName = projectName.trim();
                if(projectName.length() == 0) {
                    continue;  // Ignore empty string projects.  Probably a bug in implementation.
                }
                FimsProject project = projects.get(projectName);
                if (project == null) {
                    String idToUse = projectName;
                    for (Map<String, FimsProject> map : allProjects.values()) {
                        if(map.containsKey(projectName)) {
                            if(!allowDuplicateNamesBetweenLevels) {
                                throw new DatabaseServiceException(projectName + " exists in multiple locations in the project hierarchy.", false);
                            } else {
                                idToUse = projectName + "." + level;
                            }
                        }
                    }

                    Map<String, FimsProject> parentProjects = level > 1 ? allProjects.get(level - 1) : Collections.<String, FimsProject>emptyMap();
                    FimsProject parent = parentProjects.get(parentName);
                    projects.put(projectName, new FimsProject(idToUse, projectName, parent));
                } else {
                    // Verify existing project has the same parents
                    FimsProject existingParent = project.getParent();
                    String existingParentName = existingParent == null ? null : existingParent.getName();

                    if(!String.valueOf(parentName).equals(String.valueOf(existingParentName))) {
                        throw new DatabaseServiceException("Inconsistent project definition.  " +
                                "Project " + projectName + " has multiple parents (" + existingParentName + ", " + parentName + ")", false);
                    }
                }
                parentName = projectName;
                level++;
            }
        }
        List<FimsProject> result = new ArrayList<FimsProject>();
        for (Map<String, FimsProject> map : allProjects.values()) {
            result.addAll(map.values());
        }
        return result;
    }

    /**
     * Get the list of projects the specified samples belong to.  Use the result of {@link #getProjects()} to match up
     * the name to the project hierarchy.
     *
     * @return The names of the projects for the specified samples.
     */
    public abstract Map<String, Collection<FimsSample>> getProjectsForSamples(Collection<FimsSample> samples);

    /**
     * Implementations may find the helper method {@link #getProjectsFromListOfCombinations(java.util.List, boolean)} useful.
     *
     * @return A list of all projects in the system.
     */
    public abstract List<FimsProject> getProjects() throws DatabaseServiceException;

    /**
     * @return list of non-taxonomy fields
     */
    public abstract List<DocumentField> getCollectionAttributes();

    /**
     *
     * @return list of taxonomy fields in order of highest level (eg kingdom) to lowest (eg. species).
     */
    public abstract List<DocumentField> getTaxonomyAttributes();

    /**
     * @return list of all attributes that can be searched
     */
    public final List<DocumentField> getSearchAttributes() {
        List<DocumentField> list = new ArrayList<DocumentField>(_getSearchAttributes());
        Collections.sort(list, new Comparator<DocumentField>() {
            @Override
            public int compare(DocumentField o1, DocumentField o2) {
                return o1.getName().toLowerCase().compareTo(o2.getName().toLowerCase());
            }
        });
        return list;
    }
    public abstract List<DocumentField> _getSearchAttributes();

    public DocumentField getLatitudeField() { return null; }
    public DocumentField getLongitudeField() { return null; }

    public final BiocodeUtilities.LatLong getLatLong(AnnotatedPluginDocument annotatedDocument) {
        DocumentField latField = getLatitudeField();
        DocumentField longField = getLongitudeField();
        if(latField == null || longField == null) {
            return null;
        }
        Object latObject = annotatedDocument.getFieldValue(latField);
        Object longObject = annotatedDocument.getFieldValue(longField);
        if (latObject == null || longObject == null) {
            return null;
        }
        return new BiocodeUtilities.LatLong((Double)latObject, (Double)longObject);
    }

    public Condition[] getFieldConditions(Class fieldClass) {
        if(Integer.class.equals(fieldClass) || Double.class.equals(fieldClass)) {
            return new Condition[] {
                    Condition.EQUAL,
                    Condition.NOT_EQUAL,
                    Condition.GREATER_THAN,
                    Condition.GREATER_THAN_OR_EQUAL_TO,
                    Condition.LESS_THAN,
                    Condition.LESS_THAN_OR_EQUAL_TO
            };
        }
        else if(String.class.equals(fieldClass)) {
            return new Condition[] {
                    Condition.CONTAINS,
                    Condition.EQUAL,
                    Condition.NOT_EQUAL,
                    Condition.NOT_CONTAINS,
                    Condition.STRING_LENGTH_GREATER_THAN,
                    Condition.STRING_LENGTH_GREATER_THAN,
                    Condition.BEGINS_WITH,
                    Condition.ENDS_WITH
            };
        }
        else if(Date.class.equals(fieldClass)) {
            return new Condition[] {
                    Condition.EQUAL,
                    Condition.NOT_EQUAL,
                    Condition.GREATER_THAN,
                    Condition.GREATER_THAN_OR_EQUAL_TO,
                    Condition.LESS_THAN,
                    Condition.LESS_THAN_OR_EQUAL_TO
            };
        }
        else {
            return new Condition[] {
                    Condition.EQUAL,
                    Condition.NOT_EQUAL
            };
        }
    }

    public final List<FimsSample> getMatchingSamples(Query query) throws ConnectionException {
        List<String> tissueIds = getTissueIdsMatchingQuery(query, null);
        return retrieveSamplesForTissueIds(tissueIds);
    }

    /**
     * Return tissue IDs for FIMS entries that match a specified {@link com.biomatters.geneious.publicapi.databaseservice.Query}
     *
     * @param query The query to match
     * @param projectsToMatch A list of projects to restrict the search to
     * @return a list of tissue IDs
     *
     * @throws ConnectionException if there is a problem communicating with the database
     */
    public abstract List<String> getTissueIdsMatchingQuery(Query query, List<FimsProject> projectsToMatch) throws ConnectionException;

    /**
     * Retrieve {@link com.biomatters.plugins.biocode.labbench.FimsSample} for the specified tissue IDs
     *
     *
     * @param tissueIds The ids to find samples for
     * @param callback Callback to add results to.  May be null.
     * @return A list of all the samples found
     *
     * @throws ConnectionException if there is a problem communicating with the FIMS
     */
    protected abstract List<FimsSample> _retrieveSamplesForTissueIds(List<String> tissueIds, RetrieveCallback callback) throws ConnectionException;

    private Map<String, SoftReference<FimsSample>> sampleCache = new HashMap<String, SoftReference<FimsSample>>();

    public final FimsSample getFimsSampleFromCache(String tissueId) {
        SoftReference<FimsSample> fimsSampleSoftReference = sampleCache.get(tissueId);
        return fimsSampleSoftReference != null ? fimsSampleSoftReference.get() : null;
    }

    public abstract int getTotalNumberOfSamples() throws ConnectionException;

    /**
     * Retrieve {@link com.biomatters.plugins.biocode.labbench.FimsSample} for the specified tissue IDs.  If the sample
     * has been retrieved with this method previously, a cached copy may be returned.
     *
     * @param tissueIds The ids to find samples for
     * @return A list of all the samples found
     *
     * @throws ConnectionException if there is a problem communicating with the FIMS
     */
    public final List<FimsSample> retrieveSamplesForTissueIds(Collection<String> tissueIds) throws ConnectionException{
        List<String> samplesToSearch = new ArrayList<String>();
        List<FimsSample> samplesToReturn = new ArrayList<FimsSample>();

        for(String s : tissueIds) {
            SoftReference<FimsSample> tissueSampleWeakReference = sampleCache.get(s);
            if(tissueSampleWeakReference != null && tissueSampleWeakReference.get() != null) {
                FimsSample sample = tissueSampleWeakReference.get();
                    samplesToReturn.add(sample);
            }
            else {
                samplesToSearch.add(s);
            }
        }

        if(samplesToSearch.size() > 0) {
            for(FimsSample sample : _retrieveSamplesForTissueIds(samplesToSearch, null)) {
                String sampleId = sample.getId();
                if(sampleId == null || sampleId.trim().length() == 0) {
                    continue;
                }
                sampleCache.put(sampleId, new SoftReference<FimsSample>(sample));
                samplesToReturn.add(sample);
            }
        }

        return samplesToReturn;
    }

    public abstract DocumentField getPlateDocumentField();

    public abstract DocumentField getWellDocumentField();

    public abstract boolean storesPlateAndWellInformation();

    public Map<String, String> getTissueIdsFromFimsTissuePlate(String plateId) throws ConnectionException{
        if(storesPlateAndWellInformation()) {
            DocumentField plateField = getPlateDocumentField();
            DocumentField wellField = getWellDocumentField();

            Query query = Query.Factory.createFieldQuery(plateField, Condition.EQUAL, plateId);
            List<FimsSample> samples = getMatchingSamples(query);

            Map<String, String> results = new HashMap<String, String>();

            for(FimsSample sample : samples) {
                Object wellValue = sample.getFimsAttributeValue(wellField.getCode());
                if(wellValue != null && wellField.toString().length() > 0) {
                    results.put(wellValue.toString(), sample.getId());
                }
            }

            return results;
        }
        return Collections.emptyMap();
    }

    public abstract boolean hasPhotos();

    public  List<String> getImageUrls(FimsSample fimsSample) throws IOException {
        return Collections.emptyList();
    }
}
