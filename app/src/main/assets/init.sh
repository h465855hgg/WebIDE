#!/bin/bash
#
# WebIDE - A powerful IDE for Android web development.
# Copyright (C) 2025  如日中天  <3382198490@qq.com>
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see <https://www.gnu.org/licenses/>.
#
# 注意：不使用 set -e！
# CDN/网络不稳定时 apk/npm 安装会失败，使用 set -e 会导致整个脚本退出，
# 终端直接用不了。LSP 安装是可选的，失败不应该阻塞终端启动。

# 1. 环境变量配置
export PATH=/usr/local/bin:/usr/bin:/bin:/usr/local/sbin:/usr/sbin:/sbin
export HOME=/root

# 2. 写入欢迎语到 /etc/motd
cat > /etc/motd <<'EOF'
Welcome to WebIDE Terminal!

The Alpine Wiki contains a large amount of how-to guides and general
information about administrating Alpine systems.
See <https://wiki.alpinelinux.org/>.

Installing : apk add <pkg>
Updating : apk update && apk upgrade
EOF

# 3. 配置 DNS (如果没有配置过)
if [ ! -s /etc/resolv.conf ]; then
    echo "nameserver 8.8.8.8" > /etc/resolv.conf
fi

# 4. 美化终端提示符 (绿色用户名@主机名 当前路径 $)
export PS1="\[\e[38;5;46m\]\u\[\033[39m\]@localhost \[\033[39m\]\w \[\033[0m\]\\$ "

# 5. 检查并初始化环境 (Node.js & LSP) —— 可选步骤，失败不阻塞终端
# 仅在第一次运行(找不到 node 命令)时执行
if ! command -v node > /dev/null 2>&1; then
    echo -e "\e[34;1m[*] \e[0mInitializing Environment (Alpine 3.19)...\e[0m"

    # 更新软件源索引并安装基础依赖
    # 网络失败时只打印警告，不退出脚本
    if apk update && apk add bash gcompat glib nano nodejs npm; then
        # 安装 LSP 服务 (可选功能，编辑器已有本地补全作为后备)
        echo -e "\e[34;1m[*] \e[0mInstalling Language Servers...\e[0m"
        rm -rf /usr/local/lib/node_modules
        if npm install -g typescript typescript-language-server vscode-langservers-extracted; then
            echo -e "\e[32;1m[+] \e[0mEnvironment Ready! LSP features available.\e[0m"
        else
            echo -e "\e[33;1m[!] \e[0mLSP install failed (network issue). Terminal works fine.\e[0m"
            echo -e "\e[33;1m[!] \e[0mCode completion uses local mode. Retry later: npm install -g typescript-language-server vscode-langservers-extracted\e[0m"
        fi
    else
        echo -e "\e[33;1m[!] \e[0mapk install failed (CDN/network issue). Terminal works fine.\e[0m"
        echo -e "\e[33;1m[!] \e[0mRetry later: apk update && apk add nodejs npm\e[0m"
    fi
fi

# 6. 启动交互式 Shell
if [ "$#" -eq 0 ]; then
    # 加载系统配置
    if [ -f /etc/profile ]; then
        source /etc/profile
    fi

    # 显示欢迎语
    if [ -f /etc/motd ]; then
        cat /etc/motd
        echo "" # 空一行，美观
    fi

    # [🔥 核心修改] 默认进入 Alpine 系统根目录 /
    cd /

    # 启动 Bash
    /bin/bash
else
    # 如果有传入参数(比如执行具体命令)，则直接执行，不进入交互模式
    exec "$@"
fi
