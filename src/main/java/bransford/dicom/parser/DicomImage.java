package bransford.dicom.parser;

import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;

import bransford.jpeg.bit8.jpeglib8;
import bransford.jpeg.bit12_16.jpeglib12_16;
import bransford.jpeg.bit8.Driver8;
import bransford.jpeg.bit12_16.Driver12_16;

public class DicomImage
{
    private final DicomObject dicomObject;

    public int rows = 0;
    public int spp = 0;
    public int columns = 0;
    public int numberOfFrames = 0;

    public String photometricInterpretation = null;
    public double windowWidth = 0.0;
    public double windowCenter = 0.0;
    public double rescaleIntercept = 0.0;
    public double rescaleSlope = 1.0;


    public String pixelSpacing = null;

    public int cineRate = 0;

    public String transferSyntaxUID = null;
    public boolean isCompressed = false;
    public int bitsAllocated = 0;
    public int bitsStored = 0;
    public int highBit = 0;

    // bytes allocated per sample (1 for an 8-bit, 2 for 10/12/16-bit, 3 for 3 sample per pix color)
    public int bpp = 0;

    public String studyDescription = null;
    public String studyDate = null;
    public String studyInstanceUID = null;

    public String seriesDescription = null;
    public String seriesDate = null;
    public String seriesInstanceUID = null;
    public String SOPInstanceUID = null;

    public String instanceNumber = null;
    public String SOPClassUID = null;
    public String modality = null;
    public String accessionNumber = null;

    public String patientName = null;
    public String patientID = null;
    public String patientBirthDate = null;
    public String patientSex = null;
    public String patientAge = null;
    public String patientSize = null;
    public String patientWeight = null;

    public byte[][] pixels = null;

    public DicomImage(DicomObject dicomObject)
    {
        this.dicomObject = dicomObject;
        initialize();
    }

    private void initialize()
    {
        rows = (int) ValueConversions.getNumericValue(getTag(DicomTags.Columns));
        spp = (int) ValueConversions.getNumericValue(getTag(DicomTags.SamplesPerPixel));
        columns = (int) ValueConversions.getNumericValue(getTag(DicomTags.Columns));
        numberOfFrames = ValueConversions.stringToInt(getTag(DicomTags.NumberOfFrames));
        if (numberOfFrames == 0)
        {
            numberOfFrames++;
        }

        photometricInterpretation = stringValue(DicomTags.PhotometricInterpretation);
        String ww = stringValue(DicomTags.WindowWidth);
        if (ww != null && !ww.isEmpty())
        {
            windowWidth = new BigDecimal(ww).doubleValue();
        }

        String wc = stringValue(DicomTags.WindowCenter);
        if (wc != null && !wc.isEmpty())
        {
            windowCenter = new BigDecimal(wc).doubleValue();
        }

        String ri = stringValue(DicomTags.RescaleIntercept);
        if (ri != null && !ri.isEmpty())
        {
            rescaleIntercept = new BigDecimal(ri).doubleValue();
        }

        cineRate = (int) ValueConversions.getNumericValue(getTag(DicomTags.CineRate));

        String rs = stringValue(DicomTags.RescaleSlope);
        if (rs != null && !rs.isEmpty())
        {
            rescaleSlope = new BigDecimal(rs).doubleValue();
        }

        transferSyntaxUID = stringValue(DicomTags.PatientName);
        DicomTag transferSyntaxTag = getTag(DicomTags.TransferSyntaxUID);
        isCompressed = transferSyntaxTag.isCompressed;

        bitsAllocated = (int) ValueConversions.getNumericValue(getTag(DicomTags.BitsAllocated));
        bitsStored = (int) ValueConversions.getNumericValue(getTag(DicomTags.BitsStored));
        highBit = (int) ValueConversions.getNumericValue(getTag(DicomTags.HighBit));
        pixelSpacing = stringValue(DicomTags.PixelSpacing);
        bpp = (bitsAllocated / 8) * spp;

        studyDescription = stringValue(DicomTags.StudyDescription);
        studyDate = stringValue(DicomTags.StudyDate);
        seriesDescription = stringValue(DicomTags.SeriesDescription);
        seriesDate = stringValue(DicomTags.SeriesDate);
        studyInstanceUID = stringValue(DicomTags.StudyInstanceUID);
        instanceNumber = stringValue(DicomTags.InstanceNumber);
        seriesInstanceUID = stringValue(DicomTags.SeriesInstanceUID);
        SOPInstanceUID = stringValue(DicomTags.SOPInstanceUID);
        SOPClassUID = stringValue(DicomTags.SOPClassUID);
        modality = stringValue(DicomTags.Modality);
        accessionNumber = stringValue(DicomTags.AccessionNumber);

        patientName = stringValue(DicomTags.PatientName);
        patientID = stringValue(DicomTags.PatientID);
        patientBirthDate = stringValue(DicomTags.PatientBirthDate);
        patientSex = stringValue(DicomTags.PatientSex);
        patientAge = stringValue(DicomTags.PatientAge);
        patientSize = stringValue(DicomTags.PatientSize);
        patientWeight = stringValue(DicomTags.PatientWeight);
    }

