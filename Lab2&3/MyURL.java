import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MyURL {
  private final String protocol;
  private final String host;
  private final int port;
  private final String path;

  public MyURL(String url) {
    if (url == null)
      throw new IllegalArgumentException("Invalid url: " + url);
    String scheme = "(\\p{Alpha}[\\p{Alnum}+-.]*)";
    String domain = "(?:\\p{Alnum}(?:[\\p{Alnum}-]*\\p{Alnum})\\.)*";
    String top_label = "(?:\\p{Alpha}(?:[\\p{Alnum}-]*\\p{Alnum})?)";
    String opt_dot = "(?:\\.)?";
    String hostname = "(" + domain + top_label + opt_dot + ")?";
    String opt_port = "(?::(\\p{Digit}+))?";
    String authority = hostname + opt_port;
    // String full_path = "((/[\\p{Graph}^[/?#]]*)*/?)"; // allows empty path
    // RFC3986 allows empty path (i.e. no trailing /)
    // here, a leading / is required
    String full_path = "(/(?:[\\p{Graph}^[/?#]]*/)*(?:[\\p{Graph}^[/?#]]*)?)";
    String reg_exp = "^" + scheme + "://" + authority + full_path + "$";
    Pattern pattern = Pattern.compile(reg_exp);
    Matcher matcher = pattern.matcher(url);
    if (matcher.find()) {
      protocol = matcher.group(1);
      host = matcher.group(2);
      String s = matcher.group(3);
      try {
        port = s == null ? -1 : Integer.parseInt(s);
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("Invalid port number: " + s);
      }
      path = matcher.group(4);
    } else {
      throw new IllegalArgumentException("Malformed URL : " + url);
    }
  }

  public String getProtocol() {
    return protocol;
  }

  public String getHost() {
    return host;
  }

  public int getPort() {
    return port;
  }

  public String getPath() {
    return path;
  }

  @Override
  public String toString() {
    return protocol + "://" + host + (port >= 0 ? ":" + port : "") + path;
  }
}
