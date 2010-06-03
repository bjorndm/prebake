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

package org.prebake.service.www;

import org.prebake.service.EntityLinker;

import java.io.IOException;

import javax.annotation.Nullable;

import com.google.caja.lexer.escaping.Escaping;

/**
 * A linker that creates link for entities served by the {@link MainServlet}.
 * The links are relative to any file in a directory under the root directory.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
final class ServletEntityLinker implements EntityLinker {
  static final ServletEntityLinker INSTANCE = new ServletEntityLinker();

  private ServletEntityLinker() { /* singleton */ }

  public void endLink(
      String entityType, @Nullable String entityName, Appendable out)
      throws IOException {
    out.append("</a>");
  }

  public boolean linkEntity(
      String entityType, @Nullable String entityName, Appendable out)
      throws IOException {
    String entityDir, entityPath;
    if ("product".equals(entityType)) {
      if (entityName == null) { return false; }
      entityDir = "plan";
      entityPath = entityName;
    } else if ("plan".equals(entityType)) {
      if (entityName != null) { return false; }
      entityDir = "plan";
      entityPath = "index.html";
    } else if ("tool".equals(entityType)) {
      if (entityName == null) { return false; }
      entityDir = "tools";
      entityPath = entityName;
    } else {
      return false;
    }
    out.append("<a href=\"../");
    Escaping.escapeXml(entityDir, false, out);
    out.append('/');
    Escaping.escapeXml(entityPath, false, out);
    out.append("\">");
    return true;
  }
}
