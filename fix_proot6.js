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

async function fixProot() {
  try {
    console.log('=== 直接尝试安装Ubuntu ===');
    
    console.log('\n1. 尝试安装Ubuntu 24.04');
    try {
      await executeCommand('proot-distro install ubuntu:24.04', '安装Ubuntu 24.04');
      console.log('Ubuntu安装成功！');
      conn.end();
      return;
    } catch (e) {
      console.error('Ubuntu安装失败:', e.message);
    }
    
    console.log('\n2. 尝试安装Alpine（轻量发行版）');
    try {
      await executeCommand('proot-distro install alpine:latest', '安装Alpine');
      console.log('Alpine安装成功！');
      conn.end();
      return;
    } catch (e) {
      console.error('Alpine安装失败:', e.message);
    }
    
    console.log('\n3. 尝试直接下载Debian rootfs');
    try {
      await executeCommand('cd ~ && wget -c https://raw.githubusercontent.com/termux/proot-distro/master/distributions/debian.sh', '下载Debian配置');
      await executeCommand('cat ~/debian.sh', '查看Debian配置');
    } catch (e) {
      console.error('下载配置失败:', e.message);
    }
    
    console.log('\n=== 所有尝试完成 ===');
    conn.end();
  } catch (err) {
    console.error('\n修复失败:', err.message);
    conn.end();
    process.exit(1);
  }
}

conn.on('ready', () => {
  console.log('SSH连接成功, 开始修复proot-distro...');
  fixProot();
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