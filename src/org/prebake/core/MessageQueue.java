package org.prebake.core;

import java.util.ArrayList;
import java.util.List;

public class MessageQueue {
  private final List<String> messages;
  private boolean hasErrors;

  public MessageQueue(List<String> messages) {
    assert messages != null;
    this.messages = messages;
  }

  public MessageQueue() { this(new ArrayList<String>()); }

  public void error(String message) {
    this.hasErrors = true;
    getMessages().add(message);
  }

  public List<String> getMessages() { return messages; }

  public boolean hasErrors() { return hasErrors; }
}
