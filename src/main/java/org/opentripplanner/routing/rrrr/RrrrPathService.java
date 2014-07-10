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

package org.opentripplanner.routing.rrrr;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import lombok.Setter;

import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.Stop;
import org.onebusaway.gtfs.model.Trip;
import org.opentripplanner.api.model.Itinerary;
import org.opentripplanner.api.model.Leg;
import org.opentripplanner.api.model.TripPlan;
import org.opentripplanner.api.resource.Response;
import org.opentripplanner.common.model.GenericLocation;
import org.opentripplanner.routing.algorithm.strategies.DefaultRemainingWeightHeuristic;
import org.opentripplanner.routing.algorithm.strategies.NearestTargetsSearchTerminationStrategy;
import org.opentripplanner.routing.algorithm.strategies.TrivialRemainingWeightHeuristic;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.core.TraverseMode;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.graph.GraphIndex;
import org.opentripplanner.routing.impl.PathWeightComparator;
import org.opentripplanner.routing.pathparser.BasicPathParser;
import org.opentripplanner.routing.pathparser.PathParser;
import org.opentripplanner.routing.services.GraphService;
import org.opentripplanner.routing.services.PathService;
import org.opentripplanner.routing.services.SPTService;
import org.opentripplanner.routing.spt.GraphPath;
import org.opentripplanner.routing.spt.ShortestPathTree;
import org.opentripplanner.routing.vertextype.TransitStop;
import org.opentripplanner.util.PolylineEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;

/**
 */
public class RrrrPathService implements PathService {

    private static final Logger LOG = LoggerFactory.getLogger(RrrrPathService.class);

    @Setter
    private int timeoutSec = 10;

    private GraphService graphService;

    private SPTService sptService;

    private GeometryFactory geometryFactory = new GeometryFactory();

    public RrrrPathService(GraphService graphService, SPTService sptService) {
        this.graphService = graphService;
        this.sptService = sptService;
    }

    @Setter
    private double timeout = 0; // seconds

