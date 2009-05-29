/*
 * Subscription.scala
 */

package net.lshift.feedshub.plugin.websubscriber

import scala.actors.Actor
import scala.actors.Actor._

import net.sf.json._

import net.lshift.feedshub.harness.Logger

import net.liftweb.util.ActorPing
import net.liftweb.util.Helpers._
import java.util.concurrent.ScheduledFuture

case object RetrieveNow
case object StopPolling
case class Retrieved(content: String, result : State)

case class State(currentUrl : String, originalUrl : String, lastUpdated : Long, interval : Int)

class Subscription(log : Logger, initialState: State, saveState: State => Unit) extends Actor {

    def loop(alarm: ScheduledFuture[AnyRef],  state: State) {
        react {
            case RetrieveNow => poll(state); loop(setAlarm(state.interval), state)
                // don't try to interrupt something that's already running,
                // just to avoid complication
            case StopPolling => alarm.cancel(false)
            case Retrieved(content, responseState) => inject(content, responseState); loop(alarm, responseState)
        }
    }

    def poll(state: State) {
        log.debug("Polling: " + state.currentUrl)
        actor {
            // Actually go and get the thing;
            // then make an updated state and pass it back
            this ! Retrieved("", state)
        }
    }

    def inject(content : String, newState : State) {
        log.debug(newState toString)
        saveState(newState)
        log.debug(content)
    }

    def setAlarm(seconds : Long) : ScheduledFuture[AnyRef] = {
        log.debug("Setting alarm for " + seconds + " seconds")
        val res = ActorPing.schedule(this, RetrieveNow, seconds * 1000)
        //log.debug("Set alarm")
        res
    }

    private val firstAlarm = setAlarm(0) // make sure we set the alarm
    def act = loop(firstAlarm, initialState)
}