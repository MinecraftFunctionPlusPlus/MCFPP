# Source debugging example

Open this directory in IntelliJ IDEA with the MCFPP plugin. Put a breakpoint on
`count = increment(count)` in `src/main/mcfpp/main.mcfpp`, then use **Debug** next
to `main`. Step into `increment`, inspect `value`, step out, and inspect the float
`decimal` before and after its update.

Debug `inspectValues` in `values.mcfpp` to expand `payload.values` and watch
`payload.values[1]`. Set breakpoints in `prepare`, `sample` and `cleanup` in
`src/test/mcfpp/lifecycle.mcfpp`, then use the test gutter's **Debug** action to
follow a single test through its hooks in the original source file.

The project targets Minecraft 26.3. Select JDK 25 in Settings → Tools → MCFPP.
Leave the DSB Java override blank to share that installation. The plugin provides
the debugger-enabled DSB JAR.

Run the command-line acceptance driver with Python 3:

```text
python verify-debugger.py --compiler /path/to/MCFPP.jar --compiler-java /jdk21/bin/java --dsb /path/to/datapack-sandbox-cli.jar --dsb-java /jdk25/bin/java
```

The driver prepares an isolated temporary project and exercises ordinary functions,
test hooks, recursion, structured watches and error handling. Add `--work /path/to/work`
to retain its sources and output for inspection. See `docs/debugger.md` in the
compiler repository for DAP launch arguments and mapping details.
