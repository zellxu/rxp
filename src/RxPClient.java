import java.net.InetAddress;


public class RxPClient {
    private final int UDP_BUFFERSIZE = 4096;
    private final int RXP_BUFFERSIZE = 8192;
    private final int RXP_DATASIZE = 1024;
    private final int RXP_HEADERSIZE = 12; 
    private final int RXP_PACKETSIZE = RXP_DATASIZE+RXP_HEADERSIZE;
    private byte[] receive_buffer, send_buffer;
    private int receive_mark, send_mark; //used to mark next available space in the buffer
    private Lock receive_lock, send_lock; //used to lock the buffer
    private Condition receive_notfull, send_notfull;

    private InetAddress host;
    private int rxp_port, net_port;

    
    public RxPClient(int rxp_port, InetAddress host, int net_port) throws SocketException{
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

	public void send(byte[] bytes) {
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

    private class RxPSocket implements Runnable {
        private DatagramSocket clientSocket;
        private byte[] udp_buffer;
        private State state;

        public void run() {
            state = State.CLOSED;
            udp_buffer = new byte[UDP_BUFFERSIZE];
            try {
                clientSocket = new DatagramSocket(rxp_port);
            } catch (SocketException e) {
                e.printStackTrace();
            }

            while (true) {
                try {
                    Arrays.fill(udp_buffer, (byte)0);
                    DatagramPacket receivePacket = new DatagramPacket(udp_buffer, udp_buffer.length);
                    clientSocket.receive(receivePacket); //check and receive a udp packet
                    DatagramPacket response = parse(receivePacket);
                    clientSocket.send(response); //respond accordingly
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }

        private DatagramPacket parse(DatagramPacket receivePacket) {
            byte[] response = new byte[RXP_PACKETSIZE];
            byte[] data = receivePacket.getData();

            switch (state) {
                case CLOSE:
                break;

                case SYN_SENT:
                break;

                case CONNECT:
                break;

                case FIN_SENT:
                break;

            }

            return new DatagramPacket(response, response.length, receivePacket.getAddress(), receivePacket.getPort());
        }
    }

    private enum State{CLOSE, SYN_SENT, CONNECT, FIN_SENT};
}
