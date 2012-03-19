/*
 * Copyright (c) 2012 Orderly Ltd. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */
import sbt._

object Dependencies {
  val resolutionRepos = Seq(
    ScalaToolsSnapshots
  )

  object V {
    val hadoop    = "0.20.2"    
    val hive      = "0.8.1"
    val logging   = "1.1.1"
    val specs2    = "1.8"
  }

  object Libraries {
    val hadoop      = "org.apache.hadoop"          %  "hadoop-core"          % V.hadoop
    val hive        = "org.apache.hive"            %  "hive-common"          % V.hive
    val logging     = "commons-logging"            %  "commons-logging"      % V.logging
    val specs2      = "org.specs2"                 %% "specs2"               % V.specs2      % "test"
  }
}