package org.prebake.js;

import org.mozilla.javascript.Scriptable;

interface ScriptableSkeleton {
  Object fleshOut(Scriptable scope);
}
