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

import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

import org.onebusaway.gtfs.model.AgencyAndId;

/**
 * Service attached to a graph. Store some mapping (stop indexes, base calendar date).
 * 
 * @author laurent
 */
public class RrrrService implements Serializable {
    private static final long serialVersionUID = 2L;

    /**
     * Index of stop ID to RRRR internal ID's. Could be redundant if RRRR would accept stop ID's
     * from the request.
     */
    private Map<AgencyAndId, Integer> stopIndexes = new HashMap<>();

    /** Midnight of the first day in the 32-day calendar in seconds since the epoch, DST ignorant */
    @Getter
    @Setter
    private Date calendarStart;

    @Getter
    @Setter
    private String defaultAgencyId;

    void addStopIndex(int stopIndex, AgencyAndId stopId) {
        stopIndexes.put(stopId, stopIndex);
    }

    public Integer getStopIndex(AgencyAndId stopId) {
        return stopIndexes.get(stopId);
    }
}
