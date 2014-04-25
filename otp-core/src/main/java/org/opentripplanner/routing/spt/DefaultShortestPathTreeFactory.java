package org.opentripplanner.routing.spt;

import org.opentripplanner.routing.core.RoutingRequest;

/**
 * Default implementation of ShortestPathTreeFactory.
 * 
 * Creates a MultiShortestPathTree for any transit, bike rental, car/bike P+R, car kiss-and-ride.
 * Otherwise uses BasicShortestPathTree.
 * 
 * @author avi
 */
public class DefaultShortestPathTreeFactory implements ShortestPathTreeFactory {

    @Override
    public ShortestPathTree create(RoutingRequest options) {
        ShortestPathTree spt = null;
        if (options.getModes().isTransit() || options.allowBikeRental || options.bikeParkAndRide
                || options.parkAndRide || options.kissAndRide) {
            spt = new MultiShortestPathTree(options);
        } else {
            spt = new BasicShortestPathTree(options);
        }
        return spt;
    }

}
