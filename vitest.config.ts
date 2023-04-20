import { defineConfig } from 'vitest/config'

export default defineConfig({
    test:{},
    build: {
        outDir: "./build",
        lib: {
            entry: "./src/index.ts",
            name: "MCFPP",
            fileName:"index"
        },
        rollupOptions: {
            output: {
                banner: "#!/usr/bin/env node",
            }
        }
    }
})
