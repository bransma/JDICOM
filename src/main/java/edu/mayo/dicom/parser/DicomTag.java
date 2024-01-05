package edu.mayo.dicom.parser;

import java.io.File;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.regex.Pattern;

public class DicomTag
{
    public String group = null;
    public String element = null;
    public String description = "Unknown Tag & Data";

    // the value representation, number, string, free text,
    // date, time, etc
    public String VR = null;

    // the raw byte array read from the DICOM file
    public byte[] rawValue = null;
    // the length the raw value
    public int valueLength = 0;

    public BigInteger[] valueNumeric = null;

    public BigDecimal[] valueDecimal = null;

    public String valueString = null;

    public byte[] valueOB = null;

    // the number of values that are encoded in the raw_value
    public int multiplicity = 0;

    public String groupElementKey = null;
    public boolean isExplicitVrReserved = false;
    public boolean isExplicit = true;
    public boolean isBigEndian = false;
    public boolean isSequence = false;
    public boolean isSequenceDelimiter = false;
    public boolean isItemDelimiter = false;
    public boolean isNumeric = false;
    public boolean isItem = false;
    public boolean isPixelData = false;
    public boolean isUnknown = true;
    public boolean undefinedLength = false;

    public boolean isCompressed = false;

    public ArrayList<DicomTag> subsequence = new ArrayList<>();

    public void initializeFromDict(String[] dicom_dict_entry)
    {
        groupElementKey = dicom_dict_entry[0];
        setGroupElementFromKey(groupElementKey);
        VR = dicom_dict_entry[1];
        description = dicom_dict_entry[2];
        String mult = dicom_dict_entry[3];
        if (mult.contains("-") || mult.contains("n"))
        {
            multiplicity = -1;
        }
        else
        {
            multiplicity = Integer.parseInt(mult);
        }
        isUnknown = false;
    }

    public void initialize()
    {
        calculateIsNumeric();
        calculateIsExplicitVRReserved();
        calculateIsSequence();
        calculateIsSequenceDelimiter();
        calculateIsItemDelimiter();
        calculateIsItem();
        calculateIsPixelData();
    }

    public static DicomTag copy(DicomTag to_copy)
    {
        DicomTag copy = new DicomTag();
        copy.group = to_copy.group;
        copy.element = to_copy.element;
        copy.description = to_copy.description;

        // the value representation, number, string, free text,
        // date, time, etc
        copy.VR = to_copy.VR;

        // the number of values that are encoded in the raw_value
        copy.multiplicity = to_copy.multiplicity;

        copy.groupElementKey = to_copy.groupElementKey;
        copy.isExplicitVrReserved = to_copy.isExplicitVrReserved;
        copy.isExplicit = to_copy.isExplicit;
        copy.isBigEndian = to_copy.isBigEndian;
        copy.isSequence = to_copy.isSequence;
        copy.isSequenceDelimiter = to_copy.isSequenceDelimiter;
        copy.isItemDelimiter = to_copy.isItemDelimiter;
        copy.isNumeric = to_copy.isNumeric;
        copy.isItem = to_copy.isItem;
        copy.isPixelData = to_copy.isPixelData;
        copy.isUnknown = to_copy.isUnknown;
        copy.undefinedLength = to_copy.undefinedLength;
        return copy;
    }

    // setters
    public void addItem(DicomTag item)
    {
        subsequence.add(item);
    }

    public void createGroupElementKey(String grp, String ele)
    {
        groupElementKey = "(" + grp + ',' + ele + ")";
    }

    public void setGroupElementFromKey(String grp_ele_key)
    {
        String converted = grp_ele_key.replace("(", "");
        converted = converted.replace(")", "");
        String[] converted_a = converted.split(",");
        group = converted_a[0];
        element = converted_a[1];
    }

    public void setWithGroupElement(String grp, String ele)
    {
        group = grp;
        element = ele;
        createGroupElementKey(grp, ele);
    }

    public void setVR(String vr)
    {
        VR = vr;
        if (isUnknown)
        {
            initialize();
        }

        // there are "pseudo-VR" values, replaced when reading the tag (such as LUTs)
        calculateIsNumeric();
    }

    public void setValueLength(int value_length)
    {
        this.valueLength = value_length;
        if (String.format("%08x", value_length).equalsIgnoreCase("ffffffff"))
        {
            // keep reading until the sequence delimiter is found
            undefinedLength = true;
        }
    }

    public void setRawValue(byte[] the_value)
    {
        rawValue = the_value;
    }

    // getters

    public String get_group_element_key()
    {
        return groupElementKey;
    }

    public String getVR()
    {
        return VR;
    }

    public int getValueLength()
    {
        return valueLength;
    }

    public boolean hasSubsequence()
    {
        return subsequence.size() != 0;
    }

    public boolean isValueCalculated()
    {
        if (valueString == null && valueNumeric == null && valueDecimal == null && valueOB == null)
        {
            calculateValue();
        }

        if (valueString != null)
        {
            return true;
        }

        if (valueNumeric != null)
        {
            return true;
        }

        if (valueOB != null)
        {
            return true;
        }

        return valueDecimal != null;
    }

