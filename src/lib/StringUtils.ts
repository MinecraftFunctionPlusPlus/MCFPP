//from https://blog.csdn.net/JQery/article/details/123568298
export function toSnakeCase(name: string) {
    return name.replace(/(?<=[a-z0-9])([A-Z])/g,"_$1").toLowerCase();
}
export function toCamelCase(name:string) {
    return name.replace(/\_(\w)/g, (all, letter)=>{
        return letter.toUpperCase();
    });
}