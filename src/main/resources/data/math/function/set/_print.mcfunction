#math:set/_print

tellraw @a "---set---"
tellraw @a ["set: ",{"nbt":"data.set","entity":"@s"}]
tellraw @a ["set_tags: ",{"nbt":"data.set_tags","entity":"@s"}]
tellraw @a ["set_cnt: ",{"score":{"name":"@s","objective":"set_cnt"}}]