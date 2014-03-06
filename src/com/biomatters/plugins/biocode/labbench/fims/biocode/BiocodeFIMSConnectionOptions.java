package com.biomatters.plugins.biocode.labbench.fims.biocode;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.plugins.biocode.BiocodePlugin;
import com.biomatters.plugins.biocode.labbench.PasswordOptions;
import com.biomatters.plugins.biocode.labbench.fims.TableFimsConnection;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Created by matthew on 1/02/14.
 */
public class BiocodeFIMSConnectionOptions extends PasswordOptions {

    ComboBoxOption<ProjectOptionValue> projectOption;

    public BiocodeFIMSConnectionOptions() {
        super(BiocodePlugin.class);

        final List<ProjectOptionValue> projectOptions = new ArrayList<ProjectOptionValue>();
        List<Project> projectCache = getProjectCache();
        if(projectCache == null) {
            projectOptions.add(new ProjectOptionValue(new Project(1, "IndoP",
                    "IndoPacific Database", "https://biocode-fims.googlecode.com/svn/trunk/Documents/IndoPacific/indoPacificConfiguration.xml")));
        } else {
            for (Project project : projectCache) {
                projectOptions.add(new ProjectOptionValue(project));
            }
        }
        projectOption = addComboBoxOption("project", "Project:", projectOptions, projectOptions.get(0));

        new Thread() {
            public void run() {
                try {
                    List<Project> projects = BiocodeFIMSUtils.getProjects();
                    cacheProjects(projects);

                    final List<ProjectOptionValue> optionValues = new ArrayList<ProjectOptionValue>();
                    for (Project project : projects) {
                        optionValues.add(new ProjectOptionValue(project));
                    }

                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            projectOption.setPossibleValues(optionValues);
                        }
                    });
                } catch (DatabaseServiceException e) {
                    Dialogs.showMessageDialog("Failed to load expedition list from " + BiocodeFIMSConnection.HOST);
                }
            }
        }.start();
    }

    private static final String CACHE_NAME = "cachedProjects";
    private static final String ID = "id";
    private static final String CODE = "code";
    private static final String TITLE = "title";
    private static final String XML = "xmlConfigLocation";

    /**
     * Stores expeditions to a cache to be retrieved when Options are created to avoid the delay that is
     * required to query the web service for the live list of expeditions.
     *
     * @param projects
     */
    private void cacheProjects(List<Project> projects) {
        try {
            Preferences cacheNode = getCacheNode();
            cacheNode.clear();
            for (Project project : projects) {
                Preferences childNode = cacheNode.node(project.code);
                childNode.putInt(ID, project.id);
                childNode.put(CODE, project.code);
                childNode.put(TITLE, project.title);
                childNode.put(XML, project.xmlLocation);
            }
            cacheNode.flush();
        } catch (BackingStoreException e) {
            e.printStackTrace();  // Won't be able to store anything in the cache.  Oh well
        }
    }

    /**
     *
     * @return A list of {@link Project}s retrieved previously or null if the cache is empty or if there
     * is a problem retrieving the cache from preferences
     */
    private List<Project> getProjectCache() {
        try {
            List<Project> fromCache = new ArrayList<Project>();
            Preferences cacheNode = getCacheNode();
            String[] children = cacheNode.childrenNames();
            if(children == null || children.length == 0) {
                return null;
            }
            for (String child : children) {
                Preferences projectNode = cacheNode.node(child);
                int id = projectNode.getInt(ID, -1);
                String code = projectNode.get(CODE, null);
                String title = projectNode.get(TITLE, null);
                String xml = projectNode.get(XML, null);
                if(id != -1 && code != null && title != null && xml != null) {
                    fromCache.add(new Project(id, code, title, xml));
                }
            }
            return fromCache;
        } catch (BackingStoreException e) {
            e.printStackTrace();
            return null;  // Won't be able to use the cache, but oh well.
        }
    }

    private Preferences getCacheNode() {
        Preferences preferences = Preferences.userNodeForPackage(BiocodeFIMSConnection.class);
        return preferences.node(CACHE_NAME);
    }

    private static class ProjectOptionValue extends OptionValue {
        Project project;

        ProjectOptionValue(Project project) {
            super(project.code, project.title);
            this.project = project;
        }
    }


    public List<OptionValue> getFieldsAsOptionValues() throws DatabaseServiceException {
        List<OptionValue> fields = new ArrayList<OptionValue>();
        for (Project.Field field : projectOption.getValue().project.getFields()) {
            // todo Should we be using the uri of the column.  ie darwin core term
            fields.add(new OptionValue(TableFimsConnection.CODE_PREFIX + field.name, field.name));
        }
        return fields;
    }



    public Project getExpedition() {
        return projectOption.getValue().project;
    }
}
