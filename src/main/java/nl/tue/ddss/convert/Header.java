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

import java.util.ArrayList;
import java.util.List;

/**
 * The class to store IFC Header data.
 * @author Chi
 *
 */
public class Header {
	
	private List<String> description=new ArrayList<String>();
	private String implementation_level;
	private String name;
	private String time_stamp;
	private List<String> author=new ArrayList<String>();
	private List<String> organization=new ArrayList<String>();
	private String preprocessor_version;
	private String originating_system;
	private String authorization;
	private List<String> schema_identifiers=new ArrayList<String>();
	
	public List<String> getDescription() {
		return description;
	}
	public void setDescription(List<String> description) {
		this.description = description;
	}
	public String getImplementation_level() {
		return implementation_level;
	}
	public void setImplementation_level(String implementation_level) {
		this.implementation_level = implementation_level;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getTime_stamp() {
		return time_stamp;
	}
	public void setTime_stamp(String time_stamp) {
		this.time_stamp = time_stamp;
	}
	public List<String> getAuthor() {
		return author;
	}
	public void setAuthor(List<String> author) {
		this.author = author;
	}
	public List<String> getOrganization() {
		return organization;
	}
	public void setOrganization(List<String> organization) {
		this.organization = organization;
	}
	public String getPreprocessor_version() {
		return preprocessor_version;
	}
	public void setPreprocessor_version(String preprocessor_version) {
		this.preprocessor_version = preprocessor_version;
	}
	public String getOriginating_system() {
		return originating_system;
	}
	public void setOriginating_system(String originating_system) {
		this.originating_system = originating_system;
	}
	public String getAuthorization() {
		return authorization;
	}
	public void setAuthorization(String authorization) {
		this.authorization = authorization;
	}
	public List<String> getSchema_identifiers() {
		return schema_identifiers;
	}
	public void setSchema_identifiers(List<String> schema_identifiers) {
		this.schema_identifiers = schema_identifiers;
	}


}
