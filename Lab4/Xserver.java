import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.Math.toIntExact;

class Blade implements Runnable {
	/* Blade will be run by a thread and is conceived to be adapted easily to exercise 2 */

	// Client properties
	private Socket client;            // Client socket
	private String url;               // Requested URL as string
	private String path;              // Requested path
	private String date;              // Date of the request
	private boolean rootPath;         // true <=> the required path is "/"
	private boolean validRequest;     // true <=> the request is a valid GET request
	private boolean validHost;        // true <=> the Host is valid
	private PrintStream outStream;    // Output stream used to write the response

	// Server properties
	private String serverName;        // Basically set to "Antoine Carossio"
	private int serverPort;           // Port of the server
	private String serverHost;        // Requested host
	private boolean reading;        // true <=> the server needs to listen further requests
	private FileInputStream inStream; // Input stream Used to read the request
	private BufferedReader buffer;    // Input buffer to read the request

	public Blade(Socket client, int serverPort){
		/* Initialisation of the properties */

		this.client       = client;
		this.serverPort   = serverPort;
		this.serverHost   = "localhost";
		this.rootPath     = false;
		this.validRequest = false;
		this.validHost    = false;
		this.reading    = true;
		this.serverName   = "Server: Antoine Carossio";
		this.date         = "Date: "+ new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z").format(Calendar.getInstance().getTime());
	}

	private String generateHTMLPage(String title, String message) {
		String page = String.join("\r\n"
		         , "<!DOCTYPE html>"
		         , "<html>"
		         , "	<head>"
		         , "		<title>"+title+"</title>"
		         , "	</head>"
		         , "<body>"
		         , message
		         , "</body>"
		         , "</html>"
		);

		return page;
	}

	private String generateHeader(long pageLen, String status) {
		/* Write header for requested file of a certain length */

		String header = String.join("\r\n"
				 , status
		         , date
		         , serverName
		         , "Content-Length: "+pageLen
		         , "Connection: close\n"
		);
		
		return header;
	}

	private void writeResponse(String title, String message, String status) {
		/* Write response to the outStream */

		String page = generateHTMLPage(title, message);
		String header = generateHeader(page.length(), status);
		outStream.println(header);
		outStream.println(page);
		reading = false;
		System.out.println(status);
	}

	private void welcomeResponse() {
		/* Write response for the default welcome message */
		writeResponse("200 OK", "Welcome to the server.", "HTTP/1.1 200 OK");
	}

	private void badRequestResponse() {
		/* Write response for a 400 Bad Request */
		writeResponse("400 Error", "Your request looks invalid.", "HTTP/1.1 400 Bad Request");
	}

	private void notFoundResponse() {
		/* Write response for a 404 Not Found */
		writeResponse("404 Error", "The file you are looking for does not exist here.", "HTTP/1.1 404 Not Found");
	}

	private void checkHostValidity(String line) {
		/* Check the host is valid (typically that it is equal to 'localhost') */

		Pattern pattern = Pattern.compile("[H|h][O|o][S|s][T|t]:([^:]+)[:.+]?");
		Matcher matcher = pattern.matcher(line);
		if (matcher.find()) {
			String requiredHost = matcher.group(1).replaceAll(" ","");
			if (requiredHost.equals(serverHost)) {
				validHost = true;
			} else {
				badRequestResponse();
				reading = false;
			}
		} else {
			badRequestResponse();
		}
	}
	
	private boolean checkPathValidity(String url) {
		/* Check the validity of the URL and extract the path of the requested file */

		Pattern pattern = Pattern.compile("/(.*)");
		Matcher matcher = pattern.matcher(url);
		
		if (url.equals("/")){
			path = "/";
			rootPath = true;
			return true;
		} else if (matcher.find()) { 
			path = matcher.group(1);
			return true;
		}
		return false;

	}

	private void checkRequestValidity(String line) {
		/* Check the request is a valid GET request */

		Pattern pattern = Pattern.compile("GET\\s(.*)\\sHTTP/1\\.1");
		Matcher matcher = pattern.matcher(line);
		System.out.println(line);
		if (matcher.find()) {
			url = matcher.group(1);
			if (checkPathValidity(url)) {
				validRequest = true;
			} else {
				badRequestResponse();
				reading = false;
			}
		} else {
			badRequestResponse();
			reading = false;
		}
	}

	private void serveFile() {
		/* Serve the required file, and return a Not Found error if does not exist */

		File file = new File(path);
		long pageLen = file.length();
		byte[] fileContent = new byte[toIntExact(pageLen)]; // Convert a long to int, raising an error in case of overflow

		try {
		    inStream = new FileInputStream(file);
			inStream.read(fileContent);
			inStream.close();
		} catch (FileNotFoundException e) {
			System.out.println(path);
			notFoundResponse();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		String[] fileContentByLine = new String(fileContent).split("\r\n");
		String header = generateHeader(pageLen, "HTTP/1.1 200 OK");
		outStream.println(header);
		for (int i=0; i<fileContentByLine.length; i++) {
			outStream.println(fileContentByLine[i]);
		}
		reading = false;
	}

	private void closeConnection() throws IOException {
		/* Closes all the connections and opened streams or buffers */

		outStream.close();
		buffer.close();
		client.close();
	}

	@Override
	public void run() {
		/* Main function of the Thread, designed to easily handle the parallel version in exercise 2 */
		System.out.println("Request handled by thread "+ Thread.currentThread());
		try{
			outStream = new PrintStream(client.getOutputStream());
			buffer = new BufferedReader(new InputStreamReader(client.getInputStream()));
			while(reading){ // Continue reading and reading lines while there is no error (bad request)
				String line =  buffer.readLine();
				if (!validRequest) {
					checkRequestValidity(line);
				} else {
					if (!validHost) {
						checkHostValidity(line);
					} else {
						if (rootPath) {
							welcomeResponse();
						} else {
							serveFile(); // In case the path is "/", write the default response
						}
					}
				}
			}
			closeConnection();
		}catch(Exception e){
			e.printStackTrace();
		}
	}

}

public class Xserver {
	/* Xserver will run a thread pool managed by an executor */

	private static int serverPort;
	private static ServerSocket server;
	private static Socket client;
	private static ExecutorService exec = Executors.newFixedThreadPool(42);

	private static void handleConnection(Socket socket) throws IOException, InterruptedException {
		/* Handle the connection thanks to the executor */
		
		Runnable r = new Blade(socket, serverPort);
		Thread t = new Thread(r);
		exec.execute(t);
	}

	public static void main(String[] args){
		/* Launch the server and wait for a connection from the client */

		if (args.length == 1) {
			try {
				while (true) { // The server must be stopped manually with ^C
					serverPort = Integer.parseInt(args[0]);
					server = new ServerSocket(serverPort, 1337); // Creates a server socket and binds it to the specified local port number, with the specified backlog.
	
					do {
						client = server.accept(); 
					} while (client == null);
	
					System.out.println("New connection");
					handleConnection(client); // Handle the connection with the client
					server.close();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else {
			System.err.println("Xserver only takes 1 argument, but "+args.length+" given: java Xserver serverPort");
		}
	}
}