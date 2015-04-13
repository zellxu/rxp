import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;


public class FxAServer {
	private final static int SPLIT_SIZE = 128;

	public static void main(String args[]) throws SocketException {
		if(args.length == 0){
    		System.out.println("Too few arguments");
    		System.out.println("usage: FxAServer [RxP_Port] [NetEmu_IP] [NetEmu_Port]");
    		System.exit(0);
    	}
		if(args.length > 3){
    		System.out.println("Too many arguments");
    		System.out.println("usage: FxAServer [RxP_Port] [NetEmu_IP] [NetEmu_Port]");
    		System.exit(0);
    	}
		if(!args[0].matches("\\d+")){
			System.out.println("Invalid RxP Port number. Only 1024-9999.");
			System.exit(0);
		}
		if(!args[2].matches("\\d+")){
			System.out.println("Invalid NetEmu Port number.");
			System.exit(0);
		}
		// Assign rxp_port
		int rxp_port = Integer.parseInt(args[0]);
		if(rxp_port < 1024 || rxp_port > 9999){
			System.out.println("Invalid RxP Port number. Only 1024-9999.");
			System.exit(0);
		}
		// Assign net_port
		int net_port = Integer.parseInt(args[2]);
		InetAddress host = null;
		try {
			host= InetAddress.getByName(args[1]);
		} catch (UnknownHostException e) {
			System.out.println("Unknown host name/IP");
			System.exit(0);
		}
		
		RxPServer server = new RxPServer(rxp_port, host, net_port);
		
		System.out.println("Server started. Listen for connection.");
		while (true) {
			byte[] received_data = server.receive();
			if(received_data == null){
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				continue;
			}
			byte[] send_data = parse(received_data);
			if(send_data.length/SPLIT_SIZE < 1){
				server.send(send_data);
				continue;
			}
			byte[] temp = new byte[SPLIT_SIZE];
			for(int i=0; i<send_data.length/SPLIT_SIZE; i++){
				System.arraycopy(send_data, i*temp.length, temp, 0, temp.length);
				server.send(temp);
			}
			temp = new byte[send_data.length%SPLIT_SIZE];
			System.arraycopy(send_data, send_data.length/SPLIT_SIZE*SPLIT_SIZE, temp, 0, temp.length);
			server.send(temp);
		}
	}


	private static byte[] parse(byte[] received_data) {
		String receive = new String(received_data);
		switch(receive){
		case "connect":
			
		}
		return null;
	}
	
}
