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

import java.io.ByteArrayOutputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.Calendar;
import java.util.Date;

import lombok.Setter;

import org.opentripplanner.api.resource.Response;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.core.TraverseModeSet;
import org.opentripplanner.util.LittleEndianDataOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQException;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Proxy used to communicate with a RRRR broker/worker through ZMQ.
 * 
 * @author laurent
 */
public class RrrrProxy {

    private static final Logger LOG = LoggerFactory.getLogger(RrrrProxy.class);

    private static final String RRRR_CLIENT_ADDR = "tcp://localhost:9292";

    private static final int RECV_TIMEOUT_MS = 10000;

    private static final int SEND_TIMEOUT_MS = 5000;

    private static final int NONE = 0xFFFFFFFF;

    private static final int UNREACHED = 0xFFFF;

    private static final int AGENCY_UNFILTERED = 0xFFFF;

    private ZContext zContext;

    private Socket zmqSocket;

    @Setter
    private Date baseDate;

    public RrrrProxy() {
        LOG.info("Connecting to RRRR server @" + RRRR_CLIENT_ADDR);
        zContext = new ZContext();
    }

    // TODO Should we need to synchronize this?
    public Response sendRequest(RoutingRequest req, int fromStopIndex, int toStopIndex) {

        try {
            if (zmqSocket == null)
                initializeZMQ();

            byte[] reqbuf = buildRequest(req, fromStopIndex, toStopIndex);
            zmqSocket.send(reqbuf);

            byte[] replyBuf = zmqSocket.recv(0);
            if (replyBuf == null) {
                throw new IOException("Received NULL data from RRRR");
            }
            String reply = new String(replyBuf, "UTF-8");
            LOG.info("RRRR reply=" + reply);

            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            Response response = mapper.readValue(reply, Response.class);
            return response;

        } catch (IOException | ZMQException e) {
            LOG.warn("Error contacting RRRR.", e);
            zContext.destroySocket(zmqSocket);
            zmqSocket = null; // Force re-init
            throw new RuntimeException("Error contacting RRRR", e);
        }
    }

    private synchronized void initializeZMQ() {
        if (zmqSocket != null)
            return;
        zmqSocket = zContext.createSocket(ZMQ.REQ);
        zmqSocket.setReceiveTimeOut(RECV_TIMEOUT_MS);
        zmqSocket.setSendTimeOut(SEND_TIMEOUT_MS);
        zmqSocket.connect(RRRR_CLIENT_ADDR);
    }

