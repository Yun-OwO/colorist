#!/data/data/com.termux/files/usr/bin/bash

set -e

echo "=========================================="
echo "  在Termux上部署Debian容器和Web桌面"
echo "=========================================="

echo ""
echo "[1/5] 更新Termux包管理器..."
pkg update -y && pkg upgrade -y

echo ""
echo "[2/5] 安装proot-distro容器工具..."
pkg install proot-distro -y

echo ""
echo "[3/5] 安装Debian容器..."
proot-distro install debian

echo ""
echo "[4/5] 在Debian中安装桌面环境和noVNC..."
proot-distro login debian -- /bin/bash << 'DEBIAN_SCRIPT'
    set -e
    
    echo "更新Debian apt源..."
    apt update -y && apt upgrade -y
    
    echo "安装轻量级桌面环境 xfce4..."
    apt install -y xfce4 xfce4-goodies
    
    echo "安装VNC服务器..."
    apt install -y tightvncserver
    
    echo "安装noVNC（Web浏览器访问）..."
    apt install -y novnc websockify
    
    echo "设置VNC密码..."
    mkdir -p ~/.vnc
    echo "password" | vncpasswd -f > ~/.vnc/passwd
    chmod 600 ~/.vnc/passwd
    
    echo "配置xstartup..."
    cat > ~/.vnc/xstartup << 'EOF'
#!/bin/bash
xrdb $HOME/.Xresources
startxfce4 &
EOF
    chmod +x ~/.vnc/xstartup
    
    echo "Debian桌面环境安装完成!"
DEBIAN_SCRIPT

echo ""
echo "[5/5] 启动服务..."
proot-distro login debian -- /bin/bash << 'START_SCRIPT'
    echo "启动VNC服务器..."
    vncserver :1 -geometry 1280x800 -depth 24
    
    echo "启动noVNC代理..."
    websockify -D 6080 localhost:5901
    
    echo ""
    echo "=========================================="
    echo "  服务已启动!"
    echo "=========================================="
    echo ""
    echo "在浏览器中访问:"
    echo "  http://$(hostname -I | awk '{print $1}'):6080/vnc.html"
    echo ""
    echo "VNC密码: password"
    echo ""
START_SCRIPT

echo ""
echo "部署完成!"
echo ""
echo "访问地址: http://192.168.50.233:6080/vnc.html"