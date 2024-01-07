package bransford.dicom.parser;

import java.io.IOException;

public class DicomParser
{
    public String dcm_file = null;
    public boolean is_explicit = true;
    public boolean continueReading = true;
    public DICOMDictionary dicom_dictionary = null;

    public static String dict_file = "/Users/m056084/JDicomParser/src/main/java/edu/mayo/dicom/parser/dicom.dic";

    public DicomObject dcm = new DicomObject();

    public DicomParser(String dcm_file, String dict_file)
    {
        if (dcm_file == null || dict_file == null)
        {
            return;
        }

        this.dcm_file = dcm_file;
        DicomParser.dict_file = dict_file;
        dicom_dictionary = new DICOMDictionary(dict_file);
        DCMBuff.readBinaryDicomFile(dcm_file);
    }

    public boolean isValidDicomImage()
    {
        boolean isValid = false;
        DCMBuff.setLocationAndRange(128);
        DCMBuff.advance(DCMBuff._4bytes);
        String magic_number = DCMBuff.dataChunkToString();

        if ("DICM".equals(magic_number))
        {
            isValid = true;
        }
        else
        {
            System.err.println("Not a valid DICOM file, exiting");
        }

        return isValid;
    }

    public DicomObject parseDICOMImage() throws IOException
    {
        DicomTag tag;
        readGroup2Elements();

        while(continueReading)
        {
            tag = readTag();
            dcm.put(tag);
            continueReading = DCMBuff.isValidBufferLocation();
        }

        System.out.println("DICOM Image " + dcm_file + " successfully parsed");
        return dcm;
    }

    public DicomTag readTag()
    {
        DicomTag dicom_tag;
        int[] bytes_read = new int[1];
        dicom_tag = readBaseTag(bytes_read);
        bytes_read[0] = 0;

        if (dicom_tag.isSequence)
        {
            dicom_tag = readSequence(dicom_tag, bytes_read);
        }
        else if (dicom_tag.isPixelData)
        {
            dicom_tag = readTagData(dicom_tag, bytes_read, false, false);
            dicom_tag = readPixelData(dicom_tag, bytes_read);
        }
        else
        {
            dicom_tag = readTagData(dicom_tag, bytes_read, true, false);
        }

        return dicom_tag;
    }

    public DicomTag readBaseTag(int[] bytesRead)
    {
        bytesRead[0] = 0;
        DicomTag dicom_tag;

        DCMBuff.advance(DCMBuff._2bytes);
        bytesRead[0] += DCMBuff._2bytes;
        String group = ValueConversions.getHexString(DCMBuff.getChunk());
        group = group.toUpperCase();

        DCMBuff.advance(DCMBuff._2bytes);
        bytesRead[0] += DCMBuff._2bytes;
        String element = ValueConversions.getHexString(DCMBuff.getChunk());
        element = element.toUpperCase();
        dicom_tag = dicom_dictionary.get_tag(group, element);

        if (dicom_tag == null)
        {
            bytesRead[0] = 0;
            dicom_tag = new DicomTag();
            dicom_tag.setWithGroupElement(group, element);
            // read_tag_data will be called again, but need to know what kind of tag this is first
            dicom_tag = readTagData(dicom_tag, bytesRead, false, false);

            // tag was not in the dictionary, create one, populate data is desired, but
            // back up buffer 4 bytes, because didn't really read a tag
            DCMBuff.advance(-1 * bytesRead[0]);
        }

        return dicom_tag;
    }

