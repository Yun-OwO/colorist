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
    console.log('=== 检查Termux可用的图形界面包 ===');
    
    console.log('\n1. 搜索vnc相关包');
    await executeCommand('pkg search vnc', '搜索vnc');
    
    console.log('\n2. 搜索xorg相关包');
    await executeCommand('pkg search xorg', '搜索xorg');
    
    console.log('\n3. 搜索desktop相关包');
    await executeCommand('pkg search desktop', '搜索desktop');
    
    console.log('\n4. 搜索xfce相关包');
    await executeCommand('pkg search xfce', '搜索xfce');
    
    console.log('\n5. 搜索fluxbox相关包');
    await executeCommand('pkg search fluxbox', '搜索fluxbox');
    
    console.log('\n6. 搜索icewm相关包');
    await executeCommand('pkg search icewm', '搜索icewm');
    
    console.log('\n7. 搜索lxde相关包');
    await executeCommand('pkg search lxde', '搜索lxde');
    
    console.log('\n8. 查看已安装的包');
    await executeCommand('pkg list-installed | head -50', '已安装包');
    
    console.log('\n=== 检查完成 ===');
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