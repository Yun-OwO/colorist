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

async function diagnose() {
  console.log('=== 网络诊断 ===');
  
  console.log('\n1. 检查DNS配置');
  await executeCommand('cat /etc/resolv.conf', '查看resolv.conf');
  
  console.log('\n2. 检查网络接口');
  await executeCommand('ip addr', '查看网络接口');
  
  console.log('\n3. 检查路由');
  await executeCommand('ip route', '查看路由表');
  
  console.log('\n4. 测试基础网络连通性');
  await executeCommand('ping -c 2 8.8.8.8', 'ping 8.8.8.8');
  
  console.log('\n5. 测试DNS解析');
  await executeCommand('ping -c 2 mirrors.tuna.tsinghua.edu.cn', 'ping 清华镜像');
  
  console.log('\n6. 检查代理设置');
  await executeCommand('echo $http_proxy && echo $https_proxy', '查看代理环境变量');
  
  console.log('\n7. 测试Termux镜像源');
  await executeCommand('curl -I https://mirrors.tuna.tsinghua.edu.cn/termux/apt/termux-main/ 2>/dev/null || wget --spider https://mirrors.tuna.tsinghua.edu.cn/termux/apt/termux-main/ 2>&1', '测试清华Termux镜像');
  
  console.log('\n=== 网络诊断完成 ===');
  conn.end();
}

conn.on('ready', () => {
  console.log('SSH连接成功, 开始网络诊断...');
  diagnose();
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