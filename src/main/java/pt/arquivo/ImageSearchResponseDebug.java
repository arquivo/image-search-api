package pt.arquivo;
import org.apache.solr.common.util.NamedList;

public class ImageSearchResponseDebug {
	NamedList<Object> responseHeader;
	ImageSearchResults response;
	
	public ImageSearchResponseDebug(NamedList<Object> responseHeader, ImageSearchResults response){
		this.responseHeader= responseHeader;
		this.response = response;
	}
}