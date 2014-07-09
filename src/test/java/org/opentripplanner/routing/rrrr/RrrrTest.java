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

package org.opentripplanner.routing.rrrr;

import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Calendar;

import org.junit.Test;
import org.opentripplanner.api.resource.Response;
import org.opentripplanner.routing.core.RoutingRequest;

public class RrrrTest {

    private static final byte[] REQBUF = { (byte) 0x2a, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0xb7, (byte) 0x07, (byte) 0x00, (byte) 0x00, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0x98, (byte) 0x85,
            (byte) 0xff, (byte) 0xff, (byte) 0x48, (byte) 0xe1, (byte) 0x7a, (byte) 0x14,
            (byte) 0xae, (byte) 0x47, (byte) 0xf5, (byte) 0x3f, (byte) 0x1e, (byte) 0x01,
            (byte) 0x00, (byte) 0x00, (byte) 0x03, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xff, (byte) 0x00,
            (byte) 0xff, (byte) 0xff, (byte) 0x00, (byte) 0xff, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, }; // (96 bytes)

    @SuppressWarnings("unused")
    private void dumpBufferAsJava(byte[] buf) {
        System.out.println("byte[] buf = {");
        for (int i = 0; i < buf.length; i++) {
            System.out.print(String.format("(byte)0x%02x, ", buf[i]));
            if (i % 16 == 15) {
                System.out.println();
            }
        }
        System.out.println(String.format("}; // (%d bytes)", buf.length));
    }

    @Test
    public void testRequestSerialization() {

        RrrrProxy rrrrProxy = new RrrrProxy();

        RoutingRequest req = new RoutingRequest();
        req.setArriveBy(true);
        req.setMaxTransfers(3);
        req.setWalkSpeed(1.33);
        req.setWalkBoardCost(30);
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 14);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.YEAR, 2014);
        cal.set(Calendar.MONTH, 07);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        req.setDateTime(cal.getTime());
        rrrrProxy.setBaseDate(cal.getTime());
        byte[] reqbuf = rrrrProxy.buildRequest(req, 42, 1975);
        // dumpBufferAsJava(reqbuf);
        assertTrue(Arrays.equals(reqbuf, REQBUF));
    }

    // Disabled, need a RRRR instance to run.
    // @Test
    public void __testRequest() {
        RrrrProxy rrrrProxy = new RrrrProxy();

        RoutingRequest req = new RoutingRequest();
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 14);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.YEAR, 2014);
        cal.set(Calendar.MONTH, 07);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        req.setDateTime(cal.getTime());
        rrrrProxy.setBaseDate(cal.getTime());
        Response response = rrrrProxy.sendRequest(req, 10, 40);
        System.out.println(response);
    }
}
