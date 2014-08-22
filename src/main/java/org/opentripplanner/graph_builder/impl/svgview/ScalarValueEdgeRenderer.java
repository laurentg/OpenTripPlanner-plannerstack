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

import org.opentripplanner.routing.edgetype.PlainStreetEdge;
import org.opentripplanner.routing.graph.Edge;

/**
 * A generic EdgeRenderer which display a single scalar value for each edge, with a color depending on the
 * scalar value.
 *
 * @author laurent
 */
public class ScalarValueEdgeRenderer implements EdgeRenderer {

    public enum ScalarValue {
        BIKE_SAFETY
    };

    private ScalarValue scalar;

    private int outputLabelModulo;

    private ScalarColorPalette palette;

    private int n = 0;

    public ScalarValueEdgeRenderer(ScalarValue scalar, int outputLabelModulo) {
        this.scalar = scalar;
        this.outputLabelModulo = outputLabelModulo;
        this.palette = new DefaultScalarColorPalette(1.0f, 3.0f, 12.0f);
    }

    @Override
    public EdgeView render(Edge edge) {

        if (!(edge instanceof PlainStreetEdge))
            return null;
        PlainStreetEdge pse = (PlainStreetEdge) edge;

        float x;
        switch (scalar) {
        case BIKE_SAFETY:
            x = (float) (pse.getBicycleSafetyEffectiveLength() / pse.getLength());
            break;
        default:
            throw new RuntimeException("Implement me: " + scalar);
        }

        EdgeView view = new EdgeView();
        view.color = palette.getColor(x);
        if (outputLabelModulo > 0 && n % outputLabelModulo == 0) {
            view.label = String.format("%.2f", x);
            view.labelColor = Color.BLACK;
        }
        n++;

        return view;
    }

}
