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

package org.prebake.core;

import com.google.common.collect.Lists;

import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Collects human readable messages and tracks whether a larger operation has
 * failed.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
@ParametersAreNonnullByDefault
public class MessageQueue {
  private final List<String> messages;
  private boolean hasErrors;

  public MessageQueue(List<String> messages) {
    assert messages != null;
    this.messages = messages;
  }

  public MessageQueue() { this(Lists.<String>newArrayList()); }

  public void error(String message) {
    this.hasErrors = true;
    getMessages().add(message);
  }

  public List<String> getMessages() { return messages; }

  public boolean hasErrors() { return hasErrors; }

  /**
   * Escapes a string so that it can pass through the
   * {@link java.text.MessageFormat} formatter used by
   * {@link java.util.logging.Logger} unscathed.
   */
  public static String escape(String s) {
    return s.replace("'", "''").replace("{", "'{'");
  }
}
