/**
MIT License

Copyright (c) 2023 XiLaiTL

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */
import { lstatSync, readFileSync, readdirSync, renameSync, writeFileSync } from "node:fs";
import { extname, join } from "node:path";


const regexpNames = /(?<== require\((?:'|").*)\.(?:js|mjs)(?=(?:'|")\))/gm
const regexpNamesTs = /(?<=from (?:'|").*)\.js(?=(?:'|"))/gm

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