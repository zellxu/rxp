import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.Arrays;
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

	private final int UDP_BUFFERSIZE = 4096;
	private final int RXP_BUFFERSIZE = 8192;
	private final int RXP_DATASIZE = 1024;
	private final int RXP_HEADERSIZE = 12; 
	private final int ACKNOWLEDGEMENT = 4;
	private final int FLAG = 8; //which byte is for the flags in the header
	private final int WINDOW = 9; //which byte is for the window size in the header
	private final int CHECKSUM = 10;
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
		private final int[] crc_table = {
				0x0000, 0xC0C1, 0xC181, 0x0140, 0xC301, 0x03C0, 0x0280, 0xC241,
				0xC601, 0x06C0, 0x0780, 0xC741, 0x0500, 0xC5C1, 0xC481, 0x0440,
				0xCC01, 0x0CC0, 0x0D80, 0xCD41, 0x0F00, 0xCFC1, 0xCE81, 0x0E40,
				0x0A00, 0xCAC1, 0xCB81, 0x0B40, 0xC901, 0x09C0, 0x0880, 0xC841,
				0xD801, 0x18C0, 0x1980, 0xD941, 0x1B00, 0xDBC1, 0xDA81, 0x1A40,
				0x1E00, 0xDEC1, 0xDF81, 0x1F40, 0xDD01, 0x1DC0, 0x1C80, 0xDC41,
				0x1400, 0xD4C1, 0xD581, 0x1540, 0xD701, 0x17C0, 0x1680, 0xD641,
				0xD201, 0x12C0, 0x1380, 0xD341, 0x1100, 0xD1C1, 0xD081, 0x1040,
				0xF001, 0x30C0, 0x3180, 0xF141, 0x3300, 0xF3C1, 0xF281, 0x3240,
				0x3600, 0xF6C1, 0xF781, 0x3740, 0xF501, 0x35C0, 0x3480, 0xF441,
				0x3C00, 0xFCC1, 0xFD81, 0x3D40, 0xFF01, 0x3FC0, 0x3E80, 0xFE41,
				0xFA01, 0x3AC0, 0x3B80, 0xFB41, 0x3900, 0xF9C1, 0xF881, 0x3840,
				0x2800, 0xE8C1, 0xE981, 0x2940, 0xEB01, 0x2BC0, 0x2A80, 0xEA41,
				0xEE01, 0x2EC0, 0x2F80, 0xEF41, 0x2D00, 0xEDC1, 0xEC81, 0x2C40,
				0xE401, 0x24C0, 0x2580, 0xE541, 0x2700, 0xE7C1, 0xE681, 0x2640,
				0x2200, 0xE2C1, 0xE381, 0x2340, 0xE101, 0x21C0, 0x2080, 0xE041,
				0xA001, 0x60C0, 0x6180, 0xA141, 0x6300, 0xA3C1, 0xA281, 0x6240,
				0x6600, 0xA6C1, 0xA781, 0x6740, 0xA501, 0x65C0, 0x6480, 0xA441,
				0x6C00, 0xACC1, 0xAD81, 0x6D40, 0xAF01, 0x6FC0, 0x6E80, 0xAE41,
				0xAA01, 0x6AC0, 0x6B80, 0xAB41, 0x6900, 0xA9C1, 0xA881, 0x6840,
				0x7800, 0xB8C1, 0xB981, 0x7940, 0xBB01, 0x7BC0, 0x7A80, 0xBA41,
				0xBE01, 0x7EC0, 0x7F80, 0xBF41, 0x7D00, 0xBDC1, 0xBC81, 0x7C40,
				0xB401, 0x74C0, 0x7580, 0xB541, 0x7700, 0xB7C1, 0xB681, 0x7640,
				0x7200, 0xB2C1, 0xB381, 0x7340, 0xB101, 0x71C0, 0x7080, 0xB041,
				0x5000, 0x90C1, 0x9181, 0x5140, 0x9301, 0x53C0, 0x5280, 0x9241,
				0x9601, 0x56C0, 0x5780, 0x9741, 0x5500, 0x95C1, 0x9481, 0x5440,
				0x9C01, 0x5CC0, 0x5D80, 0x9D41, 0x5F00, 0x9FC1, 0x9E81, 0x5E40,
				0x5A00, 0x9AC1, 0x9B81, 0x5B40, 0x9901, 0x59C0, 0x5880, 0x9841,
				0x8801, 0x48C0, 0x4980, 0x8941, 0x4B00, 0x8BC1, 0x8A81, 0x4A40,
				0x4E00, 0x8EC1, 0x8F81, 0x4F40, 0x8D01, 0x4DC0, 0x4C80, 0x8C41,
				0x4400, 0x84C1, 0x8581, 0x4540, 0x8701, 0x47C0, 0x4680, 0x8641,
				0x8201, 0x42C0, 0x4380, 0x8341, 0x4100, 0x81C1, 0x8081, 0x4040,
		};

		private DatagramSocket serverSocket; //udp socket used to communicate with rxp_client
		private byte[] udp_buffer; //buffer used to put udp packets in
		private State state;
		private int s_next = 1; //the next sequence number while sending
		//s_next = (int)(Math.random()*1024);
		private int a_last = s_next; //last acknowledged sequence number while receiving
		private int s_expect; //expected sequence number for next packet
		Queue<DatagramPacket> packets; 
		Lock packets_lock;
		Condition packets_notempty;
		private int receiver_window_size;

		private int timeout;
		private long timer;

		/**
		 * Helper method for calculating the CRC16
		 * @param buffer the byte array to calculate CRC16
		 * @return CRC16 of the buffer
		 */
		private int crc16(final byte[] buffer) {
			int crc = 0x0000;
			for (byte b : buffer) 
				crc = (crc >>> 8) ^ crc_table[(crc ^ b) & 0xff];
			return crc;
		}

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

						if(System.currentTimeMillis()-timer > timeout*1000){
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
					create_data_packet();
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
			data[WINDOW] = (byte)1;
			data[CHECKSUM] = (byte)0;
			data[CHECKSUM+1] = (byte)0;
			byte[] crc = ByteBuffer.allocate(4).putInt(crc16(data)).array();
			System.arraycopy(crc, 2 , data, CHECKSUM, 2);
			return new DatagramPacket(data, data.length, host, net_port);
		}

		/**
		 * Parse the udp packet received. If packet contains application layer data,
		 * put the application layer data into the RxPSever receive buffer 
		 * @param receivePacket udp packet which contains a rxp packet
		 */
		private DatagramPacket parse(DatagramPacket receivePacket) {
			byte[] response = new byte[RXP_PACKETSIZE];
			byte[] data = receivePacket.getData();
			data = Arrays.copyOfRange(data, 0, RXP_PACKETSIZE);

			byte[] crc = new byte[4];
			System.arraycopy(data, CHECKSUM, crc, 2, CHECKSUM_SIZE);
			System.arraycopy(new byte[2], 0, data, CHECKSUM, CHECKSUM_SIZE);

			if(ByteBuffer.wrap(crc).getInt() != crc16(data))
				return null;

			
			print("SEQ"+ByteBuffer.wrap(data, 0, ACKNOWLEDGEMENT_SIZE).getInt());
			print("EXP"+s_expect);
			//check if the SEQ is what I expect
			if(state != State.CLOSED){
				if(ByteBuffer.wrap(data, 0, ACKNOWLEDGEMENT_SIZE).getInt() != s_expect)
					return null;
			}

			//check if any queued packets have been delivered
			if((data[FLAG]>>6 & 1)==1){
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
					packets_lock.unlock();
				}
			}

			switch(state){
			case CLOSED:
				//If SYN bit is set
				if((data[FLAG]>>7 & 1)==1){
					print("SYN received");
					state = State.SYN_RECEIVED;
					s_expect = toInt(Arrays.copyOfRange(data, 0, ACKNOWLEDGEMENT))+1;
					byte[] ack = ByteBuffer.allocate(ACKNOWLEDGEMENT_SIZE).putInt(s_expect).array();
					System.arraycopy(ack, 0, response, ACKNOWLEDGEMENT, ACKNOWLEDGEMENT_SIZE);
					//set SYN and ACK bit
					response[FLAG] = (byte) ((0x1<<7)|(0x1<<6));
					return pack(response);
				}
				break;	

			case SYN_RECEIVED:
				//if ACK 
				if((data[FLAG]>>6 & 1)==1 ){
					print("ACK received");
					s_expect = ByteBuffer.wrap(data, 0, ACKNOWLEDGEMENT_SIZE).getInt()+1;
					byte[] ack = ByteBuffer.allocate(ACKNOWLEDGEMENT_SIZE).putInt(s_expect).array();
					System.arraycopy(ack, 0, response, ACKNOWLEDGEMENT, ACKNOWLEDGEMENT_SIZE);
					response[FLAG] = (byte) (0x1<<6);
					
				}
			case CONNECTED:
				print("following");
				//data for upper layer
				if(true){
					//The upper_layer_data is the packet without the header part
					byte[] upper_layer_data = new byte[RXP_DATASIZE];
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

				//if FIN bit
				if(true){


				}

				//if ACK bit
				if(true){

				}

				//add data if send_buffer is not empty and ACK window allows 
				if(true){
					send_lock.lock();
					if(send_mark > 0){
						System.arraycopy(send_buffer, 0, response, RXP_HEADERSIZE, send_mark);
						send_mark = 0;
						send_notfull.signal();
					}
					send_lock.unlock();
				}
				break;

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