    public boolean isExplicitVRReserved()
    {
        calculateIsExplicitVRReserved();
        return isExplicitVrReserved;
    }

    public boolean isSequence()
    {
        return isSequence;
    }

    public boolean isSequenceDelimiter()
    {
        return isSequenceDelimiter;
    }

    public boolean isItemDelimiter()
    {
        return isItemDelimiter;
    }

    public boolean isItem()
    {
        return isItem;
    }

    public boolean isNumeric()
    {
        return isNumeric;
    }

    public boolean isUndefinedLength()
    {
        return undefinedLength;
    }

    public boolean isByteArray()
    {
        return (VR.equals("OB") || VR.equals("UN"));
    }

    // functions that can be used as initializer, or to (re)calculate
    // once enough tag data is available

    public boolean isTransferSyntaxTag()
    {
        return group.equals("0002") && element.equals("0010");
    }

    // obtained once and only once, based on the transfer syntax
    public boolean calculateIsExplicitAndBigEndian()
    {
        if (isNumeric)
        {
            isExplicit = false;
        }
        else
        {
            if (valueString == null && valueNumeric == null && valueDecimal == null)
            {
                calculateValue();
            }

            if (valueString == null)
            {
                isExplicit = false;
            }
            else
            {
                if (valueString.equals("1.2.840.10008.1.2"))
                {
                    isExplicit = false;
                }

                if (valueString.equals("1.2.840.10008.1.2.2"))
                {
                    isBigEndian = true;
                }

                if (isTransferSyntaxTag())
                {
                    isCompressed = valueString.startsWith("1.2.840.10008.1.2.4.")
                            || valueString.startsWith("1.2.840.10008.1.2.5")
                            || valueString.startsWith("1.2.840.10008.1.2.6.1");
                }
            }
        }

        return isExplicit;
    }

    public void calculateValue()
    {
        if (rawValue == null)
        {
            return;
        }

        int raw_value_length = rawValue.length;

        if (isSequence() || isSequenceDelimiter() || isItem() || isItemDelimiter())
        {
            multiplicity = 1;
            valueOB = rawValue;
            return;
        }
        // the following 6 VR's are the only true numeric values
        // however, the raw byte array may encode more than a
        // single value
        if (isNumeric())
        {
            switch (VR)
            {
                case "US", "SS", "OW" ->
                {
                    multiplicity = (raw_value_length / 2);
                    if (isPixelData && VR.equals("OW"))
                    {
                        valueOB = rawValue;
                    }
                    else
                    {
                        valueAsNumericArray(2);
                    }
                }
                case "FL", "SL", "UL", "OF" ->
                {
                    multiplicity = raw_value_length / 4;
                    valueAsNumericArray(4);
                }
                case "FD", "OD" ->
                {
                    multiplicity = raw_value_length / 8;
                    valueAsNumericArray(8);
                }
                case "OB", "UN" ->
                {
                    multiplicity = 1;
                    valueOB = rawValue;
                }
            }
        }
        else
        {
            // the raw array must be a string
            if (VR.equals("AT"))
            {
                String first = ValueConversions.getHexString(DCMBuff.getChunk(0, 2));
                String second = ValueConversions.getHexString(DCMBuff.getChunk(2, 4));
                valueString = "(" + first + "," + second + ")";
            }
            else
            {
                valueString = DCMBuff.dataChunkToString(rawValue).trim();
            }

            if (multiplicity == 0)
            {
                // custom tag that is not in the dicom.dic
                multiplicity = 1;
            }
            else if (multiplicity == -1)
            {
                // compute it based on the number of "/" delimiters
                // in the data
                if (valueString.contains("\\"))
                {
                    String[] multiplicity_a = valueString.split(Pattern.quote(File.separator));
                    multiplicity = multiplicity_a.length;
                }
                else
                {
                    multiplicity = 1;
                }
            }
            else
            {
                multiplicity = 1;
            }
        }
    }

    public void valueAsNumericArray(int sizeof_type)
    {
        if (rawValue == null)
        {
            return;
        }
        if (VR.equals("OF") || VR.equals("OD") || VR.equals("FL") || VR.equals("FD"))
        {
            valueDecimal = ValueConversions.bigDecimalArray(rawValue, sizeof_type);
        }
        else
        {
            valueNumeric = ValueConversions.bigIntegerArray(rawValue, sizeof_type, VR.startsWith("U"));
        }
    }

    public void calculateIsExplicitVRReserved()
    {
        if (VR != null)
        {
            isExplicitVrReserved = VR.equals("OB") || VR.equals("OW")
                    || VR.equals("OF") || VR.equals("SQ")
                    || VR.equals("UN") || VR.equals("UT");
        }
        else
        {
            isExplicitVrReserved = false;
        }
    }

    public void calculateIsSequence()
    {
        isSequence = VR.equals("SQ");
        multiplicity = 1;
    }

