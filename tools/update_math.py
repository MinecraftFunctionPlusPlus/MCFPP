"""Vendor the scalar dependency closure from math3.1 and regenerate MCFPP adapters.

Usage: python tools/update_math.py --source /path/to/math3.1
The source must be commit 187aa724c8df9f16393b9f8b1c8bbe712378e36c (3.1.5).
Existing MCFPP arithmetic/conversion patches are deliberately retained.
"""
import argparse
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "src/main/resources/datapack/stdlib/data"
FLOAT = "math.float:hpo/float/"
OPERAND = "53aa19cc-a067-402b-8ba1-9328cc5fb6c1"
FIELDS = ("sign", "int0", "int1", "exp")


def write(identifier, lines):
    namespace, name = identifier.split(":")
    path = DATA / namespace / "function" / (name + ".mcfunction")
    path.parent.mkdir(parents=True, exist_ok=True)
    if isinstance(lines, list):
        lines = "\n".join(lines)
    path.write_text(lines.rstrip() + "\n", encoding="utf-8")


def op(target, operation, source):
    return f"scoreboard players operation {target} int {operation} {source} int"


def set_(name, value):
    return f"scoreboard players set {name} int {value}"


def copy(target, source):
    return [op(f"{target}_{field}", "=", f"{source}_{field}") for field in FIELDS]


def literal(target, sign=1, high=1000, low=0, exponent=1):
    return [set_(f"{target}_{field}", value) for field, value in zip(FIELDS, (sign, high, low, exponent))]


def right(source):
    return [f"scoreboard players operation {OPERAND} float_{field} = {source}_{field} int" for field in FIELDS]


def binary(operation):
    return f"execute as {OPERAND} run function {FLOAT}_{operation}"


def vendor(source):
    revision = subprocess.check_output(["git", "-C", str(source), "rev-parse", "HEAD"], text=True).strip()
    if revision != "187aa724c8df9f16393b9f8b1c8bbe712378e36c":
        raise SystemExit(f"Unexpected math3.1 revision: {revision}")
    pending = ["math:float/_sqrt", "math:_sincos", "math:_atan2"]
    visited = set()
    while pending:
        identifier = pending.pop()
        if identifier in visited:
            continue
        visited.add(identifier)
        namespace, name = identifier.split(":")
        original = source / "data" / namespace / "function" / (name + ".mcfunction")
        text = original.read_text(encoding="utf-8")
        # Existing tested MCFPP implementations own these float helpers.
        def remap(match):
            name = match.group(1)
            return FLOAT + name[6:] if name.startswith("float/") else "math:" + name
        output = re.sub(r"math:([a-z0-9_./-]+)", remap, text)
        mapped = remap(re.match(r"(.*)", identifier[5:]))
        target_namespace, target_name = mapped.split(":")
        destination = DATA / target_namespace / "function" / (target_name + ".mcfunction")
        if not name.startswith("float/") or name == "float/_sqrt" or not destination.exists():
            write(mapped, output)
        for dependency in re.findall(r"\bfunction\s+(math:[a-z0-9_./-]+)", text):
            dep_name = dependency[5:]
            if dep_name.startswith("float/") and dep_name != "float/_sqrt":
                dep_path = DATA / "math.float/function/hpo" / (dep_name + ".mcfunction")
                if dep_path.exists():
                    continue
            pending.append(dependency)