    @Override
    public List<GraphPath> getPaths(RoutingRequest options) {
        if (options == null) {
            LOG.error("PathService was passed a null routing request.");
            return null;
        }
        if (options.getModes().getBicycle() || options.getModes().getCar())
            // TODO: select a fallback PathService for not supported modes
            throw new UnsupportedOperationException("Unsupported: CAR/BIKE searches");

        Graph graph = graphService.getGraph(options.getRouterId());
        options.setRoutingContext(graph);
        options.rctx.pathParsers = new PathParser[] { new BasicPathParser() };

        GenericLocation to = options.getTo();

        // Extract walk-only options
        RoutingRequest walkOptions = options.clone();
        walkOptions.rctx = null; // Hack to force re-creation of RoutingContext
        walkOptions.setTo(null);
        walkOptions.setMode(TraverseMode.WALK);
        walkOptions.setArriveBy(false);
        walkOptions.setBatch(true);
        walkOptions.setRoutingContext(graph);
        walkOptions.rctx.pathParsers = new PathParser[] { new BasicPathParser() };
        walkOptions.rctx.remainingWeightHeuristic = new TrivialRemainingWeightHeuristic();

        double maxWeight = options.getMaxWalkDistance() / options.getWalkSpeed(); // approx.
        // Search for nearest from stop - TODO Select several
        long searchBeginTime3 = System.currentTimeMillis();
        NearestTargetsSearchTerminationStrategy<TransitStop> nearestStops = new NearestTargetsSearchTerminationStrategy<TransitStop>(
                TransitStop.class, maxWeight, 3);
        ShortestPathTree spt = sptService.getShortestPathTree(walkOptions, timeout, nearestStops);
        if (nearestStops.getReachedTargetsList().isEmpty()) {
            LOG.warn("No stations found near departure.");
            return null;
        }
        TransitStop fromStop = nearestStops.getReachedTargetsList().get(0);
        GraphPath firstWalkPath = spt.getPath(fromStop, false);

        // Search for nearest to stops - Revert search
        walkOptions.rctx = null; // Hack to force re-creation of RoutingContext
        walkOptions.setFrom(to);
        walkOptions.setRoutingContext(graph);
        walkOptions.rctx.pathParsers = new PathParser[] { new BasicPathParser() };
        walkOptions.rctx.remainingWeightHeuristic = new TrivialRemainingWeightHeuristic();
        nearestStops = new NearestTargetsSearchTerminationStrategy<TransitStop>(TransitStop.class,
                maxWeight, 3);
        spt = sptService.getShortestPathTree(walkOptions, timeout, nearestStops);
        if (nearestStops.getReachedTargetsList().isEmpty()) {
            LOG.warn("No stations found near arrival.");
            return null;
        }
        TransitStop toStop = nearestStops.getReachedTargetsList().get(0);
        GraphPath lastWalkPath = spt.getPath(toStop, false);
        LOG.info("Walk paths searches: {} msec", System.currentTimeMillis() - searchBeginTime3);

        // Adjust transit search
        long adjustSec = (options.isArriveBy() ? -lastWalkPath.getDuration() : firstWalkPath
                .getDuration());
        Date originalDateTime = options.getDateTime();
        options.setDateTime(new Date(options.getDateTime().getTime() + adjustSec * 1000));
        long searchBeginTime = System.currentTimeMillis();
        TripPlan transitPaths = planTransit(fromStop, toStop, options);
        LOG.info("RRRR search: {} msec", System.currentTimeMillis() - searchBeginTime);

        // Create time-tunnels with the resulting itineraries
        for (Itinerary itinerary : transitPaths.itinerary) {
            addTimeTunnel(options, itinerary);
        }

        // Final plan through time-tunnel
        options.rctx.pathParsers = new PathParser[] { new BasicPathParser() };
        // options.setMaxWalkDistance(Double.MAX_VALUE);
        options.rctx.remainingWeightHeuristic = new DefaultRemainingWeightHeuristic();
        options.setMode(TraverseMode.WALK); // HACK to disable transit
        options.setDateTime(originalDateTime);

        long searchBeginTime2 = System.currentTimeMillis();
        spt = sptService.getShortestPathTree(options, timeoutSec);
        LOG.info("Time-tunnel search: {} msec", System.currentTimeMillis() - searchBeginTime2);

        // Remove temporary stuff
        walkOptions.cleanup();
        if (spt == null)
            return null;

        List<GraphPath> paths = spt.getPaths(options.getRoutingContext().target, true);
        // for (GraphPath path : paths)
        // path.dump();
        Collections.sort(paths, new PathWeightComparator());
        return paths;
    }

    private TripPlan planTransit(TransitStop fromStop, TransitStop toStop, RoutingRequest options) {
        Graph graph = options.rctx.graph;
        RrrrService rrrrService = graph.getService(RrrrService.class);
        if (rrrrService == null)
            throw new RuntimeException(
                    "RrrrService not found. Please rebuild the graph with a RrrrGraphBuilder.");
        RrrrProxy rrrrProxy = graph.getService(RrrrProxy.class, true);
        rrrrProxy.setBaseDate(rrrrService.getCalendarStart());

        Integer fromStopRrrrIndex = rrrrService.getStopIndex(fromStop.getStopId());
        if (fromStopRrrrIndex == null)
            throw new RuntimeException("No RRRR stop index for " + fromStop.getStopId());
        Integer toStopRrrrIndex = rrrrService.getStopIndex(toStop.getStopId());
        if (toStopRrrrIndex == null)
            throw new RuntimeException("No RRRR stop index for " + toStop.getStopId());

        Response transitPaths = rrrrProxy.sendRequest(options, fromStopRrrrIndex, toStopRrrrIndex);

        if (transitPaths.getError() != null)
            throw new RuntimeException(transitPaths.getError().getMsg()); // ?
        return transitPaths.getPlan() == null ? null : transitPaths.getPlan();
    }

