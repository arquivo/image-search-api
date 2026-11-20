package pt.arquivo.responses;

import org.apache.solr.common.SolrDocumentList;

// The reply to a failed search. It says only that the search failed: the cause is logged by ImageSearchServlet.
public class ImageSearchErrorResponse {
	static final String MESSAGE = "The search failed.";

	String error;

	String serviceName;
	String linkToService;
	String linkToDocumentation;
	long totalItems;
	int numberOfResponseItems;
	long offset;
	SolrDocumentList responseItems;

	public ImageSearchErrorResponse(){
		this.error = MESSAGE;
		this.serviceName = "Arquivo.pt - image search service.";
		this.linkToService = "https://arquivo.pt/images.jsp";
		this.linkToDocumentation = "https://github.com/arquivo/pwa-technologies/wiki/ImageSearch-API-v1-(beta)";
		this.totalItems = 0;
		this.numberOfResponseItems = 0;
		this.offset = 0;
		this.responseItems = new SolrDocumentList();
	}
}
