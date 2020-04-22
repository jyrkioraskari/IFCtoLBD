/*******************************************************************************
 * Copyright (C) 2017 Chi Zhang
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package nl.tue.ddss.convert;

@SuppressWarnings("serial")
public class IfcDataFormatException extends Exception{
	
	String type;

	public IfcDataFormatException(String message) {
		super(message);
	}
	
	public IfcDataFormatException(String messageFormat,Object...args){
		this(String.format(messageFormat, args));
	}
	
	/**
	 * Return an exception that an IFC object has more attributes than allowed.
	 * @param lineNumber Only one argument is allowed and it is supposed to be the entire line of that IFC object.
	 * @return an IfcDataFormatException.
	 */
    public static IfcDataFormatException attributeOutOfBounds(String lineNumber){
    	String messageFormat="Attribute out of bounds in line %1s: Object in IFC files has more attributes than it is allowed";
    	return new IfcDataFormatException(messageFormat,lineNumber);
    }
    
    /**
     * 
     * @param args Two arguments are allowed. T
     * @return
     */
    public static IfcDataFormatException nonExistingEntity(String lineNumber,String wrongEntity){
    	String messageFormat="Entity not exist in line %1s: %2s is not an existing entity";
    	return new IfcDataFormatException(messageFormat,lineNumber,wrongEntity);
    }
    
    public static IfcDataFormatException nonExistingType(String lineNumber,String wrongType){
    	String messageFormat="Type not exist in line %1s: %2s is not an existing TYPE";
    	return new IfcDataFormatException(messageFormat,lineNumber,wrongType);
    }
    
    public static IfcDataFormatException referencingNonExistingObject(String lineNumber,String wrongLineNumber){
    	String messageFormat="Referencing to non-existing object in line %1s: Reference to non-existing line number %2s";
    	return new IfcDataFormatException(messageFormat,lineNumber,wrongLineNumber);
    } 
    
    public static IfcDataFormatException valueOutOfRange(String lineNumber,String wrongValue,String valueType){
    	String messageFormat="Value out of range in line %1s: %2s is not a %3s, which is not allowed";
    	return new IfcDataFormatException(messageFormat,lineNumber,wrongValue,valueType);
    }
    
}