    public void calculateIsSequenceDelimiter()
    {
        isSequenceDelimiter = group.equals("FFFE") && element.equals("E0DD");
        multiplicity = 0;
    }

    public void calculateIsItemDelimiter()
    {
        isItemDelimiter = group.equals("FFFE") && element.equals("E00D");
        multiplicity = 0;
    }

    public void calculateIsItem()
    {
        isItem = group.equals("FFFE") && element.equals("E000");
    }

    public void calculateIsNumeric()
    {
        isNumeric = VR.equals("FL") || VR.equals("FD") || VR.equals("SL")
                || VR.equals("SS") || VR.equals("UL")
                || VR.equals("US") || VR.equals("OB") || VR.equals("OD")
                || VR.equals("OF") || VR.equals("OW");
    }

    public void calculateIsPixelData()
    {
        if (group.equals("7FE0"))
        {
            switch (element)
            {
                case "0010" ->
                {
                    VR = "OB";
                    isPixelData = true;
                }
                case "0009" ->
                {
                    VR = "OD";
                    isPixelData = true;
                }
                case "0008" ->
                {
                    VR = "OF";
                    isPixelData = true;
                }
            }
        }
        else
        {
            isPixelData = false;
        }
    }

    public String toString()
    {
        StringBuilder a_value_sb = new StringBuilder();

        boolean isValueCalculated = isValueCalculated();
        if (isSequence || isItemDelimiter
            || isSequenceDelimiter)
        {
           if (isUndefinedLength())
           {
               a_value_sb.append("na");
           }
        }
        else if (!isValueCalculated)
        {
            a_value_sb.append("(no value available)");
        }
        else
        {
            if (isNumeric())
            {
                if (rawValue == null || rawValue.length == 0)
                {
                    a_value_sb.append("(no value available)");
                }
                else if (isByteArray())
                {
                    a_value_sb.append("[");
                    String hexString;
                    for (int i = 0, j = 0; i < rawValue.length; i++, j++)
                    {
                        if (j < 10)
                        {
                            hexString = String.format("0x%02x", rawValue[i] & 0xFF);
                            a_value_sb.append(hexString);
                        }

                        if (j >= 10)
                        {
                            a_value_sb.append("...");
                            break;
                        }
                        else
                        {
                            if (i < rawValue.length - 1)
                            {
                                a_value_sb.append("\\");
                            }
                        }
                    }
                    a_value_sb.append("]");
                }
                else if (valueDecimal != null)
                {
                    a_value_sb.append("[");
                    for (int i = 0, j = 0; i < valueDecimal.length; i++, j++)
                    {
                        if (j < 10)
                        {
                            if (VR.equals("FD") || VR.equals("OD"))
                            {
                                a_value_sb.append(valueDecimal[i].doubleValue());
                            }
                            else
                            {
                                a_value_sb.append(valueDecimal[i].floatValue());
                            }
                        }

                        if (j >= 10)
                        {
                            a_value_sb.append("...");
                            break;
                        }
                        else
                        {
                            if (i < valueDecimal.length - 1)
                            {
                                a_value_sb.append("\\");
                            }
                        }
                    }
                    a_value_sb.append("]");
                }
                else if (valueNumeric != null)
                {
                    a_value_sb.append("[");
                    for (int i = 0, j = 0; i < valueNumeric.length; i++, j++)
                    {
                        if (j < 10)
                        {
                            a_value_sb.append(valueNumeric[i].intValue());
                        }

                        if (j >= 10)
                        {
                            a_value_sb.append("...");
                            break;
                        }
                        else
                        {
                            if (i < valueNumeric.length - 1)
                            {
                                a_value_sb.append("\\");
                            }
                        }
                    }
                    a_value_sb.append("]");
                }
            }
            else
            {
                if (valueString == null)
                {
                    valueString = DCMBuff.dataChunkToString(rawValue).trim();
                }
                a_value_sb.append(valueString);
            }
        }

        String a_value = a_value_sb.toString();
        if (a_value.trim().isEmpty())
        {
            a_value = "(no value available)";
        }

        String a_length;
        if (isUndefinedLength())
        {
            a_length = "u/l";
        }
        else
        {
            if (VR.equals("OB"))
            {
                if (rawValue != null)
                {
                    a_length = String.valueOf(rawValue.length);
                }
                else
                {
                    a_length = "u/l";
                }
            }
            else
            {
                a_length = String.valueOf(getValueLength());
            }
        }

        if (isSequenceDelimiter)
        {
            VR = "na";
        }

        String format = "%-40s%s";

        StringBuilder sb1;
        sb1 = new StringBuilder();
        sb1.append(groupElementKey);
        sb1.append("\t");
        sb1.append(VR);
        sb1.append("\t");
        sb1.append(a_value);
        String output1 = sb1.toString();

        StringBuilder sb2;
        sb2 = new StringBuilder();
        sb2.append("\t\t# ");
        sb2.append(a_length);
        sb2.append(", ");
        sb2.append(multiplicity);
        sb2.append("\t");
        sb2.append(description);

        String output2 = sb2.toString();

        return String.format(format, output1, output2);
    }
}
