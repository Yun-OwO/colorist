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
    console.log('=== 尝试多种镜像源安装Debian ===');
    
    console.log('\n1. 尝试阿里云Docker镜像');
    try {
      await executeCommand('proot-distro install registry.cn-hangzhou.aliyuncs.com/library/debian', '阿里云镜像');
      console.log('成功！');
      conn.end();
      return;
    } catch (e) {
      console.error('阿里云镜像失败:', e.message);
    }
    
    console.log('\n2. 尝试直接下载rootfs tarball');
    try {
      await executeCommand('cd ~ && wget -c https://github.com/termux/proot-distro/releases/download/v5.4.0/debian-aarch64.tar.xz', '下载Debian rootfs');
      await executeCommand('proot-distro install ~/debian-aarch64.tar.xz', '安装Debian');
      console.log('成功！');
      conn.end();
      return;
    } catch (e) {
      console.error('GitHub下载失败:', e.message);
    }
    
    console.log('\n3. 尝试清华大学镜像下载');
    try {
      await executeCommand('cd ~ && wget -c https://mirrors.tuna.tsinghua.edu.cn/docker-images/library/debian/latest/aarch64/debian-latest-aarch64-rootfs.tar.xz', '清华镜像下载');
      await executeCommand('proot-distro install ~/debian-latest-aarch64-rootfs.tar.xz', '安装Debian');
      console.log('成功！');
      conn.end();
      return;
    } catch (e) {
      console.error('清华镜像下载失败:', e.message);
    }
    
    console.log('\n4. 尝试搜索可用的rootfs镜像');
    await executeCommand('curl -s https://mirrors.tuna.tsinghua.edu.cn/termux/proot-distro/ | grep -i debian', '搜索清华镜像');
    await executeCommand('curl -s https://mirrors.tuna.tsinghua.edu.cn/docker-images/library/debian/ | head -50', '搜索Docker镜像');
    
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