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

import org.opentripplanner.routing.core.State;
import org.opentripplanner.routing.core.StateEditor;
import org.opentripplanner.routing.core.TraverseMode;
import org.opentripplanner.routing.graph.Edge;

import com.vividsolutions.jts.geom.LineString;

/**
 * Part of a "time-tunnel" set of edges, pre-computed by RRRR. This edge is a (walk) transfer
 * between two stations.
 * 
 * @author laurent
 */
public class RrrrTransferEdge extends Edge {
    private static final long serialVersionUID = 1L;

    private double distance;

    private long durationSec;

    private LineString geometry = null;

    public RrrrTransferEdge(RrrrTransitStop fromv, RrrrTransitStop tov, double distance,
            long durationSec) {
        super(fromv, tov);
        this.distance = distance;
        this.durationSec = durationSec;
    }

    public double getDistance() {
        return distance;
    }

    public LineString getGeometry() {
        return geometry;
    }

    public void setGeometry(LineString geometry) {
        this.geometry = geometry;
    }

    public String getName() {
        // TODO What name?
        if (fromv.getName().equals(tov.getName()))
            return fromv.getName();
        return fromv.getName() + " -> " + tov.getName();
    }

    public State traverse(State s0) {
        StateEditor se = s0.edit(this);
        se.setBackMode(TraverseMode.WALK);
        se.incrementTimeInSeconds((int) durationSec);
        se.incrementWeight(1); // This is a time-tunnel
        se.incrementWalkDistance(distance);
        return se.makeState();
    }
}
