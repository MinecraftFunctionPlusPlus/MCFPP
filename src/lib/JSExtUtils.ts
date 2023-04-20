import { lstatSync, readFileSync, readdirSync, writeFileSync } from "node:fs";
import { extname, join } from "node:path";


const regexpNames = /(from\s+)(["'])(?!.*\.js)(((\.?\.\/)|antlr4ts\/).*)(["'])/gm

function addJsExt(path: string) {
    const files = readdirSync(path, 'utf-8')
    for (const fileName of files) {
        const pathNew = join(path, fileName)
        const stat = lstatSync(pathNew)
        if (stat.isDirectory()) {
            addJsExt(pathNew)
        }
        else if (stat.isFile() && extname(fileName) == ".ts") {
            const codes =  readFileSync(pathNew, { encoding: 'utf-8' })
            writeFileSync(pathNew, codes.replace(regexpNames,"$1$2$3.js$6"), {encoding: "utf8"})
        }
    }
}
const path = join(process.cwd(), process.argv[2])
addJsExt(path)

export {}