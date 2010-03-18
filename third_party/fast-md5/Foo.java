import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Locale;

public class Foo {
  static final boolean DEBUG = false;

  public static void main(String... args) throws Exception {
    ClassLoader cl = new ClassLoader() {
      @Override
      protected String findLibrary(String libname) {
        if (!"MD5".equals(libname)) { return null; }
        File file = new File(System.mapLibraryName(libname));
        if (DEBUG) {
          System.err.println("Trying to find library " + libname + " / " + file);
        }

        String osArch = System.getProperty("os.arch");
        if (osArch == null) { return null; }
        if (osArch.endsWith("86")) { osArch = "x86"; }
        file = new File(new File(new File("lib", "arch"), osArch), file.getName());
        if (DEBUG) {
          System.err.println("Resolved to " + file);
        }

        URL url = getResource(file.toString());
        if (url != null) {
          try {
            // Try to locate it on the file system.
            File fullPath = new File(url.toURI());
            return fullPath.toString();
          } catch (IllegalArgumentException ex) {
            // Fall back to temp file below
          } catch (URISyntaxException ex) {
            // Fall back to temp file below
          }
        }
        InputStream in = getResourceAsStream(file.toString());
        if (in == null) { return null; }
        File tempFile;
        try {
          try {
            // Create a temporary file and load from there.
            // This has all the same Trojan problems as mktemp.
            // Applications should guard by making sure that the temp directory
            // System property is set to a dir that is not writable by less
            // privileged processes.
            tempFile = File.createTempFile("tmp", file.getName());
            OutputStream out = new FileOutputStream(tempFile);
            // Not dependable.  Windows may keep a lock on the file until
            // after the process exited.
            // We could try and find existing files in the same temp directory
            // to avoid clutter; if only we had an efficient way to hash files...
            tempFile.deleteOnExit();
            try {
              byte[] buf = new byte[4096];
              for (int n; (n = in.read(buf)) > 0;) { out.write(buf, 0, n); }
            } finally {
              out.close();
            }
          } finally {
            in.close();
          }
        } catch (IOException ex) {
          // Elevate a failure to load a library due to a failure in the
          // external environment.
          throw new RuntimeException(ex);
        }
        return tempFile.toString();
      }

      protected Class<?> findClass(String name) throws ClassNotFoundException {
        if (!"Bar".equals(name)) { throw new ClassNotFoundException(name); }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buf = new byte[1 << 12];
        InputStream in = getResourceAsStream("hidden/" + name + ".class");
        try {
          try {
            for (int n; (n = in.read(buf)) > 0;) { bytes.write(buf, 0, n); }
          } finally {
            in.close();
          }
        } catch (IOException ex) {
          throw new ClassNotFoundException(name, ex);
        }
        byte[] classBytes = bytes.toByteArray();
        return defineClass(name, classBytes, 0, classBytes.length);
      }
    };
    // Not required, but gives useful logging when developing.
    if (DEBUG) {
      System.setSecurityManager(new SecurityManager() {
          @Override
            public void checkLink(String libName) throws SecurityException {
            System.err.println("checkLink " + libName);
          }
          @Override
            public void checkRead(FileDescriptor fd) throws SecurityException {
            System.err.println("checkRead fd " + fd);
          }
          @Override
            public void checkRead(String s) throws SecurityException {
            System.err.println("checkRead str " + s);
          }
          @Override
            public void checkRead(String s, Object context) throws SecurityException {
            System.err.println("checkRead str " + s + " in " + context);
          }
          @Override
            public void checkWrite(FileDescriptor fd) throws SecurityException {
            System.err.println("checkWrite fd " + fd);
          }
          @Override
            public void checkWrite(String s) throws SecurityException {
            System.err.println("checkWrite str " + s);
          }
          @Override
            public void checkDelete(String s) throws SecurityException {
            System.err.println("checkDelete str " + s);
          }
        });
    }
    Class<?> barClass = cl.loadClass("Bar");
    if (DEBUG) {
      System.err.println("LOADED BAR CLASS " + barClass.getClassLoader());
    }
    Object barInst = barClass.newInstance();
    if (DEBUG) {
      System.err.println("CREATED BAR INSTANCE");
    }
  }
}
