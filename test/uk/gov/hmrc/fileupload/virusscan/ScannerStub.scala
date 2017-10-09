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

import java.net.ConnectException
import java.util.concurrent.Executors

import uk.gov.hmrc.fileupload.{EnvelopeId, FileId, FileRefId}
import uk.gov.hmrc.fileupload.virusscan.ScanningService.ScanResult

import scala.concurrent.{ExecutionContext, Future}

class ScannerStub extends ((EnvelopeId, FileId, FileRefId) => Future[ScanResult]) {

  implicit val ec = ExecutionContext.fromExecutor(Executors.newSingleThreadScheduledExecutor())


  var numberOfTimesCalled = 0

  override def apply(envelopeId: EnvelopeId, fileId: FileId, fileRefId: FileRefId): Future[ScanResult] =
  Future {
    numberOfTimesCalled = numberOfTimesCalled + 1
    throw new ConnectException("where did the scanner go?")
  }

}
