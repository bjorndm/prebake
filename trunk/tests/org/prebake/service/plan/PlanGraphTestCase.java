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
import org.prebake.core.Glob;
import org.prebake.core.GlobRelation;
import org.prebake.core.ImmutableGlobSet;
import org.prebake.util.PbTestCase;

import java.io.IOException;
import java.nio.file.Path;

import com.google.common.collect.ImmutableList;

import org.junit.After;
import org.junit.Before;

public abstract class PlanGraphTestCase extends PbTestCase {
  protected Path source;

  @Before
  public void setUp() throws IOException {
    source = this.fileSystemFromAsciiArt("/", "foo").getPath("/foo");
  }

  @After
  public void tearDown() throws IOException {
    source.getFileSystem().close();
    source = null;
  }

  protected PlanGraph.Builder builder(BoundName... concreteProductNames) {
    int n = concreteProductNames.length;
    GlobRelation emptyGlobRel = new GlobRelation(
        ImmutableGlobSet.of(ImmutableList.<Glob>of()),
        ImmutableGlobSet.of(ImmutableList.<Glob>of()));
    Product[] products = new Product[n];
    for (int i = n; --i >= 0;) {
      products[i] = new Product(
          concreteProductNames[i], null,
          emptyGlobRel, ImmutableList.<Action>of(), false, null, source);
    }
    return PlanGraph.builder(products);
  }
}
