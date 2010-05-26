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

import java.util.Arrays;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

/**
 * Abstracts away {@link HttpServletRequest} so tests can just implement the
 * bits we need.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
public interface Request {
  @Nonnull String getRequestURI();
  @Nullable String getParameter(String name);
  @Nonnull String getRemoteAddr();
  @Nullable String getHeader(String name);
  @Nonnull Iterable<HttpCookie> getCookies();

  public static final class Factory {
    private Factory() { /* uninstantiable */ }

    public static Request wrap(final HttpServletRequest req) {
      return new Request() {
        public Iterable<HttpCookie> getCookies() {
          final Cookie[] cookies = req.getCookies();
          if (cookies == null) { return ImmutableList.of(); }
          return Iterables.transform(
              Arrays.asList(cookies),
              new Function<Cookie, HttpCookie>() {
                public HttpCookie apply(final Cookie c) {
                  return new HttpCookie() {
                    public String getName() { return c.getName(); }
                    public String getValue() { return c.getValue(); }
                  };
                }
              });
        }
        public String getHeader(String name) { return req.getHeader(name); }
        public String getParameter(String name) {
          return req.getParameter(name);
        }
        public String getRemoteAddr() { return req.getRemoteAddr(); }
        public String getRequestURI() { return req.getRequestURI(); }
      };
    }
  }
}
