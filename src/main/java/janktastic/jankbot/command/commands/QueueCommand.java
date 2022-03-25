package janktastic.jankbot.command.commands;

import java.util.List;

import janktastic.jankbot.JankBotUtil;
import janktastic.jankbot.command.AbstractCommand;
import janktastic.jankbot.command.CommandType;

public class QueueCommand extends AbstractCommand {

  @Override
  public void run() {
    List<String> songTitles = discordAudioManager.getAudioQueue(server).getQueueTitles();
    String header = "Currently Queued:\n";
    String queueString = "";
    for (String title : songTitles) {
      queueString += title + "\n";
    }
    textChannel.sendMessage(header + (queueString.isEmpty() ? "Nothing currently queued" : JankBotUtil.codeblock(queueString))).queue();
  }

  @Override
  public CommandType getType() {
    return CommandType.queue;
  }

}
