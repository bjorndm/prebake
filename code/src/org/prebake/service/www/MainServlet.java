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

import org.prebake.channel.FileNames;
import org.prebake.core.BoundName;
import org.prebake.core.Hash;
import org.prebake.core.PreformattedStaticHtml;
import org.prebake.fs.FsUtil;
import org.prebake.js.JsonSink;
import org.prebake.service.ArtifactDescriptors;
import org.prebake.service.Prebakery;
import org.prebake.service.plan.PlanGraph;
import org.prebake.service.plan.Product;
import org.prebake.service.tools.ToolSignature;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.Attributes;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import javax.annotation.Nullable;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;
import com.google.gxp.base.GxpContext;

/**
 * An HTTP servlet that exposes documentation and logs of
 * {@link org.prebake.service.plan.Planner plan files},
 * {@link ToolSignature tools}, and {@link Product products}.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
public final class MainServlet extends HttpServlet {
  private final String token;
  private final Prebakery pb;
  /**
   * The timezone to present dates in.
   * Since this servlet presents diagnostics for a single user's client we don't
   * bother trying to choose a timezone per user.
   */
  private final TimeZone tz;

  private static final String AUTH_COOKIE_NAME = "pbauth";

  public MainServlet(String token, Prebakery pb, TimeZone tz) {
    this.token = token;
    this.pb = pb;
    this.tz = tz;
  }

  private boolean checkAuthorized(
      String path, Request req, Response resp, boolean isReadOnly)
      throws IOException {
    if (path.startsWith("/www-files/")) {
      serveStaticFile(path.substring(1), req, resp);
      return false;
    }
    if ("/auth".equals(path)) {
      String queryToken = req.getParameter("tok");
      if (token.equals(queryToken)) {
        resp.addCookie(AUTH_COOKIE_NAME, token);
        String continuePath = req.getParameter("continue");
        redirectTo(resp, continuePath != null ? continuePath : "/index.html");
      } else {
        resp.sendError(HttpServletResponse.SC_UNAUTHORIZED);
      }
      return false;
    } else if ("/auth-help.html".equals(path)) {
      serveAuthHelp(resp);
      return false;
    } else if (isReadOnly && pb.getConfig().getLocalhostTrusted()
               && "127.0.0.1".equals(req.getRemoteAddr())) {
      return true;
    } else {
      boolean authed = false;
      for (HttpCookie cookie : req.getCookies()) {
        if (AUTH_COOKIE_NAME.equals(cookie.getName())) {
          if (cookie.getValue().equals(token)) { authed = true; }
          break;
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

  private static void serveStaticFile(String path, Request req, Response resp)
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
    doGet(Request.Factory.wrap(req), Response.Factory.wrap(resp));
  }

  public void doGet(Request req, Response resp) throws IOException {
    String path = URI.create(req.getRequestURI()).getPath();
    if (!checkAuthorized(path, req, resp, true)) { return; }

    // See package-info.java for the file tree described here.
    if ("/".equals(path)) {
      redirectTo(resp, "/index.html");
    } else if ("/index.html".equals(path)) {
      serveIndex(resp);
    } else if (path.startsWith("/tools/")) {
      String subPath = path.substring(7);
      if ("".equals(subPath)) {
        redirectTo(resp, "/tools/index.html");
      } else if ("index.html".equals(subPath)) {
        serveToolsIndex(resp);
      } else if ("tools.json".equals(subPath)) {
        serveToolsJson(resp);
      } else {
        serveToolDoc(subPath, resp);
      }
    } else if (path.startsWith("/plan/")) {
      String subPath = path.substring(6);
      if ("".equals(subPath)) {
        redirectTo(resp, "/plan/index.html");
      } else if ("index.html".equals(subPath)) {
        servePlanIndex(resp);
      } else if ("plan.json".equals(subPath)) {
        servePlanJson(resp);
      } else {
        resp.sendError(404);
      }
    } else if (path.startsWith("/product/")) {
      serveProductDoc(path.substring(9), resp);
    } else if (path.startsWith("/logs/")) {
      serveLogFile(path.substring(6), resp);
    } else if (path.startsWith("/mirror/")) {
      String subPath = path.substring(8);
      mirrorClientFile(subPath, resp);
    } else {
      resp.sendError(404);
    }
  }

  private void serveLogFile(String logPath, Response resp) throws IOException {
    String artifactDescriptor;
    if (logPath.startsWith("product/")) {
      artifactDescriptor = logPath.substring(8) + ".product";
    } else if (logPath.startsWith("tool/")) {
      artifactDescriptor = logPath.substring(5) + ".tool";
    } else if (logPath.startsWith("plan/")) {
      artifactDescriptor = logPath.substring(5) + ".plan";
    } else if (logPath.equals("summary.html")) {
      resp.setContentType("text/html; charset=UTF-8");
      Writer w = resp.getWriter();
      PreformattedStaticHtml html = pb.getHighLevelLog().formatEvents(
          pb.getHighLevelLog().snapshot(), ServletEntityLinker.INSTANCE, tz);
      RecentActivityPage.write(
          w, GxpContext.builder(Locale.ENGLISH).build(),
          html);
      w.close();
      return;
    } else {
      resp.sendError(404);
      return;
    }
    serveLogForArtifact(artifactDescriptor, resp);
  }

  private void serveLogForArtifact(String artifactDescriptor, Response resp)
      throws IOException {
    resp.setContentType("text/plain; charset=UTF-8");
    Path logFile = getLogPath(artifactDescriptor);
    if (logFile.exists()) {
      long size = Attributes.readBasicFileAttributes(logFile).size();
      if (size < Integer.MAX_VALUE) {
        resp.setContentLength((int) size);
      }
      InputStream in = logFile.newInputStream();
      try {
        OutputStream out = resp.getOutputStream();
        try {
          ByteStreams.copy(in, out);
        } finally {
          out.close();
        }
      } finally {
        in.close();
      }
    } else {
      resp.getOutputStream().close();
    }
  }

  private void mirrorClientFile(String subPath, Response resp)
      throws IOException {
    if (pb.getConfig().getIgnorePattern().matcher(subPath).find()) {
      resp.sendError(404);
      return;
    }
    Path clientRoot = pb.getConfig().getClientRoot();
    subPath = FsUtil.denormalizePath(clientRoot.getRoot(), subPath);
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

  private void serveAuthHelp(Response resp) throws IOException {
    resp.setContentType("text/html; charset=UTF-8");
    Writer w = resp.getWriter();
    AuthHelpPage.write(w, GxpContext.builder(Locale.ENGLISH).build());
    w.close();
  }

  private void servePlanJson(Response resp) throws IOException {
    //resp.setContentType("application/json; charset=UTF-8");
    resp.setContentType("application/json; charset=UTF-8");
    Writer w = resp.getWriter();
    try {
      JsonSink sink = new JsonSink(w);
      Set<BoundName> upToDate = pb.getUpToDateProducts();
      PlanGraph pg = pb.getPlanGraph();
      sink.write("{")
          .writeValue("products")
          .write(":");
      // We can't just write out the products because that would include
      // the bake function which is not renderable to JSON.
      sink.write("{");
      boolean needComma = false;
      for (Product p : pb.getProducts().values()) {
        if (needComma) { sink.write(","); }
        sink.writeValue(p.name)
            .write(":")
            .writeValue(p.withJsonOnly());
        needComma = true;
      }
      sink.write("}")
          .write(",")
          .writeValue("graph")
          .write(":")
          .write("{");
      needComma = false;
      for (BoundName productName : pg.nodes.keySet()) {
        if (needComma) { sink.write(","); }
        sink.writeValue(productName)
            .write(":")
            .write("{")
            .writeValue("upToDate")
            .write(":")
            .writeValue(upToDate.contains(productName))
            .write(",")
            .writeValue("requires")
            .write(":")
            .writeValue(pg.edges.get(productName))
            .write("}");
        needComma = true;
      }
      sink.write("}")
          .write("}");
    } finally {
      w.close();
    }
  }

  private void servePlanIndex(Response resp) throws IOException {
    resp.setContentType("text/html; charset=UTF-8");
    Writer w = resp.getWriter();
    PlanIndexPage.write(
        w, GxpContext.builder(Locale.ENGLISH).build(),
        pb.getProducts().values());
    w.close();
  }

  private void serveProductDoc(String productName, Response resp)
      throws IOException {
    Product product = pb.getProducts().get(productName);
    if (product == null) {
      serveLogForArtifact(ArtifactDescriptors.forProduct(productName), resp);
      return;
    }
    String logPreview = readLogPreview(
        ArtifactDescriptors.forProduct(productName));
    String logUriPath = "../logs/product/" + productName;
    resp.setContentType("text/html; charset=UTF-8");
    Writer w = resp.getWriter();
    ProductDocPage.write(
        w, GxpContext.builder(Locale.ENGLISH).build(),
        pb.getConfig().getClientRoot(), product,
        pb.getUpToDateProducts().contains(productName), logUriPath, logPreview);
    w.close();
  }

  private void serveToolDoc(String toolName, Response resp) throws IOException {
    if (!pb.getToolNames().contains(toolName)) {
      resp.sendError(404);
      return;
    }
    ToolSignature sig = pb.getTools().get(toolName);
    String logPreview = readLogPreview(ArtifactDescriptors.forTool(toolName));
    String logUriPath = "../logs/tool/" + toolName;
    resp.setContentType("text/html; charset=UTF-8");
    Writer w = resp.getWriter();
    ToolDocPage.write(
        w, GxpContext.builder(Locale.ENGLISH).build(), toolName, sig,
        logUriPath, logPreview);
    w.close();
  }

  private void serveToolsJson(Response resp) throws IOException {
    resp.setContentType("application/json; charset=UTF-8");
    Writer w = resp.getWriter();
    try {
      JsonSink sink = new JsonSink(w);
      sink.write("{");
      Map<String, ToolSignature> tools = pb.getTools();
      boolean sawOne = false;
      for (String toolName : pb.getToolNames()) {
        if (sawOne) { sink.write(","); }
        sawOne = true;
        sink.writeValue(toolName)
            .write(":")
            .write("{");
        ToolSignature tool = tools.get(toolName);
        if (tool != null && tool.help != null) {
          sink.writeValue("help").write(":").writeValue(tool.help).write(",");
        }
        sink.writeValue("upToDate").write(":").writeValue(tool != null)
            .write("}");
      }
      sink.write("}");
    } finally {
      w.close();
    }
  }

  private void serveToolsIndex(Response resp) throws IOException {
    resp.setContentType("text/html; charset=UTF-8");
    Writer w = resp.getWriter();
    ToolsIndexPage.write(
        w, GxpContext.builder(Locale.ENGLISH).build(), pb.getTools(),
        pb.getToolNames());
    w.close();
  }

  private void serveIndex(Response resp) throws IOException {
    resp.setContentType("text/html; charset=UTF-8");
    Writer w = resp.getWriter();
    Map<BoundName, Product> products = pb.getProducts();
    IndexPage.write(
        w, GxpContext.builder(Locale.ENGLISH).build(),
        pb.getToolNames(), pb.getTools(), products.values());
    w.close();
  }

  /** @param encodedUriPath an already encoded URI path. */
  private void redirectTo(Response resp, String encodedUriPath)
      throws IOException {
    resp.sendRedirect(encodedUriPath);
  }

  @Override
  public void doPost(HttpServletRequest req, HttpServletResponse resp)
      throws IOException {
    doPost(Request.Factory.wrap(req), Response.Factory.wrap(resp));
  }

  public void doPost(Request req, Response resp) throws IOException {
    String path = URI.create(req.getRequestURI()).getPath();
    if (!checkAuthorized(path, req, resp, false)) { return; }
    // TODO
  }

  private Path getLogPath(String artifactDescriptor) {
    return pb.getConfig().getClientRoot().resolve(FileNames.DIR)
        .resolve(FileNames.LOGS).resolve(artifactDescriptor + ".log");
  }

  /** Tails a log file. */
  private @Nullable String readLogPreview(String artifactDescriptor) {
    try {
      Path p = getLogPath(artifactDescriptor);
      if (p.notExists()) { return null; }
      long size = Attributes.readBasicFileAttributes(p).size();
      if (size == 0 || size > Integer.MAX_VALUE) { return null; }
      byte[] preview = new byte[(int) Math.min(4096L, size)];
      int end;
      InputStream in = p.newInputStream(StandardOpenOption.READ);
      try {
        if (size > preview.length) {
          long tailStart = size - preview.length;
          if (tailStart != in.skip(tailStart)) {
            throw new IOException("Failed to skip to tail");
          }
        }
        end = in.read(preview);
      } finally {
        in.close();
      }
      int start = 0;
      // Skip over UTF-8 tail bytes so we start with a complete code unit.
      while (start < end && ((preview[start] & 0xc0) == 0x80)) { ++start; }
      if (end == start) { return null; }
      String tail = new String(preview, start, end - start, Charsets.UTF_8);
      if (size != (end - start)) { tail = "..." + tail; }
      return tail;
    } catch (IOException ex) {
      ex.printStackTrace();
      return null;
    }
  }
}
