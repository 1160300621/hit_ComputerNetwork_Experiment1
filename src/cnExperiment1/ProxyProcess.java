package cnExperiment1;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
客户端上的使用
1.getInputStream方法可以得到一个输入流，客户端的Socket对象上的getInputStream方法得到输入流其实就是从服务器端发回的数据。
2.getOutputStream方法得到的是一个输出流，客户端的Socket对象上的getOutputStream方法得到的输出流其实就是发送给服务器端的数据。
服务器端上的使用
1.getInputStream方法得到的是一个输入流，服务端的Socket对象上的getInputStream方法得到的输入流其实就是从客户端发送给服务器端的数据流。
2.getOutputStream方法得到的是一个输出流，服务端的Socket对象上的getOutputStream方法得到的输出流其实就是发送给客户端的数据。
 */
public class ProxyProcess implements Runnable {
	// http端口，默认为80
	static int HttpPort = 80;
	static int size=100000;

	// 超时时间
	static int timeout = 500000;

	//客户端和代理服务器的socket
	private Socket ClientSocket = null;
	private Socket ProxyClientSocket = null;

	//socket的写入写出方法包装
	private InputStream ClientInputStream = null;
	private InputStream ProxyInputStream = null;
	private BufferedReader ClientBufferReader = null;
	private BufferedReader ProxyBufferReader = null;
	private OutputStream ClientOutputStream = null;
	private OutputStream ProxyOutputStream = null;
	private PrintWriter ClientPrintWriter = null;
	private PrintWriter ProxyPrintWriter = null;

	// 对象被缓存的具体时间
	static Map<String, String> cacheTime = new HashMap<>();

	// 对象被缓存的具体数据
	static Map<String, List<Byte>> cacheBytes = new HashMap<>();

	static List<String> WebsiteFilter=new ArrayList<>();
	static Map<String,String> guide=new HashMap<>();
	
	
	public ProxyProcess(Socket clientsocket) throws IOException {
		super();
		this.ClientSocket = clientsocket;
		ClientInputStream = clientsocket.getInputStream();
		ClientBufferReader = new BufferedReader(new InputStreamReader(ClientInputStream));
		ClientOutputStream = clientsocket.getOutputStream();
		ClientPrintWriter = new PrintWriter(ClientOutputStream);
		
		WebsiteFilter.add("jwts.hit.edu.cn");
		guide.put("jwes.hit.edu.cn", "http://today.hit.edu.cn/");
		
	}

	/**
	 * 解析http头的信息，获取method，url，host，cookie
	 */
	public HttpHeader parse(List<String> header) {
		String firstLine = header.get(0);
		String method = null;
		String url = null;
		String host = null;
		String cookie = null;
		if (firstLine.charAt(0) == 'G') {
			method = "GET";
			url = firstLine.substring(4, firstLine.length() - 9);
		} else if (firstLine.charAt(0) == 'P') {
			method = "POST";
			url = firstLine.substring(5, firstLine.length() - 9);
		} else {
			method = "CONNECT";
		}

		for (int i = 0; i < header.size(); i++) {
			if (header.get(i).startsWith("Host")) {
				host = header.get(i).substring(6, header.get(i).length());
			} else if (header.get(i).startsWith("Cookie")) {
				cookie = header.get(i).substring(8, header.get(i).length());
			}
		}
		HttpHeader httpHeader = new HttpHeader(method, url, host, cookie);

		return httpHeader;
	}


	/**
	 * 获取代理服务器和服务器套接字
	 * @param host 主机名
	 * @param port 端口
	 * @param times 连接次数
	 * @return 代理服务器与服务器的Sockek
	 * @throws UnknownHostException
	 * @throws IOException
	 */
	public Socket ConnectToServer(String host, int port, int times) throws UnknownHostException, IOException {
		for (int i = 0; i < times; i++) {
			ProxyClientSocket = new Socket(host, port);

			ProxyClientSocket.setSoTimeout(timeout);
			ProxyInputStream = ProxyClientSocket.getInputStream();
			ProxyBufferReader = new BufferedReader(new InputStreamReader(ProxyInputStream));
			ProxyOutputStream = ProxyClientSocket.getOutputStream();
			ProxyPrintWriter = new PrintWriter(ProxyOutputStream);

			if (ProxyClientSocket != null) {
				return ProxyClientSocket;
			}
		}
		return null;
	}

	/**
	 * 代理服务器向服务器发送请求信息
	 * @param lst 请求信息
	 */
	public void SendToServer(List<String> lst) {
		System.out.println("\n---------Send Request to Server---------");
		for (int i = 0; i < lst.size(); i++) {
			String line = lst.get(i);
			ProxyPrintWriter.write(line + "\r\n");
			System.out.println(line);
		}
		ProxyPrintWriter.write("\r\n");
		ProxyPrintWriter.flush();
	}
	
