import { lstatSync, readFileSync, readdirSync, renameSync, writeFileSync } from "node:fs";
import { extname, join } from "node:path";


const regexpNames = /(?<== require\((?:'|")(?!antlr4ts).*)\.(?:js|mjs)(?=(?:'|")\))/gm
const regexpNamesTs = /(?<=from (?:'|")(?!antlr4ts).*)\.js(?=(?:'|"))/gm

function toCjs(path: string) {
    const files = readdirSync(path, 'utf-8')
    for (const fileName of files) {
        const pathNew = join(path, fileName)
        const stat = lstatSync(pathNew)
        if (stat.isDirectory()) {
            toCjs(pathNew)
        }
        else if (stat.isFile()) {
            const extname_ = extname(fileName)
            if (extname_ == ".ts") {
                const codes =  readFileSync(pathNew, { encoding: 'utf-8' })
                writeFileSync(pathNew, codes.replace(regexpNamesTs,".cjs"), {encoding: "utf8"})
                renameSync(pathNew,pathNew.replace(".ts",".cts"))
            }
            if (extname_ == ".js" || extname_ == ".cjs") {
                const codes =  readFileSync(pathNew, { encoding: 'utf-8' })
                writeFileSync(pathNew, codes.replace(regexpNames, ".cjs"), { encoding: "utf8" })
                renameSync(pathNew,pathNew.replace(".js",".cjs"))
            }
        }

    }
}

const path = join(process.cwd(), process.argv[2])
toCjs(path)

export {}