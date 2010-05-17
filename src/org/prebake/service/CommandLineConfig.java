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

package org.prebake.service;

import org.prebake.core.DidYouMean;
import org.prebake.core.MessageQueue;
import org.prebake.js.JsonSink;
import org.prebake.util.CommandLineArgs;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import java.io.IOException;
import java.nio.file.AccessMode;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.Attributes;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Configuration derived from command line arguments.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
@ParametersAreNonnullByDefault
final class CommandLineConfig implements Config {
  private final FileSystem fs;
  private final Path clientRoot;
  private final Pattern ignorePattern;
  private final Set<Path> planFiles;
  private final List<Path> toolDirs;
  private final int umask;
  private final int wwwPort;

  private static final short DEFAULT_UMASK = 0x1a0 /* octal 0640 */;
  private static final String DANGLING_MODIFIER_MSG;

  private static String errorMessageForRegex(String regex) {
    try {
      Pattern.compile(regex);
    } catch (PatternSyntaxException ex) {
      return ex.getMessage();
    }
    throw new RuntimeException();
  }
  static {
    // Find a way to detect misuse of * and ? in a RegExp that are commonly
    // misapplied by people who confuse RegExps and Globs.
    DANGLING_MODIFIER_MSG = commonPrefix(
        errorMessageForRegex("*"),
        errorMessageForRegex("foo.???"));
  }

  enum FlagName {
    ROOT("--root"),
    IGNORE("--ignore"),
    TOOLS("--tools"),
    UMASK("--umask"),
    WWW_PORT("--www-port"),
    ;

    final String flag;

    FlagName(String flag) { this.flag = flag; }
  }

  CommandLineConfig(FileSystem fs, MessageQueue mq, CommandLineArgs args) {
    this.fs = fs;
    {
      Path clientRoot = null;
      Pattern ignorePattern = null;
      Integer umask = null;
      Set<Path> planFiles = Sets.newLinkedHashSet();
      List<Path> toolDirs = Lists.newArrayList();
      Integer wwwPort = null;
      for (CommandLineArgs.Flag flag : args.getFlags()) {
        FlagName name = null;
        for (FlagName fn : FlagName.values()) {
          if (fn.flag.equals(flag.name)) { name = fn; }
        }
        if (name != null) {
          switch (name)  {
            case ROOT:
              if (clientRoot == null) {
                try {
                  clientRoot = fs.getPath(flag.value).toRealPath(false);
                } catch (IOException ex) {
                  mq.error("Bad root " + flag.value);
                }
              } else {
                mq.error("Dupe arg " + flag.name);
              }
              break;
            case IGNORE:
              if (ignorePattern == null) {
                try {
                  ignorePattern = Pattern.compile(flag.value, Pattern.DOTALL);
                } catch (PatternSyntaxException ex) {
                  String msg = ex.getMessage();
                  mq.error(msg);
                  if (msg.contains(DANGLING_MODIFIER_MSG)) {
                    mq.error(
                        "Ignore pattern is a regular expression, not a glob");
                  }
                }
              } else {
                mq.error("Dupe arg " + flag.name);
              }
              break;
            case UMASK:
              if (umask == null) {
                try {
                  umask = Integer.valueOf(flag.value, 8);
                } catch (NumberFormatException ex) {
                  mq.error(
                      "umask " + flag.value + " is not a valid octal number");
                }
              } else {
                mq.error("Dupe arg " + flag.name);
              }
              break;
            case TOOLS:
              String pathSeparatorRegex = Pattern.quote(getPathSeparator());
              for (String d : flag.value.split(pathSeparatorRegex)) {
                if (!"".equals(d)) {
                  Path p = fs.getPath(d);
                  try {
                    p = p.toRealPath(false);
                    if (!toolDirs.contains(p)) {
                      toolDirs.add(p);
                    } else {
                      mq.error("Dupe tools dir " + p);
                    }
                  } catch (IOException ex) {
                    mq.error("Bad tools dir " + p);
                  }
                }
              }
              break;
            case WWW_PORT:
              if (wwwPort == null) {
                try {
                  wwwPort = Integer.valueOf(flag.value, 10);
                  if (wwwPort == 0 || (wwwPort & ~0xffff) != 0) {
                    mq.error(
                        "--www-port=" + flag.value + " is not a valid port");
                  }
                } catch (NumberFormatException ex) {
                  mq.error(
                      "--www-port=" + flag.value + " is not a valid port");
                }
              } else {
                mq.error("Dupe arg " + flag.name);
              }
              break;
            default: throw new RuntimeException(flag.name);
          }
        } else {
          FlagName[] flagNames = FlagName.values();
          String[] flags = new String[flagNames.length];
          for (int i = flags.length; --i >= 0;) {
            flags[i] = flagNames[i].flag;
          }
          DidYouMean.toMessageQueue(
              "Unrecognized flag " + flag.name, flag.name, mq, flags);
        }
      }
      List<String> values = args.getValues();
      if (!values.isEmpty()) {
        for (String value : values) {
          Path p = fs.getPath(value);
          try {
            p = p.toRealPath(false);
            if (!planFiles.add(p)) {
              mq.error("Duplicate plan file " + p);
            }
          } catch (IOException ex) {
            mq.error("Bad plan file " + p);
          }
        }
      } else if (clientRoot != null) {
        planFiles.add(clientRoot.resolve("recipe.js"));
      }
      this.planFiles = ImmutableSet.copyOf(planFiles);
      this.toolDirs = ImmutableList.copyOf(toolDirs);
      this.clientRoot = clientRoot;
      this.ignorePattern = ignorePattern;
      this.umask = umask != null ? umask.intValue() : DEFAULT_UMASK;
      this.wwwPort = wwwPort != null ? wwwPort.intValue() : -1;
    }

    if (clientRoot == null) {
      mq.error("Please specify --root");
    } else {
      try {
        BasicFileAttributes attrs = Attributes.readBasicFileAttributes(
            clientRoot, LinkOption.NOFOLLOW_LINKS);
        if (!attrs.isDirectory()) {
          mq.error("Client root " + clientRoot + " is not a directory");
        } else {
          clientRoot.checkAccess(AccessMode.WRITE);
        }
      } catch (IOException ex) {
        mq.error("Client root " + clientRoot + " is not writable");
      }
    }
    for (Path planFile : planFiles) {
      boolean ok;
      try {
        BasicFileAttributes attrs = Attributes.readBasicFileAttributes(
            planFile, LinkOption.NOFOLLOW_LINKS);
        ok = attrs.isRegularFile();
      } catch (IOException ex) {
        ok = false;
      }
      if (!ok) {
        mq.error("Plan file " + planFile + " is not a file");
      }
    }
    for (Path toolDir : toolDirs) {
      boolean ok;
      try {
        BasicFileAttributes attrs = Attributes.readBasicFileAttributes(
            toolDir, LinkOption.NOFOLLOW_LINKS);
        ok = attrs.isDirectory();
      } catch (IOException ex) {
        ok = false;
      }
      if (!ok) {
        mq.error("Tool dir " + toolDir + " is not a directory");
      }
    }
    if (// Extra bits
        (umask & ~0x1ff) != 0
        // Not readable by self
        || (umask & 0x080) == 0) {
      mq.error("Invalid umask " + String.format("%04o", umask));
    }
  }

