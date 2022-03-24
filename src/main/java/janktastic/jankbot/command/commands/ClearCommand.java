package janktastic.jankbot.command.commands;

import janktastic.jankbot.command.AbstractCommand;
import janktastic.jankbot.command.CommandType;

public class ClearCommand extends AbstractCommand {

  @Override
  public void run() {
    discordAudioManager.emptyQueue(server);
    textChannel.sendMessage("The queue is now as empty as my life.").queue();
  }

  @Override
  public CommandType getType() {
    return CommandType.stop;
  }

}
