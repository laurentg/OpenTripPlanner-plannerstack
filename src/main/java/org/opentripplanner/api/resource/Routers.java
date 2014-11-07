/* This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public License
 as published by the Free Software Foundation, either version 3 of
 the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>. */

package org.opentripplanner.api.resource;

import static org.opentripplanner.api.resource.ServerInfo.Q;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.opentripplanner.api.model.RouterInfo;
import org.opentripplanner.api.model.RouterList;
import org.opentripplanner.graph_builder.GraphBuilderTask;
import org.opentripplanner.graph_builder.InputFileType;
import org.opentripplanner.graph_builder.impl.EmbeddedConfigGraphBuilderImpl;
import org.opentripplanner.graph_builder.impl.GtfsGraphBuilderImpl;
import org.opentripplanner.graph_builder.impl.PruneFloatingIslands;
import org.opentripplanner.graph_builder.impl.StreetfulStopLinker;
import org.opentripplanner.graph_builder.impl.StreetlessStopLinker;
import org.opentripplanner.graph_builder.impl.TransitToStreetNetworkGraphBuilderImpl;
import org.opentripplanner.graph_builder.impl.TransitToTaggedStopsGraphBuilderImpl;
import org.opentripplanner.graph_builder.impl.osm.DefaultWayPropertySetSource;
import org.opentripplanner.graph_builder.impl.osm.OpenStreetMapGraphBuilderImpl;
import org.opentripplanner.graph_builder.model.GtfsBundle;
import org.opentripplanner.graph_builder.services.DefaultStreetEdgeFactory;
import org.opentripplanner.graph_builder.services.GraphBuilder;
import org.opentripplanner.openstreetmap.impl.AnyFileBasedOpenStreetMapProviderImpl;
import org.opentripplanner.openstreetmap.services.OpenStreetMapProvider;
import org.opentripplanner.routing.error.GraphNotFoundException;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.graph.Graph.LoadLevel;
import org.opentripplanner.routing.impl.DefaultFareServiceFactory;
import org.opentripplanner.routing.impl.DefaultStreetVertexIndexFactory;
import org.opentripplanner.routing.impl.MemoryGraphSource;
import org.opentripplanner.routing.services.StreetVertexIndexFactory;
import org.opentripplanner.routing.services.StreetVertexIndexService;
import org.opentripplanner.standalone.CommandLineParameters;
import org.opentripplanner.standalone.OTPConfigurator;
import org.opentripplanner.standalone.OTPServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

/**
 * This REST API endpoint allows remotely loading, reloading, and evicting graphs on a running server.
 * 
 * A GraphService maintains a mapping between routerIds and specific Graph objects.
 * The HTTP verbs are used as follows to manipulate that mapping:
 * 
 * GET - see the registered routerIds and Graphs, verify whether a particular routerId is registered
 * PUT - create or replace a mapping from a routerId to a Graph loaded from the server filesystem
 * POST - create or replace a mapping from a routerId to a serialized Graph sent in the request
 * DELETE - de-register a routerId, releasing the reference to the associated graph
 * 
 * The HTTP request URLs are of the form /ws/routers/{routerId}, where the routerId is optional. 
 * If a routerId is supplied in the URL, the verb will act upon the mapping for that specific 
 * routerId. If no routerId is given, the verb will act upon all routerIds currently registered.
 * 
 * For example:
 * 
 * GET http://localhost/otp-rest-servlet/ws/routers
 * will retrieve a list of all registered routerId -> Graph mappings and their geographic bounds.
 * 
 * GET http://localhost/otp-rest-servlet/ws/routers/london
 * will return status code 200 and a brief description of the 'london' graph including geographic 
 * bounds, or 404 if the 'london' routerId is not registered.
 * 
 * PUT http://localhost/otp-rest-servlet/ws/routers
 * will reload the graphs for all currently registered routerIds from disk.
 * 
 * PUT http://localhost/otp-rest-servlet/ws/routers/paris
 * will load a Graph from a sub-directory called 'paris' and associate it with the routerId 'paris'.
 * 
 * DELETE http://localhost/otp-rest-servlet/ws/routers/paris
 * will release the Paris Graph and de-register the 'paris' routerId.
 * 
 * DELETE http://localhost/otp-rest-servlet/ws/routers
 * will de-register all currently registered routerIds.
 * 
 * The GET methods are not secured, but all other methods are secured under ROLE_ROUTERS.
 * See documentation for individual methods for additional parameters.
 */
