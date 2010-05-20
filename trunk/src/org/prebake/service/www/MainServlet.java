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

import org.prebake.core.Hash;
import org.prebake.js.JsonSink;
import org.prebake.service.Prebakery;
import org.prebake.service.plan.PlanGraph;
import org.prebake.service.plan.Product;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.net.URI;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;
import com.google.gxp.base.GxpContext;

public final class MainServlet extends HttpServlet {
  private final String token;
  private final Prebakery pb;

  private static final String AUTH_COOKIE_NAME = "pbauth";

  public MainServlet(String token, Prebakery pb) {
    this.token = token;
    this.pb = pb;
  }

  private boolean checkAuthorized(
      String path, HttpServletRequest req, HttpServletResponse resp)
      throws IOException {
    if (path.startsWith("/www-files/")) {
      serveStaticFile(path.substring(1), req, resp);
      return false;
    }
    if ("/auth".equals(path)) {
      String queryToken = req.getParameter("tok");
      if (token.equals(queryToken)) {
        resp.addCookie(new Cookie(AUTH_COOKIE_NAME, token));
        String continuePath = req.getParameter("continue");
        redirectTo(resp, continuePath != null ? continuePath : "/index.html");
      } else {
        resp.sendError(HttpServletResponse.SC_UNAUTHORIZED);
      }
      return false;
    } else if ("/auth-help.html".equals(path)) {
      serveAuthHelp(resp);
      return false;
    } else {
      boolean authed = false;
      Cookie[] cookies = req.getCookies();
      if (cookies != null) {
        for (Cookie cookie : cookies) {
          if (AUTH_COOKIE_NAME.equals(cookie.getName())) {
            if (cookie.getValue().equals(token)) { authed = true; }
            break;
          }
        }
      }
      if (authed) { return true; }
      redirectTo(resp, "/auth-help.html");
      return false;
    }
  }

  private static final Map<String, String> MIME_TYPES = ImmutableMap.of(
      "css", "text/css;charset=UTF-8",
      "js", "text/javascript;charset=UTF-8");
  private static final Map<String, String> ETAGS
      = Collections.synchronizedMap(new LinkedHashMap<String, String>() {
    @Override public boolean removeEldestEntry(Map.Entry<String, String> e) {
      return this.size() > 64;
    }
  });

  private static void serveStaticFile(
      String path, HttpServletRequest req, HttpServletResponse resp)
      throws IOException {
    String etag = ETAGS.get(path);
    {
      String inEtag = req.getHeader("If-None-Match");
      if (etag != null && etag.equals(inEtag)) {
        resp.setStatus(304);
        resp.getOutputStream().close();
        return;
      }
    }
    InputStream in = MainServlet.class.getResourceAsStream(path);
    if (in == null) {
      resp.sendError(404);
      return;
    }
    byte[] content;
    try {
      content = ByteStreams.toByteArray(in);
    } finally {
      in.close();
    }
    if (etag == null) {
      etag = Hash.builder().withData(content).build().toHexString();
      ETAGS.put(path, etag);
    }
    resp.setContentLength(content.length);
    String ext = path.substring(path.lastIndexOf('.') + 1);
    String mimeType = MIME_TYPES.get(ext);
    if (mimeType != null) { resp.setContentType(mimeType); }
    // All hex digits, so not a header splitting vuln.
    resp.setHeader("ETag", etag);
    OutputStream out = resp.getOutputStream();
    try {
      out.write(content);
    } finally {
      out.close();
    }
  }

  @Override
  public void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws IOException {
    String path = URI.create(req.getRequestURI()).getPath();
    if (!checkAuthorized(path, req, resp)) { return; }

    if ("/".equals(path)) {
      redirectTo(resp, "/index.html");
    } else if ("/index.html".equals(path)) {
      serveIndex(resp);
    } else if (path.startsWith("/tools")) {
      String subPath = path.substring(6);
      if ("/".equals(subPath)) {
        redirectTo(resp, "/tools/index.html");
      } else if ("/index.html".equals(subPath)) {
        serveToolsIndex(resp);
      } else if ("/tools.json".equals(subPath)) {
        serveToolsJson(resp);
      } else {
        serveToolDoc(subPath, resp);
      }
    } else if (path.startsWith("/plan")) {
      String subPath = path.substring(5);
      if ("/".equals(subPath)) {
        redirectTo(resp, "/plan/index.html");
      } else if ("/index.html".equals(subPath)) {
        servePlanIndex(resp);
      } else if ("/plan.json".equals(subPath)) {
        servePlanJson(resp);
      } else {
        serveProductDoc(subPath.substring(1), resp);
      }
    } else if (path.startsWith("/mirror/")) {
      String subPath = path.substring(8);
      mirrorClientFile(subPath, resp);
    } else {
      resp.sendError(404);
    }
  }

