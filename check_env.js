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
        resolve({ output, error, code });
      });
    });
  });
}

async function checkEnv() {
  try {
    console.log('=== 检查当前环境 ===');
    
    await executeCommand('proot-distro list', '检查proot-distro容器列表');
    
    await executeCommand('ls ~/noVNC 2>/dev/null || echo "noVNC未安装"', '检查noVNC');
    
    await executeCommand('ps aux | grep -E "(vnc|fluxbox)"', '检查运行中的服务');
    
    await executeCommand('ss -tlnp', '检查端口状态');
    
    await executeCommand('pkg list-installed 2>/dev/null | grep -E "(fluxbox|vnc|novnc)"', '检查已安装包');
    
    conn.end();
  } catch (err) {
    console.error('\n检查失败:', err.message);
    conn.end();
  }
}

conn.on('ready', () => {
  console.log('SSH连接成功');
  checkEnv();
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