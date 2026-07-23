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

async function getIP() {
  try {
    console.log('=== 获取IP地址 ===');
    
    console.log('\n1. 尝试ifconfig');
    await executeCommand('ifconfig', '获取网络接口');
    
    console.log('\n2. 尝试ip addr');
    await executeCommand('ip addr', '获取网络接口');
    
    console.log('\n3. 尝试hostname');
    await executeCommand('hostname', '获取主机名');
    
    console.log('\n4. 尝试cat /proc/net/fib_trie');
    await executeCommand('cat /proc/net/fib_trie | grep -A1 "|-- 192.168"', '获取IP');
    
    conn.end();
  } catch (err) {
    console.error('\n获取失败:', err.message);
    conn.end();
    process.exit(1);
  }
}

conn.on('ready', () => {
  console.log('SSH连接成功, 开始获取IP...');
  getIP();
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