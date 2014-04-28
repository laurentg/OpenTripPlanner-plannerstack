package org.opentripplanner.api.parameter;

import java.util.List;
import java.util.Set;

import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.core.TraverseMode;
import org.opentripplanner.routing.core.TraverseModeSet;

import com.beust.jcommander.internal.Sets;
import com.google.common.collect.Lists;

/**
 * An ordered list of sets of qualified modes. For example, if someone was in possession of a car
 * and wanted to park it and/or walk before taking a train or a tram, and finally rent a bicycle to
 * reach the destination: CAR_HAVE_PARK,WALK;TRAIN,TRAM;BIKE_RENT
 * It might also make sense to allow slashes meaning "or", or simply the word "or".
 * 
 * This class and QualifiedMode are clearly somewhat inefficient and allow nonsensical combinations like
 * renting and parking a subway. They are not intended for use in routing. Rather, they simply parse the
 * language of mode specifications that may be given in the mode query parameter. They are then converted
 * into more efficient and useful representation in the routing request.
 */
public class QualifiedModeSetSequence {

    public List<Set<QualifiedMode>> sets = Lists.newArrayList();
    
    public QualifiedModeSetSequence(String s) {
        for (String seg : s.split(";")) {
            Set<QualifiedMode> qModeSet = Sets.newHashSet();
            for (String qMode : seg.split(",")) {
                qModeSet.add(new QualifiedMode(qMode));
            }
            if (!qModeSet.isEmpty()) {
                sets.add(qModeSet);
            }

        }
    }
    
    /**
     * Modify an existing routing request, setting fields to reflect these qualified modes.
     * This is intended as a temporary solution, and uses the current system of a single mode set,
     * accompanied by some flags to help with routing.
     */
    public void applyToRequest(RoutingRequest req) {
        /* Start with an empty mode set. */
        TraverseModeSet modes = new TraverseModeSet();
        /* Use only the first set of qualified modes for now. */
        if (!sets.isEmpty()) {
            Set<QualifiedMode> firstModes = sets.get(0);
            // First, copy over all the modes
            for (QualifiedMode qMode : firstModes) {
                modes.setMode(qMode.mode, true);
            }
            for (QualifiedMode qMode : firstModes) {
                if (qMode.mode == TraverseMode.BICYCLE) {
                    if (qMode.qualifiers.contains(Qualifier.RENT)) {
                        req.allowBikeRental = true;
                    }
                    if (qMode.qualifiers.contains(Qualifier.PARK)) {
                        req.bikeParkAndRide = true;
                    }
                }
                if (qMode.mode == TraverseMode.CAR) {
                    if (qMode.qualifiers.contains(Qualifier.PARK)) {
                        req.parkAndRide = true;
                    } else {
                        req.kissAndRide = true;
                    }
                }
            }
            // If we have at least two set of modes,
            // take the last one as final condition
            if (sets.size() >= 2) {
                Set<QualifiedMode> lastModes = sets.get(sets.size() - 1);
                for (QualifiedMode qMode : lastModes) {
                    if (qMode.mode == TraverseMode.BICYCLE) {
                        if (qMode.qualifiers.contains(Qualifier.RENT)) {
                            req.allowBikeRental = true;
                            req.endRentingBike = true;
                        }
                    } else {
                        throw new RuntimeException("Unsupported mode for end section: "
                                + qMode.mode);
                    }
                }
            }
        }
        req.setModes(modes);
    }

}
