Build systems fail to scale to large projects when rebuilding a small portion requires stat-ing every project file.
Prebake is a build system that uses a long-lived service to hook into the file-system and watch for changes so it can avoid unnecessary I/O for incremental builds.

It also solves common problems with Ant and Make : missing dependencies and build cruft from deleted source files.

See the wiki tab for more documentation.  [Goals](Goals.md), [Usage](Usage.md), and HowToHelp are good starting places.

## No ~~missing~~ dependencies ##
It does away with missing dependencies by doing away with explicit dependencies altogether.  Build dependencies are inferred by intersecting globs ; if one product takes `*.c` and produces `*.o` and another that takes `*.o` and produces `*.lib` then the latter depends on the former.

## Declarative with dynamism when you need it ##
Prebake also gets the benefits of both a declarative build syntax (a la make) and the flexibility of hand coded shell scripts.  It uses tightly sandboxed JavaScript and "[mobile functions](MobileFunction.md)" to get the flexibility of a scripting language with the hard controls on side effects that allow for repeatable builds.  In practice, the JS in build files looks declarative, like JSON, but the dynamism is there when you need it.

## Status ##
Prebake is alpha software.  Use at your own risk.