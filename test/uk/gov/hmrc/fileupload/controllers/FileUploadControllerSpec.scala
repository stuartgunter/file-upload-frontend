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

package uk.gov.hmrc.fileupload.controllers

import java.io.File

import play.api.http.Status
import play.api.libs.Files
import play.api.mvc.MultipartFormData.{BadPart, MissingFilePart}
import play.api.mvc.{MultipartFormData, Result}
import play.api.test.Helpers._
import play.api.test.{FakeHeaders, FakeRequest}
import uk.gov.hmrc.fileupload.connectors.{Envelope, FileUploadConnector, InvalidEnvelope, ValidEnvelope}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class FileUploadControllerSpec extends UnitSpec {

  val validEnvelopeId = "1234567890"
  val validFileId = "0987654321"

  val controller = new FileUploadController {
    override lazy val fileUploadConnector = new FileUploadConnector {
      override def retrieveEnvelope(envelopeId: String): Envelope = envelopeId match {
        case "INVALID" => InvalidEnvelope
        case _ => ValidEnvelope(validEnvelopeId, Seq(validFileId))
      }
    }
  }

  def filePart(name:String) = MultipartFormData.FilePart(key         = name,
                                                         filename    = name,
                                                         contentType = Some("text/plain"),
                                                         ref         = Files.TemporaryFile("pre_", "txt"))

  def createUploadRequest(successRedirectURL:Option[String] = Some("http://somewhere.com/success"),
                          failureRedirectURL:Option[String] = Some("http://somewhere.com/failure"),
                          envelopeId:Option[String] = Some(validEnvelopeId),
                          fileIds:Seq[String] = Seq("12345"),
                          headers:Seq[(String, Seq[String])] = Seq()) = {
    var params = Map[String, Seq[String]]()

    if (successRedirectURL.isDefined) params = params + ("successRedirect" -> Seq(successRedirectURL.get))
    if (failureRedirectURL.isDefined) params = params + ("failureRedirect" -> Seq(failureRedirectURL.get))
    if (envelopeId.isDefined) params = params + ("envelopeId" -> Seq(envelopeId.get))

    val files = fileIds.map { filePart }

    val multipartBody = MultipartFormData[Files.TemporaryFile](params, files, Seq[BadPart](), Seq[MissingFilePart]())

    FakeRequest(method = "POST", uri = "/upload", headers = FakeHeaders(headers), body = multipartBody)
  }

  "validation - POST /upload" should {
    "return 303 (redirect) to the `successRedirect` parameter if a valid request is received" in {
      val successRedirectURL = "http://somewhere.com/success"
      val fakeRequest = createUploadRequest(successRedirectURL = Some(successRedirectURL))

      val result: Future[Result] = controller.upload().apply(fakeRequest)
      status(result) shouldBe Status.SEE_OTHER
      redirectLocation(result) shouldBe Some(successRedirectURL)
    }

    "return 303 (redirect) to the `failureRedirect` if the `successRedirect` parameter is empty" in {
      val failureRedirectURL = "http://somewhere.com/failure"
      val fakeRequest = createUploadRequest(successRedirectURL = Some(""), failureRedirectURL = Some(failureRedirectURL))

      val result: Future[Result] = controller.upload().apply(fakeRequest)
      status(result) shouldBe Status.SEE_OTHER
      redirectLocation(result) shouldBe Some(failureRedirectURL + "?invalidParam=successRedirect")
    }

    "return 303 (redirect) back to the requesting page if the `failureRedirect` parameter is empty" in {
      val originalURL = "http://somewhere.com/origin"
      val fakeRequest = createUploadRequest(failureRedirectURL = Some(""), headers = Seq("Referer" -> Seq(originalURL)))

      val result: Future[Result] = controller.upload().apply(fakeRequest)
      status(result) shouldBe Status.SEE_OTHER
      redirectLocation(result) shouldBe Some(originalURL + "?invalidParam=failureRedirect")
    }

    "return 303 (redirect) to the `failureRedirect` if the `envelopeId` parameter is empty" in {
      val failureRedirectURL = "http://somewhere.com/failure"
      val fakeRequest = createUploadRequest(envelopeId = Some(""), failureRedirectURL = Some(failureRedirectURL))

      val result: Future[Result] = controller.upload().apply(fakeRequest)
      status(result) shouldBe Status.SEE_OTHER
      redirectLocation(result) shouldBe Some(failureRedirectURL + "?invalidParam=envelopeId")
    }

    "return 303 (redirect) to the `failureRedirect` if the `successRedirect` parameter is not present" in {
      val failureRedirectURL = "http://somewhere.com/failure"
      val fakeRequest = createUploadRequest(successRedirectURL = None, failureRedirectURL = Some(failureRedirectURL))

      val result: Future[Result] = controller.upload().apply(fakeRequest)
      status(result) shouldBe Status.SEE_OTHER
      redirectLocation(result) shouldBe Some(failureRedirectURL + "?invalidParam=successRedirect")
    }

    "return 303 (redirect) back to the requesting page if the `failureRedirect` parameter is not present" in {
      val originalURL = "http://somewhere.com/origin"
      val fakeRequest = createUploadRequest(failureRedirectURL = None, headers = Seq("Referer" -> Seq(originalURL)))

      val result: Future[Result] = controller.upload().apply(fakeRequest)
      status(result) shouldBe Status.SEE_OTHER
      redirectLocation(result) shouldBe Some(originalURL + "?invalidParam=failureRedirect")
    }

    "return 303 (redirect) to the `failureRedirect` if the `envelopeId` parameter is not present" in {
      val failureRedirectURL = "http://somewhere.com/failure"
      val fakeRequest = createUploadRequest(envelopeId = None, failureRedirectURL = Some(failureRedirectURL))

      val result: Future[Result] = controller.upload().apply(fakeRequest)
      status(result) shouldBe Status.SEE_OTHER
      redirectLocation(result) shouldBe Some(failureRedirectURL + "?invalidParam=envelopeId")
    }

    "return 303 (redirect) to the `failureRedirect` if no `fileIds` (files) are present" in {
      val failureRedirectURL = "http://somewhere.com/failure"
      val fakeRequest = createUploadRequest(fileIds = Seq(), failureRedirectURL = Some(failureRedirectURL))

      val result: Future[Result] = controller.upload().apply(fakeRequest)
      status(result) shouldBe Status.SEE_OTHER
      redirectLocation(result) shouldBe Some(failureRedirectURL + "?invalidParam=file")
    }

    "return 303 (redirect) to the `failureRedirect` if the `envelopeId` and `successRedirect` parameters and no `fileIds` (files) are not present" in {
      val failureRedirectURL = "http://somewhere.com/failure"
      val fakeRequest = createUploadRequest(fileIds = Seq(), failureRedirectURL = Some(failureRedirectURL), successRedirectURL = None, envelopeId = None)

      val result: Future[Result] = controller.upload().apply(fakeRequest)
      status(result) shouldBe Status.SEE_OTHER

      val loc = redirectLocation(result).get.split("[?&]")
      loc should contain allOf (failureRedirectURL, "invalidParam=successRedirect", "invalidParam=envelopeId", "invalidParam=file")
    }

    "return 303 (redirect) back to the requesting page if no parameters (and no file) are present" in {
      val originalURL = "http://somewhere.com/origin"
      val fakeRequest = createUploadRequest(fileIds = Seq(), successRedirectURL = None, envelopeId = None, failureRedirectURL = None, headers = Seq("Referer" -> Seq(originalURL)))

      val result: Future[Result] = controller.upload().apply(fakeRequest)
      status(result) shouldBe Status.SEE_OTHER

      val loc = redirectLocation(result).get.split("[?&]")
      loc should contain allOf (originalURL, "invalidParam=successRedirect", "invalidParam=failureRedirect", "invalidParam=envelopeId", "invalidParam=file")
    }

    "return 400 (BadRequest) if no parameters are present and referer cannot be established" in {
      val fakeRequest = createUploadRequest(fileIds = Seq(), successRedirectURL = None, envelopeId = None, failureRedirectURL = None)

      val result: Future[Result] = controller.upload().apply(fakeRequest)
      status(result) shouldBe Status.BAD_REQUEST
    }

    "return 303 (redirect) to the `failureRedirect` if the `envelopeId` is invalid" in {
      val failureRedirectURL = "http://somewhere.com/failure"
      val fakeRequest = createUploadRequest(failureRedirectURL = Some(failureRedirectURL), envelopeId = Some("INVALID"))

      val result: Future[Result] = controller.upload().apply(fakeRequest)
      status(result) shouldBe Status.SEE_OTHER
      redirectLocation(result) shouldBe Some(failureRedirectURL + "?invalidParam=envelopeId")
    }

    "return 303 (redirect) to the `failureRedirect` if no file data is attached to the request" in {
      val failureRedirectURL = "http://somewhere.com/failure"
      val fakeRequest = createUploadRequest(failureRedirectURL = Some(failureRedirectURL), fileIds = Seq())

      val result: Future[Result] = controller.upload().apply(fakeRequest)
      status(result) shouldBe Status.SEE_OTHER
      redirectLocation(result) shouldBe Some(failureRedirectURL + "?invalidParam=file")
    }

    "return 303 (redirect) to the origin if no file data is attached to the request and no `failureRedirect`" in {
      val originalURL = "http://somewhere.com/origin"
      val fakeRequest = createUploadRequest(failureRedirectURL = None, headers = Seq("Referer" -> Seq(originalURL)), fileIds = Seq())

      val result: Future[Result] = controller.upload().apply(fakeRequest)
      status(result) shouldBe Status.SEE_OTHER

      val loc = redirectLocation(result).get.split("[?&]")
      loc should contain allOf (originalURL, "invalidParam=failureRedirect", "invalidParam=file")
    }

    "return 303 (redirect) to the `failureRedirect` if MULTIPLE file entries areattached to the request" in {
      val failureRedirectURL = "http://somewhere.com/failure"
      val fakeRequest = createUploadRequest(failureRedirectURL = Some(failureRedirectURL), fileIds = Seq("1", "2"))

      val result: Future[Result] = controller.upload().apply(fakeRequest)
      status(result) shouldBe Status.SEE_OTHER
      redirectLocation(result) shouldBe Some(failureRedirectURL + "?invalidParam=file")
    }
  }
}
