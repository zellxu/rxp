import java.io.*;
import java.net.*;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

public class TCPServer {

	private static final HashMap<String, String> PASSWORD = new HashMap<String, String>();
	static{
		PASSWORD.put("user1", "pass1");
		PASSWORD.put("user2", "pass2");
		PASSWORD.put("user3", "pass3");
		PASSWORD.put("user4", "pass4");
		PASSWORD.put("user5", "pass5");
	}
	private static HashMap<String, String> challenge = new HashMap<String, String>();
    //private static final int MAX_PACKETSIZE = 128;
	private static final int RANDOM_STRING_LENGTH = 64;
	private static final int MD5_LENGTH = 32;
    private static final int DEFAULT_PORT = 1024;
	private static boolean debug = false;

    /****************************************************************************
     *  ----------------------------------------------------------------------  *
     *  | Short Hand | Message Type     | Response                            | *
     *  |------------|------------------|-------------------------------------| *
     *  |   REQ      | Request          | Send Random String                  | *
     *  |   CHA      | Random String    | xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx | *
     *  |   URH      | Username & Hash  | Compare result, send Authentication | *
     *  |   AUT      | Authentication   | xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx | *
     *   ---------------------------------------------------------------------  *
     *                                                                          *
     *  Message Format:                                                         *
     *  - REQUEST -                                                             *
     *  Index 0-2: Message Type (REQ)                                           *
     *                                                                          *
     *  - RANDOM STRING -                                                       *
     *  Index 0-2: Message Type (CHA)                                           *
     *  Index 3-66: Random String                                               *
     *                                                                          *
     *  - USERNAME & HASH                                                       *
     *  Index 0-2: Message Type (URH)                                           *
     *  Index 3:   Username length                                              *
     *  Index 3-[3+Username length]: Username                                   *
     *  Index [3+Username length]-[3+Username length + hash length]: Hash       *
     *                                                                          *
     *  - AUTHENTICATION -                                                      *
     *  Index 0-2: Message Type (AUT)                                           *
     *  Index 3:   Result (0=Fail, 1=Succeed)                                   *
     *                                                                          *
     ***************************************************************************/

	/**
	 * Parses the received message and generates the response accordingly.
	 * @param receivePacket	message received from the client
	 * @return	the response packet
	 * @throws NoSuchAlgorithmException
	 * @throws UnsupportedEncodingException
	 */
    private static String parseMessage(String message, String host, int port) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        if(message.length() < 3){
        	if(debug)
        		System.out.print("Received message too short. ");
        	return "AUT0\n";
        }

        String type = message.substring(0,3);
        String data = message.substring(3);
        StringBuilder sb = new StringBuilder();

        // Determine type of message and handle accordingly
        if (type.equals("REQ")) {
            sb.append("CHA");
            String random = generateRandomString();
            sb.append(random);
            challenge.put(host+port, random);
        }
        else if (type.equals("URH")) {
            int usernameLength = Character.getNumericValue(data.charAt(0));
            if(usernameLength > 15 || usernameLength < 1){
            	if(debug)
            		System.out.print("Received username has wrong length. ");
            	return "AUT0\n";
            }

            String username = data.substring(1, usernameLength+1);
            String password = PASSWORD.get(username);

            if(password == null){
            	if(debug)
            		System.out.print("Received username does not exist. ");
            	return "AUT0\n";
            }

            String random = challenge.get(host+port);
            if(random == null){
            	if(debug)
            		System.out.print("Received IP does not have a challenge string. ");
            	return "AUT0\n";
            }
            if(Long.parseLong(random.substring(RANDOM_STRING_LENGTH-14))-Long.parseLong(generateTimeStamp()) > 10){
            	if(debug)
            		System.out.print("Received IP has an expired challenge. ");
            	return "AUT0\n";
            }
            //Get the hash from user
            String userhash = data.substring(usernameLength+1, usernameLength+MD5_LENGTH+1);
			//Calculate the hash
            String serverhash = hash(username+password+random);
            if(!userhash.equals(serverhash)){
            	if(debug)
            		System.out.print("Received hash not correct. ");
            	return "AUT0\n";
            }
            sb.append("AUT1");

        } else {
        	if(debug)
        		System.out.print("Received wrong message type. ");
        	return "AUT0\n";
        }

