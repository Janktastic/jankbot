package janktastic.jankbot;

public class JankBotStringUtil {
  
  //discord codeblock markup for pretty printing bot responses
  private static final String CODEBLOCK = "```";
  
  //used to determine if request is a search term or a literal link 
  public static boolean isLink(String arg) {
    if (arg.startsWith("http://") || arg.startsWith("https://")) {
      return true;
    }
    return false;
  }
  
  //wraps string in discord codeblock markup ```text```
  public static String codeblock(String text) {
    return CODEBLOCK + text + CODEBLOCK;
  }
}
