package edu.mayo.dicom.parser.test;

import edu.mayo.dicom.parser.ValueConversions;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteOrder;

import java.lang.Integer;

public class NumericConversionTest
{
    @Test
    public void testByteToShort()
    {
        byte[] shortAsBytes = new byte[2];
        // shortAsBytes[0] = (byte) 0x7F;
        // shortAsBytes[1] = (byte) 0xD1;
        shortAsBytes[1] = 0x38;
        shortAsBytes[0] = 0x30;

        if (ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN)
        {
            System.out.println("big endian");
        }
        // be 12344

        short be = ValueConversions.shortValue(shortAsBytes, ByteOrder.BIG_ENDIAN);
        short le = ValueConversions.shortValue(shortAsBytes, ByteOrder.LITTLE_ENDIAN);

        String asHex = ValueConversions.getHexString(shortAsBytes);

        short asSignedShort = ValueConversions.shortValue(shortAsBytes);

        shortAsBytes = new byte[4];
        shortAsBytes[0] = (byte) 0xBA;
        shortAsBytes[1] = (byte) 0;
        shortAsBytes[2] = (byte) 0;
        shortAsBytes[3] = (byte) 0;

        short asShort = ValueConversions.shortValue(shortAsBytes);
        System.out.println("byte array as short = " + asShort);
    }

    @Test
    public void testByteToInt()
    {
        byte[] intAsBytes = new byte[4];
        intAsBytes[0] = 0x00;
        intAsBytes[1] = 0x00;
        intAsBytes[2] = (byte) 0xB8;
        intAsBytes[3] = (byte) 0xF8;

        byte[] intAsBytes1 = new byte[4];
        intAsBytes1[0] = (byte) 0x01;
        intAsBytes1[1] = 0x00;
        intAsBytes1[2] = 0x00;
        intAsBytes1[3] = (byte) 0x80;


        if (ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN)
        {
            System.out.println("big endian");
        }
        // be 12344

        int le_base = ValueConversions.intValue(intAsBytes);
        long unsignedLong = Integer.toUnsignedLong(le_base);
        int be = ValueConversions.intValue(intAsBytes, ByteOrder.BIG_ENDIAN);
        int le = ValueConversions.intValue(intAsBytes, ByteOrder.LITTLE_ENDIAN);

        int le_base1 = ValueConversions.intValue(intAsBytes1);
        long unsignedLong1 = Integer.toUnsignedLong(le_base1);
        int unsignedInt1 = (int) Integer.toUnsignedLong(le_base1);
        int be1 = ValueConversions.intValue(intAsBytes1, ByteOrder.BIG_ENDIAN);
        int le1 = ValueConversions.intValue(intAsBytes1, ByteOrder.LITTLE_ENDIAN);

        System.out.println("be = " + be + " le = " + le);

    }

    @Test
    public void testByteToDouble()
    {
        // 42AA4000
        // 01 D4 1E 40 D0 F0 4C C0
        // le: as a double -57.881355300000003
        // be: as a double 7.5102248852838185e-300


        // le int = -4590029142977752063
        // be int = 131763552832736448
        // double swapped = bb.getDouble();
        // [1, -44, 30, 64, -48, -16, 76, -64]

        byte[] doubleAsBytes = new byte[8];
        doubleAsBytes[0] = (byte) 0x01;
        doubleAsBytes[1] = (byte) 0xD4;
        doubleAsBytes[2] = (byte) 0x1E;
        doubleAsBytes[3] = (byte) 0x40;
        doubleAsBytes[4] = (byte) 0xD0;
        doubleAsBytes[5] = (byte) 0xF0;
        doubleAsBytes[6] = (byte) 0x4C;
        doubleAsBytes[7] = (byte) 0xC0;


        double floatValue = ValueConversions.doubleValue(doubleAsBytes);
        Assert.assertEquals(floatValue, 85.125, 0);
    }

}