        if (debug) {
            String timeStamp = new SimpleDateFormat("HH.mm.ss").format(new Date());
            System.out.println("["+timeStamp+"] "+"Sending Message: "+sb.toString()+" to "+host+":"+port);
        }
        sb.append('\n'); // add end of message character to message
        return sb.toString();
    }

	/**
     * Generates a random alphanumeric string of length 64.
     * @return a random alphanumeric string of length 64.
     */
    private static String generateRandomString() {
        String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";
		String time = generateTimeStamp();
        int length = RANDOM_STRING_LENGTH - time.length();
        StringBuilder sb = new StringBuilder();
        for (int i=0; i<length; i++)
            sb.append(chars.charAt((int)(Math.random()*chars.length())));
        sb.append(time);
        return sb.toString();
    }

    /**
     * Helper method for generating time stamps
     * @return formated time stamp
     */
    private static String generateTimeStamp(){
    	return new SimpleDateFormat("yyyyMMddHHmmss").format(new Timestamp(System.currentTimeMillis()));
    }

    /**
     * Generates a MD5 hash from a given string
     * @param s the String to generate MD5 from
     * @return generated MD5
     * @throws NoSuchAlgorithmException
     * @throws UnsupportedEncodingException
     */
    private static String hash(String s) throws NoSuchAlgorithmException, UnsupportedEncodingException {
    	MessageDigest md = MessageDigest.getInstance("MD5");
    	md.reset();
		byte[] digest = md.digest(s.getBytes("UTF-8"));
		StringBuffer sb = new StringBuffer();
		for (byte b : digest) {
			sb.append(String.format("%02x", b & 0xff));
		}
		return sb.toString();
    }

    public static void main(String args[]) throws Exception {
    	if(args.length > 2){
    		System.out.println("Too many arguments");
    		System.out.println("usage: TCPServer [-d] [port]");
    		System.exit(0);
    	}

    	int port=DEFAULT_PORT;
    	for(int i=0; i<args.length; i++){
    		switch (args[i].charAt(0)){
    		case '-':
    			if(args[i].charAt(1)!='d'){
    				System.out.println("Not a valid option: "+args[i]);
    				System.out.println("usage: TCPServer [-d] [port]");
    				System.exit(0);
    			}
    			debug = true;
    			break;
    		default:
    			if(args[i].matches("\\d+"))
    				port = Integer.parseInt(args[i]);
    			else{
    				System.out.println("Not a valid portNumber: "+args[i]);
    				System.out.println("usage: TCPServer [-d] [port]");
    				System.exit(0);
    			}
    		}
    	}
    	if(port < 1024 || port > 9999){
    		System.out.println("Invalid port number. Only 1024-9999.");
			System.exit(0);
    	}

        // Initiate server
        //TCPServer server = new TCPServer();
        ServerSocket serverSocket = new ServerSocket(port);

        // Keeps the server running
        while (true) {
            Socket connectionSocket = serverSocket.accept();
            if (debug)
                System.out.println("Handling client at " +
                 connectionSocket.getInetAddress().getHostAddress() + " on port " +
                 connectionSocket.getPort());
            new Thread(new ConnectedSocket(connectionSocket)).start();
        }
    }

    static private class ConnectedSocket implements Runnable{
    	Socket connectionSocket;

    	private ConnectedSocket(Socket connectionSocket){
    		this.connectionSocket = connectionSocket;
    		
    	}

		@Override
		public void run() {
			try{
                while (connectionSocket.isConnected()) {
                    String clientHost = connectionSocket.getInetAddress().getHostAddress();
                    // Receive, process, and respond
                    BufferedReader receiveBuffer = new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));
                    DataOutputStream sendBuffer = new DataOutputStream(connectionSocket.getOutputStream());

                    String receiveData = receiveBuffer.readLine();
                    if(receiveData.getBytes().length > 128)
                        connectionSocket.close();

                    if (debug) {
                        String timeStamp = new SimpleDateFormat("HH.mm.ss").format(new Date());
                        System.out.println("["+timeStamp+"] "+"Received Message: "+receiveData + " from " + clientHost+":"+connectionSocket.getPort());
                    }
                    sendBuffer.writeBytes(parseMessage(receiveData, clientHost, connectionSocket.getPort()));
                }
            }
            catch (Exception e) {
                try {
					connectionSocket.close();
				} catch (IOException e1) {
					return;
				}
            }
		}

    }
}
