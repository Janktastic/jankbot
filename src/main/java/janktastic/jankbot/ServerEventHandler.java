package janktastic.jankbot;

import static net.dv8tion.jda.api.requests.GatewayIntent.GUILD_MESSAGES;
import static net.dv8tion.jda.api.requests.GatewayIntent.GUILD_VOICE_STATES;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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

import janktastic.jankbot.config.JankBotConfig;
import janktastic.jankbot.config.JankBotConfigFactory;
import janktastic.youtube.YoutubeSearch;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.managers.AudioManager;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

public class ServerEventHandler extends ListenerAdapter {
  
  private JankBotConfig jankBotConfig;
  //string at the beginning of a message that lets bot know its a command
  private String commandPrefix;
  
  private final YoutubeSearch youtubeSearch;
  private final AudioPlayerManager playerManager;
  
  //map of guild ids to player/queuemanager container
  private final Map<Long, ServerMusicManager> musicManagers;
  
  //map of discord user ids to their last search results
  private Map<Long, List<String>> userSearchMap = new HashMap<>();
  
  //discord codeblock markup for pretty printing bot responses
  private static final String CODEBLOCK = "```";

  
public ServerEventHandler(JankBotConfig jankBotConfig, YoutubeSearch youtubeSearch) {
  this.youtubeSearch = youtubeSearch;
  this.jankBotConfig = jankBotConfig;
  commandPrefix = jankBotConfig.getCommandPrefix();
  musicManagers = new HashMap<>();

  playerManager = new DefaultAudioPlayerManager();
  AudioSourceManagers.registerRemoteSources(playerManager);
  AudioSourceManagers.registerLocalSource(playerManager);
}

  
  @Override
  public void onReady(ReadyEvent event) {
    System.out.println("JankBot loaded, connected to " + event.getGuildAvailableCount() + " servers:");
    
    List<Guild> guilds = event.getJDA().getGuilds();
    //print server list to track who's added JankBot
    for (Guild guild : guilds) {
      System.out.println(guild.getName());
    }
  }
  //returns the queue/player container for a guild(server) and sets the JDA SendingHandler to the guild specific lavaplayer sendhandler
  private synchronized ServerMusicManager getGuildAudioPlayer(Guild guild) {
    long guildId = Long.parseLong(guild.getId());
    ServerMusicManager musicManager = musicManagers.get(guildId);

    if (musicManager == null) {
      musicManager = new ServerMusicManager(playerManager);
      musicManagers.put(guildId, musicManager);
    }

    guild.getAudioManager().setSendingHandler(musicManager.getSendHandler());

    return musicManager;
  }

  //called when bot notices a new message sent in a guild (server)
  //if command prefix detected attempt to execute the requested command
  @Override
  public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
    
    
  

