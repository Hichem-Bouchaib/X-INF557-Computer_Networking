import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.CharBuffer;
import java.util.MissingResourceException;

public class Xurl {

  public static final boolean SHOW_HEADER = false;
  public static final boolean VERBOSE = false;
  public static final int CHUNKED = -1;
  public static final int UNSPECIFIED = -2;

  /**
   * Skip and parse remaining lines of the HTTP header until the terminating
   * empty line. Return the data length when specified in this header.
   * 
   * @param stream
   *          the reader from which the HTTP stream is read
   * @return the data length when specified by the Content-Length tag, or
   *         CHUNKED (-1) if 'chunked' Transfer-Encoding is set, or UNSPECIFIED
   *         (-2) when the server is expected to close the connection.
   */
  private static int parseHeader(BufferedReader stream) {
    int length = UNSPECIFIED;
    String line = null;
    do {
      try {
        line = stream.readLine();
      } catch (IOException e1) {
        e1.printStackTrace();
        System.exit(-9);
      }
      if (SHOW_HEADER)
        System.out.println(line);
      if (line == null) {
        System.err.println("can't get header");
        System.exit(-10);
      } else if (line.startsWith("Content-Length:")) {
        String[] words = line.split(" ");
        length = Integer.parseInt(words[1]);
      } else if (line.startsWith("Transfer-Encoding: chunked")) {
        length = CHUNKED;
      }
    } while (!"".equals(line)); // empty line at the end of header
    if (length == UNSPECIFIED)
      throw new MissingResourceException(
          "no content length policy specified in header", "Content", "Length");
    return length;
  }

  /**
   * Download the document part through an already open TCP connection. Header
   * is supposed partially parsed, but not length specification.
   * 
   * @param answerStream
   *          the input stream for the current TCP connection
   * @param fileName
   *          the name of a local file where the result will be stored
   */
  private static void download(BufferedReader answerStream, String fileName) {
    int length = parseHeader(answerStream);
    char[] buffer = new char[length > 0 ? length : 0]; // initial allocation
    // System.out.println("filename : \"" + fileName + "\"");
    //
    // readLine returns null when the end of the input stream has been reached,
    // this means that the server has shutdown the stream,
    // but it is not always the case ... or not always immediately ...
    // we should also consider the "Content-Length" information from the header
    // and also handle the case of chunked data
    // used when the server doesn't know the length at start, or
    // to accomodate with smaller buffer size.
    String line = "";
    int count = 0;
    boolean chunked = length == CHUNKED;
    // __Test__.assertFalse("chunked encoding not supported", chunked);
    try {
      PrintWriter file = new PrintWriter(fileName);
      while (line != null) {
        if (chunked) {
          line = answerStream.readLine();
          if (line.length() == 0)
            line = answerStream.readLine();
          length = Integer.parseInt(line, 16);
          if (VERBOSE)
            System.out.println("CHUNK LENGTH = " + length);
          count = 0;
          if (length == 0)
            break;
        }
        if (buffer.length < length)
          buffer = new char[length]; // size extended as needed
        while (count < length) {
          int n = answerStream.read(buffer, count, length - count);
          if (n < 0) { // reached EOF
            length = count;
            break;
          }
          count += n;
          // System.out.println(n + " " + count);
        }
        file.write(buffer, 0, length);
        // uncomment the next line for exercise 3
        URLprocessing.parseDocument(CharBuffer.wrap(buffer, 0, length));
        if (!chunked && count >= length)
          break;
      }
      file.flush();
      file.close();
    } catch (IOException e1) {
      e1.printStackTrace();
      System.exit(-11);
    }
    if (VERBOSE)
      System.out.println("END OF STREAM");
  }

  /**
   * Query and download an URL through an already open TCP connection.
   * 
   * @param requestedURL
   *          the requested (absolute) URL in raw form
   * @param requestedHost
   *          as given by the requested URL
   * @param proxyHost
   *          to be passed again when the query is redirected
   * @param proxyPort
   *          to be passed again when the query is redirected
   * @param queryStream
   *          the output stream for the current TCP connection
   * @param answerStream
   *          the input stream for the current TCP connection
   * @param fileName
   *          the name of a local file where the result will be stored
   */
  private static void doRequest(String requestedURL, String requestedHost,
      String proxyHost, int proxyPort, PrintStream queryStream,
      BufferedReader answerStream, String fileName) {
    // now we are speaking HTTP
    queryStream.print("GET " + requestedURL + " HTTP/1.1\r\n");
    queryStream.print("Host: " + requestedHost + "\r\n");
    queryStream.print("\r\n"); // an 'empty' line = "\r\n" only
    queryStream.flush();
    // now start receiving...
    if (SHOW_HEADER)
      System.out.println("====== HEADER ======");
    String line = null;
    // analyse first line of header
    try {
      line = answerStream.readLine();
      if (SHOW_HEADER)
        System.out.println(line);
      if (line == null) {
        System.err.println("can't get header");
        System.exit(-5);
        return; // not reached !
      }
      String[] tokens = line.split(" ");
      if (!tokens[0].startsWith("HTTP")) {
        System.err.println("bad answered protocol");
        System.exit(-6);
      }
      if ("301".equals(tokens[1]) || "302".equals(tokens[1])) {
        while (!line.startsWith("Location: ")) {
          try {
            line = answerStream.readLine();
            if (SHOW_HEADER)
              System.out.println(line);
          } catch (IOException e1) {
            e1.printStackTrace();
            System.exit(-9);
          }
        }
        String[] toks = line.split(" ");
        query(toks[1], fileName, proxyHost, proxyPort);
        return;
      } else if (!"200".equals(tokens[1])) {
        System.err
            .println("wrong status " + tokens[1] + " for " + requestedURL);
        return;
      }
    } catch (IOException e1) {
      e1.printStackTrace();
      System.exit(-8);
    }
    download(answerStream, fileName);
  }

