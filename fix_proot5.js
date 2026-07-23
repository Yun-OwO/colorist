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
    console.log('=== 搜索可用镜像并尝试替代方案 ===');
    
    console.log('\n1. 查看proot-distro可用发行版列表');
    await executeCommand('proot-distro list -a', '查看所有可用发行版');
    
    console.log('\n2. 搜索清华镜像站的Docker镜像目录');
    await executeCommand('curl -s https://mirrors.tuna.tsinghua.edu.cn/docker-images/library/ | head -30', '查看Docker镜像目录');
    
    console.log('\n3. 尝试安装Ubuntu（国内镜像支持更好）');
    try {
      await executeCommand('proot-distro install ubuntu:24.04', '安装Ubuntu 24.04');
      console.log('Ubuntu安装成功！');
      conn.end();
      return;
    } catch (e) {
      console.error('Ubuntu安装失败:', e.message);
    }
    
    console.log('\n4. 尝试使用HTTP代理访问Docker Hub');
    try {
      await executeCommand('export http_proxy=http://10.0.0.1:8080 && export https_proxy=http://10.0.0.1:8080 && proot-distro install debian', '尝试使用代理');
      console.log('使用代理安装成功！');
      conn.end();
      return;
    } catch (e) {
      console.error('代理安装失败:', e.message);
    }
    
    console.log('\n5. 尝试下载通用Debian rootfs');
    try {
      await executeCommand('cd ~ && wget -c https://cdimage.debian.org/debian-cd/current/arm64/iso-cd/debian-12.7.0-arm64-netinst.iso', '下载Debian ISO');
      console.log('ISO下载成功！');
    } catch (e) {
      console.error('ISO下载失败:', e.message);
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