import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;


public class RxPServer {
	final int MAX_PACKETSIZE = 1024;
	DatagramSocket serverSocket;
	byte[] receiveData;
	InetAddress host;
	int net_port;
	
	public RxPServer(int rxp_port, InetAddress host, int net_port) throws SocketException {
		serverSocket = new DatagramSocket(rxp_port);
        receiveData = new byte[MAX_PACKETSIZE];
        this.host = host;
        this.net_port = net_port;
	}

	public byte[] receive() {
		// TODO Auto-generated method stub
		return null;
	}

	public void send(byte[] send_data) {
		// TODO Auto-generated method stub
		
	}

	
	

}
