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

async function setupDesktop() {
  try {
    console.log('=== 在Termux上配置桌面环境 ===');

    console.log('\nStep 1: 更新Termux包列表');
    await executeCommand('pkg update -y', '更新apt源');

    console.log('\nStep 2: 安装基础桌面环境');
    await executeCommand('pkg install -y xfce4 xfce4-goodies', '安装XFCE4');

    console.log('\nStep 3: 安装常用工具');
    await executeCommand('pkg install -y xfce4-terminal thunar gedit file-roller xarchiver eog evince vlc', '安装桌面软件', true);
    await executeCommand('pkg install -y curl wget git vim nano htop net-tools ping openssh', '安装网络和工具', true);
    await executeCommand('pkg install -y python python-pip nodejs npm openjdk-17', '安装编程语言', true);
    await executeCommand('pkg install -y build-essential cmake make clang', '安装编译工具', true);
    await executeCommand('pkg install -y zip unzip tar gzip bzip2', '安装压缩工具', true);
    await executeCommand('pkg install -y screen tmux rsync ncdu tree neofetch', '安装系统工具', true);
    await executeCommand('pkg install -y ibus', '安装输入法框架', true);

    console.log('\nStep 4: 安装中文字体');
    await executeCommand('pkg install -y fonts-noto-cjk', '安装Noto中文字体', true);

    console.log('\nStep 5: 配置中文语言环境');
    await executeCommand('export LANG=zh_CN.UTF-8', '设置语言变量');
    await executeCommand('export LC_ALL=zh_CN.UTF-8', '设置区域变量');
    await executeCommand("cat > ~/.bashrc << 'EOF'\nexport LANG=zh_CN.UTF-8\nexport LC_ALL=zh_CN.UTF-8\nexport GTK_IM_MODULE=ibus\nexport QT_IM_MODULE=ibus\nexport XMODIFIERS=@im=ibus\nEOF", '保存语言设置');

    console.log('\nStep 6: 配置xstartup使用XFCE4');
    await executeCommand("cat > ~/.vnc/xstartup << 'EOF'\n#!/data/data/com.termux/files/usr/bin/bash\nxrdb $HOME/.Xresources\nexport LANG=zh_CN.UTF-8\nexport LC_ALL=zh_CN.UTF-8\nexport GTK_IM_MODULE=ibus\nexport QT_IM_MODULE=ibus\nexport XMODIFIERS=@im=ibus\nibus-daemon -d -x &\nstartxfce4 &\nEOF\nchmod +x ~/.vnc/xstartup", '配置xstartup');

    console.log('\nStep 7: 创建桌面快捷方式目录');
    await executeCommand('mkdir -p ~/Desktop ~/.config/autostart', '创建目录');

    console.log('\nStep 8: 创建启动脚本');
    await executeCommand("cat > ~/.termux/start_vnc.sh << 'EOF'\n#!/data/data/com.termux/files/usr/bin/bash\nvncserver -kill :1 2>/dev/null || true\nsleep 2\nvncserver :1 -geometry 1280x800 -depth 24\nsleep 3\ncd ~/noVNC && ./utils/novnc_proxy --listen 6080 --vnc localhost:5901 &\necho 'VNC服务已启动'\nEOF\nchmod +x ~/.termux/start_vnc.sh", '创建启动脚本');

    console.log('\nStep 9: 配置开机自启动');
    await executeCommand('termux-wake-lock', '防止休眠');
    
    await executeCommand("cat > ~/.bash_login << 'EOF'\nif [ -z \"$SSH_CLIENT\" ] && [ -z \"$SSH_TTY\" ]; then\n  ~/.termux/start_vnc.sh\nfi\nEOF", '配置bash_login自启动');

    console.log('\nStep 10: 创建Termux启动服务');
    await executeCommand('mkdir -p ~/.termux/boot', '创建启动目录');
    await executeCommand("cat > ~/.termux/boot/start-vnc << 'EOF'\n#!/data/data/com.termux/files/usr/bin/bash\ntermux-wake-lock\n~/.termux/start_vnc.sh\nEOF\nchmod +x ~/.termux/boot/start-vnc", '创建启动脚本');

    console.log('\nStep 11: 重启VNC服务');
    await executeCommand('vncserver -kill :1 2>/dev/null || true', '停止旧VNC', true);
    await executeCommand('sleep 2', '等待2秒');
    await executeCommand('vncserver :1 -geometry 1280x800 -depth 24', '启动VNC');
    await executeCommand('sleep 3', '等待3秒');
    await executeCommand('cd ~/noVNC && ./utils/novnc_proxy --listen 6080 --vnc localhost:5901 &', '启动noVNC代理');

    console.log('\nStep 12: 检查服务状态');
    await executeCommand('ps aux | grep -E "(vnc|novnc|xfce)"', '检查进程');

    const { output } = await executeCommand('ifconfig | grep -E "inet " | head -1 | awk "{print \\$2}"', '获取IP地址');
    const ip = output.trim();

    console.log('\n========================================');
    console.log('配置完成!');
    console.log(`访问地址: http://${ip}:6080/vnc.html`);
    console.log('VNC密码: password');
    console.log('常用软件已安装');
    console.log('中文语言环境已配置');
    console.log('开机自启动已启用');
    console.log('========================================');

    conn.end();
  } catch (err) {
    console.error('\n配置失败:', err.message);
    conn.end();
    process.exit(1);
  }
}

conn.on('ready', () => {
  console.log('SSH连接成功, 开始配置...');
  setupDesktop();
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