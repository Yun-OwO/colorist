const { Client } = require('ssh2');

const conn = new Client();

conn.on('ready', () => {
  console.log('SSH连接成功');
  conn.exec('uname -a && cat /etc/os-release', (err, stream) => {
    if (err) throw err;
    stream.on('close', (code, signal) => {
      console.log('命令执行完成, 退出码:', code);
      conn.end();
    }).on('data', (data) => {
      console.log('输出:', data.toString());
    }).stderr.on('data', (data) => {
      console.log('错误:', data.toString());
    });
  });
});

conn.on('error', (err) => {
  console.error('SSH连接错误:', err.message);
});

conn.connect({
  host: '192.168.50.233',
  port: 8022,
  username: 'u0_a621',
  password: '123'
});