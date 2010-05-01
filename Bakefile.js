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
    "third_party/junit/junit.jar"];

({
  classes: {
    help: {
      summary: "Puts all the java classes and resources under lib",
      detail: [
          "Puts under lib/ everything that needs to go in the main jar",
          "including the client, service, and builtin tools; but excluding",
          "test files."].join("\n"),
      contact: "Mike Samuel <mikesamuel@gmail.com>"
    },
    actions: [tools.javac(["src/**.java"].concat(jars), "lib/**.class"),
              tools.cp("src/**.{js,txt}", "lib/**.{js,txt}"),
              tools.ls("src/org/prebake/service/tools/*.js",
                       "lib/org/prebake/service/tools/tools.txt")]
  },
  tests: {
    help: {
      summary: "Puts all the java tests classes and resources under test-lib",
      detail:  "Puts under test-lib/ everything needed for junit tests",
      contact: "Mike Samuel <mikesamuel@gmail.com>"
    },
    actions: [tools.javac(["tests/**.java", "lib/**.class"].concat(jars),
                          "test-lib/**.class")]
  },
  runtests: {
    help: 'Runs JUnit tests putting test results under reports',
    actions: [tools.junit(['test-lib/**.class', 'lib/**.class'].concat(jars),
                          'reports/tests/**.{json,html}',
                          { test_class_filter: '**Test.class' })]
  },
  docs: {
    help: "Puts javadoc under docs",
      actions: [tools.javadoc('src/**.java', 'doc/api/**.html',
                              {'protected':true})]
  }
})