  private void mirrorClientFile(String subPath, HttpServletResponse resp)
      throws IOException {
    if (pb.getConfig().getIgnorePattern().matcher(subPath).find()) {
      resp.sendError(404);
      return;
    }
    Path clientRoot = pb.getConfig().getClientRoot();
    String sep = clientRoot.getFileSystem().getSeparator();
    if (!"/".equals(sep)) { subPath = subPath.replace("/", sep); }
    Path requestedPath = clientRoot.resolve(subPath).normalize();
    if (!requestedPath.startsWith(clientRoot)) {
      resp.sendError(404);
      return;
    }
    InputStream in;
    try {
      in = requestedPath.newInputStream();
    } catch (IOException ex) {
      resp.sendError(404);
      return;
    }
    try {
      // TODO: best guess at content-type.
      OutputStream out = resp.getOutputStream();
      try {
        ByteStreams.copy(in, out);
      } finally {
        out.close();
      }
    } finally {
      in.close();
    }
  }

  private void serveAuthHelp(HttpServletResponse resp) throws IOException {
    Writer w = resp.getWriter();
    AuthHelpPage.write(w, GxpContext.builder(Locale.ENGLISH).build());
    w.close();
  }

  private void servePlanJson(HttpServletResponse resp) throws IOException {
    resp.setContentType("application/json; charset=UTF-8");
    Writer w = resp.getWriter();
    try {
      JsonSink sink = new JsonSink(w);
      Set<String> upToDate = pb.getUpToDateProducts();
      PlanGraph pg = pb.getPlanGraph();
      sink.write("{");
      sink.writeValue("products");
      sink.write(":");
      // We can't just write out the products because that would include
      // the bake function which is not renderable to JSON.
      sink.write("{");
      boolean needComma = false;
      for (Product p : pb.getProducts().values()) {
        if (needComma) { sink.write(","); }
        sink.writeValue(p.name);
        sink.write(":");
        sink.writeValue(p.withJsonOnly());
        needComma = true;
      }
      sink.write("}");
      sink.write(",");
      sink.writeValue("graph");
      sink.write(":");
      sink.write("{");
      for (String productName : pg.nodes) {
        sink.writeValue(productName);
        sink.write(":");
        sink.write("{");
        sink.writeValue("upToDate");
        sink.write(":");
        sink.writeValue(upToDate.contains(productName));
        sink.write(",");
        sink.writeValue("requires");
        sink.write(":");
        sink.writeValue(pg.edges.get(productName));
        sink.write("}");
      }
      sink.write("}");
      sink.write("}");

    } finally {
      w.close();
    }
  }

  private void servePlanIndex(HttpServletResponse resp) throws IOException {
    Writer w = resp.getWriter();
    PlanIndexPage.write(
        w, GxpContext.builder(Locale.ENGLISH).build(),
        pb.getPlanGraph(), pb.getProducts(), pb.getUpToDateProducts());
    w.close();
  }

  private void serveProductDoc(String productName, HttpServletResponse resp)
      throws IOException {
    Product product = pb.getProducts().get(productName);
    if (product == null) {
      resp.sendError(404);
      return;
    }
    Writer w = resp.getWriter();
    ProductDocPage.write(
        w, GxpContext.builder(Locale.ENGLISH).build(),
        product, pb.getUpToDateProducts().contains(productName));
    w.close();
  }

  private void serveToolDoc(String subPath, HttpServletResponse resp) {
    // TODO Auto-generated method stub

  }

  private void serveToolsJson(HttpServletResponse resp) {
    // TODO Auto-generated method stub

  }

  private void serveToolsIndex(HttpServletResponse resp) {
    // TODO Auto-generated method stub
  }

  private void serveIndex(HttpServletResponse resp) throws IOException {
    Writer w = resp.getWriter();
    Map<String, Product> products = new TreeMap<String, Product>(LEXICAL);
    products.putAll(pb.getProducts());
    IndexPage.write(
        w, GxpContext.builder(Locale.ENGLISH).build(), pb.getTools(), products);
    w.close();
  }
  private static final Comparator<String> LEXICAL = new Comparator<String>() {
    public int compare(String a, String b) { return a.compareTo(b); }
  };

  /** @param encodedUriPath an already encoded URI path. */
  private void redirectTo(HttpServletResponse resp, String encodedUriPath)
      throws IOException {
    resp.sendRedirect(encodedUriPath);
  }

  @Override
  public void doPost(HttpServletRequest req, HttpServletResponse resp)
      throws IOException {
    String path = URI.create(req.getRequestURI()).getPath();
    if (!checkAuthorized(path, req, resp)) { return; }
    // TODO
  }
}
