#math:test/perf/std_loop1
scoreboard players operation temp int += 10000 int
#循环迭代
scoreboard players remove loop int 1
execute if score loop int matches 1.. run function math:test/perf/std_loop1