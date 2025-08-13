#math.list:set/sloop
data modify block 29999984 0 8591 back_text.messages[0] set value '{"nbt":"stemp[-1]","storage":"math:io"}'
data modify entity @s Tags append from block 29999984 0 8591 back_text.messages[0]
execute store result score sstemp int if data entity @s Tags[]
execute if score sstemp int > set_cnt int run function math.list:set/success
#循环迭代
data remove storage math:io stemp[-1]
scoreboard players remove sloop int 1
execute if score sloop int matches 1.. run function math.list:set/sloop