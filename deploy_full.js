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

async function deploy() {
  try {
    console.log('Step 1: 更新Termux包管理器并配置国内镜像源');
    await executeCommand("cat > $PREFIX/etc/apt/sources.list << 'EOF'\ndeb https://mirrors.tuna.tsinghua.edu.cn/termux/apt/termux-main stable main\nEOF", '配置Termux国内镜像源');
    await executeCommand('pkg update -y', '更新包管理器');
    
    console.log('\nStep 2: 安装proot-distro');
    await executeCommand('pkg install proot-distro -y', '安装proot-distro');
    
    console.log('\nStep 3: 删除旧Debian容器并重新安装');
    await executeCommand('proot-distro remove debian 2>/dev/null || true', '删除旧Debian容器');
    await executeCommand('proot-distro install debian', '安装Debian容器');
    
    console.log('\nStep 4: 在Debian中配置国内apt源');
    await executeCommand("proot-distro login debian -- bash -c \"cat > /etc/apt/sources.list << 'EOF'\ndeb https://mirrors.tuna.tsinghua.edu.cn/debian/ bookworm main contrib non-free non-free-firmware\ndeb https://mirrors.tuna.tsinghua.edu.cn/debian/ bookworm-updates main contrib non-free non-free-firmware\ndeb https://mirrors.tuna.tsinghua.edu.cn/debian/ bookworm-backports main contrib non-free non-free-firmware\ndeb https://mirrors.tuna.tsinghua.edu.cn/debian-security/ bookworm-security main contrib non-free non-free-firmware\nEOF\"", '写入Debian国内镜像源');
    
    console.log('\nStep 5: 更新Debian');
    await executeCommand('proot-distro login debian -- bash -c "apt update -y && apt upgrade -y"', '更新Debian');
    
    console.log('\nStep 6: 安装桌面环境和noVNC');
    await executeCommand('proot-distro login debian -- bash -c "apt install -y xfce4 xfce4-goodies tightvncserver novnc websockify"', '安装桌面环境和noVNC');
    
    console.log('\nStep 7: 配置VNC');
    await executeCommand('proot-distro login debian -- bash -c "mkdir -p ~/.vnc && echo password | vncpasswd -f > ~/.vnc/passwd && chmod 600 ~/.vnc/passwd"', '设置VNC密码');
    
    await executeCommand("proot-distro login debian -- bash -c \"cat > ~/.vnc/xstartup << 'EOF'\n#!/bin/bash\nxrdb $HOME/.Xresources\nstartxfce4 &\nEOF\nchmod +x ~/.vnc/xstartup\"", '配置xstartup');
    
    console.log('\nStep 8: 启动服务');
    await executeCommand('proot-distro login debian -- bash -c "vncserver :1 -geometry 1280x800 -depth 24"', '启动VNC服务器');
    await executeCommand('proot-distro login debian -- bash -c "websockify -D 6080 localhost:5901"', '启动noVNC代理');
    
    console.log('\nStep 9: 获取IP地址');
    const { output } = await executeCommand('hostname -I', '获取IP地址');
    const ip = output.trim();
    
    console.log('\n========================================');
    console.log('部署完成!');
    console.log(`访问地址: http://${ip}:6080/vnc.html`);
    console.log('VNC密码: password');
    console.log('========================================');
    
    conn.end();
  } catch (err) {
    console.error('\n部署失败:', err.message);
    conn.end();
    process.exit(1);
  }
}

conn.on('ready', () => {
  console.log('SSH连接成功, 开始部署...');
  deploy();
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