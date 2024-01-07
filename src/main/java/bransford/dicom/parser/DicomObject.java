package bransford.dicom.parser;

import java.util.ArrayList;
import java.util.HashMap;

// A DicomImage is represented as a DicomObject, storing all tags as doubly linked list of TagNode
// Objects, where the TagNode wraps an individual DicomTag. Linked list preserves sequence of tags
// The other mechanism of storage is each tage in a Hash map, for fast retrieval
public class DicomObject
{
    public HashMap<String, DicomTag> tags = new HashMap<>();
    public TagNode head = null;
    public TagNode tail = null;

    public void put(DicomTag tag)
    {
        DicomTag cache_item = tags.get(tag.groupElementKey);

        if (cache_item == null)
        {
            add_tag(new TagNode(tag));
            tags.put(tag.groupElementKey, tag);
        }
        // else is a duplicate key, ignore
    }

    public void add_tag(TagNode node)
    {
        if (node == null)
        {
            return;
        }

        if (head == null)
        {
            tail = node;
            head = node;
        }
        else
        {
            tail.next = node;
            node.previous = tail;
            node.next = null;
            tail = node;
        }
    }

    public DicomTag get_dicom_tag(String grp_ele)
    {
        return tags.get(grp_ele);
    }

    public int getNumTags()
    {
        return tags.size();
    }

    public String dcmDump()
    {
        int depth = 0;
        TagNode node = head;
        DicomTag tag;
        StringBuilder sb = new StringBuilder();
        while (node.next != null)
        {
            tag = node.tag;
            sb.append(tag.toString()).append("\n");
            if (tag.hasSubsequence())
            {
                dumpSubsequence(tag.subsequence, depth + 1, sb);
            }
            node = node.next;
        }
        if (node == tail)
        {
            tag = node.tag;
            sb.append(tag.toString()).append("\n");
            if (tag.hasSubsequence())
            {
                dumpSubsequence(tag.subsequence, depth + 1, sb);
            }
        }

        String dcmObjectDump = sb.toString();
        System.out.println(dcmObjectDump);
        return dcmObjectDump;
    }

    private void dumpSubsequence(ArrayList<DicomTag> subsequence, int depth, StringBuilder sb)
    {
        for (int i = 0; i < subsequence.size(); i++)
        {
            DicomTag tag = subsequence.get(i);
            if (i == subsequence.size() - 1 && depth > 0)
            {
                sb.append("\t".repeat(Math.max(0, depth-1)));
            }
            else
            {
                sb.append("\t".repeat(Math.max(0, depth)));
            }
            sb.append(tag.toString()).append("\n");
            if (tag.hasSubsequence())
            {
                dumpSubsequence(tag.subsequence, depth + 1, sb);
            }
        }
    }
}

class TagNode
{
    public DicomTag tag;
    public TagNode next;
    public TagNode previous;

    public TagNode(DicomTag tag)
    {
        this.tag = tag;
        this.next = null;
        this.previous = null;
    }
}

