# JDICOM
DICOM image parser written from scratch to NEMA specification:

https://dicom.nema.org/medical/dicom/current/output/html/part01.html

The code creates a data structure of header tags and associated data for retrieval. It references the JIJG project (https://github.com/bransma/JIJG) my Java port of the Independent JPEG c-based library, to decompress both lossy and lossless pixel data. Clone JIJG and run the maven script to install in a local maven repo, such that JDICOM will build properly, as the pom refers to JIJG as a dependency.

In time I will be adding functionality for (medical) image processing and/or analysis. For example, window width/level, affine transformations, edge enhancement, digitial subtraction angiography, etc.
