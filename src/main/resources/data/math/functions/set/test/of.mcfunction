#math:set/test/of

tellraw @a "---set_test_of---"

data modify storage math:io list set value ["hello","cber","hi","im","world"]
function math:list/_toset

data modify storage math:io input set value "hi"
function math:set/_of
tellraw @a ["res: ",{"score":{"name":"res","objective":"int"}}]

data modify storage math:io input set value "no"
function math:set/_of
tellraw @a ["res: ",{"score":{"name":"res","objective":"int"}}]