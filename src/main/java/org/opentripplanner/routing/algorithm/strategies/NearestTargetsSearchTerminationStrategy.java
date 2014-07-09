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

package org.opentripplanner.routing.algorithm.strategies;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import lombok.Getter;

import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.core.State;
import org.opentripplanner.routing.graph.Vertex;
import org.opentripplanner.routing.spt.ShortestPathTree;

/**
 * A search termination strategy that look up for a certain class of Vertex and stop the search once
 * "n" of them have been found, or a maxWeight has been reached.
 * 
 * @author laurent
 * 
 * @param <T>
 */
public class NearestTargetsSearchTerminationStrategy<T extends Vertex> implements
        SearchTerminationStrategy {

    private final Set<T> reachedTargetsSet = new HashSet<>();

    @Getter
    private final List<T> reachedTargetsList = new ArrayList<>();

    private final double maxWeight;

    private final int limitTo;

    private final Class<? extends Vertex> vertexClass;

    public NearestTargetsSearchTerminationStrategy(Class<T> vertexClass, double maxWeight,
            int limitTo) {
        this.maxWeight = maxWeight;
        this.limitTo = limitTo;
        this.vertexClass = vertexClass;
    }

    /**
     * Updates the list of reached vertex of valid type and returns true if: walk distance is above
     * the maximum, or if at least limitTo target have been reached.
     */
    @SuppressWarnings("unchecked")
    @Override
    public boolean shouldSearchContinue(Vertex origin, Vertex target, State current,
            ShortestPathTree spt, RoutingRequest traverseOptions) {
        Vertex currentVertex = current.getVertex();

        if (currentVertex.getClass().equals(vertexClass)) {
            if (reachedTargetsSet.add((T) currentVertex)) {
                reachedTargetsList.add((T) currentVertex);
            }
        }

        return (reachedTargetsSet.size() < limitTo) && (current.getWeight() < maxWeight);
    }
}
