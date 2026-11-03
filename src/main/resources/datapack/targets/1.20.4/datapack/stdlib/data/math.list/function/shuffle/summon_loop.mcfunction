summon marker 0 11 0 {Tags:["stmp0","stmp1"]}
scoreboard players add stemp0 int 1
execute if score stemp0 int < stemp1 int run function math.list:shuffle/summon_loop