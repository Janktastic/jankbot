package janktastic.jankbot.audio;

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

import janktastic.jankbot.JankBotUtil;
import janktastic.youtube.YoutubeSearch;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.managers.AudioManager;

public class DiscordAudioManager {
  private final AudioPlayerManager lavaPlayerManager;
  //map of serverId to lava audio player
	private final Map<Long, AudioPlayer> audioPlayerMap;
	//map of serverId to audio queue manager
	private final Map<Long, AudioQueueManager> audioQueueMap;
	
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

    if (!JankBotUtil.isLink(playRequest)) {
      SearchListResponse response = youtubeSearch.search(playRequest, 1);
      if (!response.getItems().isEmpty()) {
        System.out.println(JankBotUtil.getYoutubeUrl(response.getItems().get(0).getId().getVideoId()));
        playRequest = JankBotUtil.getYoutubeUrl(response.getItems().get(0).getId().getVideoId());
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

  public void connectToVoiceChannel(AudioManager audioManager, VoiceChannel voiceChannel) {
    if (!voiceChannel.equals(voiceChannel.getGuild().getAudioManager().getConnectedChannel())) {
      audioManager.openAudioConnection(voiceChannel);
    }
  }

  public List<String> getLatestSearchResultForUser(Long userId) {
    return userSearchMap.get(userId);
  }
  
  public AudioTrack getCurrentTrack(Guild server) {
    return getAudioQueue(server).getCurrentTrack();
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
  
  private synchronized AudioQueueManager getAudioQueue(Guild server) {
    long serverId = Long.parseLong(server.getId());
    AudioQueueManager queueManager = audioQueueMap.get(serverId);
    if (queueManager == null) {
      queueManager = new AudioQueueManager(getAudioPlayer(server));
      audioQueueMap.put(serverId, queueManager);
    }

    return queueManager;
  }
  
  
	private LavaPlayerSendHandler getSendHandler(Long serverId) {
		return new LavaPlayerSendHandler(audioPlayerMap.get(serverId));
	}

}