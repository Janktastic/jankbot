package janktastic.jankbot;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;

public class QueueManager extends AudioEventAdapter {
  private final AudioPlayer player;
  private final AudioPlayerManager playerManager;
  private final BlockingQueue<AudioTrack> queue;
  //channel of the latest play request, where playback failures get reported
  private volatile GuildMessageChannel textChannel;

  public QueueManager(AudioPlayer player, AudioPlayerManager playerManager) {
    this.player = player;
    this.playerManager = playerManager;
    this.queue = new LinkedBlockingQueue<>();
  }

  public void setTextChannel(GuildMessageChannel textChannel) {
    this.textChannel = textChannel;
  }

  public void queue(AudioTrack track) {
    if (!player.startTrack(track, true)) {
      queue.offer(track);
    }
  }

  public void nextTrack() {
    player.startTrack(queue.poll(), false);
  }
  
  public void stop() {
    player.stopTrack();
  }

  public void clearQueue() {
	  queue.clear();
  }
  
  @Override
  public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
    if (endReason.mayStartNext) {
      nextTrack();
    }
  }
  
  @Override
  public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception) {
    GuildMessageChannel textChannel = this.textChannel;
    if (textChannel != null && YoutubeLoginCheck.isAgeRestricted(exception)) {
      textChannel.sendMessage(YoutubeLoginCheck.ageRestrictedMessage(playerManager, track.getInfo().title)).queue();
    }
  }

  //try and skip age restriction videos that wont play (or recover from other unknown reasons track would be stuck)
  @Override
  public void onTrackStuck(AudioPlayer player, AudioTrack track, long thresholdMs) {
    player.stopTrack();
    nextTrack();
  }
  
  //returns list of titles queued for displaying in channel
  public List<String> getQueueTitles() {
    List<String> titleList = new ArrayList<String>();
    for (Iterator<AudioTrack> i = queue.iterator(); i.hasNext();) {
      AudioTrack track = i.next();
      titleList.add(track.getInfo().title);
    }
    return titleList;
  }
}