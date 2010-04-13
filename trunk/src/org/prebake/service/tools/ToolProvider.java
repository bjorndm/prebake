package org.prebake.service.tools;

import org.prebake.fs.FileAndHash;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Future;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Interface that allows {@link ToolBox} to be stubbed out.
 *
 * @author mikesamuel@gmail.com
 */
@ParametersAreNonnullByDefault
public interface ToolProvider extends Closeable {
  @Nonnull List<Future<ToolSignature>> getAvailableToolSignatures();
  @Nonnull FileAndHash getTool(String toolName) throws IOException;
}