@Path("/routers")
@PermitAll // exceptions on methods
public class Routers {

    private static final Logger LOG = LoggerFactory.getLogger(Routers.class);

    @Context OTPServer server;

    /** 
     * Returns a list of routers and their bounds. 
     * @return a representation of the graphs and their geographic bounds, in JSON or XML depending
     * on the Accept header in the HTTP request.
     */
    @GET
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML + Q, MediaType.TEXT_XML + Q })
    public RouterList getRouterIds() {
        RouterList routerList = new RouterList();
        for (String id : server.graphService.getRouterIds()) {
            routerList.routerInfo.add(getRouterInfo(id));
        }
        return routerList;
    }

    /** 
     * Returns the bounds for a specific routerId, or verifies whether it is registered. 
     * @returns status code 200 if the routerId is registered, otherwise a 404.
     */
    @GET @Path("{routerId}")
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML + Q, MediaType.TEXT_XML + Q })
    public RouterInfo getGraphId(@PathParam("routerId") String routerId) {
        // factor out build one entry
        RouterInfo routerInfo = getRouterInfo(routerId);
        if (routerInfo == null)
            throw new WebApplicationException(Response.status(Status.NOT_FOUND)
                    .entity("Graph id '" + routerId + "' not registered.\n").type("text/plain")
                    .build());
        return routerInfo;
    }
    
    private RouterInfo getRouterInfo(String routerId) {
        Graph graph = server.graphService.getGraph(routerId);
        if (graph == null) return null;
        RouterInfo routerInfo = new RouterInfo();
        routerInfo.routerId = routerId;
        routerInfo.polygon = graph.getHull();
        routerInfo.buildTime = graph.buildTime;
        return routerInfo;
    }

    /** 
     * Reload the graphs for all registered routerIds from disk.
     */
    @RolesAllowed({ "ROUTERS" })
    @PUT @Produces({ MediaType.APPLICATION_JSON })
    public Response reloadGraphs(@QueryParam("path") String path, 
            @QueryParam("preEvict") @DefaultValue("true") boolean preEvict) {
        server.graphService.reloadGraphs(preEvict);
        return Response.status(Status.OK).build();
    }

    /** 
     * Load the graph for the specified routerId from disk.
     * @param preEvict before reloading each graph, evict the existing graph. This will prevent 
     * memory usage from increasing during the reload, but routing will be unavailable on this 
     * routerId for the duration of the operation.
     *
     *                FIXME @param upload read the graph from the PUT data stream instead of from disk.
     */
    @RolesAllowed({ "ROUTERS" })
    @PUT @Path("{routerId}") @Produces({ MediaType.TEXT_PLAIN })
    public Response putGraphId(
            @PathParam("routerId") String routerId, 
            @QueryParam("preEvict") @DefaultValue("true") boolean preEvict) {
        LOG.debug("Attempting to load graph '{}' from server's local filesystem.", routerId);
        try {
            server.graphService.getGraph(routerId);
            return Response.status(404).entity("graph already registered.\n").build();
        } catch (GraphNotFoundException e) {
            if (preEvict) {
                LOG.debug("Pre-evicting graph '{}'", routerId);
                server.graphService.evictGraph(routerId);
            }
            boolean success = server.graphService.registerGraph(routerId, server.graphService
                    .getGraphSourceFactory().createGraphSource(routerId));
            if (success)
                return Response.status(201).entity("graph registered.\n").build();
            else
                return Response.status(404).entity("graph not found or other error.\n").build();
        }
    }

    /** 
     * Deserialize a graph sent with the HTTP request as POST data, associating it with the given 
     * routerId.
     */
    @RolesAllowed({ "ROUTERS" })
    @POST @Path("{routerId}") @Produces({ MediaType.TEXT_PLAIN })
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    public Response postGraphOverWire (
            @PathParam("routerId") String routerId, 
            @QueryParam("preEvict") @DefaultValue("true") boolean preEvict, 
            @QueryParam("loadLevel") @DefaultValue("FULL") LoadLevel level,
            InputStream is) {
        if (preEvict) {
            LOG.debug("pre-evicting graph");
            server.graphService.evictGraph(routerId);
        }
        LOG.debug("deserializing graph from POST data stream...");
        Graph graph;
        try {
            graph = Graph.load(is, level);
            server.graphService.registerGraph(routerId, new MemoryGraphSource(routerId, graph));
            return Response.status(Status.CREATED).entity(graph.toString() + "\n").build();
        } catch (Exception e) {
            return Response.status(Status.BAD_REQUEST).entity(e.toString() + "\n").build();
        }
    }
    
    /**
     * Build a graph from data in the ZIP file posted over the wire, associating it with the given router ID.
     * This method will be selected when the Content-Type is application/zip.
     */
    @RolesAllowed({ "ROUTERS" })
    @POST @Path("{routerId}") @Consumes({"application/zip"})
    @Produces({ MediaType.TEXT_PLAIN })
    public Response buildGraphOverWire (
            @PathParam("routerId") String routerId,
            @QueryParam("preEvict") @DefaultValue("true") boolean preEvict,
            InputStream input) {
        // TODO: async processing
        
        if (preEvict) {
            LOG.debug("Pre-evicting graph with routerId {} before building new graph", routerId);
            server.graphService.evictGraph(routerId);
        }
        
        // get a temporary directory, using Google Guava
        File tempDir = Files.createTempDir();
        
        // extract the zip file to the temp dir
        ZipInputStream zis = new ZipInputStream(input);
        
        ZipEntry next;

        try {
            for (ZipEntry entry = zis.getNextEntry(); entry != null; entry = zis.getNextEntry()) {
                if (entry.isDirectory()) continue; // expect flat zip files or create directories
                File file = new File(tempDir, entry.getName());
                OutputStream os = new FileOutputStream(file);
                long bytesExtracted = ByteStreams.copy(zis, os);
                os.close();
            }
        } catch (Exception ex) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Could not extract zip file: " + ex.getMessage()).build();
        }

           
        // set up the build, using default parameters
        // this is basically simulating calling otp -b on the command line
        CommandLineParameters params = new CommandLineParameters();
        params.build = Lists.newArrayList();
        params.build.add(tempDir);
        
        GraphBuilderTask graphBuilder = new OTPConfigurator(params).builderFromParameters();
        
        graphBuilder.run();
        
        // remove the temporary directory
        // this doesn't work for nested directories, but the extract doesn't either,
        // so we'll crash long before we get here . . .
        for (File file : tempDir.listFiles()) {
            file.delete();
        }
        
        tempDir.delete();
        
        Graph graph = graphBuilder.getGraph();
        graph.index(new DefaultStreetVertexIndexFactory());
        
        server.graphService.registerGraph(routerId, new MemoryGraphSource(routerId, graph));
        return Response.status(Status.CREATED).entity(graph.toString() + "\n").build();
    }
    
    /** 
     * Save the graph data, but don't load it in memory. The file location is based on routerId.
     * If the graph already exists, the graph will be overwritten.
     */
    @RolesAllowed({ "ROUTERS" })
    @POST @Path("/save") @Produces({ MediaType.TEXT_PLAIN })
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    public Response saveGraphOverWire (
            @QueryParam("routerId") String routerId,
            InputStream is) {
        LOG.debug("save graph from POST data stream...");
        try {
            boolean success = server.graphService.getGraphSourceFactory().save(routerId, is);
            if (success) {
                return Response.status(201).entity("graph saved.\n").build();
            } else {
                return Response.status(404).entity("graph not saved or other error.\n").build();
            }
        } catch (Exception e) {
            return Response.status(Status.BAD_REQUEST).entity(e.toString()).build();
        }
    }

    /** De-register all registered routerIds, evicting them from memory. */
    @RolesAllowed({ "ROUTERS" })
    @DELETE @Produces({ MediaType.TEXT_PLAIN })
    public Response deleteAll() {
        int nEvicted = server.graphService.evictAll();
        String message = String.format("%d graphs evicted.\n", nEvicted);
        return Response.status(200).entity(message).build();
    }

    /** 
     * De-register a specific routerId, evicting the associated graph from memory. 
     * @return status code 200 if the routerId was de-registered, 
     * 404 if the routerId was not registered. 
     */
    @RolesAllowed({ "ROUTERS" })
    @DELETE @Path("{routerId}") @Produces({ MediaType.TEXT_PLAIN })
    public Response deleteGraphId(@PathParam("routerId") String routerId) {
        boolean existed = server.graphService.evictGraph(routerId);
        if (existed)
            return Response.status(200).entity("graph evicted.\n").build();
        else
            return Response.status(404).entity("graph did not exist.\n").build();
    }

}
