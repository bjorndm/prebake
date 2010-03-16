package org.prebake.service;

import org.prebake.core.MessageQueue;

import java.nio.file.FileSystems;

/**
 * An executable class that hooks the Prebakery to the real file system and
 * network, and starts it running.
 *
 * @author mikesamuel@gmail.com
 */
public final class Main {
  public static final void main(String[] argv) {
    // TODO: handle -?, --help, -h, and variants by printing usage data.
    MessageQueue mq = new MessageQueue();
    Config config = new CommandLineConfig(FileSystems.getDefault(), mq, argv);
    if (mq.hasErrors()) {
      for (String msg : mq.getMessages()) {
        System.err.println(msg);
      }
      System.exit(-1);
    }
    new Prebakery(config).start(new Runnable() {
      public void run() { System.exit(0); }
    });
  }
}
