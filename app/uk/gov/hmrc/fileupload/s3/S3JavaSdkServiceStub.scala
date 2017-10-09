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

package uk.gov.hmrc.fileupload.s3

import java.io.InputStream
import com.amazonaws.services.s3.transfer.model.UploadResult
import com.codahale.metrics.MetricRegistry

import scala.concurrent.{ExecutionContext, Future}

class S3JavaSdkServiceStub(configuration: com.typesafe.config.Config, metrics: MetricRegistry) extends S3Service {

  override def retrieveFileFromQuarantine(key: String, versionId: String)(implicit ec: ExecutionContext) = {
    ???
  }

  def upload(bucketName: String, key: String, file: InputStream, fileSize: Int): Future[UploadResult] = {
    ???
  }

  override def awsConfig = ???

  override def download(bucketName: String, key: S3KeyName) = ???

  override def download(bucketName: String, key: S3KeyName, versionId: String) = ???

  override def listFilesInBucket(bucketName: String) = ???

  override def copyFromQtoT(key: String, versionId: String) = ???

  override def getFileLengthFromQuarantine(key: String, versionId: String) = ???

  override def getBucketProperties(bucketName: String) = ???
}
