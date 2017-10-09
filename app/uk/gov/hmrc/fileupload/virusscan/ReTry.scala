/*
 * Copyright 2017 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.fileupload.virusscan

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration._
import akka.actor.Actor.Receive
import akka.pattern.pipe
import uk.gov.hmrc.fileupload.notifier.QuarantineFile

import scala.util.Success
import scala.util.Failure

object ReTry {
  private case class Retry(originalSender: ActorRef, message: Any, times: Int)

  private case class Response(originalSender: ActorRef, result: Any)

  def props(subscribe: (ActorRef, Class[_]) => Boolean, tries: Int, retryTimeOut: FiniteDuration, retryInterval: FiniteDuration, forwardTo: ActorRef): Props =
    Props(new ReTry(subscribe: (ActorRef, Class[_]) => Boolean, tries: Int, retryTimeOut: FiniteDuration, retryInterval: FiniteDuration, forwardTo: ActorRef))

}

class ReTry(subscribe: (ActorRef, Class[_]) => Boolean, val tries: Int, retryTimeOut: FiniteDuration, retryInterval: FiniteDuration, forwardTo: ActorRef) extends Actor with ActorLogging {

  import context.dispatcher
  import ReTry._

  override def preStart: Unit = {
    subscribe(self, classOf[QuarantineFile])
    subscribe(self, classOf[VirusScanRequested])
  }

  // Retry loop that keep on Re-trying the request
  def retryLoop: Receive = {

    // Response from future either Success or Failure is a Success - we propagate it back to a original sender
    case Response(originalSender, result) =>
      originalSender ! result
      context stop self

    case Retry(originalSender, message, triesLeft) =>

      // Process (Re)try here. When future completes it sends result to self
      (forwardTo ? message) (retryTimeOut) onComplete {

        case Success(result) =>
          self ! Response(originalSender, result) // sending responses via self synchronises results from futures that may come potentially in any order. It also helps the case when the actor is stopped (in this case responses will become deadletters)

        case Failure(ex) =>
          log.info(s"num of retries left: ${triesLeft}")
          if (triesLeft - 1 == 0) {// In case of last try and we got a failure (timeout) lets send Retries exceeded error
            self ! Response(originalSender, Failure(new Exception("Retries exceeded")))
          }
          else
            log.error("Error occurred: " + ex)
            log.error("num of retries exceeded")
      }

      // Send one more retry after interval
      if (triesLeft - 1 > 0)
        context.system.scheduler.scheduleOnce(retryInterval, self, Retry(originalSender, message, triesLeft - 1))

    case m @ _ =>
      log.error("No handling defined for message: " + m)

  }

  // Initial receive loop
  def receive: Receive = {

    case message @ _ =>
      self ! Retry(sender, message, tries)

      // Lets swap to a retry loop here.
      context.become(retryLoop, false)

  }

}