def scalar():
    write("math:scalar/isqrt", [
        op("ms_mid", "=", "ms_low"), op("ms_mid", "+=", "ms_high"), op("ms_mid", "/=", "2"),
        op("ms_quotient", "=", "ms_input"), op("ms_quotient", "/=", "ms_mid"),
        "execute if score ms_mid int <= ms_quotient int run " + op("ms_low", "=", "ms_mid"),
        "execute if score ms_mid int > ms_quotient int run " + op("ms_high", "=", "ms_mid"),
        op("ms_gap", "=", "ms_high"), op("ms_gap", "-=", "ms_low"),
        "execute if score ms_gap int matches 2.. run function math:scalar/isqrt",
    ])
    write("math:scalar/imul", [
        op("mp_product", "=", "mp_left"), op("mp_product", "*=", "mp_right"),
        op("mp_check", "=", "mp_product"),
        "execute unless score mp_left int matches 0 run " + op("mp_check", "/=", "mp_left"),
        "execute unless score mp_left int matches 0 unless score mp_check int = mp_right int run " + set_("mp_ok", 0),
        "execute if score mp_left int matches -1 if score mp_right int matches -2147483648 run " + set_("mp_ok", 0),
    ])
    write("math:scalar/ipow", [
        set_("mp_result", 1), set_("mp_ok", 1),
        "execute if score mp_n int matches ..-1 run " + set_("mp_ok", 0),
        "execute if score mp_base int matches 0 if score mp_n int matches 0 run " + set_("mp_ok", 0),
        "execute if score mp_ok int matches 1 if score mp_n int matches 1.. run function math:scalar/ipow_loop",
        "execute if score mp_ok int matches 0 run " + set_("mp_result", 0),
    ])
    write("math:scalar/ipow_loop", [
        op("mp_odd", "=", "mp_n"), op("mp_odd", "%=", "2"),
        "execute if score mp_odd int matches 1 run function math:scalar/ipow_multiply",
        op("mp_n", "/=", "2"),
        "execute if score mp_ok int matches 1 if score mp_n int matches 1.. run function math:scalar/ipow_square",
        "execute if score mp_ok int matches 1 if score mp_n int matches 1.. run function math:scalar/ipow_loop",
    ])
    write("math:scalar/ipow_multiply", [
        op("mp_left", "=", "mp_result"), op("mp_right", "=", "mp_base"),
        "function math:scalar/imul", op("mp_result", "=", "mp_product"),
    ])
    write("math:scalar/ipow_square", [
        op("mp_left", "=", "mp_base"), op("mp_right", "=", "mp_base"),
        "function math:scalar/imul", op("mp_base", "=", "mp_product"),
    ])
    for name, method in (("sincos", "_sincos"), ("atan2", "_atan2")):
        write("math:scalar/" + name, [
            "# Temporary calculation entity; no persistent entity, tick or forced chunk.",
            f'summon armor_stand ~ ~ ~ {{Tags:["mcfpp_math_angle"],Invisible:1b,Marker:1b,NoGravity:1b,Invulnerable:1b}}',
            "execute as @e[type=minecraft:armor_stand,tag=mcfpp_math_angle,sort=nearest,limit=1] at @s run function math:scalar/" + name + "_entity",
        ])
        write("math:scalar/" + name + "_entity", [
            "data modify storage math:io xyz set value [0.0d,0.0d,0.0d]",
            "function math:" + method,
            "kill @s",
        ])


def rounding():
    write(FLOAT + "round", [
        op("mr_m", "=", "float_int0"), op("mr_m", "*=", "10000"), op("mr_m", "+=", "float_int1"),
        "execute if score float_exp int matches 1..7 run function " + FLOAT + "round_fraction",
        "execute if score float_exp int matches ..0 run function " + FLOAT + "round_small",
    ])
    lines = []
    for exponent in range(1, 8):
        lines.append(f"execute if score float_exp int matches {exponent} run " + set_("mr_factor", 10 ** (8 - exponent)))
    lines += [
        op("mr_remainder", "=", "mr_m"), op("mr_remainder", "%=", "mr_factor"),
        op("mr_m", "-=", "mr_remainder"), set_("mr_up", 0),
        "execute if score mr_mode int matches 1 if score float_sign int matches -1 if score mr_remainder int matches 1.. run " + set_("mr_up", 1),
        "execute if score mr_mode int matches 2 if score float_sign int matches 1 if score mr_remainder int matches 1.. run " + set_("mr_up", 1),
        op("mr_half", "=", "mr_factor"), op("mr_half", "/=", "2"),
        "execute if score mr_mode int matches 3 if score float_sign int matches 1 if score mr_remainder int >= mr_half int run " + set_("mr_up", 1),
        "execute if score mr_mode int matches 3 if score float_sign int matches -1 if score mr_remainder int > mr_half int run " + set_("mr_up", 1),
        "execute if score mr_up int matches 1 run " + op("mr_m", "+=", "mr_factor"),
        "execute if score mr_m int matches 100000000.. run scoreboard players add float_exp int 1",
        "execute if score mr_m int matches 100000000.. run " + op("mr_m", "/=", "10"),
        op("float_int0", "=", "mr_m"), op("float_int0", "/=", "10000"),
        op("float_int1", "=", "mr_m"), op("float_int1", "%=", "10000"),
        "execute if score mr_m int matches 0 run function " + FLOAT + "zero",
    ]
    write(FLOAT + "round_fraction", lines)
    write(FLOAT + "round_small", [
        set_("mr_up", 0),
        "execute if score mr_mode int matches 1 if score float_sign int matches -1 run " + set_("mr_up", 1),
        "execute if score mr_mode int matches 2 if score float_sign int matches 1 run " + set_("mr_up", 1),
        "execute if score mr_mode int matches 3 if score float_exp int matches 0 if score float_sign int matches 1 if score mr_m int matches 50000000.. run " + set_("mr_up", 1),
        "execute if score mr_mode int matches 3 if score float_exp int matches 0 if score float_sign int matches -1 if score mr_m int matches 50000001.. run " + set_("mr_up", 1),
        "execute if score mr_up int matches 1 run " + set_("float_int0", 1000),
        "execute if score mr_up int matches 1 run " + set_("float_int1", 0),
        "execute if score mr_up int matches 1 run " + set_("float_exp", 1),
        "execute if score mr_up int matches 0 run function " + FLOAT + "zero",
    ])


