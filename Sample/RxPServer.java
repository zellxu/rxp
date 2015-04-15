import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


public class RxPServer {

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

	
	private static final HashMap<String, String> PASSWORD = new HashMap<String, String>();
	static{
		PASSWORD.put("user1", "pass1");
		PASSWORD.put("user2", "pass2");
		PASSWORD.put("user3", "pass3");
		PASSWORD.put("user4", "pass4");
		PASSWORD.put("user5", "pass5");
	}
	private final int UDP_BUFFERSIZE = 4096;
	private final int RXP_BUFFERSIZE = 8192;
	private final int RXP_DATASIZE = 1024;
	private final int RXP_HEADERSIZE = 12; 
	private final int ACKNOWLEDGEMENT = 4;
	private final int FLAG = 8; //which byte is for the flags in the header
	private final int WINDOW = 9; //which byte is for the window size in the header
	private final int CHECKSUM = 10;
	private final int DATA = 12;
	private final int CHECKSUM_SIZE = 2;
	private final int ACKNOWLEDGEMENT_SIZE = 4;

	private final int RXP_PACKETSIZE = RXP_DATASIZE+RXP_HEADERSIZE;
	private byte[] receive_buffer, send_buffer; //buffer for application layer
	private int receive_mark, send_mark; //used to mark next available space in the buffer
	private Lock receive_lock, send_lock; //used to lock the buffer
	private Condition receive_notfull, send_notfull;

	private InetAddress host;
	private int rxp_port, net_port;

	public RxPServer(int rxp_port, InetAddress host, int net_port) throws SocketException {
		receive_buffer = new byte[RXP_BUFFERSIZE];
		send_buffer= new byte[RXP_BUFFERSIZE];
		this.rxp_port = rxp_port;
		this.host = host;
		this.net_port = net_port;
		receive_lock = new ReentrantLock();
		send_lock = new ReentrantLock();
		receive_notfull = receive_lock.newCondition();
		send_notfull = send_lock.newCondition();
		receive_mark = 0;
		send_mark = 0;
		new Thread(new RxPSocket()).start();
	}


