#math:set/tags_sloop
data modify block 29999984 0 8591 back_text.messages[0] set value '{"nbt":"stemp[-1]","storage":"math:io"}'
data modify storage math:io set_tags append from block 29999984 0 8591 back_text.messages[0]
#循环迭代
data remove storage math:io stemp[-1]
scoreboard players remove sloop int 1
execute if score sloop int matches 1.. run function math:set/tags_sloop