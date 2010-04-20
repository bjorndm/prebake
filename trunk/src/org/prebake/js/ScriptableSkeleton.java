package org.prebake.js;

/**
 * The specification of an object that can be created once we have a JavaScript
 * context to run it in.
 */
public interface ScriptableSkeleton {
  Object fleshOut(Membrane membrane);
}
