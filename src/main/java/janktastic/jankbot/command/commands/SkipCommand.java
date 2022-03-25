package janktastic.jankbot.command.commands;

import java.util.Random;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import janktastic.jankbot.command.AbstractCommand;
import janktastic.jankbot.command.CommandType;

public class SkipCommand extends AbstractCommand {

  @Override
  public void run() {
    if (discordAudioManager.getCurrentTrack(server) == null) {
      textChannel.sendMessage("While I'd love to skip the current track for you, that would be impossible.").queue();
      return;
    }

    AudioTrack track = discordAudioManager.getCurrentTrack(server);

    boolean banger = new Random().nextBoolean();
    if (banger) {
      textChannel.sendMessage("Who doesn't like " + track.getInfo().title + "?? I see there's no accounting for taste. Skipping.").queue();
    } else {
      textChannel.sendMessage("Skipping " + track.getInfo().title + ". Good call, I thought it sucked too.").queue();
    }
    discordAudioManager.skipTrack(server);
  }

  @Override
  public CommandType getType() {
    return CommandType.stop;
  }

}