    public DicomTag readTagData(DicomTag dicomTag, int[] bytesRead, boolean read_value, boolean is_delim)
    {
        bytesRead[0] = 0;
        int value_length;
        if (is_delim)
        {
            // for delimiters, need to read length field, as per spec
            DCMBuff.advance(DCMBuff._4bytes);
            bytesRead[0] += DCMBuff._4bytes;
            value_length = DCMBuff.dataChunkToInt();
            dicomTag.setValueLength(value_length);
            return dicomTag;
        }

        if (is_explicit)
        {
            DCMBuff.advance(DCMBuff._2bytes);
            bytesRead[0] += DCMBuff._2bytes;
            String vr = DCMBuff.dataChunkToString();
            dicomTag.setVR(vr);

            // read value length
            DCMBuff.advance(DCMBuff._2bytes);
            bytesRead[0] += DCMBuff._2bytes;

            if (dicomTag.isExplicitVRReserved())
            {
                // skip reserved 2 bytes, advance to read value length
                DCMBuff.advance(DCMBuff._4bytes);
                bytesRead[0] += DCMBuff._4bytes;
                value_length = DCMBuff.dataChunkToInt();
            }
            else
            {
                value_length = DCMBuff.dataChunkToShort();
            }
        }
        else
        {
            DCMBuff.advance(DCMBuff._4bytes);
            bytesRead[0] += DCMBuff._4bytes;
            value_length = DCMBuff.dataChunkToInt();
        }

        dicomTag.setValueLength(value_length);
        if (read_value && !dicomTag.isUndefinedLength())
        {
            DCMBuff.advance(value_length);
            bytesRead[0] += value_length;
            byte[] value_bytes = DCMBuff.getChunk();
            dicomTag.setRawValue(value_bytes);
        }

        return dicomTag;
    }

    /**
     # Parse the dicom tag and data, with the following encoding scheme
     #  Basic structure of explicit DICOM data, taken from DICOM standards 2011, Part3.5, section 7.1.2
     #
     #  1) DATA ELEMENT WITH EXPLICIT VR OF OB, OW, OF, SQ, UT OR UN
     #  ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
     #  | Tag	                                                | VR	                                       | Value Length	 | Value                                                       |
     #  |-------------------------------------------------------|----------------------------------------------|-----------------|-------------------------------------------------------------|
     #  | Group Number              | Element Number            | VR                          | Reserved       | 32-bit unsigned | Even number of bytes containing the Data Element Value(s)   |
     #  | (16-bit unsigned integer) | (16-bit unsigned integer) | (2 byte character string)   | (2 bytes)      | integer         | encoded according to the VR and negotiated Transfer Syntax. |
     #  |                           |                           | of "OB", "OW", “OF”,  “SQ”, | set to a value |                 | Delimited with Sequence Delimitation Item if of Undefined   |
     #  |                           |                           | “UT” or "UN"	              | of 0000H       |                 | Length.                                                     |
     #  |---------------------------|---------------------------|-----------------------------|----------------|-----------------|-------------------------------------------------------------|
     #  | 2 bytes	                 | 2 bytes	                | 2 bytes                     | 2 bytes	       | 4 bytes	     | 'Value Length' bytes if of Explicit Length                  |
     #  ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
     #
     #  DATA ELEMENT WITH EXPLICIT VR OTHER THAN AS SHOWN IN TABLE 1)
     #  -----------------------------------------------------------------------------------------------------------------------------------------------------------------------
     #  | Tag	                                               | VR 	                     | Value Length	   | Value                                                       |
     #  |-------------------------------------------------------|-----------------------------|-----------------|-------------------------------------------------------------|
     #  | Group Number              | Element Number            | VR                          | 32-bit unsigned | Even number of bytes containing the Data Element Value(s)   |
     #  | (16-bit unsigned integer) | (16-bit unsigned integer) | (2 byte character string)   | integer         | encoded according to the VR and negotiated Transfer Syntax. |
     #  |---------------------------|---------------------------|-----------------------------|-----------------|-------------------------------------------------------------|
     #  | 2 bytes	               | 2 bytes	               | 2 bytes                     | 2 bytes	       | 'Value Length' bytes                                        |
     #  -----------------------------------------------------------------------------------------------------------------------------------------------------------------------
     #
     #  OR
     #
     #  Basic structure of implicit DICOM data, taken from DICOM standards 2011, Part3.5, section 7.1.2. The VR for implicit encoded data is taken from known VR for a given
     #  -----------------------------------------------------------------------------------------------------------------------------------------
     #  | Tag	                                               | Value Length	   | Value                                                     |
     #  |-------------------------------------------------------|-----------------|-------------------------------------------------------------|
     #  | Group Number              | Element Number            | 32-bit unsigned | Even number of bytes containing the Data Element Value(s)   |
     #  | (16-bit unsigned integer) | (16-bit unsigned integer) | integer         | encoded according to the VR specified in PS 3.6 and         |
     #  |                           |                           |                 | negotiated Transfer Syntax. Delimited with Sequence         |
     #  |                           |                           |                 | Delimitation Item if of Undefined Length                    |
     #  |---------------------------|---------------------------|-----------------|-------------------------------------------------------------|
     #  | 2 bytes	               | 2 bytes	               | 4 bytes	     | 'Value Length' bytes if of Explicit Length                  |
     #  -----------------------------------------------------------------------------------------------------------------------------------------
     #
     # A note on parsing the File Meta Information Version, tag (0002,0001)
     #
     # This is a two byte field where each bit identifies a version of this File Meta Information header.
     # In version 1 the first byte value is 00H and the second value byte value is 01H.
     #
     # Implementations reading Files with Meta Information where this attribute has bit 0 (lsb) of the second
     # byte set to 1 may interpret the File Meta Information as specified in this version of PS3.10.
     # All other bits shall not be checked.
     #
     # Note
     # A bit field where each bit identifies a version, allows explicit indication of the support of multiple
     # previous versions. Future versions of the File Meta Information that can be read by version 1 readers
     # will have bit 0 of the second byte set to 1
     **/
    public void readGroup2Elements()
    {
        DicomTag dicom_tag;

        while (true)
        {
            DCMBuff.advance(DCMBuff._2bytes);
            String group = ValueConversions.getHexString(DCMBuff.getChunk());
            group = group.toUpperCase();
            if (group.equals("0002"))
            {
                DCMBuff.advance(DCMBuff._2bytes);
                String element = ValueConversions.getHexString(DCMBuff.getChunk());
                element = element.toUpperCase();
                dicom_tag = dicom_dictionary.get_tag(group, element);

                DCMBuff.advance(DCMBuff._2bytes);
                if (dicom_tag == null)
                {
                    dicom_tag = new DicomTag();
                    dicom_tag.setWithGroupElement(group, element);
                    String vr = DCMBuff.dataChunkToString();
                    dicom_tag.setVR(vr);
                }

                // read value length
                int value_length;
                DCMBuff.advance(DCMBuff._2bytes);
                if (dicom_tag.isExplicitVRReserved())
                {
                    // skip reserved 2 bytes, advance to read value length
                    DCMBuff.advance(DCMBuff._4bytes);
                    value_length = DCMBuff.dataChunkToInt();
                }
                else
                {
                    value_length = DCMBuff.dataChunkToShort();
                }


                dicom_tag.valueLength = value_length;

                // read the data proper
                DCMBuff.advance(value_length);
                byte[] value_bytes = DCMBuff.getChunk();
                dicom_tag.setRawValue(value_bytes);

                // if this tag represents the transfer syntax
                if (dicom_tag.isTransferSyntaxTag())
                {
                    is_explicit = dicom_tag.calculateIsExplicitAndBigEndian();
                }
                dcm.put(dicom_tag);
            }
            else
            {
                // not in the group tags, back up 2 bytes, and break out of
                // read group 2 tags
                DCMBuff.advance(-1 * DCMBuff._2bytes);
                break;
            }
        }
    }