	/**
	 * Check the receive_buffer and return data if receive_buffer is not empty
	 * @return	data for application layer or null if receive_buffer is empty
	 */
	public byte[] receive() {
		receive_lock.lock();
		if(receive_mark == 0){
			receive_lock.unlock();
			return null;
		}
		byte[] data = new byte[receive_mark];
		System.arraycopy(receive_buffer, 0, data, 0, receive_mark);
		receive_mark = 0;
		receive_notfull.signal();
		receive_lock.unlock();
		return data;
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
		} catch (InterruptedException e) {
			e.printStackTrace();
		} finally{
			send_lock.unlock();
		}
	}

	public void print(String string) {
		System.out.println("RxPServer: "+string);
	}

	/**
	 * This is the socket thread which communicate with the rxp_client
	 */
	private class RxPSocket implements Runnable{

		private DatagramSocket serverSocket; //udp socket used to communicate with rxp_client
		private byte[] udp_buffer; //buffer used to put udp packets in
		private State state;
		private int s_next = 1; //the next sequence number while sending
		//s_next = (int)(Math.random()*1024);
		private int a_last = s_next; //last acknowledged sequence number while receiving
		private int s_expect = 1 ; //expected sequence number for next packet
		Queue<DatagramPacket> packets; 
		Lock packets_lock;
		Condition packets_notempty;
		private int receiver_window_size;

		private int timeout;
		private long timer;
		
		private final int RANDOMSTRING_SIZE = 64;
		private String challenge;

		public void run() {
			state = State.CLOSED;
			udp_buffer = new byte[UDP_BUFFERSIZE];
			packets = new LinkedList<DatagramPacket>();
			timeout = 500;
			packets_lock = new ReentrantLock();
			packets_notempty = packets_lock.newCondition();

			try {
				serverSocket = new DatagramSocket(rxp_port);
			} catch (SocketException e) {
				e.printStackTrace();
			}

			new Thread(new Runnable(){
				public void run() {
					while(true){
						packets_lock.lock();

						try {
							while(packets.isEmpty())
								packets_notempty.await();
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						
						if(System.currentTimeMillis()-timer > timeout ){
							print("timeout on packet lost");
							try {

								DatagramPacket packet = packets.peek();
								if(packet!=null){
									serverSocket.send(packet);
									timer = System.currentTimeMillis();
								}
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
						packets_lock.unlock();
					}
				}}).start();


			while(true){
				try {
					if(state == State.CONNECTED || state == State.CLOSE_WAIT){
						DatagramPacket packet = create_data_packet();
						if(packet != null){
							send(packet);
						}
					}
					Arrays.fill(udp_buffer, (byte)0);
					DatagramPacket receivePacket = new DatagramPacket(udp_buffer, udp_buffer.length);
					serverSocket.setSoTimeout(timeout);
					serverSocket.receive(receivePacket); //check and receive a udp packet
					DatagramPacket response = parse(receivePacket);
					if(response == null)
						continue;
					send(response); //respond accordingly
				} catch (IOException e) {}
			}
		}

		private void send(DatagramPacket packet) {
			packets_lock.lock();
			try {
				serverSocket.send(packet);
				s_next++;
				packets.add(packet);
				packets_notempty.signal();
				if(timer==0)
					timer = System.currentTimeMillis();
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				packets_lock.unlock();
			}
		}

		private DatagramPacket create_data_packet(){
			send_lock.lock();
			if(send_mark > 0 && a_last+receiver_window_size > s_next){
				if(send_mark >= RXP_DATASIZE){
					byte[] response_data = new byte[RXP_DATASIZE];
					System.arraycopy(send_buffer, 0, response_data, 0, response_data.length);
					send_mark -= response_data.length;
					send_notfull.signal();
					send_lock.unlock();
					return pack(new byte[RXP_HEADERSIZE], response_data);
				}
				else{
					byte[] response_data = new byte[send_mark];
					System.arraycopy(send_buffer, 0, response_data, 0, response_data.length);
					send_mark = 0;
					send_notfull.signal();
					send_lock.unlock();
					return pack(new byte[RXP_HEADERSIZE], response_data);
				}
			}
			send_lock.unlock();
			return null;
		}

		/**
		 * Pack the packet into a DatagramPacket. There are a few steps.
		 * 1. Add sequence number
		 * 2. Add window size
		 * 3. Add checksum
		 * @param data The packet without sequence number, window size and check sum
		 * @param object 
		 */
		private DatagramPacket pack(byte[] header, byte[] data){
			byte[] packet;
			if(data == null){
				packet = header;
			}
			else{
				packet = new byte[header.length + data.length];
				System.arraycopy(header, 0, packet, 0, header.length);
				System.arraycopy(data, 0, packet, header.length, data.length);
			}
			
			byte[] seq = ByteBuffer.allocate(4).putInt(s_next).array();
			System.arraycopy(seq, 0, packet, 0, seq.length);
			packet[WINDOW] = (byte)1;
			packet[CHECKSUM] = (byte)0;
			packet[CHECKSUM+1] = (byte)0;
			byte[] ack = ByteBuffer.allocate(ACKNOWLEDGEMENT_SIZE).putInt(s_expect).array();
			System.arraycopy(ack, 0, packet, ACKNOWLEDGEMENT, ACKNOWLEDGEMENT_SIZE);
			
			byte[] crc = ByteBuffer.allocate(4).putInt(RxPUtil.crc16(packet)).array();
			System.arraycopy(crc, 2 , packet, CHECKSUM, 2);
			
			return new DatagramPacket(packet, packet.length, host, net_port);
		}

		/**
		 * Parse the udp packet received. If packet contains application layer data,
		 * put the application layer data into the RxPSever receive buffer 
		 * @param receivePacket udp packet which contains a rxp packet
		 */
		private DatagramPacket parse(DatagramPacket receivePacket) {
			byte[] response_header = new byte[RXP_HEADERSIZE];
			byte[] data = receivePacket.getData();
			data = Arrays.copyOfRange(data, 0, receivePacket.getLength());

			byte[] crc = new byte[4];
			System.arraycopy(data, CHECKSUM, crc, 2, CHECKSUM_SIZE);
			System.arraycopy(new byte[2], 0, data, CHECKSUM, CHECKSUM_SIZE);

			if(ByteBuffer.wrap(crc).getInt() != RxPUtil.crc16(data))
				return null;

			//check if the SEQ is what I expect
			int seq = ByteBuffer.wrap(data, 0, ACKNOWLEDGEMENT_SIZE).getInt();
			if(seq != s_expect)
				return null;
			s_expect = seq+1;
			
			
			
			
			//check if any queued packets have been delivered
			//if((data[FLAG]>>6 & 1)==1){
				int ack = toInt(Arrays.copyOfRange(data, ACKNOWLEDGEMENT, ACKNOWLEDGEMENT+ACKNOWLEDGEMENT_SIZE));
				if(ack > a_last){
					print(ack-a_last+" packets have been delivered");
					packets_lock.lock();
					for(int i=0; i<ack-a_last; i++){
						packets.remove();
						if(packets.isEmpty()){
							print("packets queue empty. reset timer");
							timer = 0;
							break;
						}
					}
					a_last = ack;
					packets_lock.unlock();
				}
			//}
			
			if(state != State.CLOSED && state != State.SYN_RECEIVED){
				byte[] window = new byte[4];
				window[3] = data[WINDOW];
				receiver_window_size = toInt(window);
			}

			switch(state){
			case CLOSED:
				//If SYN bit is set
				if((data[FLAG]>>7 & 1)==1){
					print("SYN received");
					state = State.SYN_RECEIVED;
					//set SYN and ACK bit
					response_header[FLAG] = (byte) ((0x1<<7)|(0x1<<6));
					byte[] random = RxPUtil.generateRandomString().getBytes();
					challenge = new String(random);
					return pack(response_header, random);
				}
				break;	

			case SYN_RECEIVED:
				//if ACK 
				if((data[FLAG]>>6 & 1)==1 ){
					print("Hash received");
					response_header[FLAG] = (byte) (0x1<<6);
					int username_length = data[DATA];
					String username = new String(Arrays.copyOfRange(data, DATA+1, DATA+username_length+1));
					String password = PASSWORD.get(username);
					String hash = RxPUtil.hash(username+password+challenge);
					if(!hash.equals(new String(Arrays.copyOfRange(data, DATA+username_length+1, DATA+username_length+hash.length()+1)))){
						print("Hash different. Dropping packet");
						return null;
					}
					state = State.CONNECTED;
					//set ACK bit
					response_header[FLAG] = (byte) (0x1<<6);
					return pack(response_header, null);
				}
			case CONNECTED:
				int data_length = receivePacket.getLength()-RXP_HEADERSIZE;
				if(data_length>0){
					//The upper_layer_data is the packet without the header part
					byte[] upper_layer_data = new byte[data_length];
					System.arraycopy(data, DATA, upper_layer_data, 0, upper_layer_data.length);
					receive_lock.lock();
					try {
						while(upper_layer_data.length+receive_mark > RXP_BUFFERSIZE)
							receive_notfull.await();
						System.arraycopy(upper_layer_data, 0, receive_buffer, receive_mark, upper_layer_data.length);
						receive_mark+=upper_layer_data.length;
					} catch (InterruptedException e) {
						e.printStackTrace();
					} finally {
						receive_lock.unlock();
					}
				}
				else if((data[FLAG]>>6 & 1)==1){
					return null;
				}
				
				send_lock.lock();
				if(send_mark > 0 && a_last+receiver_window_size > s_next){
					if(send_mark >= RXP_DATASIZE){
						byte[] response_data = new byte[RXP_DATASIZE];
						System.arraycopy(send_buffer, 0, response_data, 0, response_data.length);
						send_mark -= response_data.length;
						send_notfull.signal();
						send_lock.unlock();
						return pack(response_header, response_data);
					}
					else{
						byte[] response_data = new byte[send_mark];
						System.arraycopy(send_buffer, 0, response_data, 0, response_data.length);
						send_mark = 0;
						send_notfull.signal();
						send_lock.unlock();
						return pack(response_header, response_data);
					}
				}
				send_lock.unlock();
				return null;

			case CLOSE_WAIT:
				//send the rest in send_buffer and form a FIN rxp packet
			}

			//TODO Format the response and return
			return null;
			//return new DatagramPacket(response, response.length, receivePacket.getAddress(), receivePacket.getPort());
		}

	}

	private int toInt(byte[] b) {
		return ByteBuffer.wrap(b).getInt();
	}

	private enum State{CLOSED, SYN_RECEIVED, CONNECTED, CLOSE_WAIT};
}
