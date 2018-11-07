package cnExperiment1;

public class HttpHeader {
	protected String method;
	protected String url;
	protected String host;
	protected String cookie;

	public HttpHeader(String method, String url, String host, String cookie) {
		super();
		this.method = method;
		this.url = url;
		this.host = host;
		this.cookie = cookie;
	}
	
	
}
