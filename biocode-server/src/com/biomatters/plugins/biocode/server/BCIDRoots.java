package com.biomatters.plugins.biocode.server;

import com.biomatters.plugins.biocode.labbench.lims.BCIDRoot;
import com.biomatters.plugins.biocode.labbench.lims.LimsDatabaseConstants;
import com.biomatters.plugins.biocode.utilities.SqlUtilities;

import javax.ws.rs.*;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.Response;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Gen Li
 *         Created on 5/08/14 4:03 PM
 */
@Path("bcid-roots")
public class BCIDRoots {
    private static Map<String, String> BCIDRootsCache = new HashMap<String, String>();

    static {
        Connection connection = null;
        PreparedStatement getBCIDRootsStatement = null;
        ResultSet BCIDRootsResultSet = null;
        try {
            connection = LIMSInitializationListener.getDataSource().getConnection();

            String getBCIDRootsQuery = "SELECT * " +
                                       "FROM " + LimsDatabaseConstants.BCID_ROOTS_TABLE_NAME;

            getBCIDRootsStatement = connection.prepareStatement(getBCIDRootsQuery);

            BCIDRootsResultSet = getBCIDRootsStatement.executeQuery();

            while (BCIDRootsResultSet.next()) {
                BCIDRootsCache.put(BCIDRootsResultSet.getString(LimsDatabaseConstants.TYPE_COLUMN_NAME_BCID_ROOTS_TABLE),
                        BCIDRootsResultSet.getString(LimsDatabaseConstants.BCID_ROOT_COLUMN_NAME_BCID_ROOTS_TABLE));
            }
        } catch (SQLException e) {
            throw new InternalServerErrorException("Failed to retrieve BCID Roots.", e);
        } finally {
            try {
                if (getBCIDRootsStatement != null) {
                    getBCIDRootsStatement.close();
                }
                if (BCIDRootsResultSet != null) {
                    BCIDRootsResultSet.close();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            SqlUtilities.closeConnection(connection);
        }
    }

    @GET
    @Produces({"application/json;qs=1", "application/xml;qs=0.5"})
    public Response get() {
        List<BCIDRoot> BCIDRoots = new ArrayList<BCIDRoot>();

        for (Map.Entry<String, String> BCIDRootEntry : BCIDRootsCache.entrySet()) {
            BCIDRoots.add(new BCIDRoot(BCIDRootEntry.getKey(), BCIDRootEntry.getValue()));
        }

        return Response.ok(new GenericEntity<List<BCIDRoot>>(BCIDRoots){}).build();
    }

    @POST
    @Consumes({"application/json", "application/xml"})
    public void add(BCIDRoot bcidRoot) {
        Connection connection = null;
        PreparedStatement addBCIDRootStatement = null;
        try {
            connection = LIMSInitializationListener.getDataSource().getConnection();

            String addBCIDRootQuery = "INSERT INTO " + LimsDatabaseConstants.BCID_ROOTS_TABLE_NAME + " VALUES (?, ?)";

            addBCIDRootStatement = connection.prepareStatement(addBCIDRootQuery);
            addBCIDRootStatement.setObject(1, bcidRoot.type);
            addBCIDRootStatement.setObject(2, bcidRoot.value);

            SqlUtilities.beginTransaction(connection);
            if (!addBCIDRootStatement.execute()) {
                SqlUtilities.commitTransaction(connection);
                BCIDRootsCache.put(bcidRoot.type, bcidRoot.value);
            }
        } catch (SQLException e) {
            throw new InternalServerErrorException("Failed to add BCID Root '" + bcidRoot.type + ":" + bcidRoot.value + "'.", e);
        } finally {
            try {
                if (addBCIDRootStatement != null) {
                    addBCIDRootStatement.close();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            SqlUtilities.closeConnection(connection);
        }
    }

    @PUT
    @Path("{type}")
    @Consumes({"application/json", "application/xml"})
    public void update(@PathParam("type")String type, BCIDRoot bcidRoot) {
        Connection connection = null;
        PreparedStatement updateBCIDRootStatement = null;
        try {
            connection = LIMSInitializationListener.getDataSource().getConnection();

            String updateBCIDRootQuery = "UPDATE " + LimsDatabaseConstants.BCID_ROOTS_TABLE_NAME + " " +
                                         "SET "    + LimsDatabaseConstants.TYPE_COLUMN_NAME_BCID_ROOTS_TABLE + "=?, "
                                                   + LimsDatabaseConstants.BCID_ROOT_COLUMN_NAME_BCID_ROOTS_TABLE + "=? " +
                                         "WHERE "  + LimsDatabaseConstants.TYPE_COLUMN_NAME_BCID_ROOTS_TABLE + "=?";

            updateBCIDRootStatement = connection.prepareStatement(updateBCIDRootQuery);
            updateBCIDRootStatement.setObject(1, bcidRoot.type);
            updateBCIDRootStatement.setObject(2, bcidRoot.value);
            updateBCIDRootStatement.setObject(3, type);

            SqlUtilities.beginTransaction(connection);
            if (updateBCIDRootStatement.executeUpdate() == 1) {
                SqlUtilities.commitTransaction(connection);
                BCIDRootsCache.remove(type);
                BCIDRootsCache.put(bcidRoot.type, bcidRoot.value);
            }
        } catch (SQLException e) {
            throw new InternalServerErrorException("Failed to update " + type + " BCID Root.", e);
        } finally {
            try {
                if (updateBCIDRootStatement != null) {
                    updateBCIDRootStatement.close();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            SqlUtilities.closeConnection(connection);
        }
    }

    @DELETE
    @Path("{type}")
    public void delete(@PathParam("type")String type) {
        Connection connection = null;
        PreparedStatement deleteBCIDRootStatement = null;
        try {
            connection = LIMSInitializationListener.getDataSource().getConnection();

            String deleteBCIDRootQuery = "DELETE FROM " + LimsDatabaseConstants.BCID_ROOTS_TABLE_NAME + " " +
                                         "WHERE "       + LimsDatabaseConstants.TYPE_COLUMN_NAME_BCID_ROOTS_TABLE + "=?";

            deleteBCIDRootStatement = connection.prepareStatement(deleteBCIDRootQuery);
            deleteBCIDRootStatement.setObject(1, type);

            SqlUtilities.beginTransaction(connection);
            if (deleteBCIDRootStatement.executeUpdate() == 1) {
                SqlUtilities.commitTransaction(connection);
                BCIDRootsCache.remove(type);
            }
        } catch (SQLException e) {
            throw new InternalServerErrorException("Failed to delete " + type + " BCID Root.", e);
        } finally {
            try {
                if (deleteBCIDRootStatement != null) {
                    deleteBCIDRootStatement.close();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            SqlUtilities.closeConnection(connection);
        }
    }
}