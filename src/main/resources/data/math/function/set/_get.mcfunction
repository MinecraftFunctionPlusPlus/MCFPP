#math:set/_get
data modify storage math:io set set from entity @s data.set
data modify storage math:io set_tags set from entity @s data.set_tags
scoreboard players operation set_cnt int = @s set_cnt