    public byte[][] getPixelData(boolean decompress)
    {
        DicomTag tag = getTag(DicomTags.PixelData);

        // number of bytes in a row
        int stride = columns * bpp;

        byte[][] rawPixels = null;

        if (isCompressed)
        {
            // should be encapsulated
            if (tag.hasSubsequence())
            {
                ArrayList<DicomTag> subsequence = tag.subsequence;

                if (numberOfFrames == subsequence.size() - 2)
                {
                    rawPixels = new byte[numberOfFrames][];
                }
                else
                {
                    System.err.println("number of frames doesn't match what is in the DICOM header");
                    rawPixels = new byte[subsequence.size() - 2][];
                }

                // clip offsets and sequence delimiter; [offset, byte[], byte[], ..., SQ delim]
                for (int i = 1, j = 0; i < subsequence.size() - 1; i++, j++)
                {
                    rawPixels[j] = subsequence.get(i).rawValue;
                }
            }
            else
            {
                System.err.println("improperly formatted compressed pixel data; " +
                        "should be encapsulated");
                return rawPixels;
            }
        }
        else
        {
            pixels = ValueConversions.chunkByteArray(stride, tag.valueOB, numberOfFrames);
        }

        if (decompress && isCompressed)
        {
            pixels = decompress(rawPixels);
        }
        else
        {
            pixels = rawPixels;
        }

        return pixels;
    }

    public byte[][] decompress(byte[][] pixels)
    {
        byte[][] decompressedPixels = null;

        if (bitsAllocated == 8)
        {
            jpeglib8.J_COLOR_SPACE ijgPhotometricInterpretation = jpeglib8.J_COLOR_SPACE.JCS_UNKNOWN;
            switch(photometricInterpretation)
            {
                case "MONOCHROME1", "MONOCHROME2" ->
                {
                    ijgPhotometricInterpretation = jpeglib8.J_COLOR_SPACE.JCS_GRAYSCALE;
                }
                case "RGB" ->
                {
                    ijgPhotometricInterpretation = jpeglib8.J_COLOR_SPACE.JCS_RGB;
                }
                case "CMYK" ->
                {
                    ijgPhotometricInterpretation = jpeglib8.J_COLOR_SPACE.JCS_CMYK;
                }
                case "YCCK" ->
                {
                    ijgPhotometricInterpretation = jpeglib8.J_COLOR_SPACE.JCS_YCCK;
                }
                default ->
                {
                    if (photometricInterpretation.contains("YBR"))
                    {
                        ijgPhotometricInterpretation = jpeglib8.J_COLOR_SPACE.JCS_YCbCr;
                    }
                }
            }

            ArrayList<HashMap<String, Integer>> imageCharacteristics = new ArrayList<>();
            Driver8 decompressor = new Driver8();
            decompressedPixels = decompressor.decompress8(pixels, ijgPhotometricInterpretation, imageCharacteristics);
        }
        else if(bitsAllocated == 10 || bitsAllocated == 12 || bitsAllocated == 16)
        {
            jpeglib12_16.J_COLOR_SPACE ijgPhotometricInterpretation = jpeglib12_16.J_COLOR_SPACE.JCS_UNKNOWN;
            switch(photometricInterpretation)
            {
                case "MONOCHROME1", "MONOCHROME2" ->
                {
                    ijgPhotometricInterpretation = jpeglib12_16.J_COLOR_SPACE.JCS_GRAYSCALE;
                }
                case "RGB" ->
                {
                    ijgPhotometricInterpretation = jpeglib12_16.J_COLOR_SPACE.JCS_RGB;
                }
                case "CMYK" ->
                {
                    ijgPhotometricInterpretation = jpeglib12_16.J_COLOR_SPACE.JCS_CMYK;
                }
                case "YCCK" ->
                {
                    ijgPhotometricInterpretation = jpeglib12_16.J_COLOR_SPACE.JCS_YCCK;
                }
                default ->
                {
                    if (photometricInterpretation.contains("YBR"))
                    {
                        ijgPhotometricInterpretation = jpeglib12_16.J_COLOR_SPACE.JCS_YCbCr;
                    }
                }
            }

            ArrayList<HashMap<String, Integer>> imageCharacteristics = new ArrayList<>();
            Driver12_16 decompressor = new Driver12_16();
            decompressedPixels = decompressor.decompress12_16(pixels, ijgPhotometricInterpretation, imageCharacteristics);
        }

        writeRawData("/Users/m056084/dicom/color_problems/xc_java.pix", decompressedPixels[0]);
        return decompressedPixels;
    }

    public DicomTag getTag(String grp_element_key)
    {
        DicomTag tag = dicomObject.get_dicom_tag(grp_element_key);
        if (tag != null)
        {
            tag.isValueCalculated();
        }

        return tag;
    }

    public String stringValue(String grp_element_key)
    {
        DicomTag tag = getTag(grp_element_key);
        if (tag == null)
        {
            return "";
        }
        else
        {
            return tag.valueString;
        }
    }

    public static void writeRawData(String fileName, byte[] bytes)
    {
        try
        {
            FileOutputStream fos = new FileOutputStream(fileName);
            fos.write(bytes);
            fos.flush();
            fos.close();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
}
