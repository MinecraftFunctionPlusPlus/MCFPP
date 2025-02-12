execute positioned 0.0 0.0 0.0 run tp @s ^ ^ ^-1.0
execute store result score stemp0 int run data get entity @s Pos[0] 10000
execute store result score stemp1 int run data get entity @s Pos[1] 10000
execute store result score stemp2 int run data get entity @s Pos[2] 10000
scoreboard players operation stemp3 int = stemp0 int
scoreboard players operation stemp4 int = stemp1 int
scoreboard players operation stemp5 int = stemp2 int
scoreboard players operation sstemp3 int = sstemp0 int
scoreboard players operation sstemp4 int = sstemp1 int
scoreboard players operation sstemp5 int = sstemp2 int

execute positioned 0.0 0.0 0.0 run tp @s ^-1.0 ^ ^
execute store result score stempi0 int run data get entity @s Pos[0] 10000
execute store result score stempi1 int run data get entity @s Pos[1] 10000
execute store result score stempi2 int run data get entity @s Pos[2] 10000

execute positioned 0.0 0.0 0.0 run tp @s ^ ^1.0 ^
execute store result score stempj0 int run data get entity @s Pos[0] 10000
execute store result score stempj1 int run data get entity @s Pos[1] 10000
execute store result score stempj2 int run data get entity @s Pos[2] 10000

execute facing entity @p eyes positioned 0.0 0.0 0.0 run tp @s ^ ^ ^1.0
execute store result score stempx int run data get entity @s Pos[0] 10000
execute store result score stempy int run data get entity @s Pos[1] 10000
execute store result score stempz int run data get entity @s Pos[2] 10000

scoreboard players operation stempu int = stempx int
scoreboard players operation stempu int *= stempi0 int
scoreboard players operation stemp6 int = stempy int
scoreboard players operation stemp6 int *= stempi1 int
scoreboard players operation stempu int += stemp6 int
scoreboard players operation stemp6 int = stempz int
scoreboard players operation stemp6 int *= stempi2 int
execute store result entity @s Pos[2] double 0.00000001 run scoreboard players operation stempu int += stemp6 int

scoreboard players operation stempv int = stempx int
scoreboard players operation stempv int *= stempj0 int
scoreboard players operation stemp6 int = stempy int
scoreboard players operation stemp6 int *= stempj1 int
scoreboard players operation stempv int += stemp6 int
scoreboard players operation stemp6 int = stempz int
scoreboard players operation stemp6 int *= stempj2 int
execute store result entity @s Pos[0] double 0.00000001 run scoreboard players operation stempv int += stemp6 int

execute positioned 0.0 0.0 0.0 facing entity @s feet rotated ~90.0 0.0 run tp @s ^ ^ ^1.0
execute store result score stempu int run data get entity @s Pos[2] 10000
execute store result score stempv int run data get entity @s Pos[0] 10000

scoreboard players operation stempi0 int *= stempu int
scoreboard players operation stempj0 int *= stempv int
scoreboard players operation stempi0 int += stempj0 int
scoreboard players operation stempi1 int *= stempu int
scoreboard players operation stempj1 int *= stempv int
scoreboard players operation stempi1 int += stempj1 int
scoreboard players operation stempi2 int *= stempu int
scoreboard players operation stempj2 int *= stempv int
scoreboard players operation stempi2 int += stempj2 int
scoreboard players operation stempi0 int /= 12500 int
scoreboard players operation stempi1 int /= 12500 int
scoreboard players operation stempi2 int /= 12500 int

scoreboard players operation stempn int = 3vec_n int
scoreboard players operation stempn int /= 5 int

execute store result entity @s Pos[0] double 0.0001 run scoreboard players operation stemp0 int += stempi0 int
execute store result entity @s Pos[1] double 0.0001 run scoreboard players operation stemp1 int += stempi1 int
execute store result entity @s Pos[2] double 0.0001 run scoreboard players operation stemp2 int += stempi2 int

