package pt.arquivo.responses;

import org.apache.solr.common.SolrDocumentList;
import pt.arquivo.ImageSearchResults;

import java.util.HashMap;

public class ImageSearchErrorResponse {
	HashMap<String,String> error;

	String serviceName;
	String linkToService;
	String linkToDocumentation;
	long totalItems;
	int numberOfResponseItems;
	long offset;
	SolrDocumentList responseItems;

	public ImageSearchErrorResponse(Throwable e){
		this.serviceName = "Arquivo.pt - image search service.";
		this.linkToService = "https://arquivo.pt/images.jsp";
		this.linkToDocumentation = "https://github.com/arquivo/pwa-technologies/wiki/ImageSearch-API-v1-(beta)";
		this.totalItems = 0;
		this.numberOfResponseItems = 0;
		this.offset = 0;
		this.responseItems = new SolrDocumentList();


		this.error = new HashMap<>();
		this.error.put(e.getClass().getCanonicalName(), e.getMessage());
	}
}