    super.onGuildMessageReceived(event);
  }

  public void printQueue(TextChannel textChannel) {
    final ServerMusicManager musicManager = getGuildAudioPlayer(textChannel.getGuild());
    List<String> songTitles = musicManager.getQueueManager().getQueueTitles();
    String header = "Currently Queued:\n";
    String queueString = "";
    for (String title : songTitles) {
      queueString += title + "\n";
    }
    textChannel.sendMessage(header + (queueString.isEmpty() ? "Nothing currently queued" : codeblock(queueString))).queue();
  }
  
  public void leaveVoice(Guild guild) {
    AudioManager audioManager = guild.getAudioManager();
    audioManager.closeAudioConnection();
  }
  
  public void printHelp(TextChannel textChannel, String user) {
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

  public void search(final TextChannel textChannel, long userId, String playRequest) {
    SearchListResponse response = youtubeSearch.search(playRequest);
    Map<String, String> idTitleMap = youtubeSearch.getIdTitleMap(response);
    //store search results by userId
    userSearchMap.put(userId, new ArrayList<String>(idTitleMap.keySet()));
    textChannel.sendMessage("Search Results:\n").queue();
    String results = "";
    Iterator<String> iter = idTitleMap.values().iterator();
    for (int i = 0; i < idTitleMap.size(); i++) {
      results += Integer.toString(i) + ":\t\t" + iter.next() + "\n";
    }

    textChannel.sendMessage(results.isEmpty() ? "No results found." : codeblock(results) + "\nTo select a track use " + commandPrefix + "[0-9]").queue();
  }
  public void loadAndPlay(final TextChannel textChannel, String playRequest, final VoiceChannel voiceChannel) {
    if (voiceChannel == null) {
      System.out.println("no channel");
      textChannel.sendMessage("You must be in a voice channel to initiate playback.").queue();
      return;
    }

    if (!isLink(playRequest)) {
      SearchListResponse response = youtubeSearch.search(playRequest);
      if (!response.getItems().isEmpty()) {
        System.out.println(youtubeSearch.getVideoUrl(response.getItems().get(0).getId().getVideoId()));
        playRequest = youtubeSearch.getVideoUrl(response.getItems().get(0).getId().getVideoId());
      }

    } else {
      System.out.println(playRequest);
    }

    final ServerMusicManager musicManager = getGuildAudioPlayer(textChannel.getGuild());
    JankAudioLoadResultHandler audioLoadHandler = new JankAudioLoadResultHandler(this, playRequest, musicManager, textChannel,
        voiceChannel);
    playerManager.loadItemOrdered(musicManager, playRequest, audioLoadHandler);
  }

  public void play(Guild guild, ServerMusicManager musicManager, AudioTrack track, VoiceChannel voiceChannel) {
    System.out.println("in play");
    System.out.println("channel: " + voiceChannel.getName());
    connectToVoiceChannel(guild.getAudioManager(), voiceChannel);

    musicManager.getQueueManager().queue(track);
  }

  public void skipTrack(TextChannel channel) {
    ServerMusicManager musicManager = getGuildAudioPlayer(channel.getGuild());
    musicManager.getQueueManager().nextTrack();

    channel.sendMessage("Skipped to next track.").queue();
  }

  public void stopPlayback(TextChannel channel) {
    ServerMusicManager musicManager = getGuildAudioPlayer(channel.getGuild());
    musicManager.getQueueManager().stop();

    channel.sendMessage("Playback stopped.").queue();
  }

  public void emptyQueue(TextChannel channel) {
    ServerMusicManager musicManager = getGuildAudioPlayer(channel.getGuild());
    musicManager.getQueueManager().clearQueue();
    channel.sendMessage("Playback queue cleared.").queue();
  }

  public VoiceChannel findVoiceChannelOfUser(Guild guild, long userId) {
    List<VoiceChannel> channels = guild.getVoiceChannels();
    for (VoiceChannel channel : channels) {
      List<Member> voiceChannelMembers = channel.getMembers();
      for (Member voiceChannelMember : voiceChannelMembers) {
        if (voiceChannelMember.getIdLong() == userId) {
          return channel;
        }
      }
    }
    return null;
  }

  public void connectToFirstVoiceChannel(AudioManager audioManager) {
    if (!audioManager.isConnected()) {
      for (VoiceChannel voiceChannel : audioManager.getGuild().getVoiceChannels()) {
        audioManager.openAudioConnection(voiceChannel);
        break;
      }
    }
  }

  public void connectToVoiceChannel(AudioManager audioManager, VoiceChannel voiceChannel) {
    if (!audioManager.isConnected()) {
      audioManager.openAudioConnection(voiceChannel);
    }
  }

  //used to determine if request is a search term or a literal link 
  private boolean isLink(String arg) {
    if (arg.startsWith("http://") || arg.startsWith("https://")) {
      return true;
    }
    return false;
  }
  
  //wraps string in discord codeblock markup ```text```
  private String codeblock(String text) {
    return CODEBLOCK + text + CODEBLOCK;
  }
  

}