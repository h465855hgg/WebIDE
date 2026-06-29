#!/bin/bash
# Alpine rootfs 构建脚本（预装 Node.js + LSP，彻底离线）
# 用法：在 WSL 中以 root 运行
#   bash build-rootfs.sh aarch64   # 构建 arm64-v8a
#   bash build-rootfs.sh armhf     # 构建 armeabi-v7a
set -e

ARCH=${1:-aarch64}
ALPINE_VER=3.19.9
WORKDIR=/tmp/alpine-build-$ARCH
ROOTFS=$WORKDIR/rootfs
PROJECT_DIR="/mnt/d/Android/AndroidStudioProjects/WebIDE"
ASSETS_DIR="$PROJECT_DIR/app/src/main/assets"

if [ "$ARCH" = "aarch64" ]; then
    QEMU=/usr/bin/qemu-aarch64
    FLAVOR_DIR="$PROJECT_DIR/app/src/arm64/assets"
elif [ "$ARCH" = "armhf" ]; then
    QEMU=/usr/bin/qemu-arm
    FLAVOR_DIR="$PROJECT_DIR/app/src/arm32/assets"
else
    echo "Usage: $0 [aarch64|armhf]"
    exit 1
fi

echo "=== Building Alpine rootfs for $ARCH ==="

# 1. 下载 minirootfs
URL="https://dl-cdn.alpinelinux.org/alpine/v3.19/releases/$ARCH/alpine-minirootfs-$ALPINE_VER-$ARCH.tar.gz"
mkdir -p $WORKDIR
if [ ! -f $WORKDIR/rootfs.tar.gz ]; then
    echo "[1/7] Downloading Alpine $ALPINE_VER minirootfs ($ARCH)..."
    wget -O $WORKDIR/rootfs.tar.gz "$URL"
else
    echo "[1/7] minirootfs already downloaded."
fi

# 2. 解压
echo "[2/7] Extracting rootfs..."
rm -rf $ROOTFS
mkdir -p $ROOTFS
tar -xzf $WORKDIR/rootfs.tar.gz -C $ROOTFS

# 3. 复制 qemu 到 rootfs（chroot 内执行 arm 二进制需要）
echo "[3/7] Copying qemu emulator..."
cp $QEMU $ROOTFS/usr/bin/

# 4. 配置 DNS 和 apk 源
cp /etc/resolv.conf $ROOTFS/etc/resolv.conf
cat > $ROOTFS/etc/apk/repositories << 'EOF'
https://dl-cdn.alpinelinux.org/alpine/v3.19/main
https://dl-cdn.alpinelinux.org/alpine/v3.19/community
EOF

# 5. 挂载 /proc /sys /dev
echo "[4/7] Mounting filesystems..."
mount --bind /proc $ROOTFS/proc
mount --bind /sys $ROOTFS/sys
mount --bind /dev $ROOTFS/dev

# 清理函数
cleanup() {
    umount $ROOTFS/proc 2>/dev/null || true
    umount $ROOTFS/sys 2>/dev/null || true
    umount $ROOTFS/dev 2>/dev/null || true
}
trap cleanup EXIT

# 6. chroot 安装 Node.js + LSP
echo "[5/7] Installing Node.js, npm and LSP servers (may take 10-20 min)..."
chroot $ROOTFS /bin/sh -c '
    set -ex
    apk update
    apk add bash gcompat glib nodejs npm

    # 配置 npm 镜像（加速下载）
    npm config set registry https://registry.npmmirror.com

    # 安装 LSP 服务器
    npm install -g typescript typescript-language-server vscode-langservers-extracted

    # 清理缓存（减小体积）
    rm -rf /var/cache/apk/* || true
    rm -rf /root/.npm || true
    rm -rf /tmp/* || true
'

# 7. 卸载并打包
echo "[6/7] Unmounting..."
cleanup
trap - EXIT

echo "[7/7] Packing rootfs.bin..."
mkdir -p "$FLAVOR_DIR"
cd $ROOTFS
tar -czf "$FLAVOR_DIR/rootfs.bin" .

echo "=== Done! Output: $FLAVOR_DIR/rootfs.bin ==="
ls -lh "$FLAVOR_DIR/rootfs.bin"
