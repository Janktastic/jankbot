package janktastic.jankbot;

import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.clients.Tv;
import dev.lavalink.youtube.clients.skeleton.Client;

//one time youtube login, prints a refresh token to put in config.json as youtubeOauthRefreshToken.
//use a burner google account, youtube may ban accounts used by bots.
//usage: java -cp jankbot.jar janktastic.jankbot.YoutubeLogin
public class YoutubeLogin {

  public static void main(String[] args) throws InterruptedException {
    YoutubeAudioSourceManager source = new YoutubeAudioSourceManager(true, new Client[] { new Tv() });
    //starts google's device login flow, youtube-source logs the url and code to enter
    source.useOauth2(null, false);

    long deadline = System.currentTimeMillis() + 15 * 60 * 1000;
    while (System.currentTimeMillis() < deadline) {
      String refreshToken = source.getOauth2RefreshToken();
      if (refreshToken != null && !refreshToken.isEmpty()) {
        System.out.println();
        System.out.println("Logged in. Add this to config.json:");
        System.out.println("  \"youtubeOauthRefreshToken\": \"" + refreshToken + "\"");
        System.exit(0);
      }
      Thread.sleep(2000);
    }
    System.out.println("Timed out waiting for the login to be completed.");
    System.exit(1);
  }
}
