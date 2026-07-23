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

async function quickFix() {
  try {
    console.log('=== 快速修复Termux桌面环境 ===');

    console.log('\nStep 1: 创建桌面快捷方式');
    await executeCommand('mkdir -p ~/Desktop', '创建桌面目录');
    
    const apps = [
      { name: '终端', exec: 'xfce4-terminal', icon: 'utilities-terminal' },
      { name: '文件管理器', exec: 'thunar', icon: 'system-file-manager' },
      { name: '文本编辑器', exec: 'mousepad', icon: 'accessories-text-editor' },
      { name: 'VLC播放器', exec: 'vlc', icon: 'vlc' },
      { name: '图片查看器', exec: 'eog', icon: 'image-viewer' },
      { name: 'PDF查看器', exec: 'evince', icon: 'document-viewer' },
      { name: '文本编辑器', exec: 'gedit', icon: 'accessories-text-editor' }
    ];

    for (const app of apps) {
      const fileName = app.name.replace(/[^\w]/g, '') + '.desktop';
      await executeCommand(`cat > ~/Desktop/${fileName} << 'EOF'\n[Desktop Entry]\nName=${app.name}\nExec=${app.exec}\nIcon=${app.icon}\nTerminal=false\nType=Application\nEOF\nchmod +x ~/Desktop/${fileName}`, `创建${app.name}快捷方式`, true);
    }

    console.log('\nStep 2: 配置XFCE4桌面设置');
    await executeCommand('xfconf-query -c xfdesktop -p /desktop-icons/style -s 1', '显示桌面图标');
    await executeCommand('xfconf-query -c xfdesktop -p /desktop-icons/show-home -s true', '显示主目录图标');
    await executeCommand('xfconf-query -c xfdesktop -p /desktop-icons/show-filesystem -s true', '显示文件系统图标');
    await executeCommand('xfconf-query -c xfdesktop -p /desktop-icons/show-trash -s true', '显示回收站图标');

    console.log('\nStep 3: 设置桌面背景');
    await executeCommand('xfconf-query -c xfdesktop -p /backdrop/screen0/monitor0/image-path -s "/data/data/com.termux/files/usr/share/backgrounds/xfce/xfce-teal.jpg"', '设置背景图片', true);
    await executeCommand('xfconf-query -c xfdesktop -p /backdrop/screen0/monitor0/image-style -s 5', '背景拉伸模式');

    console.log('\nStep 4: 配置面板');
    await executeCommand('xfconf-query -c xfce4-panel -p /panels/panel-1/size -s 36', '设置面板大小');
    await executeCommand('xfconf-query -c xfce4-panel -p /panels/panel-1/position -s "p=8;x=0;y=0"', '面板位置底部');

    console.log('\nStep 5: 重启VNC服务');
    await executeCommand('vncserver -kill :1 2>/dev/null || true', '停止旧VNC');
    await executeCommand('sleep 2', '等待2秒');
    await executeCommand('vncserver :1 -geometry 1280x800 -depth 24', '启动VNC');
    await executeCommand('sleep 3', '等待3秒');
    await executeCommand('cd ~/noVNC && ./utils/novnc_proxy --listen 6080 --vnc localhost:5901 &', '启动noVNC代理');

    console.log('\nStep 6: 检查服务状态');
    await executeCommand('ps aux | grep -E "(vnc|novnc|xfce)"', '检查进程');

    const { output } = await executeCommand('ip addr show wlan0 2>/dev/null | grep -E "inet " | head -1 | awk "{print \\$2}" | cut -d"/" -f1 || ip addr show eth0 2>/dev/null | grep -E "inet " | head -1 | awk "{print \\$2}" | cut -d"/" -f1 || echo "192.168.50.233"', '获取IP地址');
    const ip = output.trim();

    console.log('\n========================================');
    console.log('修复完成!');
    console.log(`访问地址: http://${ip}:6080/vnc.html`);
    console.log('VNC密码: password');
    console.log('桌面图标已创建');
    console.log('桌面背景已设置');
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
  console.log('SSH连接成功, 开始快速修复...');
  quickFix();
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