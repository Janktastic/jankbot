package janktastic.jankbot.command;

import java.util.List;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.VoiceChannel;

public abstract class AbstractPlayCommand extends AbstractCommand {

  protected static final String INVALID_VOICE_CHANNEL = "You must be in a voice channel or specify a valid one to initiate playback.  I'll just scream your request into the void, I was already doing it anyways.";

  protected VoiceChannel findVoiceChannelOfUser(Guild server, long userId) {
    List<VoiceChannel> channels = server.getVoiceChannels();
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

  protected VoiceChannel getVoiceChannel() {
    VoiceChannel voiceChannel = null;
    String requestedChannel = options.get(CommandOption.channel);
    if (requestedChannel != null) {
      List<VoiceChannel> matchingChannels = server.getVoiceChannelsByName(options.get(CommandOption.channel), true);
      if (matchingChannels.isEmpty()) {
        textChannel.sendMessage("No such channel: " + requestedChannel).queue();
        return null;
      }
      voiceChannel = matchingChannels.get(0);
    } else {
      voiceChannel = findVoiceChannelOfUser(server, userId);
    }
    return voiceChannel;
  }

}
