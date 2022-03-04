package janktastic.jankbot;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.managers.AudioManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.dv8tion.jda.api.requests.GatewayIntent.GUILD_MESSAGES;
import static net.dv8tion.jda.api.requests.GatewayIntent.GUILD_VOICE_STATES;

public class Main extends ListenerAdapter {
  
  private static final String JANKBOT_TOKEN = "";
  private static final String COMMAND_PREFIX = "!";
  
  public static void main(String[] args) throws Exception {
    JDABuilder.create(JANKBOT_TOKEN, GUILD_MESSAGES, GUILD_VOICE_STATES)
        .addEventListeners(new Main())
        .build();
  }

  private final AudioPlayerManager playerManager;
  private final Map<Long, GuildMusicManager> musicManagers;

  private Main() {
    this.musicManagers = new HashMap<>();

    this.playerManager = new DefaultAudioPlayerManager();
    AudioSourceManagers.registerRemoteSources(playerManager);
    AudioSourceManagers.registerLocalSource(playerManager);
  }

  private synchronized GuildMusicManager getGuildAudioPlayer(Guild guild) {
    long guildId = Long.parseLong(guild.getId());
    GuildMusicManager musicManager = musicManagers.get(guildId);

    if (musicManager == null) {
      musicManager = new GuildMusicManager(playerManager);
      musicManagers.put(guildId, musicManager);
    }

    guild.getAudioManager().setSendingHandler(musicManager.getSendHandler());

    return musicManager;
  }

  @Override
  public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
    String[] message = event.getMessage().getContentRaw().split(" ");

    if (message[0].startsWith(COMMAND_PREFIX)) {
      System.out.println("command found");
      String command = message[0].split("!")[1];
      System.out.println(command);
      List<String> args = new ArrayList<>();
      for (int i = 1; i < message.length; i++) {
        System.out.println(message[i]);
        args.add(message[i]);
      }
      
      User user = event.getAuthor();
      VoiceChannel voiceChannel = findVoiceChannelOfUser(event.getGuild(), user.getIdLong());
      
     
      System.out.println(args.size());
      if ("play".equals(command) && args.size() == 1) {
        System.out.println("calling loadAndPlay");
        loadAndPlay(event.getChannel(), args.get(0), voiceChannel);
      } else if ("skip".equals(command)) {
        skipTrack(event.getChannel());
      }
    }

    super.onGuildMessageReceived(event);
  }

  private void loadAndPlay(final TextChannel channel, final String trackUrl, final VoiceChannel voiceChannel) {
    if (voiceChannel == null) {
      System.out.println("no channel");
      channel.sendMessage("You must be in a voice channel or specify one in the command.").queue();
      return;
    }
    final GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild());

    playerManager.loadItemOrdered(musicManager, trackUrl, new AudioLoadResultHandler() {
      @Override
      public void trackLoaded(AudioTrack track) {
        channel.sendMessage("Adding to queue " + track.getInfo().title).queue();

        play(channel.getGuild(), musicManager, track, voiceChannel);
      }

      @Override
      public void playlistLoaded(AudioPlaylist playlist) {
        AudioTrack firstTrack = playlist.getSelectedTrack();

        if (firstTrack == null) {
          firstTrack = playlist.getTracks().get(0);
        }

        channel.sendMessage("Adding to queue " + firstTrack.getInfo().title + " (first track of playlist " + playlist.getName() + ")").queue();

        play(channel.getGuild(), musicManager, firstTrack, voiceChannel);
      }

      @Override
      public void noMatches() {
        channel.sendMessage("Nothing found by " + trackUrl).queue();
      }

      @Override
      public void loadFailed(FriendlyException exception) {
        channel.sendMessage("Could not play: " + exception.getMessage()).queue();
      }
    });
  }

  private void play(Guild guild, GuildMusicManager musicManager, AudioTrack track, VoiceChannel voiceChannel) {
    connectToVoiceChannel(guild.getAudioManager(), voiceChannel);

    musicManager.scheduler.queue(track);
  }

  private void skipTrack(TextChannel channel) {
    GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild());
    musicManager.scheduler.nextTrack();

    channel.sendMessage("Skipped to next track.").queue();
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

  private void connectToFirstVoiceChannel(AudioManager audioManager) {
    if (!audioManager.isConnected()) {
      for (VoiceChannel voiceChannel : audioManager.getGuild().getVoiceChannels()) {
        audioManager.openAudioConnection(voiceChannel);
        break;
      }
    }
  }
  
  private void connectToVoiceChannel(AudioManager audioManager, VoiceChannel voiceChannel) {
    if (!audioManager.isConnected()) {
      audioManager.openAudioConnection(voiceChannel);
    }
  }
}