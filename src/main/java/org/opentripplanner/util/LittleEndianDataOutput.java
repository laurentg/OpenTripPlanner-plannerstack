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

package org.opentripplanner.util;

import java.io.DataOutput;
import java.io.IOException;

/**
 * A little-endian implementation of a DataOutput. Switch byte order of short, int, and long data
 * regarding Java byte order (always big-endian).
 *
 * @author laurent
 */
public class LittleEndianDataOutput implements DataOutput {

    private DataOutput output;

    public LittleEndianDataOutput(DataOutput output) {
        this.output = output;
    }

    @Override
    public void write(int b) throws IOException {
        output.write(b);
    }

    @Override
    public void write(byte[] b) throws IOException {
        output.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        output.write(b, off, len);
    }

    @Override
    public void writeBoolean(boolean v) throws IOException {
        output.writeBoolean(v);
    }

    @Override
    public void writeByte(int v) throws IOException {
        output.writeByte(v);
    }

    @Override
    public void writeShort(int v) throws IOException {
        output.write(0xFF & v);
        output.write(0xFF & (v >> 8));
    }

    @Override
    public void writeChar(int v) throws IOException {
        output.writeChar(v);
    }

    @Override
    public void writeInt(int v) throws IOException {
        output.write(0xFF & v);
        output.write(0xFF & (v >> 8));
        output.write(0xFF & (v >> 16));
        output.write(0xFF & (v >> 24));
    }

    @Override
    public void writeLong(long v) throws IOException {
        output.write((int) (0xFF & v));
        output.write((int) (0xFF & (v >> 8)));
        output.write((int) (0xFF & (v >> 16)));
        output.write((int) (0xFF & (v >> 24)));
        output.write((int) (0xFF & (v >> 32)));
        output.write((int) (0xFF & (v >> 40)));
        output.write((int) (0xFF & (v >> 48)));
        output.write((int) (0xFF & (v >> 56)));
    }

    @Override
    public void writeFloat(float v) throws IOException {
        writeInt(Float.floatToIntBits(v));
    }

    @Override
    public void writeDouble(double v) throws IOException {
        writeLong(Double.doubleToLongBits(v));
    }

    @Override
    public void writeBytes(String s) throws IOException {
        output.writeBytes(s);
    }

    @Override
    public void writeChars(String s) throws IOException {
        output.writeChars(s);
    }

    @Override
    public void writeUTF(String s) throws IOException {
        output.writeUTF(s);
    }
}
