package org.prebake.service.tools;

import java.io.Closeable;
import java.util.List;
import java.util.concurrent.Future;

/**
 * Interface that allows {@link ToolBox} to be stubbed out.
 *
 * @author mikesamuel@gmail.com
 */
public interface ToolProvider extends Closeable {
  List<Future<ToolSignature>> getAvailableToolSignatures();
}