    /**
     #  Common possibilities:
     #
     #  1) Explicit VR = SQ of defined length, and tags of explicit length
     #  2) Explicit VR = SQ of undefined length, with tags of explicit length
     #  3) Explicit VR = SQ of undefined length, with tags of undefined length
     #  4) Explicit VR = SQ of undefined length, with tags of a mixture of explicit and undefined length
     #  5) Implicit VR = SQ of defined length, with tags of explicit length
     #
     #  Taken from Dicom Standards 2011 part 3.5, section 7.5
     #
     #  1) EXAMPLE OF A DATA ELEMENT WITH EXPLICIT VR DEFINED AS A SEQUENCE OF ITEMS
     #  (VR = SQ) OF DEFINED LENGTH, CONTAINING TWO ITEMS OF EXPLICIT LENGTH
     #
     #  ---------------------------------------------------------------------------------------------------------------------------------------------
     #  |Data Element Tag	   |     VR            | Data Element  |	Data Element Value                                                          |
     #  |                      |                   |   Length      |                                                                                |
     #  |----------------------|-------------------|---------------|--------------------------------------------------------------------------------|
     #  |(gggg, eeee) with	   | SQ    | 0000H     | 32-bit        | First Item                           | Second Item                             |
     #  | VR of SQ             |       | Reserved  | unsigned int  |                                      |                                         |
     #  |----------------------|-------|-----------|---------------|--------------------------------------|-----------------------------------------|
     #  |                      |       |           |               | Item Tag | Item Length | Item Value  | Item Tag    | Item Length | Item Value  |
     #  |	                   |       |           |               | (FFFE,   | 0000        |             | (FFFE,      | 0000        |             |
     #  |	                   |       |           |               |  E000)   | 04F8H       | Data Set    |  E000)      | 04F8H       | Data Set    |
     #  |----------------------|-------|-----------|---------------|----------|-------------|-------------|-------------|-------------|-------------|
     #  |     4                |  2    |  2        |  4            |  4       |  4          |  04F8H      |   4         |   4         |  04F8H      |
     #  |    bytes             | bytes | bytes     | bytes         | bytes    | bytes       |  bytes      |  bytes      |  bytes      |  bytes      |
     #  ---------------------------------------------------------------------------------------------------------------------------------------------
     #
     #  2) EXAMPLE OF A DATA ELEMENT WITH EXPLICIT VR DEFINED AS A SEQUENCE OF ITEMS
     #  (VR = SQ) OF UNDEFINED LENGTH, CONTAINING TWO ITEMS OF EXPLICIT LENGTH
     #
     #  -------------------------------------------------------------------------------------------------------------------------------------------------------------------------
     #  |Data Element Tag	   |     VR            | Data Element |	Data Element Value                                                                                          |
     #  |                      |                   |   Length     |                                                                                                             |
     #  |----------------------|-------------------|--------------|-------------------------------------------------------------------------------------------------------------|
     #  |(gggg, eeee) with	   | SQ    | 0000H     | 0xFFFFFFFF   | First Item                           | Second Item                             | SQ Delimitation            |
     #  | VR of SQ             |       | Reserved  | undef'd len  |                                      |                                         | Item                       |
     #  |----------------------|-------|-----------|--------------|--------------------------------------|-----------------------------------------|----------------------------|
     #  |                      |       |           |              | Item Tag | Item Length | Item Value  | Item Tag    | Item Length | Item Value  | SQ delim tag | Item Length |
     #  |	                   |       |           |              | (FFFE,   | 0000        |             | (FFFE,      | 0000        |             | (FFFE,       | 0000        |
     #  |	                   |       |           |              |  E000)   | 04F8H       | Data Set    |  E000)      | 04F8H       | Data Set    |  E0DD)       | 0000H       |
     #  |----------------------|-------|-----------|--------------|----------|-------------|-------------|-------------|-------------|-------------|--------------|-------------|
     #  |     4                |  2    |  2        |  4           |  4       |  4          |  04F8H      |   4         |   4         |  04F8H      |   4          |   4         |
     #  |    bytes             | bytes | bytes     | bytes        | bytes    | bytes       |  bytes      |  bytes      |  bytes      |  bytes      |  bytes       |  bytes      |
     #  -------------------------------------------------------------------------------------------------------------------------------------------------------------------------
     #
     #  3) EXAMPLE OF A DATA ELEMENT WITH EXPLICIT VR DEFINED AS A SEQUENCE OF ITEMS
     #  (VR = SQ) OF UNDEFINED LENGTH, CONTAINING TWO ITEMS OF UNDEFINED LENGTH
     #
     #  ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
     #  |Data Element Tag	   |     VR            | Data Element  |	Data Element Value                                                                                                                                   |
     #  |                      |                   |   Length      |                                                                                                                                                         |
     #  |----------------------|-------------------|---------------|---------------------------------------------------------------------------------------------------------------------------------------------------------|
     #  |(gggg, eeee) with	   | SQ    | 0000H     | 0xFFFFFFFF    | First Item                                                  | Second Item                                                  | SQ Delimitation            |
     #  | VR of SQ             |       | Reserved  | undefined len |                                                             |                                                              | Item                       |
     #  |----------------------|-------|-----------|---------------|-------------------------------------------------------------|--------------------------------------------------------------|----------------------------|
     #  |                      |       |           |               | Item Tag | Item Length | Item Value  | Item Delim. | Length | Item Tag | Item Length  | Item Value  | Item Delim. | Length | SQ delim tag | Item Length |
     #  |	                   |       |           |               | (FFFE,   | 0xFFFFFFFF  |             | Tag         | 0000   | (FFFE,   | 0xFFFFFFFF   |             | Tag         | 0000   | (FFFE,       | 0000        |
     #  |	                   |       |           |               |  E000)   | undef'd len | Data Set    | (FFFE,E00D) | 0000H  |  E000)   | undef'd len  | Data Set    | (FFFE,E00D) | 0000H  | E0DD)        | 0000H       |
     #  |----------------------|-------|-----------|---------------|----------|-------------|-------------|-------------|--------|----------|--------------|-------------|-------------|--------|--------------|-------------|
     #  |     4                |  2    |  2        |  4            |  4       |  4          | undef'd len |   4         |   4    |   4      |   4          | undef'd len |   4         |   4    |    4         |   4         |
     #  |    bytes             | bytes | bytes     | bytes         | bytes    | bytes       |             |  bytes      |  bytes |  bytes   |  bytes       |             |  bytes      |  bytes |   bytes      |  bytes      |
     #  ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
     #
     #  4) EXAMPLE OF A DATA ELEMENT WITH EXPLICIT VR DEFINED AS A SEQUENCE OF ITEMS
     #  (VR = SQ) OF UNDEFINED LENGTH, CONTAINING ITEMS WITH A MIXTURE EXPLICIT AND UNDEFINED LENGTH
     #
     #  -----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
     #  |Data Element Tag	   |     VR            | Data Element  |	Data Element Value                                                                                                            |
     #  |                      |                   |   Length      |                                                                                                                                  |
     #  |----------------------|-------------------|---------------|----------------------------------------------------------------------------------------------------------------------------------|
     #  |(gggg, eeee) with	   | SQ    | 0000H     | 0xFFFFFFFF    | First Item                           | Second Item                                                  | SQ Delimitation            |
     #  | VR of SQ             |       | Reserved  | undefined len |                                      |                                                              | Item                       |
     #  |----------------------|-------|-----------|---------------|--------------------------------------|--------------------------------------------------------------|----------------------------|
     #  |                      |       |           |               | Item Tag | Item Length | Item Value  | Item Tag | Item Length  | Item Value  | Item Delim. | Length | SQ delim tag | Item Length |
     #  |	                   |       |           |               | (FFFE,   | 0000        |             | (FFFE,   | 0xFFFFFFFF   |             | Tag         | 0000   | (FFFE,       | 0000        |
     #  |	                   |       |           |               |  E000)   | 04F8H       | Data Set    |  E000)   | undef'd len  | Data Set    | (FFFE,E00D) | 0000H  | E0DD)        | 0000H       |
     #  |----------------------|-------|-----------|---------------|----------|-------------|-------------|----------|--------------|-------------|-------------|--------|--------------|-------------|
     #  |     4                |  2    |  2        |  4            |  4       |  4          |  04F8H      |   4      |   4          | undef'd len |   4         |   4    |    4         |   4         |
     #  |    bytes             | bytes | bytes     | bytes         | bytes    | bytes       |  bytes      |  bytes   |  bytes       |             |  bytes      |  bytes |   bytes      |  bytes      |
     #  -----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
     #
     #
     #  Data Sets are themselves tags of explicit VR, and could be recursive/nested sequences as well
     #
     #  EXAMPLE OF A DATA ELEMENT WITH IMPLICIT VR DEFINED AS A SEQUENCE
     #  OF ITEMS (VR = SQ) WITH THREE ITEMS OF EXPLICIT LENGTH
     #
     #  ------------------------------------------------------------------------------------------------------------------------------------------------------------
     #  |Data Element Tag	   | Data Element |	Data Element Value                                                                                                 |
     #  |                      |    Length	  |                                                                                                                    |
     #  |----------------------|--------------|--------------------------------------------------------------------------------------------------------------------|
     #  |(gggg, eeee) with	   | 00000F0      | First Item                           | Second Item                          | Third Item                           |
     #  | VR of SQ             | 0H           |                                      |                                      |                                      |
     #  |----------------------|--------------|------------------------------------- |--------------------------------------|---------------------------------------
     #  |                      |              | Item Tag | Item Length | Item Value  | Item Tag | Item Length | Item Value  | Item Tag | Item Length | Item Value  |
     #  |	                   |              | (FFFE,   | 0000        |             | (FFFE,   | 0000        |             | (FFFE,   | 0000        |             |
     #  |	                   |              |  E000)   | 04F8H       | Data Set    |  E000)   | 04F8H       | Data Set    |  E000)   | 04F8H       | Data Set    |
     #  |----------------------|--------------|----------|-------------|-------------|----------|-------------|-------------|----------|-------------|-------------|
     #  |     4 bytes          |  4 bytes     | 4 bytes  | 4 bytes     | 04F8H bytes | 4 bytes  | 4 bytes     | 04F8H bytes | 4 bytes  | 4 bytes     | 04F8H bytes |
     #  ------------------------------------------------------------------------------------------------------------------------------------------------------------
     #
     #
     #  On entry, the Data Element Values will be read, signalled ended when the correct number of bytes is read, or when a SQ Delimiter is encountered
     #
     #  This function answers the current location in the buffer (e.g. range.location)
     #
     #
     * 
     */
    public DicomTag readSequence(DicomTag start_sequence_tag, int[] bytesRead)
    {
        int bytes_consumed = 0;
        start_sequence_tag = readTagData(start_sequence_tag, bytesRead, false, false);
        bytes_consumed += bytesRead[0];

        int sequence_value_length = start_sequence_tag.valueLength;

        // this first tag better be an item
        DicomTag item_tag = readBaseTag(bytesRead);

        // peek a 'normal' case, then rewind the buffer pointers
        if (item_tag.isSequenceDelimiter())
        {
            // empty sequence
            start_sequence_tag.addItem(item_tag);
            return item_tag;
        }

        // back up and allow the loops below to read all the tags
        DCMBuff.advance(-1 * DCMBuff._4bytes);
        boolean is_sequence_delimiter = false;

        // this begins the item sequence, until we hit another sequence (which results in recursion)
        // or the end signaled by a sequence delimiter
        DicomTag tag;
        if (start_sequence_tag.isUndefinedLength())
        {
            while (!is_sequence_delimiter)
            {
                tag = readBaseTag(bytesRead);
                bytes_consumed += bytesRead[0];
                is_sequence_delimiter = tag.isSequenceDelimiter();
                if (is_sequence_delimiter)
                {
                    // empty sequence
                    start_sequence_tag.addItem(tag);
                    readTagData(tag, bytesRead,false, true);
                    break;
                }
                tag = readItem(tag, bytesRead);
                bytes_consumed += bytesRead[0];
                start_sequence_tag.addItem(tag);
            }
        }
        else
        {
            while (bytes_consumed < sequence_value_length)
            {
                tag = readBaseTag(bytesRead);
                bytes_consumed += bytesRead[0];
                if (is_sequence_delimiter)
                {
                    // end of sequence
                    start_sequence_tag.addItem(tag);
                    break;
                }

                tag = readItem(tag, bytesRead);
                bytes_consumed += bytesRead[0];
                start_sequence_tag.addItem(tag);
            }
        }

        return start_sequence_tag;
    }

