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

package org.prebake.service.plan;

import org.prebake.core.BoundName;
import org.prebake.core.GlobRelation;
import org.prebake.core.MessageQueue;
import org.prebake.js.JsonSink;
import org.prebake.js.JsonSource;
import org.prebake.util.PbTestCase;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.FileSystem;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ProductTest extends PbTestCase {
  private FileSystem fs;

  @Before public void setUp() throws IOException {
    fs = fileSystemFromAsciiArt("/", "/");
  }

  @After public void tearDown() throws IOException {
    fs.close();
    fs = null;
  }

  @Test public final void testSerialForm() throws IOException {
    assertSerializesTo(
        (
         ""
         + "{"
         + "'inputs':['src/**.c','src/**.h'],"
         + "'outputs':['obj/**.o'],"
         + "'actions':["
         + "{'tool':'gcc',"
           + "'inputs':['src/p1/**.c','src/**.h'],"
           + "'outputs':['obj/**.o'],"
           + "'options':{'foo':'bar'}},"
         + "{'tool':'gcc',"
           + "'inputs':['src/p2/**.c','src/**.h'],"
           + "'outputs':['obj/p2/**.o']}"
         + "]"
         + "}"
         ).replace('\'', '"'),
        new Product(
            BoundName.fromString("foo"),
            null,
            new GlobRelation(
                globs("src/**.c", "src/**.h"),
                globs("obj/**.o")),
            ImmutableList.of(
                new Action(
                    "gcc",
                    globs("src/p1/**.c", "src/**.h"),
                    globs("obj/**.o"),
                    ImmutableMap.of("foo", "bar")),
                new Action(
                    "gcc",
                    globs("src/p2/**.c", "src/**.h"),
                    globs("obj/p2/**.o"),
                    ImmutableMap.<String, String>of())),
            false,
            null,
            fs.getPath("/foo/Bakefile.js")));

  }

  @Test public final void testSimpleParsedForm() throws IOException {
    assertParsesTo(
        new Product(
            BoundName.fromString("foo"),
            null,
            new GlobRelation(
                globs("src/**.c", "src/**.h"),
                globs("obj/**.o")),
            ImmutableList.of(
                new Action(
                    "gcc",
                    globs("src/**.c", "src/**.h"),
                    globs("obj/**.o"),
                    ImmutableMap.of("foo", "bar"))),
            false,
            null,
            fs.getPath("/foo/Bakefile.js")),
        (""
         + "{'tool':'gcc',"
         + " 'inputs':['src/**.c','src/**.h'],"
         + " 'outputs':['obj/**.o'],"
         + " 'options':{'foo':'bar'}}"
        ).replace('\'', '"'));
  }

  @Test public final void testAbstractProduct() throws IOException {
    Product p = assertParsesTo(
        new Product(
            BoundName.fromString("foo"),
            null,
            new GlobRelation(
                globs("src/*(x)/**.c", "src/**.h"),
                globs("obj/*(x)/**.o"),
                ImmutableList.of(new GlobRelation.Param(
                    "x", ImmutableSet.of("foo", "bar", "baz"), null))),
            ImmutableList.of(
                new Action(
                    "gcc",
                    globs("src/*(x)/**.c", "src/**.h"),
                    globs("obj/*(x)/**.o"),
                    ImmutableMap.of("foo", "bar"))),
            false,
            null,
            fs.getPath("/foo/Bakefile.js")),
        (""
         + "{"
         + "  'actions': ["
         + "    {'tool':'gcc',"
         + "    'inputs':['src/*(x)/**.c','src/**.h'],"
         + "    'outputs':['obj/*(x)/**.o'],"
         + "    'options':{'foo':'bar'}}"
         + "  ],"
         + "  'parameters': ["
         + "    { 'name': 'x', 'values': ['foo', 'bar', 'baz']}"
         + "  ]"
         + "}"
        ).replace('\'', '"'));
    assertEquals("foo", p.name.ident);
    assertFalse(p.isConcrete());
    Product p2 = p.withParameterValues(ImmutableMap.of("x", "bar"));
    assertSerializesTo(
        (""
         + "{"
         + "'inputs':['src/bar/**.c','src/**.h'],"
         + "'outputs':['obj/bar/**.o'],"
         + "'actions':["
           + "{'tool':'gcc',"
           + "'inputs':['src/bar/**.c','src/**.h'],"
           + "'outputs':['obj/bar/**.o'],"
           + "'options':{'foo':'bar'}}"
         + "]"
         + "}").replace('\'', '"'),
        p2);
    assertEquals("foo[\"x\":\"bar\"]", p2.name.ident);
    assertTrue(p2.isConcrete());
  }

  private void assertSerializesTo(String productJson, Product p)
      throws IOException {
    String json;
    {
      StringBuilder sb = new StringBuilder();
      JsonSink sink = new JsonSink(sb);
      p.toJson(sink);
      sink.close();
      json = sb.toString();
    }

    assertEquals(productJson, json);
    assertParsesTo(p, json);
  }

  private Product assertParsesTo(Product p, String productJson)
      throws IOException {
    MessageQueue mq = new MessageQueue();
    JsonSource src = new JsonSource(new StringReader(productJson));
    Product deserialized = Product.converter(p.name, p.source).convert(
        src.nextValue(), mq);
    for (String msg : mq.getMessages()) { System.err.println(msg); }
    assertFalse(mq.hasErrors());
    assertEquals(p, deserialized);
    return deserialized;
  }
}
