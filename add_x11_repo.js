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
    console.log('=== 添加X11源并安装桌面环境 ===');
    
    console.log('\n1. 添加X11源');
    await executeCommand('pkg install x11-repo -y', '安装X11源');
    
    console.log('\n2. 更新包管理器');
    await executeCommand('pkg update -y', '更新包管理器');
    
    console.log('\n3. 搜索桌面环境包');
    await executeCommand('pkg search xfce', '搜索xfce');
    
    console.log('\n4. 搜索vnc包');
    await executeCommand('pkg search vnc', '搜索vnc');
    
    console.log('\n5. 安装xorg和vnc');
    await executeCommand('pkg install xorg-server tigervnc -y', '安装xorg和vnc');
    
    console.log('\n6. 安装fluxbox或icewm');
    await executeCommand('pkg install fluxbox -y', '安装fluxbox');
    
    console.log('\n7. 安装noVNC');
    await executeCommand('pkg install novnc websockify -y', '安装noVNC');
    
    console.log('\n8. 配置VNC');
    await executeCommand('mkdir -p ~/.vnc', '创建.vnc目录');
    await executeCommand('echo password | vncpasswd -f > ~/.vnc/passwd && chmod 600 ~/.vnc/passwd', '设置VNC密码');
    
    await executeCommand("cat > ~/.vnc/xstartup << 'EOF'\n#!/data/data/com.termux/files/usr/bin/bash\nxrdb $HOME/.Xresources\nfluxbox &\nEOF\nchmod +x ~/.vnc/xstartup", '配置xstartup');
    
    console.log('\n9. 启动VNC服务器');
    await executeCommand('vncserver :1 -geometry 1280x800 -depth 24', '启动VNC');
    
    console.log('\n10. 启动noVNC代理');
    await executeCommand('websockify -D 6080 localhost:5901', '启动noVNC');
    
    console.log('\n11. 获取IP地址');
    const { output } = await executeCommand('hostname -I', '获取IP');
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