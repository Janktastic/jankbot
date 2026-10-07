package janktastic.jankbot;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import janktastic.jankbot.config.JankBotConfigFactory;

//checks that youtube playback actually works (load + decode audio) without connecting to discord.
//used by deploy/update.sh to decide whether a youtube-source version is safe to deploy.
//usage: java -cp jankbot.jar janktastic.jankbot.SmokeTest [identifier ...]
//exits 0 if every identifier played, 1 otherwise
public class SmokeTest {

  //a mix of direct links and searches; "around the world" is a good canary, it fails on some clients that play other videos
  private static final String[] DEFAULT_IDENTIFIERS = {
      "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
      "https://www.youtube.com/watch?v=fJ9rUzIMcZQ",
      "ytsearch:daft punk around the world",
      "ytsearch:fleetwood mac dreams"
  };
  //~2 seconds of audio
  private static final int REQUIRED_FRAMES = 100;

  public static void main(String[] args) throws IOException {
    String[] identifiers = args.length > 0 ? args : DEFAULT_IDENTIFIERS;
    //same config as the bot (config.json if present, plus env overrides) so the same setup is tested
    AudioPlayerManager playerManager = AudioSetup.createPlayerManager(JankBotConfigFactory.buildConfigIfPresent());

    boolean allPassed = true;
    for (String identifier : identifiers) {
      String result;
      try {
        result = "PASS " + identifier + " -> " + play(playerManager, identifier);
      } catch (Exception e) {
        allPassed = false;
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        result = "FAIL " + identifier + " -> " + cause;
      }
      System.out.println("SMOKETEST " + result);
    }
    System.out.println("SMOKETEST " + (allPassed ? "PASSED" : "FAILED"));
    playerManager.shutdown();
    System.exit(allPassed ? 0 : 1);
  }

  //loads the identifier and decodes some audio, returns the track title
  private static String play(AudioPlayerManager playerManager, String identifier) throws Exception {
    CompletableFuture<AudioTrack> loaded = new CompletableFuture<>();
    playerManager.loadItem(identifier, new AudioLoadResultHandler() {
      @Override
      public void trackLoaded(AudioTrack track) {
        loaded.complete(track);
      }

      @Override
      public void playlistLoaded(AudioPlaylist playlist) {
        AudioTrack track = playlist.getSelectedTrack() != null ? playlist.getSelectedTrack() : playlist.getTracks().get(0);
        loaded.complete(track);
      }

      @Override
      public void noMatches() {
        loaded.completeExceptionally(new IllegalStateException("no matches"));
      }

      @Override
      public void loadFailed(FriendlyException exception) {
        loaded.completeExceptionally(exception);
      }
    });
    AudioTrack track = loaded.get(30, TimeUnit.SECONDS);

    AudioPlayer player = playerManager.createPlayer();
    try {
      player.playTrack(track);
      int frames = 0;
      long deadline = System.currentTimeMillis() + 30000;
      while (frames < REQUIRED_FRAMES && System.currentTimeMillis() < deadline) {
        if (player.provide(100, TimeUnit.MILLISECONDS) != null) {
          frames++;
        }
      }
      if (frames < REQUIRED_FRAMES) {
        throw new IllegalStateException("only decoded " + frames + "/" + REQUIRED_FRAMES + " audio frames");
      }
    } finally {
      player.destroy();
    }
    return track.getInfo().title;
  }
}
