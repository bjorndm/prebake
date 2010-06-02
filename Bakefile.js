// Copyright 2010, Mike Samuel
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// This is a prebake plan file, the PreBake analogue of a Makefile.
// See http://code.google.com/p/prebake/wiki/PlanFile for details.

var jars = [
    "third_party/bdb/je.jar",
    "third_party/guava-libraries/guava.jar",
    "third_party/fast-md5/fast-md5.jar",
    "third_party/rhino/js.jar",
    "third_party/findbugs/lib/jsr305.jar",
    "third_party/junit/junit.jar",
    "third_party/caja/caja.jar",
    "third_party/caja/htmlparser.jar",
    "third_party/caja/json_simple/json_simple.jar",
    "third_party/gxp/gxp-snapshot.jar",
    "third_party/jetty/lib/servlet-api-2.5.jar",
    "third_party/jetty/lib/jetty-continuation-7.0.2.v20100331.jar",
    "third_party/jetty/lib/jetty-http-7.0.2.v20100331.jar",
    "third_party/jetty/lib/jetty-io-7.0.2.v20100331.jar",
    "third_party/jetty/lib/jetty-server-7.0.2.v20100331.jar",
    "third_party/jetty/lib/jetty-servlet-7.0.2.v20100331.jar",
    "third_party/jetty/lib/jetty-util-7.0.2.v20100331.jar"
    ];

({
  classes: {
    help: {
      summary: "Puts all the java classes and resources under lib",
      detail: [
          "Puts under lib/ all the classes that needs to go in the main jars",
          "including the client and service classes; but excluding tests."
          ].join("\n"),
      contact: "Mike Samuel <mikesamuel@gmail.com>"
    },
    actions: [tools.javac(["src///**.java", "genfiles///**.java"].concat(jars),
                          "lib///**.class")]
  },
  resources: {
    help: {
      summary: "Puts all the resources under lib",
      detail: [
          "Puts under lib/ all the data files that needs to go in the main jars",
          "including the builtin tools."
          ].join("\n"),
      contact: "Mike Samuel <mikesamuel@gmail.com>"
    },
    actions: [tools.cp("src/org/prebake/service/**.{css,js,txt}",
                       "lib/org/prebake/service/**.{css,js,txt}"),
              // Make a list of the builtin tools.
              tools.ls("src/org/prebake/service/tools/*.js",
                       "lib/org/prebake/service/tools/tools.txt")]
  },
  tests: {
    help: {
      summary: "Puts all the java tests classes and resources under test-lib",
      detail:  "Puts under test-lib/ everything needed for junit tests",
      contact: "Mike Samuel <mikesamuel@gmail.com>"
    },
    actions: [tools.javac(["tests///**.java", "lib///**.class"].concat(jars),
                          "test-lib///**.class")]
  },
  gxps: tools.gxpc("src///**.gxp", "genfiles///**.java", { warn: "error" }),
  runtests: {
    help: "Runs JUnit tests putting test results under reports",
    actions: [tools.junit(
        ["test-lib///**.class",
         "lib///**",
         "jars/service.jar"].concat(jars),
        "reports/tests///**.{json,html,css}",
        { test_class_filter: "**Test.class",
          runner_classpath: jars })]
  },
  docs: {
    help: "Puts javadoc under docs",
    actions: [tools.javadoc(["src/**.java"].concat(jars), "doc/api///**.html",
                            {visibility: "protected"})]
  },
  checks: {
    help: "Runs FindBugs over the source code (and test code)",
    actions: [tools.findbugs(["{test-lib,lib}///**.class"].concat(jars),
                             "reports/bugs///index.html",
                             { effort: "max", priority: "medium" })]
  },
  jars: {
    help: "Packages service and client into separate jars",
    actions: [
        tools.jar(
            "lib///**.class", "jars/client.jar",
            {
              manifest: {
                "Main-Class": "org.prebake.client.Main",
                "Class-Path": "../third_party/guava-libraries/guava.jar"
              }
            }),
        tools.jar(
            "lib///**.{class,js,css,txt}", "jars/service.jar",
            {
              manifest: {
                "Main-Class": "org.prebake.service.Main",
                "Class-Path": Array.map(
                    jars, function (jar) { return "../" + jar; }).join(" ")
              }
            })]
  }
})
