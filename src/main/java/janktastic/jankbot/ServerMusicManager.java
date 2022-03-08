package janktastic.jankbot;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;

//container for player & queue manager
public class ServerMusicManager {

	private final AudioPlayer player;
	private final QueueManager queueManager;

	public ServerMusicManager(AudioPlayerManager manager) {
		player = manager.createPlayer();
		queueManager = new QueueManager(player);
		player.addListener(queueManager);
	}
	
	public LavaPlayerSendHandler getSendHandler() {
		return new LavaPlayerSendHandler(player);
	}
	
	public AudioPlayer getPlayer() {
		return player;
	}

  public QueueManager getQueueManager() {
    return queueManager;
  }
}