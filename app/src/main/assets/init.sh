#!/bin/sh
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

# 只设置当前终端进程的环境；不要把 Android 动态库路径传给 Alpine 程序。
unset LD_LIBRARY_PATH LD_PRELOAD
export PATH=/usr/local/bin:/usr/bin:/bin:/usr/local/sbin:/usr/sbin:/sbin
export HOME=/root

# 非交互命令直接执行，避免欢迎语污染输出。
if [ "$#" -gt 0 ]; then
    exec "$@"
fi

# 欢迎语直接输出，不改写与 LSP 共用的 /etc 文件。
cat <<'EOF'
Welcome to WebIDE Terminal!

The Alpine Wiki contains a large amount of how-to guides and general
information about administrating Alpine systems.
See <https://wiki.alpinelinux.org/>.

Installing : apk add <pkg>
Updating : apk update && apk upgrade
EOF

# 保留终端原有的默认目录。优先 Bash，兼容只有 BusyBox 的旧 rootfs。
# 不在打开会话时安装/升级软件，也不依赖 Node.js 能否运行。
cd / || exit 1
if [ -x /bin/bash ]; then
    export SHELL=/bin/bash
    export PS1='\u@localhost:\w\$ '
    exec /bin/bash -i
else
    export SHELL=/bin/ash
    export PS1='\u@localhost:\w\$ '
    exec /bin/ash -i
fi
