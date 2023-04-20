# MCFPP

## Develop The Project?

开发环境：

- Node.js: *v18.14.0*
- Visual Studio Code Plugins:

  - ANTLR4 grammar syntax support
  - JavaScript and TypeScript Nightly

开发环境构建：

```sh
npm install -g pnpm
```

```sh
git clone https://github.com/MinecraftFunctionPlusPlus/MCFPP.git
cd MCFPP
pnpm install
```

项目中使用的一些npm脚本：

```sh
pnpm run generator-antlr #生成antlr的文件 
pnpm run build #编译ts文件为js
pnpm run typecheck #运行tsc的编译检查
pnpm run start #编译之后可用，启动项目
pnpm run quick-start #不用编译可用，直接启动项目
pnpm run test:ui #测试项目

```
