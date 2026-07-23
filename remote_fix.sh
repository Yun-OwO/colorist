#!/data/data/com.termux/files/usr/bin/bash

echo "=== 快速修复Termux桌面环境 ==="

echo -e "\nStep 1: 创建桌面快捷方式"
mkdir -p ~/Desktop

cat > ~/Desktop/terminal.desktop << 'EOF'
[Desktop Entry]
Name=终端
Comment=XFCE Terminal
Exec=xfce4-terminal
Icon=utilities-terminal
Terminal=false
Type=Application
Categories=Utility;Terminal;
EOF
chmod +x ~/Desktop/terminal.desktop

cat > ~/Desktop/filemanager.desktop << 'EOF'
[Desktop Entry]
Name=文件管理器
Comment=Thunar File Manager
Exec=thunar
Icon=system-file-manager
Terminal=false
Type=Application
Categories=System;FileTools;
EOF
chmod +x ~/Desktop/filemanager.desktop

cat > ~/Desktop/mousepad.desktop << 'EOF'
[Desktop Entry]
Name=文本编辑器
Comment=Mousepad Text Editor
Exec=mousepad
Icon=accessories-text-editor
Terminal=false
Type=Application
Categories=Utility;TextEditor;
EOF
chmod +x ~/Desktop/mousepad.desktop

cat > ~/Desktop/vlc.desktop << 'EOF'
[Desktop Entry]
Name=VLC播放器
Comment=Media Player
Exec=vlc
Icon=vlc
Terminal=false
Type=Application
Categories=AudioVideo;Player;
EOF
chmod +x ~/Desktop/vlc.desktop

cat > ~/Desktop/eog.desktop << 'EOF'
[Desktop Entry]
Name=图片查看器
Comment=EOG Image Viewer
Exec=eog
Icon=image-viewer
Terminal=false
Type=Application
Categories=Graphics;Viewer;
EOF
chmod +x ~/Desktop/eog.desktop

cat > ~/Desktop/evince.desktop << 'EOF'
[Desktop Entry]
Name=PDF查看器
Comment=Evince PDF Viewer
Exec=evince
Icon=document-viewer
Terminal=false
Type=Application
Categories=Utility;Viewer;
EOF
chmod +x ~/Desktop/evince.desktop

cat > ~/Desktop/gedit.desktop << 'EOF'
[Desktop Entry]
Name=文本编辑器(gedit)
Comment=Gedit Text Editor
Exec=gedit
Icon=accessories-text-editor
Terminal=false
Type=Application
Categories=Utility;TextEditor;
EOF
chmod +x ~/Desktop/gedit.desktop

echo -e "\nStep 2: 创建开机自启动脚本"
mkdir -p ~/.termux/boot

cat > ~/.termux/boot/start-vnc << 'EOF'
#!/data/data/com.termux/files/usr/bin/bash
termux-wake-lock
sleep 5
vncserver -kill :1 2>/dev/null || true
sleep 2
vncserver :1 -geometry 1280x800 -depth 24
sleep 5
cd ~/noVNC && ./utils/novnc_proxy --listen 6080 --vnc localhost:5901 &
EOF
chmod +x ~/.termux/boot/start-vnc

echo -e "\nStep 3: 配置xstartup"
cat > ~/.vnc/xstartup << 'EOF'
#!/data/data/com.termux/files/usr/bin/bash
xrdb $HOME/.Xresources
startxfce4 &
EOF
chmod +x ~/.vnc/xstartup

echo -e "\nStep 4: 重启VNC服务"
vncserver -kill :1 2>/dev/null || true
sleep 3
vncserver :1 -geometry 1280x800 -depth 24
sleep 5
cd ~/noVNC && ./utils/novnc_proxy --listen 6080 --vnc localhost:5901 &

echo -e "\nStep 5: 检查服务状态"
ps aux | grep -E "(vnc|novnc|xfce)"

echo -e "\n========================================"
echo "修复完成!"
echo "访问地址: http://$(ip addr show wlan0 2>/dev/null | grep -E "inet " | head -1 | awk '{print $2}' | cut -d"/" -f1 || ip addr show eth0 2>/dev/null | grep -E "inet " | head -1 | awk '{print $2}' | cut -d"/" -f1 || echo "192.168.50.233"):6080/vnc.html"
echo "VNC密码: password"
echo "桌面图标已创建"
echo "开机自启动已启用"
echo "========================================"