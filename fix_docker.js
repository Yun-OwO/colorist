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

async function fixDocker() {
  try {
    console.log('=== 尝试使用Docker安装Debian ===');
    
    console.log('\n1. 安装Docker');
    await executeCommand('pkg install docker -y', '安装Docker');
    
    console.log('\n2. 启动Docker服务');
    await executeCommand('dockerd --daemonize', '启动Docker', true);
    
    console.log('\n3. 等待Docker启动');
    await executeCommand('sleep 5', '等待5秒');
    
    console.log('\n4. 设置Docker镜像加速器');
    await executeCommand('mkdir -p ~/.docker', '创建Docker配置目录');
    await executeCommand("cat > ~/.docker/config.json << 'EOF'\n{\"registry-mirrors\": [\"https://registry.cn-hangzhou.aliyuncs.com\"]}\nEOF", '配置阿里云镜像');
    
    console.log('\n5. 重启Docker服务');
    await executeCommand('pkill dockerd; sleep 2; dockerd --daemonize', '重启Docker', true);
    
    console.log('\n6. 等待Docker启动');
    await executeCommand('sleep 5', '等待5秒');
    
    console.log('\n7. 检查Docker状态');
    await executeCommand('docker info', '检查Docker状态', true);
    
    console.log('\n8. 尝试拉取Debian镜像');
    await executeCommand('docker pull debian:bookworm', '拉取Debian镜像');
    
    console.log('\n9. 运行Debian容器');
    await executeCommand('docker run -d -p 6080:6080 -v /dev/shm:/dev/shm --name debian-desktop dorowu/debian-xfce-vnc', '运行带桌面的Debian容器');
    
    console.log('\n=== Docker安装完成 ===');
    conn.end();
  } catch (err) {
    console.error('\n安装失败:', err.message);
    conn.end();
    process.exit(1);
  }
}

conn.on('ready', () => {
  console.log('SSH连接成功, 开始安装Docker...');
  fixDocker();
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