package janktastic.jankbot.config;

import java.io.File;
import java.io.IOException;

import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JankBotConfigFactory {
  public static JankBotConfig buildConfig() throws StreamReadException, DatabindException, IOException {
    ObjectMapper mapper = new ObjectMapper();
    JankBotConfig config = mapper.readValue(configFile(), JankBotConfig.class);
    applyEnvOverrides(config);
    return config;
  }

  //for tools that don't need discord (SmokeTest, YoutubeLogin), the config file is used if present
  public static JankBotConfig buildConfigIfPresent() throws StreamReadException, DatabindException, IOException {
    if (configFile().exists()) {
      return buildConfig();
    }
    JankBotConfig config = new JankBotConfig();
    applyEnvOverrides(config);
    return config;
  }

  //config path can be overridden for the container, defaults to config.json in the working dir
  private static File configFile() {
    return new File(System.getenv().getOrDefault("JANKBOT_CONFIG", "config.json"));
  }

  //env vars win over the config file so docker compose can wire up yt-cipher
  private static void applyEnvOverrides(JankBotConfig config) {
    String cipherUrl = System.getenv("JANKBOT_REMOTE_CIPHER_URL");
    if (cipherUrl != null && !cipherUrl.isEmpty()) {
      config.setRemoteCipherUrl(cipherUrl);
    }
    String cipherPassword = System.getenv("JANKBOT_REMOTE_CIPHER_PASSWORD");
    if (cipherPassword != null && !cipherPassword.isEmpty()) {
      config.setRemoteCipherPassword(cipherPassword);
    }
  }
}
