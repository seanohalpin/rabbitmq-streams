import com.rabbitmq.streams.harness.InputReader;
import com.rabbitmq.streams.harness.InputMessage;
import com.rabbitmq.streams.harness.PipelineComponent;
import com.rabbitmq.streams.harness.PluginException;
import net.sf.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

public class logger extends PipelineComponent {

  public final InputReader input = new InputReader() {

    @Override
    public void handleMessage(InputMessage msg) throws PluginException {
      BufferedReader br =
        new BufferedReader(new InputStreamReader(new ByteArrayInputStream(msg.body())));
      StringBuilder sb = new StringBuilder();
      try {
        String line = br.readLine();
        while (null != line) {
          sb.append(line);
          sb.append(newline);
          line = br.readLine();
        }
      }
      catch (IOException ex) {
        throw new PluginException(ex);
      }

      logger.this.log.debug(sb.toString());
    }
  };

  @Override
  public void configure(JSONObject config) {
    registerInput("input", input);
  }
}