def powers():
    write(FLOAT + "pow", [
        op("mf_negative", "=", "mf_n"), *copy("mf_base", "float"), *literal("mf_result"),
        "execute if score mf_n int matches -2147483648 run function " + FLOAT + "pow_min",
        "execute if score mf_n int matches -2147483647..-1 run " + op("mf_n", "*=", "-1"),
        "execute if score mf_n int matches 1.. run function " + FLOAT + "pow_loop",
        *copy("float", "mf_result"),
        "execute if score mf_negative int matches ..-1 run function " + FLOAT + "pow_inverse",
        "execute if score mf_base_sign int matches 0 if score mf_negative int matches ..0 run function " + FLOAT + "zero",
    ])
    write(FLOAT + "pow_min", [
        *copy("float", "mf_base"), *right("mf_base"), binary("mult"), *copy("mf_base", "float"),
        set_("mf_n", 1073741824),
    ])
    write(FLOAT + "pow_loop", [
        op("mf_odd", "=", "mf_n"), op("mf_odd", "%=", "2"),
        "execute if score mf_odd int matches 1 run function " + FLOAT + "pow_multiply",
        op("mf_n", "/=", "2"),
        "execute if score mf_n int matches 1.. run function " + FLOAT + "pow_square",
        "execute if score mf_n int matches 1.. run function " + FLOAT + "pow_loop",
    ])
    write(FLOAT + "pow_multiply", [*copy("float", "mf_result"), *right("mf_base"), binary("mult"), *copy("mf_result", "float")])
    write(FLOAT + "pow_square", [*copy("float", "mf_base"), *right("mf_base"), binary("mult"), *copy("mf_base", "float")])
    write(FLOAT + "pow_inverse", [*right("float"), *literal("float"), binary("div")])


