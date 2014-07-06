package com.biomatters.plugins.biocode.labbench.fims;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.plugins.biocode.labbench.PasswordOptions;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.FimsSample;
import com.biomatters.plugins.biocode.XmlUtilities;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.documents.DocumentField;

import java.util.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.net.HttpURLConnection;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import org.jdom.input.SAXBuilder;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.output.XMLOutputter;
import org.jdom.output.Format;

/**
 * @author Steve
 * @version $Id$
 */
public abstract class TableFimsConnection extends FIMSConnection{

    // We would add this prefix to all codes except Steve originally defined them without it and we don't want to mess
    // up users databases with extra fields.
    public static final String CODE_PREFIX = "TABLEFIMS:";

    protected String getTissueCol() {
        return tissueCol.replace(CODE_PREFIX, "");
    }

    private String tissueCol;
    private String specimenCol;
    private boolean storePlates;
    private boolean linkPhotos;
    private String plateCol;
    private String wellCol;
    private List<DocumentField> fields;
    private List<DocumentField> taxonomyFields;
    private List<DocumentField> projectFields;
    private List<DocumentField> columns;
    private boolean connected = false;

    public final PasswordOptions getConnectionOptions() {
        return _getConnectionOptions();
    }

    public abstract TableFimsConnectionOptions _getConnectionOptions();

    public final void _connect(Options optionsa) throws ConnectionException {

        TableFimsConnectionOptions options = (TableFimsConnectionOptions)optionsa;
        tissueCol = options.getTissueColumn();
        specimenCol = options.getSpecimenColumn();

        linkPhotos = options.linkPhotos();

        storePlates = options.storePlates();
        if(storePlates) {
            plateCol = options.getPlateColumn();
            wellCol = options.getWellColumn();
        }
        fields = new ArrayList<DocumentField>();

        _connect(options);
        try {
            columns = getTableColumns();
        } catch (IOException e) {
            throw new ConnectionException(e.getMessage(), e);
        }

        taxonomyFields = getFieldsFromMultipleOptions(options, TableFimsConnectionOptions.TAX_FIELDS, TableFimsConnectionOptions.TAX_COL);
        for (DocumentField field : columns) {
            if (!taxonomyFields.contains(field)) {
                fields.add(field);
            }
        }

        if(Boolean.TRUE.equals(options.getValue(TableFimsConnectionOptions.STORE_PROJECTS))) {
            projectFields = getFieldsFromMultipleOptions(options, TableFimsConnectionOptions.PROJECT_FIELDS, TableFimsConnectionOptions.PROJECT_COLUMN);
        } else {
            projectFields = Collections.emptyList();
        }


        //if the tissue or specimen id is also a taxonomy field, it won't be in the fields list, and will cause problems later on
        if(getTableCol(tissueCol) == null) {
            StringBuilder error = new StringBuilder("You have listed your tissue sample field (" + tissueCol + ") as also being a taxonomy field.  This is not allowed.");
            error.append("\n\nTaxonomy Fields:\n\n");
            for(DocumentField field : taxonomyFields) {
                error.append(field.getCode()).append(": ").append(field.getName()).append("\n");
            }
            error.append("\n\nOther Fields:\n\n");
            for(DocumentField field : fields) {
                error.append(field.getCode()).append(": ").append(field.getName()).append("\n");
            }
            
            throw new ConnectionException(error.toString(), error.toString());
        }
        if(getTableCol(specimenCol) == null) {
            throw new ConnectionException(null, "You have listed your specimen field ("+tissueCol+") as also being a taxonomy field.  This is not allowed.");
        }
        if(getTissueSampleDocumentField() == null) {
            throw new ConnectionException("You have not set a tissue column");
        }
        connected = true;
    }

    List<DocumentField> getFieldsFromMultipleOptions(TableFimsConnectionOptions options, String fieldCode, String columnCode) throws ConnectionException {
        List<DocumentField> result = new ArrayList<DocumentField>();
        List<Options> taxOptions = options.getMultipleOptions(fieldCode).getValues();
        for(Options taxOptionsValue : taxOptions){
            Options.OptionValue colValue = (Options.OptionValue)taxOptionsValue.getOption(columnCode).getValue();
            String code = XmlUtilities.encodeXMLChars(colValue.getName());
            for(DocumentField column : columns) {
                if(code.equals(column.getCode())) {
                    if(!String.class.isAssignableFrom(column.getValueType())) {
                        throw new ConnectionException("The taxonomy column '"+column.getName()+"' has an incorrect type ('"+column.getValueType().getSimpleName()+"').  All taxonomy columns must be string columns.");
                    }
                    result.add(column);
                }
            }
        }
        return result;
    }

    public abstract List<DocumentField> getTableColumns() throws IOException;

    public DocumentField getTableCol(String colName) {
        if(fields != null) {
            for(DocumentField field : fields) {
                if(field.getCode().equals(colName)) {
                    return field;
                }
            }
        }
        return null;
    }

    public abstract void _connect(TableFimsConnectionOptions options) throws ConnectionException;

    public final void disconnect() {
        connected = false;
        _disconnect();
    }

    public boolean isConnected() {
        return connected;
    }

    public abstract void _disconnect();

    public final DocumentField getTissueSampleDocumentField() {
        return getTableCol(tissueCol);
    }

    public final DocumentField getSpecimenDocumentField() {
        return getTableCol(specimenCol);
    }

    public final List<DocumentField> getCollectionAttributes() {
        return new ArrayList<DocumentField>(fields);
    }

