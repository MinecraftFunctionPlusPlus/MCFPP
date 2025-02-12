#math:set/_of
# 判断元素是否在集合中
# 需要传入世界实体为执行者
# 输入storage math:io input
# 输出<res,int>

data modify entity @s Tags set from storage math:io set_tags

data modify block 29999984 0 8591 back_text.messages[0] set value '{"nbt":"input","storage":"math:io"}'
data modify entity @s Tags append from block 29999984 0 8591 back_text.messages[0]
execute store result score sstemp int if data entity @s Tags[]
execute store result score res int if score sstemp int = set_cnt int

data modify entity @s Tags set value ["math_marker"]