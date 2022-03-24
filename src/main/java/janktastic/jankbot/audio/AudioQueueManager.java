package janktastic.jankbot.audio;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class AudioQueueManager extends AudioEventAdapter {
  private final AudioPlayer player;
  private final BlockingQueue<AudioTrack> queue;
  private AudioTrack currentTrack;

  public AudioQueueManager(AudioPlayer player) {
    this.player = player;
    this.queue = new LinkedBlockingQueue<>();
  }

  public void queue(AudioTrack track) {
    if (!player.startTrack(track, true)) {
      queue.offer(track);
    } else {
      currentTrack = track;
    }
  }

  public void nextTrack() {
    currentTrack = queue.poll();
    player.startTrack(currentTrack, false);
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
    } else {
      currentTrack = null;
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
  
  public AudioTrack getCurrentTrack() {
    return currentTrack;
  }
}