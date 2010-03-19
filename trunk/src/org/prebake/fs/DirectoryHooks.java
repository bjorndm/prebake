package org.prebake.fs;

import java.io.Closeable;
import java.io.IOError;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKind;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.Attributes;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Publishes events when files change.
 *
 * @author mikesamuel@gmail.com
 */
public class DirectoryHooks implements Closeable {
  // For how-tos on WatchService:
  // http://blogs.sun.com/thejavatutorials/entry/watching_a_directory_for_changes
  // http://java.sun.com/docs/books/tutorial/essential/io/notification.html

  // This code is derived from the sample code in those how-tos.

  // For alternate mechanisms, see

  // Linux : http://www.linuxjournal.com/article/8478
  // BSD / Mac : http://developer.apple.com/Mac/library/documentation/Darwin/Reference/ManPages/man2/kqueue.2.html
  // Mac : FileSystemEvents http://developer.apple.com/mac/articles/cocoa/filesystemevents.html
  // Windows : http://msdn.microsoft.com/en-us/library/aa365261(VS.85).aspx

  private final Path root;
  private final BlockingQueue<Path> q = new LinkedBlockingQueue<Path>(1 << 12);
  private Thread watcher;

  public DirectoryHooks(Path root) {
    this.root = root;
  }

  public BlockingQueue<Path> getUpdates() { return q; }

  public void start() throws IOException {
    final Map<WatchKey, Path> keys;
    final WatchService ws;
    synchronized (this) {
      if (this.watcher != null) { return; }
      keys = new HashMap<WatchKey, Path>();
      ws = root.getFileSystem().newWatchService();
      this.watcher = new Thread(new Runnable() {
        public void run() {
          while (true) {
            synchronized (DirectoryHooks.this) {
              if (Thread.currentThread() != DirectoryHooks.this.watcher) {
                break;
              }
            }
            processEvent(ws, keys);
          }
        }
      });
    }
    register(root, ws, keys);
    watcher.start();
  }

  public void close() {
    Thread watcher;
    synchronized (this) {
      if (this.watcher == null) { return; }
      watcher = this.watcher;
      this.watcher = null;
    }
    watcher.interrupt();
  }

  private void processEvent(WatchService ws, Map<WatchKey, Path> keys) {
    WatchKey key;
    try {
      key = ws.take();
    } catch (InterruptedException x) {
      return;
    }

    Path dir = keys.get(key);
    if (dir == null) { return; }

    try {
      for (WatchEvent<?> event: key.pollEvents()) {
        WatchEvent.Kind<?> kind = event.kind();

        if (kind == StandardWatchEventKind.OVERFLOW) {
          // TODO: figure out what overflow means.
          continue;
        }

        // Context for directory entry event is the file name of entry
        Path name = (Path) event.context();
        Path child = dir.resolve(name);

        // If directory is created, and watching recursively, then
        // register it and its sub-directories
        if (kind == StandardWatchEventKind.ENTRY_CREATE) {
          // TODO: What about permission changes that make it readable?
          try {
            if (Attributes.readBasicFileAttributes(
                    child, LinkOption.NOFOLLOW_LINKS)
                .isDirectory()) {
              register(child, ws, keys);
            }
          } catch (IOException x) {
            // TODO: handle
          }
        }
        try {
          q.put(child);
        } catch (InterruptedException ex) {
          return;
        }
      }
    } finally {
      // Reset key so that we can receive further events and remove it from set
      // if directory no longer accessible.
      boolean valid = key.reset();
      if (!valid) { keys.remove(key); }
    }
  }

  private void register(
      Path p, final WatchService ws, final Map<WatchKey, Path> keys)
      throws IOException {
    try {
      Files.walkFileTree(p, new SimpleFileVisitor<Path>() {
        // TODO: implement other visitor methods
        @Override
        public FileVisitResult preVisitDirectory(Path dir) {
          try {
            WatchKey key = dir.register(
                ws,
                StandardWatchEventKind.ENTRY_CREATE,
                StandardWatchEventKind.ENTRY_DELETE,
                StandardWatchEventKind.ENTRY_MODIFY);
            keys.put(key, dir);
          } catch (IOException x) {
            throw new IOError(x);
          }
          return FileVisitResult.CONTINUE;
        }
        @Override
        public FileVisitResult visitFile(Path p, BasicFileAttributes attrs) {
          try {
            q.put(p);
          } catch (InterruptedException ex) {
            return FileVisitResult.TERMINATE;
          }
          return FileVisitResult.CONTINUE;
        }
      });
    } catch (IOError err) {
      throw (IOException) err.getCause();
    }
  }
}
