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

package uk.gov.hmrc.fileupload.filters

import java.util.UUID

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.codahale.metrics.MetricRegistry
import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}
import play.api.http.HeaderNames
import play.api.mvc.Action
import play.api.mvc.Results.Ok
import play.api.test.FakeRequest

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class UserAgentRequestFilterSpec extends FunSuite with BeforeAndAfterAll with Matchers with Eventually {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  override def afterAll(): Unit = {
    super.afterAll()
    system.terminate()
  }

  val dfsFrontend = "dfs-frontend"
  val whitelist =
    Set(dfsFrontend, "voa-property-linking-frontend").map(UserAgent.apply)

  val nginxChecks = "nginx-health"
  val blacklist =
    Set(nginxChecks).map(UserAgent.apply)

  implicit val patience: PatienceConfig = PatienceConfig(5.seconds, 1.second)

  def withFilter[T](block: (UserAgentRequestFilter, MetricRegistry) => T): T = {
    val metrics = new MetricRegistry
    val filter = new UserAgentRequestFilter(metrics, whitelist, blacklist)
    block(filter, metrics)
  }

  val endAction = Action(Ok("boom"))

  def grabTimerCounts(metrics: MetricRegistry): Map[String, Long] =
    metrics.getTimers.asScala.map { case (name, timer) =>
      name -> timer.getCount
    }.toMap

  test("Timer created for User-Agent when header is white listed") {
    withFilter { (filter, metrics) =>
      val rh = FakeRequest().withHeaders(HeaderNames.USER_AGENT -> dfsFrontend)
      filter(endAction)(rh).run()

      eventually {
        metrics.timer(s"request.user-agent.$dfsFrontend").getCount shouldBe 1
      }

      filter(endAction)(rh).run()

      eventually {
        grabTimerCounts(metrics) should contain(s"request.user-agent.$dfsFrontend" -> 2)
      }
    }
  }

  test("Timer for NoUserAgent is incremented when User-Agent header is missing") {
    withFilter { (filter, metrics) =>
      val rh = FakeRequest()
      filter(endAction)(rh).run()

      eventually {
        grabTimerCounts(metrics) should contain("request.user-agent.NoUserAgent" -> 1)
      }
    }
  }

  test("Timer for UnknownUserAgent is incremented when User-Agent header not in whitelist") {
    withFilter { (filter, metrics) =>
      val rh = FakeRequest().withHeaders(HeaderNames.USER_AGENT -> UUID.randomUUID().toString)
      filter(endAction)(rh).run()

      eventually {
        grabTimerCounts(metrics) should contain ("request.user-agent.UnknownUserAgent" -> 1)
      }
    }
  }

  test("Timer for User-Agent in ignore list is.. ignored") {
    withFilter { (filter, metrics) =>
      val rh = FakeRequest().withHeaders(HeaderNames.USER_AGENT -> nginxChecks)
      filter(endAction)(rh).run()
      metrics.getTimers.size() shouldBe 0
    }
  }

}
