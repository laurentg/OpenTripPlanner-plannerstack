/* This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public License
 as published by the Free Software Foundation, either version 3 of
 the License, or (props, at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>. */

package org.opentripplanner.routing.rrrr;

import org.onebusaway.gtfs.model.Route;
import org.onebusaway.gtfs.model.Trip;
import org.opentripplanner.gtfs.GtfsLibrary;
import org.opentripplanner.routing.core.State;
import org.opentripplanner.routing.core.StateEditor;
import org.opentripplanner.routing.core.TraverseMode;
import org.opentripplanner.routing.edgetype.OnboardEdge;
import org.opentripplanner.routing.graph.Edge;

/**
 * Part of a "time-tunnel" set of edges, pre-computed by RRRR. This edge is a board/alight (edge
 * from a station from onboard to offboard or the other way around).
 * 
 * @author laurent
 */
public class RrrrBoardAlightEdge extends Edge implements OnboardEdge {
    private static final long serialVersionUID = 1L;

    private Trip trip;

    private long timeSec;

    private boolean boarding;

    public RrrrBoardAlightEdge(RrrrTransitStop fromStop, RrrrTransitStopBoarded toStop, Trip trip,
            long timeSec) {
        super(fromStop, toStop);
        this.boarding = true;
        this.trip = trip;
        this.timeSec = timeSec;
    }

    public RrrrBoardAlightEdge(RrrrTransitStopBoarded fromStop, RrrrTransitStop toStop, Trip trip,
            long timeSec) {
        super(fromStop, toStop);
        this.boarding = false;
        this.trip = trip;
        this.timeSec = timeSec;
    }

    @Override
    public String getName() {
        return GtfsLibrary.getRouteName(trip.getRoute());
    }

    @Override
    public State traverse(State s0) {
        StateEditor se = s0.edit(this);
        if (boarding) // TODO arriveBy
            se.incrementNumBoardings();
        Route route = trip.getRoute();
        if (boarding)
            se.setBackMode(TraverseMode.LEG_SWITCH);
        se.setTimeSeconds(timeSec);
        se.incrementWeight(1); // A wormhole
        se.setRoute(route.getId());
        se.setTripId(trip.getId());
        return se.makeState();
    }

    @Override
    public Trip getTrip() {
        return trip;
    }

    @Override
    public int getStopIndex() {
        return 0; // TODO
    }
}
