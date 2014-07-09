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

import org.onebusaway.gtfs.model.Stop;
import org.onebusaway.gtfs.model.Trip;
import org.opentripplanner.gtfs.GtfsLibrary;
import org.opentripplanner.routing.core.State;
import org.opentripplanner.routing.core.StateEditor;
import org.opentripplanner.routing.edgetype.HopEdge;
import org.opentripplanner.routing.edgetype.OnboardEdge;
import org.opentripplanner.routing.graph.Edge;

import com.vividsolutions.jts.geom.LineString;

/**
 * Part of a "time-tunnel" set of edges, pre-computed by RRRR. This edge is a multi-hop (edge from
 * the first station of a transit leg to the last one, in boarded state).
 * 
 * @author laurent
 */
public class RrrrHopEdge extends Edge implements HopEdge, OnboardEdge {
    private static final long serialVersionUID = 1L;

    private Stop beginStop, endStop;

    private LineString geometry;

    private Trip trip;

    private long durationSec;

    public RrrrHopEdge(RrrrTransitStopBoarded from, RrrrTransitStopBoarded to, Trip trip,
            long durationSec) {
        super(from, to);
        beginStop = from.getStop();
        endStop = to.getStop();
        this.trip = trip;
        this.durationSec = durationSec;
    }

    @Override
    public Stop getEndStop() {
        return endStop;
    }

    @Override
    public Stop getBeginStop() {
        return beginStop;
    }

    @Override
    public void setGeometry(LineString geometry) {
        this.geometry = geometry;
    }

    @Override
    public double getDistance() {
        return 0;
    }

    @Override
    public LineString getGeometry() {
        return geometry;
    }

    @Override
    public String getName() {
        return GtfsLibrary.getRouteName(trip.getRoute());
    }

    @Override
    public State traverse(State s0) {
        StateEditor se = s0.edit(this);
        se.incrementTimeInSeconds((int) durationSec);
        se.incrementWeight(1); // A wormhole
        se.setBackMode(GtfsLibrary.getTraverseMode(trip.getRoute()));
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
