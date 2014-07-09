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

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import lombok.Setter;

import org.opentripplanner.graph_builder.services.GraphBuilder;
import org.opentripplanner.routing.graph.Graph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RrrrGraphBuilder implements GraphBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(RrrrGraphBuilder.class);

    @Setter
    private File timetableDatFile;

    @Override
    public void buildGraph(Graph graph, HashMap<Class<?>, Object> extra) {

        LOG.info("Loading RRRR stop indexes from timetable.");

        RrrrService rrrrService = graph.getService(RrrrService.class, true);
        RrrrTimetableReader reader = new RrrrTimetableReader();
        try {
            // Load stop indexes
            reader.load(rrrrService, timetableDatFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        LOG.info("Loading RRRR timetable: done.");
    }

    @Override
    public List<String> provides() {
        // Provides RRRR data
        return Arrays.asList("rrrr");
    }

    @Override
    public List<String> getPrerequisites() {
        return Arrays.asList(); // Nothing
    }

    @Override
    public void checkInputs() {
        if (timetableDatFile == null) {
            throw new IllegalArgumentException("Did not specify a timetable.dat file!");
        }
        if (!timetableDatFile.exists()) {
            throw new RuntimeException(timetableDatFile + " does not exist.");
        }
        if (!timetableDatFile.canRead()) {
            throw new RuntimeException(timetableDatFile + " cannot be read.");
        }
    }

}
