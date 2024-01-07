package bransford.dicom.parser;

import java.io.IOException;

public class Main
{
    public static void main(String[] args)
    {
        if (args.length != 2)
        {
            System.err.println("Command line args must contain the fully " +
                    "qualified path to <DICOM file> <dictionary file>");
            return;
        }

        DicomParser parser = new DicomParser(args[0], args[1]);
        if (DCMBuff.data == null)
        {
            System.err.println("unable to parse DICOM filer: " + args[0]);
            System.exit(-1);
        }

        try
        {
            if (parser.isValidDicomImage())
            {
                DicomObject dcm = parser.parseDICOMImage();
                dcm.dcmDump();
                DicomImage dicomImage = new DicomImage(dcm);
                byte[][] pixelData = dicomImage.getPixelData(true);
                System.out.println("got the pixel data");
            }
            else
            {
                System.err.println("Invalid DICOM, missing magic number: " + args[0]);
                System.exit(-1);
            }
        }
        catch (IOException ioe)
        {
            ioe.printStackTrace();
            System.exit(-1);
        }
    }
}
