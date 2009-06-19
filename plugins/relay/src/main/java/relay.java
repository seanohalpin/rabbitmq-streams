import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.List;

import com.rabbitmq.streams.harness.InputReader;
import com.rabbitmq.streams.harness.Server;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import com.fourspaces.couchdb.Document;
import com.rabbitmq.client.QueueingConsumer.Delivery;

public class relay extends Server {

    private final Set<String> activeTerminals = new HashSet<String>();

    protected void terminalStatusChange(String terminalId,
                                        List<JSONObject> terminalConfigs,
                                        boolean active) {
      if (active) {
        activeTerminals.add(terminalId);
      } else {
        activeTerminals.remove(terminalId);
      }
    }

    public final InputReader input = new InputReader() {

        public void handleDelivery(Delivery message) throws Exception {
            String terminalId = message.getEnvelope().getRoutingKey();
            if (activeTerminals.contains(terminalId)) {
                relay.this.output.publishWithKey(message.getBody(), terminalId);
            }
            relay.this.ack(message);
        }
    };

    public relay(JSONObject config) throws IOException {
        super(config);
        postConstructorInit();
    }

}