import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Scanner;

public class FxAClient {
	private final static int MAX_SIZE = 1024;
	private static Scanner scan = new Scanner(System.in);
	static RxPClient client; 
	
	public static void connect() {
		// Verify valid input
		System.out.println("Enter user name");
		String username = scan.nextLine();
		if(username.length()>15 || username.length() < 1){
			System.out.println("Invalid user name length. Only 1-15");
			return;
		}
		if(!username.matches("^[a-zA-Z0-9_]+$")){
			System.out.println("Invalid username. Aalphanumeric and underscores only.");
			return;
		}
		System.out.println("Enter password");
		String password = scan.nextLine();
		if(password.length()>15 || password.length() < 1){
			System.out.println("Invalid password length. Only 1-15");
			return;
		}
		if(!password.matches("^[a-zA-Z0-9_]+$")){
			System.out.println("Invalid password. Aalphanumeric and underscores only.");
			return;
		}
		client.setUsername(username);
		client.setPassword(password);
		client.send("CNT".getBytes());
		int counter = 0;
		while(true){
			byte[] receive = client.receive();
			if(receive == null){
				try {
					Thread.sleep(2000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				System.out.println("Connecting...");
				counter++;
				if(counter > 4){
					System.out.println("Error: Server not responding\nConnection failed");
					return;
				}
				continue;
			}
			if(new String(receive).equals("CACK")){
				System.out.println("Connected to server");
				break;
			}
		}
	}
	
	private static void close() {
		client.close();
	}

	public static void check(String f) throws IOException {
		String send = "CHK"+f;
		client.send(send.getBytes());
		int length, counter = 0;
		while(true){
			byte[] receive = client.receive();
			if(receive == null){
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				counter++;
				if (counter > 100){
					System.out.println("Error");
					return;
				}
				continue;
			}
			String l = new String(receive);
			if(!l.matches("\\d+")){
				System.out.println("Error: Server not responding");
				return;
			}
			length = Integer.parseInt(l);
			if(length == -1){
				System.out.println("File does not exist");
				return;
			}
			break;
		}
		System.out.println("File found, length "+length+"B, downloading...");
		get(f, length);
	}	
	
	public static void get(String f, int length) throws IOException {
		String send = "GET"+f;
		client.send(send.getBytes());
		FileOutputStream out = new FileOutputStream(f);
		int size = 0, counter = 0;
		while(size < length){
			byte[] receive = client.receive();
			if(receive == null){
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				counter++;
				if (counter > 100){
					System.out.println("Error: Server not responding");
					return;
				}
				continue;
			}
			out.write(receive);
			size += receive.length;
		}
		out.close();
		System.out.println("File successfully downloaded");
	}

	public static void main(String args[]) throws IOException {
		
		if(args.length == 0){
    		System.out.println("Too few arguments");
    		System.out.println("usage: FxAClient [RxP_Port] [NetEmu_IP] [NetEmu_Port]");
    		System.exit(0);
    	}
		if(args.length > 3){
    		System.out.println("Too many arguments");
    		System.out.println("usage: FxAClient [RxP_Port] [NetEmu_IP] [NetEmu_Port]");
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
		
		client = new RxPClient(rxp_port, host, net_port);
		
		System.out.println("Client started. Please enter command.");
		printUsage();
		String line;
		while (true) {
			System.out.print("Client@" + rxp_port + ">>>>> ");
			line = scan.nextLine().trim();
			if(line.isEmpty()){
				printUsage();
				continue;
			}
			if(line.toLowerCase().equals("connect"))
				connect();
			else if(line.toLowerCase().equals("close")){
				System.out.println("Client closed. Bye!");
				close();
				return;
			}
			else if(line.substring(0, 1).equals("W")){
				String[] split = line.split(" ");
				if(split.length != 2 || !split[1].matches("\\d+")){
					printError();
					continue;
				}
				client.setWindowsize(Integer.parseInt(split[1]));
			}
			else if(line.length()>2 && line.substring(0, 3).toLowerCase().equals("get")){
				String[] split = line.split(" ");
				if(split.length != 2){
					printError();
					continue;
				}
				check(split[1]);
			}
			else
				printError();
		}
	}

	private static void printError(){
		System.out.println("Can't understand input");
		printUsage();
	}
	
	private static void printUsage(){
		System.out.println("Usage: \n\tconnect\t\tconnect to the server\n"
				+ "\tget [F]\t\tget the file with name F from the server\n"
				+ "\tclose\t\tclose the program\n");
	}
}