execute positioned 0.0 0.0 0.0 facing entity @s feet run tp @s ^ ^ ^1.0
execute store result score stemp0 int run data get entity @s Pos[0] 10000
execute store result score stemp1 int run data get entity @s Pos[1] 10000
execute store result score stemp2 int run data get entity @s Pos[2] 10000

scoreboard players operation stemp6 int = 3vec_l int
scoreboard players operation stemp7 int = 3vec_l int
scoreboard players operation stemp6 int /= 10000 int
scoreboard players operation stemp7 int %= 10000 int

scoreboard players operation stemp8 int = stemp0 int
scoreboard players operation stemp0 int *= stemp6 int
scoreboard players operation stemp8 int *= stemp7 int
scoreboard players operation stemp8 int /= 10000 int
scoreboard players operation stemp0 int += stemp8 int
scoreboard players operation stemp0 int /= 3vec_n int

scoreboard players operation stemp8 int = stemp1 int
scoreboard players operation stemp1 int *= stemp6 int
scoreboard players operation stemp8 int *= stemp7 int
scoreboard players operation stemp8 int /= 10000 int
scoreboard players operation stemp1 int += stemp8 int
scoreboard players operation stemp1 int /= 3vec_n int

scoreboard players operation stemp8 int = stemp2 int
scoreboard players operation stemp2 int *= stemp6 int
scoreboard players operation stemp8 int *= stemp7 int
scoreboard players operation stemp8 int /= 10000 int
scoreboard players operation stemp2 int += stemp8 int
scoreboard players operation stemp2 int /= 3vec_n int

scoreboard players set sloop int 0
execute if score sloop int < stempn int run function math:3vec/dsp/loop1

scoreboard players operation stemp0 int = stemp3 int
scoreboard players operation stemp1 int = stemp4 int
scoreboard players operation stemp2 int = stemp5 int
scoreboard players operation sstemp0 int = sstemp3 int
scoreboard players operation sstemp1 int = sstemp4 int
scoreboard players operation sstemp2 int = sstemp5 int

execute store result entity @s Pos[0] double 0.0001 run scoreboard players operation stemp0 int -= stempi0 int
execute store result entity @s Pos[1] double 0.0001 run scoreboard players operation stemp1 int -= stempi1 int
execute store result entity @s Pos[2] double 0.0001 run scoreboard players operation stemp2 int -= stempi2 int

execute positioned 0.0 0.0 0.0 facing entity @s feet run tp @s ^ ^ ^1.0
execute store result score stemp0 int run data get entity @s Pos[0] 10000
execute store result score stemp1 int run data get entity @s Pos[1] 10000
execute store result score stemp2 int run data get entity @s Pos[2] 10000

scoreboard players operation stemp6 int = 3vec_l int
scoreboard players operation stemp7 int = 3vec_l int
scoreboard players operation stemp6 int /= 10000 int
scoreboard players operation stemp7 int %= 10000 int

scoreboard players operation stemp8 int = stemp0 int
scoreboard players operation stemp0 int *= stemp6 int
scoreboard players operation stemp8 int *= stemp7 int
scoreboard players operation stemp8 int /= 10000 int
scoreboard players operation stemp0 int += stemp8 int
scoreboard players operation stemp0 int /= 3vec_n int

scoreboard players operation stemp8 int = stemp1 int
scoreboard players operation stemp1 int *= stemp6 int
scoreboard players operation stemp8 int *= stemp7 int
scoreboard players operation stemp8 int /= 10000 int
scoreboard players operation stemp1 int += stemp8 int
scoreboard players operation stemp1 int /= 3vec_n int

scoreboard players operation stemp8 int = stemp2 int
scoreboard players operation stemp2 int *= stemp6 int
scoreboard players operation stemp8 int *= stemp7 int
scoreboard players operation stemp8 int /= 10000 int
scoreboard players operation stemp2 int += stemp8 int
scoreboard players operation stemp2 int /= 3vec_n int

scoreboard players set sloop int 0
execute if score sloop int < stempn int run function math:3vec/dsp/loop1

tp @s ~ ~ ~