	/**
	 * 没有缓存的情况下，代理服务器从服务器转发响应信息到客户端
	 * @param url
	 * @return
	 */
	public boolean SendBackToClient(String url) {
		/*
		 * 必须采用bytes数组，否则由于ASCII码与unicode编码的差异，无法识别
		 */
		
		System.out.println("\n---------Retransmission From Server To Client---------");
		
		List<Byte> lst=new ArrayList<>();
		
		try {
			// String time = null;
			byte bytes[] = new byte[size];
			int len;
			while (true) {
				if ((len = ProxyInputStream.read(bytes)) >= 0) {
					ClientOutputStream.write(bytes, 0, len);
					for (int i=0;i<len;i++) {
						lst.add(bytes[i]);
					}
				} else if (len < 0) {
					break;
				}
			}
			
			byte b[]=new byte[lst.size()];
			for (int i=0;i<lst.size();i++) {
				b[i]=lst.get(i);
			}
			
			String s=new String(b);
			String time=findTime(s);
			cacheTime.put(url, time);
			cacheBytes.put(url, lst);
			
			ClientPrintWriter.write("\r\n");
			ClientPrintWriter.flush();
			ClientOutputStream.close();
		} catch (IOException e) {
		} catch (Exception e) {
		}
		return true;
	}
	
	/**
	 * 根据字符串获取其中的Date时间
	 * @param s
	 * @return
	 */
	public String findTime(String s) {
		int begin=s.indexOf("Date");
		int end=s.indexOf("GMT");
		//System.out.println(s.substring(begin+6, end+3));
		return s.substring(begin+6, end+3);
	}

	/**
	 * 有缓存的情况下，给客户端需要的信息
	 * 1.在缓存时间后服务器没有修改对象，则将缓存直接发送给客户端
	 * 2.在缓存时间后服务器修改对象了，则将Sever的新对象发送给客户端
	 * @param header
	 * @param host
	 * @param url
	 * @return
	 */
	public boolean SendBackToClientWithCache(List<String> header,String host, String url) {
		String modifiTime=cacheTime.get(url);
		// 发送确认是否修改的报文到服务器
		ProxyPrintWriter.write(header.get(0) + "\r\n");
		ProxyPrintWriter.write("Host: "+host + "\r\n");
		
		System.out.println("Modified Time:"+modifiTime);
		String str = "If-modified-since: " + modifiTime + "\r\n";
		ProxyPrintWriter.write(str);
		ProxyPrintWriter.write("\r\n");
		ProxyPrintWriter.flush();

		try {
			String ServerMessage = ProxyBufferReader.readLine();
			//System.out.println(ServerMessage);
			if (ServerMessage == null) {
				return false;
			}
			System.out.println("Server Message First Line:"+ServerMessage);
			// 如果服务器在缓存时间后未修改对象，直接转发给客户端缓存
			if (ServerMessage.contains("Not Modified")) {
				List<Byte> lst=cacheBytes.get(url);
				byte bytes[]=new byte[lst.size()];
				for (int i=0;i<lst.size();i++) {
					bytes[i]=lst.get(i);
				}
				ClientOutputStream.write(bytes);
				ClientPrintWriter.write("\r\n");
				ClientPrintWriter.flush();
				ClientPrintWriter.close();
			}
			//如果修改过对象，则将新的对象按字节发给客户端
			else if (ServerMessage.contains("OK")) {
				DataOutputStream d=new DataOutputStream(ClientOutputStream);
				byte[] b=(ServerMessage+"\r\n").getBytes();
				d.write(b);
				//ClientPrintWriter.write("\r\n");
				byte bytes[] = new byte[size];
				int len;
				while (true) {
					if ((len = ProxyInputStream.read(bytes)) > 0) {
						ClientOutputStream.write(bytes, 0, len);
					} else if (len < 0) {
						break;
					}
				}
		
				cacheTime.remove(url);
				cacheBytes.remove(url);
				//cacheTime.put(url, time);
				//cacheBytes.put(url, lst);
				
				ClientPrintWriter.write("\r\n");
				ClientPrintWriter.flush();
				ClientOutputStream.close();
			}
			else {	
				cacheBytes.remove(url);
				cacheTime.remove(url);
				while (!ProxyBufferReader.readLine().equals("")) {
					;
				}
				byte bytes[] = new byte[size];
				int len;
				while (true) {
					if ((len = ProxyInputStream.read(bytes)) >= 0) {
						//ClientOutputStream.write(bytes, 0, len);
					} else if (len < 0) {
						break;
					}
				}
				run();
			}
		} catch (IOException e1) {
		}

		return true;
	}
	
