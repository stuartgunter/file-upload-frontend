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

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit}
import cats.data.Xor
import org.scalactic.source.Position
import org.scalatest.Matchers
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Seconds, Span}
import play.api.{Configuration, Environment}
import play.api.libs.json.Json
import uk.gov.hmrc.fileupload.notifier.NotifierService.NotifySuccess
import uk.gov.hmrc.fileupload.notifier.{CommandHandler, MarkFileAsClean, QuarantineFile}
import uk.gov.hmrc.fileupload.virusscan.ScanningService.{ScanResult, ScanResultFileClean}
import uk.gov.hmrc.fileupload._
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

class ScannerActorSpec extends TestKit(ActorSystem("scanner")) with ImplicitSender with UnitSpec with Matchers with Eventually with StopSystemAfterAll {

  import scala.concurrent.ExecutionContext.Implicits.global

  implicit override val patienceConfig = PatienceConfig(timeout = scaled(Span(5, Seconds)), interval = scaled(Span(2, Seconds)))

  "ScannerActor" should {
    "scan files" in new ScanFixture {
      val eventsToSend = fillEvents(5)
      send(eventsToSend)

      eventually {
        collector shouldBe expectedCollector(eventsToSend)
      }

      collector = List.empty[Any]
      val newEventToSend = fillEvents(1)
      send(newEventToSend)

      eventually {
        collector shouldBe expectedCollector(newEventToSend)
      }
    }

    "retry scanning if the scanner is unavailable" in {
      def subscribe = (_: ActorRef, _: Class[_]) => true
      val scannerStub = new ScannerStub
      var collector = List.empty[Any]


      val commandHandler = new CommandHandler {
        def notify(command: AnyRef)(implicit ec: ExecutionContext) = {
          collector = collector.::(command)
          Future.successful(Xor.Right(NotifySuccess))
        }
      }
      val scannerActor = system.actorOf(ScannerActor.props(subscribe, scannerStub, commandHandler))
      val retry = system.actorOf(ReTry.props(subscribe, tries = 5, retryTimeOut = 500 millis, retryInterval = 100 millis, scannerActor))

      val event = QuarantineFile(EnvelopeId(), FileId(), FileRefId(), 0, "name", "pdf", 10, Json.obj())

      retry ! event

      eventually {
        scannerStub.numberOfTimesCalled shouldBe 5
      }(PatienceConfig(timeout = 60000.millis, 1000.millis), Position.here)
    }
  }

    "retry scanning if the scanner is unavailable test" in {
      def subscribe = (_: ActorRef, _: Class[_]) => true
      val scannerStub = new ScannerStub
      var collector = List.empty[Any]


      val commandHandler = new CommandHandler {
        def notify(command: AnyRef)(implicit ec: ExecutionContext) = {
          collector = collector.::(command)
          Future.successful(Xor.Right(NotifySuccess))
        }
      }

      import play.api._
      import play.api.inject._
      import play.core.DefaultWebCommands

      val env = Environment.simple(new java.io.File("."), Mode.Dev)
      val configuration = Configuration.load(env)
      val context = ApplicationLoader.Context(
        environment = env,
        sourceMapper = None,
        webCommands = new DefaultWebCommands(),
        initialConfiguration = configuration
      )

      val app = new ApplicationModule(context) {
        override lazy val actorSystem = system
        override lazy val scanBinaryData: (EnvelopeId, FileId, FileRefId) => Future[ScanResult] = scannerStub
      }

      val event = QuarantineFile(EnvelopeId(), FileId(), FileRefId(), 0, "name", "pdf", 10, Json.obj())

      app.scannerActor ! event

      eventually {
        scannerStub.numberOfTimesCalled shouldBe 2
      }
    }


  trait ScanFixture {
    val envelopeId = EnvelopeId()
    val fileId = FileId()
    val fileRefId = FileRefId()

    def subscribe = (_: ActorRef, _: Class[_]) => true

    var collector = List.empty[Any]

    def scanBinaryData(envelopeId: EnvelopeId, fileId: FileId, fileRefId: FileRefId) = {
      Thread.sleep(100)
      collector = collector.::(fileRefId)
      Future.successful(Xor.right(ScanResultFileClean))
    }

    val commandHandler = new CommandHandler {
      def notify(command: AnyRef)(implicit ec: ExecutionContext) = {
        collector = collector.::(command)
        Future.successful(Xor.Right(NotifySuccess))
      }
    }

    def fillEvents(n: Int) = List.fill(n) {
      QuarantineFile(EnvelopeId(), FileId(), FileRefId(), 0, "name", "pdf", 10, Json.obj())
    }

    def expectedCollector(events: List[QuarantineFile]): List[Any] =
      events.reverse.flatMap(e => {
        List(MarkFileAsClean(e.id, e.fileId, e.fileRefId), e.fileRefId)
      })

    val actor = system.actorOf(ScannerActor.props(subscribe, scanBinaryData, commandHandler))

    def send(eventsToSend: List[QuarantineFile]) =
      eventsToSend.foreach {
        actor ! _
    }
  }
}
