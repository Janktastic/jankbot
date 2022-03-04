package janktastic.jankbot.config;

import java.io.File;
import java.io.IOException;

import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JankBotConfigFactory {
  public static JankBotConfig buildConfig() throws StreamReadException, DatabindException, IOException {
    ObjectMapper mapper = new ObjectMapper();
    File file = new File("config.json");
    JankBotConfig config = mapper.readValue(file, JankBotConfig.class);
    return config;
  }
}
