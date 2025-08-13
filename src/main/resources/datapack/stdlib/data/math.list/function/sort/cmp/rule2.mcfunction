#math.list:sort/rule2
#这个函数传入了两个元素： storage math:io cmp.a和cmp.b
#需要根据自定义排序规则，比较两个元素的大小
#如果a元素在规则里比b元素靠前，把1返回到sres int
#否则把0返回到sres int
#例如排序规则是比较a和b的出生日期谁更靠前
execute store result score sstemp0 int run data get storage math:io cmp.a.birth
execute store result score sstemp1 int run data get storage math:io cmp.b.birth
execute store result score sres int if score sstemp0 int <= sstemp1 int