    // A sequence is a sequence of items, items of undefined length will be bookended by the undefined length
    // item tag and an item delimiter. If the length of an item is explicit, read until the correct number of
    // bytes is consumed.

    public DicomTag readItem(DicomTag item_tag, int[] bytesRead)
    {
        bytesRead[0] = 0;
        int[] bytesReturned = new int[]{0};

        if (item_tag.isExplicit)
        {
            DCMBuff.advance(DCMBuff._4bytes);
            bytesRead[0] += DCMBuff._4bytes;
            int item_length = DCMBuff.dataChunkToInt();
            item_tag.setValueLength(item_length);
        }

        DicomTag item;
        if (item_tag.isUndefinedLength())
        {
            item = readBaseTag(bytesReturned);
            bytesRead[0] += bytesReturned[0];
            DCMBuff.advance(-1 * DCMBuff._4bytes);
            if (item.isItemDelimiter)
            {
                item_tag.addItem(item);
            }
            else
            {
                while (!item.isItemDelimiter)
                {
                    item = readBaseTag(bytesReturned);
                    bytesRead[0] += bytesReturned[0];
                    if (item.isItemDelimiter)
                    {
                        item_tag.addItem(item);
                        readTagData(item, bytesReturned, false, true);
                        break;
                    }
                    if (item.isSequence)
                    {
                        item = readSequence(item, bytesReturned);
                        item_tag.addItem(item);
                    }
                    else if (!item.isItemDelimiter)
                    {
                        item = readTagData(item, bytesReturned, true, false);
                        item_tag.addItem(item);
                    }
                    bytesRead[0] += bytesReturned[0];
                }
            }
        }
        else
        {
            while (bytesRead[0] < item_tag.valueLength)
            {
                item = readBaseTag(bytesReturned);
                bytesRead[0] += bytesReturned[0];
                if (item.isSequence)
                {
                    item = readSequence(item, bytesReturned);
                }
                else
                {
                    item = readTagData(item, bytesReturned, true, false);
                }
                bytesRead[0] += bytesReturned[0];
                item_tag.addItem(item);
            }
        }

        return item_tag;
    }

