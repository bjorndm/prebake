package org.prebake.service.tools;

import java.util.List;
import java.util.concurrent.Future;

/**
 * Interface that allows {@link ToolBox} to be stubbed out.
 *
 * @author mikesamuel@gmail.com
 */
public interface ToolProvider {
  List<Future<ToolSignature>> getAvailableToolSignatures();
}
