package janktastic.jankbot;

import java.util.List;

import janktastic.jankbot.audio.DiscordAudioManager;
import janktastic.jankbot.command.AbstractCommand;
import janktastic.jankbot.command.JankBotCommandFactory;
import janktastic.jankbot.config.JankBotConfig;
import janktastic.youtube.YoutubeSearch;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class DiscordEventHandler extends ListenerAdapter {

  private final JankBotCommandFactory cmdFactory;
  private final DiscordAudioManager discordAudioManager;

  public DiscordEventHandler(JankBotConfig jankBotConfig, YoutubeSearch youtubeSearch) {
    this.discordAudioManager = new DiscordAudioManager(youtubeSearch);
    cmdFactory = new JankBotCommandFactory(discordAudioManager, jankBotConfig.getCommandPrefix());
  }

  @Override
  public void onReady(ReadyEvent event) {
    System.out.println("JankBot loaded, connected to " + event.getGuildAvailableCount() + " servers:");

    List<Guild> guilds = event.getJDA().getGuilds();
    // print server list to track who's added JankBot
    for (Guild guild : guilds) {
      System.out.println(guild.getName());
    }
  }

  // called when bot notices a new message sent in a guild (server)
  // if command prefix detected attempt to execute the requested command
  @Override
  public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
    AbstractCommand command = cmdFactory.build(event);
    if (command == null) {
      return;
    } else {
      command.run();
    }
    super.onGuildMessageReceived(event);
  }

}