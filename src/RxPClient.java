import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


public class RxPClient {

	/*********************************************************************************************
	 *  ---------------------------------------------------------------------------------------  *
	 *  |       0       |          1          |           2           |           3           |  *
	 *  |-------------------------------------------------------------------------------------|  *
	 *  |0|1|2|3|4|5|6|7|8|9|10|11|12|13|14|15|16|17|18|19|20|21|22|23|24|25|26|27|28|29|30|31|  *
	 *  |-------------------------------------------------------------------------------------|  *
	 *  |                                   Sequence Number                                   |  *
	 *  |-------------------------------------------------------------------------------------|  *
	 *  |                                 Acknowledge Number                                  |  *
	 *  |-------------------------------------------------------------------------------------|  *
	 *  |S|A|F|         |                      |                                              |  *
	 *  |Y|C|I|         |      Window Size     |                  Check Sum                   |  *
	 *  |N|K|N|         |                      |                                              |  *
	 *  |-------------------------------------------------------------------------------------|  *
	 *  |                                         Data                                        |  *
	 *  ---------------------------------------------------------------------------------------  *
	 *                                                                                           *
	 *********************************************************************************************/

	private final int UDP_BUFFERSIZE = 4096;
	private final int RXP_BUFFERSIZE = 8192;
	private final int RXP_DATASIZE = 1024;
	private final int RXP_HEADERSIZE = 12; 
	private final int FLAG = 8; //which byte is for the flags in the header
	private final int WINDOW = 9; //which byte is for the window size in the header

	private final int RXP_PACKETSIZE = RXP_DATASIZE+RXP_HEADERSIZE;
	private byte[] receive_buffer, send_buffer;
	private int receive_mark, send_mark; //used to mark next available space in the buffer
	private Lock receive_lock, send_lock; //used to lock the buffer
	private Condition receive_notfull, send_notfull, send_notempty;

	private InetAddress host;
	private int rxp_port, net_port;

	private String username, password;
	private int window_size;

	public RxPClient(int rxp_port, InetAddress host, int net_port) {
		// TODO Auto-generated constructor stub
		receive_buffer = new byte[RXP_BUFFERSIZE];
		send_buffer= new byte[RXP_BUFFERSIZE];
		this.rxp_port = rxp_port;
		this.host = host;
		this.net_port = net_port;
		receive_lock = new ReentrantLock();
		send_lock = new ReentrantLock();
		receive_notfull = receive_lock.newCondition();
		send_notfull = send_lock.newCondition();
		send_notempty = send_lock.newCondition();
		receive_mark = 0;
		send_mark = 0;
		window_size = 1;
		new Thread(new RxPSocket()).start();
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public void setWindowsize(int size){
		this.window_size = size;
	}

	/**
	 * Check the receive_buffer and return data if receive_buffer is not empty
	 * @return	data for application layer or null if receive_buffer is empty
	 */
	public byte[] receive() {
		if(receive_mark == 0)
			return null;

		receive_lock.lock();
		byte[] data = new byte[receive_mark];
		System.arraycopy(receive_buffer, 0, data, 0, receive_mark);
		receive_mark = 0;
		receive_notfull.signal();
		receive_lock.unlock();
		return data;
	}

	public void close() {
		// TODO Auto-generated method stub

	}

	/**
	 * Check the send_buffer and add data if send_buffer has space
	 * @param send_data
	 */
	public void send(byte[] send_data) {
		send_lock.lock();
		try {
			while(send_data.length+send_mark > RXP_BUFFERSIZE)
				send_notfull.await();
			System.arraycopy(send_data, 0, send_buffer, send_mark, send_data.length);
			send_mark += send_data.length;
			send_notempty.signal();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} finally{
			send_lock.unlock();
		}
	}

	/**
	 * This is the socket thread which communicate with the rxp_client
	 */
	private class RxPSocket implements Runnable {
		private DatagramSocket clientSocket;
		private byte[] udp_buffer;
		private State state;
		private int a_last; //last acknowledged sequence number
		private int s_next; //the next sequence number to send
		Queue<DatagramPacket> packets; 

		private int timeout;

		public void run() {
			state = State.CLOSED;
			udp_buffer = new byte[UDP_BUFFERSIZE];
			packets = new ArrayBlockingQueue<DatagramPacket>(64);
			timeout = 2000;

			try {
				clientSocket = new DatagramSocket(rxp_port);
			} catch (SocketException e) {
				e.printStackTrace();
			}

			new Thread(new Runnable(){
				public void run() {

				}}).start();

			while (true) {
				if(state == State.CLOSED){
					//Wait for connect() in FxA
					send_lock.lock();
							try {
								while(send_mark == 0){
									send_notempty.await();
								}
								System.out.println("RxPClient: initiating connection");
								byte[] data = new byte[RXP_PACKETSIZE];
								s_next = (int)(Math.random()*1024);
								//set SYN bit
								data[FLAG] = (byte) (0x1<<7);
								DatagramPacket packet = pack(data);
								send(packet);
							} catch (IOException | InterruptedException e) {
								e.printStackTrace();
							}finally{
								send_lock.unlock();
							}

				}

				try {
					create_data_packet();
					Arrays.fill(udp_buffer, (byte)0);
					DatagramPacket receivePacket = new DatagramPacket(udp_buffer, udp_buffer.length);
					clientSocket.setSoTimeout(timeout);
					clientSocket.receive(receivePacket); //check and receive a udp packet
					DatagramPacket response = parse(receivePacket);
					if(response == null)
						continue;
					clientSocket.send(response); //respond accordingly
				} catch (IOException e) {
					//If failed at connecting,
					if(state == State.CLOSED){
						send_mark = 0;
						System.out.println("RxPClient: Server didn't respond");
					}
				}
			}
		}

		private void send(DatagramPacket packet) {
			// TODO Auto-generated method stub

		}

		private void create_data_packet(){

		}

		/**
		 * Pack the packet into a DatagramPacket. There are a few steps.
		 * 1. Add sequence number
		 * 2. Add window size
		 * 3. Add checksum
		 * @param data The packet without sequence number, window size and check sum
		 */
		private DatagramPacket pack(byte[] data){
			byte[] seq = ByteBuffer.allocate(4).putInt(s_next).array();
			System.arraycopy(seq, 0, data, 0, seq.length);

			return null;
		}

		/**
		 * Parse the udp packet received. If packet contains application layer data,
		 * put the application layer data into the RxPClient receive buffer 
		 * @param receivePacket udp packet which contains a rxp packet
		 */
		private DatagramPacket parse(DatagramPacket receivePacket) {
			byte[] response = new byte[RXP_PACKETSIZE];
			byte[] data = receivePacket.getData();

			switch (state) {
			case CLOSED:
				break;

			case SYN_SENT:

				//When case change to CONNECTED, change timeout to be 500
				break;

			case CONNECTED:
				break;

			case FIN_SENT:
				break;

			}

			return new DatagramPacket(response, response.length, receivePacket.getAddress(), receivePacket.getPort());
		}
	}

	private enum State{CLOSED, SYN_SENT, CONNECTED, FIN_SENT}


}
