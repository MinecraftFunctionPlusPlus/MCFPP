# MCFPP source debugger

The IDEA plugin uses one JDK 25 installation for the compiler, DAP adapter,
language server and separate Datapack Sandbox process. Minecraft version selection comes from
`mcfpp.toml`, including Minecraft 26.3 and its native floating point commands.

## Debug in IntelliJ IDEA

1. Install the complete MCFPP plugin ZIP. In **Settings → Tools → MCFPP**, select
   JDK 25. Leave the DSB Java override blank to reuse it. The plugin includes the DSB JAR.
2. Open an MCFPP project and put a breakpoint on an executable source statement.
3. Use **Debug** beside a top-level function without parameters, or beside a single
   `@Test`. A saved MCFPP build configuration can also specify **Debug function**
   and **Stop on entry**.
4. Use IDEA's Continue, Pause, Step Into, Step Over, Step Out, and Stop actions.
   The Debug window shows source frames and local variables. Expand structured
   values or add paths such as `payload.values[1]` in Watches.

Each launch starts a new world. Breakpoints are installed before load initialization.
Tests execute load, the file's BeforeEach hooks, the selected test, and AfterEach
hooks in their existing order. Assertion failures are reported after cleanup.
Runtime command errors terminate the session with a diagnostic and source location.
Stopping a session or closing its project disposes the adapter and runtime processes.

Watches accept variable names, fields, quoted field names, and constant integer
indices, including negative list indices. Evaluation reads the frozen pause state.
Unavailable or uninitialized values explain why they cannot be read. Expressions
that call functions or write values are rejected.

Entity and block NBT locations, selector score holders and complex NBT paths are
resolved by DSB against a detached copy of the pause state. Reads use the selected
frame's executor, coordinates, rotation and dimension, and do not execute commands
in the live world.

## Debug builds and mappings

`mcfpp build --debug-info` writes a separate build under the project's output
directory, in `debug/`. This flag is independent of the compiler's `-debug` flag.
Debug builds use O0, retain runtime statements and frames, and continue to perform
language-required compile-time evaluation.

`debug/mcfpp.debug.json` is a versioned sidecar with schema 1. It records source
ranges, statement and lexical scope identities, physical function and command
locations, logical function identities, call targets, macro origins, and constant,
scoreboard, NBT and saved caller variable locations. Source tests point back to the
original test files. Resource overrides have no mapping to a replaced compiler body.

Mappings describe the published datapack. SHA-256 values cover source files and
pack artifacts; launch and execution controls reject edits that invalidate them.
Lines with no runtime sequence point produce unverified breakpoints in IDEA.
Rebuild/relaunch after editing sources.

## DAP clients

Start `mcfpp debug --stdio`. Standard output carries Content-Length-framed DAP only;
compiler logs and sandbox output arrive as DAP output events. The adapter uses
LSP4J Debug 0.22.0. A launch request has these arguments:

```json
{
  "projectPath": "F:/projects/example",
  "function": "example:main",
  "dsbJava": "F:/Java/jdk-25/bin/java.exe",
  "dsbJar": "F:/tools/datapack-sandbox-cli.jar",
  "stopOnEntry": false,
  "offline": false,
  "locked": false
}
```

For a test, replace `function` with `test`, using its discovery ID such as
`example:sample`. Exactly one target is required. `DSB_JAR` and `DSB_JAVA` supply
defaults for the two runtime paths; without a Java override DSB uses the adapter's JDK.
The client initializes, launches, installs
breakpoints after the initialized event, and sends configurationDone.

The adapter handles configuration, breakpoints, threads, stackTrace, scopes,
variables, evaluate, continue, pause, next, stepIn, stepOut, terminate and disconnect.
Compiler helper functions are folded into their logical source frame. A new loop
iteration remains a new stoppable occurrence of the same statement.

The bundled DSB source baseline, portable patch and artifact hashes are in the
IDEA plugin's `src/main/resources/dps/` directory. Its `LIVE_DEBUG.md` patch describes
the JSONL debug protocol. DSB must advertise live debugging protocol version 1;
older bundled runtimes are rejected during launch.

## Runnable acceptance example

Use `examples/debugger`. Its `verify-debugger.py` drives the public DAP stream
against real compiler and DSB child processes. Supply `--compiler`,
`--compiler-java`, `--dsb`, and `--dsb-java`. `--work` keeps the generated project
and mappings; `--case` selects a scenario. `--legacy-dsb` additionally verifies
that a runtime without live debugging is rejected.

The delivered artifact hashes and automated/IDEA verification results are recorded
in [debugger-acceptance.md](debugger-acceptance.md).
