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

async function check() {
  try {
    console.log('=== 检查服务状态 ===');
    
    console.log('\n1. 检查端口状态');
    await executeCommand('ss -tlnp | grep 6080', '检查6080端口');
    await executeCommand('ss -tlnp | grep 5901', '检查5901端口');
    
    console.log('\n2. 获取IP地址');
    const { output } = await executeCommand('hostname -I', '获取IP');
    const ip = output.trim();
    
    console.log('\n3. 检查进程');
    await executeCommand('ps aux | grep vnc', '检查VNC进程');
    await executeCommand('ps aux | grep novnc', '检查noVNC进程');
    
    console.log('\n========================================');
    console.log('服务状态检查完成!');
    console.log(`访问地址: http://${ip}:6080/vnc.html`);
    console.log('VNC密码: password');
    console.log('========================================');
    
    conn.end();
  } catch (err) {
    console.error('\n检查失败:', err.message);
    conn.end();
    process.exit(1);
  }
}

conn.on('ready', () => {
  console.log('SSH连接成功, 开始检查...');
  check();
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