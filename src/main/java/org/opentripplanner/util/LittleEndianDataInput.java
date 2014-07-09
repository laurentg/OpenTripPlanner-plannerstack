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

import java.io.DataInput;
import java.io.IOException;

/**
 * A little-endian implementation of a DataInput. Switch byte order of short, int, and long data
 * regarding Java byte order (always big-endian).
 * 
 * @author laurent
 */
public class LittleEndianDataInput implements DataInput {

    private DataInput input;

    public LittleEndianDataInput(DataInput input) {
        this.input = input;
    }

    @Override
    public void readFully(byte[] b) throws IOException {
        input.readFully(b);
    }

    @Override
    public void readFully(byte[] b, int off, int len) throws IOException {
        input.readFully(b, off, len);
    }

    @Override
    public int skipBytes(int n) throws IOException {
        return input.skipBytes(n);
    }

    @Override
    public boolean readBoolean() throws IOException {
        return readUnsignedByte() != 0;
    }

    @Override
    public byte readByte() throws IOException {
        return input.readByte();
    }

    @Override
    public int readUnsignedByte() throws IOException {
        return input.readByte();
    }

    @Override
    public short readShort() throws IOException {
        return (short) readUnsignedShort();
    }

    @Override
    public int readUnsignedShort() throws IOException {
        byte b1 = input.readByte();
        byte b2 = input.readByte();
        return intFromBytes((byte) 0, (byte) 0, b2, b1);
    }

    @Override
    public char readChar() throws IOException {
        return input.readChar();
    }

    @Override
    public int readInt() throws IOException {
        byte b1 = input.readByte();
        byte b2 = input.readByte();
        byte b3 = input.readByte();
        byte b4 = input.readByte();
        return intFromBytes(b4, b3, b2, b1);
    }

    @Override
    public long readLong() throws IOException {
        byte b1 = input.readByte();
        byte b2 = input.readByte();
        byte b3 = input.readByte();
        byte b4 = input.readByte();
        byte b5 = input.readByte();
        byte b6 = input.readByte();
        byte b7 = input.readByte();
        byte b8 = input.readByte();
        return longFromBytes(b8, b7, b6, b5, b4, b3, b2, b1);
    }

    @Override
    public float readFloat() throws IOException {
        return Float.intBitsToFloat(readInt());
    }

    @Override
    public double readDouble() throws IOException {
        return Double.longBitsToDouble(readLong());
    }

    @Override
    public String readLine() throws IOException {
        return input.readLine();
    }

    @Override
    public String readUTF() throws IOException {
        return input.readUTF();
    }

    private static int intFromBytes(byte b1, byte b2, byte b3, byte b4) {
        return b1 << 24 | (b2 & 0xFF) << 16 | (b3 & 0xFF) << 8 | (b4 & 0xFF);
    }

    private static long longFromBytes(byte b1, byte b2, byte b3, byte b4, byte b5, byte b6,
            byte b7, byte b8) {
        return (b1 & 0xFFL) << 56 | (b2 & 0xFFL) << 48 | (b3 & 0xFFL) << 40 | (b4 & 0xFFL) << 32
                | (b5 & 0xFFL) << 24 | (b6 & 0xFFL) << 16 | (b7 & 0xFFL) << 8 | (b8 & 0xFFL);
    }
}
