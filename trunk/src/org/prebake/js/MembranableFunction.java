package org.prebake.js;

import org.prebake.core.Documentation;

import javax.annotation.Nullable;

import com.google.common.base.Function;

/**
 * Marker interface for a function that can pass across the JavaScript membrane
 * to be exposed as an object.
 *
 * @author mikesamuel@gmail.com
 */
public interface MembranableFunction extends Function<Object[], Object> {
  /** A JavaScript identifier which will show up in stack traces. */
  @Nullable String getName();
  @Nullable Documentation getHelp();
}
