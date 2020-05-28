package com.openifctools.guidcompressor;

/**
 * This class is a simple data type used during the conversion between compressed 
 * and uncompressed string representations of GUIDs use within the Industry Foundation
 * Classes (IFC). It is equivalent to the struct _GUID in the c implementation of the 
 * algorithm as follows:<br>
 * originally proposed by Jim Forester<br>
 * implemented previously by Jeremy Tammik using hex-encoding<br>
 * Peter Muigg, June 1998<br>
 * Janos Maros, July 2000



 * This class is provided as-is with no warranty.<br>
 * <br>
 * The class Guid is part of the OPEN IFC JAVA TOOLBOX package. Copyright:
 * CCPL BY-NC-SA 3.0 (cc) 2008 Eike Tauscher, Jan Tulke <br>
 * <br>
 * The OPEN IFC JAVA TOOLBOX package itself (except this class) is licensed under <br>
 * (http://creativecommons.org/licenses/by-nc-sa/3.0/de/) Creative Commons
 * Attribution-Non-Commercial- Share Alike 3.0 Germany.
 * Please visit 
 * 
 * http://www.openifctools.com for more
 * information.
 * 
 * @author Jan Tulke
 * @version 1.0 - 24.07.2009
 *
 */

public class Guid {
	long   Data1;
	int    Data2;
	int    Data3;
	char[] Data4 = new char[8];
}
