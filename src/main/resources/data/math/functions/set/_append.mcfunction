#math:set/_append
#需要传入世界实体为执行者
data modify block 29999984 0 8591 back_text.messages[0] set value '{"nbt":"input","storage":"math:io"}'

data modify entity @s Tags set from storage math:io set_tags
data modify entity @s Tags append from block 29999984 0 8591 back_text.messages[0]
data modify storage math:io set_tags set from entity @s Tags

execute store result score sstemp int if data storage math:io set_tags[]
execute if score sstemp int > set_cnt int run function math:set/append_success

data modify entity @s Tags set value ["math_marker"]