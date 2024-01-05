package edu.mayo.dicom.parser.test;

import edu.mayo.dicom.parser.DCMBuff;
import edu.mayo.dicom.parser.DicomObject;
import edu.mayo.dicom.parser.DicomParser;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class DicomObjectTest
{

    @Test
    public void printDCMTest()
    {
        String dcm_file = "/Users/m056084/dicom/testDCMParser/0002.DCM";
        String dict_file = "/Users/m056084/JDicomParser/src/edu/mayo/dicom/parser/dicom.dic";

        DicomParser parser = new DicomParser(dcm_file, dict_file);
        if (DCMBuff.data == null)
        {
            System.err.println("unable to parse DICOM filer: " + dcm_file);
            System.exit(-1);
        }

        try
        {
            if (parser.isValidDicomImage())
            {
                DicomObject dcm = parser.parseDICOMImage();
                int numTags = dcm.getNumTags();
                Assert.assertEquals(75, numTags);
                String dcmObjectDump = dcm.dcmDump();
                // parser.exploreDicomObject();
            }
            else
            {
                System.err.println("Invalid DICOM, missing magic number: " + dcm_file);
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
