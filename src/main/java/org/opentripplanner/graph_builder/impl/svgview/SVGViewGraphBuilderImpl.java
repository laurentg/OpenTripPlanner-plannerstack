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
import java.awt.Stroke;
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
import org.opentripplanner.common.geometry.GeometryUtils;
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
    private float lineWidth = 1.5f;

    @Setter
    private float pointWidth = lineWidth * 2.5f;

    @Setter
    private Font labelFont = new Font(Font.SANS_SERIF, Font.PLAIN, 4);

    @Setter
    private Font legendFont = new Font(Font.SANS_SERIF, Font.BOLD, 50);

    // TODO Support multiple edgeRenderer and vertexRenderer
    @Setter
    private EdgeRenderer edgeRenderer;

    @Setter
    private VertexRenderer vertexRenderer;

    @Setter
    private int edgesPerSVG = 30000;

    private int nxy = 1;

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

        int edges = graph.getEdges().size();
        nxy = (int) Math.round(Math.sqrt(edges / edgesPerSVG)) + 1;
        Envelope extent = graph.getExtent();

        LOG.info("Generating {} SVG graph view to {}-x-y.svg", nxy * nxy, svgOutputFilePrefix);

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
        SVGPainter[][] painters = new SVGPainter[nxy][nxy];
        Graphics2D[][] graphics = new Graphics2D[nxy][nxy];
        for (int ix = 0; ix < nxy; ix++)
            for (int iy = 0; iy < nxy; iy++) {
                painters[ix][iy] = new SVGPainter(new File(String.format("%s-%d-%d.svg",
                        svgOutputFilePrefix, ix, iy)), canvasSize);
                graphics[ix][iy] = painters[ix][iy].getGraphics();
            }

        Stroke lineStroke = new BasicStroke(lineWidth, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL);
        Stroke pointStroke = new BasicStroke(pointWidth, BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_BEVEL);

        /* Draw rectangle bounds in RED for each sub-region SVG */
        for (int ix = 0; ix < nxy; ix++)
            for (int iy = 0; iy < nxy; iy++) {
                Graphics2D graphic = graphics[ix][iy];
                Path2D.Double bounds = new Path2D.Double();
                bounds.moveTo(1.0 * ix * canvasSize.getWidth() / nxy,
                        1.0 * iy * canvasSize.getHeight() / nxy);
                bounds.lineTo(1.0 * (ix + 1) * canvasSize.getWidth() / nxy,
                        1.0 * iy * canvasSize.getHeight() / nxy);
                bounds.lineTo(1.0 * (ix + 1) * canvasSize.getWidth() / nxy, 1.0 * (iy + 1)
                        * canvasSize.getHeight() / nxy);
                bounds.lineTo(1.0 * ix * canvasSize.getWidth() / nxy,
                        1.0 * (iy + 1) * canvasSize.getHeight() / nxy);
                bounds.closePath();
                graphic.setColor(Color.RED);
                graphic.draw(bounds);
            }

        if (edgeRenderer != null) {
            int n = 0;
            for (Edge e : graph.getEdges()) {
                EdgeView edgeView = edgeRenderer.render(e);
                if (edgeView == null)
                    continue;

                Graphics2D graphic = findGraphics(e.getFromVertex().getLon(), e.getFromVertex()
                        .getLat(), graphics, transform, canvasSize);

                LineString geometry = e.getGeometry();
                if (geometry == null)
                    geometry = GeometryUtils.getGeometryFactory().createLineString(
                            new Coordinate[] { e.getFromVertex().getCoordinate(),
                                    e.getToVertex().getCoordinate() });
                Path2D.Double shape = convertGeometry(geometry, transform, lineWidth * 0.7);

                graphic.setFont(labelFont);
                graphic.setColor(edgeView.color != null ? edgeView.color : Color.GRAY);
                graphic.setStroke(lineStroke);
                graphic.draw(shape);

                if (edgeView.label != null) {
                    Point2D labelPosition = findMidPoint(e.getGeometry(), transform);
                    graphic.setColor(edgeView.labelColor);
                    graphic.drawString(edgeView.label, (float) labelPosition.getX(),
                            (float) labelPosition.getY());
                }

                n++;
                if (n % 10000 == 0) {
                    LOG.info("{} edges processed.", n);
                }
            }

            /* Render legend */
            for (int ix = 0; ix < nxy; ix++)
                for (int iy = 0; iy < nxy; iy++) {
                    Graphics2D graphic = graphics[ix][iy];
                    graphic.setFont(legendFont);
                    int yLegend = 0;
                    for (LegendLabelView legendLabel : edgeRenderer.getLegend()) {
                        graphic.setColor(legendLabel.color);
                        graphic.fillRect(MAX_COORDINATE + 100, yLegend, legendFont.getSize() * 4,
                                legendFont.getSize());
                        graphic.setColor(Color.BLACK);
                        graphic.drawString(legendLabel.label,
                                MAX_COORDINATE + 100 + legendFont.getSize() * 5, yLegend
                                        + legendFont.getSize());
                        yLegend += legendFont.getSize() * 2;
                    }
                }
        }

        if (vertexRenderer != null) {
            int n = 0;
            for (Vertex v : graph.getVertices()) {
                VertexView vertexView = vertexRenderer.render(v);
                if (vertexView == null)
                    continue;

                Graphics2D graphic = findGraphics(v.getLon(), v.getLat(), graphics, transform,
                        canvasSize);
                Path2D.Double shape = convertGeometry(v.getLon(), v.getLat(), transform);
                graphic.setStroke(pointStroke);
                graphic.setColor(vertexView.color != null ? vertexView.color : Color.GRAY);
                graphic.draw(shape);

                if (vertexView.label != null) {
                    graphic.setFont(labelFont);
                    graphic.setColor(vertexView.labelColor);
                    graphic.drawString(vertexView.label, (float) shape.getCurrentPoint().getX()
                            + labelFont.getSize(), (float) shape.getCurrentPoint().getY());
                }

                n++;
                if (n % 10000 == 0) {
                    LOG.info("{} vertex processed.", n);
                }
            }
        }

        try {
            for (int ix = 0; ix < nxy; ix++)
                for (int iy = 0; iy < nxy; iy++) {
                    painters[ix][iy].save();
                }
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

    private Graphics2D findGraphics(double lon, double lat, Graphics2D[][] graphics,
            AffineTransform transform, Dimension canvasSize) {
        Point2D p = transform.transform(new Point2D.Double(lon, lat), null);
        int ix = (int) (p.getX() / canvasSize.getWidth() * nxy);
        if (ix < 0)
            ix = 0;
        if (ix >= nxy)
            ix = nxy - 1;
        int iy = (int) (p.getY() / canvasSize.getHeight() * nxy);
        if (iy < 0)
            iy = 0;
        if (iy >= nxy)
            iy = nxy - 1;
        return graphics[ix][iy];
    }

    @Override
    public void checkInputs() {
        // no inputs to check
    }
}