    /**
     * <pre>
     *     uint32_t from;       // start stop index from the user's perspective, independent of arrive_by
     *     uint32_t to;         // destination stop index from the user's perspective, independent of arrive_by
     *     uint32_t via;        // preferred transfer stop index from the user's perspective, default: NONE
     *     uint32_t start_trip_route; // for onboard departure: route index on which to begin
     *     uint32_t start_trip_trip;  // for onboard departure: trip index within that route
     *     rtime_t time;        // the departure or arrival time at which to search (in internal rtime)
     *     rtime_t time_cutoff; // the latest (or earliest in arrive_by) acceptable time to reach the destination
     *     double walk_speed;   // speed at which the user walks, in meters per second
     *     uint8_t walk_slack;  // an extra delay per transfer, in seconds
     *     bool arrive_by;      // whether the given time is an arrival time rather than a departure time
     *     bool time_rounded;   // whether the requested time had to be rounded down to fit in an rtime field
     *     uint32_t max_transfers;  // the largest number of transfers to allow in the result
     *     calendar_t day_mask; // bit for the day on which we are searching, relative to the timetable calendar
     *     uint8_t mode;        // selects the mode by a bitfield
     *     #ifdef FEATURE_AGENCY_FILTER
     *     uint16_t agency;     // filters routes by a specific agency
     *     #endif
     *     uint8_t trip_attributes; // select required attributes bitfield (from trips)
     *     uint8_t optimise;    // restrict the output to specific optimisation flags
     *     uint32_t n_banned_routes; // 1
     *     uint32_t n_banned_stops; // 1
     *     uint32_t n_banned_stops_hard; // 1
     *     uint32_t n_banned_trips; // 1
     *     uint32_t banned_route; // One route which is banned
     *     uint32_t banned_stop; // One stop which is banned
     *     uint32_t banned_trip_route; // One trip which is banned, this is its route
     *     uint32_t banned_trip_offset; // One trip which is banned, this is its tripoffset
     *     uint32_t banned_stop_hard; // One stop which is banned
     *     bool intermediatestops; // Show intermetiastops in the output
     * </pre>
     */
    protected byte[] buildRequest(RoutingRequest req, int fromStopIndex, int toStopIndex) {

        int mode = convertMode(req.getModes());
        Calendar cal = Calendar.getInstance(); // Which timezone?
        // TODO Adjust time based on a cutoff (for example departure before 4 AM ?)
        cal.setTime(req.getDateTime());
        int time = cal.get(Calendar.HOUR_OF_DAY) * 3600 + cal.get(Calendar.MINUTE) * 60
                + cal.get(Calendar.SECOND) + 3600 * 24;
        // TODO Should send a date/time in the request, simpler
        int dateOffset = (int) ((req.getDateTime().getTime() - baseDate.getTime()) / (1000 * 60 * 60 * 24));
        if (dateOffset > 31) {
            LOG.warn(String
                    .format("Date %s outside RRRR 32 days range, approximating with another date same day of the week.",
                            req.getDateTime()));
            dateOffset %= 28;
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            // TODO Convert to protobuf for the request?
            boolean hostBigEndian = ByteOrder.nativeOrder().equals(ByteOrder.BIG_ENDIAN);
            DataOutput dos = new DataOutputStream(baos);
            if (!hostBigEndian)
                dos = new LittleEndianDataOutput(dos);

            dos.writeInt(fromStopIndex); // from
            dos.writeInt(toStopIndex); // to
            dos.writeInt(NONE); // via
            dos.writeInt(NONE); // start_trip_route
            dos.writeInt(NONE); // start_trip_trip
            dos.writeShort(time >> 2); // time
            dos.writeShort(UNREACHED); // time_cutoff
            dos.writeDouble(req.getWalkSpeed()); // walk_speed
            dos.writeByte(req.getWalkBoardCost()); // walk_slack
            dos.writeBoolean(req.isArriveBy()); // arrive_by
            dos.writeBoolean(false); // time_rounded
            dos.writeByte(0); // padding
            dos.writeInt(req.getMaxTransfers()); // max_transfers
            dos.writeInt(1 << dateOffset); // day_mask
            dos.writeByte(mode); // mode
            dos.writeByte(0); // padding
            dos.writeShort(AGENCY_UNFILTERED); // agency
            dos.writeByte(0); // TODO trip_attributes (wheelchair)
            dos.writeByte(255); // TODO optimize
            // TODO Banned stop, trips, etc...
            dos.writeShort(0); // padding
            dos.writeInt(0); // n_banned_routes
            dos.writeInt(0); // n_banned_stops
            dos.writeInt(0); // n_banned_stops_hard
            dos.writeInt(0); // n_banned_trips
            dos.writeInt(NONE); // banned_route
            dos.writeInt(NONE); // banned_stop
            dos.writeInt(NONE); // banned_trip_route
            dos.writeInt(NONE); // banned_trip_offset
            dos.writeInt(NONE); // banned_stop_hard
            dos.writeByte(0); // intermediatestops
            dos.writeByte(0); // pad
            dos.writeShort(0); // pad
            dos.writeInt(0); // pad

            baos.close();
        } catch (IOException e) {
            // Can't happen
            throw new RuntimeException(e);
        }
        return baos.toByteArray();
    }

    private int convertMode(TraverseModeSet modes) {
        /**
         * <pre>
         * typedef enum tmode {
         *     m_tram      =   1,
         *     m_subway    =   2,
         *     m_rail      =   4,
         *     m_bus       =   8,
         *     m_ferry     =  16,
         *     m_cablecar  =  32,
         *     m_gondola   =  64,
         *     m_funicular = 128,
         *     m_all       = 255
         * } tmode_t;
         * </pre>
         */
        int retval = 0;
        if (modes.getTram())
            retval |= 1;
        if (modes.getSubway())
            retval |= 2;
        if (modes.getRail())
            retval |= 4;
        if (modes.getBus())
            retval |= 8;
        if (modes.getFerry())
            retval |= 16;
        if (modes.getCableCar())
            retval |= 32;
        if (modes.getGondola())
            retval |= 64;
        if (modes.getFunicular())
            retval |= 128;
        return retval;
    }
}
