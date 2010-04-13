package org.prebake.channel;

import org.prebake.service.Prebakery;

/**
 * Constants for the names of files created by the {@link Prebakery} service.
 *
 * @see <a href="http://code.google.com/p/prebake/wiki/PrebakeDirectory">wiki
 *     </a>
 * @author mikesamuel@gmail.com
 */
public final class FileNames {
  public static final String DIR = ".prebake";
  public static final String CMD_LINE = "cmdline";
  public static final String PORT = "port";
  public static final String TOKEN = "token";

  private FileNames() { /* not instantiable */ }
}
