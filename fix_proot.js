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

async function fixProot() {
  try {
    console.log('=== 修复 proot-distro 镜像源 ===');
    
    console.log('\n1. 查找 proot-distro 配置文件');
    await executeCommand('find $PREFIX -name "*.json" | grep -i proot', '查找proot配置文件');
    await executeCommand('ls -la $PREFIX/etc/proot-distro/ 2>/dev/null || ls -la $PREFIX/share/proot-distro/ 2>/dev/null', '查看proot配置目录');
    
    console.log('\n2. 查看 proot-distro 可用发行版');
    await executeCommand('proot-distro list', '查看可用发行版');
    
    console.log('\n3. 手动下载 Debian rootfs 并安装');
    await executeCommand('cd ~ && wget -c https://mirrors.tuna.tsinghua.edu.cn/termux/proot-distro/debian.tar.xz', '下载Debian rootfs');
    
    console.log('\n4. 安装Debian容器');
    await executeCommand('proot-distro install --file ~/debian.tar.xz debian', '安装Debian');
    
    console.log('\n=== 修复完成 ===');
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