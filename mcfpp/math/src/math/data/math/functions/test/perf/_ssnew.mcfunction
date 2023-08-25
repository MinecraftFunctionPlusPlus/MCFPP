#math:test/perf/_ssnew
gamerule maxCommandChainLength 2147483647
scoreboard players set perf_n int 1000000

scoreboard players set temp int 8593

worldborder set 1 0
worldborder set 50000000 50000
scoreboard players operation loop int = perf_n int
execute if score loop int matches 1.. run function math:test/perf/std_loop0
execute store result score perf_base int run worldborder get
scoreboard players operation perf_base_avg int = perf_base int
scoreboard players operation perf_base_min int = perf_base int
scoreboard players operation perf_base_max int = perf_base int

worldborder set 1 0
worldborder set 50000000 50000
scoreboard players operation loop int = perf_n int
execute if score loop int matches 1.. run function math:test/perf/std_loop1
execute store result score perf_line int run worldborder get
scoreboard players operation perf_line_avg int = perf_line int
scoreboard players operation perf_line_min int = perf_line int
scoreboard players operation perf_line_max int = perf_line int

worldborder set 1 0
worldborder set 50000000 50000
scoreboard players operation loop int = perf_n int
execute if score loop int matches 1.. run function math:test/perf/std_loop2
execute store result score perf_line_mult int run worldborder get
scoreboard players operation perf_line_mult_avg int = perf_line_mult int
scoreboard players operation perf_line_mult_min int = perf_line_mult int
scoreboard players operation perf_line_mult_max int = perf_line_mult int

worldborder set 50000000 0