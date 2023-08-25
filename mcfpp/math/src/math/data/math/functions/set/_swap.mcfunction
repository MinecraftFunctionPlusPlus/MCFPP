#math:set/_swap
data modify storage math:io stemp set from entity @s data.set
data modify entity @s data.set set from storage math:io set
data modify storage math:io set set from storage math:io stemp

data modify storage math:io stemp set from entity @s data.set_tags
data modify entity @s data.set_tags set from storage math:io set_tags
data modify storage math:io set_tags set from storage math:io stemp

scoreboard players operation set_cnt int >< @s set_cnt