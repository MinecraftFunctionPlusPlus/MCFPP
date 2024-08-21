#math:test/perf/_ssprint
tellraw @a "---perf_test---"

tellraw @a ["perf_n:     ",{"score":{"name":"perf_n","objective":"int"}}]

execute store result score sstemp int run scoreboard players operation perf_base_max int -= perf_base_avg int
scoreboard players operation perf_base_max int += perf_base_avg int
scoreboard players operation perf_base_avg int -= perf_base_min int
scoreboard players operation sstemp int > perf_base_avg int
scoreboard players operation perf_base_avg int += perf_base_min int
scoreboard players operation sstemp int *= 100 int
scoreboard players operation sstemp int /= perf_base_avg int
tellraw @a ["base:        {avg:",{"score":{"name":"perf_base_avg","objective":"int"}},{"text":", min:"},{"score":{"name":"perf_base_min","objective":"int"}},{"text":", max:"},{"score":{"name":"perf_base_max","objective":"int"}},{"text":", err:"},{"score":{"name":"sstemp","objective":"int"}},"%}"]

execute store result score sstemp int run scoreboard players operation perf_line_max int -= perf_line_avg int
scoreboard players operation perf_line_max int += perf_line_avg int
scoreboard players operation perf_line_avg int -= perf_line_min int
scoreboard players operation sstemp int > perf_line_avg int
scoreboard players operation perf_line_avg int += perf_line_min int
scoreboard players operation sstemp int *= 100 int
scoreboard players operation sstemp int /= perf_line_avg int
tellraw @a ["line:         {avg:",{"score":{"name":"perf_line_avg","objective":"int"}},{"text":", min:"},{"score":{"name":"perf_line_min","objective":"int"}},{"text":", max:"},{"score":{"name":"perf_line_max","objective":"int"}},{"text":", err:"},{"score":{"name":"sstemp","objective":"int"}},"%}"]

execute store result score sstemp int run scoreboard players operation perf_line_mult_max int -= perf_line_mult_avg int
scoreboard players operation perf_line_mult_max int += perf_line_mult_avg int
scoreboard players operation perf_line_mult_avg int -= perf_line_mult_min int
scoreboard players operation sstemp int > perf_line_mult_avg int
scoreboard players operation perf_line_mult_avg int += perf_line_mult_min int
scoreboard players operation sstemp int *= 100 int
scoreboard players operation sstemp int /= perf_line_mult_avg int
tellraw @a ["line_mult:     {avg:",{"score":{"name":"perf_line_mult_avg","objective":"int"}},{"text":", min:"},{"score":{"name":"perf_line_mult_min","objective":"int"}},{"text":", max:"},{"score":{"name":"perf_line_mult_max","objective":"int"}},{"text":", err:"},{"score":{"name":"sstemp","objective":"int"}},"%}"]