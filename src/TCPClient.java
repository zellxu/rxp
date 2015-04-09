import java.net.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.io.*;
import java.util.Date;
import java.text.SimpleDateFormat;

public class TCPClient {

    //private static final int PACKETSIZE = 128;
	private static final int RANDOM_STRING_LENGTH = 64;
    private static String username;
    private static String password;
    private static boolean debug = false;
    private static final int DEFAULT_PORT = 1024;

    /****************************************************************************
     *   ---------------------------------------------------------------------  *
     *  | Short Hand | Message Type     | Response                            | *
     *  |------------|------------------|-------------------------------------| *
     *  |   REQ      | Request          | xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx | *
     *  |   CHA      | Random String    | Send URH                            | *
     *  |   URH      | Username & Hash  | xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx | *
     *  |   AUT      | Authentication   | Determine Result                    | *
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
     * @param message Message received from server
     * @return  response message
     * @throws NoSuchAlgorithmException
     * @throws UnsupportedEncodingException
     */
    private String parseMessage(String message) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        if(message.length() < 4){
        	if(debug)
        		System.out.print("Received message too short. ");
        	System.exit(0);
        }

        String type = message.substring(0,3);
        String data = message.substring(3);
        StringBuilder sb = new StringBuilder();

        // Determine type of message and handle accordingly
        if (type.equals("CHA")) {
            String hash = hash(username+password+data.substring(0,RANDOM_STRING_LENGTH));
            sb.append("URH");
            sb.append(username.length());
            sb.append(username);
            sb.append(hash);
        } else if (type.equals("AUT")) {
            int result = Character.getNumericValue(data.charAt(0));
            if (result==1)
                System.out.println("Welcome to our service.");
            else
                System.out.println("User authorization failed.");
            System.exit(0);
        } else {
            System.out.println("Cannot understand message.");
            System.exit(0);
        }

        if (debug) {
            String timeStamp = new SimpleDateFormat("HH.mm.ss").format(new Date());
            System.out.println("["+timeStamp+"] "+"Sending Message: "+sb.toString());
        }
        sb.append('\n'); // add end of message character to message
        return sb.toString();
    }

    /**
     * Generates a MD5 hash from a given string
     * @param s the String to generate MD5 from
     * @return generated MD5
     * @throws NoSuchAlgorithmException
     * @throws UnsupportedEncodingException
     */
    private String hash(String s) throws NoSuchAlgorithmException, UnsupportedEncodingException {
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
    	if(args.length > 4 || args.length < 3){
    		System.out.println("Wrong number of arguments");
    		System.out.println("usage: TCPClient [-d] [host:port] [username] [password]");
    		System.exit(0);
    	}

    	int off = 0;
    	if (args[0].charAt(0) == '-'){
    		if(args[0].charAt(1)!='d'){
				System.out.println("Not a valid option: "+args[0]);
				System.out.println("usage: TCPClient [-d] [host:port] [username] [password]");
				System.exit(0);
			}
			debug = true;
			off++;
    	}
        String delimiter = "[:]+";
        String[] tokens = args[0+off].split(delimiter);
        String hostStr = tokens[0];
        InetAddress host = InetAddress.getByName(hostStr);
        int port = DEFAULT_PORT;
        if(tokens.length > 1 && tokens[1].matches("\\d+"))
        	port = Integer.parseInt(tokens[1]);
        else if(debug)
        	System.out.println("Using default port number 1024");
        if(port < 1024 || port > 9999){
    		System.out.println("Invalid port number. Only 1024-9999.");
			System.exit(0);
    	}

        username = args[1+off];
        if(username.length()>15 || username.length() < 1){
        	System.out.println("Invalid user name length. Only 1-15");
        	System.exit(0);
        }
        if(!username.matches("^[a-zA-Z0-9_]+$")){
        	System.out.println("Invalid username. Aalphanumeric and underscores only.");
        	System.exit(0);
        }
        password = args[2+off];
        if(password.length()>15 || password.length() < 1){
        	System.out.println("Invalid password length. Only 1-15");
        	System.exit(0);
        }
        if(!password.matches("^[a-zA-Z0-9_]+$")){
        	System.out.println("Invalid password. Aalphanumeric and underscores only.");
        	System.exit(0);
        }

        // Initiate client
        TCPClient client = new TCPClient();
        Socket clientSocket = new Socket(host, port);
        BufferedReader receiveBuffer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        DataOutputStream sendBuffer = new DataOutputStream(clientSocket.getOutputStream());

        // Check connection
        if (!clientSocket.isConnected()) {
            System.out.println("Connection is not established. Please try again.");
            clientSocket.close();
            System.exit(0);
        }

        // Send request message
        String request = "REQ\n";
        sendBuffer.writeBytes(request);
        if(debug){
            String timeStamp = new SimpleDateFormat("HH.mm.ss").format(new Date());
        	System.out.println("["+timeStamp+"] "+"Sending Message: REQ to " +host+":"+port);
        }
        // Keep it running until authentication message is received.
        String receiveData = "";
        while(true){
            receiveBuffer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            sendBuffer = new DataOutputStream(clientSocket.getOutputStream());
	        	receiveData = receiveBuffer.readLine();
                if (debug) {
                    String timeStamp = new SimpleDateFormat("HH.mm.ss").format(new Date());
                    System.out.println("["+timeStamp+"] "+"Received Message: "+receiveData+" from "+ host+":"+port);
                }
	        sendBuffer.writeBytes(client.parseMessage(receiveData));
            if (receiveData.substring(0,3).equals("AUT")) {
                clientSocket.close();
                break;
            }
        }
    }
}
