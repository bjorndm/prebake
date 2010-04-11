package org.prebake.service.build;

import org.prebake.os.OperatingSystem;
import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public final class Baker {
  private final OperatingSystem os;

  public Baker(OperatingSystem os) { this.os = os; }

}
