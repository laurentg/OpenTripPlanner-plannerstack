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

import org.opentripplanner.routing.core.RoutingContext;
import org.opentripplanner.routing.core.State;
import org.opentripplanner.routing.core.StateEditor;
import org.opentripplanner.routing.core.TraverseMode;
import org.opentripplanner.routing.graph.Edge;
import org.opentripplanner.routing.graph.Vertex;

/**
 * Part of a "time-tunnel" set of edges, pre-computed by RRRR. This edge act as a private gate to
 * prevent the time-tunnel to be used by other concurrent requests on the same graph.
 * 
 * @author laurent
 */
public class RrrrPrivateEdge extends Edge {
    private static final long serialVersionUID = 1L;

    private RoutingContext rctx;

    public RrrrPrivateEdge(Vertex from, Vertex to, RoutingContext routingContext) {
        super(from, to);
        this.rctx = routingContext;
    }

    public String getName() {
        return getToVertex().getName();
    }

    public State traverse(State s0) {
        // Allow traversal only for a given routing context
        RoutingContext stateRctx = s0.getOptions().getRoutingContext();
        if (stateRctx != rctx) {
            return null;
        }
        StateEditor se = s0.edit(this);
        se.setBackMode(TraverseMode.LEG_SWITCH);
        se.incrementWeight(1);
        return se.makeState();
    }
}
