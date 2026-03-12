/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.ratelimitedallowlist.models

import play.api.mvc.QueryStringBindable

import scala.util.Try

enum ReportFrequency:
  case daily, weekly
object ReportFrequency:
  given QueryStringBindable[ReportFrequency] with
    override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, ReportFrequency]] =
      summon[QueryStringBindable[String]]
        .bind(key, params)
        .map {
          _.flatMap { raw => 
            Try(ReportFrequency.valueOf(raw)).toEither.left.map(_.getMessage)
          }
        }

    override def unbind(key: String, value: ReportFrequency): String =
      summon[QueryStringBindable[String]].unbind(key, value.toString)
      


final case class AllowListReportQueryParams(frequency: ReportFrequency)

object AllowListReportQueryParams:

  given QueryStringBindable[AllowListReportQueryParams] with
    override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, AllowListReportQueryParams]] =
      for {
        freq <- summon[QueryStringBindable[ReportFrequency]].bind("frequency", params)
      } yield {
        freq match
          case Right(f) => Right(AllowListReportQueryParams(f))
          case _        => Left("Unable to bind AllowListReportQueryParams")
      }

    override def unbind(key: String, value: AllowListReportQueryParams): String =
      summon[QueryStringBindable[ReportFrequency]].unbind("frequency", value.frequency)