    private void addTimeTunnel(RoutingRequest options, Itinerary itinerary) {
        Graph graph = options.rctx.graph;
        GraphIndex graphIndex = graph.index;
        RrrrService rrrrService = graph.getService(RrrrService.class);

        // Remove first and last non transit leg
        // TODO With multi-stop depart, we should have an option in RRRR
        // to prevent using transfer before first boarding.
        int firstIndex = 0;
        while (firstIndex < itinerary.legs.size() && !itinerary.legs.get(firstIndex).isTransitLeg())
            firstIndex++;
        int lastIndex = itinerary.legs.size() - 1;
        while (lastIndex >= 0 && !itinerary.legs.get(lastIndex).isTransitLeg())
            lastIndex--;

        if (firstIndex > lastIndex)
            return; // No transit in path

        RrrrTransitStop lastAlighted = null;
        for (int i = firstIndex; i <= lastIndex; i++) {
            Leg leg = itinerary.legs.get(i);
            AgencyAndId fromStopId = leg.from.stopId;
            fromStopId.setAgencyId(rrrrService.getDefaultAgencyId()); // TODO RRRR hardcode NL
            Stop fromStop = graphIndex.stopForId.get(fromStopId);
            AgencyAndId toStopId = leg.to.stopId;
            toStopId.setAgencyId(rrrrService.getDefaultAgencyId()); // TODO RRRR hardcode NL
            Stop toStop = graphIndex.stopForId.get(toStopId);
            if (i == firstIndex) {
                // Link with private edge to first station
                lastAlighted = new RrrrTransitStop(fromStop);
                options.rctx.addTemporaryVertex(lastAlighted);
                TransitStop fromStopVertex = graphIndex.stopVertexForStop.get(fromStop);
                new RrrrPrivateEdge(fromStopVertex, lastAlighted, options.rctx);
            }
            RrrrTransitStop toAlighted = new RrrrTransitStop(toStop);
            options.rctx.addTemporaryVertex(toAlighted);
            if (i == lastIndex) {
                // Link with private edge to last station
                TransitStop toStopVertex = graphIndex.stopVertexForStop.get(toStop);
                new RrrrPrivateEdge(toAlighted, toStopVertex, options.rctx);
            }
            long startSec = leg.startTime.getTime().getTime() / 1000;
            long endSec = leg.endTime.getTime().getTime() / 1000;
            long durationSec = endSec - startSec;
            if (leg.isTransitLeg()) {
                // Transit
                RrrrTransitStopBoarded fromBoarded = new RrrrTransitStopBoarded(fromStop);
                RrrrTransitStopBoarded toBoarded = new RrrrTransitStopBoarded(fromStop);
                options.rctx.addTemporaryVertex(fromBoarded);
                options.rctx.addTemporaryVertex(toBoarded);
                AgencyAndId tripId = new AgencyAndId(leg.agencyId, leg.tripId);
                Trip trip = graphIndex.tripForId.get(tripId);
                new RrrrBoardAlightEdge(lastAlighted, fromBoarded, trip, startSec);
                RrrrHopEdge hopEdge = new RrrrHopEdge(fromBoarded, toBoarded, trip, durationSec);
                new RrrrBoardAlightEdge(toBoarded, toAlighted, trip, endSec);
                hopEdge.setGeometry(decodeLegGeometry(leg));
            } else {
                // Walk (transfer)
                RrrrTransferEdge transferEdge = new RrrrTransferEdge(lastAlighted, toAlighted,
                        leg.distance, durationSec);
                transferEdge.setGeometry(decodeLegGeometry(leg));
            }
            lastAlighted = toAlighted;
        }
    }

    private LineString decodeLegGeometry(Leg leg) {
        if (leg.legGeometry == null)
            return null;
        List<Coordinate> coordinates = PolylineEncoder.decode(leg.legGeometry);
        LineString retval = geometryFactory.createLineString(coordinates
                .toArray(new Coordinate[coordinates.size()]));
        return retval;
    }
}
