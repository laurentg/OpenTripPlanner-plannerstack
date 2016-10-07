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

package org.opentripplanner.scripting.api;

import org.opentripplanner.common.geometry.SphericalDistanceLibrary;

/**
 * Simple geographical coordinates.
 * 
 * @author laurent
 */
public class OtpsLatLon {

    private double lat, lon;

    protected OtpsLatLon(double lat, double lon) {
        this.lat = lat;
        this.lon = lon;
    }

    /**
     * @return The latitude.
     */
    public double getLat() {
        return lat;
    }

    /**
     * @return The longitude.
     */
    public double getLon() {
        return lon;
    }

    /**
     * @return The approximate (fast to compute) distance between two points. Note: this is the fast
     *         version and can lead to small imprecision when the two points are very further apart.
     *         For small distances (< few kms) the error is usually negligeable.
     */
    public double fastDistance(OtpsLatLon loc) {
        return SphericalDistanceLibrary.fastDistance(this.lat, this.lon, loc.lat, loc.lon);
    }

    /**
     * @return The exact (slower to compute) haversine distance between two points.
     */
    public double distance(OtpsLatLon loc) {
        return SphericalDistanceLibrary.distance(this.lat, this.lon, loc.lat, loc.lon);
    }

    @Override
    public String toString() {
        return "(" + lat + "," + lon + ")";
    }

}
