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

package org.opentripplanner.graph_builder.impl.svgview;

import java.awt.Color;

import org.opentripplanner.routing.graph.Vertex;
import org.opentripplanner.routing.vertextype.BikeRentalStationVertex;
import org.opentripplanner.routing.vertextype.IntersectionVertex;
import org.opentripplanner.routing.vertextype.ParkAndRideVertex;
import org.opentripplanner.routing.vertextype.TransitStationStop;

/**
 * A simple VertexRenderer that simply render every vertex as a colored dot depending on the type.
 * 
 * @author laurent
 */
public class VertexColoredDotRenderer implements VertexRenderer {

    private boolean includeTransit, includeCar, includeBike;

    public VertexColoredDotRenderer(boolean includeTransit, boolean includeCar, boolean includeBike) {
        this.includeTransit = includeTransit;
        this.includeCar = includeCar;
        this.includeBike = includeBike;
    }

    public VertexView render(Vertex vertex) {
        VertexView retval = new VertexView();
        if (vertex instanceof IntersectionVertex) {
            retval.color = Color.DARK_GRAY;
        } else if (vertex instanceof TransitStationStop && includeTransit) {
            retval.color = Color.BLUE;
            retval.label = vertex.getName();
            retval.labelColor = Color.BLUE;
        } else if (vertex instanceof BikeRentalStationVertex && includeBike) {
            retval.color = Color.GREEN;
            retval.label = vertex.getName();
            retval.labelColor = Color.GREEN;
        } else if (vertex instanceof ParkAndRideVertex && includeCar) {
            retval.color = Color.RED;
            retval.label = vertex.getName();
            retval.labelColor = Color.RED;
        } else {
            return null;
        }
        return retval;
    }
}
