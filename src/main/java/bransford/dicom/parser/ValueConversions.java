package bransford.dicom.parser;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class ValueConversions
{
    public static String getHexString(int toHex)
    {
        return String.format("%04x", toHex & 0xFFFF);
    }

    public static String getHexString(byte[] array)
    {
        int toHex = ValueConversions.shortValue(array);
        return getHexString(toHex);
    }

    public static int stringToInt(DicomTag tag)
    {
        if (tag == null || tag.isNumeric)
        {
            return 0;
        }

        if (tag.valueString == null || tag.valueString.isEmpty())
        {
            return 0;
        }
        int value = 0;

        try
        {
            value = Integer.parseInt(tag.valueString);
        }
        catch (NumberFormatException nfe)
        {
            System.err.println("Error parsing string to an integer: " + nfe.getMessage());
        }

        return value;
    }

    public static short shortValue(byte[] array)
    {
        return shortValue(array, DCMBuff.system_byte_order);
    }

    public static short shortValue(byte[] array, ByteOrder byteOrder)
    {
        ByteBuffer buff = ByteBuffer.wrap(array).order(byteOrder);
        return buff.getShort();
    }

    public static int intValue(byte[] array)
    {
       return intValue(array, DCMBuff.system_byte_order);
    }

    public static int intValue(byte[] array, ByteOrder byteOrder)
    {
        ByteBuffer buff = ByteBuffer.wrap(array).order(byteOrder);
        return buff.getInt(0);
    }

    public static float floatValue(byte[] chunk, int sizeof_type)
    {
        ByteBuffer buff = ByteBuffer.wrap(chunk).order(DCMBuff.system_byte_order);
        switch (sizeof_type)
        {
            case (2) ->
            {
                return Float.intBitsToFloat(buff.getShort());
            }
            case (4) ->
            {
                return Float.intBitsToFloat(buff.getInt());
            }
            default ->
            {
                return -1.0f;
            }
        }
    }

    public static double doubleValue(byte[] chunk)
    {
        ByteBuffer buff = ByteBuffer.wrap(chunk).order(DCMBuff.system_byte_order);
        return Double.longBitsToDouble(buff.getLong());
    }

    // unsigned

    public static int unsignedShortValue(byte[] array)
    {
        short signedInt = ValueConversions.shortValue(array);
        return Short.toUnsignedInt(signedInt);
    }

    public static long unsignedIntValue(byte[] array)
    {
        int signedInt = ValueConversions.intValue(array);
        return Integer.toUnsignedLong(signedInt);
    }

    public static BigInteger[] bigIntegerArray(byte[] raw_value, int sizeof_type, boolean unsigned)
    {
        int raw_value_length = raw_value.length;

        int num_to_pad = raw_value_length % sizeof_type;

        if (num_to_pad != 0)
        {
            byte[] ar = new byte[num_to_pad];
            Arrays.fill(ar, (byte) 0);
            byte[] c = new byte[raw_value_length + num_to_pad];
            System.arraycopy(raw_value, 0, c, 0, raw_value_length);
            System.arraycopy(ar, 0, c, raw_value.length, ar.length);
            raw_value = c;
        }

        int cardinality = raw_value_length / sizeof_type;
        BigInteger[] result = new BigInteger[cardinality];
        byte[] chunk;

        for (int i = 0, c = 0; i < raw_value_length; i+=sizeof_type, c+=1)
        {
            chunk = new byte[sizeof_type];
            System.arraycopy(raw_value, i, chunk, 0, sizeof_type);
            switch (sizeof_type)
            {
                case 2 ->
                {
                    String val;
                    if (unsigned)
                    {
                        val = String.valueOf(unsignedShortValue(chunk));
                    }
                    else
                    {
                        val = String.valueOf(shortValue(chunk));
                    }
                    BigInteger bi = new BigInteger(val);
                    result[c] = bi;
                }
                case 4 ->
                {
                    String val;
                    if (unsigned)
                    {
                        val = String.valueOf(unsignedIntValue(chunk));
                    }
                    else
                    {
                        val = String.valueOf(intValue(chunk));
                    }
                    BigInteger bi = new BigInteger(val);
                    result[c] = bi;
                }
                default ->
                {
                    System.err.println("invalid integer type, size_of = " + sizeof_type);
                }
            }
        }


        return result;
    }

    public static BigDecimal[] bigDecimalArray(byte[] raw_value, int sizeof_type)
    {
        int raw_value_length = raw_value.length;
        int num_to_pad = raw_value_length % sizeof_type;

        if (num_to_pad != 0)
        {
            byte[] ar = new byte[num_to_pad];
            Arrays.fill(ar, (byte) 0);

            byte[] c = new byte[raw_value_length + num_to_pad];
            System.arraycopy(raw_value, 0, c, 0, raw_value_length);
            System.arraycopy(ar, 0, c, raw_value.length, ar.length);
            raw_value = c;
        }

        int cardinality = raw_value_length / sizeof_type;
        BigDecimal[] result = new BigDecimal[cardinality];
        byte[] chunk;

        for (int i = 0, c = 0; i < raw_value_length; i+=sizeof_type, c+=1)
        {
            chunk = new byte[sizeof_type];
            System.arraycopy(raw_value, i, chunk, 0, sizeof_type);
            switch (sizeof_type)
            {
                case 2, 4 ->
                {
                    result[c] = BigDecimal.valueOf(floatValue(chunk, sizeof_type));
                }
                case 8 ->
                {
                     result[c] = BigDecimal.valueOf(doubleValue(chunk));
                }
                default ->
                {
                    System.err.println("invalid decimal type, size_of = " + sizeof_type);
                }
            }
        }

        return result;
    }

    public static byte[][] chunkByteArray(int stride, byte[] raw_value, int numChunks)
    {
        int raw_value_length = raw_value.length;
        int num_to_pad = raw_value_length % 4;
        if (num_to_pad != 0)
        {
            byte[] ar = new byte[num_to_pad];
            Arrays.fill(ar, (byte) 0);
            byte[] c = new byte[raw_value_length + num_to_pad];
            System.arraycopy(raw_value, 0, c, 0, raw_value_length);
            System.arraycopy(ar, 0, c, raw_value.length, ar.length);
            raw_value = c;
        }

        byte[][] frames = new byte[numChunks][];

        if (numChunks == 1)
        {
            frames[0] = raw_value;
        }
        else
        {
            byte[] chunk;

            for (int row_bytes = 0, row = 0; row_bytes < raw_value.length; row_bytes += stride, row += 1)
            {
                chunk = new byte[stride];
                System.arraycopy(raw_value, row_bytes, chunk, 0, stride);
                frames[row] = chunk;
            }
        }

        return frames;
    }

    public static long getNumericValue(DicomTag tag)
    {
        if (tag == null || !tag.isNumeric)
        {
            return 0;
        }

        byte[] rawValue = tag.rawValue;

        if (rawValue == null || rawValue.length == 0)
        {
            return 0;
        }

        long value = 0;
        switch (tag.VR)
        {
            case "US" ->
            {
                if (rawValue.length <= 2)
                {
                    value = ValueConversions.unsignedShortValue(rawValue);
                }
            }
            case "SS" ->
            {
                if (rawValue.length <= 2)
                {
                    value = ValueConversions.shortValue(rawValue);
                }
            }
            case "UL" ->
            {
                if (rawValue.length <= 4)
                {
                    value = ValueConversions.unsignedIntValue(rawValue);
                }
            }
            case "SL" ->
            {
                if (rawValue.length <= 4)
                {
                    value = ValueConversions.intValue(rawValue);
                }
            }
            default ->
            {
                return 0;
            }
        }

        return value;
    }

    public static Double getDecimalValue(DicomTag tag)
    {
        if (tag == null || !tag.isNumeric)
        {
            return 0.0;
        }

        byte[] rawValue = tag.rawValue;

        if (rawValue == null || rawValue.length == 0)
        {
            return 0.0;
        }

        double value = 0.0;
        switch (tag.VR)
        {
            case "FL" ->
            {
                value = ValueConversions.floatValue(rawValue, 4);
            }
            case "FD" ->
            {
                value = ValueConversions.doubleValue(rawValue);
            }
            default ->
            {
                return 0.0;
            }
        }

        return value;
    }
}
