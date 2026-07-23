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

async function deploy() {
  try {
    console.log('=== 直接在Termux中安装Xfce4桌面和noVNC ===');
    
    console.log('\n1. 更新Termux包管理器');
    await executeCommand('pkg update -y', '更新包管理器');
    
    console.log('\n2. 安装Xfce4桌面环境');
    await executeCommand('pkg install xfce4 xfce4-goodies -y', '安装Xfce4');
    
    console.log('\n3. 安装VNC服务器');
    await executeCommand('pkg install tigervnc -y', '安装tigervnc');
    
    console.log('\n4. 安装noVNC');
    await executeCommand('pkg install novnc websockify -y', '安装noVNC');
    
    console.log('\n5. 创建VNC配置');
    await executeCommand('mkdir -p ~/.vnc', '创建.vnc目录');
    await executeCommand('echo password | vncpasswd -f > ~/.vnc/passwd && chmod 600 ~/.vnc/passwd', '设置VNC密码');
    
    console.log('\n6. 配置xstartup');
    await executeCommand("cat > ~/.vnc/xstartup << 'EOF'\n#!/data/data/com.termux/files/usr/bin/bash\nxrdb $HOME/.Xresources\nstartxfce4 &\nEOF\nchmod +x ~/.vnc/xstartup", '配置xstartup');
    
    console.log('\n7. 启动VNC服务器');
    await executeCommand('vncserver :1 -geometry 1280x800 -depth 24', '启动VNC');
    
    console.log('\n8. 启动noVNC代理');
    await executeCommand('websockify -D 6080 localhost:5901', '启动noVNC');
    
    console.log('\n9. 获取IP地址');
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