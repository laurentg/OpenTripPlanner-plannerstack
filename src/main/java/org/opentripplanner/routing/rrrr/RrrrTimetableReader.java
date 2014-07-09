package org.opentripplanner.routing.rrrr;

import java.io.DataInput;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.util.Date;

import org.onebusaway.gtfs.model.AgencyAndId;
import org.opentripplanner.util.LittleEndianDataInput;

/**
 * Read a RRRR timetable to extract the stop ID mapping and the base date.
 * 
 * TODO: remove this when RRRR is able to interpret stops GTFS IDs in the request and date/time in
 * some standard format.
 * 
 * @author laurent
 */
public class RrrrTimetableReader {

    private static final String TTABLE_MAGIC = "TTABLEV2";

    public RrrrTimetableReader() {
    }

    @SuppressWarnings("unused")
    public void load(RrrrService rrrrService, File tablefile) throws IOException {
        // TODO RRRR is platform dependent on endianness, make this configurable.
        // By default, take endianness of JVM assuming RRRR is running on the
        // same machine.
        boolean hostBigEndian = ByteOrder.nativeOrder().equals(ByteOrder.BIG_ENDIAN);
        RandomAccessFile input = new RandomAccessFile(tablefile, "r");
        DataInput dis;
        if (hostBigEndian)
            dis = input;
        else
            dis = new LittleEndianDataInput(input);
        try {
            byte[] magic = new byte[TTABLE_MAGIC.length()];
            dis.readFully(magic);
            String magicString = new String(magic, "ASCII");
            if (!TTABLE_MAGIC.equals(magicString))
                throw new IOException("Invalid magic: " + magicString);

            // Read header
            long calendarStart = dis.readLong();
            long calendarDst = dis.readInt();
            int nStops = dis.readInt();
            int nRoutes = dis.readInt();
            int nTrips = dis.readInt();
            int nAgencies = 1; // ?

            long locStops = dis.readInt();
            long locStopAttributes = dis.readInt();
            long locStopCoords = dis.readInt();
            long locRoutes = dis.readInt();
            long locRouteStops = dis.readInt();
            long locRouteStopAttributes = dis.readInt();
            long locStopTimes = dis.readInt();
            long locTrips = dis.readInt();
            long locTripAttributes = dis.readInt();
            long locStopRoutes = dis.readInt();
            long locTransferTargetStops = dis.readInt();
            long locTransferDistMeters = dis.readInt();
            long locTripActive = dis.readInt();
            long locRouteActive = dis.readInt();
            long locPlatformCodes = dis.readInt();
            long locStopNames = dis.readInt();
            long locStopNameIdx = dis.readInt();
            long locAgencyIds = dis.readInt();
            long locAgencyNames = dis.readInt();
            long locAgencyUrls = dis.readInt();
            long locHeadsigns = dis.readInt();
            long locRouteShortnames = dis.readInt();
            long locProductCategories = dis.readInt();
            long locRouteIds = dis.readInt();
            long locStopIds = dis.readInt();
            long locTripIds = dis.readInt();

            // Set the calendar start
            rrrrService.setCalendarStart(new Date(calendarStart * 1000));

            // Take the first agency as default ID
            // TODO This is a hack to replace RRRR stop agency IDs, hardcoded to NL
            input.seek(locAgencyIds);
            int agencyIdWidth = dis.readInt();
            String defaultAgencyId = readCString(dis, agencyIdWidth);
            rrrrService.setDefaultAgencyId(defaultAgencyId);

            // Read stop IDs
            input.seek(locStopIds);
            int stopIdWidth = dis.readInt();
            for (int i = 0; i < nStops; i++) {
                String stopGtfsId = readCString(dis, stopIdWidth);
                rrrrService.addStopIndex(i, new AgencyAndId(defaultAgencyId, stopGtfsId));
            }

            // Read stop names
            int[] stopNameIndex = new int[nStops];
            input.seek(locStopNameIdx);
            for (int i = 0; i < nStops; i++) {
                stopNameIndex[i] = dis.readInt();
            }
            for (int i = 0; i < nStops; i++) {
                input.seek(locStopNames + stopNameIndex[i]);
                String name = readCString(dis, 4096);
            }

        } finally {
            input.close();
        }
    }

    // TODO Allow infinite width (strings are null-terminated)
    private static String readCString(DataInput dis, int width) throws IOException {
        byte[] data = new byte[width];
        dis.readFully(data);
        int len = 0;
        while (len < width && data[len] != 0x0)
            len++;
        return new String(data, 0, len, "UTF-8");
    }
}
