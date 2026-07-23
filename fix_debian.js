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

async function fixDebian() {
  try {
    console.log('=== 修复Debian容器 ===');

    console.log('\nStep 1: 检查proot-distro状态');
    await executeCommand('proot-distro list', '检查容器列表');
    await executeCommand('proot-distro version', '检查版本');

    console.log('\nStep 2: 尝试修复Debian容器');
    await executeCommand('proot-distro remove debian 2>/dev/null || true', '删除旧容器', true);
    await executeCommand('sleep 2', '等待2秒');

    console.log('\nStep 3: 尝试手动下载Debian rootfs');
    await executeCommand('cd ~ && wget -c https://mirrors.tuna.tsinghua.edu.cn/debian-cdimage/ports/latest/debian-bookworm-DI-aarch64-netinst.iso 2>/dev/null || echo "下载ISO失败"', '下载ISO', true);

    console.log('\nStep 4: 尝试使用本地文件安装');
    await executeCommand('ls ~/*.iso 2>/dev/null || echo "无ISO文件"', '检查ISO文件');

    console.log('\nStep 5: 尝试重新安装Debian');
    await executeCommand('proot-distro install debian', '重新安装Debian');

    conn.end();
  } catch (err) {
    console.error('\n修复失败:', err.message);
    conn.end();
  }
}

conn.on('ready', () => {
  console.log('SSH连接成功, 开始修复...');
  fixDebian();
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