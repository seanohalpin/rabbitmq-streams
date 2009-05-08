/*
 * Dispatcher.scala
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package net.lshift.feedshub.plugin.archive

import scala.actors.Actor
import scala.actors.Actor._

import net.lshift.feedshub.harness._
import com.rabbitmq.client.QueueingConsumer.Delivery;

case class Entry(bytes : Array[Byte], key : String, ack : (() => Unit))

class Dispatcher extends Actor {
    private val destinations : Map[String, Destination] = Map()

    def act {
        loop {
            react {
                case Entry(bytes, key, ack) =>
                    destinations.get(key) match {
                        case Some(destination) =>
                            destination ! NewEntry(bytes, ack)
                        case None => true
                    }
            }
        }
    }
}
