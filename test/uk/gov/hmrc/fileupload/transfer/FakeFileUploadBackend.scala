/*
 * Copyright 2016 HM Revenue & Customs
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

package uk.gov.hmrc.fileupload.transfer

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._
import com.github.tomakehurst.wiremock.verification.LoggedRequest
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, Suite}
import play.api.http.Status
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId}

import scala.collection.JavaConverters._

trait FakeFileUploadBackend extends BeforeAndAfterAll with ScalaFutures {
  this: Suite =>

  lazy val fileUploadBackendPort = 8080

  lazy val backend = new WireMockServer(wireMockConfig().port(fileUploadBackendPort))

  final lazy val fileUploadBackendBaseUrl = s"http://localhost:$fileUploadBackendPort"

  override def beforeAll() = {
    super.beforeAll()
    backend.start()
  }

  override def afterAll() = {
    super.afterAll()
    backend.stop()
  }

  def respondToEnvelopeCheck(envelopeId: EnvelopeId, status: Int = Status.OK, body: String = "") = {
    backend.addStubMapping(
      get(urlPathMatching(s"/file-upload/envelopes/${envelopeId.value}"))
        .willReturn(new ResponseDefinitionBuilder()
          .withBody(body)
          .withStatus(status))
        .build())
  }

  def responseToUpload(envelopeId: EnvelopeId, fileId: FileId, status: Int = Status.OK, body: String = "") = {
    backend.addStubMapping(
      put(urlPathMatching(fileContentUrl(envelopeId, fileId)))
        .willReturn(new ResponseDefinitionBuilder()
          .withBody(body)
          .withStatus(status))
        .build())
  }

  def respondToCreateEnvelope(envelopeIdOfCreated: EnvelopeId) = {
    backend.addStubMapping(
      post(urlPathMatching(s"/file-upload/envelopes"))
        .willReturn(new ResponseDefinitionBuilder()
            .withHeader("Location", s"$fileUploadBackendBaseUrl/file-upload/envelopes/${envelopeIdOfCreated.value}")
          .withStatus(Status.CREATED))
        .build())
  }

  def responseToDownloadFile(envelopeId: EnvelopeId, fileId: FileId, textBody: String = "", status: Int = Status.OK) = {
    backend.addStubMapping(
      get(urlPathMatching(fileContentUrl(envelopeId, fileId)))
        .willReturn(new ResponseDefinitionBuilder()
          .withBody(textBody)
          .withStatus(status))
        .build())
  }

  def stubResponseForSendMetadata(envelopeId: EnvelopeId, fileId: FileId, metadata: JsObject = Json.obj("foo" -> "bar"),
                                  status: Int = Status.OK, body: String = "") = {
    backend.addStubMapping(
      put(urlMatching(metadataContentUrl(envelopeId, fileId)))
        .willReturn(
          new ResponseDefinitionBuilder()
            .withStatus(status)
            .withBody(body)
        ).build()
    )
  }

  def uploadedFile(envelopeId: EnvelopeId, fileId: FileId): Option[LoggedRequest] = {
    backend.findAll(putRequestedFor(urlPathMatching(fileContentUrl(envelopeId, fileId)))).asScala.headOption
  }

  def eventQuarantinedTriggered() = {
    backend.verify(postRequestedFor(urlEqualTo("/file-upload/events/Quarantined")))
  }

  private def fileContentUrl(envelopeId: EnvelopeId, fileId: FileId) = {
    s"/file-upload/envelopes/$envelopeId/files/$fileId/content"
  }

  private def metadataContentUrl(envelopId: EnvelopeId, fileId: FileId) = {
    s"/file-upload/envelopes/$envelopId/files/$fileId/metadata"
  }
}
