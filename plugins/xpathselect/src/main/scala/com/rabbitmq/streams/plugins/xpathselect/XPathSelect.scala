package com.rabbitmq.streams.plugins.xpathselect

import com.rabbitmq.streams.harness.PipelineComponent
import net.sf.json.JSONObject
import javax.xml.xpath.XPathFactory
import javax.xml.xpath.XPathExpressionException
import com.rabbitmq.streams.harness.PluginBuildException
import com.rabbitmq.streams.harness.InputReader
import org.xml.sax.InputSource
import javax.xml.xpath.XPathConstants
import java.io.ByteArrayInputStream
import org.w3c.dom.NodeList
import javax.xml.transform.TransformerFactory
import java.io.ByteArrayOutputStream
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.dom.DOMSource
import com.rabbitmq.streams.harness.InputMessage

/**
 * Hello world!
 *
 */
class XPathSelect extends PipelineComponent {
  override def configure(config : JSONObject) {
    val compilerfactory = XPathFactory.newInstance
    val identity = TransformerFactory.newInstance().newTransformer()
    val compiler = compilerfactory.newXPath
    object input extends InputReader {
      override def handleMessage(msg : InputMessage, config : JSONObject) {
        identity.reset
        val expression = config.getString("expression")
        try {
          val res = (compiler.evaluate(expression,
                                       new InputSource(new ByteArrayInputStream(msg.body)),
                                       XPathConstants.NODESET)).asInstanceOf[NodeList]
          for {
            i <- 0 to (res.getLength - 1);
            node = res.item(i)
            buffer = new ByteArrayOutputStream();
            dest = new StreamResult(buffer);
            _ = identity.transform(new DOMSource(node), dest)
          } publishToChannel("output", msg.withBody(buffer.toByteArray))
        }
        catch {
          case ex : XPathExpressionException => log.error(ex); throw new PluginBuildException("Cannot compile expression " + expression, ex)
          case ex : Exception => log.error(ex); throw new PluginBuildException("Could not evaluate expression", ex)
        }
      }
    }
    registerInput("input", input)
  }
}

