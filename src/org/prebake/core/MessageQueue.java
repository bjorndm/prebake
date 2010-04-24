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
