package edu.mayo.dicom.parser;

import java.io.*;
import java.util.HashMap;

public class DICOMDictionary
{
    private final HashMap<String, DicomTag> dictionary = new HashMap<>();

    private final boolean writeInterface = false;

    public DICOMDictionary(String dict)
    {
        init(dict);
    }

    private void init(String dict)
    {
        BufferedReader reader;
        DicomTag tag;

        try
        {
            reader = new BufferedReader(new FileReader(dict));
            String line;

            while ((line = reader.readLine()) != null)
            {
                if (line.contains("#") || line.contains("Tag"))
                {
                    continue;
                }

                tag = new DicomTag();
                tag.initializeFromDict(line.split("\t"));
                dictionary.put(tag.groupElementKey, tag);
            }

            reader.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    public DicomTag get_tag(String grp, String ele)
    {
        String group_element_key = "(" + grp + "," + ele + ")";
        DicomTag a_tag = get_tag_with_key(group_element_key);

        if (a_tag != null)
        {
            a_tag = DicomTag.copy(a_tag);
            a_tag.initialize();
        }

        return a_tag;
    }

    public DicomTag get_tag_with_key(String  group_element_key)
    {
        return dictionary.get(group_element_key);
    }

    public HashMap<String, DicomTag> getDictionary()
    {
        return dictionary;
    }
}
