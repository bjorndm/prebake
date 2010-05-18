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
import org.prebake.service.Prebakery;
import org.prebake.service.plan.Product;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.net.URI;
import java.net.URLDecoder;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import javax.servlet.ServletException;
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
      String query = req.getQueryString();
      if (query != null && URLDecoder.decode(query, "UTF-8").equals(token)) {
        resp.addCookie(new Cookie("pbauth", token));
        redirectTo(resp, "/index.html");
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
          if ("pbauth".equals(cookie.getName())) {
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
      "css", "text/css;charset=UTF-8");
  private static final Map<String, String> ETAGS
      = Collections.synchronizedMap(new LinkedHashMap<String, String>() {
    @Override public boolean removeEldestEntry(Map.Entry<String, String> e) {
      return this.size() > 64;
    }
  });

  private static void serveStaticFile(
      String path, HttpServletRequest req, HttpServletResponse resp)
      throws IOException {
    String inEtag = req.getHeader("If-None-Match");
    String etag = ETAGS.get(path);
    if (etag != null && etag.equals(inEtag)) {
      resp.setStatus(304);
      resp.getOutputStream().close();
      return;
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
    if (inEtag == null) {
      inEtag = Hash.builder().withData(content).build().toHexString();
      ETAGS.put(path, inEtag);
    }
    resp.setContentLength(content.length);
    String ext = path.substring(path.lastIndexOf('.') + 1);
    String mimeType = MIME_TYPES.get(ext);
    if (mimeType != null) { resp.setContentType(mimeType); }
    // All hex digits, so not a header splitting vuln.
    resp.setHeader("ETag", inEtag);
    OutputStream out = resp.getOutputStream();
    try {
      out.write(content);
    } finally {
      out.close();
    }
  }

  @Override
  public void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws IOException, ServletException {
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
    } else if (path.startsWith("/plans")) {
      String subPath = path.substring(6);
      if ("/".equals(subPath)) {
        redirectTo(resp, "/plans/index.html");
      } else if ("/index.html".equals(subPath)) {
        servePlansIndex(resp);
      } else if ("/plans.json".equals(subPath)) {
        servePlansJson(resp);
      } else {
        servePlanDoc(subPath, resp);
      }
    } else if (path.startsWith("/mirror")) {
      String subPath = path.substring(7);
      mirrorClientFile(subPath, resp);
    } else {
      resp.sendError(404);
    }
  }

  private void mirrorClientFile(String subPath, HttpServletResponse resp) {
    // TODO Auto-generated method stub

  }

  private void serveAuthHelp(HttpServletResponse resp) throws IOException {
    Writer w = resp.getWriter();
    AuthHelpPage.write(w, GxpContext.builder(Locale.ENGLISH).build());
    w.close();
  }

  private void servePlanDoc(String subPath, HttpServletResponse resp) {
    // TODO Auto-generated method stub

  }

  private void servePlansJson(HttpServletResponse resp) {
    // TODO Auto-generated method stub

  }

  private void servePlansIndex(HttpServletResponse resp) {
    // TODO Auto-generated method stub

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
      throws ServletException, IOException {
    String path = URI.create(req.getRequestURI()).getPath();
    if (!checkAuthorized(path, req, resp)) { return; }
    // TODO
  }
}
