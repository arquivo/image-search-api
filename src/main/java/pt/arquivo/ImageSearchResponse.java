package pt.arquivo;

import org.apache.solr.common.util.NamedList;

public class ImageSearchResponse {
	NamedList<Object> responseHeader;
	ImageSearchResults response;
	
	public ImageSearchResponse(NamedList<Object> responseHeader, ImageSearchResults response){
		this.responseHeader= responseHeader;
		this.response = response;
	}
}