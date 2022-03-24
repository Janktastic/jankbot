package janktastic.jankbot.command.commands;

import janktastic.jankbot.command.AbstractAudioCommand;
import janktastic.jankbot.command.CommandType;
import net.dv8tion.jda.api.entities.VoiceChannel;

public class PlayCommand extends AbstractAudioCommand{

  @Override
  public void run() {
    VoiceChannel voiceChannel = getVoiceChannel();
    if (voiceChannel == null) {
      textChannel.sendMessage(INVALID_VOICE_CHANNEL).queue();
      return;
    }
    
    discordAudioManager.loadAndPlay(textChannel, arg, voiceChannel);
  }

  @Override
  public CommandType getType() {
    return CommandType.play;
  }

}
