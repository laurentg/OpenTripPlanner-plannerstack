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

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;

import org.apache.batik.dom.GenericDOMImplementation;
import org.apache.batik.svggen.SVGGraphics2D;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;

/**
 * Utility class to create a SVG file via a simple Paintable interface. Basically a convenient
 * wrapper around Batik to prevent dependency-leakage to some other part of the code.
 * 
 * @author laurent
 */
public class SVGPainter {

    private static final Logger LOG = LoggerFactory.getLogger(SVGPainter.class);

    private File svgOutputFile;

    private SVGGraphics2D svgGraphics;

    private Dimension canvasSize;

    public SVGPainter(File svgOutputFile, Dimension canvasSize) {
        this.svgOutputFile = svgOutputFile;
        DOMImplementation domImpl = GenericDOMImplementation.getDOMImplementation();
        Document document = domImpl.createDocument("http://www.w3.org/2000/svg", "svg", null);
        svgGraphics = new SVGGraphics2D(document);
        this.canvasSize = canvasSize;
    }

    public Graphics2D getGraphics() {
        return svgGraphics;
    }

    public void save() throws IOException {
        svgGraphics.setSVGCanvasSize(canvasSize);
        Writer out = new OutputStreamWriter(new FileOutputStream(svgOutputFile), "UTF-8");
        LOG.info("Writing SVG file: {}", svgOutputFile);
        long start = System.currentTimeMillis();
        svgGraphics.stream(out, true);
        LOG.info("SVG file {} write OK (took {} ms)", svgOutputFile, System.currentTimeMillis()
                - start);
    }
}
