package bransford.dicom.parser;

import java.io.*;
import java.nio.ByteOrder;
import java.util.Arrays;

public class DCMBuff
{
    public static final String ascii = "UTF-8";
    public static final int _2bytes = 2;
    public static final int _4bytes = 4;
    public static final int _8bytes = 8;
    public static int buffer_location = 0;
    public static int buffer_range = 0;

    public static final ByteOrder system_byte_order = ByteOrder.nativeOrder();

    public static byte[] data = null;

    public static void readBinaryDicomFile(String dcm_file)
    {
        try
        {
            File file = new File(dcm_file);
            InputStream inputStream = new FileInputStream(file);

            long length = file.length();
            byte[] bytes = new byte[(int) length];

            int bytes_read = inputStream.read(bytes);

            if (bytes_read == length)
            {
                data = bytes;
            }
            else
            {
                System.err.println("could not read entire file");
            }

            inputStream.close();
        }
        catch (IOException ioe)
        {
            ioe.printStackTrace();
        }
    }

    public static boolean advance(int num_bytes)
    {
        if (num_bytes < 0)
        {
            // reset to place in buffer to the previous location
            buffer_location += num_bytes;
            buffer_range += num_bytes;
        }
        else
        {
            buffer_location = buffer_range;
            buffer_range = buffer_location + num_bytes;
        }

        return isValidBufferLocation();
    }

    public static void setLocationAndRange(int location)
    {
        buffer_location = location;
        buffer_range = location;
    }

    // return buffer chunk based on current location and range of data
    public static byte[] getChunk()
    {
        return getChunk(buffer_location, buffer_range);
    }

    // return buffer chunk based on any location (including current)
    public static byte[] getChunk(int fromIndex, int toIndex)
    {
        return Arrays.copyOfRange(data, fromIndex, toIndex);
    }


    // Method assumes location and range have been set
    public static String dataChunkToString()
    {
        byte[] chunk = getChunk(buffer_location, buffer_range);
        return dataChunkToString(chunk);
    }

    public static String dataChunkToString(byte[] chunk)
    {
        String ascii_string;
        try
        {
            ascii_string = new String(chunk, ascii);
        }
        catch (UnsupportedEncodingException encodingException)
        {
            System.err.println("Unsupported encoding: " + ascii);
            ascii_string = "";
        }

        return ascii_string;
    }

    // Method assumes location and range have been set
    public static int dataChunkToInt()
    {
        byte[] chunk = getChunk(buffer_location, buffer_range);
        return dataChunkToInt(chunk);
    }

    public static long dataChunkToUnsignedInt()
    {
        byte[] chunk = getChunk(buffer_location, buffer_range);
        return dataChunkToUnsignedInt(chunk);
    }

    public static int dataChunkToInt(byte[] chunk)
    {
        return ValueConversions.intValue(chunk);
    }

    public static long dataChunkToUnsignedInt(byte[] chunk)
    {
        return ValueConversions.unsignedIntValue(chunk);
    }

    public static short dataChunkToShort()
    {
        byte[] chunk = getChunk(buffer_location, buffer_range);
        return dataChunkToShort(chunk);
    }

    public static int dataChunkToUnsignedShort()
    {
        byte[] chunk = getChunk(buffer_location, buffer_range);
        return dataChunkToUnsignedShort(chunk);
    }

    public static short dataChunkToShort(byte[] chunk)
    {
        return ValueConversions.shortValue(chunk);
    }

    public static int dataChunkToUnsignedShort(byte[] chunk)
    {
        return ValueConversions.unsignedShortValue(chunk);
    }

    public static boolean isValidBufferLocation()
    {
        return buffer_range < data.length;
    }
}
