package janktastic.jankbot.audio;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.VoiceChannel;

public class JankAudioLoadResultHandler implements AudioLoadResultHandler {
  private String playRequest;
  private TextChannel textChannel;
  private VoiceChannel voiceChannel;
  private DiscordAudioManager musicManager;

  public JankAudioLoadResultHandler(String playRequest, DiscordAudioManager musicManager, TextChannel textChannel,
      VoiceChannel voiceChannel) {
    this.musicManager = musicManager;
    this.textChannel = textChannel;
    this.voiceChannel = voiceChannel;
    this.playRequest = playRequest;
  }

  @Override
  public void trackLoaded(AudioTrack track) {
    textChannel.sendMessage("Adding to queue " + track.getInfo().title).queue();
    musicManager.play(textChannel.getGuild(), track, voiceChannel);
  }

  @Override
  public void playlistLoaded(AudioPlaylist playlist) {
    AudioTrack firstTrack = playlist.getSelectedTrack();
    if (firstTrack == null) {
      firstTrack = playlist.getTracks().get(0);
    }
    textChannel.sendMessage("Adding to queue " + firstTrack.getInfo().title + " (first track of playlist " + playlist.getName() + ")")
        .queue();
    musicManager.play(textChannel.getGuild(), firstTrack, voiceChannel);
  }

  @Override
  public void noMatches() {
    textChannel.sendMessage("Not Found: " + playRequest).queue();
  }

  @Override
  public void loadFailed(FriendlyException exception) {
    textChannel.sendMessage("Could not play: " + exception.getMessage()).queue();
  }
}
