package janktastic.jankbot.command;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.google.api.services.youtube.model.SearchListResponse;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import janktastic.jankbot.JankAudioLoadResultHandler;
import janktastic.jankbot.DiscordAudioManager;
import janktastic.jankbot.config.JankBotConfig;
import janktastic.youtube.YoutubeSearch;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.managers.AudioManager;

public class JankBotCommandRunner {
  
  private final YoutubeSearch youtubeSearch;
  private final AudioPlayerManager playerManager;
  
  //map of guild ids to player/queuemanager container
  private final Map<Long, DiscordAudioManager> musicManagers;

  //string at the beginning of a message that lets bot know its a command
  private String commandPrefix;
  
  public JankBotCommandRunner(JankBotConfig config, YoutubeSearch youtubeSearch) {
    this.youtubeSearch = youtubeSearch;
    musicManagers = new HashMap<>();

    playerManager = new DefaultAudioPlayerManager();
    AudioSourceManagers.registerRemoteSources(playerManager);
    AudioSourceManagers.registerLocalSource(playerManager);
  }


  
  private void printQueue(TextChannel textChannel) {
    final DiscordAudioManager musicManager = getGuildAudioPlayer(textChannel.getGuild());
    List<String> songTitles = musicManager.getQueueManager().getQueueTitles();
    String header = "Currently Queued:\n";
    String queueString = "";
    for (String title : songTitles) {
      queueString += title + "\n";
    }
    textChannel.sendMessage(header + (queueString.isEmpty() ? "Nothing currently queued" : codeblock(queueString))).queue();
  }
  
  private void printHelp(TextChannel textChannel, String user) {
    String format = "%-25s%-25s%n";
    String helpHeader = "**Please command me " + user + "-san :pleading_face:**";
    Map<String, String> commands = new LinkedHashMap<>();
    commands.put(commandPrefix + "play", "Plays an exact url or the first result for a search term");
    commands.put(commandPrefix + "search",
        "Returns the first 10 items for a search term. To play one of them use " + commandPrefix + "[0-9]");
    commands.put(commandPrefix + "skip", "Skips the current track");
    commands.put(commandPrefix + "stop", "Stops playback");
    commands.put(commandPrefix + "clear", "Clears the playback queue");
    commands.put(commandPrefix + "queue", "Print currently queued tracks");
    commands.put(commandPrefix + "leave", "Leave the current voice channel");

    String helpMsg = "";
    for (Entry<String, String> entry : commands.entrySet()) {
      helpMsg += String.format(format, entry.getKey(), entry.getValue());
    }
    textChannel.sendMessage(helpHeader + "\n" + codeblock(helpMsg)).queue();
  }


  
 


}
