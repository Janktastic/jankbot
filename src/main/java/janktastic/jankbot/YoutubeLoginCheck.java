package janktastic.jankbot;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;

import dev.lavalink.youtube.YoutubeAudioSourceManager;

//explains why an age restricted video failed, age restricted videos only play through the youtube login
public class YoutubeLoginCheck {

  public static String ageRestrictedMessage(AudioPlayerManager playerManager, String title) {
    YoutubeAudioSourceManager youtube = playerManager.source(YoutubeAudioSourceManager.class);
    String refreshToken = youtube == null ? null : youtube.getOauth2RefreshToken();
    if (refreshToken == null || refreshToken.isBlank()) {
      return title + " is age restricted, which needs a YouTube login (youtubeOauthRefreshToken in config.json).";
    }
    try {
      //same call as at startup: throws if the token is invalid, and re-enables the login if it only failed temporarily
      youtube.useOauth2(refreshToken, true);
    } catch (RuntimeException e) {
      System.out.println("YouTube login failed while playing age restricted video: " + e.getMessage());
      return title + " is age restricted and the YouTube login failed, the refresh token may need to be updated.";
    }
    return title + " is age restricted and couldn't be played even though the YouTube login works, check the bot log.";
  }

  public static boolean isAgeRestricted(Throwable exception) {
    //youtube-source reports every client's failure in the message, check the whole cause chain
    for (Throwable t = exception; t != null; t = t.getCause()) {
      String message = t.getMessage() == null ? "" : t.getMessage().toLowerCase();
      if (message.contains("age verification") || message.contains("confirm your age") || message.contains("age restrict")
          || message.contains("inappropriate for some users")) {
        return true;
      }
    }
    return false;
  }
}
