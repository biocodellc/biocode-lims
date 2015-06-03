package com.biomatters.plugins.biocode.labbench.fims;

import com.biomatters.geneious.publicapi.components.Dialogs;
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
import com.biomatters.plugins.biocode.labbench.LoginOptions;
import com.biomatters.plugins.biocode.labbench.PasswordOptions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

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
    protected int requestTimeoutInSeconds = LoginOptions.DEFAULT_TIMEOUT;

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
        requestTimeoutInSeconds = ((Options.IntegerOption)options.getParentOptions().getOption(LoginOptions.FIMS_REQUEST_TIMEOUT_OPTION_NAME)).getValue();
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

        checkForDuplicateDocumentFields();
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
     * @param allowDuplicateNames True if projects with the same name are allowed in different parts of the hierarchy
     * @return a list of {@link com.biomatters.plugins.biocode.labbench.fims.FimsProject}
     * @throws DatabaseServiceException if there is a problem determining the overall project hierarchy
     */
    protected static List<FimsProject> getProjectsFromListOfCombinations(List<List<String>> projectCombinations, boolean allowDuplicateNames) throws DatabaseServiceException {
        Multimap<String, FimsProject> projects = ArrayListMultimap.create();

        for (List<String> projectCombination : projectCombinations) {
            String parentName = null;
            for (String projectName : projectCombination) {
                projectName = projectName.trim();
                if(projectName.length() == 0) {
                    continue;  // Ignore empty string projects.  Probably a bug in implementation.
                }
                Collection<FimsProject> projectsForName = projects.get(projectName);
                if(projectsForName == null || projectsForName.isEmpty()) {
                    FimsProject parent = getProjectFromLineage(projects, projectCombination, parentName);
                    projects.put(projectName, new FimsProject(projectName, projectName, parent));
                } else {
                    FimsProject existing = getProjectFromLineage(projects, projectCombination, projectName);
                    if(existing == null) {
                        // If there are projects for name but none that match this lineage then it's name must be duplicated
                        if(allowDuplicateNames) {
                            FimsProject existingParent = getProjectFromLineage(projects, projectCombination, parentName);
                            projects.put(projectName, new FimsProject(projectName + "." + (projectsForName.size() + 1), projectName, existingParent));
                        } else {
                            FimsProject duplicate = projectsForName.iterator().next();
                            FimsProject duplicateParent = duplicate.getParent();
                            String duplicateParentName = duplicateParent == null ? "none" : duplicateParent.getName();
                            throw new DatabaseServiceException("Inconsistent project definition.  " +
                                    "Project " + projectName + " has multiple parents (" + duplicateParentName + ", " + parentName + ")", false);
                        }
                    }
                }
                parentName = projectName;
            }
        }
        List<FimsProject> result = new ArrayList<FimsProject>();
        for (Collection<FimsProject> projs : projects.asMap().values()) {
            result.addAll(projs);
        }
        return result;
    }

    static FimsProject getProjectFromLineage(Multimap<String, FimsProject> projects, List<String> line, String projectName) {
        if(projectName == null) {
            return null;
        }
        int indexOfParent = line.indexOf(projectName);
        if(indexOfParent == -1) {
            throw new IllegalArgumentException(projectName + " not in lineage " + line);
        }
        for (FimsProject candidate : projects.get(projectName)) {
            List<String> lineage = new ArrayList<String>();
            FimsProject p = candidate;
            while(p.getParent() != null) {
                lineage.add(0, p.getParent().getName());
                p = p.getParent();
            }
            if(line.subList(0, indexOfParent).equals(lineage)) {
                return candidate;
            }
        }
        return null;
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
    public final List<DocumentField> getCollectionAttributes() {
        return sortAndRemoveDuplicates(_getCollectionAttributes());
    }

    /**
     *
     * @return list of taxonomy fields in order of highest level (eg kingdom) to lowest (eg. species).
     */
    public final List<DocumentField> getTaxonomyAttributes() {
        return sortAndRemoveDuplicates(_getTaxonomyAttributes());
    }

    /**
     * @return list of all attributes that can be searched
     */
    public final List<DocumentField> getSearchAttributes() {
        return sortAndRemoveDuplicates(_getSearchAttributes());
    }

    private void checkForDuplicateDocumentFields() throws ConnectionException {
        String duplicateSearchAttributesList = generateListOfDuplicateDocumentFields(_getSearchAttributes());
        if (!duplicateSearchAttributesList.isEmpty() && !Dialogs.showYesNoDialog(
                "The following duplications in document fields were detected in the FIMS:\n\n" +
                        duplicateSearchAttributesList + "\n\n" +
                        "Document fields will be filtered arbitrarily to eliminate the duplications.\nDo you wish to continue?",
                "Duplicate Document Fields Detected in FIMS",
                null,
                Dialogs.DialogIcon.WARNING))
            throw ConnectionException.NO_DIALOG;
    }

    private List<DocumentField> sortAndRemoveDuplicates(List<DocumentField> fields) {
        fields = removeDuplicates(fields);

        Collections.sort(fields, new Comparator<DocumentField>() {
            @Override
            public int compare(DocumentField o1, DocumentField o2) {
                return o1.getName().toLowerCase().compareTo(o2.getName().toLowerCase());
            }
        });

        return fields;
    }

    private List<DocumentField> removeDuplicates(List<DocumentField> fields) {
        List<DocumentField> fieldsWithoutDuplicates = new ArrayList<DocumentField>();
        List<List<DocumentField>> fieldsGroupedByCode = groupDocumentFieldsByCode(fields);

        for (List<DocumentField> group : fieldsGroupedByCode) {
            while (group.size() > 1)
                group.remove(group.size() - 1);  // Remove all but the the first

            fieldsWithoutDuplicates.add(group.get(0));
        }

        return fieldsWithoutDuplicates;
    }

    private String generateListOfDuplicateDocumentFields(Collection<DocumentField> fields) {
        StringBuilder listBuilder = new StringBuilder();
        List<List<DocumentField>> fieldsGroupedByCode = groupDocumentFieldsByCode(fields);

        for (List<DocumentField> group : fieldsGroupedByCode) {
            if (group.size() > 1) {
                Iterator<DocumentField> intraGroupIterator = group.iterator();
                listBuilder.append("Document fields with code [").append(group.get(0).getCode()).append("]:\n");

                while (intraGroupIterator.hasNext())
                    listBuilder.append(intraGroupIterator.next().getName()).append(intraGroupIterator.hasNext() ? ", " : "");

                listBuilder.append("\n\n");
            }
        }

        if (listBuilder.length() > 0) {
            /* Remove trailing new line characters. */
            listBuilder.deleteCharAt(listBuilder.length() - 1);
            listBuilder.deleteCharAt(listBuilder.length() - 1);
        }

        return listBuilder.toString();
    }

    private List<List<DocumentField>> groupDocumentFieldsByCode(Collection<DocumentField> fields) {
        List<List<DocumentField>> fieldsGroupedByCode = new ArrayList<List<DocumentField>>();
        String fieldCode;
        boolean foundGroupContainingFieldsWithCode;

        for (DocumentField field : fields) {
            fieldCode = field.getCode();
            foundGroupContainingFieldsWithCode = false;

            for (List<DocumentField> group : fieldsGroupedByCode)
                if (containsDocumentFieldWithCode(group, fieldCode)) {
                    group.add(field);

                    foundGroupContainingFieldsWithCode = true;

                    break;
                }

            if (!foundGroupContainingFieldsWithCode) {
                List<DocumentField> groupToContainFieldsWithCode = new ArrayList<DocumentField>();

                groupToContainFieldsWithCode.add(field);

                fieldsGroupedByCode.add(groupToContainFieldsWithCode);
            }
        }

        return fieldsGroupedByCode;
    }

    private boolean containsDocumentFieldWithCode(Collection<DocumentField> fields, String code) {
        for (DocumentField field : fields)
            if (field.getCode().equals(code))
                return true;
        return false;
    }

    /**
     *
     * @see #getCollectionAttributes()
     */
    protected abstract List<DocumentField> _getCollectionAttributes();

    /**
     *
     * @see #getTaxonomyAttributes()
     */
    protected abstract List<DocumentField> _getTaxonomyAttributes();

    /**
     * A complete list of all attributes provided by the FIMSConnection.  Implementations MUST include the results of
     * {@link #_getCollectionAttributes()} and {@link #getTaxonomyAttributes()} in the returned list.
     *
     * @see #getSearchAttributes()
     */
    protected abstract List<DocumentField> _getSearchAttributes();

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
                    Condition.DATE_AFTER,
                    Condition.DATE_AFTER_OR_ON,
                    Condition.DATE_BEFORE,
                    Condition.DATE_BEFORE_OR_ON
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
     * Return tissue IDs for FIMS entries that match a specified {@link com.biomatters.geneious.publicapi.databaseservice.Query}
     *
     * @param query The query to match
     * @param projectsToMatch A list of projects to restrict the search to
     * @param allowEmptyQuery whether allow empty query string
     * @return a list of tissue IDs
     *
     * @throws ConnectionException if there is a problem communicating with the database
     */
    public abstract List<String> getTissueIdsMatchingQuery(Query query, List<FimsProject> projectsToMatch, boolean allowEmptyQuery) throws ConnectionException;

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
