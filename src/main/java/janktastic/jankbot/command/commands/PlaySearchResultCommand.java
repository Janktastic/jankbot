package janktastic.jankbot.command.commands;

import java.util.List;

import janktastic.jankbot.JankBotUtil;
import janktastic.jankbot.command.AbstractAudioCommand;
import janktastic.jankbot.command.CommandType;
import net.dv8tion.jda.api.entities.VoiceChannel;

public class PlaySearchResultCommand extends AbstractAudioCommand{

  @Override
  public void run() {
    VoiceChannel voiceChannel = getVoiceChannel();
    if (voiceChannel == null) {
      textChannel.sendMessage(INVALID_VOICE_CHANNEL).queue();
      return;
    }
    
    List<String> searchResults = discordAudioManager.getLatestSearchResultForUser(userId);
    int requestedNumber = Integer.valueOf(arg);
    
    if (searchResults != null && requestedNumber < searchResults.size()) {
      String videoId = searchResults.get(requestedNumber);
      discordAudioManager.loadAndPlay(textChannel, JankBotUtil.getYoutubeUrl(videoId), voiceChannel);
    }
    else {
      textChannel.sendMessage("Invalid search result requested.").queue();
      return;
    }
  }

  @Override
  public CommandType getType() {
    return CommandType.playSearchResult;
  }


}
