const { Client } = require('ssh2');

const conn = new Client();

function executeCommand(cmd, description) {
  return new Promise((resolve, reject) => {
    console.log(`\n[${description}]`);
    console.log(`执行命令: ${cmd}`);
    
    conn.exec(cmd, (err, stream) => {
      if (err) {
        console.error('命令执行错误:', err.message);
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
        if (code === 0) {
          resolve({ output, error });
        } else {
          reject(new Error(`命令失败, 退出码: ${code}, 错误: ${error}`));
        }
      });
    });
  });
}

async function installApps() {
  try {
    console.log('=== 在Debian容器中安装常用软件 ===');

    console.log('\nStep 1: 更新Debian包列表');
    await executeCommand('proot-distro login debian -- bash -c "apt update -y"', '更新apt源');

    console.log('\nStep 2: 安装XFCE4桌面环境');
    await executeCommand('proot-distro login debian -- bash -c "apt install -y xfce4 xfce4-goodies"', '安装XFCE4');

    console.log('\nStep 3: 安装常用软件');
    const packages = [
      'firefox-esr',
      'chromium',
      'thunar',
      'xfce4-terminal',
      'gedit',
      'libreoffice',
      'file-roller',
      'gzip',
      'tar',
      'unzip',
      'zip',
      'curl',
      'wget',
      'git',
      'vim',
      'nano',
      'htop',
      'net-tools',
      'iputils-ping',
      'openssh-client',
      'xarchiver',
      'evince',
      'eog',
      'vlc',
      'transmission-gtk',
      'xfce4-screenshooter',
      'xfce4-power-manager',
      'xfce4-notifyd',
      'fonts-wqy-zenhei',
      'fonts-wqy-microhei',
      'ibus',
      'ibus-pinyin',
      'network-manager-gnome',
      'policykit-1-gnome',
      'tightvncserver',
      'novnc',
      'websockify'
    ];
    await executeCommand(`proot-distro login debian -- bash -c "apt install -y ${packages.join(' ')}"`, '安装常用软件');

    console.log('\nStep 4: 配置中文语言环境');
    await executeCommand('proot-distro login debian -- bash -c "apt install -y locales && locale-gen zh_CN.UTF-8"', '安装中文locale');
    await executeCommand('proot-distro login debian -- bash -c "update-locale LANG=zh_CN.UTF-8 LC_ALL=zh_CN.UTF-8"', '设置中文环境');

    console.log('\nStep 5: 创建桌面快捷方式目录');
    await executeCommand('proot-distro login debian -- bash -c "mkdir -p ~/Desktop ~/.config/autostart"', '创建目录');

    console.log('\nStep 6: 创建启动脚本');
    await executeCommand("proot-distro login debian -- bash -c \"cat > /usr/local/bin/start_vnc.sh << 'EOF'\n#!/bin/bash\nvncserver -kill :1 2>/dev/null || true\nsleep 2\nvncserver :1 -geometry 1280x800 -depth 24\nwebsockify -D 6080 localhost:5901\necho 'VNC服务已启动'\nEOF\nchmod +x /usr/local/bin/start_vnc.sh\"", '创建启动脚本');

    console.log('\nStep 7: 创建systemd服务');
    await executeCommand("proot-distro login debian -- bash -c \"cat > /etc/systemd/system/vncserver.service << 'EOF'\n[Unit]\nDescription=VNC Server for Desktop\nAfter=network.target\n\n[Service]\nType=forking\nUser=root\nExecStart=/usr/local/bin/start_vnc.sh\nExecStop=/usr/bin/vncserver -kill :1\nRestart=on-failure\nRestartSec=5\n\n[Install]\nWantedBy=multi-user.target\nEOF\"", '创建systemd服务');

    console.log('\nStep 8: 启用服务');
    await executeCommand('proot-distro login debian -- bash -c "systemctl daemon-reload"', '重载systemd');
    await executeCommand('proot-distro login debian -- bash -c "systemctl enable vncserver"', '启用开机自启');

    console.log('\nStep 9: 配置Debian的xstartup');
    await executeCommand("proot-distro login debian -- bash -c \"mkdir -p ~/.vnc && cat > ~/.vnc/xstartup << 'EOF'\n#!/bin/bash\nxrdb $HOME/.Xresources\nexport LANG=zh_CN.UTF-8\nexport LC_ALL=zh_CN.UTF-8\nexport GTK_IM_MODULE=ibus\nexport QT_IM_MODULE=ibus\nexport XMODIFIERS=@im=ibus\nibus-daemon -d -x &\nstartxfce4 &\nEOF\nchmod +x ~/.vnc/xstartup\"", '配置xstartup');

    console.log('\nStep 10: 设置VNC密码');
    await executeCommand('proot-distro login debian -- bash -c "echo password | vncpasswd -f > ~/.vnc/passwd && chmod 600 ~/.vnc/passwd"', '设置VNC密码');

    console.log('\nStep 11: 创建Termux启动脚本');
    await executeCommand("cat > ~/.termux/startup.sh << 'EOF'\n#!/data/data/com.termux/files/usr/bin/bash\nproot-distro login debian -- bash -c \"systemctl start vncserver\"\necho 'Debian VNC服务已启动'\nEOF\nchmod +x ~/.termux/startup.sh", '创建Termux启动脚本');

    console.log('\nStep 12: 配置Termux自启动');
    await executeCommand('termux-wake-lock', '防止休眠');
    
    await executeCommand("cat >> ~/.bashrc << 'EOF'\n\nif [ -z \"$SSH_CLIENT\" ] && [ -z \"$SSH_TTY\" ] && [ -z \"$PROOT_DISTRO\" ]; then\n  ~/.termux/startup.sh\nfi\nEOF", '添加到bashrc');

    console.log('\nStep 13: 重启Debian中的VNC服务');
    await executeCommand('proot-distro login debian -- bash -c "systemctl start vncserver"', '启动服务');

    console.log('\nStep 14: 检查服务状态');
    await executeCommand('proot-distro login debian -- bash -c "systemctl status vncserver"', '检查状态');

    const { output } = await executeCommand('hostname -I', '获取IP地址');
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
  installApps();
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