package janktastic.jankbot.command.commands;

import janktastic.jankbot.command.AbstractCommand;
import janktastic.jankbot.command.CommandType;

public class StopCommand extends AbstractCommand {

  @Override
  public void run() {
    if (discordAudioManager.getCurrentTrack(server) == null) {
      textChannel.sendMessage("Nothing currently playing...  maybe you have tinnitus?").queue();
      return;
    }

    String username = server.getMemberById(userId).getEffectiveName();
    textChannel.sendMessage("Hey everyone, " + username + " hates fun.  Stopping playback before he calls in a noise complaint.").queue();

    discordAudioManager.stopPlayback(server);
  }

  @Override
  public CommandType getType() {
    return CommandType.stop;
  }

}
