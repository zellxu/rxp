import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Arrays;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


public class RxPServer {
	private final int UDP_BUFFERSIZE = 4096;
	private final int RXP_BUFFERSIZE = 8192;
	private final int RXP_DATASIZE = 1024;
	private final int RXP_HEADERSIZE = 12; 
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
		try {
			while(true) {
				Thread t = new Thread(new RxPSocket());
				t.start();
				t.join();
			} 
		}catch (InterruptedException e) {
			e.printStackTrace();
		}
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

	/**
	 * This is the socket thread which communicate with the rxp_client
	 */
	private class RxPSocket implements Runnable{
		private DatagramSocket serverSocket; //udp socket used to communicate with rxp_client
		private byte[] udp_buffer; //buffer used to put udp packets in
		private State state;

		public void run() {
			
			state = State.CLOSED;
			udp_buffer = new byte[UDP_BUFFERSIZE];
			try {
				serverSocket = new DatagramSocket(rxp_port);
			} catch (SocketException e) {
				e.printStackTrace();
			}

			while(true){
				try {
					Arrays.fill(udp_buffer, (byte)0);
					DatagramPacket receivePacket = new DatagramPacket(udp_buffer, udp_buffer.length);
					serverSocket.receive(receivePacket); //check and receive a udp packet
					DatagramPacket response = parse(receivePacket);
					serverSocket.send(response); //respond accordingly
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}

		/**
		 * Parse the udp packet received. If packet contains application layer data,
		 * put the application layer data into the RxPSever receive buffer 
		 * @param receivePacket udp packet which contains a rxp packet
		 */
		private DatagramPacket parse(DatagramPacket receivePacket) {
			/*TODO set the bits and format the content of this message to be a rxp packet
			 * like the one in the design documents with the new changes like REQ bit or AUT bit.
			 */
			byte[] response = new byte[RXP_PACKETSIZE];
			byte[] data = receivePacket.getData();

			switch(state){
			//TODO If the server is closed, only accept packet with SYN bit set
			case CLOSED:
				//If SYN bit is set
				if((data[8]>>7 & 1)==1){

				}
				break;	

				//TODO If the server is connected, check if data is for upper layer or FIN or ACK
			case CONNECTED:
				//data for upper layer
				if(true){
					//The upper_layer_data is the packet without the header part
					byte[] upper_layer_data = new byte[RXP_DATASIZE]; //need to find out what our new header size is now for our new protocol design
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

			case FIN_WAIT:
				//send the rest in send_buffer and form a FIN rxp packet
			}


			//TODO Format the response and return
			return new DatagramPacket(response, response.length, receivePacket.getAddress(), receivePacket.getPort());

		}



	}

	private enum State{CLOSED, SYN_RECEIVED, CONNECTED, FIN_WAIT};
}
