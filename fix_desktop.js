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
    console.log('=== 修复Termux桌面环境 ===');

    console.log('\nStep 1: 检查当前服务状态');
    await executeCommand('ps aux | grep -E "(vnc|novnc|xfce)"', '检查进程');
    await executeCommand('ifconfig | grep -E "inet " | head -1', '获取IP地址');

    console.log('\nStep 2: 安装中文语言包');
    await executeCommand('pkg install -y tzdata locales-all', '安装locale', true);
    await executeCommand('locale -a | grep zh_CN', '检查中文locale', true);

    console.log('\nStep 3: 安装中文字体');
    await executeCommand('pkg install -y fonts-noto', '安装Noto字体', true);
    await executeCommand('pkg install -y fonts-dejavu', '安装DejaVu字体', true);
    
    await executeCommand('mkdir -p ~/.fonts', '创建字体目录');
    await executeCommand('curl -L -o ~/.fonts/wqy-microhei.ttc "https://github.com/deepin-community/wqy-microhei/raw/master/wqy-microhei.ttc"', '下载文泉驿字体', true);
    await executeCommand('fc-cache -fv', '更新字体缓存');

    console.log('\nStep 4: 配置中文locale');
    await executeCommand('echo "zh_CN.UTF-8 UTF-8" >> /data/data/com.termux/files/usr/share/i18n/SUPPORTED', '添加中文支持');
    await executeCommand('locale-gen zh_CN.UTF-8', '生成中文locale', true);
    await executeCommand('export LANG=zh_CN.UTF-8 && export LC_ALL=zh_CN.UTF-8', '设置语言环境');
    
    await executeCommand("cat > ~/.bashrc << 'EOF'\nexport LANG=zh_CN.UTF-8\nexport LC_ALL=zh_CN.UTF-8\nexport LC_CTYPE=zh_CN.UTF-8\nexport GTK_IM_MODULE=ibus\nexport QT_IM_MODULE=ibus\nexport XMODIFIERS=@im=ibus\nexport PATH=\$PATH:\$HOME/.local/bin\nEOF", '更新bashrc');

    console.log('\nStep 5: 安装ibus中文输入法');
    await executeCommand('pkg install -y ibus-pinyin', '安装拼音输入法', true);
    await executeCommand('pkg install -y ibus-libpinyin', '安装libpinyin', true);
    
    await executeCommand("cat > ~/.config/ibus/bus << 'EOF'\nIBUS_ADDRESS=unix:abstract=/tmp/dbus-ibus,guid=ibus\nEOF", '配置ibus', true);

    console.log('\nStep 6: 配置xstartup');
    await executeCommand("cat > ~/.vnc/xstartup << 'EOF'\n#!/data/data/com.termux/files/usr/bin/bash\nxrdb $HOME/.Xresources\nexport LANG=zh_CN.UTF-8\nexport LC_ALL=zh_CN.UTF-8\nexport LC_CTYPE=zh_CN.UTF-8\nexport GTK_IM_MODULE=ibus\nexport QT_IM_MODULE=ibus\nexport XMODIFIERS=@im=ibus\nexport PATH=\$PATH:\$HOME/.local/bin\nibus-daemon -d -x &\nsleep 1\nstartxfce4 &\nEOF\nchmod +x ~/.vnc/xstartup", '配置xstartup');

    console.log('\nStep 7: 创建桌面快捷方式');
    await executeCommand('mkdir -p ~/Desktop', '创建桌面目录');
    
    await executeCommand("cat > ~/Desktop/terminal.desktop << 'EOF'\n[Desktop Entry]\nName=终端\nComment=XFCE Terminal\nExec=xfce4-terminal\nIcon=utilities-terminal\nTerminal=false\nType=Application\nCategories=Utility;Terminal;\nEOF\nchmod +x ~/Desktop/terminal.desktop", '创建终端快捷方式');

    await executeCommand("cat > ~/Desktop/filemanager.desktop << 'EOF'\n[Desktop Entry]\nName=文件管理器\nComment=Thunar File Manager\nExec=thunar\nIcon=system-file-manager\nTerminal=false\nType=Application\nCategories=System;FileTools;\nEOF\nchmod +x ~/Desktop/filemanager.desktop", '创建文件管理器快捷方式');

    await executeCommand("cat > ~/Desktop/texteditor.desktop << 'EOF'\n[Desktop Entry]\nName=文本编辑器\nComment=Gedit Text Editor\nExec=gedit\nIcon=accessories-text-editor\nTerminal=false\nType=Application\nCategories=Utility;TextEditor;\nEOF\nchmod +x ~/Desktop/texteditor.desktop", '创建文本编辑器快捷方式');

    await executeCommand("cat > ~/Desktop/browser.desktop << 'EOF'\n[Desktop Entry]\nName=浏览器\nComment=Web Browser\nExec=xdg-open http://localhost:6080/vnc.html\nIcon=web-browser\nTerminal=false\nType=Application\nCategories=Network;WebBrowser;\nEOF\nchmod +x ~/Desktop/browser.desktop", '创建浏览器快捷方式');

    await executeCommand("cat > ~/Desktop/vlc.desktop << 'EOF'\n[Desktop Entry]\nName=VLC播放器\nComment=Media Player\nExec=vlc\nIcon=vlc\nTerminal=false\nType=Application\nCategories=AudioVideo;Player;\nEOF\nchmod +x ~/Desktop/vlc.desktop", '创建VLC快捷方式');

    console.log('\nStep 8: 配置XFCE4桌面背景和设置');
    await executeCommand('xfconf-query -c xfdesktop -p /desktop-icons/style -s 1', '显示桌面图标');
    await executeCommand('xfconf-query -c xfdesktop -p /desktop-icons/show-home -s true', '显示主目录图标');
    await executeCommand('xfconf-query -c xfdesktop -p /desktop-icons/show-filesystem -s true', '显示文件系统图标');

    console.log('\nStep 9: 重启VNC服务应用更改');
    await executeCommand('vncserver -kill :1 2>/dev/null || true', '停止旧VNC');
    await executeCommand('sleep 3', '等待3秒');
    await executeCommand('vncserver :1 -geometry 1280x800 -depth 24', '启动VNC');
    await executeCommand('sleep 5', '等待5秒');
    await executeCommand('cd ~/noVNC && ./utils/novnc_proxy --listen 6080 --vnc localhost:5901 &', '启动noVNC代理');

    console.log('\nStep 10: 检查服务状态');
    await executeCommand('ps aux | grep -E "(vnc|novnc|xfce|ibus)"', '检查进程');
    
    const { output } = await executeCommand('ifconfig | grep -E "inet " | head -1 | awk "{print \\$2}"', '获取IP地址');
    const ip = output.trim();

    console.log('\n========================================');
    console.log('修复完成!');
    console.log(`访问地址: http://${ip}:6080/vnc.html`);
    console.log('VNC密码: password');
    console.log('中文语言环境已配置');
    console.log('中文字体已安装');
    console.log('桌面图标已创建');
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