  public static String toArgv(Config config) {
    List<String> argv = Lists.newArrayList();
    Path root = config.getClientRoot();
    if (root != null) {
      argv.add(FlagName.ROOT.flag);
      argv.add(root.toString());
    }
    Pattern p = config.getIgnorePattern();
    if (p != null) {
      argv.add(FlagName.IGNORE.flag);
      argv.add(p.pattern());
    }
    List<Path> toolDirs = config.getToolDirs();
    if (!toolDirs.isEmpty()) {
      StringBuilder sb = new StringBuilder();
      String sep = config.getPathSeparator();
      for (Path d : toolDirs) {
        if (sb.length() != 0) { sb.append(sep); }
        sb.append(d);
      }
      argv.add(FlagName.TOOLS.flag);
      argv.add(sb.toString());
    }
    int umask = config.getUmask();
    if (umask != DEFAULT_UMASK) {
      argv.add(FlagName.UMASK.flag);
      argv.add(Integer.toOctalString(umask));
    }
    int wwwPort = config.getWwwPort();
    if (wwwPort != -1) {
      argv.add(FlagName.WWW_PORT.flag);
      argv.add(Integer.toString(wwwPort));
    }
    int planStart = argv.size();
    boolean needsSep = false;
    for (Path pf : config.getPlanFiles()) {
      String pfs = pf.toString();
      if (pfs.startsWith("-")) { needsSep = true; }
      argv.add(pfs);
    }
    if (needsSep) { argv.add(planStart, "--"); }
    StringBuilder sb = new StringBuilder();
    JsonSink sink = new JsonSink(sb);
    try {
      sink.writeValue(argv);
      sink.close();
    } catch (IOException ex) {
      Throwables.propagate(ex);  // IOException writing to StringBuilder
    }
    return sb.toString();
  }

  public @Nullable Path getClientRoot() { return clientRoot; }

  public @Nullable Pattern getIgnorePattern() { return ignorePattern; }

  public Set<Path> getPlanFiles() { return planFiles; }

  public List<Path> getToolDirs() { return toolDirs; }

  public int getUmask() { return umask; }

  public int getWwwPort() { return wwwPort; }

  private static String commonPrefix(String a, String b) {
    int n = Math.min(a.length(), b.length());
    int i = 0;
    while (i < n && a.charAt(i) == b.charAt(i)) { ++i; }
    return a.substring(0, i);
  }

  public String getPathSeparator() {
    return "\\".equals(fs.getSeparator()) ? ";" : ":";
  }
}
