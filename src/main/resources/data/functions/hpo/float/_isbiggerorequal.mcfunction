#return @s >= ssobj
execute if score @s float_sign >= float_sign int run return 1
execute if score @s float_exp >= float_exp int run return 1
execute if score @s float_int0 >= float_int0 int run return 1
execute if score @s float_int1 >= float_int1 int run return 1
return 0