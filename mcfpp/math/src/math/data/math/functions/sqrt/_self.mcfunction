#math:sqrt/_self
execute store result score stemp0 int store result score stemp1 int store result score stemp2 int run scoreboard players operation stemp3 int = sqrt int
execute if score sqrt int matches ..13924 run function math:sqrt/range5
execute if score sqrt int matches 13925..16777216 run function math:sqrt/range6
execute if score sqrt int matches 16777217.. run function math:sqrt/range7
scoreboard players operation stemp0 int /= sqrt int
scoreboard players operation sqrt int += stemp0 int
scoreboard players operation sqrt int /= 2 int
scoreboard players operation stemp1 int /= sqrt int
scoreboard players operation sqrt int += stemp1 int
scoreboard players operation sqrt int /= 2 int
scoreboard players operation stemp2 int /= sqrt int
scoreboard players operation sqrt int += stemp2 int
scoreboard players operation sqrt int /= 2 int
scoreboard players operation stemp3 int /= sqrt int
scoreboard players operation sqrt int += stemp3 int
scoreboard players operation sqrt int /= 2 int