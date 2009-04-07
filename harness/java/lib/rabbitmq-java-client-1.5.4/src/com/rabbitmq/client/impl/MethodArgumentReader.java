//   The contents of this file are subject to the Mozilla Public License
//   Version 1.1 (the "License"); you may not use this file except in
//   compliance with the License. You may obtain a copy of the License at
//   http://www.mozilla.org/MPL/
//
//   Software distributed under the License is distributed on an "AS IS"
//   basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
//   License for the specific language governing rights and limitations
//   under the License.
//
//   The Original Code is RabbitMQ.
//
//   The Initial Developers of the Original Code are LShift Ltd,
//   Cohesive Financial Technologies LLC, and Rabbit Technologies Ltd.
//
//   Portions created before 22-Nov-2008 00:00:00 GMT by LShift Ltd,
//   Cohesive Financial Technologies LLC, or Rabbit Technologies Ltd
//   are Copyright (C) 2007-2008 LShift Ltd, Cohesive Financial
//   Technologies LLC, and Rabbit Technologies Ltd.
//
//   Portions created by LShift Ltd are Copyright (C) 2007-2009 LShift
//   Ltd. Portions created by Cohesive Financial Technologies LLC are
//   Copyright (C) 2007-2009 Cohesive Financial Technologies
//   LLC. Portions created by Rabbit Technologies Ltd are Copyright
//   (C) 2007-2009 Rabbit Technologies Ltd.
//
//   All Rights Reserved.
//
//   Contributor(s): ______________________________________.
//

package com.rabbitmq.client.impl;

import java.io.DataInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.rabbitmq.client.MalformedFrameException;

/**
 * Parses AMQP wire-protocol {@link Method} arguments from a
 * DataInputStream. Methods on this object are usually called from
 * autogenerated code.
 */
public class MethodArgumentReader
{
    private static final long INT_MASK = 0xffffffff;

    /**
     * Protected API - Cast an int to a long without extending the
     * sign bit of the int out into the high half of the long.
     */
    protected static long unsignedExtend(int value)
    {
        long extended = value;
        return extended & INT_MASK;
    }

    /** The stream we are reading from. */
    private final DataInputStream in;
    /** If we are reading one or more bits, holds the current packed collection of bits */
    private int bits;
    /** If we are reading one or more bits, keeps track of which bit position we are reading from */
    private int bit;

    /**
     * Private API - resets the bit group accumulator variables when
     * some non-bit argument value is to be read.
     */
    private void clearBits()
    {
        bits = 0;
        bit = 0x100;
    }

    /**
     * Construct a MethodArgumentReader streaming over the given DataInputStream.
     */
    public MethodArgumentReader(DataInputStream in)
    {
        this.in = in;
        clearBits();
    }

    /** Public API - convenience method - reads a short string from a DataInputStream. */
    public static final String readShortstr(DataInputStream in)
        throws IOException
    {
        byte [] b = new byte[in.readUnsignedByte()];
        in.readFully(b);
        return new String(b, "utf-8");
    }

    /** Public API - reads a short string argument. */
    public final String readShortstr()
        throws IOException
    {
        clearBits();
        return readShortstr(this.in);
    }

    /** Public API - convenience method - reads a long string argument from a DataInputStream. */
    public static final LongString readLongstr(final DataInputStream in)
        throws IOException
    {
        final long contentLength = unsignedExtend(in.readInt());
        if(contentLength < Integer.MAX_VALUE) {
            final byte [] buffer = new byte[(int)contentLength];
            in.readFully(buffer);

            return LongStringHelper.asLongString(buffer);
        }
            throw new UnsupportedOperationException
                ("Very long strings not currently supported");
    }

    /** Public API - reads a long string argument. */
    public final LongString readLongstr()
        throws IOException
    {
        clearBits();
        return readLongstr(this.in);
    }

    /** Public API - reads a short integer argument. */
    public final int readShort()
        throws IOException
    {
        clearBits();
        return in.readUnsignedShort();
    }

    /** Public API - reads an integer argument. */
    public final int readLong()
        throws IOException
    {
        clearBits();
        return in.readInt();
    }

    /** Public API - reads a long integer argument. */
    public final long readLonglong()
        throws IOException
    {
        clearBits();
        return in.readLong();
    }

    /** Public API - reads a bit/boolean argument. */
    public final boolean readBit()
        throws IOException
    {
        if (bit > 0x80) {
            bits = in.readUnsignedByte();
            bit = 0x01;
        }

        boolean result = (bits&bit) != 0;
        bit = bit << 1;
        return result;
    }

    /** Public API - reads a table argument. */
    public final Map<String, Object> readTable()
        throws IOException
    {
        clearBits();
        return readTable(this.in);
    }

    /**
     * Public API - reads a table argument from a given stream. Also
     * called by {@link ContentHeaderPropertyReader}.
     */
    public static final Map<String, Object> readTable(DataInputStream in)
        throws IOException
    {
        Map<String, Object> table = new HashMap<String, Object>();
        long tableLength = unsignedExtend(in.readInt());

        DataInputStream tableIn = new DataInputStream
            (new TruncatedInputStream(in, tableLength));
        Object value = null;
        while(tableIn.available() > 0) {
            String name = readShortstr(tableIn);
            switch(tableIn.readUnsignedByte()) {
            case 'S':
                value = readLongstr(tableIn);
                break;
            case 'I':
                value = tableIn.readInt();
                break;
            case 'D':
                int scale = tableIn.readUnsignedByte();
                byte [] unscaled = new byte[4];
                tableIn.readFully(unscaled);
                value = new BigDecimal(new BigInteger(unscaled), scale);
                break;
            case 'T':
                value = readTimestamp(tableIn);
                break;
            case 'F':
                value = readTable(tableIn);
                break;
            default:
                throw new MalformedFrameException
                    ("Unrecognised type in table");
            }

            if(!table.containsKey(name))
                table.put(name, value);
        }

        return table;
    }

    /** Public API - reads an octet argument. */
    public final int readOctet()
        throws IOException
    {
        clearBits();
        return in.readUnsignedByte();
    }

    /** Public API - convenience method - reads a timestamp argument from the DataInputStream. */
    public static final Date readTimestamp(DataInputStream in)
        throws IOException
    {
        return new Date(in.readLong()*1000);
    }

    /** Public API - reads an timestamp argument. */
    public final Date readTimestamp()
        throws IOException
    {
        clearBits();
        return readTimestamp(this.in);
    }
}
