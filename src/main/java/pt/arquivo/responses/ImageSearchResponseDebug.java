package pt.arquivo.responses;
import org.apache.solr.common.util.NamedList;
import pt.arquivo.ImageSearchResults;

public class ImageSearchResponseDebug {
	NamedList responseHeader;
	ImageSearchResults response;
	
	public ImageSearchResponseDebug(NamedList responseHeader, ImageSearchResults response){
		this.responseHeader= responseHeader;
		this.response = response;
	}
}