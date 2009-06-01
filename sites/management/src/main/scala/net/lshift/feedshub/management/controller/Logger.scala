package net.lshift.feedshub.management.controller

import scala.actors.Actor
import scala.actors.Actor._
import scala.collection.mutable.HashSet
import com.rabbitmq.client._

/**
 * This is an abstract class used to consume messages from the feeshub log exchange.
 *
 * The exact log messages to be consumed need to defined using a LogBinding.
 *
 */
abstract class Logger(binding: LogBinding) extends Actor {
  val LogExchange = "feedshub/log"

  def connect {
    val listener = this
    val parameters = new ConnectionParameters
    parameters.setUsername("feedshub_admin")
    parameters.setPassword("feedshub_admin")
    parameters.setVirtualHost("/")
    val cf = new ConnectionFactory(parameters)
    val ch = cf.newConnection("localhost", 5672).createChannel

    object LogConsumer extends DefaultConsumer(ch) {
      override def handleDelivery(consumerTag: String, envelope: Envelope, properties: AMQP.BasicProperties, body: Array[Byte]) = {
        val key = envelope.getRoutingKey
        val logLevel = LogLevel.from(key.substring(0, key.indexOf(".")))
        val component = key.split("\\.").drop(1)
        listener ! LogMessage(logLevel, new String(body), component)
      }
    }

    val queue = ch.queueDeclare().getQueue
    binding.bindings.foreach(ch.queueBind(queue, LogExchange, _))
    ch.queueBind(queue, LogExchange, "#")
    ch.basicConsume(queue, true, LogConsumer)
  }

  def handlers: PartialFunction[Any, Unit] = {
    case msg@LogMessage(level, desc, component) => processMessage(msg)
    case Stop => exit("stop")
  }

  def act() = {
    loop(react(handlers))
  }

  def processMessage(message: LogMessage)

  connect
  start
}

case object Stop