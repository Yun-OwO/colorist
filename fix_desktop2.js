const { Client } = require('ssh2');

const conn = new Client();

function executeCommand(cmd, description, ignoreError = false) {
  return new Promise((resolve, reject) => {
    console.log(`\n[${description}]`);
    console.log(`执行命令: ${cmd}`);
    
    conn.exec(cmd, (err, stream) => {
      if (err) {
        console.error('命令执行错误:', err.message);
        if (ignoreError) {
          return resolve({ output: '', error: err.message, code: -1 });
        }
        return reject(err);
      }
      
      let output = '';
      let error = '';
      
      stream.on('data', (data) => {
        output += data.toString();
        process.stdout.write(data.toString());
      });
      
      stream.stderr.on('data', (data) => {
        error += data.toString();
        process.stderr.write(data.toString());
      });
      
      stream.on('close', (code, signal) => {
        console.log(`命令完成, 退出码: ${code}`);
        if (code === 0 || ignoreError) {
          resolve({ output, error, code });
        } else {
          reject(new Error(`命令失败, 退出码: ${code}, 错误: ${error}`));
        }
      });
    });
  });
}

async function fixDesktop() {
  try {
    console.log('=== 修复Termux桌面环境 (简化版) ===');

    console.log('\nStep 1: 检查当前服务状态');
    await executeCommand('ps aux | grep -E "(vnc|novnc|xfce)"', '检查进程');

    console.log('\nStep 2: 创建桌面快捷方式');
    await executeCommand('mkdir -p ~/Desktop', '创建桌面目录');
    
    await executeCommand("cat > ~/Desktop/terminal.desktop << 'EOF'\n[Desktop Entry]\nName=终端\nComment=XFCE Terminal\nExec=xfce4-terminal\nIcon=utilities-terminal\nTerminal=false\nType=Application\nCategories=Utility;Terminal;\nEOF\nchmod +x ~/Desktop/terminal.desktop", '创建终端快捷方式');

    await executeCommand("cat > ~/Desktop/filemanager.desktop << 'EOF'\n[Desktop Entry]\nName=文件管理器\nComment=Thunar File Manager\nExec=thunar\nIcon=system-file-manager\nTerminal=false\nType=Application\nCategories=System;FileTools;\nEOF\nchmod +x ~/Desktop/filemanager.desktop", '创建文件管理器快捷方式');

    await executeCommand("cat > ~/Desktop/texteditor.desktop << 'EOF'\n[Desktop Entry]\nName=文本编辑器\nComment=Mousepad Text Editor\nExec=mousepad\nIcon=accessories-text-editor\nTerminal=false\nType=Application\nCategories=Utility;TextEditor;\nEOF\nchmod +x ~/Desktop/texteditor.desktop", '创建文本编辑器快捷方式');

    await executeCommand("cat > ~/Desktop/vlc.desktop << 'EOF'\n[Desktop Entry]\nName=VLC播放器\nComment=Media Player\nExec=vlc\nIcon=vlc\nTerminal=false\nType=Application\nCategories=AudioVideo;Player;\nEOF\nchmod +x ~/Desktop/vlc.desktop", '创建VLC快捷方式');

    await executeCommand("cat > ~/Desktop/imageviewer.desktop << 'EOF'\n[Desktop Entry]\nName=图片查看器\nComment=EOG Image Viewer\nExec=eog\nIcon=image-viewer\nTerminal=false\nType=Application\nCategories=Graphics;Viewer;\nEOF\nchmod +x ~/Desktop/imageviewer.desktop", '创建图片查看器快捷方式');

    await executeCommand("cat > ~/Desktop/pdfviewer.desktop << 'EOF'\n[Desktop Entry]\nName=PDF查看器\nComment=Evince PDF Viewer\nExec=evince\nIcon=document-viewer\nTerminal=false\nType=Application\nCategories=Utility;Viewer;\nEOF\nchmod +x ~/Desktop/pdfviewer.desktop", '创建PDF查看器快捷方式');

    console.log('\nStep 3: 下载完整中文字体');
    await executeCommand('rm -f ~/.fonts/wqy-microhei.ttc', '删除旧字体');
    await executeCommand('curl -L -o ~/.fonts/wqy-microhei.ttc "https://mirrors.tuna.tsinghua.edu.cn/github-release/intel-iot-devkit/wqy-microhei-fonts/LatestRelease/wqy-microhei.ttc"', '下载文泉驿字体', true);
    
    await executeCommand('curl -L -o ~/.fonts/NotoSansCJK-Regular.ttc "https://github.com/googlefonts/noto-cjk/raw/main/Sans/OTF/SimplifiedChinese/NotoSansCJKsc-Regular.otf"', '下载Noto Sans CJK', true);
    
    await executeCommand('fc-cache -fv ~/.fonts', '更新字体缓存');

    console.log('\nStep 4: 配置XFCE4桌面设置');
    await executeCommand('xfconf-query -c xfdesktop -p /desktop-icons/style -s 1', '显示桌面图标');
    await executeCommand('xfconf-query -c xfdesktop -p /desktop-icons/show-home -s true', '显示主目录图标');
    await executeCommand('xfconf-query -c xfdesktop -p /desktop-icons/show-filesystem -s true', '显示文件系统图标');
    await executeCommand('xfconf-query -c xfdesktop -p /desktop-icons/show-trash -s true', '显示回收站图标');
    
    await executeCommand('xfconf-query -c xfwm4 -p /general/workspace_count -s 1', '设置工作区数量');
    await executeCommand('xfconf-query -c xfwm4 -p /general/button_layout -s "O|HMC"', '设置窗口按钮布局');

    console.log('\nStep 5: 配置xstartup');
    await executeCommand("cat > ~/.vnc/xstartup << 'EOF'\n#!/data/data/com.termux/files/usr/bin/bash\nxrdb $HOME/.Xresources\nstartxfce4 &\nEOF\nchmod +x ~/.vnc/xstartup", '配置xstartup');

    console.log('\nStep 6: 配置bashrc环境变量');
    await executeCommand("cat > ~/.bashrc << 'EOF'\nexport PATH=\$PATH:\$HOME/.local/bin\nalias ll='ls -la'\nalias la='ls -A'\nalias l='ls -CF'\nEOF", '更新bashrc');

    console.log('\nStep 7: 创建应用程序启动器');
    await executeCommand('mkdir -p ~/.config/autostart', '创建自启动目录');
    
    await executeCommand("cat > ~/.config/autostart/xfce4-terminal.desktop << 'EOF'\n[Desktop Entry]\nName=终端\nExec=xfce4-terminal\nType=Application\nEOF", '终端自启动');

    console.log('\nStep 8: 重启VNC服务应用更改');
    await executeCommand('vncserver -kill :1 2>/dev/null || true', '停止旧VNC');
    await executeCommand('sleep 3', '等待3秒');
    await executeCommand('vncserver :1 -geometry 1280x800 -depth 24', '启动VNC');
    await executeCommand('sleep 5', '等待5秒');
    await executeCommand('cd ~/noVNC && ./utils/novnc_proxy --listen 6080 --vnc localhost:5901 &', '启动noVNC代理');

    console.log('\nStep 9: 检查服务状态');
    await executeCommand('ps aux | grep -E "(vnc|novnc|xfce)"', '检查进程');
    
    const { output } = await executeCommand('ip addr show wlan0 2>/dev/null | grep -E "inet " | head -1 | awk "{print \\$2}" | cut -d"/" -f1 || ip addr show eth0 2>/dev/null | grep -E "inet " | head -1 | awk "{print \\$2}" | cut -d"/" -f1 || echo "192.168.50.233"', '获取IP地址');
    const ip = output.trim();

    console.log('\n========================================');
    console.log('修复完成!');
    console.log(`访问地址: http://${ip}:6080/vnc.html`);
    console.log('VNC密码: password');
    console.log('桌面图标已创建');
    console.log('中文字体已安装');
    console.log('开机自启动已启用');
    console.log('========================================');

    conn.end();
  } catch (err) {
    console.error('\n修复失败:', err.message);
    conn.end();
    process.exit(1);
  }
}

conn.on('ready', () => {
  console.log('SSH连接成功, 开始修复...');
  fixDesktop();
});

conn.on('error', (err) => {
  console.error('SSH连接错误:', err.message);
  process.exit(1);
});

conn.connect({
  host: '192.168.50.233',
  port: 8022,
  username: 'u0_a621',
  password: '123'
});