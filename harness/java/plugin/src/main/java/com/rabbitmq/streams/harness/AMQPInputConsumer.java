package com.rabbitmq.streams.harness;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.QueueingConsumer.Delivery;
import java.io.IOException;
import java.util.HashMap;
import net.sf.json.JSONObject;
import net.sf.json.JSONArray;
import static java.util.Collections.EMPTY_MAP;

import java.util.Map;

public abstract class AMQPInputConsumer implements Runnable {

  public static final String PLUGIN_VALUES_HEADER = "x-streams-plugin-values";

  protected QueueingConsumer consumer;
  protected final InputHandler handler;
  private final JSONObject originalConfiguration;
  private JSONObject staticConfiguration;
  protected final Logger log;
  protected final boolean trace;

  protected AMQPInputConsumer(QueueingConsumer consumer, InputHandler handler, JSONObject originalConfig, Logger log, boolean traceOn) {
    this.trace = traceOn;
    this.log = log;
    this.consumer = consumer;
    this.handler = handler;
    this.originalConfiguration = originalConfig;
    this.staticConfiguration = interpolateConfig(originalConfiguration, EMPTY_MAP);
  }

  protected AMQPInputConsumer(QueueingConsumer consumer, InputHandler handler, JSONObject originalConfig, Logger log) {
    this(consumer, handler, originalConfig, log, false);
  }

  /**
   * Set values in the header.
   */
  protected static void setValuesInHeader(Map<String, Object> headersToMutate, JSONObject vals) {
    headersToMutate.put(PLUGIN_VALUES_HEADER, vals);
  }

  protected static JSONObject getValuesFromHeader(Map<String, Object> headers) {
    return (headers.containsKey(PLUGIN_VALUES_HEADER)) ? JSONObject.fromObject(headers.get(PLUGIN_VALUES_HEADER)) : null;
  }

  JSONObject mergeConfigWithHeaders(Map<String, Object> headers) {
    if (headers != null) {
      JSONObject values = getValuesFromHeader(headers);
      if (values != null) {
        return interpolateConfig(originalConfiguration, values);
      }
    }
    return staticConfiguration;
  }

  protected static Object interpolateValue(Object uninterpolated, Map<String, Object> vals) {
    if (uninterpolated instanceof String) {
      String uninterpolatedString = (String) uninterpolated;
      if (uninterpolatedString.startsWith("$")) {
        String valKey = uninterpolatedString.substring(1);
        return vals.containsKey(valKey) ? vals.get(valKey) : "";
      }
      return uninterpolated;
    }
    else if (uninterpolated instanceof JSONObject) {
      return interpolateConfig((JSONObject)uninterpolated, vals);
    }
    else if (uninterpolated instanceof JSONArray) {
      JSONArray uninterpolatedArray = (JSONArray) uninterpolated;
      JSONArray result = new JSONArray();
      for (Object item : uninterpolatedArray) {
        result.add(interpolateValue(item, vals));
      }
      return result;
    }
    else return uninterpolated;
  }

  /**
   * Interpolate values given in the header into the dynamic configuration.
   * This is so that upstream components can pass on calculated values, to
   * be used for handling a particular message.
   */
  protected static JSONObject interpolateConfig(JSONObject uninterpolated, Map<String, Object> vals) {
    // This can largely be done statically, but I'm waiting until the harness
    // is refactored.
    JSONObject result = new JSONObject();
    for (Object k : uninterpolated.keySet()) {
      String key = k.toString();
      result.put(k, interpolateValue(uninterpolated.get(key), vals));
    }
    return result;
  }

  protected static class AMQPMessage extends InputMessage {
    private final Delivery delivery;
    private final Channel channel;

    private class MessageDecorator extends InputMessage {

      InputMessage inner;
      private Map<String, Object> headers;
      private byte[] body;

      MessageDecorator(InputMessage msg, byte[] body, Map<String, Object> headers) {
        inner = msg;
        this.body = body;
        this.headers = headers;
      }

      MessageDecorator(InputMessage msg, byte[] body, String key, Object val) {
        this(msg, body, null);
        Map<String, Object> h = new HashMap(1);
        h.put(key, val);
        headers = h;
      }

      @Override
      public InputMessage withHeader(String key, Object val) {
        return new MessageDecorator(this, body, key, val);
      }

      @Override
      public InputMessage withBody(byte[] body) {
        return new MessageDecorator(this, body, this.headers);
      }

      @Override
      public InputMessage withHeaders(Map<String, Object> headers) {
        Map<String, Object> hs = new HashMap(inner.headers());
        hs.putAll(headers);
        return new MessageDecorator(this, this.body, hs);
      }

      @Override
      public void ack() throws MessagingException {
        inner.ack();
      }

      public Map<String, Object> headers() {
        return (null==headers) ? inner.headers() : headers;
      }

      public byte[] body() {
        return (null==body) ? inner.body() : body;
      }

      public String routingKey() {
        return inner.routingKey();
      }

    }

    AMQPMessage(Channel channel, Delivery delivery) {
      this.channel = channel;
      this.delivery = delivery;
    }

    public Map<String, Object> headers() {
      return delivery.getProperties().headers;
    }

    public byte[] body() {
      return delivery.getBody();
    }

    public void ack() throws MessagingException {
      try {
        channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
      }
      catch (IOException ioe) {
        throw new MessagingException("Could not ack message", ioe);
      }
    }

    public String routingKey() {
      return delivery.getEnvelope().getRoutingKey();
    }

    @Override
    public InputMessage withHeader(String key, Object val) {
      return new MessageDecorator(this, null, key, val);
    }

    @Override
    public InputMessage withBody(byte[] body) {
      return new MessageDecorator(this, body, null);
    }

    @Override
    public InputMessage withHeaders(Map<String, Object> headers) {
      return new MessageDecorator(this, null, headers);
    }
  }

}

