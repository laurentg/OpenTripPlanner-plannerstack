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
import org.opentripplanner.routing.vertextype.TransitVertex;

/**
 * Part of a "time-tunnel" set of edges/vertices, pre-computed by RRRR. This vertice is a station,
 * alighted.
 * 
 * @author laurent
 */
public class RrrrTransitStop extends TransitVertex {
    private static final long serialVersionUID = 1L;

    public RrrrTransitStop(Stop stop) {
        super(null, "RRRR stop " + stop.getId(), stop);
    }

    @Override
    public int removeTemporaryEdges() {
        int n = getIncoming().size() + getOutgoing().size();
        removeAllEdges();
        return n;
    }
}
