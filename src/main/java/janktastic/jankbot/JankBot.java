package janktastic.jankbot;

import static net.dv8tion.jda.api.requests.GatewayIntent.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;

import janktastic.jankbot.config.JankBotConfig;
import janktastic.jankbot.config.JankBotConfigFactory;
import janktastic.youtube.YoutubeSearch;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

public class JankBot {
  private static JankBotConfig jankBotConfig;
  private static String discordToken;
  private static YoutubeSearch youtubeSearch;
  // default can be overridden by config

  public static void main(String[] args) throws Exception {
    // TODO: allow passing in config file path
    jankBotConfig = JankBotConfigFactory.buildConfig();
    youtubeSearch = new YoutubeSearch(jankBotConfig.getGoogleApiKey());
    discordToken = jankBotConfig.getDiscordBotToken();

    printBotBanner();
    System.out.println("Initializing JankBot...");

    JDABuilder.create(discordToken, GUILD_MESSAGES, GUILD_VOICE_STATES, GUILD_MESSAGE_REACTIONS)
        .addEventListeners(new DiscordEventHandler(jankBotConfig, youtubeSearch))
        .setMemberCachePolicy(MemberCachePolicy.ALL).enableIntents(GatewayIntent.GUILD_MEMBERS)
        // disable jda cache for now to prevent warnings on startup
        .disableCache(CacheFlag.ACTIVITY, CacheFlag.EMOTE, CacheFlag.CLIENT_STATUS, CacheFlag.ONLINE_STATUS).build();
   
  }

  private static void printBotBanner() {
    InputStream is = new JankBot().getClass().getClassLoader().getResourceAsStream("banner");
    byte[] encoded;
    try {
      encoded = is.readAllBytes();
    } catch (IOException e) {
      return;
    }
    String banner = new String(encoded, StandardCharsets.UTF_8);
    System.out.println(banner);
  }

}
