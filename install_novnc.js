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
    console.log('=== 安装noVNC并完成配置 ===');
    
    console.log('\n1. 安装git');
    await executeCommand('pkg install git -y', '安装git');
    
    console.log('\n2. 克隆noVNC仓库');
    await executeCommand('cd ~ && git clone https://github.com/novnc/noVNC.git', '克隆noVNC');
    
    console.log('\n3. 创建VNC配置');
    await executeCommand('mkdir -p ~/.vnc', '创建.vnc目录');
    await executeCommand('echo password | vncpasswd -f > ~/.vnc/passwd && chmod 600 ~/.vnc/passwd', '设置VNC密码');
    
    console.log('\n4. 配置xstartup');
    await executeCommand("cat > ~/.vnc/xstartup << 'EOF'\n#!/data/data/com.termux/files/usr/bin/bash\nxrdb $HOME/.Xresources\nfluxbox &\nEOF\nchmod +x ~/.vnc/xstartup", '配置xstartup');
    
    console.log('\n5. 启动VNC服务器');
    await executeCommand('vncserver :1 -geometry 1280x800 -depth 24', '启动VNC');
    
    console.log('\n6. 启动noVNC代理');
    await executeCommand('cd ~/noVNC && ./utils/novnc_proxy --listen 6080 --vnc localhost:5901 &', '启动noVNC');
    
    console.log('\n7. 等待服务启动');
    await executeCommand('sleep 3', '等待3秒');
    
    console.log('\n8. 检查服务状态');
    await executeCommand('netstat -tlnp | grep 6080', '检查6080端口');
    await executeCommand('netstat -tlnp | grep 5901', '检查5901端口');
    
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