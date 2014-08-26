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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import lombok.Setter;

import org.apache.commons.math3.util.FastMath;
import org.opentripplanner.graph_builder.services.GraphBuilder;
import org.opentripplanner.routing.graph.Edge;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.graph.Vertex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.LineString;

/**
 * Create a SVG file which display edges/vertices in some color, and some (optional) value attached
 * to it (bike safety factor for example).
 * 
 * @author laurent
 */
public class SVGViewGraphBuilderImpl implements GraphBuilder {

    // private DistanceLibrary distanceLibrary = SphericalDistanceLibrary.getInstance();

    private static final Logger LOG = LoggerFactory.getLogger(SVGViewGraphBuilderImpl.class);

    /**
     * This is purely arbitrary, will affine-transform all coordinates to fit in a box this size.
     * Need a rather large value, as the font size should be integer and will use this unit.
     */
    private static final int MAX_COORDINATE = 10000;

    @Setter
    private String svgOutputFilePrefix = "Graph";

    @Setter
    private float lineWidth = 3.0f;

    @Setter
    private float pointWidth = lineWidth * 2.5f;

    @Setter
    private int fontSize = 6;

    @Setter
    private int legendFontSize = 50;

    @Setter
    private EdgeRenderer edgeRenderer;

    @Setter
    private VertexRenderer vertexRenderer;

    /**
     * An set of ids which identifies what stages this graph builder provides (i.e. streets,
     * elevation, transit)
     */
    public List<String> provides() {
        return Collections.emptyList();
    }

    /** A list of ids of stages which must be provided before this stage */
    public List<String> getPrerequisites() {
        return Arrays.asList("streets");
    }

    @Override
    public void buildGraph(final Graph graph, HashMap<Class<?>, Object> extra) {

        LOG.info("Generating SVG graph view to {}", svgOutputFilePrefix);

        Envelope extent = graph.getExtent();

        /* Equirectangular project with phi0 = center of the graph extent */
        double cosLat = Math.cos(Math.toRadians(graph.getExtent().centre().y));
        double xScale = MAX_COORDINATE / (extent.getMaxX() - extent.getMinX());
        double yScale = MAX_COORDINATE / (extent.getMaxY() - extent.getMinY());
        double scale = Math.min(xScale, yScale / cosLat);
        /*
         * Implementation note: Do not set the transform to the graphics context directly, the
         * generated SVG is painfully slow (it generate a transform for *each* generated path!)
         */
        AffineTransform transform = new AffineTransform();
        transform.scale(scale, -scale / cosLat);
        transform.translate(-extent.getMinX(), -extent.getMaxY());
        Dimension canvasSize = new Dimension((int) (MAX_COORDINATE * scale / xScale),
                (int) (MAX_COORDINATE * scale / yScale / cosLat));
        SVGPainter painter = new SVGPainter(new File(svgOutputFilePrefix + ".svg"), canvasSize);
        Graphics2D graphics = painter.getGraphics();

        int yLegend = 0;

        if (edgeRenderer != null) {
            int n = 0;
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, fontSize));
            graphics.setStroke(new BasicStroke(lineWidth, BasicStroke.CAP_BUTT,
                    BasicStroke.JOIN_BEVEL));
            for (Edge e : graph.getEdges()) {
                EdgeView edgeView = edgeRenderer.render(e);
                if (edgeView == null)
                    continue;

                Path2D.Double shape = convertGeometry(e.getGeometry(), transform, lineWidth * 0.7);

                graphics.setColor(edgeView.color != null ? edgeView.color : Color.GRAY);
                graphics.draw(shape);

                if (edgeView.label != null) {
                    Point2D labelPosition = findMidPoint(e.getGeometry(), transform);
                    graphics.setColor(edgeView.labelColor);
                    graphics.drawString(edgeView.label, (float) labelPosition.getX() + fontSize,
                            (float) labelPosition.getY());
                }

                n++;
                if (n % 10000 == 0) {
                    LOG.info("{} edges processed.", n);
                }
            }

