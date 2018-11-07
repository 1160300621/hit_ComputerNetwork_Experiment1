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
�ͻ����ϵ�ʹ��
1.getInputStream�������Եõ�һ�����������ͻ��˵�Socket�����ϵ�getInputStream�����õ���������ʵ���Ǵӷ������˷��ص����ݡ�
2.getOutputStream�����õ�����һ����������ͻ��˵�Socket�����ϵ�getOutputStream�����õ����������ʵ���Ƿ��͸��������˵����ݡ�
���������ϵ�ʹ��
1.getInputStream�����õ�����һ��������������˵�Socket�����ϵ�getInputStream�����õ�����������ʵ���Ǵӿͻ��˷��͸��������˵���������
2.getOutputStream�����õ�����һ�������������˵�Socket�����ϵ�getOutputStream�����õ����������ʵ���Ƿ��͸��ͻ��˵����ݡ�
 */
public class ProxyProcess implements Runnable {
	// http�˿ڣ�Ĭ��Ϊ80
	static int HttpPort = 80;
	static int size=100000;

	// ��ʱʱ��
	static int timeout = 500000;

	//�ͻ��˺ʹ����������socket
	private Socket ClientSocket = null;
	private Socket ProxyClientSocket = null;

	//socket��д��д��������װ
	private InputStream ClientInputStream = null;
	private InputStream ProxyInputStream = null;
	private BufferedReader ClientBufferReader = null;
	private BufferedReader ProxyBufferReader = null;
	private OutputStream ClientOutputStream = null;
	private OutputStream ProxyOutputStream = null;
	private PrintWriter ClientPrintWriter = null;
	private PrintWriter ProxyPrintWriter = null;

	// ���󱻻���ľ���ʱ��
	static Map<String, String> cacheTime = new HashMap<>();

	// ���󱻻���ľ�������
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
	 * ����httpͷ����Ϣ����ȡmethod��url��host��cookie
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
	 * ��ȡ����������ͷ������׽���
	 * @param host ������
	 * @param port �˿�
	 * @param times ���Ӵ���
	 * @return ������������������Sockek
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
	 * ��������������������������Ϣ
	 * @param lst ������Ϣ
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
	 * û�л��������£�����������ӷ�����ת����Ӧ��Ϣ���ͻ���
	 * @param url
	 * @return
	 */
	public boolean SendBackToClient(String url) {
		/*
		 * �������bytes���飬��������ASCII����unicode����Ĳ��죬�޷�ʶ��
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
	 * �����ַ�����ȡ���е�Dateʱ��
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
	 * �л��������£����ͻ�����Ҫ����Ϣ
	 * 1.�ڻ���ʱ��������û���޸Ķ����򽫻���ֱ�ӷ��͸��ͻ���
	 * 2.�ڻ���ʱ���������޸Ķ����ˣ���Sever���¶����͸��ͻ���
	 * @param header
	 * @param host
	 * @param url
	 * @return
	 */
	public boolean SendBackToClientWithCache(List<String> header,String host, String url) {
		String modifiTime=cacheTime.get(url);
		// ����ȷ���Ƿ��޸ĵı��ĵ�������
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
			// ����������ڻ���ʱ���δ�޸Ķ���ֱ��ת�����ͻ��˻���
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
			//����޸Ĺ��������µĶ����ֽڷ����ͻ���
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
			ClientPrintWriter.write("��������\t");
			if (i%11==0) {
				ClientPrintWriter.write("\r\n");
			}
		}
			//ClientPrintWriter.write("��������\t��������		��������		��������		��������		��������\r\n");
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
		 * ��ȡ������������������Socket
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
			 * û��Cache�����
			 */
			System.err.println("\n---------No Cache---------");
			SendToServer(header);
			SendBackToClient(url);
		} else {
			/*
			 * ��Cache�����
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
			 * ��ȡ�ӿͻ��˷��͵�������Ϣ
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
			
			//����������Ϣ��ȡhttp��Ϣ
			HttpHeader httpHeader = parse(header);
			String url = httpHeader.url;
			String host = httpHeader.host;

			/*
			 * ����
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
			 * ������վ
			 */
			for (int i=0;i<WebsiteFilter.size();i++) {
				if (host.contains(WebsiteFilter.get(i))) {
					Filter();
					System.err.println(url+"has been filterred");
					return;
				}
			}
			
						
			/*
			 * ��ȡ������������������Socket
			 */
			if (ConnectToServer(host, HttpPort, 5) == null) {
				return;
			}
			System.out.println("url="+url);
			System.out.println("host="+host);
			
			boolean flag=cacheTime.containsKey(url)&&cacheBytes.containsKey(url);
			if (!flag) {
				/*
				 * û��Cache�����
				 */
				System.err.println("\n---------No Cache---------");
				SendToServer(header);
				SendBackToClient(url);
			} else {
				/*
				 * ��Cache�����
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