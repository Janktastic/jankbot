package janktastic.jankbot;

import static net.dv8tion.jda.api.requests.GatewayIntent.GUILD_MESSAGES;
import static net.dv8tion.jda.api.requests.GatewayIntent.GUILD_VOICE_STATES;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import janktastic.jankbot.config.JankBotConfig;
import janktastic.jankbot.config.JankBotConfigFactory;
import janktastic.youtube.YoutubeSearch;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

public class Jankbot {
  private static JankBotConfig jankBotConfig;
  //string at the beginning of a message that lets bot know its a command
  private static String discordToken;
  private static YoutubeSearch youtubeSearch;
;
  
  public static void main(String[] args) throws Exception {
    //TODO: allow passing in config file path
    jankBotConfig = JankBotConfigFactory.buildConfig();
    youtubeSearch = new YoutubeSearch(jankBotConfig.getGoogleApiKey());
    discordToken = jankBotConfig.getDiscordBotToken();

    printBotBanner();
    System.out.println("Initializing JankBot...");
    
    JDA jda = JDABuilder.create(discordToken, GUILD_MESSAGES, GUILD_VOICE_STATES).addEventListeners(new ServerEventHandler(jankBotConfig, youtubeSearch))
    //disable jda cache for now to prevent warnings on startup
      .disableCache(CacheFlag.ACTIVITY, CacheFlag.EMOTE, CacheFlag.CLIENT_STATUS, CacheFlag.ONLINE_STATUS).build();
  }
  
  
  //gotta have a sweet ascii banner or what are we even doing in life?
  private static void printBotBanner() {
    InputStream is = new Jankbot().getClass().getClassLoader().getResourceAsStream("banner");
    byte[] encoded;
    try {
      encoded = is.readAllBytes();
    } catch (IOException e) {
      return; //guess we're not printing a dope ascii banner to the console?
    }
    String banner = new String(encoded, StandardCharsets.UTF_8);
    System.out.println(banner);
  }

}