            /* Render legend */
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, legendFontSize));
            for (LegendLabelView legendLabel : edgeRenderer.getLegend()) {
                graphics.setColor(legendLabel.color);
                graphics.fillRect(MAX_COORDINATE + 100, yLegend, legendFontSize * 4, legendFontSize);
                graphics.setColor(Color.BLACK);
                graphics.drawString(legendLabel.label, MAX_COORDINATE + 100 + legendFontSize * 5,
                        yLegend + legendFontSize);
                yLegend += legendFontSize * 2;
            }
        }

        if (vertexRenderer != null) {
            int n = 0;
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, fontSize));
            graphics.setStroke(new BasicStroke(pointWidth, BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_BEVEL));
            graphics.setColor(Color.BLUE);
            for (Vertex v : graph.getVertices()) {
                VertexView vertexView = vertexRenderer.render(v);
                if (vertexView == null)
                    continue;

                Path2D.Double shape = convertGeometry(v.getLon(), v.getLat(), transform);
                graphics.setColor(vertexView.color != null ? vertexView.color : Color.GRAY);
                graphics.draw(shape);

                if (vertexView.label != null) {
                    graphics.setColor(vertexView.labelColor);
                    graphics.drawString(vertexView.label, (float) shape.getCurrentPoint().getX()
                            + fontSize, (float) shape.getCurrentPoint().getY());
                }

                n++;
                if (n % 10000 == 0) {
                    LOG.info("{} vertex processed.", n);
                }
            }
        }

        try {
            painter.save();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Path2D.Double convertGeometry(LineString geometry, AffineTransform transform,
            double offsetRight) {
        Path2D.Double retval = new Path2D.Double();
        Coordinate coords[] = geometry.getCoordinates();
        // Get end-points
        Point2D A = transform.transform(new Point2D.Double(coords[0].x, coords[0].y), null);
        Point2D B = transform.transform(new Point2D.Double(coords[coords.length - 1].x,
                coords[coords.length - 1].y), null);
        double dx = B.getX() - A.getX();
        double dy = B.getY() - A.getY();
        double l = FastMath.sqrt(dx * dx + dy * dy);
        double xoffset = 0, yoffset = 0;
        if (l > 1E-10) {
            xoffset = -dy / l * offsetRight;
            yoffset = dx / l * offsetRight;
        }
        retval.moveTo(A.getX() + xoffset, A.getY() + yoffset);
        for (int i = 1; i < coords.length - 1; i++) {
            Point2D p = transform.transform(new Point2D.Double(coords[i].x, coords[i].y), null);
            retval.lineTo(p.getX() + xoffset, p.getY() + yoffset);
        }
        retval.lineTo(B.getX() + xoffset, B.getY() + yoffset);
        return retval;
    }

    private Point2D findMidPoint(LineString geometry, AffineTransform transform) {
        Coordinate coords[] = geometry.getCoordinates();
        // This is approximate, but will end-up somewhere along the line
        Coordinate mid1 = coords[coords.length / 2 - 1]; // Can't underflow
        Coordinate mid2 = coords[coords.length / 2];
        Point2D p = transform.transform(new Point2D.Double((mid1.x + mid2.x) / 2.0,
                (mid1.y + mid2.y) / 2.0), null);
        return p;
    }

    private Path2D.Double convertGeometry(double x, double y, AffineTransform transform) {
        // There does not seem to have "point" as shape. Use a zero-length line
        Path2D.Double retval = new Path2D.Double();
        Point2D p = transform.transform(new Point2D.Double(x, y), null);
        retval.moveTo(p.getX(), p.getY());
        retval.lineTo(p.getX(), p.getY());
        return retval;
    }

    @Override
    public void checkInputs() {
        // no inputs to check
    }
}