    public final List<DocumentField> getTaxonomyAttributes() {
        return isConnected() ? new ArrayList<DocumentField>(taxonomyFields) : Collections.<DocumentField>emptyList();
    }

    public final List<DocumentField> _getSearchAttributes() {
        ArrayList<DocumentField> fields = new ArrayList<DocumentField>();
        if(isConnected()) {
            fields.addAll(this.fields);
            fields.addAll(taxonomyFields);
        }
        return fields;
    }

    public boolean storesPlateAndWellInformation() {
        return storePlates;
    }

    @Override
    public final DocumentField getPlateDocumentField() {
        if(!storePlates) {
            return null;
        }
        return getTableCol(plateCol);
    }

    @Override
    public final DocumentField getWellDocumentField() {
        if(!storePlates) {
            return null;
        }
        return getTableCol(wellCol);
    }

    public boolean hasPhotos() {
        return linkPhotos;
    }

    @Override
    public final List<String> getImageUrls(FimsSample fimsSample) throws IOException {
        URL xmlUrl = new URL("http://www.flickr.com/services/rest/?method=flickr.photos.search&format=rest&machine_tags=bioValidator:specimen="+ URLEncoder.encode("\""+fimsSample.getSpecimenId()+"\"", "UTF-8")+"&api_key=724c92d972c3822bdb9c8ff501fb3d6a");
        System.out.println(xmlUrl);
        final HttpURLConnection urlConnection = (HttpURLConnection)xmlUrl.openConnection();
        urlConnection.setRequestMethod("GET");
        InputStream in = urlConnection.getInputStream();
        SAXBuilder builder = new SAXBuilder();
        Element root;
        try {
            root = builder.build(in).detachRootElement();
        } catch (JDOMException e) {
            IOException exception = new IOException("Error parsing server response: "+e.getMessage());
            exception.initCause(e);
            throw exception;
        }
        XMLOutputter out = new XMLOutputter(Format.getPrettyFormat());
        out.output(root, System.out);
        if(root.getName().equals("rsp") && "ok".equals(root.getAttributeValue("stat"))) {
            root = root.getChild("photos");
        }
        else {
            if(root.getChild("err") != null) {
                throw new IOException(root.getChild("err").getAttributeValue("msg"));
            }
            return Collections.emptyList();
        }
        if(root == null) {
            return Collections.emptyList();
        }
        List<Element> imageUrls = root.getChildren("photo");
        List<String> result = new ArrayList<String>();
        for(Element e : imageUrls) {
            result.add("http://farm"+e.getAttributeValue("farm")+".static.flickr.com/"+e.getAttributeValue("server")+"/"+e.getAttributeValue("id")+"_"+e.getAttributeValue("secret")+"_z.jpg");
        }
        return result;
    }

    @Override
    public List<FimsProject> getProjects() throws DatabaseServiceException {
        if(getProjectFields().isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, FimsProject> projects = new HashMap<String, FimsProject>();

        List<List<String>> projectCombinations = getProjectLists();
        for (List<String> projectCombination : projectCombinations) {
            String parentName = null;
            for (String projectName : projectCombination) {
                projectName = projectName.trim();
                if(projectName.length() == 0) {
                    continue;  // Ignore empty string projects.  Probably a bug in implementation.
                }
                if (!projects.containsKey(projectName)) {
                    FimsProject parent = projects.get(parentName);
                    projects.put(projectName,
                            new FimsProject(projectName, projectName, parent));
                }
                parentName = projectName;
            }
        }
        return new ArrayList<FimsProject>(projects.values());
    }

    @Override
    public List<String> getProjectsForSamples(Collection<FimsSample> samples) {
        Set<String> projectNames = new HashSet<String>();
        for (FimsSample sample : samples) {
            String project = getProjectForSample(sample);
            if(project != null) {
                projectNames.add(project);
            }
        }
        return new ArrayList<String>(projectNames);
    }

    private String getProjectForSample(FimsSample sample) {
        List<DocumentField> projectsLowestToHighest = new ArrayList<DocumentField>(projectFields);
        Collections.reverse(projectsLowestToHighest);
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
     * Returns a list of each project combination.
     *
     * ie.
     * {
     *  {Project,SubProject,SubSubProject}
     *  {Project,SubProject2}
     *  {Project,SubProject,SubSubProject2}
     * }
     *
     * @return list of unique project lines.  May contain duplicates
     */
    protected abstract List<List<String>> getProjectLists() throws DatabaseServiceException;

    protected List<DocumentField> getProjectFields() {
        return Collections.unmodifiableList(projectFields);
    }

    /**
     * @param projects The projects to map
     * @return A mapping from {@link com.biomatters.geneious.publicapi.documents.DocumentField} to projects
     */
    protected Map<DocumentField, Collection<FimsProject>> getFieldsToProjects(List<FimsProject> projects) {
        Multimap<DocumentField, FimsProject> map = ArrayListMultimap.create();
        List<DocumentField> fields = projectFields;
        for (FimsProject project : projects) {
            int index = getProjectLevelIndex(project);
            if(index >= 0 && index < fields.size()) {
                map.put(fields.get(index), project);
            }
        }
        return map.asMap();
    }

    private int getProjectLevelIndex(FimsProject project) {
        int level = 0;
        FimsProject parent = project.getParent();
        while(parent != null) {
            parent = parent.getParent();
            level--;
        }
        return level;
    }
}
