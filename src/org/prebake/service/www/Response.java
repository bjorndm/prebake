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

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;

import javax.annotation.Nonnull;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

/**
 * Abstracts away {@link HttpServletResponse} so tests can just implement the
 * bits we need.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
public interface Response {
  void addCookie(String name, String value);
  void sendRedirect(String uri) throws IOException;
  void sendError(int status) throws IOException;
  void setStatus(int status);
  void setContentLength(int length);
  void setHeader(String name, String value);
  void setContentType(String contentType);
  @Nonnull Writer getWriter() throws IOException;
  @Nonnull OutputStream getOutputStream() throws IOException;

  public static final class Factory {
    private Factory() { /* uninstantiable */ }

    public static Response wrap(final HttpServletResponse resp) {
      return new Response() {
        public void addCookie(String name, String value) {
          resp.addCookie(new Cookie(name, value));
        }
        public OutputStream getOutputStream() throws IOException {
          return resp.getOutputStream();
        }
        public Writer getWriter() throws IOException { return resp.getWriter(); }
        public void sendError(int status) throws IOException {
          resp.sendError(status);
        }
        public void sendRedirect(String uri) throws IOException {
          resp.sendRedirect(uri);
        }
        public void setContentLength(int length) {
          resp.setContentLength(length);
        }
        public void setContentType(String contentType) {
          resp.setContentType(contentType);
        }
        public void setHeader(String name, String value) {
          assert name.indexOf('\n') < 0 && value.indexOf('\n') < 0;
          resp.setHeader(name, value);
        }
        public void setStatus(int status) { resp.setStatus(status); }
      };
    }
  }
}
