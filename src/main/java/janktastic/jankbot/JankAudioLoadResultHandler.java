package janktastic.jankbot;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import janktastic.jankbot.command.JankBotCommandRunner;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.VoiceChannel;

public class JankAudioLoadResultHandler implements AudioLoadResultHandler {
	
	private TextChannel textChannel;
	private VoiceChannel voiceChannel;
	private ServerMusicManager musicManager;
	private JankBotCommandRunner bot;
	private String trackUrl;
	
	public JankAudioLoadResultHandler(JankBotCommandRunner bot, String trackUrl, ServerMusicManager musicManager, TextChannel textChannel, VoiceChannel voiceChannel) {
		this.bot = bot;
		this.trackUrl = trackUrl;
		this.musicManager = musicManager;
		this.textChannel = textChannel;
		this.voiceChannel = voiceChannel;
	}
	
    @Override
    public void trackLoaded(AudioTrack track) {
      textChannel.sendMessage("Adding to queue " + track.getInfo().title).queue();

      bot.play(textChannel.getGuild(), musicManager, track, voiceChannel);
    }

    @Override
    public void playlistLoaded(AudioPlaylist playlist) {
      AudioTrack firstTrack = playlist.getSelectedTrack();

      if (firstTrack == null) {
        firstTrack = playlist.getTracks().get(0);
      }

      textChannel.sendMessage("Adding to queue " + firstTrack.getInfo().title + " (first track of playlist " + playlist.getName() + ")").queue();

      bot.play(textChannel.getGuild(), musicManager, firstTrack, voiceChannel);
    }

    @Override
    public void noMatches() {
    	textChannel.sendMessage("Not Found: " + trackUrl).queue();
    }

    @Override
    public void loadFailed(FriendlyException exception) {
    	textChannel.sendMessage("Could not play: " + exception.getMessage()).queue();
    }
}
