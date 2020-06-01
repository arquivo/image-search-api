package pt.arquivo.responses;
import org.apache.solr.common.util.NamedList;
import pt.arquivo.ImageSearchResults;

public class ImageSearchResponseDebug {
	NamedList<Object> responseHeader;
	ImageSearchResults response;
	
	public ImageSearchResponseDebug(NamedList<Object> responseHeader, ImageSearchResults response){
		this.responseHeader= responseHeader;
		this.response = response;
	}
}