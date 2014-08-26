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
import java.util.ArrayList;
import java.util.List;

import org.opentripplanner.routing.edgetype.ParkAndRideLinkEdge;
import org.opentripplanner.routing.edgetype.PathwayEdge;
import org.opentripplanner.routing.edgetype.PlainStreetEdge;
import org.opentripplanner.routing.edgetype.StreetBikeRentalLink;
import org.opentripplanner.routing.edgetype.StreetTransitLink;
import org.opentripplanner.routing.edgetype.StreetTraversalPermission;
import org.opentripplanner.routing.graph.Edge;

/**
 * An EdgeRenderer which display traversal permissions and connectivity (links).
 * 
 * @author laurent
 */
public class PermissionsEdgeRenderer implements EdgeRenderer {

    private static final Color LINK_COLOR = Color.ORANGE;

    private static final Color STAIRS_COLOR = Color.PINK;

    public PermissionsEdgeRenderer() {
    }

    @Override
    public List<LegendLabelView> getLegend() {
        List<LegendLabelView> legend = new ArrayList<LegendLabelView>();
        legend.add(new LegendLabelView(getColor(StreetTraversalPermission.PEDESTRIAN), "WALK"));
        legend.add(new LegendLabelView(getColor(StreetTraversalPermission.BICYCLE), "BICYCLE"));
        legend.add(new LegendLabelView(getColor(StreetTraversalPermission.PEDESTRIAN_AND_BICYCLE),
                "WALK+BICYCLE"));
        legend.add(new LegendLabelView(getColor(StreetTraversalPermission.CAR), "CAR"));
        legend.add(new LegendLabelView(getColor(StreetTraversalPermission.PEDESTRIAN_AND_CAR),
                "WALK+CAR"));
        legend.add(new LegendLabelView(getColor(StreetTraversalPermission.BICYCLE_AND_CAR),
                "BICYLE+CAR"));
        legend.add(new LegendLabelView(
                getColor(StreetTraversalPermission.PEDESTRIAN_AND_BICYCLE_AND_CAR),
                "WALK+BICYLE+CAR"));
        legend.add(new LegendLabelView(LINK_COLOR, "LINK"));
        legend.add(new LegendLabelView(STAIRS_COLOR, "STAIRS"));
        return legend;
    }

    @Override
    public EdgeView render(Edge edge) {

        // TODO StationStopEdge needed?
        if (edge instanceof StreetTransitLink || edge instanceof StreetBikeRentalLink
                || edge instanceof ParkAndRideLinkEdge || edge instanceof PathwayEdge) {
            EdgeView view = new EdgeView();
            view.color = LINK_COLOR;
            return view;
        }

        if (!(edge instanceof PlainStreetEdge))
            return null;
        PlainStreetEdge pse = (PlainStreetEdge) edge;
        EdgeView view = new EdgeView();
        if (pse.isStairs()) {
            view.color = STAIRS_COLOR;
        } else {
            view.color = getColor(pse.getPermission());
        }
        return view;
    }

    private Color getColor(StreetTraversalPermission permissions) {
        /*
         * We use the trick that there are 3 main traversal modes (WALK, BIKE and CAR) and 3 color
         * channels (R, G, B).
         */
        float r = 0.2f;
        float g = 0.2f;
        float b = 0.2f;
        if (permissions.allows(StreetTraversalPermission.PEDESTRIAN))
            g += 0.5f;
        if (permissions.allows(StreetTraversalPermission.BICYCLE))
            b += 0.5f;
        if (permissions.allows(StreetTraversalPermission.CAR))
            r += 0.5f;
        return new Color(r, g, b);
    }

}
