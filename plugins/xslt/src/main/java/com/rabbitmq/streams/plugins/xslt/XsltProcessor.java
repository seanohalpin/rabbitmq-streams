package com.rabbitmq.streams.plugins.xslt;

import com.rabbitmq.streams.harness.InputReader;
import com.rabbitmq.streams.harness.InputMessage;
import com.rabbitmq.streams.harness.PipelineComponent;
import com.rabbitmq.streams.harness.PluginBuildException;
import com.rabbitmq.streams.harness.PluginException;
import net.sf.json.JSONObject;

import javax.xml.transform.*;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;

public class XsltProcessor extends PipelineComponent {
  private Transformer transformer;

  private final ErrorListener xsltErrorLogger = new ErrorListener() {

    public void error(TransformerException exception) throws TransformerException {
      log.error(exception);
    }

    public void fatalError(TransformerException exception) throws TransformerException {
      log.fatal(exception);
    }

    public void warning(TransformerException exception) throws TransformerException {
      log.warn(exception);
    }
  };

  public void configure(final JSONObject config) throws PluginBuildException {
    if(null == config) throw new PluginBuildException("Cannot build plugin with null configuration");

    String xsltUrl = config.getString("stylesheet_url");
    InputStream content;
    try {
      URLConnection connection = new URL(xsltUrl).openConnection();
      connection.connect();
      content = (InputStream) connection.getContent();
    }
    catch (IOException ex) {
      ex.printStackTrace();
      throw new PluginBuildException("Unable to read stylesheet", ex);
    }
    StreamSource xsltSource = new StreamSource(content);
    TransformerFactory factory = TransformerFactory.newInstance();
    factory.setErrorListener(xsltErrorLogger);
    Transformer temp;
    try {
      temp = factory.newTransformer(xsltSource);
    }
    catch (TransformerConfigurationException e) {
      log.fatal(e);
      throw new PluginBuildException("Cannot compile configured stylesheet", e);
    }
    transformer = temp;
    transformer.setErrorListener(xsltErrorLogger);

    registerInput("input", input);
  }

  InputReader input = new InputReader() {

    @Override
    public void handleMessage(InputMessage msg, JSONObject config) throws PluginException {
      StreamSource xmlSource = new StreamSource(new ByteArrayInputStream(msg.body()));
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      StreamResult result = new StreamResult(output);

      try {
        transformer.transform(xmlSource, result);
        XsltProcessor.this.publishToChannel("output", msg.withBody(output.toString()));
      }
      catch (Exception e) {
        throw new PluginException(e);
      }
    }

  };
}