  /**
   * Open a TCP connection and query the specified URL.
   * 
   * @param connectionHost
   *          the target of the TCP connection
   * @param connectionPort
   *          the target of the TCP connection
   * @param requestedURL
   *          the requested (absolute) URL in raw form
   * @param requestedHost
   *          as given by the requested URL
   * @param fileName
   *          the name of a local file where the result will be stored
   */
  public static void connectAndQuery(String connectionHost, int connectionPort,
      String requestedURL, String requestedHost, String fileName) {
    // we create an unconnected socket
    // no explicit binding, so the local address (local port number) is picked
    // by system
    Socket socket = new Socket();
    PrintStream queryStream = null;
    BufferedReader answerStream = null;
    // open the connection
    int timeout = 2000; // timeout on connection establishing
    try {
      // try connecting to the proxy (or server itself)
      // a finite timeout is specified
      socket.connect(new InetSocketAddress(connectionHost, connectionPort),
          timeout);
      // if we are here, then we are connected
      // we build useful streams to talk through the socket
      queryStream = new PrintStream(socket.getOutputStream());
      answerStream = new BufferedReader(
          new InputStreamReader(socket.getInputStream()));
    } catch (IOException e) {
      System.err.println(e);
      System.exit(-4);
    }
    doRequest(requestedURL, requestedHost, connectionHost, connectionPort,
        queryStream, answerStream, fileName);
    try {
      socket.shutdownOutput(); // close the other direction
      socket.close(); // close everything and release memory
    } catch (IOException e) {
      System.err.println(e);
      return;
    }
  }

  /**
   * Query the specified URL.
   * 
   * @param requestedURL
   *          the requested (absolute) URL in raw form
   * @param indicatedName
   *          the name of a local file where the result will be stored when not
   *          null, otherwise automatic naming rules apply when null
   * @param proxyHost
   *          the target host for the TCP connection when not null, otherwise
   *          the connection target (host and port) is picked from the given URL
   * @param proxyPort
   *          the target port for the TCP connection when a proxyHost is
   *          specified, otherwise the connection target (host and port) is
   *          picked from the given UR; a negative port number specifies the
   *          default HTTP port
   */
  public static void query(String requestedURL, String indicatedName,
      String proxyHost, int proxyPort) {
    // weird initialization to prevent a warning about potential null reference
    MyURL url = new MyURL("a://b/");
    try {
      url = new MyURL(requestedURL);
    } catch (IllegalArgumentException e) {
      System.err.println(e);
      System.exit(-2);
    }
    if (!"http".equals(url.getProtocol())) {
      System.err.println("unsupported protocol " + url.getProtocol());
      System.exit(-3);
    }
    if (VERBOSE)
      System.out.println("server name = " + url.getHost());
    int port = url.getPort();
    if (port < 0)
      port = 80;
    if (VERBOSE) {
      System.out.println("port = " + port);
      System.out.println("path = \"" + url.getPath() + '"');
    }
    String fileName = indicatedName;
    if (fileName == null) {
      fileName = "index";
      String[] names = url.getPath().split("/");
      if (!url.getPath().endsWith("/"))
        fileName = names[names.length - 1];
    }
    if (VERBOSE)
      System.out.println("file name = " + fileName);
    if (proxyHost == null) // case of a direct access
      connectAndQuery(url.getHost(), port, url.getPath(), url.getHost(),
          fileName);
    else if (proxyPort < 0)
      connectAndQuery(proxyHost, 80, requestedURL, url.getHost(), fileName);
    else
      connectAndQuery(proxyHost, proxyPort, requestedURL, url.getHost(),
          fileName);
  }

  /**
   * Query the specified URL.
   * 
   * @param requestedURL
   *          the requested (absolute) URL in raw form
   * @param proxyHost
   *          the target host for the TCP connection when not null, otherwise
   *          the connection target (host and port) is picked from the given URL
   * @param proxyPort
   *          the target port for the TCP connection when a proxyHost is
   *          specified, otherwise the connection target (host and port) is
   *          picked from the given UR; a negative port number specifies the
   *          default HTTP port
   */
  public static void query(String requestedURL, String proxyHost,
      int proxyPort) {
    query(requestedURL, null, proxyHost, proxyPort);
  }

  public static void main(String[] args) {
    if (args.length < 1) {
      System.err.println("Usage: java Xurl url [proxyHost proxyPort]");
      System.exit(-1);
    }
    String proxyHost = null;
    if (args.length > 1)
      proxyHost = args[1];
    int proxyPort = -1;
    if (args.length > 2)
      proxyPort = Integer.parseInt(args[2]);
    query(args[0], proxyHost, proxyPort);
  }

}
