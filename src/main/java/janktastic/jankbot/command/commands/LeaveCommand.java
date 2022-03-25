package janktastic.jankbot.command.commands;

import janktastic.jankbot.command.AbstractCommand;
import janktastic.jankbot.command.CommandType;

public class LeaveCommand extends AbstractCommand {

  @Override
  public void run() {
    server.getAudioManager().closeAudioConnection();
    textChannel.sendMessage("Disconnected from voice but I must scream.").queue();
  }

  @Override
  public CommandType getType() {
    return CommandType.leave;
  }

}
