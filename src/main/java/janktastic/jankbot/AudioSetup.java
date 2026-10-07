package janktastic.jankbot;

import java.util.ArrayList;
import java.util.List;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;

import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.YoutubeSourceOptions;
import dev.lavalink.youtube.clients.Tv;
import dev.lavalink.youtube.clients.skeleton.Client;
import janktastic.jankbot.config.JankBotConfig;

//builds the lavaplayer manager, shared by the bot and SmokeTest so they always test the same setup
public class AudioSetup {

  //youtube-source client class names, tried in order until one can play the video.
  //which clients work changes as youtube blocks them, override with JANKBOT_YOUTUBE_CLIENTS (comma separated)
  private static final String DEFAULT_CLIENTS = "Music,AndroidVr,Web,WebEmbedded,Ios,Android,TvHtml5Simply";

  public static AudioPlayerManager createPlayerManager(JankBotConfig config) {
    AudioPlayerManager playerManager = new DefaultAudioPlayerManager();

    //lavaplayer's built-in youtube source is broken, use youtube-source instead
    YoutubeSourceOptions options = new YoutubeSourceOptions().setAllowSearch(true);
    String cipherUrl = config.getRemoteCipherUrl();
    if (cipherUrl != null && !cipherUrl.isEmpty()) {
      System.out.println("Using remote cipher server " + cipherUrl);
      options.setRemoteCipher(cipherUrl, config.getRemoteCipherPassword(), "jankbot");
    }
    String refreshToken = config.getYoutubeOauthRefreshToken();
    boolean loginConfigured = refreshToken != null && !refreshToken.isBlank();
    YoutubeAudioSourceManager youtubeSourceManager = new YoutubeAudioSourceManager(options, createClients(loginConfigured));
    if (loginConfigured) {
      enableLogin(youtubeSourceManager, refreshToken);
    }
    playerManager.registerSourceManager(youtubeSourceManager);

    AudioSourceManagers.registerRemoteSources(playerManager,
        com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager.class);
    AudioSourceManagers.registerLocalSource(playerManager);
    return playerManager;
  }

  //the login is only used by the TV client, which is tried last, so the account is only used when the anonymous clients fail.
  //a bad or revoked token just means the TV client fails, everything else keeps working
  private static void enableLogin(YoutubeAudioSourceManager youtubeSourceManager, String refreshToken) {
    try {
      youtubeSourceManager.useOauth2(refreshToken, true);
      System.out.println("Logged in to YouTube, age restricted videos are enabled");
    } catch (RuntimeException e) {
      System.out.println("YouTube login failed, continuing without it (age restricted videos won't play): " + e.getMessage());
    }
  }

  private static Client[] createClients(boolean loginConfigured) {
    String names = System.getenv("JANKBOT_YOUTUBE_CLIENTS");
    if (names == null || names.isBlank()) {
      names = DEFAULT_CLIENTS;
    }
    List<Client> clients = new ArrayList<>();
    for (String name : names.split(",")) {
      try {
        clients.add((Client) Class.forName("dev.lavalink.youtube.clients." + name.trim()).getDeclaredConstructor().newInstance());
      } catch (ReflectiveOperationException | ClassCastException e) {
        //a client can disappear in a youtube-source update, skip it rather than failing to start
        System.out.println("Unknown youtube client " + name + ", skipping");
      }
    }
    //TV can only play when logged in
    if (loginConfigured && clients.stream().noneMatch(Client::supportsOAuth)) {
      clients.add(new Tv());
      names += ",Tv";
    }
    System.out.println("Using youtube clients " + names);
    return clients.toArray(new Client[0]);
  }
}
