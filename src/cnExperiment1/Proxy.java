package cnExperiment1;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class Proxy {
	private static ServerSocket ProxyServerSocket;
	private static List<String> UserFilter=new ArrayList<>();

	static boolean InitSocket(int port) {
		try {
			ProxyServerSocket=new ServerSocket(port);
			ProxyServerSocket.setSoTimeout(1000000);
		} catch (IOException e) {
			System.out.println("初始化ProxyServerSocket失败");
			return false;
		}
		return true;
		
	}

	static boolean UserFilterAdd() {
		UserFilter.add("127.0.0.1");
		UserFilter.add("1.1.1.1");
		UserFilter.add("2.2.2.2");
		return UserFilter.size()>0;
	}
	
	
	public static void main(String[] args) {
		int ProxyPort=10240;
		System.out.println("---------正在准备代理服务器---------");
		if (InitSocket(ProxyPort)) {
			System.out.println("开始监听端口"+ProxyPort);
		}
		
		
		//UserFilterAdd();
		
		while (true) {
			try {
				Socket socket=ProxyServerSocket.accept();
		    	
				String address=socket.getInetAddress().getHostAddress();
				for (int i=0;i<UserFilter.size();i++) {
					if (address.equals((UserFilter.get(i)))) {
						System.err.println("用户IP:"+address+"被屏蔽");
						System.exit(0);
					}
				}
				
				new Thread(new ProxyProcess(socket)).start();
				
			} catch (IOException e) {
				System.out.println("连接超时");
			}
		
		}
	}
}
