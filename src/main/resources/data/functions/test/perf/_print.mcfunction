#math:test/perf/_print

execute store result score sstemp int run scoreboard players operation perf_max int -= perf_avg int
scoreboard players operation perf_max int += perf_avg int
scoreboard players operation perf_avg int -= perf_min int
scoreboard players operation sstemp int > perf_avg int
scoreboard players operation perf_avg int += perf_min int
scoreboard players operation sstemp int *= 100 int
scoreboard players operation sstemp int /= perf_avg int

tellraw @a "---perf_test_result---"
tellraw @a ["perf_n:     ",{"score":{"name":"perf_n","objective":"int"}}]
tellraw @a ["min: ",{"score":{"name":"perf_min","objective":"int"}},"    max: ",{"score":{"name":"perf_max","objective":"int"}}]
tellraw @a ["avg: ",{"score":{"name":"perf_avg","objective":"int"}},"    err: ",{"score":{"name":"sstemp","objective":"int"}},"%"]

scoreboard players operation sstemp int = perf_line_avg int
scoreboard players operation sstemp int -= perf_base_avg int
scoreboard players operation sstemp0 int = perf_avg int
scoreboard players operation sstemp0 int -= perf_base_avg int
scoreboard players operation sstemp1 int = sstemp0 int
scoreboard players operation sstemp0 int /= sstemp int
scoreboard players operation sstemp1 int %= sstemp int
scoreboard players operation sstemp1 int *= 100 int
scoreboard players operation sstemp1 int /= sstemp int
data modify block 29999984 0 8591 back_text.messages[0] set value '["scb: ",{"score":{"name":"sstemp0","objective":"int"}},".",{"score":{"name":"sstemp1","objective":"int"}}]'

scoreboard players operation sstemp int = perf_line_mult_avg int
scoreboard players operation sstemp int -= perf_base_avg int
scoreboard players operation sstemp0 int = perf_avg int
scoreboard players operation sstemp0 int -= perf_base_avg int
scoreboard players operation sstemp1 int = sstemp0 int
scoreboard players operation sstemp0 int /= sstemp int
scoreboard players operation sstemp1 int %= sstemp int
scoreboard players operation sstemp1 int *= 100 int
scoreboard players operation sstemp1 int /= sstemp int
tellraw @a [{"nbt":"back_text.messages[0]","block":"29999984 0 8591","interpret":true},"    scb_mult: ",{"score":{"name":"sstemp0","objective":"int"}},".",{"score":{"name":"sstemp1","objective":"int"}}]