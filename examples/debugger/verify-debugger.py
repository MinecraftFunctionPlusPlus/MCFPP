"""Exercise the packaged debugger through its public DAP stream and a real DSB process."""
import argparse
import json
from pathlib import Path
import queue
import shutil
import subprocess
import tempfile
import threading
import time


class Dap:
    def __init__(self, compiler_java, compiler):
        self.process = subprocess.Popen([compiler_java, "-jar", str(compiler), "debug", "--stdio"],
                                        stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        self.messages = queue.Queue()
        self.events = []
        self.sequence = 0
        self.stderr = []
        threading.Thread(target=self.read, daemon=True).start()
        threading.Thread(target=lambda: self.stderr.extend(self.process.stderr.read().decode("utf-8", errors="replace").splitlines()), daemon=True).start()

    def read(self):
        try:
            while True:
                headers = {}
                while True:
                    line = self.process.stdout.readline()
                    if not line:
                        raise EOFError("Debug adapter exited")
                    if line == b"\r\n":
                        break
                    key, value = line.decode("ascii").split(":", 1)
                    headers[key.lower()] = value.strip()
                count = int(headers["content-length"])
                payload = b""
                while len(payload) < count:
                    part = self.process.stdout.read(count - len(payload))
                    if not part:
                        raise EOFError("Incomplete DAP frame")
                    payload += part
                self.messages.put(json.loads(payload))
        except Exception as error:
            self.messages.put(error)

    def receive(self, deadline):
        message = self.messages.get(timeout=max(0.1, deadline - time.monotonic()))
        if isinstance(message, Exception):
            raise RuntimeError(f"{message}\n" + "\n".join(self.stderr))
        return message

    def request(self, command, arguments=None, timeout=180, success=True):
        self.sequence += 1
        identifier = self.sequence
        body = json.dumps({"seq": identifier, "type": "request", "command": command, "arguments": arguments or {}}).encode()
        self.process.stdin.write(f"Content-Length: {len(body)}\r\n\r\n".encode() + body)
        self.process.stdin.flush()
        deadline = time.monotonic() + timeout
        while True:
            message = self.receive(deadline)
            if message.get("type") == "event":
                self.events.append(message)
            elif message.get("request_seq") == identifier:
                if success and not message.get("success"):
                    logs = "".join(event.get("body", {}).get("output", "") for event in self.events if event.get("event") == "output")
                    raise AssertionError(f"{command}: {message}\n{logs}")
                return message.get("body", {}) if success else message

    def event(self, name, timeout=30):
        deadline = time.monotonic() + timeout
        while True:
            for index, event in enumerate(self.events):
                if event.get("event") == name:
                    return self.events.pop(index).get("body", {})
            if name == "stopped" and any(event.get("event") == "terminated" for event in self.events):
                logs = "".join(event.get("body", {}).get("output", "") for event in self.events if event.get("event") == "output")
                raise AssertionError("Session terminated before reaching the expected breakpoint\n" + logs)
            message = self.receive(deadline)
            if message.get("type") == "event":
                self.events.append(message)

    def launch(self, root, source, points, args, **target):
        self.request("initialize", {"adapterID": "mcfpp", "linesStartAt1": True, "columnsStartAt1": True})
        self.request("launch", {"projectPath": str(root), "dsbJar": str(args.dsb), "dsbJava": args.dsb_java, **target})
        self.event("initialized")
        result = self.request("setBreakpoints", {"source": {"path": str(source)}, "breakpoints": [{"line": line} for line in points]})
        assert all(point["verified"] for point in result["breakpoints"]), result
        self.request("configurationDone")

    def stopped_line(self):
        self.event("stopped")
        frames = self.request("stackTrace", {"threadId": 1})["stackFrames"]
        assert frames, "No source stack frames"
        return frames[0]["line"], frames

    def value(self, expression, frame=1):
        return self.request("evaluate", {"expression": expression, "frameId": frame, "context": "watch"})["result"]

    def close(self):
        try:
            if self.process.poll() is None:
                self.request("disconnect", timeout=10)
                self.process.stdin.close()
                self.process.wait(timeout=10)
        finally:
            if self.process.poll() is None:
                self.process.kill()


def verify_function(root, args):
    source = root / "src/main/mcfpp/main.mcfpp"
    dap = Dap(args.compiler_java, args.compiler)
    try:
        dap.launch(root, source, [9], args, function="debugger_demo:main")
        line, frames = dap.stopped_line()
        assert line == 9, (line, frames)
        assert dap.value("count") == "1"
        dap.request("stepIn", {"threadId": 1})
        line, frames = dap.stopped_line()
        assert line == 2 and len(frames) == 2, (line, frames)
        assert dap.value("value") == "1"
        assert dap.value("count", frame=2) == "1"
        dap.request("next", {"threadId": 1})
        assert dap.stopped_line()[0] == 3
        assert dap.value("copy") == "1"
        dap.request("stepOut", {"threadId": 1})
        assert dap.stopped_line()[0] == 10
        assert dap.value("count") == "2"
        dap.request("next", {"threadId": 1})
        assert dap.stopped_line()[0] == 11
        assert float(dap.value("decimal")) == 1.5
        dap.request("next", {"threadId": 1})
        assert dap.stopped_line()[0] == 12
        assert float(dap.value("decimal")) == 2.0
        dap.request("setBreakpoints", {"source": {"path": str(source)}, "breakpoints": [{"line": 13}]})
        dap.request("continue", {"threadId": 1})
        assert dap.stopped_line()[0] == 13
        assert dap.value("count") == "2"
        dap.request("continue", {"threadId": 1})
        assert dap.stopped_line()[0] == 13
        assert dap.value("count") == "3"
        dap.request("setBreakpoints", {"source": {"path": str(source)}, "breakpoints": []})
        dap.request("continue", {"threadId": 1})
        assert dap.event("exited")["exitCode"] == 0
        dap.event("terminated")
        console = "".join(event.get("body", {}).get("output", "") for event in dap.events
                          if event.get("event") == "output" and event.get("body", {}).get("category") == "stdout")
        assert console.count("Debugger demo finished") == 1, console
        assert "23" in console.splitlines(), console
        assert "1" not in console.splitlines(), console
        print("PASS ordinary function: breakpoint, nested stepping, caller locals, float values, loop revisit")
    finally:
        dap.close()


def verify_test(root, args):
    source = root / "src/test/mcfpp/lifecycle.mcfpp"
    source.parent.mkdir(parents=True, exist_ok=True)
    source.write_text("""import mcfpp.test:*
@BeforeEach
func prepare() {
    /scoreboard players set #phase mcfpp_test 1
}
@Test
func sample() {
    assertEquals(2, increment(1))
}
@AfterEach
func cleanup() {
    /scoreboard players set #phase mcfpp_test 2
}
""", encoding="utf-8")
    dap = Dap(args.compiler_java, args.compiler)
    try:
        dap.launch(root, source, [4, 8, 12], args, test="debugger_demo:sample")
        for expected in [4, 8, 12]:
            line, frames = dap.stopped_line()
            assert line == expected and Path(frames[0]["source"]["path"]) == source, (line, frames)
            dap.request("continue", {"threadId": 1})
        assert dap.event("exited")["exitCode"] == 0
        dap.event("terminated")
        print("PASS MCFPP test: original source, BeforeEach, test assertions, AfterEach")
    finally:
        dap.close()


def verify_values(root, args):
    source = root / "src/main/mcfpp/values.mcfpp"
    source.write_text("""data Payload { var values: list<int>; }
func recurse(depth: int): int {
    var saved = depth
    if (depth > 0) {
        return recurse(depth - 1) + saved
    }
    return saved
}
func inspectValues() {
    var depth = 99
    const limit = 42
    var payload = {values:[2,9]} as Payload
    if (depth == 99) {
        var depth = 7
        /say inner
    }
    var result = recurse(2)
    /say values
}
""", encoding="utf-8")
    dap = Dap(args.compiler_java, args.compiler)
    try:
        dap.launch(root, source, [4, 15, 18], args, function="debugger_demo:inspectValues")
        assert dap.stopped_line()[0] == 15
        assert dap.value("depth") == "7"
        assert dap.value("limit") == "42"
        assert dap.value("payload.values[1]") == "9"
        rejected = dap.request("evaluate", {"expression": "payload.clear()", "context": "watch"}, success=False)
        assert not rejected["success"]
        for depth in [2, 1, 0]:
            dap.request("continue", {"threadId": 1})
            line, frames = dap.stopped_line()
            assert line == 4 and len(frames) == 4 - depth, (line, frames)
            assert dap.value("depth") == str(depth)
            assert dap.value("saved") == str(depth)
            if depth < 2:
                assert dap.value("saved", frame=2) == str(depth + 1)
        dap.request("continue", {"threadId": 1})
        line, frames = dap.stopped_line()
        assert line == 18, (line, frames)
        outer = dap.value("depth")
        assert outer == "99", (outer, frames)
        assert dap.value("result") == "3"
        assert dap.value("payload.values[0]") == "2"
        dap.request("continue", {"threadId": 1})
        assert dap.event("exited")["exitCode"] == 0
        print("PASS values: shadowing, constants, fields, indices, recursive saved caller variables, read-only watches")
    finally:
        dap.close()


def verify_errors(root, args):
    source = root / "src/main/mcfpp/main.mcfpp"
    dap = Dap(args.compiler_java, args.compiler)
    original = source.read_text(encoding="utf-8")
    try:
        dap.launch(root, source, [9], args, function="debugger_demo:main")
        dap.stopped_line()
        invalid = dap.request("setBreakpoints", {"source": {"path": str(source)}, "breakpoints": [{"line": 6}]})
        assert not invalid["breakpoints"][0]["verified"]
        source.write_text(original + "\n// modified while paused\n", encoding="utf-8")
        result = dap.request("continue", {"threadId": 1}, success=False)
        assert not result["success"] and "Source changed" in result["message"], result
    finally:
        source.write_text(original, encoding="utf-8")
        dap.close()
    print("PASS errors: non-executable breakpoint, stale source rejection, disconnect while paused")
    broken = root / "src/main/mcfpp/errors.mcfpp"
    broken.write_text("func crash() {\n    /unknown_command\n}\n", encoding="utf-8")
    dap = Dap(args.compiler_java, args.compiler)
    try:
        dap.launch(root, broken, [], args, function="debugger_demo:crash")
        assert dap.event("exited")["exitCode"] == 1
        dap.event("terminated")
        output = "".join(event.get("body", {}).get("output", "") for event in dap.events if event.get("event") == "output")
        assert f"{broken}:2:" in output, output
    finally:
        dap.close()
    print("PASS runtime command error: source location and failed exit status")
    if args.legacy_dsb:
        dap = Dap(args.compiler_java, args.compiler)
        try:
            dap.request("initialize", {"adapterID": "mcfpp"})
            result = dap.request("launch", {"projectPath": str(root), "dsbJar": str(args.legacy_dsb.resolve()), "dsbJava": args.dsb_java,
                                           "function": "debugger_demo:main"}, success=False)
            assert not result["success"] and "live debugging protocol" in result["message"], result
        finally:
            dap.close()
        print("PASS runtime capability mismatch: launch rejected with a useful message")
    broken.write_text("func broken( { }\n", encoding="utf-8")
    dap = Dap(args.compiler_java, args.compiler)
    try:
        dap.request("initialize", {"adapterID": "mcfpp"})
        result = dap.request("launch", {"projectPath": str(root), "dsbJar": str(args.dsb), "dsbJava": args.dsb_java,
                                       "function": "debugger_demo:main"}, success=False)
        assert not result["success"] and "Debug build failed" in result["message"], result
    finally:
        dap.close()
        broken.unlink()
    print("PASS compilation failure: launch fails and the adapter exits cleanly")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--compiler", type=Path, required=True)
    parser.add_argument("--compiler-java", default="java")
    parser.add_argument("--dsb", type=Path, required=True)
    parser.add_argument("--dsb-java", help="Optional DSB Java override; otherwise reuse the compiler JDK")
    parser.add_argument("--work", type=Path, help="Keep the prepared project here for inspecting generated mappings")
    parser.add_argument("--case", choices=["all", "function", "test", "values", "errors"], default="all")
    parser.add_argument("--legacy-dsb", type=Path, help="Optional previous DSB JAR to check capability rejection")
    args = parser.parse_args()
    args.compiler = args.compiler.resolve()
    args.dsb = args.dsb.resolve()
    def verify(root):
        example = Path(__file__).parent
        root.mkdir(parents=True, exist_ok=True)
        shutil.copy(example / "mcfpp.toml", root)
        shutil.copy(example / "mcfpp-packages.json", root)
        shutil.copytree(example / "src", root / "src", dirs_exist_ok=True)
        for name, check in [("function", verify_function), ("test", verify_test), ("values", verify_values), ("errors", verify_errors)]:
            if args.case in ("all", name):
                check(root, args)
    if args.work:
        verify(args.work.resolve())
    else:
        with tempfile.TemporaryDirectory(prefix="mcfpp-debugger-") as temporary:
            verify(Path(temporary))


if __name__ == "__main__":
    main()