def roots():
    # Bracket the root by adjacent powers of ten, then bisect. Unlike the upstream
    # fixed Newton count, convergence does not deteriorate for large degrees.
    write(FLOAT + "nroot", [
        *copy("mr_input", "float"), op("mr_odd", "=", "mr_n"), op("mr_odd", "%=", "2"),
        set_("mr_valid", 1),
        "execute if score mr_n int matches ..1 run " + set_("mr_valid", 0),
        "execute if score mr_input_sign int matches -1 if score mr_odd int matches 0 run " + set_("mr_valid", 0),
        "execute if score mr_valid int matches 1 unless score mr_input_sign int matches 0 run function " + FLOAT + "nroot_start",
        "execute if score mr_valid int matches 0 run function " + FLOAT + "zero",
        "execute if score mr_input_sign int matches 0 run function " + FLOAT + "zero",
    ])
    write(FLOAT + "nroot_start", [
        op("mr_degree", "=", "mr_input_exp"), "scoreboard players remove mr_degree int 1",
        op("mr_degree", "/=", "mr_n"),
        *literal("mr_low"), op("mr_low_exp", "=", "mr_degree"), "scoreboard players add mr_low_exp int 1",
        *copy("mr_high", "mr_low"), "scoreboard players add mr_high_exp int 1",
        set_("mr_iterations", 32), "function " + FLOAT + "nroot_loop",
        *copy("float", "mr_mid"), op("float_sign", "=", "mr_input_sign"),
    ])
    write(FLOAT + "nroot_loop", [
        *copy("float", "mr_low"), *right("mr_high"), binary("add"),
        *literal("mr_two", high=2000), *right("mr_two"), binary("div"), *copy("mr_mid", "float"),
        op("mf_n", "=", "mr_n"), "function " + FLOAT + "pow",
        set_("mr_less", 0),
        "execute if score float_exp int < mr_input_exp int run " + set_("mr_less", 1),
        "execute if score float_exp int = mr_input_exp int if score float_int0 int < mr_input_int0 int run " + set_("mr_less", 1),
        "execute if score float_exp int = mr_input_exp int if score float_int0 int = mr_input_int0 int if score float_int1 int <= mr_input_int1 int run " + set_("mr_less", 1),
        *["execute if score mr_less int matches 1 run " + command for command in copy("mr_low", "mr_mid")],
        *["execute if score mr_less int matches 0 run " + command for command in copy("mr_high", "mr_mid")],
        "scoreboard players remove mr_iterations int 1",
        "execute if score mr_iterations int matches 1.. run function " + FLOAT + "nroot_loop",
    ])


def angles():
    write(FLOAT + "angle", [
        *literal("ma_degrees", high=5729, low=5779, exponent=2), *right("ma_degrees"), binary("mult"),
        "scoreboard players add float_exp int 4",
        op("theta", "=", "float_int0"), op("theta", "*=", "10000"), op("theta", "+=", "float_int1"),
        *[f"execute if score float_exp int matches {e} run " + op("theta", "/=", str(10 ** (8-e))) for e in range(1, 8)],
        "execute if score float_exp int matches ..0 run " + set_("theta", 0),
        op("theta", "%=", "3600000"),
        op("ma_shift", "=", "float_exp"), "scoreboard players remove ma_shift int 8",
        "execute if score ma_shift int matches 1.. run function " + FLOAT + "angle_shift",
        op("theta", "*=", "float_sign"),
    ])
    write(FLOAT + "angle_shift", [
        op("theta", "*=", "10"), op("theta", "%=", "3600000"),
        "scoreboard players remove ma_shift int 1",
        "execute if score ma_shift int matches 1.. run function " + FLOAT + "angle_shift",
    ])


def initialize():
    path = DATA / "math/function/_init.mcfunction"
    constants = [line for line in path.read_text(encoding="utf-8").splitlines()
                 if re.fullmatch(r"scoreboard players set -?\d+ int -?\d+", line)]
    values = {int(line.split()[-1]) for line in constants}
    # math3.1 moved constants (including new sqrt estimate divisors) into
    # marker_control:_init. Import the numeric dependencies without that world module.
    pending = ["math:scalar/isqrt", "math:scalar/ipow", "math:scalar/sincos", "math:scalar/atan2"]
    pending += [FLOAT + name for name in ("_sqrt", "round", "angle", "pow", "nroot")]
    visited = set()
    required = set()
    while pending:
        identifier = pending.pop()
        if identifier in visited:
            continue
        visited.add(identifier)
        namespace, name = identifier.split(":")
        resource = DATA / namespace / "function" / (name + ".mcfunction")
        text = "\n".join(line for line in resource.read_text(encoding="utf-8").splitlines()
                         if not line.lstrip().startswith("#"))
        required.update(map(int, re.findall(r"(?<![a-zA-Z_])(-?\d+) int\b", text)))
        pending.extend(re.findall(r"\bfunction\s+([a-z0-9_.-]+:[a-z0-9_./-]+)", text))
    for value in sorted(required):
        if value not in values:
            constants.append(set_(str(value), value))
    write("math:_init", [
        "# Numeric initialization only. Calculation entities are scoped to individual calls.",
        "scoreboard objectives add int dummy", *constants,
        "scoreboard players set math mcfpp_init 1",
    ])


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    options = parser.parse_args()
    vendor(options.source)
    scalar()
    rounding()
    powers()
    roots()
    angles()
    initialize()
