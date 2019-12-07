import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class URLprocessing {

  public interface URLhandler {
    void takeUrl(String url);
  }

  public static URLhandler handler = new URLhandler() {
    public void takeUrl(String url) {
      System.out.println(url);
    }
  };

  /**
   * Parse the given buffer to fetch embedded links and call the handler to
   * process these links.
   * 
   * @param data
   *          the buffer containing the http document
   */
  public static void parseDocument(CharSequence data) {
	  /* Parse the document and find all valid urls */
	  
	  // Convert data to strings and isolate potential urls
	  String line = data.toString().replaceAll(">",">\n");
      String regex = "<[a|A].*[h|H][r|R][e|E][f|F]\\s*=\\s*(\"(.*)\"|\'(.*)\').*>";
      Pattern pattern = Pattern.compile(regex);
      Matcher matcher = pattern.matcher(line);

      // Iterate over all findings
      while (matcher.find()) {
          String url = matcher.group(1).replaceAll("\'","").replaceAll("\"","");
          try {
              new MyURL(url);
          } catch (IllegalArgumentException e) {
              continue;
          }
          if (url.contains("http://")) {
        	  handler.takeUrl(url);
          }
      }
  }

  public static void main(String[] args) {
	  String test = "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD XHTML 1.1//EN\" \"http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd\"><html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>Labs 1..2 - Implementation of a Multi Threaded Web Client</title><meta http-equiv=\"Content-Type\" content=\"text/html; charset=ISO-8859-1\"><link href=\"../td557.css\" type=\"text/css\" rel=\"stylesheet\"></head><body><div class=\"titre\">INF 557 &mdash; Labs 1..2</div><div class=\"sousTitre\">Implementation of a Multi Threaded Web Client</div><p><div class=\"sousTitre\">Lab 1</div><p><a href=\"http://moodle.polytechnique.fr/mod/assign/view.php?id=25010\">assignment in moodle</a> </p><p><a href=\"INF557-rc_1-1.php\">Upload Form</a></p><A HREF=\"status-rc_1.php\" target=\"_blank\">results will be here</A><p>Given solutions for Lab 1:<ul><li><a href=\"solutions_1/MyURL_basic.java\"><tt>MyURL_basic.java</tt></a></li><li><a href=\"solutions_1/MyURL_patterns.java\"><tt>MyURL_patterns.java</tt></a></li><li><a href=\"solutions_1/Xurl.java\"><tt>Xurl.java</tt></a></li></ul></p><p><div class=\"sousTitre\">Lab 2</div><p><a href=\"http://moodle.polytechnique.fr/mod/assign/view.php?id=25013\">assignment in moodle</a> </p><p>Given material: <!-- (all are in this <a href=\"transport.zip\">zip file</a>): --><p>for exercise 3:<ul><li><a href=\"material_2/URLprocessing.java\"><tt>URLprocessing.java</tt></a></li></ul></p><p>for exercise 4:<ul><li><a href=\"material_2/URLQueue.java\"><tt>URLQueue.java</tt></a></li><li><a href=\"material_2/ListQueue.java\"><tt>ListQueue.java</tt></a></li><li><a href=\"material_2/Wget.java\"><tt>Wget.java</tt></a></li></ul></p></body></html>";
	  URLprocessing.parseDocument(test);
  }

}