    /**
     #  From DICOM PS3.5 2017c - Data Structures and Encoding, A.4 Transfer Syntaxes For Encapsulation of Encoded Pixel Data
     #
     #  Table A.4-1. Example for Elements of an Encoded Single-Frame Image Defined as a Sequence of Three Fragments Without Basic Offset Table Item Value
     #  -----------------------------------------------------------------------------------------------------------------------------------------------------------------------------
     #  |Pixel Data Element    |     VR            | Data Element |	Data Element Value                                                                                              |
     #  |    Tag               |                   |   Length     |                                                                                                                 |
     #  |----------------------|-------------------|--------------|-----------------------------------------------------------------------------------------------------------------|
     #  |(gggg, eeee) with	   | OB    | 0000H     | 0xFFFFFFFF   | Basic Offset Table with NO Item      | First Fragment (Single Frame)           |...| SQ Delimitation            |
     #  | VR of SQ             |       | Reserved  | undef'd len  |           value                      |      Pixel Data   (repeated N times)    |...| Item                       |
     #  |----------------------|-------|-----------|--------------|--------------------------------------|-----------------------------------------|---|----------------------------|
     #  |                      |       |           |              | Item Tag | Item Length | Item Value  | Item Tag    | Item Length | Item Value  |...| SQ delim tag | Item Length |
     #  |	                   |       |           |              | (FFFE,   | 0000        |             | (FFFE,      | 0000        |             |...| (FFFE,       | 0000        |
     #  |	                   |       |           |              |  E000)   | 04F8H       | Data Set    |  E000)      | 04F8H       | Data Set    |...|  E0DD)       | 0000H       |
     #  |----------------------|-------|-----------|--------------|----------|-------------|-------------|-------------|-------------|-------------|---|-----------|----------------|
     #  |     4                |  2    |  2        |  4           |  4       |  4          |  04F8H      |   4         |   4         |  04F8H      |...|   4          |   4         |
     #  |    bytes             | bytes | bytes     | bytes        | bytes    | bytes       |  bytes      |  bytes      |  bytes      |  bytes      |...|  bytes       |  bytes      |
     #  -----------------------------------------------------------------------------------------------------------------------------------------------------------------------------
     * 
     */
    public DicomTag readPixelData(DicomTag pixel_data_tag, int[] bytesRead)
    {
        // if the pixel data is encoded with a defined value length, just read it like a regular tag, if (however) it
        // is a sequence of frames, the table above is how to read it
        if (pixel_data_tag.isUndefinedLength())
        {
            // this begins the item sequence, until we hit another sequence (which results in recursion)
            // or the end signaled by a sequence delimiter
            boolean is_sequence_delimiter = false;
            while (!is_sequence_delimiter)
            {
                DicomTag tag = readBaseTag(bytesRead);
                if (tag.isItem)
                {
                    DCMBuff.advance(DCMBuff._4bytes);
                    bytesRead[0] += DCMBuff._4bytes;
                    int value_length = DCMBuff.dataChunkToInt();
                    DCMBuff.advance(value_length);
                    bytesRead[0] += value_length;
                    byte[] value_bytes = DCMBuff.getChunk();
                    tag.setRawValue(value_bytes);
                    tag.setVR(pixel_data_tag.getVR());
                    pixel_data_tag.addItem(tag);
                }
                else if (tag.isSequenceDelimiter)
                {
                    tag = readTagData(tag, bytesRead, false, false);
                    pixel_data_tag.addItem(tag);
                    is_sequence_delimiter = true;
                }
            }
        }
        else
        {
            DCMBuff.advance(pixel_data_tag.valueLength);
            byte[] value_bytes = DCMBuff.getChunk();
            pixel_data_tag.setRawValue(value_bytes);
        }
        return pixel_data_tag;
    }
}
