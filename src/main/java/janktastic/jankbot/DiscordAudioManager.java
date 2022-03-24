package janktastic.jankbot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.api.services.youtube.model.SearchListResponse;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import janktastic.youtube.YoutubeSearch;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.managers.AudioManager;

public class DiscordAudioManager {

  private final AudioPlayerManager lavaPlayerManager;
  //map of serverId to lava audio player
	private final Map<Long, AudioPlayer> audioPlayerMap;
	//map of serverId to audio queue manager
	private final Map<Long, QueueManager> audioQueueMap;
	
	//map of discord userIds to their last search results
  private final Map<Long, List<String>> userSearchMap;
  
  private final YoutubeSearch youtubeSearch;

	public DiscordAudioManager(YoutubeSearch youtubeSearch) {
	  this.lavaPlayerManager = new DefaultAudioPlayerManager();
    AudioSourceManagers.registerRemoteSources(lavaPlayerManager);
    AudioSourceManagers.registerLocalSource(lavaPlayerManager);
    this.youtubeSearch = youtubeSearch;
	  audioPlayerMap = new HashMap<>();
	  audioQueueMap = new HashMap<>();
	  userSearchMap = new HashMap<>();
	}
	
  public void loadAndPlay(final TextChannel textChannel, String playRequest, final VoiceChannel voiceChannel) {
    if (textChannel == null || playRequest == null || voiceChannel == null) {
      return;
    }

    if (!JankBotStringUtil.isLink(playRequest)) {
      SearchListResponse response = youtubeSearch.search(playRequest, 1);
      if (!response.getItems().isEmpty()) {
        System.out.println(youtubeSearch.getVideoUrl(response.getItems().get(0).getId().getVideoId()));
        playRequest = youtubeSearch.getVideoUrl(response.getItems().get(0).getId().getVideoId());
      }

    } else {
      System.out.println(playRequest);
    }
    
    JankAudioLoadResultHandler audioLoadHandler = new JankAudioLoadResultHandler(playRequest, this, textChannel, voiceChannel);
    lavaPlayerManager.loadItemOrdered(textChannel.getGuild().getIdLong(), playRequest, audioLoadHandler);
  }
  
  public void leaveVoice(Guild guild) {
    AudioManager disscordServerAudioManager = guild.getAudioManager();
    disscordServerAudioManager.closeAudioConnection();
  }
  
  public Map<String, String> search(final TextChannel textChannel, long userId, String playRequest) {
    SearchListResponse response = youtubeSearch.search(playRequest, 10);
    Map<String, String> idTitleMap = youtubeSearch.getIdTitleMap(response);
    //store search results by userId
    userSearchMap.put(userId, new ArrayList<String>(idTitleMap.keySet()));
    return idTitleMap;
    /*
    textChannel.sendMessage("Search Results:\n").queue();
    String results = "";
    Iterator<String> iter = idTitleMap.values().iterator();
    for (int i = 0; i < idTitleMap.size(); i++) {
      results += Integer.toString(i) + ":\t\t" + iter.next() + "\n";
    }

    textChannel.sendMessage(results.isEmpty() ? "No results found." : codeblock(results) + "\nTo select a track use " + commandPrefix + "[0-9]").queue();
    */
  }
  
  public void play(Guild guild, AudioTrack track, VoiceChannel voiceChannel) {
    System.out.println("in play");
    System.out.println("channel: " + voiceChannel.getName());
    connectToVoiceChannel(guild.getAudioManager(), voiceChannel);
    getAudioQueue(guild).queue(track);
  }

  public void skipTrack(Guild guild) {
    getAudioQueue(guild).nextTrack();
  }

  public void stopPlayback(Guild guild) {
    getAudioQueue(guild).stop();
  }

  public void emptyQueue(Guild guild) {
    getAudioQueue(guild).clearQueue();
  }

  private VoiceChannel findVoiceChannelOfUser(Guild guild, long userId) {
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

  public void connectToVoiceChannel(AudioManager audioManager, VoiceChannel voiceChannel) {
    if (!voiceChannel.equals(voiceChannel.getGuild().getAudioManager().getConnectedChannel())) {
      audioManager.openAudioConnection(voiceChannel);
    }
  }

  private synchronized AudioPlayer getAudioPlayer(Guild server) {
    long serverId = Long.parseLong(server.getId());
    AudioPlayer audioPlayer = audioPlayerMap.get(serverId);
    if (audioPlayer == null) {
      audioPlayer = lavaPlayerManager.createPlayer();
      audioPlayerMap.put(serverId, audioPlayer);
      server.getAudioManager().setSendingHandler(getSendHandler(serverId));
    }

    return audioPlayer;
  }
  
  private synchronized QueueManager getAudioQueue(Guild server) {
    long serverId = Long.parseLong(server.getId());
    QueueManager queueManager = audioQueueMap.get(serverId);
    if (queueManager == null) {
      queueManager = new QueueManager(getAudioPlayer(server));
      audioQueueMap.put(serverId, queueManager);
    }

    return queueManager;
  }
  
  
	private LavaPlayerSendHandler getSendHandler(Long serverId) {
		return new LavaPlayerSendHandler(audioPlayerMap.get(serverId));
	}
	

}