	void Filter() {
		for (int i=1;i<419;i++) {
			ClientPrintWriter.write("被过滤了\t");
			if (i%11==0) {
				ClientPrintWriter.write("\r\n");
			}
		}
			//ClientPrintWriter.write("被过滤了\t被过滤了		被过滤了		被过滤了		被过滤了		被过滤了\r\n");
		ClientPrintWriter.write("\r\n");
		ClientPrintWriter.flush();
		ClientPrintWriter.close();
	}
	
	private void phishing(List<String> header){
		/*
		 * GET http://today.hit.edu.cn/ HTTP/1.1
		 * Host: today.hit.edu.cn
		 * User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:63.0) Gecko/20100101 Firefox/63.0
		 * Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*//*;q=0.8
		 * Accept-Language: zh-CN,zh;q=0.8,zh-TW;q=0.7,zh-HK;q=0.5,en-US;q=0.3,en;q=0.2
		 * Accept-Encoding: gzip, deflate
		 * Connection: keep-alive
		 * Upgrade-Insecure-Requests: 1
		 */
		header.clear();
		header.add("GET http://www.lottery.gov.cn/ HTTP/1.1");
		header.add("Host: www.lottery.gov.cn");
		header.add("User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:63.0) Gecko/20100101 Firefox/63.0");
		header.add("Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
		header.add("Accept-Language: zh-CN,zh;q=0.8,zh-TW;q=0.7,zh-HK;q=0.5,en-US;q=0.3,en;q=0.2");
		header.add("Accept-Encoding: gzip, deflate");
		header.add("Connection: keep-alive");
		header.add("Upgrade-Insecure-Requests: 1");
		HttpHeader httpHeader=parse(header);
		
		String host=httpHeader.host;
		String url=httpHeader.url;
		/*
		 * 获取代理服务器与服务器的Socket
		 */
		try {
			if (ConnectToServer(host, HttpPort, 5) == null) {
				return;
			}
		} catch (UnknownHostException e) {
		} catch (IOException e) {
		}
		System.out.println("url="+url);
		System.out.println("host="+host);
		
		boolean flag=cacheTime.containsKey(url)&&cacheBytes.containsKey(url);
		if (!flag) {
			/*
			 * 没有Cache的情况
			 */
			System.err.println("\n---------No Cache---------");
			SendToServer(header);
			SendBackToClient(url);
		} else {
			/*
			 * 有Cache的情况
			 */
			System.err.println("\n---------Cache in Memory---------");
			SendBackToClientWithCache(header, host,url);
		}


		
}		


	@Override
	public void run() {
		try {
			ClientSocket.setSoTimeout(timeout);
			String line = null;
			List<String> header = new ArrayList<>();
			/*
			 * 获取从客户端发送的请求信息
			 */
			line = ClientBufferReader.readLine();
			if (line == null) {
				return;
			}
			header.add(line);
			System.out.println("\n---------Request From Client---------");
			System.out.println(line);
			while (!(line = ClientBufferReader.readLine()).equals("")) {
				header.add(line);
				System.out.println(line);
			}
			
			//解析报文信息获取http信息
			HttpHeader httpHeader = parse(header);
			String url = httpHeader.url;
			String host = httpHeader.host;

			/*
			 * 钓鱼
			 */
			for (String h:guide.keySet()) {
				if (host.contains(h)) {
					phishing(header);
					return;
				}
			}
			
			if (httpHeader.method.equals("CONNECT")) {
				return;
			}

			/*
			 * 过滤网站
			 */
			for (int i=0;i<WebsiteFilter.size();i++) {
				if (host.contains(WebsiteFilter.get(i))) {
					Filter();
					System.err.println(url+"has been filterred");
					return;
				}
			}
			
						
			/*
			 * 获取代理服务器与服务器的Socket
			 */
			if (ConnectToServer(host, HttpPort, 5) == null) {
				return;
			}
			System.out.println("url="+url);
			System.out.println("host="+host);
			
			boolean flag=cacheTime.containsKey(url)&&cacheBytes.containsKey(url);
			if (!flag) {
				/*
				 * 没有Cache的情况
				 */
				System.err.println("\n---------No Cache---------");
				SendToServer(header);
				SendBackToClient(url);
			} else {
				/*
				 * 有Cache的情况
				 */
				System.err.println("\n---------Cache in Memory---------");
				SendBackToClientWithCache(header, host,url);
			}

		} catch (SocketException e) {
		} catch (IOException e) {
		}catch (Exception e){
			
		}

	}
}